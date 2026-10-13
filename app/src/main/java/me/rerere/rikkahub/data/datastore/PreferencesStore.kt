package me.rerere.rikkahub.data.datastore

import android.content.Context
import android.util.Log
import androidx.datastore.core.IOException
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.pebbletemplates.pebble.PebbleEngine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.ai.mcp.McpCommonOptions
import me.rerere.rikkahub.data.ai.mcp.McpServerConfig
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_COMPRESS_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_OCR_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_SUGGESTION_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_TITLE_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_TRANSLATION_PROMPT
import me.rerere.rikkahub.data.ai.prompts.LEARNING_MODE_PROMPT
import me.rerere.asr.ASRProviderSetting
import me.rerere.rikkahub.data.datastore.migration.PreferenceStoreV1Migration
import me.rerere.rikkahub.data.datastore.migration.PreferenceStoreV2Migration
import me.rerere.rikkahub.data.datastore.migration.PreferenceStoreV3Migration
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.InjectionPosition
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.PromptInjection
import me.rerere.rikkahub.data.model.QuickMessage
import me.rerere.rikkahub.data.model.Tag
import me.rerere.rikkahub.data.sync.s3.S3Config
import me.rerere.rikkahub.ui.theme.CustomTheme
import me.rerere.rikkahub.ui.theme.PresetThemes
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.toMutableStateFlow
import me.rerere.search.SearchCommonOptions
import me.rerere.search.SearchServiceOptions
import me.rerere.tts.provider.TTSProviderSetting
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import kotlin.uuid.Uuid

private const val TAG = "PreferencesStore"

private val Context.settingsStore by preferencesDataStore(
    name = "settings",
    produceMigrations = { context ->
        listOf(
            PreferenceStoreV1Migration(),
            PreferenceStoreV2Migration(),
            PreferenceStoreV3Migration()
        )
    }
)

