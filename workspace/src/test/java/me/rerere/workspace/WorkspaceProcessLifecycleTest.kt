package me.rerere.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList

/**
 * stdio MCP 的进程生命周期验证（宿主 / 非 Rootfs 路径）。
 *
 * 这条路径对应 MCP 配置里「Rootfs 留空」: 直接跑设备上已有的可执行文件（`rea mcp` 就是这种）。
 * 之前完全没有测试覆盖。
 *
 * 读输出统一采用「写完 stdin 就关闭 → 等子进程退出 → 读到 EOF」的形式:
 * 子进程的 printf 输出停在 stdio 块缓冲里, 只有退出时才会 flush,
 * 若边跑边 readLine 会永远等不到数据。
 */
class WorkspaceProcessLifecycleTest {

    private lateinit var base: File

    @Before
    fun setUp() {
        base = Files.createTempDirectory("ws-proc-test").toFile()
    }

    private fun manager() = WorkspaceManager(baseDir = base)

    @Test(timeout = 60_000)
    fun `host process round-trips bytes over stdin and stdout`() {
        val process = manager().launchHostProcess(
            "while IFS= read -r line; do printf 'got:%s\\n' \"\$line\"; done",
        )
        try {
            process.stdin.write("hello\n".toByteArray())
            process.stdin.flush()
            process.stdin.close() // EOF: 子进程退出前会 flush 掉缓冲的输出
            val out = process.stdout.readBytes().decodeToString()
            assertEquals("got:hello\n", out)
            // EOF 不代表进程已被回收, 轮询等它退出, 否则 exitCodeOrNull() 会偶发返回 null
            val deadline = System.currentTimeMillis() + 5_000
            while (process.exitCodeOrNull() == null && System.currentTimeMillis() < deadline) {
                Thread.sleep(20)
            }
            assertEquals(0, process.exitCodeOrNull())
        } finally {
            process.destroy()
        }
    }

    @Test(timeout = 60_000)
    fun `destroy stops the child instead of leaking it`() {
        val process = manager().launchHostProcess("sleep 30")
        assertTrue("child should be running before destroy", process.isAlive)
        process.destroy(graceMillis = 500)
        assertFalse("child survived destroy()", process.isAlive)
    }

    @Test(timeout = 60_000)
    fun `extraEnv reaches the child process in host mode`() {
        // 回归: HostShellRunner 曾完全忽略 extraEnv, 于是 host 模式下 MCP 服务器
        // 配置的 env 静默失效 —— 让子进程自己把变量打印出来, 而不是信任实现。
        val process = manager().launchHostProcess(
            command = "printf '[%s]' \"\$WS_TEST_ENV\"",
            extraEnv = listOf("WS_TEST_ENV" to "hello-from-env"),
        )
        try {
            val out = process.stdout.readBytes().decodeToString()
            assertEquals("[hello-from-env]", out)
        } finally {
            process.destroy()
        }
    }

    @Test(timeout = 90_000)
    fun `large volume of stderr does not deadlock the child`() {
        // stderr 只写不读会在管道缓冲(Linux 默认 64KB)写满后让子进程永久阻塞在 write(),
        // 表现为「MCP 请求挂死」而不是报错。这里刻意制造远超 64KB 的 stderr。
        val lines = 4000
        val pad = "x".repeat(60)
        val seen = CopyOnWriteArrayList<String>()
        val process = manager().launchHostProcess(
            command = "i=0; while [ \$i -lt $lines ]; do echo \"n-\$i-$pad\" >&2; " +
                "i=\$((i+1)); done; " +
                "while IFS= read -r line; do printf 'got:%s\\n' \"\$line\"; done",
            onStderrLine = { seen.add(it) },
        )
        try {
            process.stdin.write("after-stderr\n".toByteArray())
            process.stdin.flush()
            process.stdin.close()
            // 若 stderr 未被排空, 子进程会卡死在这里, readBytes 阻塞直到测试超时
            val out = process.stdout.readBytes().decodeToString()
            assertEquals("got:after-stderr\n", out)
            assertTrue("expected >= $lines stderr lines, got ${seen.size}", seen.size >= lines)
            assertEquals("n-0-$pad", seen.first())
        } finally {
            process.destroy()
        }
    }
}