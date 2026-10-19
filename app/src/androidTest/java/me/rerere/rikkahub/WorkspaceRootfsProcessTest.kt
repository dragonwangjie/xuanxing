package me.rerere.rikkahub

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.rerere.workspace.ProotShellRunner
import me.rerere.workspace.RootfsInstaller
import me.rerere.workspace.WorkspaceManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Rootfs（proot）路径的长驻进程验证。
 *
 * 与 WorkspaceMcpStdioTest 的分工: 那边覆盖「Rootfs 留空、直接跑设备上可执行文件」的
 * 宿主路径; 这里覆盖 MCP 配置指向 rootfs 内解释器（如 python）时走的 proot 路径。
 * 两条路径的进程构造方式完全不同（裸 ProcessBuilder vs proot + `env -i` + 挂载表）,
 * 宿主路径通过不代表 proot 路径可用。
 *
 * 为什么不做「写一帧立刻读一帧」: rootfs 内是 musl/busybox, stdout 是块缓冲, 子进程的
 * 输出要到退出才 flush（宿主路径的 Android mksh 会及时 flush, 所以那边才能逐帧读写）。
 * 真实 MCP server（python/node）自己会 flush。本测试要证明的是 proot 通道本身能双向
 * 传数据、不吞帧、不掺入多余输出, 以及 env 注入这个 rootfs 路径独有的坑。
 */
@RunWith(AndroidJUnit4::class)
class WorkspaceRootfsProcessTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun manager(): WorkspaceManager = WorkspaceManager(
        baseDir = File(context.filesDir, "workspaces"),
        // 与生产 RepositoryModule 的装配一致: proot 二进制取自 APK 的 nativeLibraryDir
        shellRunner = ProotShellRunner(
            nativeLibraryDir = File(context.applicationInfo.nativeLibraryDir),
        ),
    )

    /**
     * 允许用 `-e rootfsUrl ...` 覆盖镜像来源。
     *
     * 本机验证时可配 `adb reverse tcp:PORT tcp:PORT` 后指向宿主的 http://127.0.0.1:PORT/...,
     * 免得被公网抖动干扰（ubuntu-base 有 30MB, 跨境链路经常中断, 会让测试结果不可信）。
     * App 的 manifest 已开 usesCleartextTraffic, 所以 http 可用。
     */
    private fun rootfsUrl(): String =
        InstrumentationRegistry.getArguments().getString("rootfsUrl")?.takeIf { it.isNotBlank() }
            ?: productionRootfsUrl()

    /**
     * 与生产 WorkspaceDetailPage 的 DEFAULT_ROOTFS_URL 同一份镜像（ubuntu-base, 自带 bash）。
     *
     * 不能用 Alpine minirootfs: buildCommand 无条件 exec /bin/bash, 而 minirootfs 只有
     * busybox ash, 用它测出来的失败与生产无关。
     */
    private fun productionRootfsUrl(): String {
        val arch = when (val abi = android.os.Build.SUPPORTED_ABIS.firstOrNull().orEmpty()) {
            "arm64-v8a" -> "arm64"
            "x86_64" -> "amd64"
            else -> throw UnsupportedOperationException("No ubuntu-base image for abi=$abi")
        }
        return "https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/" +
            "ubuntu-base-24.04.3-base-$arch.tar.gz"
    }

    /** 复用已装好的 rootfs, 避免每次重下 ~30MB; 网络不可达时跳过而不是失败。 */
    private fun ensureRootfs(manager: WorkspaceManager, root: String) {
        if (manager.hasRootfs(root)) return
        val url = rootfsUrl()
        Assume.assumeTrue("rootfs CDN unreachable, skipping", isReachable(url))
        manager.ensureWorkspace(root)
        RootfsInstaller(manager).install(root, url)
        assertTrue("rootfs install did not produce bin/sh", manager.hasRootfs(root))
    }

    private fun isReachable(url: String): Boolean = runCatching {
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "HEAD"
            connectTimeout = 8_000
            readTimeout = 8_000
        }.responseCode.let { it in 200..299 }
    }.getOrDefault(false)

    /** proot 二进制必须随 APK 装进 nativeLibraryDir, 否则 rootfs 一切功能都是空谈。 */
    @Test
    fun prootBinariesArePackaged() {
        val dir = File(context.applicationInfo.nativeLibraryDir)
        assertTrue(
            "libproot_exec.so missing in ${dir.absolutePath}",
            File(dir, "libproot_exec.so").isFile,
        )
        assertTrue(
            "libproot_loader.so missing in ${dir.absolutePath}",
            File(dir, "libproot_loader.so").isFile,
        )
    }

    /**
     * proot 内子进程能拿到 extraEnv。
     *
     * 回归点: buildCommand 用 `env -i` 起 shell, proot 内的环境被清空, 所以 extraEnv
     * 必须并入同一个 env 调用; 早先往 ProcessBuilder 塞变量的写法在 rootfs 路径上
     * 完全无效, 表现为 MCP 服务器配置的 env 静默失效。
     */
    @Test(timeout = 300_000)
    fun extraEnvReachesProotChild() {
        val manager = manager()
        val root = "mcp_rootfs_test"
        ensureRootfs(manager, root)

        val process = manager.launchProcess(
            root = root,
            command = "printf '[%s]' \"\$XUANXING_PROBE\"",
            extraEnv = listOf("XUANXING_PROBE" to "probe-value"),
        )
        try {
            process.stdin.close()
            val out = process.stdout.readBytes().decodeToString()
            assertEquals("[probe-value]", out)
        } finally {
            process.destroy()
        }
    }

    /**
     * 三帧输入 → 三帧输出, 不多不少。
     *
     * 精确断言行列表而非「包含」: 多出来的行意味着 proot 内混进了 profile/motd 之类的
     * 输出, 会直接破坏换行分隔的 JSON-RPC 分帧 —— 这正是长驻进程必须 loginShell=false
     * 的原因, 一旦发生就是「服务器无响应」级别、极难定位的故障。
     */
    @Test(timeout = 300_000)
    fun rootfsProcessExchangesFramesOverStdio() {
        val manager = manager()
        val root = "mcp_rootfs_test"
        ensureRootfs(manager, root)

        // 必须用换行拼接: shell 的 while/do/done 是行级语法, 用 "; " 拼会得 do; 直接语法错误
        val script = listOf(
            "i=0",
            "while IFS= read -r line; do",
            "  i=\$((i+1))",
            "  printf 'got-%s:%s\\n' \"\$i\" \"\$line\"",
            "done",
            "printf 'seen:%s\\n' \"\$i\"",
        ).joinToString("\n")

        val process = manager.launchProcess(root = root, command = script)
        try {
            process.stdin.write("frame-1\nframe-2\nframe-3\n".toByteArray())
            process.stdin.flush()
            process.stdin.close() // EOF → rootfs 内循环结束并 flush

            val lines = process.stdout.readBytes().decodeToString()
                .split('\n').filter { it.isNotBlank() }

            assertEquals(
                "unexpected stdout framing: $lines",
                listOf("got-1:frame-1", "got-2:frame-2", "got-3:frame-3", "seen:3"),
                lines,
            )
        } finally {
            process.destroy()
        }
        assertTrue("proot process survived destroy", !process.isAlive)
    }
}