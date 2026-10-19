package me.rerere.rikkahub.service

import kotlinx.serialization.json.JsonPrimitive
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.CustomBody
import me.rerere.ai.provider.CustomHeader
import me.rerere.ai.provider.Model
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatServiceTest {
    @Test
    fun `background generation params include model custom request configuration`() {
        val headers = listOf(CustomHeader(name = "X-Gateway-Token", value = "test-token"))
        val bodies = listOf(CustomBody(key = "gateway_mode", value = JsonPrimitive("strict")))
        val model = Model(
            modelId = "custom-chat-model",
            customHeaders = headers,
            customBodies = bodies,
        )

        val params = backgroundTextGenerationParams(model)
        assertEquals(model, params.model)
        // 锁住 AUTO：后台生成不发推理参数、由服务端按模型默认处理。
        // 改成 OFF 不是无害的默认值修正——它会给 provider 追加「显式关闭推理」的参数
        // （见 backgroundTextGenerationParams 的 KDoc），纯推理模型可能直接报错。
        // 真要省思维 token，请连同各 provider 分支一起验证后单独改。
        assertEquals(ReasoningLevel.AUTO, params.reasoningLevel)
        assertEquals(headers, params.customHeaders)
        assertEquals(bodies, params.customBody)
    }
}
