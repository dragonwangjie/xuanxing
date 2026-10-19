package me.rerere.rikkahub.data.ai.transformers

import kotlinx.datetime.LocalDateTime
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * applyTimeReminder 的注入形状。
 *
 * 两条独立规则，都会影响消息条数与下标，所以这里用「按内容定位」而不是裸下标：
 *  1) 首条用户消息前无条件注入一条「当前时间」（不带 since last message）——
 *     目的是让 AI 在任何会话里都知道现在几点，与时间间隔无关；
 *  2) 之后只有与上一条消息间隔 > 1 小时才注入，并带上间隔文案。
 * 因此注入后顺序是 [R, msg1, R?, msg2, R?, ...]，msg1 的下标恒为 1。
 */
class TimeReminderTransformerTest {

    private fun userMessage(text: String, createdAt: LocalDateTime) = UIMessage(
        role = MessageRole.USER,
        parts = listOf(UIMessagePart.Text(text)),
        createdAt = createdAt,
    )

    private fun getMessageText(msg: UIMessage): String =
        msg.parts.filterIsInstance<UIMessagePart.Text>().joinToString("") { it.text }

    private fun reminderTexts(messages: List<UIMessage>): List<String> =
        messages.map { getMessageText(it) }.filter { it.contains("<time_reminder>") }

    @Test
    fun `first user message always gets a current-time reminder`() {
        val messages = listOf(userMessage("Hello", LocalDateTime(2026, 2, 22, 10, 0, 0)))
        val result = applyTimeReminder(messages)

        assertEquals(2, result.size)
        // 首条注入只报当前时间, 没有「距上一条」的概念
        val injected = getMessageText(result[0])
        assertTrue(injected.contains("<time_reminder>"))
        assertFalse(injected.contains("since last message"))
        assertEquals("Hello", getMessageText(result[1]))
    }

    @Test
    fun `gap less than 1 hour should not inject a gap reminder`() {
        val messages = listOf(
            userMessage("Hello", LocalDateTime(2026, 2, 22, 10, 0, 0)),
            userMessage("World", LocalDateTime(2026, 2, 22, 10, 30, 0)), // 30 分钟
        )
        val result = applyTimeReminder(messages)

        // 只有首条那一条注入
        assertEquals(3, result.size)
        assertEquals(1, reminderTexts(result).size)
    }

    @Test
    fun `gap exactly 1 hour should not inject a gap reminder`() {
        val messages = listOf(
            userMessage("Hello", LocalDateTime(2026, 2, 22, 10, 0, 0)),
            userMessage("World", LocalDateTime(2026, 2, 22, 11, 0, 0)), // 恰好 1 小时
        )
        val result = applyTimeReminder(messages)

        // 阈值是严格大于, 恰好 3600s 不触发
        assertEquals(3, result.size)
        assertEquals(1, reminderTexts(result).size)
    }

    @Test
    fun `gap more than 1 hour injects before the second message`() {
        val messages = listOf(
            userMessage("Hello", LocalDateTime(2026, 2, 22, 10, 0, 0)),
            userMessage("World", LocalDateTime(2026, 2, 22, 12, 0, 0)), // 2 小时
        )
        val result = applyTimeReminder(messages)

        assertEquals(4, result.size)
        assertEquals(listOf("Hello", "World"), listOf(getMessageText(result[1]), getMessageText(result[3])))
        // 第二条注入紧贴它自己的消息之前, 且带间隔文案
        val gapReminder = getMessageText(result[2])
        assertTrue(gapReminder.contains("<time_reminder>"))
        assertTrue(gapReminder.contains("2 h since last message"))
    }

    @Test
    fun `injected gap reminder contains a comma and the gap in hours`() {
        val messages = listOf(
            userMessage("Hello", LocalDateTime(2026, 2, 22, 10, 0, 0)),
            userMessage("World", LocalDateTime(2026, 2, 22, 12, 0, 0)),
        )
        val injected = reminderTexts(applyTimeReminder(messages)).last()

        // 星期几与时间之间用逗号分隔；不断言具体星期名（依赖 Locale）
        assertTrue(injected.contains(","))
        assertTrue(injected.contains("2 h since last message"))
    }

    @Test
    fun `gap in days should format as days`() {
        val messages = listOf(
            userMessage("Hello", LocalDateTime(2026, 2, 20, 10, 0, 0)),
            userMessage("World", LocalDateTime(2026, 2, 22, 10, 0, 0)), // 2 天
        )
        val injected = reminderTexts(applyTimeReminder(messages)).last()

        assertTrue(injected.contains("2 d since last message"))
    }

    @Test
    fun `multiple large gaps inject one reminder per gap`() {
        val messages = listOf(
            userMessage("Msg 1", LocalDateTime(2026, 2, 20, 10, 0, 0)),
            userMessage("Msg 2", LocalDateTime(2026, 2, 21, 10, 0, 0)), // 1 天
            userMessage("Msg 3", LocalDateTime(2026, 2, 22, 10, 0, 0)), // 1 天
        )
        val result = applyTimeReminder(messages)

        // 3 条原始 + 3 条注入（首条当前时间 + 两个间隔）
        assertEquals(6, result.size)
        assertEquals(3, reminderTexts(result).size)
        assertEquals(
            listOf("Msg 1", "Msg 2", "Msg 3"),
            listOf(getMessageText(result[1]), getMessageText(result[3]), getMessageText(result[5])),
        )
    }

    @Test
    fun `empty messages should return empty`() {
        assertEquals(0, applyTimeReminder(emptyList()).size)
    }
}