package me.rerere.rikkahub.data.ai.mcp

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.util.StringValues
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.SseClientTransport
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.shared.AbstractTransport
import io.modelcontextprotocol.kotlin.sdk.shared.RequestOptions
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import me.rerere.ai.core.InputSchema
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.workspace.WorkspaceManager
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

private const val TAG = "McpSessionRegistry"
// 玄星：重连次数 5→10，本机 MCP 后端（MT/SOMCP 等）偶发抖动时多给几次机会，别过早放弃。
private const val MAX_RECONNECT_ATTEMPTS = 10
private const val BASE_RECONNECT_DELAY_MS = 1000L
private const val MAX_RECONNECT_DELAY_MS = 30000L

/** 单个 MCP Server 的全部运行时状态。 */
private class McpSession(initialConfig: McpServerConfig) {
    @Volatile
    var config: McpServerConfig = initialConfig

    @Volatile
    var client: Client? = null

    @Volatile
    var connectedConfig: McpServerConfig? = null

    val lifecycleMutex = Mutex()
    var reconnectJob: Job? = null
    var reconnectAttempt: Int = 0

    /**
     * stdio 传输专属: 子进程句柄。
     * transport.close() 只关流, 不保证回收 proot/bash 孙进程, 因此进程必须由会话显式销毁,
     * 否则每次重连都会漏一个常驻进程。
     */
    @Volatile
    var stdioProcess: McpStdioConnection? = null

    /** stdio 进程意外退出的重启计数（上限见 StdioServer.maxRestartAttempts）。 */
    var stdioRestartAttempt: Int = 0
}

private sealed interface ConnectResult {
    data object Success : ConnectResult
    data object Stale : ConnectResult
    data object NeedsAuthorization : ConnectResult
    data object Failed : ConnectResult
}

internal class McpClientUnavailableException(message: String) : IllegalStateException(message)

internal class McpStatusStore {
    private val _status = MutableStateFlow<Map<Uuid, McpStatus>>(emptyMap())
    val status: StateFlow<Map<Uuid, McpStatus>> = _status.asStateFlow()

    fun get(configId: Uuid): Flow<McpStatus> =
        status.map { it[configId] ?: McpStatus.Idle }.distinctUntilChanged()

    fun update(configId: Uuid, status: McpStatus) {
        _status.update { current -> current + (configId to status) }
    }

    fun remove(configId: Uuid) {
        _status.update { current -> current - configId }
    }
}

/**
 * MCP 连接运行时注册表。
 *
 * 每个 serverId 对应一个 [McpSession]，该 Session 的连接、同步、关闭和重连通过同一把 Mutex 串行执行。
 * Client 只有在 connect 与首次工具同步都成功后才对外可见。
 */
