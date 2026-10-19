package me.rerere.rikkahub.data.ai.mcp

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.rerere.ai.core.InputSchema
import kotlin.uuid.Uuid

@Serializable
data class McpCommonOptions(
    val enable: Boolean = true,
    val name: String = "",
    val headers: List<Pair<String, String>> = emptyList(),
    val tools: List<McpTool> = emptyList(),
    val oauth: McpOAuthState? = null,
    /**
     * 懒加载工具：不把该服务器的全部工具塞进每次请求的上下文, 而是只注入一个搜索元工具,
     * 由模型按需检索并展开。用于工具数上百的服务器（例如逆向类）——
     * 200+ 个 schema 常驻上下文会直接吃掉数万 token 并稀释模型注意力。
     * 已被手动启用的工具（白名单）仍然始终注入。
     */
    val lazyTools: Boolean = false,
) {
    /** 已启用的工具即白名单：懒加载模式下它们无条件进入上下文。 */
    val pinnedTools: List<McpTool> get() = tools.filter { it.enable }
}

/**
 * OAuth 2.1 授权状态，遵循 MCP 授权规范 (2025-11-25)。
 *
 * 持久化了动态客户端注册结果、授权服务器端点以及令牌，用于对需要
 * OAuth 授权的 MCP Server 注入 `Authorization: Bearer` 请求头并支持刷新。
 */
@Serializable
data class McpOAuthState(
    val enabled: Boolean = false,
    val clientId: String? = null,
    val clientSecret: String? = null,
    val authorizationEndpoint: String? = null,
    val tokenEndpoint: String? = null,
    val registrationEndpoint: String? = null,
    val scope: String? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val expiresAt: Long = 0L, // epoch millis, 0 表示未知/不过期
) {
    val isAuthorized: Boolean get() = !accessToken.isNullOrBlank()

    // 脱敏 toString，避免 client_secret / token 随 config 打印到日志
    override fun toString(): String =
        "McpOAuthState(enabled=$enabled, clientId=$clientId, clientSecret=${clientSecret.masked()}, " +
            "authorizationEndpoint=$authorizationEndpoint, tokenEndpoint=$tokenEndpoint, " +
            "registrationEndpoint=$registrationEndpoint, scope=$scope, " +
            "accessToken=${accessToken.masked()}, refreshToken=${refreshToken.masked()}, expiresAt=$expiresAt)"

    private fun String?.masked(): String = when {
        this == null -> "null"
        isBlank() -> "***"
        else -> "***(${length})"
    }
}

@Serializable
data class McpTool(
    val enable: Boolean = true,
    val name: String = "",
    val description: String? = null,
    val inputSchema: InputSchema? = null,
    val needsApproval: Boolean = false
)

@Serializable
sealed class McpServerConfig {
    abstract val id: Uuid
    abstract val commonOptions: McpCommonOptions

    abstract fun clone(
        id: Uuid = this.id,
        commonOptions: McpCommonOptions = this.commonOptions
    ): McpServerConfig

    @Serializable
    @SerialName("sse")
    data class SseTransportServer(
        override val id: Uuid = Uuid.random(),
        override val commonOptions: McpCommonOptions = McpCommonOptions(),
        val url: String = "",
    ) : McpServerConfig() {
        override fun clone(id: Uuid, commonOptions: McpCommonOptions): McpServerConfig {
            return copy(id = id, commonOptions = commonOptions)
        }
    }

    @Serializable
    @SerialName("streamable_http")
    data class StreamableHTTPServer(
        override val id: Uuid = Uuid.random(),
        override val commonOptions: McpCommonOptions = McpCommonOptions(),
        val url: String = "",
    ) : McpServerConfig() {
        override fun clone(id: Uuid, commonOptions: McpCommonOptions): McpServerConfig {
            return copy(id = id, commonOptions = commonOptions)
        }
    }

    @Serializable
    @SerialName("stdio")
    data class StdioServer(
        override val id: Uuid = Uuid.random(),
        override val commonOptions: McpCommonOptions = McpCommonOptions(),
        /** 可执行文件；绝对路径，或 Rootfs 内可直接解析的命令名。 */
        val command: String = "",
        val args: List<String> = emptyList(),
        /** 追加到进程环境（继承基础环境，不做 env -i 清空）。 */
        val env: List<Pair<String, String>> = emptyList(),
        /**
         * 运行位置：null = 设备宿主 shell（本机可执行文件）；
         * 非空 = 该名称 workspace 的 Rootfs 内（proot），用于跑 node/java 这类 Linux 工具链。
         */
        val workspaceRoot: String? = null,
        /** Rootfs 内的相对工作目录；仅 [workspaceRoot] 非空时生效。 */
        val cwd: String = "",
        /**
         * 工具进程意外退出后的自动重启上限。stdio 服务器是本地进程，
         * 崩溃多半是确定性的（命令写错/依赖缺失），不宜无限拉起。
         */
        val maxRestartAttempts: Int = 3,
    ) : McpServerConfig() {
        override fun clone(id: Uuid, commonOptions: McpCommonOptions): McpServerConfig {
            return copy(id = id, commonOptions = commonOptions)
        }
    }
}

/** MCP Server 的连接地址（作为 OAuth 的 canonical resource 标识）。 */
val McpServerConfig.serverUrl: String
    get() = when (this) {
        is McpServerConfig.SseTransportServer -> url
        is McpServerConfig.StreamableHTTPServer -> url
        // stdio 没有 URL；用命令与位置拼一个稳定的规范标识，保证 OAuth/去重等按 URL 索引的逻辑仍有确定值。
        is McpServerConfig.StdioServer -> "stdio://" +
            (workspaceRoot?.let { "$it/" }.orEmpty()) + command
    }
