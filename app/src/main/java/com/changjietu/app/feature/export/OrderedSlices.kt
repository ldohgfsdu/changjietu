package com.changjietu.app.feature.export

import com.changjietu.app.image.processing.SliceOutput

/**
 * 分享、相册、ZIP 共用一个确定性的原图阅读顺序。
 * 不依赖 HashSet / 选择顺序 / 文件系统的 lastModified。
 * 注意：Android 接收应用仍可以自行重排 ACTION_SEND_MULTIPLE 的图片。
 */
object OrderedSlices {
    fun ascending(slices: List<SliceOutput>): List<SliceOutput> =
        slices.sortedWith(compareBy<SliceOutput> { it.startY }.thenBy { it.index })
}