internal class McpSessionRegistry(
    private val settingsStore: SettingsStore,
    private val appScope: AppScope,
    private val httpClient: HttpClient,
    private val oauthCoordinator: McpOAuthCoordinator,
    private val statusStore: McpStatusStore,
    private val workspaceManager: WorkspaceManager,
) {
    private val sessions = ConcurrentHashMap<Uuid, McpSession>()

    fun getClient(configId: Uuid): Client? = sessions[configId]?.client

    fun getStatus(configId: Uuid): Flow<McpStatus> = statusStore.get(configId)

    fun reconcile(configs: List<McpServerConfig>) {
        val activeConfigs = configs
            .filter { it.commonOptions.enable && it.commonOptions.name.isNotBlank() }
            .associateBy { it.id }

        (sessions.keys - activeConfigs.keys).forEach { configId ->
            val detached = sessions.remove(configId) ?: return@forEach
            oauthCoordinator.forget(configId)
            statusStore.remove(configId)
            appScope.launch { closeSession(detached) }
        }

        activeConfigs.values.forEach { newConfig ->
            val existing = sessions[newConfig.id]
            if (existing == null) {
                val session = McpSession(newConfig)
                if (sessions.putIfAbsent(newConfig.id, session) == null) {
                    appScope.launch { addClient(newConfig) }
                }
                return@forEach
            }

            val mustReconnect = !hasSameConnectionParameters(existing.config, newConfig)
            existing.config = newConfig
            if (mustReconnect) {
                appScope.launch { addClient(newConfig) }
            }
        }
    }

    suspend fun callTool(serverId: Uuid, toolName: String, args: JsonObject): CallToolResult {
        val session = sessions[serverId]
            ?: throw McpClientUnavailableException("No MCP session for server $serverId")
        val freshConfig = oauthCoordinator.ensureFreshToken(session.config)
        if (!hasSameConnectionParameters(session.connectedConfig, freshConfig)) {
            session.config = freshConfig
            addClient(freshConfig)
        }

        val sdkClient = session.client
            ?: throw McpClientUnavailableException("MCP client $serverId is not connected")
        val config = session.connectedConfig ?: session.config
        Log.i(TAG, "Calling tool $toolName on $serverId (${config.commonOptions.name})")
        return try {
            sdkClient.callTool(
                request = CallToolRequest(
                    params = CallToolRequestParams(name = toolName, arguments = args),
                ),
                options = RequestOptions(timeout = 120.seconds),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (oauthCoordinator.needsAuthorization(config, e)) {
                statusStore.update(config.id, McpStatus.NeedsAuthorization)
            }
            throw e
        }
    }

    suspend fun addClient(configInput: McpServerConfig) {
        // SettingsStore 是配置真源。旧任务排队后可能晚于新配置执行，不能再写回旧快照。
        val desiredConfig = settingsStore.settingsFlow.value.mcpServers.find { it.id == configInput.id }
        if (desiredConfig == null) {
            removeClient(configInput)
            return
        }
        if (!desiredConfig.commonOptions.enable || desiredConfig.commonOptions.name.isBlank()) {
            removeClient(desiredConfig)
            return
        }

        val session = sessions.computeIfAbsent(desiredConfig.id) { McpSession(desiredConfig) }
        session.config = desiredConfig
        connectSession(
            session = session,
            requestedConfig = desiredConfig,
            cancelPendingReconnect = true,
            forceReconnect = false,
        )
    }

    suspend fun removeClient(config: McpServerConfig) {
        val session = sessions.remove(config.id)
        oauthCoordinator.forget(config.id)
        if (session != null) closeSession(session)
        statusStore.remove(config.id)
    }

    suspend fun syncAll() {
        sessions.values.toList().forEach { session -> syncSession(session) }
    }

    private suspend fun connectSession(
        session: McpSession,
        requestedConfig: McpServerConfig,
        cancelPendingReconnect: Boolean,
        forceReconnect: Boolean,
    ): ConnectResult = withContext(Dispatchers.IO) {
        session.lifecycleMutex.withLock {
            if (sessions[requestedConfig.id] !== session) return@withLock ConnectResult.Stale
            if (!hasSameConnectionParameters(session.config, requestedConfig)) {
                return@withLock ConnectResult.Stale
            }

            if (cancelPendingReconnect) {
                session.reconnectJob?.cancel()
                session.reconnectJob = null
                session.reconnectAttempt = 0
            }

            val config = oauthCoordinator.ensureFreshToken(session.config)
            session.config = config
            if (!forceReconnect &&
                session.client != null &&
                hasSameConnectionParameters(session.connectedConfig, config)
            ) {
                return@withLock ConnectResult.Success
            }

            statusStore.update(config.id, McpStatus.Connecting)
            val oldClient = session.client
            session.client = null
            session.connectedConfig = null
            oldClient?.let { closeClient(it, config.commonOptions.name) }

            val sdkClient = createSdkClient(config)
            val transport = createTransport(session, config)
            installTransportCallbacks(config, sdkClient, transport)

            try {
                sdkClient.connect(transport)
                val syncedConfig = syncTools(session, sdkClient, config)
                if (sessions[config.id] !== session ||
                    !hasSameConnectionParameters(config, syncedConfig)
                ) {
                    closeClient(sdkClient, config.commonOptions.name)
                    destroyStdioProcess(session)
                    return@withLock ConnectResult.Stale
                }

                session.config = syncedConfig
                session.connectedConfig = syncedConfig
                session.client = sdkClient
                session.reconnectAttempt = 0
                statusStore.update(config.id, McpStatus.Connected)
                Log.i(TAG, "Connected MCP server ${config.id} (${config.commonOptions.name})")
                ConnectResult.Success
            } catch (e: CancellationException) {
                closeClient(sdkClient, config.commonOptions.name)
                destroyStdioProcess(session)
                throw e
            } catch (e: Exception) {
                closeClient(sdkClient, config.commonOptions.name)
                // 连接失败（命令写错/依赖缺失/启动即崩溃）时必须回收进程,
                // 否则每次重试都会累积一个孤儿 proot 进程。
                destroyStdioProcess(session)
                Log.e(TAG, "Failed to connect MCP server ${config.id}", e)
                if (oauthCoordinator.needsAuthorization(config, e)) {
                    statusStore.update(config.id, McpStatus.NeedsAuthorization)
                    ConnectResult.NeedsAuthorization
                } else {
                    statusStore.update(config.id, McpStatus.Error.from(e))
                    ConnectResult.Failed
                }
            }
        }
    }

    private suspend fun syncSession(session: McpSession) {
        val config = session.config
        if (session.client == null) {
            addClient(config)
            return
        }

        var reconnectConfig: McpServerConfig? = null
        withContext(Dispatchers.IO) {
            session.lifecycleMutex.withLock {
                val sdkClient = session.client ?: return@withLock
                val connectedConfig = session.connectedConfig ?: return@withLock
                statusStore.update(config.id, McpStatus.Connecting)
                try {
                    val syncedConfig = syncTools(session, sdkClient, session.config)
                    session.config = syncedConfig
                    if (hasSameConnectionParameters(connectedConfig, syncedConfig)) {
                        session.connectedConfig = syncedConfig
                        statusStore.update(config.id, McpStatus.Connected)
                    } else {
                        reconnectConfig = syncedConfig
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (oauthCoordinator.needsAuthorization(config, e)) {
                        statusStore.update(config.id, McpStatus.NeedsAuthorization)
                    } else {
                        statusStore.update(config.id, McpStatus.Error.from(e))
                    }
                }
            }
        }
        reconnectConfig?.let { addClient(it) }
    }

    private suspend fun syncTools(
        session: McpSession,
        sdkClient: Client,
        connectionConfig: McpServerConfig,
    ): McpServerConfig {
        val serverTools = sdkClient.listTools().tools
        Log.i(TAG, "Synced ${serverTools.size} tools from ${connectionConfig.id}")
        var updatedConfig = connectionConfig
        settingsStore.update { old ->
            old.copy(
                mcpServers = old.mcpServers.map { storedConfig ->
                    if (storedConfig.id != connectionConfig.id) return@map storedConfig
                    val tools = mergeTools(
                        storedTools = storedConfig.commonOptions.tools,
                        serverTools = serverTools,
                        // 懒加载服务器上服务器新增的工具默认走搜索, 不自动 pin 进上下文
                        defaultEnabledForNewTools = !storedConfig.commonOptions.lazyTools,
                    )
                    storedConfig.clone(commonOptions = storedConfig.commonOptions.copy(tools = tools))
                        .also { updatedConfig = it }
                }
            )
        }
        session.config = updatedConfig
        return updatedConfig
    }

    private fun installTransportCallbacks(
        config: McpServerConfig,
        sdkClient: Client,
        transport: AbstractTransport,
    ) {
        transport.onClose {
            Log.i(TAG, "Transport closed for ${config.id} (${config.commonOptions.name})")
            requestReconnect(config.id, sdkClient)
        }
        transport.onError { error ->
            Log.e(TAG, "Transport error for ${config.id}: ${error.message}")
            if (!isSseStreamGiveUpError(error)) requestReconnect(config.id, sdkClient)
        }
    }

    /** 合并重复的 onError/onClose 通知，并保证每个 Session 最多只有一个重连任务。 */
    private fun requestReconnect(
        configId: Uuid,
        sourceClient: Client?,
        retryAfterFailure: Boolean = false,
    ) {
        appScope.launch {
            val session = sessions[configId] ?: return@launch
            session.lifecycleMutex.withLock {
                if (sessions[configId] !== session) return@withLock
                if (sourceClient != null && session.client !== sourceClient) return@withLock
                if (!retryAfterFailure && statusStore.status.value[configId] != McpStatus.Connected) {
                    return@withLock
                }
                if (session.reconnectJob?.isActive == true) return@withLock

                val attempt = session.reconnectAttempt + 1
                // stdio 的重连等于重新 spawn 进程: 崩溃多为确定性原因（命令写错/依赖缺失）,
                // 用配置里较小的上限, 避免把一个坏命令变成无限重启循环。
                val cap = (session.config as? McpServerConfig.StdioServer)?.maxRestartAttempts
                    ?.coerceIn(0, MAX_RECONNECT_ATTEMPTS)
                    ?: MAX_RECONNECT_ATTEMPTS
                if (attempt > cap) {
                    val failedClient = session.client
                    session.client = null
                    session.connectedConfig = null
                    failedClient?.let { closeClient(it, session.config.commonOptions.name) }
                    statusStore.update(configId, McpStatus.Error("连接断开，已达最大重连次数"))
                    return@withLock
                }

                session.reconnectAttempt = attempt
                statusStore.update(configId, McpStatus.Reconnecting(attempt, MAX_RECONNECT_ATTEMPTS))
                session.reconnectJob = appScope.launch {
                    reconnectAfterDelay(session, calculateBackoffDelay(attempt))
                }
            }
        }
    }

    private suspend fun reconnectAfterDelay(session: McpSession, delayMs: Long) {
        val runningJob = currentCoroutineContext().job
        var retry = false
        try {
            delay(delayMs)
            val latestConfig = settingsStore.settingsFlow.value.mcpServers.find {
                it.id == session.config.id &&
                    it.commonOptions.enable &&
                    it.commonOptions.name.isNotBlank()
            } ?: return
            session.config = latestConfig
            retry = connectSession(
                session = session,
                requestedConfig = latestConfig,
                cancelPendingReconnect = false,
                forceReconnect = true,
            ) == ConnectResult.Failed
        } catch (e: CancellationException) {
            throw e
        } finally {
            withContext(NonCancellable) {
                session.lifecycleMutex.withLock {
                    if (session.reconnectJob === runningJob) session.reconnectJob = null
                }
            }
        }

        if (retry) requestReconnect(session.config.id, sourceClient = null, retryAfterFailure = true)
    }

    private suspend fun closeSession(session: McpSession) = withContext(Dispatchers.IO) {
        session.lifecycleMutex.withLock {
            session.reconnectJob?.cancel()
            session.reconnectJob = null
            session.reconnectAttempt = 0
            session.stdioRestartAttempt = 0
            val sdkClient = session.client
            session.client = null
            session.connectedConfig = null
            sdkClient?.let { closeClient(it, session.config.commonOptions.name) }
            // client.close() 只关流; proot 与 Rootfs 内的孙进程要靠这里显式回收。
            destroyStdioProcess(session)
        }
    }

    private suspend fun closeClient(client: Client, serverName: String) {
        runCatching { client.close() }
            .onFailure { Log.w(TAG, "Failed to close MCP client $serverName", it) }
    }

    private fun createSdkClient(config: McpServerConfig): Client = Client(
        clientInfo = Implementation(name = config.commonOptions.name, version = "1.0")
    )

    private fun createTransport(session: McpSession, config: McpServerConfig): AbstractTransport = when (config) {
        is McpServerConfig.SseTransportServer -> SseClientTransport(
            urlString = config.url,
            client = httpClient,
            requestBuilder = { appendResolvedHeaders(config) },
        )

        is McpServerConfig.StreamableHTTPServer -> StreamableHttpClientTransport(
            url = config.url,
            client = httpClient,
            requestBuilder = { appendResolvedHeaders(config) },
        )

        is McpServerConfig.StdioServer -> {
            // 先回收上一个进程再拉起新的: 重连路径可能残留旧进程,
            // 两个进程同时读写同一个 workspace 会互相踩踏。
            session.stdioProcess?.let { stale ->
                Log.i(TAG, "Destroying stale stdio process for ${config.commonOptions.name}")
                runCatching { stale.close() }
            }
            val connection = McpStdioLauncher.launch(
                config = config,
                workspaceManager = workspaceManager,
                onStderr = { line ->
                    Log.d(TAG, "stdio[${config.commonOptions.name}] $line")
                },
            )
            session.stdioProcess = connection
            connection.transport
        }
    }

    /** 只回收进程, 不动 transport（transport 由 client.close() 负责）。 */
    private fun destroyStdioProcess(session: McpSession) {
        val connection = session.stdioProcess ?: return
        session.stdioProcess = null
        runCatching { connection.close() }
            .onFailure { Log.w(TAG, "Failed to destroy stdio process", it) }
    }

    private fun HttpRequestBuilder.appendResolvedHeaders(config: McpServerConfig) {
        headers.appendAll(StringValues.build {
            config.resolvedHeaders().forEach { (name, value) -> append(name, value) }
        })
    }

    private fun calculateBackoffDelay(attempt: Int): Long {
        val exponentialDelay = BASE_RECONNECT_DELAY_MS * (1L shl (attempt - 1).coerceAtMost(10))
        return exponentialDelay.coerceAtMost(MAX_RECONNECT_DELAY_MS)
    }

    private fun isSseStreamGiveUpError(error: Throwable): Boolean {
        val message = generateSequence(error) { it.cause }
            .mapNotNull { it.message }
            .joinToString(" ")
        return message.contains("Maximum reconnection attempts exceeded", ignoreCase = true)
    }
}

/** 只包含会影响实际连接的字段；工具开关和 Schema 变化不会触发重连。 */
internal data class McpConnectionKey(
    val transportType: String,
    val serverUrl: String,
    val clientName: String,
    val headers: List<Pair<String, String>>,
    /** stdio 专属: 命令/参数/环境/位置任一变化都必须重连（HTTP 服务器为空）。 */
    val stdioCommand: List<String> = emptyList(),
    val stdioEnv: List<Pair<String, String>> = emptyList(),
)

internal fun McpServerConfig.connectionKey(): McpConnectionKey = when (this) {
    is McpServerConfig.StdioServer -> McpConnectionKey(
        transportType = "stdio",
        serverUrl = serverUrl,
        clientName = commonOptions.name,
        headers = resolvedHeaders(),
        stdioCommand = listOf(command) + args + listOf("cwd=$cwd", "root=${workspaceRoot.orEmpty()}"),
        stdioEnv = env,
    )

    else -> McpConnectionKey(
        transportType = when (this) {
            is McpServerConfig.SseTransportServer -> "sse"
            is McpServerConfig.StreamableHTTPServer -> "streamable_http"
            is McpServerConfig.StdioServer -> "stdio"
        },
        serverUrl = serverUrl,
        clientName = commonOptions.name,
        headers = resolvedHeaders(),
    )
}

private fun hasSameConnectionParameters(
    left: McpServerConfig?,
    right: McpServerConfig?,
): Boolean = left != null && right != null && left.connectionKey() == right.connectionKey()

private fun McpServerConfig.resolvedHeaders(): List<Pair<String, String>> {
    val base = commonOptions.headers
    val token = commonOptions.oauth?.takeIf { it.enabled }?.accessToken
    val hasAuthorization = base.any { it.first.equals("Authorization", ignoreCase = true) }
    return if (!token.isNullOrBlank() && !hasAuthorization) {
        base + ("Authorization" to "Bearer $token")
    } else {
        base
    }
}

private fun mergeTools(
    storedTools: List<McpTool>,
    serverTools: List<Tool>,
    /** 懒加载服务器上「新出现的工具」默认不启用, 否则等于自动 pin, 懒加载静默失效。 */
    defaultEnabledForNewTools: Boolean = true,
): List<McpTool> {
    val toolsByName = storedTools.associateBy { it.name }
    return serverTools.map { serverTool ->
        toolsByName[serverTool.name]?.copy(
            description = serverTool.description,
            inputSchema = serverTool.inputSchema.toSchema(),
        ) ?: McpTool(
            name = serverTool.name,
            description = serverTool.description,
            enable = defaultEnabledForNewTools,
            inputSchema = serverTool.inputSchema.toSchema(),
        )
    }
}

private fun ToolSchema.toSchema(): InputSchema =
    InputSchema.Obj(properties = properties ?: JsonObject(emptyMap()), required = required)
