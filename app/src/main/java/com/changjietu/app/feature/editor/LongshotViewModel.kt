package com.changjietu.app.feature.editor

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.changjietu.app.core.DisplaySpec
import com.changjietu.app.core.EditorState
import com.changjietu.app.core.ProcessErrorMessages
import com.changjietu.app.core.ProcessSettings
import com.changjietu.app.core.ProcessTransition
import com.changjietu.app.core.ShareDelivery
import com.changjietu.app.core.SafeCropMargins
import com.changjietu.app.core.SourceCutGeometry
import com.changjietu.app.core.SourceInfo
import com.changjietu.app.core.UriAccessSource
import com.changjietu.app.core.UriPersistencePolicy
import com.changjietu.app.core.ZipNameSanitizer
import com.changjietu.app.feature.export.ExportRepository
import com.changjietu.app.feature.export.ExportFileRetention
import com.changjietu.app.feature.export.OrderedSlices
import com.changjietu.app.feature.export.SaveSummary
import com.changjietu.app.feature.export.SavedZip
import com.changjietu.app.feature.export.SliceZipper
import com.changjietu.app.feature.history.RecentStore
import com.changjietu.app.image.analysis.MarginDetector
import com.changjietu.app.image.analysis.Margins
import com.changjietu.app.image.analysis.QualityAnalyzer
import com.changjietu.app.image.analysis.SplitPointDetector
import com.changjietu.app.image.processing.BitmapRegionSource
import com.changjietu.app.image.processing.ImageSplitter
import com.changjietu.app.image.processing.SliceGeometry
import com.changjietu.app.image.processing.OverviewRenderer
import com.changjietu.app.image.processing.RegionDecoderUtil
import com.changjietu.app.image.processing.SliceOutput
import com.changjietu.app.image.processing.SplitRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface HomeUiState {
    data object Idle : HomeUiState
    data class Loading(val message: String) : HomeUiState
    data class Error(val message: String) : HomeUiState
}

/**
 * 剪辑式编辑页用的长图总览（已解码位图，不交给 Coil 重新解码）。
 * [contentWidth]/[contentHeight] 为裁边后的内容坐标尺寸，用于 y ↔ 切点线性映射。
 */
data class OverviewState(
    val bitmap: ImageBitmap,
    val contentWidth: Int,
    val contentHeight: Int,
)

/**
 * 处理页上的预计切分。切点在内容坐标（裁边之后），末元素是内容高度。
 * 位图本身是整张原图，裁边用 [contentLeft]/[contentTop] 叠在预览上。
 */
data class PreviewPlan(
    val sliceCount: Int,
    val cutEnds: List<Int>,
    val contentLeft: Int,
    val contentTop: Int,
    val contentWidth: Int,
    val contentHeight: Int,
    val sourceWidth: Int,
    val sourceHeight: Int,
)

sealed interface PreviewBitmapState {
    data object Empty : PreviewBitmapState
    data object Loading : PreviewBitmapState
    data class Ready(val overview: OverviewState) : PreviewBitmapState
    data object Failed : PreviewBitmapState
}

class LongshotViewModel(app: Application) : AndroidViewModel(app) {

    private val exportRepo = ExportRepository(app)
    private val fileRetention = ExportFileRetention()
    private val recentStore = RecentStore(app)

    /** 当前设备真实屏幕像素；切片默认高度取其长边，保证单张不超过手机屏幕。 */
    val displaySpec: DisplaySpec = DisplaySpec.from(app)

    private val _homeState = MutableStateFlow<HomeUiState>(HomeUiState.Idle)
    val homeState: StateFlow<HomeUiState> = _homeState.asStateFlow()

    private val _source = MutableStateFlow<SourceInfo?>(null)
    val source: StateFlow<SourceInfo?> = _source.asStateFlow()

    private val _settings = MutableStateFlow(
        ProcessSettings(targetHeight = displaySpec.maxSliceHeightPx),
    )
    val settings: StateFlow<ProcessSettings> = _settings.asStateFlow()

    /** 防止旧的加载/切片任务在新图片打开后回写过期结果。 */
    private var sourceGeneration = 0

    /** 连续微调切点时只保留最后一次重切结果。 */
    private var reprocessJob: Job? = null

    private val _editor = MutableStateFlow<EditorState?>(null)
    val editor: StateFlow<EditorState?> = _editor.asStateFlow()

    /** 结果页剪辑总览：裁边后的内容区域。只在初始切分成功时生成，手动编辑不重建。 */
    private val _overview = MutableStateFlow<OverviewState?>(null)
    val overview: StateFlow<OverviewState?> = _overview.asStateFlow()

    /** 精细编辑高清区域（窗口分块解码，sample=1，不整图解码）。 */
    private val _detailOverview = MutableStateFlow<OverviewState?>(null)
    val detailOverview: StateFlow<OverviewState?> = _detailOverview.asStateFlow()
    private var detailJob: Job? = null

