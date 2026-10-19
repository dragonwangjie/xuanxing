package me.rerere.rikkahub.data.ai.mcp

import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import me.rerere.workspace.WorkspaceManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * stdio MCP 端到端: 配置 → McpStdioLauncher → StdioClientTransport → 真实 MCP SDK Client。
 * 对端是 src/test/resources/fake_stdio_mcp_server.py（行分隔 JSON-RPC）。
 */
class McpStdioRoundTripTest {

    private fun findScript(): File {
        val fromResources = Thread.currentThread().contextClassLoader
            ?.getResource("fake_stdio_mcp_server.py")?.let { File(it.toURI()) }
        if (fromResources?.exists() == true) return fromResources
        var dir: File? = File(System.getProperty("user.dir"))
        while (dir != null) {
            val candidate = File(dir, "app/src/test/resources/fake_stdio_mcp_server.py")
            if (candidate.exists()) return candidate
            dir = dir.parentFile
        }
        error("fake_stdio_mcp_server.py not found from ${System.getProperty("user.dir")}")
    }

    @Test(timeout = 180_000)
    fun `stdio server serves tools through the real mcp sdk`() {
        val python = File("/usr/bin/python3")
        assumeTrue("needs /usr/bin/python3", python.canExecute())
        val script = findScript()
        val base = Files.createTempDirectory("mcp-roundtrip").toFile()

        val config = McpServerConfig.StdioServer(
            commonOptions = McpCommonOptions(name = "fake", enable = true),
            command = python.absolutePath,
            args = listOf(script.absolutePath),
            workspaceRoot = null,
            env = listOf("MCP_TEST_SECRET" to "rot13-xray"),
        )

        val connection = McpStdioLauncher.launch(config, WorkspaceManager(baseDir = base)) { }
        val client = Client(clientInfo = Implementation(name = "roundtrip-test", version = "1.0"))
        try {
            val (toolNames, callText) = runBlocking {
                client.connect(connection.transport)
                val names = client.listTools().tools.map { it.name }
                val result = client.callTool(
                    request = CallToolRequest(
                        params = CallToolRequestParams(
                            name = "env_probe",
                            arguments = buildJsonObject { },
                        ),
                    ),
                )
                names to result.content.toString()
            }

            assertEquals(listOf("env_probe"), toolNames)
            assertTrue("injected env missing from result: $callText", callText.contains("rot13-xray"))
        } finally {
            runBlocking { runCatching { client.close() } }
            runCatching { connection.close() }
        }
    }
}