package me.rerere.rikkahub.data.ai.transformers

import androidx.core.net.toFile
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.document.DocxParser
import me.rerere.document.EpubParser
import me.rerere.document.PdfParser
import me.rerere.document.PptxParser
import me.rerere.rikkahub.utils.isReverseBinaryFile
import java.io.File

object DocumentAsPromptTransformer : InputMessageTransformer {
    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        return withContext(Dispatchers.IO) {
            messages.map { message ->
                message.copy(
                    parts = message.parts.toMutableList().apply {
                        val documents = filterIsInstance<UIMessagePart.Document>()
                        if (documents.isNotEmpty()) {
                            documents.forEach { document ->
                                val path = resolveWorkspacePath(document)
                                val absPath = runCatching { document.url.toUri().toFile().absolutePath }.getOrNull()
                                // 玄星：逆向二进制文件（APK/SO/DEX 等）不读文本内容（读了是乱码），
                                // 只把路径交给 AI，让它调 MT 管理器 / SOMCP 等 MCP 工具分析。
                                val prompt = if (isReverseBinaryFile(document.fileName)) {
                                    val pathHint = path ?: absPath ?: document.url
                                    """
                                      用户上传了一个逆向分析用的二进制文件，请勿尝试把它当文本读取。
                                      <ReverseFile name="${document.fileName}" mime="${document.mime}" path="$pathHint" />
                                      请根据文件类型主动调用对应 MCP 工具分析它：
                                      - .apk/.aab/.xapk 等 → 用 MT 管理器（mt_apk_open，参数 path 用上面的路径；纯分析用 temporary=true，完成后 mt_apk_close）。
                                      - .so/.dylib/.a 等 native 库 → 用 SOMCP（so_open，参数用上面的路径）。
                                      - .dex/.jar/.class/.smali → 视情况用 MT 管理器或反编译相关技能。
                                      先说明你打算怎么分析，再开始调用工具。
                                      """.trimIndent()
                                } else {
                                    val content = readDocumentContent(document)
                                    val pathAttr = path?.let { " path=\"$it\"" } ?: ""
                                    """
                                      <UploadFile name="${document.fileName}"$pathAttr>
                                      ```
                                      $content
                                      ```
                                      </UploadFile>
                                      """.trimMargin()
                                }
                                add(0, UIMessagePart.Text(prompt))
                            }
                        }
                    }
                )
            }
        }
    }

    private fun parsePdfAsText(file: File): String {
        return PdfParser.parserPdf(file)
    }

    private fun parseDocxAsText(file: File): String {
        return DocxParser.parse(file)
    }

    private fun parsePptxAsText(file: File): String {
        return PptxParser.parse(file)
    }

    private fun parseEpubAsText(file: File): String {
        return EpubParser.parse(file)
    }

    // 上传文件保存在 filesDir/upload 下, 该目录通过 proot 挂载到 workspace 的 /upload
    // 返回文件在 workspace 内的绝对路径, 便于 AI 用 workspace 工具直接读取原始文件
    private fun resolveWorkspacePath(document: UIMessagePart.Document): String? {
        val file = runCatching { document.url.toUri().toFile() }.getOrNull() ?: return null
        if (file.parentFile?.name != "upload") return null
        return "/upload/${file.name}"
    }

    private fun readDocumentContent(document: UIMessagePart.Document): String {
        val file = runCatching { document.url.toUri().toFile() }.getOrNull()
            ?: return "[ERROR, invalid file uri: ${document.fileName}]"
        if (!file.exists() || !file.isFile) {
            return "[ERROR, file not found: ${document.fileName}]"
        }
        return runCatching {
            when (document.mime) {
                "application/pdf" -> parsePdfAsText(file)
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> parseDocxAsText(file)
                "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> parsePptxAsText(file)
                "application/epub+zip" -> parseEpubAsText(file)
                else -> file.readText()
            }
        }.getOrElse {
            "[ERROR, failed to read file: ${document.fileName}]"
        }
    }
}