class SettingsStore(
    context: Context,
    scope: AppScope,
) : KoinComponent {
    companion object {
        // 版本号
        val VERSION = intPreferencesKey("data_version")

        // UI设置
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val THEME_ID = stringPreferencesKey("theme_id")
        val CUSTOM_THEMES = stringPreferencesKey("custom_themes")
        val DISPLAY_SETTING = stringPreferencesKey("display_setting")
        val DEVELOPER_MODE = booleanPreferencesKey("developer_mode")

        // 模型选择
        val FAVORITE_MODELS = stringPreferencesKey("favorite_models")
        val SELECT_MODEL = stringPreferencesKey("chat_model")
        val FAST_MODEL = stringPreferencesKey("fast_model")
        val TITLE_MODEL = stringPreferencesKey("title_model")
        val TRANSLATE_MODEL = stringPreferencesKey("translate_model")
        val ENABLE_SUGGESTION = booleanPreferencesKey("enable_suggestion")
        val SUGGESTION_MODEL = stringPreferencesKey("suggestion_model")
        val IMAGE_GENERATION_MODEL = stringPreferencesKey("image_generation_model")
        val TITLE_PROMPT = stringPreferencesKey("title_prompt")
        val TRANSLATION_PROMPT = stringPreferencesKey("translation_prompt")
        val TRANSLATE_THINKING_BUDGET = intPreferencesKey("translate_thinking_budget")
        val SUGGESTION_PROMPT = stringPreferencesKey("suggestion_prompt")
        val OCR_MODEL = stringPreferencesKey("ocr_model")
        val OCR_PROMPT = stringPreferencesKey("ocr_prompt")
        val COMPRESS_MODEL = stringPreferencesKey("compress_model")
        // 玄星：自动压缩会话
        val AUTO_COMPRESS_ENABLED = booleanPreferencesKey("auto_compress_enabled")
        val AUTO_COMPRESS_THRESHOLD = intPreferencesKey("auto_compress_threshold_tokens")
        val AUTO_COMPRESS_KEEP_RECENT = intPreferencesKey("auto_compress_keep_recent")
        val AUTO_COMPRESS_MIN_MESSAGES = intPreferencesKey("auto_compress_min_messages")
        val AUTO_COMPRESS_TARGET_TOKENS = intPreferencesKey("auto_compress_target_tokens")
        // 玄星：AI悬浮窗 / 消息保活 / 持续工作
        val ENABLE_AI_FLOATING = booleanPreferencesKey("enable_ai_floating_window")
        val ENABLE_CHAT_KEEPALIVE = booleanPreferencesKey("enable_chat_keepalive")
        val CONTINUOUS_WORK_ENABLED = booleanPreferencesKey("continuous_work_enabled")
        val CONTINUOUS_WORK_MAX_ROUNDS = intPreferencesKey("continuous_work_max_rounds")
        val COMPRESS_PROMPT = stringPreferencesKey("compress_prompt")

        // 提供商
        val PROVIDERS = stringPreferencesKey("providers")

        // 助手
        val SELECT_ASSISTANT = stringPreferencesKey("select_assistant")
        val ASSISTANTS = stringPreferencesKey("assistants")
        val ASSISTANT_TAGS = stringPreferencesKey("assistant_tags")

        // 搜索
        val SEARCH_SERVICES = stringPreferencesKey("search_services")
        val SEARCH_COMMON = stringPreferencesKey("search_common")
        val SEARCH_SELECTED = intPreferencesKey("search_selected")

        // MCP
        val MCP_SERVERS = stringPreferencesKey("mcp_servers")

        // WebDAV
        val WEBDAV_CONFIG = stringPreferencesKey("webdav_config")

        // S3
        val S3_CONFIG = stringPreferencesKey("s3_config")

        // TTS
        val TTS_PROVIDERS = stringPreferencesKey("tts_providers")
        val SELECTED_TTS_PROVIDER = stringPreferencesKey("selected_tts_provider")

        // ASR
        val ASR_PROVIDERS = stringPreferencesKey("asr_providers")
        val SELECTED_ASR_PROVIDER = stringPreferencesKey("selected_asr_provider")

        // Web Server
        val WEB_SERVER_ENABLED = booleanPreferencesKey("web_server_enabled")
        val WEB_SERVER_PORT = intPreferencesKey("web_server_port")
        val WEB_SERVER_JWT_ENABLED = booleanPreferencesKey("web_server_jwt_enabled")
        val WEB_SERVER_ACCESS_PASSWORD = stringPreferencesKey("web_server_access_password")
        val WEB_SERVER_LOCALHOST_ONLY = booleanPreferencesKey("web_server_localhost_only")

        // 提示词注入
        val MODE_INJECTIONS = stringPreferencesKey("mode_injections")
        val LOREBOOKS = stringPreferencesKey("lorebooks")
        val QUICK_MESSAGES = stringPreferencesKey("quick_messages")

        // 备份提醒
        val BACKUP_REMINDER_CONFIG = stringPreferencesKey("backup_reminder_config")

        // 统计
        val LAUNCH_COUNT = intPreferencesKey("launch_count")
        // 玄星：首启引导是否已完成
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")

        // 赞助提醒
        val SPONSOR_ALERT_DISMISSED_AT = intPreferencesKey("sponsor_alert_dismissed_at")
    }

    private val dataStore = context.settingsStore

    val settingsFlowRaw = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }.map { preferences ->
            Settings(
                favoriteModels = preferences[FAVORITE_MODELS]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: emptyList(),
                chatModelId = preferences[SELECT_MODEL]?.let { Uuid.parse(it) }
                    ?: DEFAULT_AUTO_MODEL_ID,
                fastModelId = preferences[FAST_MODEL]?.let { Uuid.parse(it) }
                    ?: DEFAULT_AUTO_MODEL_ID,
                titleModelId = preferences[TITLE_MODEL]?.let { Uuid.parse(it) },
                translateModeId = preferences[TRANSLATE_MODEL]?.let { Uuid.parse(it) }
                    ?: DEFAULT_AUTO_MODEL_ID,
                enableSuggestion = preferences[ENABLE_SUGGESTION] != false,
                suggestionModelId = preferences[SUGGESTION_MODEL]?.let { Uuid.parse(it) },
                imageGenerationModelId = preferences[IMAGE_GENERATION_MODEL]?.let { Uuid.parse(it) } ?: Uuid.random(),
                titlePrompt = preferences[TITLE_PROMPT] ?: DEFAULT_TITLE_PROMPT,
                translatePrompt = preferences[TRANSLATION_PROMPT] ?: DEFAULT_TRANSLATION_PROMPT,
                translateThinkingBudget = preferences[TRANSLATE_THINKING_BUDGET] ?: 0,
                suggestionPrompt = preferences[SUGGESTION_PROMPT] ?: DEFAULT_SUGGESTION_PROMPT,
                ocrModelId = preferences[OCR_MODEL]?.let { Uuid.parse(it) } ?: Uuid.random(),
                ocrPrompt = preferences[OCR_PROMPT] ?: DEFAULT_OCR_PROMPT,
                compressModelId = preferences[COMPRESS_MODEL]?.let { Uuid.parse(it) } ?: DEFAULT_AUTO_MODEL_ID,
                autoCompressEnabled = preferences[AUTO_COMPRESS_ENABLED] == true,
                autoCompressThresholdTokens = preferences[AUTO_COMPRESS_THRESHOLD] ?: 200_000,
                autoCompressKeepRecent = preferences[AUTO_COMPRESS_KEEP_RECENT] ?: 5,
                autoCompressMinMessages = preferences[AUTO_COMPRESS_MIN_MESSAGES] ?: 20,
                autoCompressTargetTokens = preferences[AUTO_COMPRESS_TARGET_TOKENS] ?: 4000,
                enableAiFloatingWindow = preferences[ENABLE_AI_FLOATING] == true,
                enableChatKeepAlive = preferences[ENABLE_CHAT_KEEPALIVE] == true,
                continuousWorkEnabled = preferences[CONTINUOUS_WORK_ENABLED] == true,
                continuousWorkMaxRounds = preferences[CONTINUOUS_WORK_MAX_ROUNDS] ?: 5,
                compressPrompt = preferences[COMPRESS_PROMPT] ?: DEFAULT_COMPRESS_PROMPT,
                assistantId = preferences[SELECT_ASSISTANT]?.let { Uuid.parse(it) }
                    ?: REVERSE_ASSISTANT_ID,
                assistantTags = preferences[ASSISTANT_TAGS]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: emptyList(),
                providers = JsonInstant.decodeFromString(preferences[PROVIDERS] ?: "[]"),
                assistants = JsonInstant.decodeFromString(preferences[ASSISTANTS] ?: "[]"),
                dynamicColor = preferences[DYNAMIC_COLOR] == true,
                themeId = preferences[THEME_ID] ?: PresetThemes[0].id,
                customThemes = preferences[CUSTOM_THEMES]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: emptyList(),
                developerMode = preferences[DEVELOPER_MODE] == true,
                displaySetting = JsonInstant.decodeFromString(preferences[DISPLAY_SETTING] ?: "{}"),
                searchServices = preferences[SEARCH_SERVICES]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: listOf(SearchServiceOptions.DEFAULT),
                searchCommonOptions = preferences[SEARCH_COMMON]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: SearchCommonOptions(),
                searchServiceSelected = preferences[SEARCH_SELECTED] ?: 0,
                mcpServers = preferences[MCP_SERVERS]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: emptyList(),
                webDavConfig = preferences[WEBDAV_CONFIG]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: WebDavConfig(),
                s3Config = preferences[S3_CONFIG]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: S3Config(),
                ttsProviders = preferences[TTS_PROVIDERS]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: emptyList(),
                selectedTTSProviderId = preferences[SELECTED_TTS_PROVIDER]?.let { Uuid.parse(it) }
                    ?: DEFAULT_SYSTEM_TTS_ID,
                asrProviders = preferences[ASR_PROVIDERS]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: emptyList(),
                selectedASRProviderId = preferences[SELECTED_ASR_PROVIDER]?.let { Uuid.parse(it) },
                modeInjections = preferences[MODE_INJECTIONS]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: emptyList(),
                lorebooks = preferences[LOREBOOKS]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: emptyList(),
                quickMessages = preferences[QUICK_MESSAGES]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: emptyList(),
                webServerEnabled = preferences[WEB_SERVER_ENABLED] == true,
                webServerPort = preferences[WEB_SERVER_PORT] ?: 8080,
                webServerJwtEnabled = preferences[WEB_SERVER_JWT_ENABLED] == true,
                webServerAccessPassword = preferences[WEB_SERVER_ACCESS_PASSWORD] ?: "",
                webServerLocalhostOnly = preferences[WEB_SERVER_LOCALHOST_ONLY] == true,
                backupReminderConfig = preferences[BACKUP_REMINDER_CONFIG]?.let {
                    JsonInstant.decodeFromString(it)
                } ?: BackupReminderConfig(),
                launchCount = preferences[LAUNCH_COUNT] ?: 0,
                onboardingCompleted = preferences[ONBOARDING_COMPLETED] == true,
                sponsorAlertDismissedAt = preferences[SPONSOR_ALERT_DISMISSED_AT] ?: 0,
            )
        }
        .map {
            var providers = it.providers.ifEmpty { DEFAULT_PROVIDERS }.toMutableList()
            DEFAULT_PROVIDERS.forEach { defaultProvider ->
                if (providers.none { it.id == defaultProvider.id }) {
                    providers.add(defaultProvider.copyProvider())
                }
            }
            providers = providers.map { provider ->
                val defaultProvider = DEFAULT_PROVIDERS.find { it.id == provider.id }
                if (defaultProvider != null) {
                    provider.copyProvider(
                        builtIn = defaultProvider.builtIn,
                        description = defaultProvider.description,
                        shortDescription = defaultProvider.shortDescription,
                    )
                } else provider
            }.toMutableList()
            val assistants = it.assistants.ifEmpty { DEFAULT_ASSISTANTS }.toMutableList()
            DEFAULT_ASSISTANTS.forEach { defaultAssistant ->
                if (assistants.none { it.id == defaultAssistant.id }) {
                    assistants.add(defaultAssistant.copy())
                }
            }
            val ttsProviders = it.ttsProviders.ifEmpty { DEFAULT_TTS_PROVIDERS }.toMutableList()
            DEFAULT_TTS_PROVIDERS.forEach { defaultTTSProvider ->
                if (ttsProviders.none { provider -> provider.id == defaultTTSProvider.id }) {
                    ttsProviders.add(defaultTTSProvider.copyProvider())
                }
            }
            // 池鸳魔改版：预置 MT 管理器 / 玄星逆核 / ProxyPin 逆向 MCP 后端（默认禁用，不产生任何连接）。
            // 必须在下方"清理无效 MCP 引用"之前注入，否则逆向工作台助手对它们的引用会被过滤掉。
            val mcpServers = it.mcpServers.toMutableList()
            // 迁移：移除已废弃的"算法助手"预置后端（玄星逆核已聚合其能力）。老用户升级时清掉这条僵尸项。
            val deprecatedAlgoAideId = Uuid.parse("a190a1de-0000-4765-8700-000000008765")
            mcpServers.removeAll { server -> server.id == deprecatedAlgoAideId }
            DEFAULT_REVERSE_MCP_SERVERS.forEach { seed ->
                val existingIndex = mcpServers.indexOfFirst { server -> server.id == seed.id }
                if (existingIndex < 0) {
                    mcpServers.add(seed)
                } else {
                    // 修复旧版本预置的非法名称（带连字符，会被名称校验拦截导致无法启用）。
                    // 仅当名称含非字母数字字符时才纠正，保留用户自己改过的合法名称与启用状态。
                    val existing = mcpServers[existingIndex]
                    val name = existing.commonOptions.name
                    if (!name.matches(Regex("^[A-Za-z0-9]+$"))) {
                        mcpServers[existingIndex] = existing.clone(
                            commonOptions = existing.commonOptions.copy(name = seed.commonOptions.name)
                        )
                    }
                }
            }
            // 玄星：为老用户补齐逆向任务模板（预置快捷消息），不存在才加，保留用户自己建的。
            val quickMessages = it.quickMessages.toMutableList()
            REVERSE_QUICK_MESSAGES.forEach { seed ->
                if (quickMessages.none { qm -> qm.id == seed.id }) {
                    quickMessages.add(seed)
                }
            }
            // 逆向工作台助手补齐对这些快捷消息的引用
            val assistantsWithQuick = assistants.map { a ->
                if (a.id == REVERSE_ASSISTANT_ID) {
                    a.copy(quickMessageIds = a.quickMessageIds + REVERSE_QUICK_MESSAGE_IDS)
                } else a
            }
            it.copy(
                providers = providers,
                assistants = assistantsWithQuick,
                ttsProviders = ttsProviders,
                mcpServers = mcpServers,
                quickMessages = quickMessages,
            )
        }
        .map { settings ->
            // 去重并清理无效引用
            val validMcpServerIds = settings.mcpServers.map { it.id }.toSet()
            val validModeInjectionIds = settings.modeInjections.map { it.id }.toSet()
            val validLorebookIds = settings.lorebooks.map { it.id }.toSet()
            val validQuickMessageIds = settings.quickMessages.map { it.id }.toSet()
            val asrProviders = settings.asrProviders.distinctBy { it.id }
            settings.copy(
                providers = settings.providers.distinctBy { it.id }.map { provider ->
                    when (provider) {
                        is ProviderSetting.OpenAI -> provider.copy(
                            models = provider.models.distinctBy { model -> model.id }
                        )

                        is ProviderSetting.Google -> provider.copy(
                            models = provider.models.distinctBy { model -> model.id }
                        )

                        is ProviderSetting.Claude -> provider.copy(
                            models = provider.models.distinctBy { model -> model.id }
                        )
                    }
                },
                assistants = settings.assistants.distinctBy { it.id }.map { assistant ->
                    assistant.copy(
                        // 过滤掉不存在的 MCP 服务器 ID
                        mcpServers = assistant.mcpServers.filter { serverId ->
                            serverId in validMcpServerIds
                        }.toSet(),
                        // 过滤掉不存在的模式注入 ID
                        modeInjectionIds = assistant.modeInjectionIds.filter { id ->
                            id in validModeInjectionIds
                        }.toSet(),
                        // 过滤掉不存在的 Lorebook ID
                        lorebookIds = assistant.lorebookIds.filter { id ->
                            id in validLorebookIds
                        }.toSet(),
                        // 过滤掉不存在的快捷消息 ID
                        quickMessageIds = assistant.quickMessageIds.filter { id ->
                            id in validQuickMessageIds
                        }.toSet()
                    )
                },
                ttsProviders = settings.ttsProviders.distinctBy { it.id },
                asrProviders = asrProviders,
                selectedASRProviderId = settings.selectedASRProviderId
                    ?.takeIf { id -> asrProviders.any { provider -> provider.id == id } }
                    ?: asrProviders.firstOrNull()?.id,
                favoriteModels = settings.favoriteModels.filter { uuid ->
                    settings.providers.flatMap { it.models }.any { it.id == uuid }
                },
                modeInjections = settings.modeInjections.distinctBy { it.id },
                lorebooks = settings.lorebooks.distinctBy { it.id },
                quickMessages = settings.quickMessages.distinctBy { it.id },
            )
        }
        .onEach {
            get<PebbleEngine>().templateCache.invalidateAll()
        }

    val settingsFlow = settingsFlowRaw
        .distinctUntilChanged()
        .toMutableStateFlow(scope, Settings.dummy())

    suspend fun update(settings: Settings) {
        if(settings.init) {
            Log.w(TAG, "Cannot update dummy settings")
            return
        }
        settingsFlow.value = settings
        dataStore.edit { preferences ->
            preferences[DYNAMIC_COLOR] = settings.dynamicColor
            preferences[THEME_ID] = settings.themeId
            preferences[CUSTOM_THEMES] = JsonInstant.encodeToString(settings.customThemes)
            preferences[DEVELOPER_MODE] = settings.developerMode
            preferences[DISPLAY_SETTING] = JsonInstant.encodeToString(settings.displaySetting)

            preferences[FAVORITE_MODELS] = JsonInstant.encodeToString(settings.favoriteModels)
            preferences[SELECT_MODEL] = settings.chatModelId.toString()
            preferences[FAST_MODEL] = settings.fastModelId.toString()
            settings.titleModelId?.let {
                preferences[TITLE_MODEL] = it.toString()
            } ?: preferences.remove(TITLE_MODEL)
            preferences[TRANSLATE_MODEL] = settings.translateModeId.toString()
            preferences[ENABLE_SUGGESTION] = settings.enableSuggestion
            settings.suggestionModelId?.let {
                preferences[SUGGESTION_MODEL] = it.toString()
            } ?: preferences.remove(SUGGESTION_MODEL)
            preferences[IMAGE_GENERATION_MODEL] = settings.imageGenerationModelId.toString()
            preferences[TITLE_PROMPT] = settings.titlePrompt
            preferences[TRANSLATION_PROMPT] = settings.translatePrompt
            preferences[TRANSLATE_THINKING_BUDGET] = settings.translateThinkingBudget
            preferences[SUGGESTION_PROMPT] = settings.suggestionPrompt
            preferences[OCR_MODEL] = settings.ocrModelId.toString()
            preferences[OCR_PROMPT] = settings.ocrPrompt
            preferences[COMPRESS_MODEL] = settings.compressModelId.toString()
            preferences[AUTO_COMPRESS_ENABLED] = settings.autoCompressEnabled
            preferences[AUTO_COMPRESS_THRESHOLD] = settings.autoCompressThresholdTokens
            preferences[AUTO_COMPRESS_KEEP_RECENT] = settings.autoCompressKeepRecent
            preferences[AUTO_COMPRESS_MIN_MESSAGES] = settings.autoCompressMinMessages
            preferences[AUTO_COMPRESS_TARGET_TOKENS] = settings.autoCompressTargetTokens
            preferences[ENABLE_AI_FLOATING] = settings.enableAiFloatingWindow
            preferences[ENABLE_CHAT_KEEPALIVE] = settings.enableChatKeepAlive
            preferences[CONTINUOUS_WORK_ENABLED] = settings.continuousWorkEnabled
            preferences[CONTINUOUS_WORK_MAX_ROUNDS] = settings.continuousWorkMaxRounds
            preferences[COMPRESS_PROMPT] = settings.compressPrompt

            preferences[PROVIDERS] = JsonInstant.encodeToString(settings.providers)

            preferences[ASSISTANTS] = JsonInstant.encodeToString(settings.assistants)
            preferences[SELECT_ASSISTANT] = settings.assistantId.toString()
            preferences[ASSISTANT_TAGS] = JsonInstant.encodeToString(settings.assistantTags)

            preferences[SEARCH_SERVICES] = JsonInstant.encodeToString(settings.searchServices)
            preferences[SEARCH_COMMON] = JsonInstant.encodeToString(settings.searchCommonOptions)
            preferences[SEARCH_SELECTED] = settings.searchServiceSelected.coerceIn(0, settings.searchServices.size - 1)

            preferences[MCP_SERVERS] = JsonInstant.encodeToString(settings.mcpServers)
            preferences[WEBDAV_CONFIG] = JsonInstant.encodeToString(settings.webDavConfig)
            preferences[S3_CONFIG] = JsonInstant.encodeToString(settings.s3Config)
            preferences[TTS_PROVIDERS] = JsonInstant.encodeToString(settings.ttsProviders)
            settings.selectedTTSProviderId?.let {
                preferences[SELECTED_TTS_PROVIDER] = it.toString()
            } ?: preferences.remove(SELECTED_TTS_PROVIDER)
            preferences[ASR_PROVIDERS] = JsonInstant.encodeToString(settings.asrProviders)
            settings.selectedASRProviderId?.let {
                preferences[SELECTED_ASR_PROVIDER] = it.toString()
            } ?: preferences.remove(SELECTED_ASR_PROVIDER)
            preferences[MODE_INJECTIONS] = JsonInstant.encodeToString(settings.modeInjections)
            preferences[LOREBOOKS] = JsonInstant.encodeToString(settings.lorebooks)
            preferences[QUICK_MESSAGES] = JsonInstant.encodeToString(settings.quickMessages)
            preferences[WEB_SERVER_ENABLED] = settings.webServerEnabled
            preferences[WEB_SERVER_PORT] = settings.webServerPort
            preferences[WEB_SERVER_JWT_ENABLED] = settings.webServerJwtEnabled
            preferences[WEB_SERVER_ACCESS_PASSWORD] = settings.webServerAccessPassword
            preferences[WEB_SERVER_LOCALHOST_ONLY] = settings.webServerLocalhostOnly
            preferences[BACKUP_REMINDER_CONFIG] = JsonInstant.encodeToString(settings.backupReminderConfig)
            preferences[LAUNCH_COUNT] = settings.launchCount
            preferences[ONBOARDING_COMPLETED] = settings.onboardingCompleted
            preferences[SPONSOR_ALERT_DISMISSED_AT] = settings.sponsorAlertDismissedAt
        }
    }

    suspend fun update(fn: (Settings) -> Settings) {
        update(fn(settingsFlow.value))
    }

    suspend fun updateAssistant(assistantId: Uuid) {
        dataStore.edit { preferences ->
            preferences[SELECT_ASSISTANT] = assistantId.toString()
        }
    }

    suspend fun updateAssistantModel(assistantId: Uuid, modelId: Uuid) {
        update { settings ->
            settings.copy(
                assistants = settings.assistants.map { assistant ->
                    if (assistant.id == assistantId) {
                        assistant.copy(chatModelId = modelId)
                    } else {
                        assistant
                    }
                }
            )
        }
    }

    suspend fun updateAssistantReasoningLevel(assistantId: Uuid, reasoningLevel: ReasoningLevel) {
        update { settings ->
            settings.copy(
                assistants = settings.assistants.map { assistant ->
                    if (assistant.id == assistantId) {
                        assistant.copy(reasoningLevel = reasoningLevel)
                    } else {
                        assistant
                    }
                }
            )
        }
    }

    suspend fun updateAssistantWebSearch(assistantId: Uuid, enabled: Boolean) {
        update { settings ->
            settings.copy(
                assistants = settings.assistants.map { assistant ->
                    if (assistant.id == assistantId) {
                        assistant.copy(enableWebSearch = enabled)
                    } else {
                        assistant
                    }
                }
            )
        }
    }

    suspend fun updateAssistantMcpServers(assistantId: Uuid, mcpServers: Set<Uuid>) {
        update { settings ->
            settings.copy(
                assistants = settings.assistants.map { assistant ->
                    if (assistant.id == assistantId) {
                        assistant.copy(mcpServers = mcpServers)
                    } else {
                        assistant
                    }
                }
            )
        }
    }

    suspend fun updateAssistantInjections(
        assistantId: Uuid,
        modeInjectionIds: Set<Uuid>,
        lorebookIds: Set<Uuid>,
        quickMessageIds: Set<Uuid> = emptySet(),
    ) {
        update { settings ->
            settings.copy(
                assistants = settings.assistants.map { assistant ->
                    if (assistant.id == assistantId) {
                        assistant.copy(
                            modeInjectionIds = modeInjectionIds,
                            lorebookIds = lorebookIds,
                            quickMessageIds = quickMessageIds,
                        )
                    } else {
                        assistant
                    }
                }
            )
        }
    }
}