    /** 按 content 窗口加载高清条带（供精细编辑全屏使用）。 */
    fun loadDetailWindow(top: Int, height: Int) {
        val ed = _editor.value ?: return
        detailJob?.cancel()
        _detailOverview.value = null // 不向新窗口显示上一窗口的过期图像
        val generation = sourceGeneration
        detailJob = viewModelScope.launch {
            val rendered: OverviewState? = try {
                withContext(Dispatchers.IO) {
                    val app = getApplication<Application>()
                    val region = app.contentResolver.openInputStream(ed.source.uri)?.use {
                        BitmapRegionSource.open(it)
                    } ?: return@withContext null
                    region.use { src ->
                        val m = ed.appliedMargins
                        val w = ed.contentWidth
                        val h = height.coerceIn(1, ed.contentHeight)
                        val t = top.coerceIn(0, (ed.contentHeight - h).coerceAtLeast(0))
                        val handle = src.decodeRegion(
                            m.left,
                            m.top + t,
                            m.left + w,
                            m.top + t + h,
                            sampleSize = 1,
                        ) ?: return@use null
                        // asImageBitmap() 只包装 Android Bitmap，不复制像素。
                        // 先生成独立快照，再关闭会 recycle 原 Bitmap 的解码句柄。
                        handle.use { hdl ->
                            hdl.bitmapOrNull
                                ?.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
                                ?.asImageBitmap()
                                ?.let { img ->
                                    OverviewState(bitmap = img, contentWidth = w, contentHeight = h)
                                }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            if (isActive && generation == sourceGeneration) _detailOverview.value = rendered
        }
    }

    /** 处理页切线预览：整图分块总览。高度/重叠只改切线，不重新解码。 */
    private val _previewBitmap = MutableStateFlow<PreviewBitmapState>(PreviewBitmapState.Empty)
    val previewBitmap: StateFlow<PreviewBitmapState> = _previewBitmap.asStateFlow()

    private val _previewPlan = MutableStateFlow<PreviewPlan?>(null)
    val previewPlan: StateFlow<PreviewPlan?> = _previewPlan.asStateFlow()

    private var previewJob: Job? = null

    /** 处理页全屏手动切线（source 坐标，不含末端）。null = 自动计划；与裁边解耦，切换裁边不重置。 */
    private val _manualCuts = MutableStateFlow<List<Int>?>(null)
    val manualCuts: StateFlow<List<Int>?> = _manualCuts.asStateFlow()

    /** 用户手动裁边框（source 坐标）。null = 用检测建议值；切换裁边开关保留，恢复检测值才清空。 */
    private val _manualMargins = MutableStateFlow<Margins?>(null)
    val manualMargins: StateFlow<Margins?> = _manualMargins.asStateFlow()

    private val _recents = MutableStateFlow(recentStore.list())
    val recents: StateFlow<List<com.changjietu.app.feature.history.RecentItem>> = _recents.asStateFlow()

    /** 外部分享交付的一次性状态机（pending/consumed/ack），见 [ShareDelivery]。 */
    private val shareDelivery = ShareDelivery()

    private val _pendingShareId = MutableStateFlow<Long?>(null)
    val pendingShareId: StateFlow<Long?> = _pendingShareId.asStateFlow()

    /** 处理完成的一次性导航契约（C2.3）：仅初始 processing 成功结束时产生新 pending。 */
    private val processTransition = ProcessTransition()
    val pendingProcessResultId: StateFlow<Long?> = processTransition.pending

    /** 本实例是否已消费过分享（配置重建去重护栏）。 */
    fun hasConsumedShare(): Boolean = shareDelivery.consumed

    /** 外部 SEND/SEND_MULTIPLE 分享入口：登记一次性交付 → 加载新源。 */
    fun consumeShare(uri: Uri, flags: Int) {
        val id = shareDelivery.consume()
        _pendingShareId.value = id
        openUri(uri, UriAccessSource.EXTERNAL_SHARE, flags)
    }

    /** 无效/缺失/不支持的外部输入：graceful fail，留在首页展示错误，不 crash、不误判为正常源。 */
    fun openInvalidShare() {
        val id = shareDelivery.consume()
        shareDelivery.ack(id)
        sourceGeneration++
        reprocessJob?.cancel()
        reprocessJob = null
        _source.value = null
        _editor.value = null
        _overview.value = null
        _manualCuts.value = null
        _manualMargins.value = null
        resetPreview()
        _homeState.value = HomeUiState.Error(ProcessErrorMessages.INVALID_SHARE)
        _pendingShareId.value = null
    }

    /** UI 在完成导航后 ack；仅当 id 匹配当前 pending 才清空。 */
    fun ackSharePending(id: Long) {
        shareDelivery.ack(id)
        if (shareDelivery.pendingId == null) _pendingShareId.value = null
    }

    /** UI 在完成 preview 导航后消费处理完成事件；仅匹配当前 pending 才生效。 */
    fun ackProcessResult(id: Long) {
        processTransition.ack(id)
    }

    /** 用户 Photo Picker 选图入口。 */
    fun openPickerUri(uri: Uri) = openUri(uri, UriAccessSource.USER_PICKER)

    /** 从最近处理历史打开入口。 */
    fun openRecent(uri: Uri) = openUri(uri, UriAccessSource.RECENT)

    fun updateSettings(transform: (ProcessSettings) -> ProcessSettings) {
        _settings.update { current ->
            val next = transform(current)
            next.copy(
                targetHeight = next.targetHeight.coerceIn(400, displaySpec.maxSliceHeightPx),
                overlap = next.overlap.coerceIn(0, 160),
            )
        }
        // 解耦：裁边/高度/重叠变化都不清空 manualCuts（source 坐标），由映射层过滤越界点。
        // 重新估算片数
        val src = _source.value ?: return
        publishPreviewPlan(src, _settings.value)
    }

    private fun effectiveMargins(src: SourceInfo, settings: ProcessSettings): Margins =
        SafeCropMargins.effective(settings.cropMargins, src.margins, _manualMargins.value)

    fun updateManualMargins(transform: (Margins) -> Margins) {
        val src = _source.value ?: return
        val next = transform(_manualMargins.value ?: SafeCropMargins.automatic(src.margins))
        _manualMargins.value = SourceCutGeometry.sanitizeMargins(next, src.width, src.height)
        publishPreviewPlan(src, _settings.value)
    }

    fun clearManualMargins() {
        if (_manualMargins.value == null) return
        _manualMargins.value = null
        val src = _source.value ?: return
        publishPreviewPlan(src, _settings.value)
    }

    /** 处理页全屏拖动切线：只改内存切点（source 坐标），立即刷新预览计划，不做 IO。 */
    fun previewManualCut(cutIndex: Int, newContentEnd: Int) {
        val plan = _previewPlan.value ?: return
        val currentSource = _manualCuts.value
            ?: plan.cutEnds.dropLast(1).map { it + plan.contentTop }
        val visibleSource = SourceCutGeometry.visibleSource(
            currentSource,
            plan.contentTop,
            plan.contentHeight,
            MIN_SLICE_PX,
        )
        if (cutIndex !in visibleSource.indices) return
        val cutsContent = visibleSource.map { it - plan.contentTop }
        val minEnd = if (cutIndex == 0) MIN_SLICE_PX else cutsContent[cutIndex - 1] + MIN_SLICE_PX
        val maxEnd = cutsContent.getOrNull(cutIndex + 1)?.let { it - MIN_SLICE_PX }
            ?: (plan.contentHeight - MIN_SLICE_PX)
        if (maxEnd <= minEnd) return
        val fullIndex = SourceCutGeometry.fullIndexOfVisible(
            currentSource,
            cutIndex,
            plan.contentTop,
            plan.contentHeight,
            MIN_SLICE_PX,
        )
        if (fullIndex < 0) return
        val nextSource = currentSource.toMutableList()
        nextSource[fullIndex] = SourceCutGeometry.sourceFromContent(
            newContentEnd.coerceIn(minEnd, maxEnd),
            plan.contentTop,
        )
        _manualCuts.value = nextSource
        val src = _source.value ?: return
        publishPreviewPlan(src, _settings.value)
    }

    /** 处理页全屏长按/点击新增切入点（content 坐标）。 */
    fun addManualCutPoint(contentY: Int) {
        val plan = _previewPlan.value ?: return
        val currentSource = _manualCuts.value
            ?: plan.cutEnds.dropLast(1).map { it + plan.contentTop }
        val cutsContent = currentSource.map { it - plan.contentTop }
        val minGap = _settings.value.overlap + MIN_SLICE_PX
        val yContent = contentY.coerceIn(MIN_SLICE_PX, plan.contentHeight - MIN_SLICE_PX)
        if (yContent <= MIN_SLICE_PX || yContent >= plan.contentHeight - MIN_SLICE_PX) return
        if (cutsContent.any { kotlin.math.abs(it - yContent) < minGap }) return
        _manualCuts.value = (currentSource + SourceCutGeometry.sourceFromContent(yContent, plan.contentTop)).sorted()
        val src = _source.value ?: return
        publishPreviewPlan(src, _settings.value)
    }

    /** 删除一条可见切线；隐藏在裁边外的切线仍以 source 坐标保留。 */
    fun deleteManualCutPoint(visibleIndex: Int) {
        val plan = _previewPlan.value ?: return
        val allSource = _manualCuts.value
            ?: plan.cutEnds.dropLast(1).map { it + plan.contentTop }
        val remaining = SourceCutGeometry.withoutVisibleCut(
            allSource, visibleIndex, plan.contentTop, plan.contentHeight, MIN_SLICE_PX,
        ) ?: return
        _manualCuts.value = remaining
        val src = _source.value ?: return
        publishPreviewPlan(src, _settings.value)
    }

    fun clearManualCuts() {
        if (_manualCuts.value == null) return
        _manualCuts.value = null
        val src = _source.value ?: return
        publishPreviewPlan(src, _settings.value)
    }

    fun openUri(uri: Uri, source: UriAccessSource, flags: Int = 0) {
        if (UriPersistencePolicy.shouldAttemptPersistRead(source, flags)) {
            tryPersistRead(uri)
        }
        val generation = ++sourceGeneration
        reprocessJob?.cancel()
        reprocessJob = null
        // Claim loading synchronously before HomeScreen / share navigation enters process.
        // Otherwise process-death route recovery could mistake an in-flight new import
        // for a restored destination with missing source data.
        _homeState.value = HomeUiState.Loading("正在分析图片…")
        _source.value = null
        _editor.value = null
        _overview.value = null
        _manualCuts.value = null
        _manualMargins.value = null
        detailJob?.cancel()
        detailJob = null
        _detailOverview.value = null
        previewJob?.cancel()
        previewJob = null
        _previewPlan.value = null
        _previewBitmap.value = PreviewBitmapState.Loading
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { checkReadable(uri) }
                val info = withContext(Dispatchers.IO) { analyzeUri(uri) }
                if (generation != sourceGeneration) return@launch
                _source.value = info
                publishPreviewPlan(info, _settings.value)
                refreshPreviewBitmap(info, generation)
                _homeState.value = HomeUiState.Idle
            } catch (e: Exception) {
                if (generation != sourceGeneration) return@launch
                _source.value = null
                previewJob?.cancel()
                previewJob = null
                _previewPlan.value = null
                _previewBitmap.value = PreviewBitmapState.Empty
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

    /**
     * 尝试取得长期只读权限；仅申请 READ，绝不申请 WRITE。
     * 失败（提供方不支持/stale）时 graceful 回退为临时访问，不 crash、不标记为已持久化。
     */
    private fun tryPersistRead(uri: Uri) {
        val cr = getApplication<Application>().contentResolver
        try {
            cr.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: Exception) {
            // graceful fallback：保持临时 capability
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
            val analysis = RegionDecoderUtil.computeAnalysisMaps(src)
            val colE = analysis.columnEnergy
            val rowE = analysis.rowEnergy
            val protectedSeams = analysis.protectedSeamRows
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
                protectedRows = protectedSeams.copyOfRange(margins.top, margins.top + contentH),
            )
            return SourceInfo(
                uri = uri,
                width = w,
                height = h,
                quality = quality,
                margins = margins,
                estimatedSlices = plan.sliceCount,
                columnEnergy = colE,
                rowEnergy = rowE,
                protectedSeamRows = protectedSeams,
            )
        }
    }

    fun startProcess() {
        val srcInfo = _source.value ?: return
        if (_editor.value?.isProcessing == true) return
        reprocessJob?.cancel()
        reprocessJob = null
        val s = _settings.value
        val generation = sourceGeneration
        _overview.value = null
        // 同步进入 processing：第二次点击必须在启动协程之前看到忙碌状态，
        // 避免同一源被重复并发编码，仍沿用现有 sourceGeneration 防旧结果回写。
        _editor.value = EditorState(
            source = srcInfo,
            settings = s,
            cutEnds = emptyList(),
            contentHeight = 0,
            contentWidth = 0,
            isProcessing = true,
            statusMessage = "正在切片…",
        )
        viewModelScope.launch {
            try {
                val state = withContext(Dispatchers.IO) { processInternal(srcInfo, s) }
                if (generation != sourceGeneration) return@launch
                _editor.value = state
                // 一次性导航事件：仅初始 processing 成功结束时产生；手动切片编辑不产生。
                processTransition.markCompleted()
                recentStore.add(srcInfo.uri, srcInfo.width, srcInfo.height, state.slices.size)
                _recents.value = recentStore.list()
            } catch (e: Exception) {
                if (generation != sourceGeneration) return@launch
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
            val margins = effectiveMargins(srcInfo, s)
            val contentH = (src.height - margins.top - margins.bottom).coerceAtLeast(1)
            val contentW = (src.width - margins.left - margins.right).coerceAtLeast(1)
            // 生成剪辑式编辑页的长图总览（分块解码 + 采样，失败只降级为无总览，不阻断切片）。
            runCatching {
                _overview.value = OverviewState(
                    bitmap = OverviewRenderer.render(
                        source = src,
                        contentLeft = margins.left,
                        contentTop = margins.top,
                        contentWidth = contentW,
                        contentHeight = contentH,
                    ),
                    contentWidth = contentW,
                    contentHeight = contentH,
                )
            }.onFailure { _overview.value = null }
            // 复用 analyzeUri 已计算的 source-invariant raw energy，不再二次全图扫描
            val colE = srcInfo.columnEnergy
            val rowE = srcInfo.rowEnergy
            val manual = _manualCuts.value
            val plan = if (manual != null) {
                val contentCuts = SourceCutGeometry.contentCutsFromSource(
                    manual,
                    margins.top,
                    contentH,
                    MIN_SLICE_PX,
                )
                val ends = (contentCuts + contentH).distinct().sorted().toMutableList()
                if (ends.lastOrNull() != contentH) ends.add(contentH)
                val ranges = SplitPointDetector.rangesFromCutEnds(contentH, ends, s.overlap)
                com.changjietu.app.image.analysis.SplitPlan(
                    contentHeight = contentH,
                    targetSliceHeight = s.targetHeight,
                    overlap = s.overlap,
                    ranges = ranges,
                )
            } else {
                val contentEnergy = if (margins.top + contentH <= rowE.size) {
                    rowE.copyOfRange(margins.top, margins.top + contentH)
                } else {
                    FloatArray(contentH) { 1f }
                }
                SplitPointDetector.plan(
                    contentHeight = contentH,
                    targetSliceHeight = s.targetHeight,
                    overlap = s.overlap,
                    rowEnergy = contentEnergy,
                    protectedRows = srcInfo.protectedSeamRows
                        .takeIf { it.size >= margins.top + contentH }
                        ?.copyOfRange(margins.top, margins.top + contentH),
                )
            }
            if (s.overlap == 0) {
                // 初始计划必须完整覆盖所有源图行且严格无重复。
                SliceGeometry.requireNoOverlapCoverage(plan.ranges, contentH)
            }
            val outDir = File(app.cacheDir, "slices/${System.currentTimeMillis()}").also {
                it.mkdirs()
            }
            // 清理旧切片目录，保留最近 3 个（保护当前目录）
            cleanupOldEntries(File(app.cacheDir, "slices"), keep = 3, protect = outDir)
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
                appliedMargins = margins,
                slices = slices,
                selectedIndices = slices.indices.toSet(),
                isProcessing = false,
                statusMessage = "已生成 ${slices.size} 张",
            )
        }
    }

    private fun cleanupOldEntries(root: File, keep: Int, protect: File?) {
        if (!root.exists()) return
        val visible = _editor.value?.slices?.map { it.file }.orEmpty()
        val entries = root.listFiles()
            ?.filter { it != protect }
            ?.sortedByDescending { it.name }
            ?: return
        entries.forEachIndexed { rank, entry ->
            if (visible.any { fileRetention.covers(entry, it) }) return@forEachIndexed
            ReprocessInFlightDirectories.pruneIfUnprotected(entry) {
                fileRetention.prune(entry, rank, keep)
            }
        }
    }

    fun mergeSliceWithNext(index: Int) {
        val ed = _editor.value ?: return
        if (index < 0 || index >= ed.slices.size - 1) return
        val ranges = ed.slices.map { it.startY until it.endY }.toMutableList()
        val a = ranges[index]
        val b = ranges[index + 1]
        ranges[index] = a.first until b.last + 1
        ranges.removeAt(index + 1)
        reprocessFromRanges(ranges, ed.cropTop, ed.cropBottom)
    }

    fun deleteSlice(index: Int) {
        val ed = _editor.value ?: return
        if (ed.slices.size <= 1) return
        if (index !in ed.slices.indices) return
        val ranges = ed.slices.map { it.startY until it.endY }.toMutableList()
        ranges.removeAt(index)
        reprocessFromRanges(ranges, ed.cropTop, ed.cropBottom)
    }

    /**
     * 拖动切点手柄时的实时（零 IO）更新：只改 cutEnds 让切点线跟手。
     * 绝不重切片、绝不产生 ProcessTransition pending（C2F1 导航契约要求手动编辑不误导航）。
     * cutEnds 末元素 = cropBottom（整体下边界），不是可拖动切点。
     */
    fun previewCut(cutIndex: Int, newEnd: Int) {
        val ed = _editor.value ?: return
        if (cutIndex !in 0 until ed.cutEnds.lastIndex) return
        val minEnd = if (cutIndex == 0) {
            ed.cropTop + MIN_SLICE_PX
        } else {
            ed.cutEnds[cutIndex - 1] + MIN_SLICE_PX
        }
        val maxEnd = ed.cutEnds[cutIndex + 1] - MIN_SLICE_PX
        if (maxEnd <= minEnd) return
        val ends = ed.cutEnds.toMutableList()
        ends[cutIndex] = newEnd.coerceIn(minEnd, maxEnd)
        _editor.value = ed.copy(cutEnds = ends)
    }

    /** 拖动头/尾裁切手柄时的实时更新：只改整体截取范围，松手由 [commitCuts] 统一重切。 */
    fun previewCrop(top: Int, bottom: Int) {
        val ed = _editor.value ?: return
        val minRange = (ed.settings.overlap + MIN_SLICE_PX * 2)
            .coerceAtMost(ed.contentHeight)
        val newBottom = bottom.coerceIn(minRange, ed.contentHeight)
        val newTop = top.coerceIn(0, (newBottom - minRange).coerceAtLeast(0))
        _editor.value = ed.copy(cropTop = newTop, cropBottom = newBottom)
    }

    /** 松手时调用一次：按当前 cutEnds / 截取范围重建 ranges，只重切一次。 */
    fun commitCuts() {
        val ed = _editor.value ?: return
        val ranges = buildRangesFromEditor(ed)
        // 按下后松手但像素边界未改变时，禁止无意义的整批 JPEG 重切。
        if (SliceGeometry.matchesRenderedRanges(ranges, ed.slices)) return
        reprocessFromRanges(ranges, ed.cropTop, ed.cropBottom)
    }

    /** 在长图上点击新增切入点；距已有切点过近则忽略。 */
    fun addCutPoint(contentY: Int) {
        val ed = _editor.value ?: return
        val minGap = ed.settings.overlap + MIN_SLICE_PX
        val lo = ed.cropTop + minGap
        val hi = ed.cropBottom - MIN_SLICE_PX
        if (hi <= lo) return
        val y = contentY.coerceIn(lo, hi)
        if (ed.cutEnds.any { kotlin.math.abs(it - y) < minGap }) return
        _editor.value = ed.copy(cutEnds = (ed.cutEnds + y).sorted())
        commitCuts()
    }

    /**
     * 由 cutEnds + 整体截取范围推导带重叠的切片 ranges（content 坐标）。
     * 传 contentHeight = cropBottom，避免 rangesFromCutEnds 再补一次 contentHeight；
     * 末片 end 固定为 cropBottom，头片 start 固定为 cropTop。
     */
    private fun buildRangesFromEditor(ed: EditorState): List<IntRange> {
        val ov = ed.settings.overlap
        val minGap = ov + MIN_SLICE_PX
        val ends = ed.cutEnds
            .filter { it > ed.cropTop + minGap && it < ed.cropBottom - MIN_SLICE_PX }
            .distinct()
            .sorted()
            .toMutableList()
        if (ends.lastOrNull() != ed.cropBottom) ends.add(ed.cropBottom)
        val raw = SplitPointDetector.rangesFromCutEnds(ed.cropBottom, ends, ov)
        val clamped = raw.map { r ->
            val s = maxOf(r.first, ed.cropTop)
            val e = minOf(r.last + 1, ed.cropBottom)
            if (e > s) s until e else null
        }.filterNotNull()
        return clamped.ifEmpty { listOf(ed.cropTop until ed.cropBottom) }
    }

    private fun reprocessFromRanges(ranges: List<IntRange>, cropTop: Int, cropBottom: Int) {
        val ed = _editor.value ?: return
        val generation = sourceGeneration
        reprocessJob?.cancel()
        reprocessJob = viewModelScope.launch {
            _editor.update { current ->
                if (generation == sourceGeneration && current != null && current.source.uri == ed.source.uri) {
                    current.copy(isProcessing = true, statusMessage = "重新切片…", error = null)
                } else current
            }
            var outputDirectory: File? = null
            var committed = false
            try {
                val updated = withContext(Dispatchers.IO) {
                    val app = getApplication<Application>()
                    val cr = app.contentResolver
                    val region = cr.openInputStream(ed.source.uri)?.use { BitmapRegionSource.open(it) }
                        ?: error("无法打开图片")
                    region.use { src ->
                        val margins = ed.appliedMargins
                        val plan = com.changjietu.app.image.analysis.SplitPlan(
                            contentHeight = ed.contentHeight,
                            targetSliceHeight = ed.settings.targetHeight,
                            overlap = ed.settings.overlap,
                            ranges = ranges,
                        )
                        val outDir = ReprocessInFlightDirectories.create(File(app.cacheDir, "slices"))
                        outputDirectory = outDir
                        cleanupOldEntries(File(app.cacheDir, "slices"), keep = 3, protect = outDir)
                        val slices = ImageSplitter.splitToFiles(
                            SplitRequest(
                                source = src,
                                margins = margins,
                                plan = plan,
                                outputDir = outDir,
                                jpegQuality = ed.settings.jpegQuality,
                                drawBadge = ed.settings.drawBadge,
                                colEnergy = ed.source.columnEnergy,
                                rowEnergy = ed.source.rowEnergy,
                            ),
                        )
                        ed.copy(
                            cutEnds = SplitPointDetector.cutEndsFromRanges(ranges, cropBottom),
                            cropTop = cropTop,
                            cropBottom = cropBottom,
                            slices = slices,
                            isProcessing = false,
                            statusMessage = if (slices.size == ed.slices.size) {
                                "已更新为 ${slices.size} 张"
                            } else {
                                "已更新为 ${slices.size} 张（已全选）"
                            },
                        )
                    }
                }
                if (isActive && generation == sourceGeneration) {
                    // A cancelled or superseded job must not retain its unreferenced files.
                    while (isActive && generation == sourceGeneration) {
                        val current = _editor.value ?: break
                        if (current.source.uri != ed.source.uri) break
                        val next = updated.copy(
                            selectedIndices = ReprocessSelectionPolicy.resolve(
                                currentSelection = current.selectedIndices,
                                currentSliceCount = current.slices.size,
                                newSliceCount = updated.slices.size,
                            ),
                        )
                        if (_editor.compareAndSet(current, next)) {
                            committed = true
                            break
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!isActive) return@launch
                val friendly = ProcessErrorMessages.logAndFriendly(e, ProcessErrorMessages.REPROCESS_FAILED)
                if (generation == sourceGeneration) {
                    _editor.update { current ->
                        if (current == null || current.source.uri != ed.source.uri) current
                        else current.copy(isProcessing = false, error = friendly, statusMessage = null)
                    }
                }
            } finally {
                ReprocessOutputLifecycle.finish(outputDirectory, committed)
            }
        }
    }

    fun toggleSliceSelection(index: Int) {
        _editor.update { ed ->
            if (ed == null || index !in ed.slices.indices) {
                ed
            } else {
                ed.copy(
                    selectedIndices = if (index in ed.selectedIndices) {
                        ed.selectedIndices - index
                    } else {
                        ed.selectedIndices + index
                    },
                )
            }
        }
    }

    fun selectAllSlices() {
        _editor.update { ed -> ed?.copy(selectedIndices = ed.slices.indices.toSet()) }
    }

    fun clearSliceSelection() {
        _editor.update { ed -> ed?.copy(selectedIndices = emptySet()) }
    }

    fun selectedSlices(): List<SliceOutput> {
        val ed = _editor.value ?: return emptyList()
        return OrderedSlices.ascending(ed.selectedIndices.sorted().mapNotNull { ed.slices.getOrNull(it) })
    }

    fun saveToGallery(onResult: (Result<SaveSummary>) -> Unit) {
        val slices = selectedSlices()
        if (slices.isEmpty()) {
            onResult(Result.failure(IllegalStateException(ProcessErrorMessages.NO_SELECTION)))
            return
        }
        val lease = runCatching { fileRetention.acquire(slices.map { it.file }) }
            .getOrElse { onResult(Result.failure(it)); return }
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { exportRepo.saveToGallery(slices) }
                onResult(result)
                result.onSuccess { summary ->
                    _editor.update {
                        it?.copy(
                            statusMessage = "已保存 ${summary.saved} 张到相册 Pictures/${ExportRepository.ALBUM_NAME}",
                        )
                    }
                }
            } finally {
                lease.close()
            }
        }
    }

    /** ZIP 重命名对话框的预填默认名。 */
    fun defaultZipName(): String =
        "longshot_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}"

    /**
     * 打包选中切片为 ZIP。[requestedName] 为用户自定义名（经 [ZipNameSanitizer] 清洗），
     * 缓存 ZIP 文件名保持不变以便 FileProvider 分享；保存/展示用自定义名。
     */
    fun packageZipToDownloads(
        requestedName: String? = null,
        onResult: (Result<SavedZip>) -> Unit,
    ) {
        val slices = selectedSlices()
        if (slices.isEmpty()) {
            onResult(Result.failure(IllegalStateException(ProcessErrorMessages.NO_SELECTION)))
            return
        }
        val lease = runCatching { fileRetention.acquire(slices.map { it.file }) }
            .getOrElse { onResult(Result.failure(it)); return }
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val app = getApplication<Application>()
                        val root = File(app.cacheDir, "zip").also { it.mkdirs() }
                        cleanupOldEntries(root, keep = 3, protect = null)
                        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
                        val zip = File(root, "longshot_$stamp.zip")
                        val count = SliceZipper.zip(slices, zip)
                        check(count > 0) { "没有可打包的切片" }
                        val displayName = ZipNameSanitizer.sanitize(requestedName, "longshot_$stamp")
                        exportRepo.saveZipToDownloads(zip, displayName).getOrThrow()
                    }
                }
                onResult(result)
                result.onSuccess { saved ->
                    _editor.update {
                        it?.copy(statusMessage = "已打包 ${slices.size} 张：${saved.locationLabel}")
                    }
                }
            } finally {
                lease.close()
            }
        }
    }

