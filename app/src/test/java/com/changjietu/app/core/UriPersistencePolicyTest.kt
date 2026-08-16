package com.changjietu.app.core

import android.content.Intent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UriPersistencePolicyTest {

    private fun shareFlags(read: Boolean, persistable: Boolean): Int {
        var f = 0
        if (read) f = f or Intent.FLAG_GRANT_READ_URI_PERMISSION
        if (persistable) f = f or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        return f
    }

    // ---- USER_PICKER（Photo Picker）----

    @Test
    fun picker_persists_evenWithoutPersistableFlagInput() {
        // 官方 Photo Picker：回调只有 URI、无可依赖的 persistable flags，
        // 应直接尝试 takePersistableUriPermission(READ)。flags=0 也要尝试。
        assertTrue(UriPersistencePolicy.shouldAttemptPersistRead(UriAccessSource.USER_PICKER, 0))
    }

    @Test
    fun picker_persists_withReadFlag() {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        assertTrue(UriPersistencePolicy.shouldAttemptPersistRead(UriAccessSource.USER_PICKER, flags))
    }

    @Test
    fun picker_persists_withWriteOnly() {
        // 即便 flags 只有 WRITE（Picker 不该有），USER_PICKER 仍应尝试 READ 持久化。
        val flags = Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        assertTrue(UriPersistencePolicy.shouldAttemptPersistRead(UriAccessSource.USER_PICKER, flags))
    }

    // ---- RECENT ----

    @Test
    fun recent_persists_evenWithoutFlags() {
        assertTrue(UriPersistencePolicy.shouldAttemptPersistRead(UriAccessSource.RECENT, 0))
    }

    // ---- EXTERNAL_SHARE ----

    @Test
    fun externalShare_persists_onlyWhenReadAndPersistable() {
        val flags = shareFlags(read = true, persistable = true)
        assertTrue(UriPersistencePolicy.shouldAttemptPersistRead(UriAccessSource.EXTERNAL_SHARE, flags))
    }

    @Test
    fun externalShare_isTemporary_whenPersistableFlagMissing() {
        // 仅有临时 READ（如部分分享提供方），不得视为可持久化。
        val flags = shareFlags(read = true, persistable = false)
        assertFalse(UriPersistencePolicy.shouldAttemptPersistRead(UriAccessSource.EXTERNAL_SHARE, flags))
    }

    @Test
    fun externalShare_isTemporary_whenOnlyPersistableNoRead() {
        val flags = shareFlags(read = false, persistable = true)
        assertFalse(UriPersistencePolicy.shouldAttemptPersistRead(UriAccessSource.EXTERNAL_SHARE, flags))
    }

    @Test
    fun externalShare_isTemporary_whenNoFlags() {
        assertFalse(UriPersistencePolicy.shouldAttemptPersistRead(UriAccessSource.EXTERNAL_SHARE, 0))
    }

    @Test
    fun externalShare_isTemporary_withWriteOnly() {
        // 写权限不参与判断，也不应据此请求持久化。
        val flags = Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        assertFalse(UriPersistencePolicy.shouldAttemptPersistRead(UriAccessSource.EXTERNAL_SHARE, flags))
    }

    @Test
    fun policy_neverRequestsWriteByPolicyShape() {
        // 契约只暴露 READ 持久化判定；调用方仅用 FLAG_GRANT_READ_URI_PERMISSION。
        // 此断言保证 EXTERNAL_SHARE 的判定不因 WRITE flag 而放宽。
        assertFalse(
            UriPersistencePolicy.shouldAttemptPersistRead(
                UriAccessSource.EXTERNAL_SHARE,
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            ),
        )
    }
}
