package me.rerere.rikkahub.data.ai.mcp

import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import java.io.ByteArrayInputStream
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import me.rerere.workspace.LaunchedWorkspaceProcess
import me.rerere.workspace.WorkspaceManager

/**
 * stdio MCP 服务器的一次连接: 进程句柄 + 绑定其标准流的 transport。
 * 调用方关闭 transport 后仍必须调用 [McpStdioConnection.close] 回收进程。
 */
class McpStdioConnection(
    val process: LaunchedWorkspaceProcess,
    val transport: StdioClientTransport,
) {
    fun close() = process.destroy()
}

/**
 * 把配置里的命令拉起为长驻进程, 并接到 MCP 的 stdio transport 上。
 *
 * SDK 的 StdioClientTransport 接收的是**流**而非命令, 所以进程生命周期完全由我们掌握:
 * 这正是需要的 —— 崩溃重启、随会话销毁、避免孤儿进程都必须由调用方控制。
 */
object McpStdioLauncher {
    fun launch(
        config: McpServerConfig.StdioServer,
        workspaceManager: WorkspaceManager,
        onStderr: (String) -> Unit,
    ): McpStdioConnection {
        val commandLine = buildCommandLine(config.command, config.args)
        require(commandLine.isNotEmpty()) { "stdio MCP server '${config.commonOptions.name}' has no command" }

        val env = config.env
        val process = if (config.workspaceRoot.isNullOrBlank()) {
            workspaceManager.launchHostProcess(
                command = commandLine,
                extraEnv = env,
                onStderrLine = onStderr,
            )
        } else {
            workspaceManager.launchProcess(
                root = config.workspaceRoot,
                command = commandLine,
                cwd = config.cwd,
                extraEnv = env,
                onStderrLine = onStderr,
            )
        }

        // stderr 已由进程的排空线程逐行消费（防止管道写满阻塞子进程）,
        // 这里交给 transport 一个空流即可, 避免两处争抢同一个 fd。
        return McpStdioConnection(
            process = process,
            transport = StdioClientTransport(
                input = process.stdout.asSource().buffered(),
                output = process.stdin.asSink().buffered(),
                error = ByteArrayInputStream(ByteArray(0)).asSource().buffered(),
            ),
        )
    }

    /**
     * 组合成交给 `sh -c` 的命令行。**所有** token 一律转义。
     *
     * 单 token 也不能原样透传: 用户在 command 里填 `foo; rm -rf /sdcard` 时,
     * 不转义就会被 shell 当成两条命令执行。转义后它变成"找一个名字叫
     * `foo; rm -rf /sdcard` 的可执行文件", 找不到就失败退出 —— 失败可以调试,
     * 注入不可以。代价是多 token 场景不支持带空格/引号的参数（已知 UX 限制）。
     */
    internal fun buildCommandLine(command: String, args: List<String>): String =
        (listOf(command) + args)
            .filter { it.isNotBlank() }
            .joinToString(" ") { shellQuote(it) }
            .trim()

    /** POSIX 单引号转义: 命令与参数由用户填写, 不能拼进 shell 却没转义。 */
    private fun shellQuote(value: String): String =
        if (value.isNotEmpty() && value.all { it.isLetterOrDigit() || it in "./_-:=@+" }) {
            value
        } else {
            "'" + value.replace("'", "'\\''") + "'"
        }
}