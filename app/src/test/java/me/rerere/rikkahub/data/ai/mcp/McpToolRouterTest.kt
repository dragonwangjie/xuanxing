package me.rerere.rikkahub.data.ai.mcp

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class McpToolRouterTest {

    private fun tool(name: String, description: String? = null, enable: Boolean = false) =
        McpTool(name = name, description = description, enable = enable)

    private fun server(
        name: String,
        tools: List<McpTool>,
        lazyTools: Boolean,
        id: Uuid = Uuid.random(),
    ) = McpServerConfig.StreamableHTTPServer(
        id = id,
        commonOptions = McpCommonOptions(name = name, enable = true, tools = tools, lazyTools = lazyTools),
        url = "http://127.0.0.1:1/mcp",
    )

    @Test
    fun `non lazy server exposes all enabled tools`() {
        val s = server(
            name = "normal",
            tools = listOf(tool("a", enable = true), tool("b", enable = false)),
            lazyTools = false,
        )
        val exposure = McpToolRouter.exposure(listOf(s), emptySet())
        assertEquals(listOf("a"), exposure.direct.map { it.third.name })
        assertTrue(exposure.lazyServers.isEmpty())
    }

    @Test
    fun `lazy server exposes only pinned tools plus one search slot`() {
        val s = server(
            name = "rea",
            tools = listOf(
                tool("decompile", "decompile a class", enable = true),
                tool("inspect_android_method", "decompile one method"),
                tool("search_android_classes", "find classes"),
            ),
            lazyTools = true,
        )
        val exposure = McpToolRouter.exposure(listOf(s), emptySet())

        // 只有白名单工具直接进上下文
        assertEquals(listOf("decompile"), exposure.direct.map { it.third.name })
        assertEquals(1, exposure.lazyServers.size)
        // 未 pin 的两个工具算作「可搜索」
        assertEquals(2, exposure.lazyServers.single().availableCount)
    }

    @Test
    fun `searching expands tools into the session for later turns`() {
        val id = Uuid.random()
        val method = tool("inspect_android_method", "decompile one method")
        val s = server(
            "rea",
            listOf(tool("decompile", enable = true), method, tool("unrelated", "xyz")),
            lazyTools = true,
            id = id,
        )

        assertEquals(1, McpToolRouter.exposure(listOf(s), emptySet()).direct.size)

        // 模拟模型调用搜索元工具后回写的 key
        val expanded = setOf(McpToolRouter.toolKey(id, "inspect_android_method"))
        val after = McpToolRouter.exposure(listOf(s), expanded)

        assertTrue(after.direct.any { it.third.name == "inspect_android_method" })
        // 展开后不再算「隐藏」
        assertEquals(1, after.lazyServers.single().availableCount)
    }

    @Test
    fun `search ranks name hits above description hits`() {
        val s = server(
            "rea",
            listOf(
                tool("decompile", "read one part of an app as code"),
                tool("inspect_android_method", "decompile one method"),
                tool("decode_android_resources", "decode resources"),
            ),
            lazyTools = true,
        )
        val found = McpToolRouter.search(s, "decompile")
        assertEquals("decompile", found.first().name)
    }

    @Test
    fun `search requires every term to match somewhere`() {
        val s = server(
            "rea",
            listOf(tool("inspect_android_method", "decompile one method with evidence")),
            lazyTools = true,
        )
        assertEquals(1, McpToolRouter.search(s, "method evidence").size)
        assertTrue(McpToolRouter.search(s, "method signing").isEmpty())
    }

    @Test
    fun `search never returns already pinned tools`() {
        val s = server(
            "rea",
            listOf(tool("decompile", "decompile code", enable = true)),
            lazyTools = true,
        )
        assertTrue(McpToolRouter.search(s, "decompile").isEmpty())
    }

    @Test
    fun `search results teach the model the callable name`() {
        val text = McpToolRouter.formatSearchResults(
            serverName = "rea",
            expandedPrefix = "mcp__rea__",
            found = listOf(tool("inspect_android_method", "decompile one method")),
            remaining = 3,
        )
        assertTrue(text.contains("mcp__rea__inspect_android_method"))
        assertTrue(text.contains("3 more tools remain hidden"))
    }

    @Test
    fun `disabled server contributes nothing`() {
        val s = McpServerConfig.StdioServer(
            commonOptions = McpCommonOptions(
                name = "off",
                enable = false,
                tools = listOf(tool("a", enable = true)),
                lazyTools = true,
            ),
            command = "rea",
            args = listOf("mcp"),
        )
        assertTrue(McpToolRouter.exposure(listOf(s), emptySet()).isEmpty)
    }

    @Test
    fun `tool key is scoped by server so same tool name on two servers stays distinct`() {
        val a = Uuid.random()
        val b = Uuid.random()
        assertNotEquals(McpToolRouter.toolKey(a, "x"), McpToolRouter.toolKey(b, "x"))
        assertTrue(McpToolRouter.toolKey(a, "x").startsWith("$a/"))
    }

    // ---- 尺寸闸门 ----

    private fun hugeSchemaTool(name: String, enable: Boolean = false): McpTool = McpTool(
        name = name,
        description = "schema-heavy tool",
        enable = enable,
        inputSchema = me.rerere.ai.core.InputSchema.Obj(
            properties = kotlinx.serialization.json.buildJsonObject {
                repeat(700) { i ->
                    put("param_$i", kotlinx.serialization.json.buildJsonObject {
                        put("type", "string")
                        put("description", "x".repeat(40))
                    })
                }
            },
            required = emptyList(),
        ),
    )

    @Test
    fun `oversized tool is not searchable nor advertised as available`() {
        val big = hugeSchemaTool("compare_web_captures")
        assertTrue(
            "fixture should exceed the gate, got ${McpToolRouter.toolPayloadBytes(big)}",
            McpToolRouter.toolPayloadBytes(big) > McpToolRouter.MAX_INJECT_TOOL_BYTES,
        )
        val s = server(
            name = "rea",
            tools = listOf(big, tool("web_compare_small", "capture web traffic")),
            lazyTools = true,
        )
        val exposure = McpToolRouter.exposure(listOf(s), emptySet())
        assertEquals(1, exposure.lazyServers.single().availableCount)
        // 搜它的名字也不该返回它: 返回了也注入不了, 等于骗模型
        assertTrue(McpToolRouter.search(s, "compare_web_captures").isEmpty())
    }

    @Test
    fun `explicitly pinned oversized tool still injects because that is user intent`() {
        val big = hugeSchemaTool("compare_web_captures").copy(enable = true)
        val s = server(name = "rea", tools = listOf(big), lazyTools = true)
        val exposure = McpToolRouter.exposure(listOf(s), emptySet())
        assertEquals(listOf("compare_web_captures"), exposure.direct.map { it.third.name })
    }

    @Test
    fun `expanded tools stop being injected once the byte budget is exhausted`() {
        val id = Uuid.random()
        val costPerTool = 30_000 // 单个过闸, 4 个合计超 100K 预算
        // McpTool.enable 默认 true(=白名单直通, 不受尺寸/预算约束), 这里必须显式关掉
        val tools = (1..4).map {
            McpTool(name = "tool_$it", description = "d".repeat(costPerTool), enable = false)
        }
        val s = server(name = "rea", tools = tools, lazyTools = true, id = id)
        val keys = tools.map { McpToolRouter.toolKey(id, it.name) }.toSet()
        val exposure = McpToolRouter.exposure(listOf(s), keys)
        val injected = exposure.direct.size
        assertTrue("expected partial injection under budget, got $injected/4", injected in 1..3)
    }

    @Test
    fun `zero search result hands the model the hidden tool names`() {
        val text = McpToolRouter.formatSearchResults(
            serverName = "rea",
            expandedPrefix = "mcp__rea__",
            found = emptyList(),
            remaining = 2,
            candidateNames = listOf("procedure_assembly", "list_strings"),
        )
        assertTrue(text.contains("No tools matched"))
        assertTrue(text.contains("procedure_assembly"))
        assertTrue(text.contains("list_strings"))
    }

    @Test
    fun `zero result without candidates keeps the old short reply`() {
        val text = McpToolRouter.formatSearchResults(
            serverName = "rea", expandedPrefix = "mcp__rea__", found = emptyList(), remaining = 0,
        )
        assertTrue(text.contains("No tools matched"))
        assertTrue(!text.contains("Hidden tool names"))
    }
}