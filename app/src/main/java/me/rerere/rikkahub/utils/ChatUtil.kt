package me.rerere.rikkahub.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.ui.context.Navigator
import kotlin.uuid.Uuid

private const val TAG = "ChatUtil"

fun navigateToChatPage(
    navigator: Navigator,
    chatId: Uuid = Uuid.random(),
    initText: String? = null,
    initFiles: List<Uri> = emptyList(),
    nodeId: Uuid? = null,
) {
    Log.i(TAG, "navigateToChatPage: navigate to $chatId")
    // 池鸳魔改：改为 push（而非清栈），使聊天页叠在当前 tab 之上，
    // 返回时回到来源 tab（首页/对话等），配合底部导航栏。
    navigator.navigate(
        Screen.Chat(
            id = chatId.toString(),
            text = initText,
            files = initFiles.map { it.toString() },
            nodeId = nodeId?.toString(),
        )
    ) {
        launchSingleTop = true
    }
}

fun Context.copyMessageToClipboard(message: UIMessage) {
    this.writeClipboardText(message.toText())
}

private val ALLOWED_MIME_TYPES = setOf(
    "text/plain", "text/html", "text/css", "text/javascript", "text/csv", "text/xml",
    "application/json", "application/javascript", "application/pdf",
    "application/msword",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/vnd.ms-excel",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/vnd.ms-powerpoint",
    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    "application/epub+zip"
)

private val ALLOWED_FILE_EXTENSIONS = setOf(
    "txt", "md", "csv", "json", "js", "jsx", "mjs", "cjs",
    "html", "css", "vue", "svelte", "xml",
    "py", "rb", "lua", "sql", "java", "kt", "ts", "tsx",
    "dart", "php", "swift", "go",
    "bat", "cmd", "ps1", "psm1", "sh", "bash", "zsh", "fish",
    "c", "h", "cpp", "cc", "cxx", "hpp", "hh", "hxx",
    "rs", "cs", "markdown", "mdx",
    "toml", "ini", "env", "gradle", "kts", "properties",
    "proto", "graphql", "gql", "yml", "yaml"
)

// 玄星：逆向分析用的二进制文件扩展名。这些文件不读文本内容（读了是乱码），
// 只把 workspace 路径告诉 AI，让它调 MT 管理器 / SOMCP 等 MCP 工具去分析。
val REVERSE_BINARY_EXTENSIONS = setOf(
    "apk", "aab", "apks", "xapk", "apkm",   // 安卓应用包
    "so", "dex", "odex", "vdex", "oat",     // native 库 / dex
    "jar", "aar", "dylib", "a",             // java / native 库
    "dll", "exe", "sys", "bin", "elf",      // PE / 其它二进制
    "class", "smali"
)

// 玄星：逆向二进制文件常见的 MIME 类型。很多文件管理器给 APK/SO/DEX 返回的
// displayName 不带扩展名（只能靠 MIME 判断），或返回通用的 octet-stream，
// 这里按 MIME 兜底放行，避免"能选中却报不支持"的坑。
val REVERSE_BINARY_MIME_TYPES = setOf(
    "application/vnd.android.package-archive", // apk
    "application/java-archive",                // jar
    "application/x-executable",                // elf/so
    "application/x-sharedlib",                 // so
    "application/x-elf",                       // elf
    "application/x-dex",                       // dex
    "application/octet-stream",                // so/dex/bin 常见通用类型
)

/** 玄星：是否为逆向二进制文件（按扩展名判断）。 */
fun isReverseBinaryFile(fileName: String): Boolean {
    val extension = fileName.substringAfterLast('.', "").lowercase()
    return extension in REVERSE_BINARY_EXTENSIONS
}

/** 玄星：是否为逆向二进制文件（扩展名 + MIME 双判断）。文件名无扩展名时靠 MIME 兜底，
 *  确保这类文件被复制到公共目录供外部 MCP（MT/SOMCP）读取，而不是塞进私有目录。 */
fun isReverseBinaryFile(fileName: String, mime: String): Boolean {
    if (isReverseBinaryFile(fileName)) return true
    val extension = fileName.substringAfterLast('.', "").lowercase()
    // 扩展名拿不到时才用 MIME 兜底（octet-stream 太泛，有扩展名时以扩展名为准）
    return extension.isEmpty() && mime in REVERSE_BINARY_MIME_TYPES
}

fun isAllowedFileType(fileName: String, mime: String): Boolean {
    if (mime in ALLOWED_MIME_TYPES || mime.startsWith("text/")) return true
    val extension = fileName.substringAfterLast('.', "").lowercase()
    // 逆向二进制文件放行（后续按路径交给 MCP 分析，不读文本内容）。
    // 双保险：文件名有逆向扩展名 → 放行；扩展名拿不到但 MIME 命中逆向二进制类型 → 也放行。
    if (extension in ALLOWED_FILE_EXTENSIONS || extension in REVERSE_BINARY_EXTENSIONS) return true
    return mime in REVERSE_BINARY_MIME_TYPES
}