    fun shareIntent(): Intent? {
        val slices = selectedSlices()
        if (slices.isEmpty()) return null
        val intent = exportRepo.buildShareIntent(slices)
        fileRetention.markShared(slices.map { it.file })
        return intent
    }

    fun shareZipIntent(zipFile: File): Intent {
        val intent = exportRepo.buildZipShareIntent(zipFile)
        fileRetention.markShared(listOf(zipFile))
        return intent
    }

    fun clearSource() {
        sourceGeneration++
        reprocessJob?.cancel()
        reprocessJob = null
        _source.value = null
        _editor.value = null
        _overview.value = null
        _manualCuts.value = null
        _manualMargins.value = null
        detailJob?.cancel()
        detailJob = null
        _detailOverview.value = null
        resetPreview()
        _homeState.value = HomeUiState.Idle
    }

    private fun resetPreview() {
        previewJob?.cancel()
        previewJob = null
        _previewPlan.value = null
        _previewBitmap.value = PreviewBitmapState.Empty
    }

    /** 用与正式切片相同的缝隙计划刷新切线。不解码。 */
    private fun publishPreviewPlan(src: SourceInfo, settings: ProcessSettings) {
        val plan = buildPreviewPlan(src, settings)
        _previewPlan.value = plan
        if (src.estimatedSlices != plan.sliceCount) {
            _source.value = src.copy(estimatedSlices = plan.sliceCount)
        }
    }

