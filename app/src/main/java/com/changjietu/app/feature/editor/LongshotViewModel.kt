package com.changjietu.app.feature.editor

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.changjietu.app.core.EditorState
import com.changjietu.app.core.ProcessErrorMessages
import com.changjietu.app.core.ProcessSettings
import com.changjietu.app.core.SourceInfo
import com.changjietu.app.core.UriPersistencePolicy
import com.changjietu.app.feature.export.ExportRepository
import com.changjietu.app.feature.history.RecentStore
import com.changjietu.app.image.analysis.MarginDetector
import com.changjietu.app.image.analysis.Margins
import com.changjietu.app.image.analysis.QualityAnalyzer
import com.changjietu.app.image.analysis.SplitPointDetector
import com.changjietu.app.image.processing.BitmapRegionSource
import com.changjietu.app.image.processing.ImageSplitter
import com.changjietu.app.image.processing.RegionDecoderUtil
import com.changjietu.app.image.processing.SplitRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface HomeUiState {
    data object Idle : HomeUiState
    data class Loading(val message: String) : HomeUiState
    data class Error(val message: String) : HomeUiState
}

class LongshotViewModel(app: Application) : AndroidViewModel(app) {

    private val exportRepo = ExportRepository(app)
    private val recentStore = RecentStore(app)

    private val _homeState = MutableStateFlow<HomeUiState>(HomeUiState.Idle)
    val homeState: StateFlow<HomeUiState> = _homeState.asStateFlow()

    private val _source = MutableStateFlow<SourceInfo?>(null)
    val source: StateFlow<SourceInfo?> = _source.asStateFlow()

    private val _settings = MutableStateFlow(ProcessSettings())
    val settings: StateFlow<ProcessSettings> = _settings.asStateFlow()

    private val _editor = MutableStateFlow<EditorState?>(null)
    val editor: StateFlow<EditorState?> = _editor.asStateFlow()

    private val _recents = MutableStateFlow(recentStore.list())
    val recents: StateFlow<List<com.changjietu.app.feature.history.RecentItem>> = _recents.asStateFlow()

    /**
     * 外部分享事件的单调递增序号 = 分享进入导航的单一事实来源。
     * 仅由 [consumeShare] / [openInvalidShare] 递增；配置变更后序号不变，
     * 因此 UI 不会因 recomposition / configuration change 重复导航。
     */
    private val _shareRequestId = MutableStateFlow(0L)
    val shareRequestId: StateFlow<Long> = _shareRequestId.asStateFlow()

    /** 外部分享入口：持久化读权限（若来源支持）→ 加载新源并递增分享序号。 */
    fun consumeShare(uri: Uri, flags: Int) {
        val cr = getApplication<Application>().contentResolver
        if (UriPersistencePolicy.shouldTakePersistableRead(flags)) {
            try {
                cr.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
                // 提供方不支持 persistable grant → 回退为临时访问，不在此失败
            }
        }
        _shareRequestId.update { it + 1 }
        openUri(uri)
    }

    /** 无效/缺失/不支持的外部输入：graceful fail，不 crash、不误判为正常源。 */
    fun openInvalidShare() {
        _source.value = null
        _editor.value = null
        _homeState.value = HomeUiState.Error(ProcessErrorMessages.INVALID_SHARE)
        _shareRequestId.update { it + 1 }
    }

    fun updateSettings(transform: (ProcessSettings) -> ProcessSettings) {
        _settings.update(transform)
        // 重新估算片数
        val src = _source.value ?: return
        val s = _settings.value
        val contentH = if (s.cropMargins) {
            src.height - src.margins.top - src.margins.bottom
        } else {
            src.height
        }.coerceAtLeast(1)
        val plan = SplitPointDetector.plan(
            contentHeight = contentH,
            targetSliceHeight = s.targetHeight,
            overlap = s.overlap,
        )
        _source.value = src.copy(estimatedSlices = plan.sliceCount)
    }

    fun openUri(uri: Uri) {
        viewModelScope.launch {
            _homeState.value = HomeUiState.Loading("正在分析图片…")
            _editor.value = null
            try {
                withContext(Dispatchers.IO) { checkReadable(uri) }
                val info = withContext(Dispatchers.IO) { analyzeUri(uri) }
                _source.value = info
                _homeState.value = HomeUiState.Idle
            } catch (e: Exception) {
                _source.value = null
                _homeState.value = HomeUiState.Error(
                    ProcessErrorMessages.logAndFriendly(
                        e,
                        if (e is SourceUnavailableException) {
                            ProcessErrorMessages.OPEN_FAILED
                        } else {
                            ProcessErrorMessages.ANALYZE_FAILED
                        },
                    ),
                )
            }
        }
    }