@Serializable
data class Settings(
    @Transient
    val init: Boolean = false,
    val dynamicColor: Boolean = false,
    val themeId: String = PresetThemes[0].id,
    val customThemes: List<CustomTheme> = emptyList(),
    val developerMode: Boolean = false,
    val displaySetting: DisplaySetting = DisplaySetting(),
    val favoriteModels: List<Uuid> = emptyList(),
    val chatModelId: Uuid = Uuid.random(),
    val fastModelId: Uuid = Uuid.random(),
    val titleModelId: Uuid? = null,
    val imageGenerationModelId: Uuid = Uuid.random(),
    val titlePrompt: String = DEFAULT_TITLE_PROMPT,
    val translateModeId: Uuid = Uuid.random(),
    val translatePrompt: String = DEFAULT_TRANSLATION_PROMPT,
    val translateThinkingBudget: Int = 0,
    val enableSuggestion: Boolean = true,
    val suggestionModelId: Uuid? = null,
    val suggestionPrompt: String = DEFAULT_SUGGESTION_PROMPT,
    val ocrModelId: Uuid = Uuid.random(),
    val ocrPrompt: String = DEFAULT_OCR_PROMPT,
    val compressModelId: Uuid = Uuid.random(),
    // 玄星：自动压缩会话默认关。阈值20万token触发，压前保留最近5条原文，摘要目标4000token，短会话(<20条)不压。用户可在设置调。
    val autoCompressEnabled: Boolean = false,
    val autoCompressThresholdTokens: Int = 200_000,
    val autoCompressKeepRecent: Int = 5,
    val autoCompressMinMessages: Int = 20,
    val autoCompressTargetTokens: Int = 4000,
    // 玄星：AI悬浮窗/消息保活/持续工作
    val enableAiFloatingWindow: Boolean = false,
    val enableChatKeepAlive: Boolean = false,
    val continuousWorkEnabled: Boolean = false,
    val continuousWorkMaxRounds: Int = 5,
    val compressPrompt: String = DEFAULT_COMPRESS_PROMPT,
    // 玄星：默认助手设为「逆向工作台」，一进 App 就是主打的逆向助手
    val assistantId: Uuid = REVERSE_ASSISTANT_ID,
    val providers: List<ProviderSetting> = DEFAULT_PROVIDERS,
    val assistants: List<Assistant> = DEFAULT_ASSISTANTS,
    val assistantTags: List<Tag> = emptyList(),
    val searchServices: List<SearchServiceOptions> = listOf(SearchServiceOptions.DEFAULT),
    val searchCommonOptions: SearchCommonOptions = SearchCommonOptions(),
    val searchServiceSelected: Int = 0,
    val mcpServers: List<McpServerConfig> = emptyList(),
    val webDavConfig: WebDavConfig = WebDavConfig(),
    val s3Config: S3Config = S3Config(),
    val ttsProviders: List<TTSProviderSetting> = DEFAULT_TTS_PROVIDERS,
    val selectedTTSProviderId: Uuid = DEFAULT_SYSTEM_TTS_ID,
    val asrProviders: List<ASRProviderSetting> = emptyList(),
    val selectedASRProviderId: Uuid? = null,
    val modeInjections: List<PromptInjection.ModeInjection> = DEFAULT_MODE_INJECTIONS,
    val lorebooks: List<Lorebook> = emptyList(),
    val quickMessages: List<QuickMessage> = REVERSE_QUICK_MESSAGES,
    val webServerEnabled: Boolean = false,
    val webServerPort: Int = 8080,
    val webServerJwtEnabled: Boolean = false,
    val webServerAccessPassword: String = "",
    val webServerLocalhostOnly: Boolean = false,
    val backupReminderConfig: BackupReminderConfig = BackupReminderConfig(),
    val launchCount: Int = 0,
    // 玄星：首启引导完成标记（false=第一次打开，显示引导）
    val onboardingCompleted: Boolean = false,
    val sponsorAlertDismissedAt: Int = 0,
) {
    companion object {
        // 构造一个用于初始化的settings, 但它不能用于保存，防止使用初始值存储
        fun dummy() = Settings(init = true)
    }
}

