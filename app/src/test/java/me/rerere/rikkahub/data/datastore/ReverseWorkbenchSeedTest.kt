package me.rerere.rikkahub.data.datastore

import me.rerere.rikkahub.data.ai.mcp.McpServerConfig
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 逆向工作台预置种子的形状约束。
 *
 * 这里锁的都是真机上会「静默失效」的属性:
 *  - 名称带非字母数字 → 被启用时的名称校验拦住, 用户点了开关但连不上;
 *  - 默认 enable=true → 没装对应 App 的用户每次同步都报错、还占用连接;
 *  - stdio 用裸命令名 → 宿主 shell 是 /system/bin/sh(非登录, PATH 不含 Termux/rootfs 的 bin),
 *    进程起不来且只在运行期报错, 单元测试与编译都发现不了。
 */
class ReverseWorkbenchSeedTest {

    @Test
    fun `every seeded server name passes the enable-time charset check`() {
        DEFAULT_REVERSE_MCP_SERVERS.forEach { server ->
            val name = server.commonOptions.name
            assertTrue("invalid name '$name'", name.matches(Regex("^[A-Za-z0-9]+$")))
        }
    }

    @Test
    fun `all seeds ship disabled`() {
        assertTrue(
            "seeds must not auto-connect",
            DEFAULT_REVERSE_MCP_SERVERS.none { it.commonOptions.enable },
        )
    }

    /**
     * stdio 预置必须能被宿主解析: 绝对路径, 或落进 rootfs(proot 内有自己的 PATH)。
     * 裸命令名在 Android 应用进程里几乎必然 command-not-found —— 曾被当成「可用预置」
     * 塞进来过一次, 见 REA 的调研结论(npm 包 rea-agents, 依赖 node, Android 原生没有)。
     */
    @Test
    fun `stdio seeds are resolvable without a login shell PATH`() {
        val stdioSeeds = DEFAULT_REVERSE_MCP_SERVERS.filterIsInstance<McpServerConfig.StdioServer>()
        stdioSeeds.forEach { seed ->
            val resolvable = seed.command.startsWith("/") ||
                !seed.workspaceRoot.isNullOrBlank()
            assertTrue(
                "stdio seed '${seed.commonOptions.name}' uses bare command " +
                    "'${seed.command}'; use an absolute path or set workspaceRoot",
                resolvable,
            )
        }
    }
}