    /** 读取预检：URI 已失效/无权限（stale）时抛出 [SourceUnavailableException]，走恢复路径。 */
    private fun checkReadable(uri: Uri) {
        val cr = getApplication<Application>().contentResolver
        val stream = try {
            cr.openInputStream(uri)
        } catch (e: SecurityException) {
            null
        }
        if (stream == null) throw SourceUnavailableException()
        stream.close()
    }

    private fun analyzeUri(uri: Uri): SourceInfo {
        val app = getApplication<Application>()
        val cr = app.contentResolver
        // bounds
        val (w, h) = cr.openInputStream(uri)?.use { ImageSplitter.decodeBounds(it) }
            ?: error("无法读取图片尺寸")
        val source = cr.openInputStream(uri)?.use { BitmapRegionSource.open(it) }
            ?: error("无法创建区域解码器（格式可能不受支持）")
        source.use { src ->
            val (colE, rowE) = RegionDecoderUtil.computeEnergyMaps(src)
            val glyph = RegionDecoderUtil.estimateGlyphHeight(rowE)
            val quality = QualityAnalyzer.analyze(w, h, glyph.takeIf { it > 0f })
            val margins = MarginDetector.detect(w, h, colE, rowE)
            val contentH = (h - margins.top - margins.bottom).coerceAtLeast(1)
            val s = _settings.value
            val plan = SplitPointDetector.plan(
                contentHeight = contentH,
                targetSliceHeight = s.targetHeight,
                overlap = s.overlap,
                rowEnergy = rowE.copyOfRange(margins.top, margins.top + contentH),
            )
            return SourceInfo(
                uri = uri,
                width = w,
                height = h,
                quality = quality,
                margins = margins,
                estimatedSlices = plan.sliceCount,
            )
        }
    }

    fun startProcess() {
        val srcInfo = _source.value ?: return
        val s = _settings.value
        viewModelScope.launch {
            _editor.value = EditorState(
                source = srcInfo,
                settings = s,
                cutEnds = emptyList(),
                contentHeight = 0,
                contentWidth = 0,
                isProcessing = true,
                statusMessage = "正在切片…",
            )
            try {
                val state = withContext(Dispatchers.IO) { processInternal(srcInfo, s) }
                _editor.value = state
                recentStore.add(srcInfo.uri, srcInfo.width, srcInfo.height, state.slices.size)
                _recents.value = recentStore.list()
            } catch (e: Exception) {
                val friendly = ProcessErrorMessages.logAndFriendly(e, ProcessErrorMessages.PROCESS_FAILED)
                _editor.update {
                    it?.copy(isProcessing = false, error = friendly, statusMessage = null)
                        ?: EditorState(
                            source = srcInfo,
                            settings = s,
                            cutEnds = emptyList(),
                            contentHeight = 0,
                            contentWidth = 0,
                            error = friendly,
                        )
                }
            }
        }
    }

    private fun processInternal(srcInfo: SourceInfo, s: ProcessSettings): EditorState {
        val app = getApplication<Application>()
        val cr = app.contentResolver
        val region = cr.openInputStream(srcInfo.uri)?.use { BitmapRegionSource.open(it) }
            ?: error("无法打开图片进行切片")
        region.use { src ->
            val margins = if (s.cropMargins) srcInfo.margins else Margins(0, 0, 0, 0)
            val contentH = (src.height - margins.top - margins.bottom).coerceAtLeast(1)
            val contentW = (src.width - margins.left - margins.right).coerceAtLeast(1)
            val (colE, rowE) = RegionDecoderUtil.computeEnergyMaps(src)
            val contentEnergy = if (margins.top + contentH <= rowE.size) {
                rowE.copyOfRange(margins.top, margins.top + contentH)
            } else {
                FloatArray(contentH) { 1f }
            }
            val plan = SplitPointDetector.plan(
                contentHeight = contentH,
                targetSliceHeight = s.targetHeight,
                overlap = s.overlap,
                rowEnergy = contentEnergy,
            )
            val outDir = File(app.cacheDir, "slices/${System.currentTimeMillis()}").also {
                it.mkdirs()
            }
            // 清理旧切片目录，保留最近 3 个
            cleanupOldSliceDirs(File(app.cacheDir, "slices"), keep = 3)
            val slices = ImageSplitter.splitToFiles(
                SplitRequest(
                    source = src,
                    margins = margins,
                    plan = plan,
                    outputDir = outDir,
                    jpegQuality = s.jpegQuality,
                    drawBadge = s.drawBadge,
                    colEnergy = colE,
                    rowEnergy = rowE,
                ),
            )
            if (slices.isEmpty()) error("未生成任何切片")
            val cutEnds = SplitPointDetector.cutEndsFromRanges(plan.ranges, contentH)
            return EditorState(
                source = srcInfo,
                settings = s,
                cutEnds = cutEnds,
                contentHeight = contentH,
                contentWidth = contentW,
                slices = slices,
                isProcessing = false,
                statusMessage = "已生成 ${slices.size} 张",
            )
        }
    }

