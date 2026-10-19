package me.rerere.rikkahub.data.ai.mcp

import kotlinx.serialization.json.encodeToJsonElement
import me.rerere.ai.core.InputSchema
import kotlin.uuid.Uuid

/**
 * MCP 工具的「白名单 + 搜索式按需加载」路由层。
 *
 * 动机是通用的: 一个 MCP 服务器可能导出上百个工具（逆向类尤其夸张）,
 * 把全部 schema 常驻每次请求的上下文会吃掉数万 token, 还会稀释模型对真正相关工具的注意力。
 * 因此把服务器分成两类:
 *  - 普通服务器: 启用的工具（白名单）直接注入 —— 与旧行为一致。
 *  - 懒加载服务器: 只注入一个搜索元工具; 模型搜到的工具被「展开」进会话,
 *    后续轮次即可像普通工具一样调用。白名单里的工具仍然无条件注入。
 */
object McpToolRouter {

    /** 单次搜索最多展开多少工具, 防止一次搜索把几十个 schema 灌进上下文。 */
    const val MAX_EXPAND_PER_SEARCH = 6

    /** 单个懒加载服务器在会话内最多展开多少个工具, 防止长对话无限累积。 */
    const val MAX_EXPANDED_PER_SERVER = 24

    /**
     * 懒加载展开的单个工具注入体积上限(序列化后的近似字节数)。
     * 真实语料(rea, 207 工具)里最大者约 155KB(~39K token), 一个工具就能吃掉半个上下文;
     * 32KB 阈值只拦这一个异常者(16KB 会连带拦掉 12 个正常工具)。
     * 白名单(pinned)不受此限: 那是用户显式意图。
     */
    const val MAX_INJECT_TOOL_BYTES = 32_000

    /** 会话内展开工具的累计注入预算。24 个 32KB 工具仍可到 ~190K token, 数量上限堵不住, 按字节再兜一层。 */
    const val MAX_EXPANDED_BYTES_PER_SERVER = 100_000

    /** 搜索零结果时, 返回给模型的候选名字上限(207 个全名实测仅 ~4.3KB)。 */
    const val MAX_ZERO_RESULT_NAMES = 250

    private val sizeJson = kotlinx.serialization.json.Json { }

    /** 工具注入后的近似字节数: name + description + schema 的 JSON 序列化长度。 */
    fun toolPayloadBytes(tool: McpTool): Int {
        val schemaLen = tool.inputSchema?.let { schema ->
            // 与 provider 侧同一套序列化(ChatCompletionsAPI 注入 tools 用的就是它)
            runCatching { sizeJson.encodeToJsonElement(schema).toString().length }.getOrNull() ?: 0
        } ?: 0
        return tool.name.length + (tool.description?.length ?: 0) + schemaLen
    }

    /** 尚未注入、且尺寸允许搜索后展开的工具。exposure 与零结果兜底共用同一判据。 */
    fun injectableHiddenTools(
        server: McpServerConfig,
        expandedTools: Set<String>,
    ): List<McpTool> {
        val pinnedNames = server.commonOptions.pinnedTools.map { it.name }.toSet()
        return server.commonOptions.tools.filter {
            it.name !in pinnedNames &&
                toolKey(server.id, it.name) !in expandedTools &&
                toolPayloadBytes(it) <= MAX_INJECT_TOOL_BYTES
        }
    }

    data class LazyServer(
        val serverId: Uuid,
        val serverName: String,
        /** 未被白名单固定、可按需搜索的工具数。 */
        val availableCount: Int,
        /** 若干真实工具名, 写进搜索元工具的说明, 让模型知道这台服务器能做什么。 */
        val samples: List<McpTool> = emptyList(),
    )

    data class Exposure(
        /** 直接作为 function 注入的工具。 */
        val direct: List<Triple<Uuid, String, McpTool>>,
        /** 只暴露搜索元工具的懒加载服务器。 */
        val lazyServers: List<LazyServer>,
    ) {
        val isEmpty: Boolean get() = direct.isEmpty() && lazyServers.isEmpty()
    }

    /** 会话内已展开工具的标识: `serverId/toolName`, 与存储无关, 便于随会话持久化。 */
    fun toolKey(serverId: Uuid, toolName: String): String = "$serverId/$toolName"

    /**
     * 纯函数: 只依赖「已筛选的服务器列表」, 不碰 Settings/Assistant, 便于单元测试。
     * 调用方负责从设置里把当前助手启用的服务器筛出来（见 McpManager.toolExposure）。
     */
    fun exposure(
        servers: List<McpServerConfig>,
        expandedTools: Set<String>,
    ): Exposure {
        val direct = mutableListOf<Triple<Uuid, String, McpTool>>()
        val lazyServers = mutableListOf<LazyServer>()

        servers
            .filter { it.commonOptions.enable }
            .forEach { server ->
                val options = server.commonOptions
                // 懒加载与普通模式共享白名单: 已启用的工具无条件注入
                options.pinnedTools.forEach { tool -> direct += Triple(server.id, options.name, tool) }
                if (!options.lazyTools) return@forEach

                val pinnedNames = options.pinnedTools.map { it.name }.toSet()
                val searchable = server.commonOptions.tools.filter { it.name !in pinnedNames }
                // 已展开的工具按体积预算注入: 数量上限(MAX_EXPANDED_PER_SERVER)挡不住
                // 「24 个各 30KB」这种累积, 所以再按字节兜一层。
                var expandedBytes = 0
                searchable.asSequence()
                    .filter { toolKey(server.id, it.name) in expandedTools }
                    .take(MAX_EXPANDED_PER_SERVER)
                    .forEach { tool ->
                        val cost = toolPayloadBytes(tool)
                        if (cost <= MAX_INJECT_TOOL_BYTES &&
                            expandedBytes + cost <= MAX_EXPANDED_BYTES_PER_SERVER
                        ) {
                            expandedBytes += cost
                            direct += Triple(server.id, options.name, tool)
                        }
                    }
                // 超尺寸工具不显示为「可搜」: 搜到也无法注入, 承诺了就是骗模型。
                // 用户真要用可以显式 pin(pinned 不受尺寸限制)。
                val hidden = injectableHiddenTools(server, expandedTools)
                lazyServers += LazyServer(
                    serverId = server.id,
                    serverName = options.name,
                    availableCount = hidden.size,
                    samples = hidden.take(6),
                )
            }
        return Exposure(direct, lazyServers)
    }

