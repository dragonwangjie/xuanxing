package me.rerere.rikkahub.data.ai

import me.rerere.rikkahub.data.datastore.SettingsStore
import okhttp3.Interceptor
import okhttp3.Response
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.IOException

/**
 * 玄星：网络请求自动重试拦截器。
 *
 * 同步自 RikkaHub 2.4.16 新增「自动重试开关」：
 *   - 仅重试：IOException（网络不可用/超时）、HTTP 408/429/5xx 错误
 *   - 重试次数：3 次，间隔呈指数退避 (500ms, 1000ms, 2000ms)
 *   - 可通过设置开关 `displaySetting.enableAutoRetry` 全局关闭
 *
 * 注意：OkHttp Interceptor 是同步调用的，不能用 suspend。
 * 用 Thread.sleep 实现退避，用 settingsFlow.value 直接读当前缓存值。
 */
class RetryInterceptor : Interceptor, KoinComponent {

    private val settingsStore: SettingsStore by inject()

    companion object {
        private const val MAX_RETRIES = 3
        private val RETRYABLE_STATUS_CODES = setOf(408, 429, 500, 502, 503, 504)
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        // 直接读 StateFlow 缓存值（同步，非阻塞）
        val enabled = runCatching {
            settingsStore.settingsFlow.value.displaySetting.enableAutoRetry
        }.getOrElse { true } // 读设置失败时默认开启重试

        if (!enabled) {
            return chain.proceed(chain.request())
        }

        var lastException: Exception? = null
        var attempt = 0
        while (attempt < MAX_RETRIES) {
            try {
                val response = chain.proceed(chain.request())
                val code = response.code
                if (code in RETRYABLE_STATUS_CODES) {
                    // 可重试的 HTTP 状态码
                    response.close()
                    lastException = IOException("Retriable HTTP $code")
                    delayRetry(attempt)
                    attempt++
                    continue
                }
                // 非可重试状态码：直接返回
                return response
            } catch (e: IOException) {
                lastException = e
                // 网络类错误：重试
                delayRetry(attempt)
                attempt++
                continue
            } catch (e: Exception) {
                // 其他异常不重试（避免重试解析/业务异常）
                break
            }
        }

        lastException?.let { throw it }
        throw IOException("Request failed after $MAX_RETRIES retries")
    }

    private fun delayRetry(attempt: Int) {
        // 指数退避：attempt 0 -> 500ms, 1 -> 1000ms, 2 -> 2000ms
        val ms = 500L * (2 shl attempt.coerceIn(0, 2))
        try {
            Thread.sleep(ms)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }
}