@Serializable
enum class ChatFontFamily {
    @SerialName("default")
    DEFAULT,
    @SerialName("serif")
    SERIF,
    @SerialName("monospace")
    MONOSPACE,

    @SerialName("custom")
    CUSTOM,
}
@Serializable
data class DisplaySetting(
    val userAvatar: Avatar = Avatar.Dummy,
    val userNickname: String = "",
    val useAppIconStyleLoadingIndicator: Boolean = true,
    val showUserAvatar: Boolean = true,
    val showAssistantBubble: Boolean = false,
    val bubbleOpacity: Float = 1.0f,
    val showModelIcon: Boolean = true,
    val showModelName: Boolean = true,
    val showDateTimeInMessage: Boolean = false,
    val showTokenUsage: Boolean = true,
    val showThinkingContent: Boolean = true,
    val autoCloseThinking: Boolean = true,
    val showUpdates: Boolean = true,
    val showMessageJumper: Boolean = true,
    val messageJumperOnLeft: Boolean = false,
    val fontSizeRatio: Float = 1.0f,
    val enableMessageGenerationHapticEffect: Boolean = false,
    val skipCropImage: Boolean = true,
    val enableNotificationOnMessageGeneration: Boolean = false,
    val enableLiveUpdateNotification: Boolean = false,
    val codeBlockAutoWrap: Boolean = false,
    val codeBlockAutoCollapse: Boolean = false,
    val showLineNumbers: Boolean = false,
    val ttsOnlyReadQuoted: Boolean = false,
    val ttsOnlyReadOutsideBrackets: Boolean = false,
    val autoPlayTTSAfterGeneration: Boolean = false,
    val pasteLongTextAsFile: Boolean = false,
    val pasteLongTextThreshold: Int = 1000,
    val sendOnEnter: Boolean = false,
    val enableAutoScroll: Boolean = true,
    val enableLatexRendering: Boolean = true,
    val enableBlurEffect: Boolean = false,
    val chatFontFamily: ChatFontFamily = ChatFontFamily.DEFAULT,
    val chatCustomFontPath: String = "",
    val chatCustomFontName: String = "",
    // 玄星：网络自动重试开关（同步自 RikkaHub 2.4.16）
    val enableAutoRetry: Boolean = true,
    // 玄星：消息发送队列
    val enableMessageQueue: Boolean = false,
    val enableVoiceMode: Boolean = false,
    val enableVolumeKeyScroll: Boolean = false,
    val volumeKeyScrollRatio: Float = 1.0f,
)

