package com.changjietu.app.core

import android.content.Intent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UriPersistencePolicyTest {

    @Test
    fun persistable_permission_success_when_readAndPersistableGranted() {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        assertTrue(UriPersistencePolicy.shouldTakePersistableRead(flags))
    }

    @Test
    fun persistable_unavailable_whenPersistableFlagMissing() {
        // Photo Picker：仅临时读权限，无 PERSISTABLE 标志
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        assertFalse(UriPersistencePolicy.shouldTakePersistableRead(flags))
    }

    @Test
    fun persistable_unavailable_whenReadFlagMissing() {
        val flags = Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        assertFalse(UriPersistencePolicy.shouldTakePersistableRead(flags))
    }

    @Test
    fun persistable_unavailable_whenNoFlags() {
        assertFalse(UriPersistencePolicy.shouldTakePersistableRead(0))
    }

    @Test
    fun persistable_unavailable_withWriteOnly() {
        // 即便有 WRITE 标志，也绝不据此请求持久化（只申请 READ）
        val flags = Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        assertFalse(UriPersistencePolicy.shouldTakePersistableRead(flags))
    }
}
