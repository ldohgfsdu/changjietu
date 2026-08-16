package com.changjietu.app.core

import android.content.Intent

/**
 * 决定是否对收到的 Uri 调用 `takePersistableUriPermission` 获取长期只读权限。
 *
 * 不同来源的 Intent grant 语义不同，因此依据运行时 Intent flags 判定，而不是
 * 假设所有来源都支持 persistable grant：
 *
 * - Photo Picker（`PickVisualMedia`）只授予临时读权限、**不带** PERSISTABLE 标志，
 *   不得假设其 URI 永久有效 → 不持久化，失效走 stale 恢复路径。
 * - `ACTION_OPEN_DOCUMENT` / 支持 persistable 的分享提供方会同时带
 *   `FLAG_GRANT_PERSISTABLE_URI_PERMISSION` → 尝试长期只读。
 * - 即便标志齐备，仍可能因提供方不支持而抛异常，调用处需 catch 并回退到临时访问。
 *
 * 只申请 READ 权限，绝不请求写权限。
 */
object UriPersistencePolicy {

    /**
     * 是否应尝试获取长期只读权限。只有同时授予「读取」和「可持久化」才尝试。
     */
    fun shouldTakePersistableRead(flags: Int): Boolean {
        val readGranted = (flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0
        val persistableGranted = (flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) != 0
        return readGranted && persistableGranted
    }
}