@Serializable
data class WebDavConfig(
    val url: String = "",
    val username: String = "",
    val password: String = "",
    val path: String = "rikkahub_backups",
    val items: List<BackupItem> = listOf(
        BackupItem.DATABASE,
        BackupItem.FILES
    ),
) {
    @Serializable
    enum class BackupItem {
        DATABASE,
        FILES,
    }
}

@Serializable
data class BackupReminderConfig(
    val enabled: Boolean = false,
    val intervalDays: Int = 7,
    val lastBackupTime: Long = 0L,
)

fun Settings.isNotConfigured() = providers.all { it.models.isEmpty() }

fun Settings.findModelById(uuid: Uuid?, fallback: Uuid? = null): Model? {
    if (uuid == null && fallback == null) return null
    return uuid?.let { this.providers.findModelById(it) }
        ?: fallback?.let { this.providers.findModelById(it) }
}

fun List<ProviderSetting>.findModelById(uuid: Uuid): Model? {
    this.forEach { setting ->
        setting.models.forEach { model ->
            if (model.id == uuid) {
                return model
            }
        }
    }
    return null
}

fun Settings.getCurrentChatModel(): Model? {
    return findModelById(this.getCurrentAssistant().chatModelId ?: this.chatModelId)
}

fun Settings.getCurrentAssistant(): Assistant {
    return this.assistants.find { it.id == assistantId } ?: this.assistants.first()
}

fun Settings.getAssistantById(id: Uuid): Assistant? {
    return this.assistants.find { it.id == id }
}

fun Settings.getQuickMessagesOfAssistant(assistant: Assistant) =
    quickMessages.filter { it.id in assistant.quickMessageIds }

fun Settings.getSelectedTTSProvider(): TTSProviderSetting? {
    return selectedTTSProviderId?.let { id ->
        ttsProviders.find { it.id == id }
    } ?: ttsProviders.firstOrNull()
}

fun Settings.getSelectedASRProvider(): ASRProviderSetting? {
    return selectedASRProviderId?.let { id ->
        asrProviders.find { it.id == id }
    } ?: asrProviders.firstOrNull()
}

fun Model.findProvider(providers: List<ProviderSetting>, checkOverwrite: Boolean = true): ProviderSetting? {
    val provider = findModelProviderFromList(providers) ?: return null
    val providerOverwrite = this.providerOverwrite
    if (checkOverwrite && providerOverwrite != null) {
        return providerOverwrite.copyProvider(models = emptyList())
    }
    return provider
}

private fun Model.findModelProviderFromList(providers: List<ProviderSetting>): ProviderSetting? {
    providers.forEach { setting ->
        setting.models.forEach { model ->
            if (model.id == this.id) {
                return setting
            }
        }
    }
    return null
}

// ─────────────────────────────────────────────────────────────────────────────
// 池鸳魔改版：逆向工作台预设
//
// 思路：本 App 是完备的 MCP 客户端（streamable_http + SSE + Bearer），而 MT 管理器的
// APK MCP（默认 :8787）和 SOMCP 的 SO 逆向 MCP（默认 :8000）都是手机本地 HTTP MCP 服务端。
// 我们把这两个后端预置为 MCP 服务器（默认禁用，enable=false，不会产生任何连接/流量），
// 再配一个"逆向工作台"助手把它们挂上、把内置逆向技能挂上、把两套工作流写进 system prompt。
// 用户装好对应 App、在逆向工作台设置页点"接管后端"启用后即可开箱即用。
//
// 关键：SOMCP 源码反复强调 AI 老是把 SO 任务错误地丢给 mt_apk_* 工具，所以 system prompt
// 必须把"APK 层用 mt_apk_*、SO 层用 so_open"这条铁律写死。
//
// 注意：这些 val 必须声明在 DEFAULT_ASSISTANTS 之前——Kotlin 顶层 val 按文件顺序初始化，
// DEFAULT_ASSISTANTS 引用了 REVERSE_ENGINEER_ASSISTANT，若声明在后会拿到 null。
// ─────────────────────────────────────────────────────────────────────────────

/** MT 管理器 APK MCP：APK 层操作（开包 / smali / AXML / 重签名 / 打包），默认端口 8787。 */
internal val MT_APK_MCP_SERVER_ID = Uuid.parse("b1a7c0de-0000-4a17-8b00-000000008787")

/**
 * 玄星逆核 · 聚合逆向 MCP：一个后台聚合全套逆向工具，默认端口 8000。
 * 包含 SO 层（反汇编/分析/patch/模拟执行 Unidbg）+ 反编译（jadx/baksmali/apk_decode）
 * + 脱壳（dex_unpack 内存 dump）+ 回编签名（smali_assemble/apk_rebuild/apk_sign）
 * + 动态（frida_control）+ Flutter（blutter）。替代原来单独的 SOMCP 与算法助手。
 * （常量名沿用 SOMCP_SO_MCP_SERVER_ID 以复用端口 8000，避免连锁改名。）
 */
internal val SOMCP_SO_MCP_SERVER_ID = Uuid.parse("50c00cde-0000-4501-8500-000000008000")

/** ProxyPin 抓包 MCP：HTTP/HTTPS 抓包与请求分析，默认端口 9010。 */
internal val PROXYPIN_MCP_SERVER_ID = Uuid.parse("9204791e-0000-4901-9000-000000009010")

