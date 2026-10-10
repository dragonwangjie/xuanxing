package me.rerere.rikkahub.data.ai.tools.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import java.net.HttpURLConnection
import java.net.URL

/**
 * 玄星：内置网页抓取工具（不需外部 MCP）。抓取给定 URL 的正文，去除 HTML 标签返回纯文本。
 * 用于让 AI 查资料、读文档页、看接口说明等。
 */
internal fun buildWebFetchTool(): Tool = Tool(
    name = "web_fetch",
    description = """
        Fetch the main text content of a web page by URL. Returns plain text with HTML tags stripped.
        Use this to read documentation, articles, API references, or any public web page.
        Only http/https URLs are supported. Content is truncated to a reasonable length.
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("url", buildJsonObject {
                    put("type", "string")
                    put("description", "The absolute http/https URL to fetch")
                })
                put("maxChars", buildJsonObject {
                    put("type", "integer")
                    put("description", "Max characters of extracted text to return (default 20000)")
                })
            },
            required = listOf("url")
        )
    },
    execute = { arg ->
        val params = arg.jsonObject
        val url = params["url"]?.jsonPrimitive?.contentOrNull?.trim() ?: error("url is required")
        val maxChars = params["maxChars"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()?.coerceIn(1000, 100000) ?: 20000
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            error("Only http/https URLs are supported: $url")
        }
        val result = withContext(Dispatchers.IO) {
            runCatching { fetchAndExtract(url, maxChars) }.getOrElse { e ->
                "[抓取失败: ${e.message ?: e.javaClass.simpleName}]"
            }
        }
        val payload = buildJsonObject {
            put("url", url)
            put("content", result)
        }
        listOf(UIMessagePart.Text(payload.toString()))
    }
)

private fun fetchAndExtract(url: String, maxChars: Int): String {
    val conn = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15000
        readTimeout = 20000
        instanceFollowRedirects = true
        requestMethod = "GET"
        setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36"
        )
        setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
    }
    try {
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val raw = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
        if (code !in 200..299) {
            return "[HTTP $code] ${raw.take(500)}"
        }
        return stripHtml(raw).take(maxChars)
    } finally {
        conn.disconnect()
    }
}

/** 极简 HTML 转纯文本：去 script/style、去标签、解码常见实体、压缩空白。 */
private fun stripHtml(html: String): String {
    var s = html
    s = s.replace(Regex("(?is)<script.*?</script>"), " ")
    s = s.replace(Regex("(?is)<style.*?</style>"), " ")
    s = s.replace(Regex("(?is)<noscript.*?</noscript>"), " ")
    s = s.replace(Regex("(?is)<!--.*?-->"), " ")
    // 块级标签转换行，便于阅读
    s = s.replace(Regex("(?i)<(br|/p|/div|/li|/h[1-6]|/tr)\\s*/?>"), "\n")
    s = s.replace(Regex("(?is)<[^>]+>"), " ")
    // 常见 HTML 实体
    s = s.replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
    // 压缩多余空白
    s = s.replace(Regex("[ \\t\\x0B\\f\\r]+"), " ")
    s = s.replace(Regex("\\n{3,}"), "\n\n")
    return s.trim()
}
