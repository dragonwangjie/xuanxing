package me.rerere.rikkahub.service

import android.app.Application
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.petterp.floatingx.FloatingX
import com.petterp.floatingx.assist.FxGravity
import com.petterp.floatingx.assist.FxScopeType
import me.rerere.rikkahub.R

/**
 * 玄星：AI 生成中系统级悬浮球。
 *
 * 与旧的应用内悬浮窗（AiGeneratingFloatingWindow，仅玄星前台可见）不同，
 * 这里用系统级悬浮窗（SYSTEM_ALERT_WINDOW），可在切后台、其他 App、桌面全程显示。
 * 由 ChatKeepAliveService（前台保活服务）在生成开始时 show、结束时 dismiss，
 * 生命周期脱离 Activity，滑到后台也不消失。
 *
 * 权限策略：FxScopeType.SYSTEM_AUTO —— 有悬浮窗权限走系统级；没权限自动降级为应用内浮窗
 * （不崩，但降级后就只在前台可见，需引导用户去授权）。
 */
object AiFloatingBall {

    private const val TAG = "ai_generating_ball"

    /** 是否已授予悬浮窗权限。 */
    fun hasOverlayPermission(context: Context): Boolean =
        Settings.canDrawOverlays(context)

    /** 显示悬浮球（幂等，重复调用只显示一个）。 */
    fun show(context: Context) {
        val app = context.applicationContext as? Application ?: return
        if (FloatingX.isInstalled(TAG)) {
            FloatingX.controlOrNull(TAG)?.show()
            return
        }
        runCatching {
            FloatingX.install {
                setContext(app)
                setTag(TAG)
                // SYSTEM_AUTO：有权限=系统级(能浮在其他App上)，无权限=自动降级应用内(不崩)
                setScopeType(FxScopeType.SYSTEM_AUTO)
                setGravity(FxGravity.LEFT_OR_BOTTOM)
                setOffsetXY(16f, -120f)
                setEnableEdgeAdsorption(true) // 边缘吸附
                setEnableAnimation(true)
                setLayoutView(buildBallView(app))
            }.show()
        }
    }

    /** 隐藏并销毁悬浮球。 */
    fun dismiss() {
        runCatching {
            FloatingX.controlOrNull(TAG)?.cancel()
        }
    }

    /** 代码构建"⟳ 玄星生成中"胶囊视图（不依赖 Compose，避免 Service 里 ComposeView 的 lifecycle 坑）。 */
    private fun buildBallView(context: Context): View {
        val density = context.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val accent = Color.parseColor("#7C3AED")   // 玄星紫
        val accentLight = Color.parseColor("#A78BFA")

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(18), dp(10))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(24).toFloat()
                colors = intArrayOf(accent, accentLight)
                orientation = GradientDrawable.Orientation.LEFT_RIGHT
            }
            elevation = dp(6).toFloat()
        }

        // 旋转图标。关键：ProgressBar 的 indeterminate 动画、以及 ObjectAnimator 属性动画都依赖
        // Choreographer 的 vsync 帧回调，App 切到后台/其他 App 时系统会冻结/节流这些帧回调，
        // 表现为"卡住不转"。这里改用 Handler.postDelayed 自驱动 setRotation——由消息队列驱动，
        // 前台服务进程存活期间不受窗口可见性影响，切后台照样转。
        val spinner = ImageView(context).apply {
            setImageResource(R.mipmap.ic_launcher)
            layoutParams = LinearLayout.LayoutParams(dp(20), dp(20)).apply {
                rightMargin = dp(8)
            }
        }

        val text = TextView(context).apply {
            text = "玄星生成中"
            setTextColor(Color.WHITE)
            textSize = 14f
        }

        // Handler 自驱动旋转：约 30fps，每帧转 12°（≈1.4 圈/秒）。attach 时启动、detach 时停（防泄漏）。
        val handler = Handler(Looper.getMainLooper())
        val stepMs = 33L
        val degPerStep = 12f
        val spinRunnable = object : Runnable {
            override fun run() {
                spinner.rotation = (spinner.rotation + degPerStep) % 360f
                handler.postDelayed(this, stepMs)
            }
        }
        spinner.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                handler.removeCallbacks(spinRunnable)
                handler.post(spinRunnable)
            }
            override fun onViewDetachedFromWindow(v: View) {
                handler.removeCallbacks(spinRunnable)
            }
        })

        row.addView(spinner)
        row.addView(text)
        return row
    }
}