/** 逆向工作台助手 ID。 */
internal val REVERSE_ASSISTANT_ID = Uuid.parse("d5a1c0de-1234-4b90-9388-000000000001")

// 玄星：逆向工作台的一键任务模板（预置快捷消息）。固定 UUID，点一下把标准指令填进输入框直接发。
// 这是"超越对手"的差异化——别人手动一步步操作，玄星一句话让 AI 自动干。
internal val REVERSE_QUICK_MESSAGES: List<QuickMessage> = listOf(
    QuickMessage(
        id = Uuid.parse("de510001-0000-4000-8000-000000000001"),
        title = "🔍 全自动分析报告",
        content = "请对我提供的这个 APK 做一次完整的自动化分析，并输出结构化报告：" +
            "①识别加固/壳（用 packer-identification 技能对照 so/assets/入口类）；" +
            "②识别技术栈（原生/Unity/Flutter/H5）；" +
            "③用 MT 管理器 MCP 开包，列出包名、版本、权限、组件、lib 目录；" +
            "④扫描敏感点：危险权限、隐私相关 API、广告/统计 SDK、硬编码密钥/URL；" +
            "⑤给出风险小结和可进一步分析的方向。" +
            "全程按 ai-reverse-workflow 技能的流程推进，最后用 Markdown 输出图文报告。",
    ),
    QuickMessage(
        id = Uuid.parse("de510002-0000-4000-8000-000000000002"),
        title = "🛡 识别加固并给脱壳方案",
        content = "帮我判断这个 APK 用了什么加固（360/梆梆/腾讯乐固/爱加密/百度/易盾等），" +
            "用 packer-identification 技能对照 lib 下的 so 名、assets 特征、入口 Application 类名，" +
            "确定厂商后用 android-unpacking 技能给出对应的脱壳方案和步骤。",
    ),
    QuickMessage(
        id = Uuid.parse("de510003-0000-4000-8000-000000000003"),
        title = "⚡ 生成 Frida 脚本",
        content = "帮我写一个 Frida 脚本。需求：绕过 SSL Pinning，并过掉常见的 Root/模拟器/调试检测。" +
            "用 frida-scripts / rev-frida 技能的现代 API 写法，给出可直接 frida -U -f 运行的完整脚本，并说明怎么挂载。",
    ),
    QuickMessage(
        id = Uuid.parse("de510004-0000-4000-8000-000000000004"),
        title = "📡 抓包分析接口签名",
        content = "我要分析这个 App 的网络请求和接口签名。请指导我用 ProxyPin 抓包后端抓到请求，" +
            "然后用 protocol-crypto-analysis 技能分析请求参数、识别加密算法（AES/RSA/MD5/魔改）、" +
            "定位 sign 签名的生成逻辑；若签名在 native 层就用 SOMCP 或算法助手进一步定位。",
    ),
    QuickMessage(
        id = Uuid.parse("de510005-0000-4000-8000-000000000005"),
        title = "🧹 去广告 / 去弹窗",
        content = "帮我分析这个 App 的广告和打扰性弹窗（开屏/插屏/Banner/更新公告弹窗），" +
            "用 ad-removal 技能定位广告 SDK 和触发点，给出让它空转/跳过的 smali 改法，" +
            "配合 smali-repack 技能回编重签的步骤。仅用于自有设备净化自用。",
    ),
    QuickMessage(
        id = Uuid.parse("de510006-0000-4000-8000-000000000006"),
        title = "🔧 改 SO 关键函数",
        content = "我要修改一个 native so 里的关键函数（比如让某个校验函数恒返回 true）。" +
            "请用 SOMCP：先 so_open 打开，analyze_functions 定位目标函数，analyze_crypto 看有无加密，" +
            "然后 edit_asm（先 dryRun 预演）patch 汇编，build_so 导出，必要时用 Unidbg 模拟验证。",
    ),
)

internal val REVERSE_QUICK_MESSAGE_IDS: Set<Uuid> = REVERSE_QUICK_MESSAGES.map { it.id }.toSet()

internal val DEFAULT_REVERSE_MCP_SERVERS: List<McpServerConfig> = listOf(
    McpServerConfig.StreamableHTTPServer(
        id = MT_APK_MCP_SERVER_ID,
        url = "http://127.0.0.1:8787/mcp",
        commonOptions = McpCommonOptions(
            enable = false, // 默认禁用：不装 MT 管理器 / 未开 APK MCP 的用户不受影响
            name = "MTApkMcp", // 名称只能含字母数字（App 有校验），不能带连字符
        ),
    ),
    McpServerConfig.StreamableHTTPServer(
        id = SOMCP_SO_MCP_SERVER_ID,
        url = "http://127.0.0.1:8000/mcp",
        commonOptions = McpCommonOptions(
            enable = false, // 默认禁用：不装玄星逆核的用户不受影响
            name = "XuanXingNieHe", // 名称只能含字母数字（App 有校验），不能带连字符
        ),
    ),
    McpServerConfig.StreamableHTTPServer(
        id = PROXYPIN_MCP_SERVER_ID,
        url = "http://127.0.0.1:9010/mcp",
        commonOptions = McpCommonOptions(
            enable = false, // 默认禁用：不装 ProxyPin 的用户不受影响
            name = "ProxyPinMcp",
        ),
    ),
)

