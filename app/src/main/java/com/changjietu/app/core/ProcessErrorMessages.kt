package com.changjietu.app.core

import android.util.Log

/**
 * 处理失败时 UI 只展示友好中文；完整堆栈进 Logcat。
 */
object ProcessErrorMessages {
    const val TAG = "Changjietu"

    const val OPEN_FAILED = "源图片不可用或权限已失效，请从相册重新选择图片。"
    const val INVALID_SHARE = "未收到有效的图片，请从相册重新选择。"
    const val ANALYZE_FAILED = "图片分析失败，请重试或换一张图。"
    const val PROCESS_FAILED = "切片处理失败，请调整参数后重试。"
    const val REPROCESS_FAILED = "重新切片失败，请重试。"
    const val EMPTY_SLICES = "未能生成切片，请检查图片后重试。"
    const val SAVE_FAILED = "保存失败，请检查存储权限后重试。"
    const val ZIP_FAILED = "ZIP 打包失败，请检查存储权限后重试。"
    const val NO_SELECTION = "请至少选择一张切片。"
    const val SHARE_EMPTY = "暂无切片可分享。"

    fun logAndFriendly(e: Throwable, friendly: String, tag: String = TAG): String {
        Log.e(tag, friendly, e)
        return friendly
    }

    /** 若异常信息像 JVM/Bitmap 堆栈原文，则改用友好文案。 */
    fun sanitizeForUi(raw: String?, fallback: String): String {
        if (raw.isNullOrBlank()) return fallback
        val looksTechnical =
            raw.contains("Exception", ignoreCase = true) ||
                raw.contains("Error:", ignoreCase = true) ||
                raw.contains("Bitmap", ignoreCase = true) ||
                raw.contains("Canvas", ignoreCase = true) ||
                raw.contains("at com.", ignoreCase = true) ||
                raw.contains("Immutable", ignoreCase = true) ||
                raw.contains("\n") ||
                raw.length > 80
        return if (looksTechnical) fallback else raw
    }
}
