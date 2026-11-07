package com.changjietu.app.core

/**
 * 手动切线与裁边解耦的纯几何（JVM 可测）。
 * 手动切线统一以原图（source）Y 坐标存储，裁边只改变 contentTop/contentHeight 映射，
 * 切换裁边、修改左右裁边都不改动 source 切点。
 */
object SourceCutGeometry {
    fun sourceFromContent(contentCut: Int, contentTop: Int): Int = contentCut + contentTop

    fun contentCutsFromSource(
        manualSource: List<Int>,
        contentTop: Int,
        contentHeight: Int,
        minSlicePx: Int = 50,
    ): List<Int> = manualSource
        .map { it - contentTop }
        .filter { it in minSlicePx..contentHeight }
        .distinct()
        .sorted()

    fun conflicts(
        manualSource: List<Int>,
        contentTop: Int,
        contentHeight: Int,
        minSlicePx: Int = 50,
    ): Boolean {
        if (manualSource.isEmpty()) return false
        val contentCuts = contentCutsFromSource(manualSource, contentTop, contentHeight, minSlicePx)
        return contentCuts.size != manualSource.distinct().size
    }
}
