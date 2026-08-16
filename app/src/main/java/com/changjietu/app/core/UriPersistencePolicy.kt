package com.changjietu.app.core

import android.content.Intent

/** URI 的获取来源，决定持久化读权限策略。 */
enum class UriAccessSource {
    /** 用户通过 Photo Picker / PickVisualMedia 选择，或底层 fallback ACTION_OPEN_DOCUMENT。 */
    USER_PICKER,
    /** 外部 ACTION_SEND / ACTION_SEND_MULTIPLE 分享进入。 */
    EXTERNAL_SHARE,
    /** 从「最近处理」历史打开。 */
    RECENT,
}

/**
 * 决定是否对该 Uri 尝试取得长期只读权限（`takePersistableUriPermission`，READ only）。
 *
 * 不同来源的 grant 语义不同，因此采用 **source-aware** 契约而非仅看 Intent flags：
 *
 * - [UriAccessSource.USER_PICKER]：官方 Photo Picker 契约明确允许并在需要长期访问时
 *   要求对返回 URI 直接调用 `takePersistableUriPermission(uri, READ)`；
 *   `PickVisualMedia` 回调只有 URI、没有可依赖的 persistable flags，
 *   fallback `ACTION_OPEN_DOCUMENT` 同样支持。→ **无条件尝试 READ**（失败 graceful 回退）。
 * - [UriAccessSource.RECENT]：来自用户选择的 URI，同样尝试长期 READ。
 * - [UriAccessSource.EXTERNAL_SHARE]：不得假设 grant 可永久持有，仅当 Intent flags 同时
 *   含 READ + PERSISTABLE（来源 contract 真正允许）才尝试，否则按 temporary capability 处理。
 *
 * 只申请 READ 权限，绝不请求 WRITE。调用方 catch 任何异常并回退为临时访问，
 * 不把 temporary grant 标记为已持久化。
 */
object UriPersistencePolicy {

    /**
     * 是否应尝试取得长期只读权限。
     *
     * @param source URI 来源。
     * @param flags 仅对 [UriAccessSource.EXTERNAL_SHARE] 有意义（外部 Intent 的 grant flags）；
     *              对 USER_PICKER / RECENT 忽略（其契约不依赖该 flags）。
     */
    fun shouldAttemptPersistRead(source: UriAccessSource, flags: Int = 0): Boolean =
        when (source) {
            UriAccessSource.USER_PICKER, UriAccessSource.RECENT -> true
            UriAccessSource.EXTERNAL_SHARE ->
                (flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0 &&
                    (flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) != 0
        }
}
