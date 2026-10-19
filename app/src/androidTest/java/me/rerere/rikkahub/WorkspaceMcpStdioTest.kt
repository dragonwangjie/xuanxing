package me.rerere.rikkahub

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.workspace.HostShellRunner
import me.rerere.workspace.WorkspaceManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.BufferedReader
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * MCP stdio 通道的 App 进程内验证。
 *
 * 关键性: 必须在 App 自己的进程（真 uid、真 SELinux 域、真 nativeLibraryDir）里跑一遍,
 * 因为 exec 子进程的继承属性与 adb shell / 宿主 JVM 测试都不同 —— 前两者的通过不代表
 * 真机能跑通。这里走 WorkspaceManager.launchHostProcess() 与生产路径同一套代码。
 */
@RunWith(AndroidJUnit4::class)
class WorkspaceMcpStdioTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun fakeMcpServerRespondsOverStdio() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = WorkspaceManager(
            baseDir = context.getDir("mcp_stdio_test", android.content.Context.MODE_PRIVATE),
            shellRunner = HostShellRunner(),
        )
        val root = "mcp_stdio_test"
        manager.ensureWorkspace(root)

        // 客户端按 initialize(1) → tools/list(2) → tools/call(3) 的顺序发请求,
        // 因此按行序回显固定 id 即可, 既不用 sed 也不用担心 Kotlin 字符串插值吃掉 $。
        val script = listOf(
            "#!/system/bin/sh",
            "i=0",
            "while IFS= read -r line; do",
            "  i=\$((i+1))",
            "  case \"\$i\" in",
            "    1) printf '%s\\n' '{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"protocolVersion\":\"2024-11-05\",\"capabilities\":{\"tools\":{}},\"serverInfo\":{\"name\":\"fake-sh\",\"version\":\"0.1\"}}}' ;;",
            "    2) printf '%s\\n' '{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{\"tools\":[{\"name\":\"echo\",\"description\":\"echo\",\"inputSchema\":{\"type\":\"object\",\"properties\":{\"message\":{\"type\":\"string\"}},\"required\":[\"message\"]}}]}}' ;;",
            "    *) printf '%s\\n' '{\"jsonrpc\":\"2.0\",\"id\":3,\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"echo:\"}],\"isError\":false}}' ;;",
            "  esac",
            "done",
        ).joinToString("\n")
        manager.writeText(root, "fake_mcp.sh", script)
        manager.executeCommand(root, "chmod 755 fake_mcp.sh")

        val process = manager.launchHostProcess(command = "sh fake_mcp.sh", cwdRoot = root)
        val stderrLines = mutableListOf<String>()
        val reader = BufferedReader(process.stdout.reader())
        val readerPool = Executors.newSingleThreadExecutor { r -> Thread(r, "test-stdout").apply { isDaemon = true } }

        try {
            fun request(payload: String): JsonObject {
                // stdio 传输是 newline-delimited JSON: 没有换行, 对端的 read 永远凑不齐一帧。
                process.stdin.write((payload + "\n").toByteArray())
                process.stdin.flush()
                val future = readerPool.submit<String?> { reader.readLine() }
                val line = future.get(30, TimeUnit.SECONDS)
                assertNotNull("server closed stdout without responding; stderr=$stderrLines", line)
                return json.parseToJsonElement(line!!).jsonObject
            }

            val init = request("""{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test","version":"1"}}}""")
            assertEquals(1, init["id"]!!.jsonPrimitive.content.toInt())
            assertNotNull("init result missing", init["result"])

            val toolsResp = request("""{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}""")
            val tools = toolsResp["result"]!!.jsonObject["tools"]!!
            assertTrue("tools/list should return a list", tools.toString().contains("echo"))

            val call = request("""{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"echo","arguments":{"message":"ping"}}}""")
            assertTrue("tools/call result should carry text", call["result"].toString().contains("echo:"))
            assertTrue("process should still be alive after 3 rounds", process.isAlive)
        } finally {
            process.destroy()
            readerPool.shutdownNow()
        }
    }
}
