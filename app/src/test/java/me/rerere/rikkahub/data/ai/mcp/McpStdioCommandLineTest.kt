package me.rerere.rikkahub.data.ai.mcp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * 行为级测试: 把 buildCommandLine 的产物真正交给 `sh -c` 执行。
 *
 * 判据刻意不用「marker 文件没出现」这一种 —— 实测发现它会给出假阳性:
 * 未转义的单引号注入会让 sh 直接语法报错(rc=2)而根本不执行 payload,
 * 于是**有缺陷的实现也能通过**。主判据改为让 shell 自己回显它看到的每个参数
 * (`printf '[%s]' ...`): 转义正确 ⇒ 恰好一个方括号且内容等于原 payload;
 * 转义失效 ⇒ 被拆成多个参数, 或因语法错误而输出为空。
 */
class McpStdioCommandLineTest {

    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("mcp-stdio-test").toFile()
    }

    private fun marker(name: String) = File(dir, name)

    private fun run(commandLine: String): Pair<Int, String> {
        val p = ProcessBuilder("/bin/sh", "-c", commandLine)
            .redirectErrorStream(true)
            .start()
        val out = p.inputStream.readBytes().toString(Charsets.UTF_8)
        return p.waitFor() to out
    }

    /** 让 shell 回显它实际收到的参数, 用于数出「shell 看到了几个参数」。 */
    private fun shellSees(vararg payloads: String): String =
        run(McpStdioLauncher.buildCommandLine("printf", listOf("[%s]") + payloads.toList())).second.trim()

    @Test
    fun `safe tokens are left unquoted so the line stays readable`() {
        assertEquals("rea mcp --verbose", McpStdioLauncher.buildCommandLine("rea", listOf("mcp", "--verbose")))
    }

    @Test
    fun `blank args are dropped`() {
        assertEquals("rea mcp", McpStdioLauncher.buildCommandLine("rea", listOf("", "  ", "mcp")))
    }

    @Test
    fun `semicolon stays inside one argument and spawns nothing`() {
        val pwned = marker("pwned-semi")
        val payload = "ok; touch ${pwned.absolutePath}"
        val echo = shellSees(payload)

        assertEquals("shell word-split the argument: $echo", "[$payload]", echo)
        assertFalse("injected command executed: $echo", pwned.exists())
    }

    @Test
    fun `semicolon in the command field is not treated as two commands`() {
        val pwned = marker("pwned-cmd")
        // command 里填注入串: 应整体当成一个「找不到」的可执行文件名, 而不是两条命令
        val (_, out) = run(McpStdioLauncher.buildCommandLine("true; touch ${pwned.absolutePath}", emptyList()))
        assertFalse("command field was word-split (output: $out)", pwned.exists())
    }

    @Test
    fun `single quote cannot break out of quoting`() {
        val pwned = marker("pwned-quote")
        // 经典逃逸: 先闭合单引号, 插入命令, 再重新开引号
        val payload = "x'; touch ${pwned.absolutePath}; 'y"
        val echo = shellSees(payload)

        // 转义正确 ⇒ 原样回显成一个参数; 转义失效 ⇒ 语法报错(空输出)或被拆成多个 []
        assertEquals("quote breakout (shell echoed: '$echo')", "[$payload]", echo)
        assertFalse("injected command executed", pwned.exists())
    }

    @Test
    fun `command substitution and backticks are not expanded`() {
        val pwnedSub = marker("pwned-subst")
        val pwnedTick = marker("pwned-tick")
        val payload1 = "\$(touch ${pwnedSub.absolutePath})"
        val payload2 = "`touch ${pwnedTick.absolutePath}`"

        assertEquals("[$payload1][$payload2]", shellSees(payload1, payload2))
        assertFalse("command substitution ran", pwnedSub.exists())
        assertFalse("backtick expansion ran", pwnedTick.exists())
    }

    @Test
    fun `a path containing spaces is passed as one literal argument`() {
        val spaced = File(dir, "a b").apply { writeText("x") }
        val (_, out) = run(McpStdioLauncher.buildCommandLine("/bin/cat", listOf(spaced.absolutePath)))
        assertEquals("x", out.trim())
    }

    @Test
    fun `quoting does not break a normal two argument invocation`() {
        val (_, out) = run(McpStdioLauncher.buildCommandLine("printf", listOf("[%s]", "a", "b")))
        assertEquals("[a][b]", out.trim())
    }
}