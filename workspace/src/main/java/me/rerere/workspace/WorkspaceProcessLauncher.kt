package me.rerere.workspace

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * 长驻 Rootfs 进程启动能力。
 *
 * [WorkspaceShellRunner] 是「一次性命令」语义: 起进程 → 等退出 → 收 stdout/stderr,
 * 且带硬超时。MCP stdio 服务器需要相反的生命周期: 进程必须跨多次工具调用一直存活,
 * 双方靠 stdin/stdout 收发换行分隔的 JSON-RPC, 谁都不能提前 waitFor。
 * 因此单独抽象这个接口, 但底层 proot 参数与挂载表仍与一次性命令共用同一份构造函数,
 * 避免两条路径各拼一套参数而漂移。
 */
interface WorkspaceProcessLauncher {
    /**
     * 启动一个长驻进程。调用方负责最终调用 [LaunchedWorkspaceProcess.destroy]。
     *
     * @param onStderrLine 逐行回调进程 stderr, 用于日志与错误诊断; 回调在专用线程上执行。
     */
    fun launch(
        context: WorkspaceShellContext,
        onStderrLine: ((String) -> Unit)? = null,
    ): LaunchedWorkspaceProcess
}

/**
 * 一个已启动的长驻 Rootfs 进程句柄。
 *
 * 注意 stdin/stdout 直接暴露为流而不做缓冲收集: JSON-RPC 是分帧协议,
 * 由上层（MCP SDK 的 stdio transport）按行读取, 这里不能提前消费或截断。
 */
class LaunchedWorkspaceProcess internal constructor(
    val context: WorkspaceShellContext,
    private val process: Process,
    onStderrLine: ((String) -> Unit)?,
) {
    /** 写入 = 发送给子进程的 stdin。 */
    val stdin: OutputStream get() = process.outputStream

    /** 读取 = 子进程 stdout 上的 JSON-RPC 帧。 */
    val stdout: InputStream get() = process.inputStream

    @Volatile
    var lastErrorLine: String? = null
        private set

    val isAlive: Boolean get() = process.isAlive

    private val stderrThread = Thread {
        try {
            process.errorStream.bufferedReader().use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    lastErrorLine = line
                    // 回调异常不能杀死排空线程, 否则管道写满会阻塞子进程
                    runCatching { onStderrLine?.invoke(line) }
                }
            }
        } catch (_: IOException) {
            // 进程被强杀时流会被关闭, 属预期路径
        }
    }.apply {
        isDaemon = true
        name = "workspace-stderr-${context.root}"
        // 必须持续排空 stderr: 否则管道缓冲写满后子进程会永久阻塞在 write() 上,
        // 表现为 MCP 请求无响应而非报错, 极难定位。
        start()
    }

    /** 子进程退出码; 进程仍在运行时返回 null。 */
    fun exitCodeOrNull(): Int? = if (process.isAlive) null else process.exitValue()

    /**
     * 优雅停止: 先关 stdin（EOF）让子进程自然退出, 宽限期后强杀。
     *
     * proot 以 `--kill-on-exit` 启动, 因此 proot 退出时会一并带走 Rootfs 内的孙进程,
     * 这里只需确保 proot 本体退出。
     */
    fun destroy(graceMillis: Long = DEFAULT_GRACE_MILLIS) {
        runCatching { process.outputStream.close() } // EOF: 多数 CLI server 见到 EOF 自行退出
        val exited = runCatching { process.waitFor(graceMillis, java.util.concurrent.TimeUnit.MILLISECONDS) }
            .getOrDefault(false)
        if (!exited) {
            process.destroy()
            if (!runCatching { process.waitFor(GRACE_MILLIS, java.util.concurrent.TimeUnit.MILLISECONDS) }.getOrDefault(false)) {
                process.destroyForcibly()
            }
        }
        stderrThread.interrupt()
    }

    private companion object {
        const val DEFAULT_GRACE_MILLIS = 3_000L
        const val GRACE_MILLIS = 2_000L
    }
}