package com.changjietu.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessErrorMessagesTest {

    @Test
    fun sanitize_hides_canvas_immutable_exception_text() {
        val raw = "Immutable bitmap passed to Canvas constructor"
        val ui = ProcessErrorMessages.sanitizeForUi(raw, ProcessErrorMessages.PROCESS_FAILED)
        assertEquals(ProcessErrorMessages.PROCESS_FAILED, ui)
        assertFalse(ui.contains("Canvas"))
    }

    @Test
    fun sanitize_keeps_short_product_message() {
        val ui = ProcessErrorMessages.sanitizeForUi("参数无效", ProcessErrorMessages.PROCESS_FAILED)
        assertEquals("参数无效", ui)
    }

    @Test
    fun friendly_constants_are_chinese_user_facing() {
        assertTrue(ProcessErrorMessages.PROCESS_FAILED.contains("重试"))
        assertTrue(ProcessErrorMessages.ANALYZE_FAILED.contains("图片"))
    }
}