    private fun cleanupOldSliceDirs(root: File, keep: Int) {
        if (!root.exists()) return
        val dirs = root.listFiles()?.filter { it.isDirectory }?.sortedByDescending { it.name } ?: return
        dirs.drop(keep).forEach { it.deleteRecursively() }
    }

    fun mergeSliceWithNext(index: Int) {
        val ed = _editor.value ?: return
        if (index < 0 || index >= ed.slices.size - 1) return
        val ranges = ed.slices.map { it.startY until it.endY }.toMutableList()
        val a = ranges[index]
        val b = ranges[index + 1]
        ranges[index] = a.first until b.last + 1
        ranges.removeAt(index + 1)
        reprocessFromRanges(ranges)
    }

    fun deleteSlice(index: Int) {
        val ed = _editor.value ?: return
        if (ed.slices.size <= 1) return
        if (index !in ed.slices.indices) return
        val ranges = ed.slices.map { it.startY until it.endY }.toMutableList()
        ranges.removeAt(index)
        reprocessFromRanges(ranges)
    }

    fun updateCutEnd(cutIndex: Int, newEnd: Int) {
        val ed = _editor.value ?: return
        if (cutIndex !in ed.cutEnds.indices) return
        val ends = ed.cutEnds.toMutableList()
        val minEnd = if (cutIndex == 0) 50 else ends[cutIndex - 1] + 50
        val maxEnd = if (cutIndex == ends.lastIndex) ed.contentHeight else ends[cutIndex + 1] - 50
        ends[cutIndex] = newEnd.coerceIn(minEnd, maxEnd)
        if (ends.last() != ed.contentHeight) ends[ends.lastIndex] = ed.contentHeight
        val ranges = SplitPointDetector.rangesFromCutEnds(ed.contentHeight, ends, ed.settings.overlap)
        reprocessFromRanges(ranges)
    }

    private fun reprocessFromRanges(ranges: List<IntRange>) {
        val ed = _editor.value ?: return
        viewModelScope.launch {
            _editor.value = ed.copy(isProcessing = true, statusMessage = "重新切片…", error = null)
            try {
                val updated = withContext(Dispatchers.IO) {
                    val app = getApplication<Application>()
                    val cr = app.contentResolver
                    val region = cr.openInputStream(ed.source.uri)?.use { BitmapRegionSource.open(it) }
                        ?: error("无法打开图片")
                    region.use { src ->
                        val margins = if (ed.settings.cropMargins) ed.source.margins else Margins(0, 0, 0, 0)
                        val plan = com.changjietu.app.image.analysis.SplitPlan(
                            contentHeight = ed.contentHeight,
                            targetSliceHeight = ed.settings.targetHeight,
                            overlap = ed.settings.overlap,
                            ranges = ranges,
                        )
                        val outDir = File(app.cacheDir, "slices/${System.currentTimeMillis()}").also { it.mkdirs() }
                        val slices = ImageSplitter.splitToFiles(
                            SplitRequest(
                                source = src,
                                margins = margins,
                                plan = plan,
                                outputDir = outDir,
                                jpegQuality = ed.settings.jpegQuality,
                                drawBadge = ed.settings.drawBadge,
                            ),
                        )
                        ed.copy(
                            cutEnds = SplitPointDetector.cutEndsFromRanges(ranges, ed.contentHeight),
                            slices = slices,
                            isProcessing = false,
                            statusMessage = "已更新为 ${slices.size} 张",
                        )
                    }
                }
                _editor.value = updated
            } catch (e: Exception) {
                val friendly = ProcessErrorMessages.logAndFriendly(e, ProcessErrorMessages.REPROCESS_FAILED)
                _editor.value = ed.copy(isProcessing = false, error = friendly, statusMessage = null)
            }
        }
    }

    fun saveToGallery(onResult: (Result<Int>) -> Unit) {
        val slices = _editor.value?.slices.orEmpty()
        if (slices.isEmpty()) {
            onResult(Result.failure(IllegalStateException("没有可保存的切片")))
            return
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { exportRepo.saveAllToGallery(slices) }
            onResult(result)
            if (result.isSuccess) {
                _editor.update { it?.copy(statusMessage = "已保存 ${result.getOrNull()} 张到相册") }
            }
        }
    }

    fun shareIntent(): Intent? {
        val slices = _editor.value?.slices.orEmpty()
        if (slices.isEmpty()) return null
        return exportRepo.buildShareIntent(slices)
    }

    fun clearSource() {
        _source.value = null
        _editor.value = null
        _homeState.value = HomeUiState.Idle
    }
}

/** URI 已失效 / 读取权限已回收（stale）时抛出，映射到「源不可用，请重新选择」恢复文案。 */
private class SourceUnavailableException : Exception()