    private fun buildPreviewPlan(src: SourceInfo, settings: ProcessSettings): PreviewPlan {
        val margins = effectiveMargins(src, settings)
        val contentH = (src.height - margins.top - margins.bottom).coerceAtLeast(1)
        val contentW = (src.width - margins.left - margins.right).coerceAtLeast(1)
        val manual = _manualCuts.value
        if (manual != null) {
            val contentCuts = SourceCutGeometry.contentCutsFromSource(
                manual,
                margins.top,
                contentH,
                MIN_SLICE_PX,
            )
            val ends = (contentCuts + contentH).distinct().sorted().toMutableList()
            if (ends.lastOrNull() != contentH) ends.add(contentH)
            val ranges = SplitPointDetector.rangesFromCutEnds(contentH, ends, settings.overlap)
            return PreviewPlan(
                sliceCount = ranges.size,
                cutEnds = ends,
                contentLeft = margins.left,
                contentTop = margins.top,
                contentWidth = contentW,
                contentHeight = contentH,
                sourceWidth = src.width,
                sourceHeight = src.height,
            )
        }
        val energy = src.rowEnergy
        val contentEnergy = if (energy.size >= margins.top + contentH) {
            energy.copyOfRange(margins.top, margins.top + contentH)
        } else {
            FloatArray(contentH) { 1f }
        }
        val plan = SplitPointDetector.plan(
            contentHeight = contentH,
            targetSliceHeight = settings.targetHeight,
            overlap = settings.overlap,
            rowEnergy = contentEnergy,
            protectedRows = src.protectedSeamRows
                .takeIf { it.size >= margins.top + contentH }
                ?.copyOfRange(margins.top, margins.top + contentH),
        )
        return PreviewPlan(
            sliceCount = plan.sliceCount,
            cutEnds = SplitPointDetector.cutEndsFromRanges(plan.ranges, contentH),
            contentLeft = margins.left,
            contentTop = margins.top,
            contentWidth = contentW,
            contentHeight = contentH,
            sourceWidth = src.width,
            sourceHeight = src.height,
        )
    }