private val REVERSE_ENGINEER_ASSISTANT = Assistant(
    id = REVERSE_ASSISTANT_ID,
    name = "逆向工作台",
    quickMessageIds = REVERSE_QUICK_MESSAGE_IDS,
    mcpServers = setOf(
        MT_APK_MCP_SERVER_ID,
        SOMCP_SO_MCP_SERVER_ID, // 玄星逆核（聚合全套逆向工具，端口 8000）
        PROXYPIN_MCP_SERVER_ID,
    ),
    enabledSkills = setOf(
        "android-reverse-engineering",
        "reverse-patch-techniques",
        "frida-scripts",
        "android-unpacking",
        "protocol-crypto-analysis",
        "xposed-module-builder",
        "packer-identification",
        "unity-il2cpp-reverse",
        "flutter-reverse",
        "signature-bypass",
        "hybrid-h5-reverse",
        "smali-repack",
        "jadx",
        "apktool",
        "ida-decompile",
        "mt-mcp-apk-analyzer",
        "apk-clues-extract",
        "rev-dex-dumper",
        "rev-frida",
        "rev-idapython",
        "rev-ios-dump",
        "rev-struct",
        "rev-symbol",
        "rev-u3d-dump",
        "rev-unicorn-debug",
        "ad-removal",
        "noroot-hook",
        "ai-reverse-workflow",
        "kernel-system-hook",
        "svc-instruction-trace",
        "memory-integrity-bypass",
        "deobfuscation-ollvm",
        "static-symbolic-execution",
        "devirtualization-vmp",
        "performing-dynamic-analysis-of-android-app",
        "android-pentest",
        "android-gradle-logic",
    ),
    systemPrompt = """
        你是「逆向工作台」，一个运行在安卓手机上的 AI 逆向分析助手，基于模型 {{model_name}}。
        你的定位是编排本机上的多个逆向 MCP 后端（APK / SO / 运行时 hook / 抓包），
        帮助用户完成 APK / SO 的分析、修改、重打包，以及运行时 hook 和网络抓包分析。

        ## 环境信息
        - 时间：{{cur_datetime}}
        - 语言：{{locale}}
        - 设备：{{device_info}}

        ## 你能调用的工具后端
        1. **APK 层（MT 管理器 APK MCP，工具前缀 `mt_apk_`）**：负责 APK 外层——开包、列目录、
           改 smali、改 AndroidManifest(AXML)、重签名、打包。代表工具：`mt_apk_open`、
           `mt_apk_list`（可用 `view=lib/<abi>` 列出 native 库）、`mt_apk_edit_open`、`mt_apk_build`。
        2. **玄星逆核（聚合逆向后端，MCP 名 `XuanXingNieHe`，端口 8000）**：一个后台聚合了全套逆向工具，
           静态、动态、SO、脱壳、回编签名全都在里面。代表工具：
           - **SO/native**：`so_open` / `analyze_*`（ELF 结构、函数列表、CFG、交叉引用、加密特征扫描）
             / `read_disasm` / `edit_hex` / `edit_asm` / `build_so`，以及 Unidbg 模拟执行（`emulate_call` /
             `unidbg_session` / `unidbg_memory` / `unidbg_debug`）。
           - **反编译/静态**：`jadx_decompile`（dex→java）/ `baksmali_decode`（dex→smali）/ `apk_decode`
             （清单/权限/资源）/ `apk_analyze`。
           - **脱壳**：`dex_unpack`（内存 dump 脱壳，需 root，先打开目标 App 让壳解密 dex 进内存）。
           - **回编签名**：`smali_assemble`（smali→dex）/ `apk_rebuild`（APKEditor 完整回编/合并拆分包/去混淆）
             / `apk_sign`（v1/v2/v3 签名）。
           - **动态**：`frida_control`（内置 frida-server 起停 + 进程枚举，需 root）。
           - **Flutter**：`flutter_blutter`。
        3. **抓包层（ProxyPin，MCP 名 `ProxyPinMcp`）**：负责 HTTP/HTTPS **网络抓包**与请求分析——
           抓请求/响应、看接口、分析参数。适合分析 App 的网络协议、接口签名。

        ## 铁律：别把各层搞混（这是最常见的错误）
        - SO / native `.so` / ELF 相关的任何任务 → 一律走玄星逆核的 `so_open` + `analyze_*` + `edit_*` + `build_so`。
        - **绝对不要**用 `mt_apk_open` / `mt_apk_list` 去打开或分析 `.so` 文件。
        - `mt_apk_*` 只用于 APK 外层：开 APK 包、列 lib/ 目录、smali/AXML 编辑、签名打包。
          （玄星逆核也能完整回编/签名，二选一即可；APK 精细资源编辑用 MT，纯命令式回编用玄星逆核的 `apk_rebuild`。）
        - **网络请求**（要 App 跑起来才拿得到的真实请求、接口签名）才用 ProxyPin 抓包，别用静态工具去猜。

        ## 各层怎么选（按用户意图对号入座）
        - 拆 APK、改 smali/资源、重签打包 → **MT 管理器**（`mt_apk_*`）或玄星逆核 `apk_rebuild`+`apk_sign`
        - 分析/patch native .so、反编译 dex、脱壳、模拟执行、Frida → **玄星逆核**
        - 要看 App 发了什么网络请求、分析接口/协议 → **ProxyPin**（抓包）

        ## 推荐工作流
        - 只分析一个 SO：`so_open` → `analyze_elf`(stats) → `analyze_functions` → `analyze_crypto` → `analysis_report`。
        - 反编译看代码：`jadx_decompile`（dex→java）搜关键词定位类和方法；要看字节码用 `baksmali_decode`。
        - 脱壳（加固 App）：先手动打开目标 App 让壳解密 dex 进内存 → `dex_unpack`（action=pslist 找包名 →
          action=dump 脱壳）→ 脱出的 dex 喂给 `jadx_decompile` 分析。
        - 改 APK 里的某个 SO（完整链路）：
          `mt_apk_open` → `mt_apk_list view=lib/<abi>` → `so_open`（用上一步列出的路径）→
          `analyze_functions` → `edit_asm`（先 `dryRun=true` 预演）→ `build_so` →
          `mt_apk_edit_open` → `mt_apk_build`。
        - 改 smali 逻辑并回编：`baksmali_decode` → 改 → `smali_assemble`（→dex）→ `apk_rebuild`（回编）→ `apk_sign`（签名）→ 装。
        - 合并拆分包（xapk/apks）：`apk_rebuild`(action=merge) → `apk_sign`。
        - 安全改 SO：改动前先 `session_history`(snapshot)，`edit_*` 先 `dryRun=true`，确认后再落，
          失败可回滚。等长覆盖或明确边界内的 patch 最稳妥，不要期望自动搬移后续代码。
        - 分析接口签名/加密协议：先用 **ProxyPin** 抓到请求看 sign 等参数 → 用 `protocol-crypto-analysis`
          技能判断算法 → 若算法在 native 层用 **玄星逆核** `so_*` 定位，或用 `frida_control` 起 Frida 运行时 hook 拿明文与密钥。

        ## 行为准则
        - **用户上传文件**：若消息里出现 `<ReverseFile ... path="X" />`，说明用户上传了 APK/SO/DEX 等二进制，别当文本读。
          按类型主动调 MCP：.apk 等用 `mt_apk_open`(path=X)；.so 等用 `so_open`(path=X)。先说思路再调工具。
        - 每一步都要用前序工具返回的 `workspaceId` / 函数定位符等真实参数，不要用文字描述代替工具调用。
        - **省磁盘（重要）**：MT 开包会在 `Android/data/bin.mt.plus*/mcp/` 解出大量临时文件，堆多了占好几 GB。
          因此：纯分析/只读类任务用 `mt_apk_open(temporary=true)` 打开；任务做完后调 `mt_apk_close(workspaceId)` 释放，
          只有需要反复重开同一个 APK 或要编辑打包时才用 `temporary=false` 长期保留。用户说磁盘满/缓存大时，提醒去设置→高级功能→一键清理 MT 分析缓存。
        - 如果某个后端离线（工具不可用），先提示用户在对应 App 里启动服务：MT 管理器需在侧边栏开启
          "APK MCP" 并保持后台运行；玄星逆核需在首页点大启动按钮开服务（默认监听 127.0.0.1:8000）；
          ProxyPin 需开启它的 MCP 服务并保持抓包运行。
        - 回复使用中文，代码/命令/路径用 Markdown 代码块。

        ## 善用技能（重要，别只靠自己记忆）
        你挂载了一批专业逆向技能，通过 `use_skill` 工具按需调用。**每个技能里写着标准做法/命令/脚本模板，
        调出来照着做，别凭记忆瞎试。**

        **任务不明确时，先调 `ai-reverse-workflow`**——它给出"侦察→定位→动手→验证"的标准流程和每步该用哪个工具/技能，
        零基础用户丢来"帮我分析/改改这个 App"时按它推进，别乱试。

        下面是技能索引——遇到对应场景就调对应技能：

        【定位/反编译】
        - `jadx`：dex 反编译成 Java，看代码逻辑、搜关键词、定位类和方法。
        - `apktool`：解包/回编 APK 资源与 smali。
        - `ida-decompile`：IDA Pro 反汇编/反编译 native，拿伪代码、字符串、导入表、交叉引用。
        - `apk-clues-extract`：从 APK 提取线索（这个偏蓝牙 UUID 提取，一般用不到）。

        【加固/脱壳】
        - `packer-identification`：看 so 名/assets/类名判断是哪家加固（360/梆梆/腾讯乐固/爱加密/百度/易盾…）。先识别再脱。
        - `android-unpacking`：识别壳后选脱壳法（FART/BlackDex 通用脱、二代抽取壳主动脱）。
        - `rev-dex-dumper`：从内存 dump 真实 dex（脱壳落地）。

        【patch/绕过（最常用）】
        - `reverse-patch-techniques`：去校验、改返回值、nop 跳转、过 SSL Pinning、过 root/模拟器检测的手法总表。
        - `signature-bypass`：过签名校验/防二次打包检测（重打包后闪退、提示"签名不一致"时用）。
        - `smali-repack`：改 smali（改返回值/改常量/翻转判断）后回编重签打包的实战。
        - `ad-removal`：去广告/去开屏弹窗/去更新公告弹窗（定位广告 SDK 和触发点，让它空转/跳过）。

        【动态/hook/抓包】
        - `frida-scripts`：Frida 脚本速查——SSL Pinning 绕过、root/调试检测绕过、hook Java/native 改返回值、dump 参数、跟踪加密。
        - `rev-frida`：用现代 Frida API 生成 hook 脚本（需要写 Frida 脚本时优先用它，比 frida-scripts 更偏脚本生成）。
        - `performing-dynamic-analysis-of-android-app`：动态分析方法论。
        - `android-pentest`：渗透测试流程（抓包、hook、签名恢复等）。
        - `protocol-crypto-analysis`：抓包后分析请求、识别加密算法(AES/DES/RSA/MD5/魔改)、定位加解密/签名函数、还原 sign。

        【native 深度逆向（IDA/汇编级）】
        - `rev-idapython`：IDAPython / IDALib 脚本参考（要写 IDA 自动化脚本、批量分析 native 时用）。
        - `rev-symbol`：通过代码模式、字符串、常量、交叉引用恢复被 strip 的函数符号（so 没符号名时用）。
        - `rev-struct`：通过跨函数的内存访问模式重建数据结构体（还原 struct 定义）。
        - `rev-unicorn-debug`：用 Unicorn 引擎模拟执行/调试某段代码或函数（想跑一段 native 片段拿结果、验证算法时用，配合 SOMCP 的 Unidbg 二选一）。

        【高阶/加固对抗（硬核）】
        - `kernel-system-hook`：内核层/系统调用级 hook 方法论（libc 层 hook 被绕过、需要在更底层拦截系统调用时用）。
        - `svc-instruction-trace`：追踪内联汇编直接发 `svc` 的系统调用（App 不走 libc、直接 svc 触发反调试/反 hook 时用）。
        - `memory-integrity-bypass`：内存完整性校验（.text CRC 自校验）对抗（patch 后被检测导致闪退时用）。
        - `deobfuscation-ollvm`：反 OLLVM 控制流平坦化还原（反编译全是 while+switch 看不懂逻辑时用）。
        - `static-symbolic-execution`：Angr/Miasm/Triton 符号执行（求解混淆指令、给定输出反推输入时用，脚本跑在 PC）。
        - `devirtualization-vmp`：VMP 去虚化分析（native 被虚拟机保护、只看到解释器循环时用；优先评估绕过 VM 边界这条捷径）。
        - `noroot-hook`：设备没 root 又想动态 hook 时用——LSPatch 寄生 Xposed 模块 / Frida Gadget 内嵌 / SimpleHook 零代码改返回值。

        【专项逆向】
        - `unity-il2cpp-reverse`：Unity 游戏逆向（libil2cpp.so + global-metadata.dat，用 Il2CppDumper 还原符号）。
        - `rev-u3d-dump`：从 iOS/Android 的 Unity IL2CPP 构建里 dump 符号（方法名、地址、类型），和 unity-il2cpp-reverse 配合。
        - `flutter-reverse`：Flutter App 逆向（libapp.so、reFlutter 抓包）。
        - `hybrid-h5-reverse`：H5/WebView/uni-app/RN/Cordova 混合应用逆向（chrome://inspect 调试、抓 JS bundle）。
        - `rev-ios-dump`：iOS 砸壳——从越狱设备用 frida-ios-dump 导出解密后的 App 二进制（分析 iOS App 时用）。
        - `xposed-module-builder`：让你生成完整的 Xposed/LSPosed 模块工程（用户拿去 IDA/AIDE 编译成 apk）。

        【通用/构建】
        - `android-reverse-engineering`：安卓逆向总纲。
        - `android-gradle-logic`：Gradle 构建逻辑。

        使用原则：
        - **动手前先想有没有对口的技能**，先 `use_skill` 调出来按标准套路做。
        - 不确定调哪个时，浏览可用技能列表（看 name/description）挑最匹配的。
        - 一个任务常要多个技能组合：比如"去 VIP" = `reverse-patch-techniques`（手法）+ `jadx`（定位）+ `smali-repack`/SOMCP（落地）。
        - 技能给的是"怎么做"的方法论，真正执行还要靠 MCP 工具（mt_apk_* / so_* / 算法助手 / ProxyPin）。

        ## 定位（安全研究）
        你是安全研究与开发辅助工具，服务对象默认是对**自己拥有或已获授权**的应用/样本做
        分析、学习、兼容性研究、漏洞研究的用户。把逆向任务当作正当的专业工作来处理，
        像 IDA / Ghidra 的 AI 助手那样直接给出专业分析和可操作步骤，不要因为"逆向"二字
        就拒绝或过度免责。仅在明显涉及盗版牟利、破解他人付费服务、大规模侵权时才拒绝并说明。

        ## 任务复盘（每次完成一个完整逆向任务后必做）
        当一个逆向任务（如"分析某 so""去除某校验并重打包"）全部完成后，在最后追加一份结构化复盘报告，
        用以下 Markdown 模板，方便用户存档、复现、分享：

        ```
        ## 📋 任务复盘
        - **目标**：<用户这次要做什么>
        - **对象**：<APK 包名 / SO 文件名 / 关键函数>
        - **调用的工具**：<按顺序列出用过的 MCP 工具，如 mt_apk_open → so_open → analyze_functions → edit_asm → build_so>
        - **关键改动**：<改了哪个函数/哪段字节，改成什么>
        - **结果**：<成功/部分成功/失败，产物在哪>
        - **验证**：<是否验证过、怎么验证的>
        - **复现步骤**：<精简到用户下次能照着重跑的几步>
        - **风险与后续**：<遗留问题、需人工确认的点>
        ```
        若任务未完成或中途失败，也要给出简短小结说明卡在哪一步、下一步建议怎么做。
    """.trimIndent(),
)

