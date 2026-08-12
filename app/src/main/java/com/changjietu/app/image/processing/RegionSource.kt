package com.changjietu.app.image.processing

import android.graphics.Bitmap

/**
 * 区域解码句柄。来自 BitmapRegionDecoder 的缓冲视为只读（通常 immutable）。
 * 禁止对 [bitmapOrNull] 创建 Canvas；绘制只允许在 [SliceRenderer] 新建的输出 Bitmap 上。
 */
interface RegionHandle : AutoCloseable {
    val width: Int
    val height: Int
    /** false 表示不可写入（典型 region decode） */
    val isMutable: Boolean
    /** Android Bitmap；测试假源可为 null */
    val bitmapOrNull: Bitmap?
}

class BitmapRegionHandle(
    private val bitmap: Bitmap,
) : RegionHandle {
    override val width: Int get() = bitmap.width
    override val height: Int get() = bitmap.height
    override val isMutable: Boolean get() = bitmap.isMutable
    override val bitmapOrNull: Bitmap get() = bitmap
    override fun close() {
        if (!bitmap.isRecycled) bitmap.recycle()
    }
}

/**
 * 分块读取源。禁止假定 decode 结果可写。
 */
interface RegionSource : AutoCloseable {
    val width: Int
    val height: Int
    fun decodeRegion(left: Int, top: Int, right: Int, bottom: Int, sampleSize: Int = 1): RegionHandle?
}
