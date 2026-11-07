package com.changjietu.app.core

import android.net.Uri
import com.changjietu.app.image.analysis.Margins
import com.changjietu.app.image.analysis.QualityReport
import com.changjietu.app.image.processing.SliceOutput

enum class ProcessMode {
    FOR_AI,
    READ,
    ARCHIVE,
}

data class ProcessSettings(
    val mode: ProcessMode = ProcessMode.FOR_AI,
    val targetHeight: Int = 2200,
    val overlap: Int = 120,
    val cropMargins: Boolean = true,
    val removeStickyHeader: Boolean = false, // phase-2, always false in MVP
    val jpegQuality: Int = 95,
    val drawBadge: Boolean = true,
)

data class SourceInfo(
    val uri: Uri,
    val width: Int,
    val height: Int,
    val quality: QualityReport,
    val margins: Margins = Margins(0, 0, 0, 0),
    val estimatedSlices: Int = 0,
    /** 源图列能量（源坐标，仅 source-invariant，不随设置变化）。 */
    val columnEnergy: FloatArray = FloatArray(0),
    /** 源图行能量（源坐标，仅 source-invariant，不随设置变化）。 */
    val rowEnergy: FloatArray = FloatArray(0),
)

data class EditorState(
    val source: SourceInfo,
    val settings: ProcessSettings,
    /** content 坐标系 cut ends（exclusive），末元素 = [cropBottom] */
    val cutEnds: List<Int>,
    val contentHeight: Int,
    val contentWidth: Int,
    /** 整体截取范围（content 坐标，含首不含尾）；头尾裁切手柄可拖动收缩。 */
    val cropTop: Int = 0,
    val cropBottom: Int = contentHeight,
    val slices: List<SliceOutput> = emptyList(),
    /** 预览页逐张选择；重新切片后默认全选。 */
    val selectedIndices: Set<Int> = slices.indices.toSet(),
    val isProcessing: Boolean = false,
    val error: String? = null,
    val statusMessage: String? = null,
)
