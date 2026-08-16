package com.changjietu.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareContractTest {

    private fun single(action: String?, mime: String?, streams: List<String?>): ShareContract.Input =
        ShareContract.resolve(action, mime, streams)

    @Test
    fun actionSend_validImage_resolvesSingleUri() {
        val input = single(ShareContract.ACTION_SEND, "image/png", listOf("content://img/1"))
        assertEquals(ShareContract.Input.Single("content://img/1"), input)
    }

    @Test
    fun actionSend_imageWildcardMime_resolves() {
        val input = single(ShareContract.ACTION_SEND, "image/*", listOf("content://img/1"))
        assertTrue(input is ShareContract.Input.Single)
    }

    @Test
    fun actionSend_nullStream_isInvalid() {
        assertSame(ShareContract.Input.Invalid, single(ShareContract.ACTION_SEND, "image/png", listOf(null)))
    }

    @Test
    fun actionSend_emptyStream_isInvalid() {
        assertSame(ShareContract.Input.Invalid, single(ShareContract.ACTION_SEND, "image/png", emptyList()))
    }

    @Test
    fun actionSend_blankStream_isInvalid() {
        assertSame(ShareContract.Input.Invalid, single(ShareContract.ACTION_SEND, "image/png", listOf("   ")))
    }

    @Test
    fun unsupportedMime_isInvalid() {
        assertSame(
            ShareContract.Input.Invalid,
            single(ShareContract.ACTION_SEND, "text/plain", listOf("content://img/1")),
        )
    }

    @Test
    fun nullMime_isInvalid() {
        assertSame(
            ShareContract.Input.Invalid,
            single(ShareContract.ACTION_SEND, null, listOf("content://img/1")),
        )
    }

    @Test
    fun unknownAction_isInvalid() {
        assertSame(
            ShareContract.Input.Invalid,
            single("android.intent.action.MAIN", "image/png", listOf("content://img/1")),
        )
    }

    @Test
    fun nullAction_isInvalid() {
        assertSame(ShareContract.Input.Invalid, single(null, "image/png", listOf("content://img/1")))
    }

    @Test
    fun actionSendMultiple_takesFirstImage_contract() {
        val input = single(
            ShareContract.ACTION_SEND_MULTIPLE,
            "image/*",
            listOf("content://img/1", "content://img/2"),
        )
        assertEquals(ShareContract.Input.Single("content://img/1"), input)
    }

    @Test
    fun actionSendMultiple_leadingBlankFirst_isInvalid_firstImageMissing() {
        // 契约：只取第一张。若第一张缺失（null/空白），则视为无效，不跳到后续图片。
        assertSame(
            ShareContract.Input.Invalid,
            single(ShareContract.ACTION_SEND_MULTIPLE, "image/*", listOf(null, "content://img/2")),
        )
    }

    @Test
    fun actionSendMultiple_emptyStream_isInvalid() {
        assertSame(
            ShareContract.Input.Invalid,
            single(ShareContract.ACTION_SEND_MULTIPLE, "image/*", emptyList()),
        )
    }

    @Test
    fun resolve_isDeterministic_sameInputSameOutput() {
        val streams = listOf("content://img/1", "content://img/2")
        val a = single(ShareContract.ACTION_SEND_MULTIPLE, "image/*", streams)
        val b = single(ShareContract.ACTION_SEND_MULTIPLE, "image/*", streams)
        assertEquals(a, b)
    }

    @Test
    fun isImageMime_trueForImageTypes() {
        assertTrue(ShareContract.isImageMime("image/png"))
        assertTrue(ShareContract.isImageMime("image/*"))
        assertTrue(ShareContract.isImageMime("image/jpeg"))
    }

    @Test
    fun isImageMime_falseForNonImageAndNull() {
        org.junit.Assert.assertFalse(ShareContract.isImageMime("text/plain"))
        org.junit.Assert.assertFalse(ShareContract.isImageMime(""))
        org.junit.Assert.assertFalse(ShareContract.isImageMime(null))
    }
}
