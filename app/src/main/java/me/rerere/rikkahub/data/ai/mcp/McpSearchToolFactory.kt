package me.rerere.rikkahub.data.ai.mcp

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool

/**
 * 懒加载服务器的搜索元工具: 每个懒加载服务器只占一个 function 槽位,
 * 用它把真正需要的工具按需捞进会话。
 *
 * 展开动作通过 [onExpand] 回调交给调用方（ChatService）持久化到会话,
 * 这里不直接依赖会话存储, 保持可测试。
 */
fun createMcpSearchTool(
    lazyServer: McpToolRouter.LazyServer,
    sampleTools: List<McpTool>,
    manager: McpManager,
    expandedTools: Set<String>,
    onExpand: (Set<String>) -> Unit,
): Tool {
    val prefix = "mcp__${lazyServer.serverName}__"
    return Tool(
        name = "${prefix}search_tools",
        description = McpToolRouter.searchToolDescription(
            serverName = lazyServer.serverName,
            sampleTools = sampleTools,
            remaining = lazyServer.availableCount,
        ),
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("query", buildJsonObject {
                        put("type", "string")
                        put(
                            "description",
                            "One or two keywords for the capability you need. Every word must appear in a " +
                                "tool's name or description, so MORE words MEAN FEWER results: prefer a single " +
                                "distinctive word (e.g. 'decompile', 'manifest', 'xref', 'frida') and broaden " +
                                "by removing words if nothing matches. Do not search a whole phrase."
                        )
                    })
                },
                required = listOf("query"),
            )
        },
        needsApproval = { false },
        execute = { arguments ->
            val query = arguments.jsonObject["query"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val (keys, parts) = manager.searchTools(lazyServer.serverId, query, expandedTools)
            if (keys.isNotEmpty()) onExpand(keys)
            parts
        },
    )
}