    /**
     * 在服务器的工具清单里按关键词检索。本地匹配, 不发 RPC:
     * 工具清单在连接时已由 syncTools 落到设置里, 检索无需触达服务器。
     * 打分: 名字命中远高于描述命中, 前缀命中再加分; 任一词完全不相关则淘汰（AND 语义）。
     */
    fun search(server: McpServerConfig, query: String, limit: Int = MAX_EXPAND_PER_SEARCH): List<McpTool> {
        val terms = query.lowercase().split(Regex("[\\s,|/]+")).map { it.trim() }.filter { it.length >= 2 }
        if (terms.isEmpty()) return emptyList()
        return server.commonOptions.tools
            .asSequence()
            .filterNot { it.enable } // 白名单里的已经作为 function 存在, 不必再搜
            // 超尺寸工具不会被注入, 搜出来也调不动, 索性不参与检索
            .filter { toolPayloadBytes(it) <= MAX_INJECT_TOOL_BYTES }
            .map { tool -> tool to score(tool, terms) }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<McpTool, Int>> { it.second }.thenBy { it.first.name })
            .take(limit.coerceIn(1, 20))
            .map { it.first }
            .toList()
    }

    private fun score(tool: McpTool, terms: List<String>): Int {
        val name = tool.name.lowercase()
        val description = tool.description?.lowercase().orEmpty()
        var total = 0
        for (term in terms) {
            when {
                name == term -> total += 12
                name.startsWith(term) -> total += 8
                name.contains(term) -> total += 5
                description.contains(term) -> total += 2
                else -> return 0
            }
        }
        return total
    }

    /** 把 schema 压成一行提示, 让模型在搜索响应里就能看到参数结构。 */
    fun schemaHint(tool: McpTool): String {
        val schema = tool.inputSchema as? InputSchema.Obj ?: return "no parameters declared"
        val required = schema.required?.takeIf { it.isNotEmpty() }?.let { " required=$it" } ?: ""
        return ("props=" + schema.properties.keys + required)
            .replace(Regex("\\s+"), " ")
            .take(400)
    }

    /** 搜索元工具的说明: 让模型知道这个服务器还有哪些能力可以捞出来用。 */
    fun searchToolDescription(serverName: String, sampleTools: List<McpTool>, remaining: Int): String =
        buildString {
            append("Search the MCP server '$serverName' for tools it provides but that are not currently ")
            append("exposed as functions ($remaining available). ")
            append("Call this BEFORE assuming the server cannot do something; then call the returned tool ")
            append("directly by its full name in a later turn. ")
            if (sampleTools.isNotEmpty()) {
                append("Examples of searchable tools: ")
                append(sampleTools.joinToString(", ") { it.name })
                append(".")
            }
        }

    /** 搜索命中后返回给模型的文本: 含全名与参数结构, 便于立刻构造调用。 */
    fun formatSearchResults(
        serverName: String,
        expandedPrefix: String,
        found: List<McpTool>,
        remaining: Int,
        candidateNames: List<String> = emptyList(),
    ): String = buildString {
        if (found.isEmpty()) {
            append("No tools matched. Try broader keywords (single words work best). ")
            append("$remaining tools are still hidden on '$serverName'.")
            // 检索是 AND 精确子串匹配, 换个说法就可能零结果(实测 8 组常见多词查询全部零命中)。
            // 实测 207 个全名仅 ~4.3KB(常驻 schema 的 0.5%), 直接列出来让模型自己挑,
            // 比让它继续猜词烧轮次便宜得多。挑中后用名字再搜一次即可展开。
            if (candidateNames.isNotEmpty()) {
                append("\nHidden tool names on this server: ")
                append(candidateNames.take(MAX_ZERO_RESULT_NAMES).joinToString(", "))
                append("\nIf one fits, search again with its most distinctive name word to load it.")
            }
            return@buildString
        }
        append("Matched ${found.size} tool(s) on '$serverName'. They are now callable by these exact names:\n")
        found.forEach { tool ->
            append("- $expandedPrefix${tool.name}\n")
            tool.description?.let { append("  description: ${it.take(300)}\n") }
            append("  parameters: ${schemaHint(tool)}\n")
        }
        if (remaining > 0) append("$remaining more tools remain hidden; search again if needed.")
    }
}