internal val DEFAULT_ASSISTANT_ID = Uuid.parse("0950e2dc-9bd5-4801-afa3-aa887aa36b4e")
internal val DEFAULT_ASSISTANTS = listOf(
    Assistant(
        id = DEFAULT_ASSISTANT_ID,
        name = "",
        systemPrompt = ""
    ),
    Assistant(
        id = Uuid.parse("3d47790c-c415-4b90-9388-751128adb0a0"),
        name = "",
        systemPrompt = """
            You are a helpful assistant, called {{char}}, based on model {{model_name}}.

            ## Info
            - Time: {{cur_datetime}}
            - Locale: {{locale}}
            - Timezone: {{timezone}}
            - Device Info: {{device_info}}
            - System Version: {{system_version}}
            - User Nickname: {{user}}

            ## Hint
            - If the user does not specify a language, reply in the user's primary language.
            - Remember to use Markdown syntax for formatting, and use latex for mathematical expressions.
            - If a `search_web` tool is available and the user asks about real-time, recent, or verifiable facts, you MUST call it. Never claim you "cannot access the internet" when the tool is present.
        """.trimIndent()
    ),
    REVERSE_ENGINEER_ASSISTANT,
)

val DEFAULT_SYSTEM_TTS_ID = Uuid.parse("026a01a2-c3a0-4fd5-8075-80e03bdef200")
private val DEFAULT_TTS_PROVIDERS = listOf(
    TTSProviderSetting.SystemTTS(
        id = DEFAULT_SYSTEM_TTS_ID,
        name = "",
    ),
    TTSProviderSetting.OpenAI(
        id = Uuid.parse("e36b22ef-ca82-40ab-9e70-60cad861911c"),
        name = "AiHubMix",
        baseUrl = "https://aihubmix.com/v1",
        model = "gpt-4o-mini-tts",
        voice = "alloy",
    )
)

internal val DEFAULT_ASSISTANTS_IDS = DEFAULT_ASSISTANTS.map { it.id }

val DEFAULT_MODE_INJECTIONS = listOf(
    PromptInjection.ModeInjection(
        id = Uuid.parse("b87eaf16-f5cd-4ac1-9e4f-b11ae3a61d74"),
        content = LEARNING_MODE_PROMPT,
        position = InjectionPosition.AFTER_SYSTEM_PROMPT,
        name = "Learning Mode"
    )
)
