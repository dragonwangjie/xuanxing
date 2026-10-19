package me.rerere.rikkahub.data.datastore

import me.rerere.ai.provider.ProviderSetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultProvidersTest {
    @Test
    fun `default providers should include vercel ai gateway with expected balance config`() {
        val vercelProviders = DEFAULT_PROVIDERS
            .filterIsInstance<ProviderSetting.OpenAI>()
            .filter { it.name == "Vercel AI Gateway" }

        assertEquals(1, vercelProviders.size)

        val provider = vercelProviders.single()
        assertEquals("https://ai-gateway.vercel.sh/v1", provider.baseUrl)
        // 默认启用：它和列表里其余 20+ 个商业网关一样是可直接填 Key 就用的服务；
        // 默认禁用的只有那个带说明注释的「内置(需自填Key)」（原官方免费源，会 402/429）。
        assertTrue(provider.enabled)
        assertTrue(provider.builtIn)
        assertTrue(provider.balanceOption.enabled)
        assertEquals("/credits", provider.balanceOption.apiPath)
        assertEquals("balance", provider.balanceOption.resultPath)
    }
}
