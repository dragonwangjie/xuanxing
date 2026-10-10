package me.rerere.rikkahub.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.rerere.rikkahub.CHAT_LIVE_UPDATE_NOTIFICATION_CHANNEL_ID
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.event.AppEvent
import me.rerere.rikkahub.data.event.AppEventBus
import org.koin.android.ext.android.inject

private const val TAG = "ChatKeepAliveService"

/**
 * 玄星：消息保活前台服务。
 * 生成开始时被拉起，占用前台 + 显示"AI 生成中"常驻通知，让系统不轻易杀掉进程，
 * 使后台/熄屏时 AI 对话仍能继续跑完。生成结束（ChatGenerationEnded）后自动退出。
 * 由 Settings.enableChatKeepAlive 控制是否启用。
 */
class ChatKeepAliveService : Service() {

    companion object {
        const val ACTION_START = "me.rerere.rikkahub.action.CHAT_KEEPALIVE_START"
        const val ACTION_STOP = "me.rerere.rikkahub.action.CHAT_KEEPALIVE_STOP"
        const val NOTIFICATION_ID = 2002
    }

    private val appEventBus: AppEventBus by inject()
    private val settingsStore: me.rerere.rikkahub.data.datastore.SettingsStore by inject()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var observerJob: Job? = null
    // 记录当前在生成的会话数；归零则退出前台
    private val activeConversations = HashSet<String>()
    // 悬浮球是否已显示（避免重复 install）
    private var floatingBallShown = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                dismissFloatingBall()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }

            else -> {
                if (!startForegroundCompat()) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                startObserving()
                maybeShowFloatingBall()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        dismissFloatingBall()
        serviceScope.cancel()
    }

    /** 按悬浮球开关显示系统级悬浮球（幂等）。 */
    private fun maybeShowFloatingBall() {
        serviceScope.launch {
            val enabled = runCatching { settingsStore.settingsFlow.first().enableAiFloatingWindow }
                .getOrDefault(false)
            if (enabled && !floatingBallShown) {
                AiFloatingBall.show(applicationContext)
                floatingBallShown = true
            }
        }
    }

    private fun dismissFloatingBall() {
        if (floatingBallShown) {
            AiFloatingBall.dismiss()
            floatingBallShown = false
        }
    }

    private fun startObserving() {
        if (observerJob != null) return
        observerJob = serviceScope.launch {
            appEventBus.events.collect { event ->
                when (event) {
                    is AppEvent.ChatGenerationUpdate -> {
                        activeConversations.add(event.conversationId.toString())
                    }

                    is AppEvent.ChatGenerationEnded -> {
                        activeConversations.remove(event.conversationId.toString())
                        // 没有会话在生成了 → 撤悬浮球 + 退出前台服务
                        if (activeConversations.isEmpty()) {
                            dismissFloatingBall()
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            stopSelf()
                        }
                    }

                    else -> Unit
                }
            }
        }
    }

    private fun startForegroundCompat(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    buildNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, buildNotification())
            }
            true
        } catch (e: Exception) {
            // OEM ROM 可能拒绝 FGS，兜底不崩
            Log.e(TAG, "Failed to start foreground service", e)
            false
        }
    }

    private fun buildNotification() =
        NotificationCompat.Builder(this, CHAT_LIVE_UPDATE_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.small_icon)
            .setContentTitle("玄星正在后台生成")
            .setContentText("AI 对话生成中，保持后台运行…")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
}
