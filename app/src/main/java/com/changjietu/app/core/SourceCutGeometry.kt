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
    ): List<Int> = visibleSource(manualSource, contentTop, contentHeight, minSlicePx)
        .map { it - contentTop }

    /** 界面上实际可见的 source 切点（保留原顺序，隐藏点不参与索引）。 */
    fun visibleSource(
        manualSource: List<Int>,
        contentTop: Int,
        contentHeight: Int,
        minSlicePx: Int = 50,
    ): List<Int> = manualSource.filter { (it - contentTop) in minSlicePx..contentHeight }

    /** 把可见索引映射回全列表下标；无对应时返回 -1。 */
    fun fullIndexOfVisible(
        manualSource: List<Int>,
        visibleIndex: Int,
        contentTop: Int,
        contentHeight: Int,
        minSlicePx: Int = 50,
    ): Int {
        val visible = visibleSource(manualSource, contentTop, contentHeight, minSlicePx)
        if (visibleIndex !in visible.indices) return -1
        return manualSource.indexOf(visible[visibleIndex])
    }

    /**
     * 删除当前可见的切线，但保留裁边范围外的隐藏切线。
     * 返回 null 表示索引无效；空列表表示用户明确删除了全部切线。
     */
    fun withoutVisibleCut(
        manualSource: List<Int>,
        visibleIndex: Int,
        contentTop: Int,
        contentHeight: Int,
        minSlicePx: Int = 50,
    ): List<Int>? {
        val fullIndex = fullIndexOfVisible(
            manualSource, visibleIndex, contentTop, contentHeight, minSlicePx,
        )
        if (fullIndex < 0) return null
        return manualSource.toMutableList().apply { removeAt(fullIndex) }
    }

    /** 统一合法矩形约束：L+R ≤ W−minW，T+B ≤ H−minH（左/上优先）。 */
    fun sanitizeMargins(
        margins: com.changjietu.app.image.analysis.Margins,
        width: Int,
        height: Int,
        minContentW: Int = 64,
        minContentH: Int = 100,
    ): com.changjietu.app.image.analysis.Margins {
        val mw = minOf(minContentW, width)
        val mh = minOf(minContentH, height)
        val l = margins.left.coerceIn(0, (width - mw).coerceAtLeast(0))
        val r = margins.right.coerceIn(0, (width - mw - l).coerceAtLeast(0))
        val t = margins.top.coerceIn(0, (height - mh).coerceAtLeast(0))
        val b = margins.bottom.coerceIn(0, (height - mh - t).coerceAtLeast(0))
        return com.changjietu.app.image.analysis.Margins(l, t, r, b)
    }

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
