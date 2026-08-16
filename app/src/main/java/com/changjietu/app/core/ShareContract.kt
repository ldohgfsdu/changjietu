package com.changjietu.app.core

// 外部分享进入契约：把「外部 App 分享进来的一张图」解析为 App 要处理的单一输入。
// 纯逻辑、无 Android 依赖（Uri 以字符串传递），便于 JVM 单测覆盖 malformed /
// unsupported / null / SEND vs SEND_MULTIPLE 的最终契约。
// 多图契约（MVP）：外部 ACTION_SEND_MULTIPLE 仅取第一张，不做多图拼接。
object ShareContract {

    const val ACTION_SEND = "android.intent.action.SEND"
    const val ACTION_SEND_MULTIPLE = "android.intent.action.SEND_MULTIPLE"

    // 分享解析结果：要么得到一个待处理的图 URI，要么整体无效（不 crash、不误判为正常源）。
    sealed interface Input {
        data class Single(val uri: String) : Input
        data object Invalid : Input
    }

    // 是否属于图片 MIME。Manifest 过滤器限定 image/*；此处做防御性二次校验。
    fun isImageMime(mime: String?): Boolean =
        mime?.equals("image/*", ignoreCase = true) == true ||
            mime?.startsWith("image/", ignoreCase = true) == true

    // 依据 action / mime / streamUris 解析出唯一输入。
    // - ACTION_SEND：取 EXTRA_STREAM 单张。
    // - ACTION_SEND_MULTIPLE：取列表第一张（MVP 最终契约）。
    // - 空流、非图片 MIME、空 URI、未知 action → Invalid（graceful fail）。
    // @param streams 已从 Intent 提取的 URI 字符串列表；null/空白元素视为无效。
    fun resolve(action: String?, mime: String?, streams: List<String?>): Input {
        if (!isImageMime(mime)) return Input.Invalid
        return when (action) {
            ACTION_SEND, ACTION_SEND_MULTIPLE -> {
                val uri = streams.firstOrNull()?.takeIf { !it.isNullOrBlank() }
                if (uri == null) Input.Invalid else Input.Single(uri)
            }
            else -> Input.Invalid
        }
    }
}