    /** 整图总览，分块解码。裁边开关只改叠线，不调用这里。 */
    private fun refreshPreviewBitmap(info: SourceInfo, generation: Int) {
        previewJob?.cancel()
        _previewBitmap.value = PreviewBitmapState.Loading
        previewJob = viewModelScope.launch {
            val rendered = try {
                withContext(Dispatchers.IO) { renderFullOverview(info) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (generation != sourceGeneration) return@launch
            _previewBitmap.value = if (rendered == null) {
                PreviewBitmapState.Failed
            } else {
                PreviewBitmapState.Ready(
                    OverviewState(
                        bitmap = rendered,
                        contentWidth = info.width,
                        contentHeight = info.height,
                    ),
                )
            }
        }
    }

    private fun renderFullOverview(info: SourceInfo): ImageBitmap? {
        val cr = getApplication<Application>().contentResolver
        val region = cr.openInputStream(info.uri)?.use { BitmapRegionSource.open(it) }
            ?: return null
        return region.use { src ->
            OverviewRenderer.render(
                source = src,
                contentLeft = 0,
                contentTop = 0,
                contentWidth = info.width,
                contentHeight = info.height,
            )
        }
    }
}

/** 手动编辑时相邻切点/边界之间的最小像素间隔，避免产生碎片切片。 */
private const val MIN_SLICE_PX = 50

/** URI 已失效 / 读取权限已回收（stale）时抛出，映射到「源不可用，请重新选择」恢复文案。 */
private class SourceUnavailableException : Exception()
