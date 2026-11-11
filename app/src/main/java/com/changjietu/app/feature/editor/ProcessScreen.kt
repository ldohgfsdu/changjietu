package com.changjietu.app.feature.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.changjietu.app.core.DisplaySpec
import com.changjietu.app.core.ProcessSettings
import com.changjietu.app.core.SafeCropMargins
import com.changjietu.app.core.SourceInfo
import com.changjietu.app.image.analysis.SplitPointDetector
import com.changjietu.app.image.analysis.QualityLevel
import com.changjietu.app.image.analysis.displayName
import com.changjietu.app.ui.theme.ChangjietuTheme
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.icons.ArrowBack
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ProcessScreen(
    viewModel: LongshotViewModel,
    onBack: () -> Unit,
) {
    val source by viewModel.source.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val editor by viewModel.editor.collectAsStateWithLifecycle()
    val homeState by viewModel.homeState.collectAsStateWithLifecycle()
    val previewBitmap by viewModel.previewBitmap.collectAsStateWithLifecycle()
    val previewPlan by viewModel.previewPlan.collectAsStateWithLifecycle()
    val manualCuts by viewModel.manualCuts.collectAsStateWithLifecycle()
    val manualMargins by viewModel.manualMargins.collectAsStateWithLifecycle()
    val manualActive = manualCuts != null
    val src = source
    val displaySpec = viewModel.displaySpec
    val sliceCount = previewPlan?.sliceCount ?: src?.estimatedSlices ?: 0
    val processing = editor?.isProcessing == true
    // A cut in a table or text block cannot be promised safe without an
    // identified gap. Surface the uncertainty before the user exports slices.
    val uncertainCuts = remember(previewPlan, src) {
        val p = previewPlan
        if (src == null || p == null) {
            emptyList()
        } else {
            val first = p.contentTop
            val last = first + p.contentHeight
            if (first < 0 || last > src.rowEnergy.size || last <= first) {
                emptyList()
            } else {
                SplitPointDetector.uncertainCuts(
                    p.cutEnds.dropLast(1),
                    src.rowEnergy.copyOfRange(first, last),
                )
            }
        }
    }

    Scaffold(
        containerColor = MiuixTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            SmallTopAppBar(
                title = "裁切图片",
                color = MiuixTheme.colorScheme.background,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        bottomBar = {
            if (src != null) {
                ProcessBottomBar(
                    sliceCount = sliceCount,
                    processing = processing,
                    regenerate = !editor?.slices.isNullOrEmpty(),
                    onClick = { viewModel.startProcess() },
                )
            }
        },
    ) { padding ->
        if (src == null) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                when (val st = homeState) {
                    is HomeUiState.Loading -> {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text(st.message)
                    }
                    is HomeUiState.Error -> Text(st.message, color = Color(0xFFB3261E))
                    HomeUiState.Idle -> Text("未选择图片，请返回首页。")
                }
            }
            return@Scaffold
        }

        var expanded by remember { mutableStateOf(false) }
        val bitmap = (previewBitmap as? PreviewBitmapState.Ready)?.overview?.bitmap
        val emptyText = when (previewBitmap) {
            PreviewBitmapState.Failed -> "切线预览没有生成，仍可按下面的张数切开"
            PreviewBitmapState.Empty, PreviewBitmapState.Loading -> "正在生成切线预览…"
            is PreviewBitmapState.Ready -> "正在生成切线预览…"
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "原图 ${src.width} × ${src.height}",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
            QualitySummary(src)

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MiuixTheme.colorScheme.surface)
                        .clickable(enabled = bitmap != null) { expanded = true },
                ) {
                    CutPreview(
                        bitmap = bitmap,
                        plan = previewPlan,
                        showCrop = settings.cropMargins,
                        emptyText = emptyText,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (bitmap != null) {
                    Text(
                        "点击查看并调整切线",
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    )
                }
                if (uncertainCuts.isNotEmpty()) {
                    Surface(
                        color = MiuixTheme.colorScheme.surface,
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text(
                            "检测到 ${uncertainCuts.size} 条切线缺少可靠空白带，" +
                                "可能穿过文字、表格或图片。生成前请点开预览检查并手动调整。",
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                }
                Text(
                    "预计 $sliceCount 张切片 · 保持原始尺寸",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false,
                )
                Text(
                    if (manualActive) "已手动调整切线，生成时按当前切线执行。" else "点击预览可先在长图上拖动切线。",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                )
            }

            Text("裁切设置", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            HeightControl(
                settings = settings,
                displaySpec = displaySpec,
                contentWidth = previewPlan?.contentWidth
                    ?: (src.width - src.margins.left - src.margins.right).coerceAtLeast(1),
                onTargetChange = { value ->
                    viewModel.updateSettings { it.copy(targetHeight = value) }
                },
            )
            OverlapControl(
                settings = settings,
                sliceCount = sliceCount,
                onChange = { value ->
                    viewModel.updateSettings { it.copy(overlap = value) }
                },
            )
            SuperSwitch(
                title = "自动裁除上下空白",
                summary = marginSummary(src, settings.cropMargins, manualMargins),
                checked = settings.cropMargins,
                onCheckedChange = { checked ->
                    viewModel.updateSettings { it.copy(cropMargins = checked) }
                },
            )

            val effectiveMargins = if (settings.cropMargins) (manualMargins ?: src.margins) else com.changjietu.app.image.analysis.Margins(0, 0, 0, 0)
            val manualCutsNow = manualCuts
            if (manualCutsNow != null && settings.cropMargins) {
                val contentH = (src.height - effectiveMargins.top - effectiveMargins.bottom).coerceAtLeast(1)
                val conflict = com.changjietu.app.core.SourceCutGeometry.conflicts(
                    manualCutsNow,
                    effectiveMargins.top,
                    contentH,
                    50,
                )
                if (conflict) {
                    Surface(color = Color(0xFFFFF4E5), shape = RoundedCornerShape(12.dp)) {
                        Text(
                            "裁边范围与切线冲突：部分切点已落在裁边外并被忽略，请调整裁边或切线。",
                            color = Color(0xFF8A4B00),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }

            MoreOptions(
                settings = settings,
                src = src,
                manualMargins = manualMargins,
                onChange = viewModel::updateSettings,
                onManualMarginsChange = viewModel::updateManualMargins,
                onClearManualMargins = viewModel::clearManualMargins,
            )

            editor?.error?.let {
                Surface(color = Color(0xFFFFEDEA), shape = RoundedCornerShape(12.dp)) {
                    Text(
                        it,
                        color = Color(0xFF8C1D18),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        val planNow = previewPlan
        if (expanded && bitmap != null && planNow != null) {
            CutPreviewDialog(
                bitmap = bitmap,
                plan = planNow,
                showCrop = settings.cropMargins,
                manualActive = manualActive,
                uncertainCutEnds = uncertainCuts.toSet(),
                onCutPreview = viewModel::previewManualCut,
                onAddCutPoint = viewModel::addManualCutPoint,
                onClearManualCuts = viewModel::clearManualCuts,
                onDismiss = { expanded = false },
            )
        }
    }
}

@Composable
private fun ProcessBottomBar(
    sliceCount: Int,
    processing: Boolean,
    regenerate: Boolean,
    onClick: () -> Unit,
) {
    val label = when {
        processing -> "正在切片…"
        regenerate -> "重新生成 $sliceCount 张切片"
        sliceCount > 0 -> "生成 $sliceCount 张切片"
        else -> "生成切片"
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(MiuixTheme.colorScheme.background)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Button(
            onClick = onClick,
            enabled = !processing,
            colors = ButtonDefaults.buttonColorsPrimary(),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            if (processing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = Color.White,
                )
                Spacer(Modifier.width(10.dp))
            }
            Text(
                label,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun QualitySummary(src: SourceInfo) {
    val q = src.quality
    val caption = MiuixTheme.colorScheme.onSurfaceSecondary
    when (q.level) {
        QualityLevel.GOOD -> {
            Text(q.level.displayName(), fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(
                "原图宽度 ${src.width} px",
                fontSize = 13.sp,
                color = caption,
                maxLines = 1,
                softWrap = false,
            )
        }
        QualityLevel.USABLE -> {
            Text(q.level.displayName(), fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(
                "原图宽度 ${src.width} px。${q.conclusion}",
                fontSize = 13.sp,
                color = caption,
            )
        }
        QualityLevel.LOW, QualityLevel.UNRECOVERABLE -> {
            val danger = q.level == QualityLevel.UNRECOVERABLE
            Surface(
                color = if (danger) Color(0xFFFFEDEA) else Color(0xFFFFF4E5),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        q.level.displayName(),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (danger) Color(0xFF8C1D18) else Color(0xFF8A4B00),
                    )
                    if (danger) {
                        Text(
                            "不是文件损坏。放大无法恢复已经丢失的文字细节。",
                            fontSize = 13.sp,
                            color = Color(0xFF8C1D18),
                        )
                    }
                    Text(
                        q.conclusion,
                        fontSize = 13.sp,
                        color = if (danger) Color(0xFF8C1D18) else Color(0xFF8A4B00),
                    )
                }
            }
        }
    }
}

@Composable
private fun HeightControl(
    settings: ProcessSettings,
    displaySpec: DisplaySpec,
    contentWidth: Int,
    onTargetChange: (Int) -> Unit,
) {
    val minTarget = (displaySpec.maxSliceHeightPx * 0.5f).toInt().coerceAtLeast(400)
    val maxTarget = displaySpec.maxSliceHeightPx
    val percent = displaySpec.percentOfScreenHeight(settings.targetHeight)
    val caption = MiuixTheme.colorScheme.onSurfaceSecondary
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("每张高度", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text(
                    if (settings.targetHeight >= maxTarget) "按当前设备屏幕像素高度裁切" else "占屏幕高度 $percent%",
                    fontSize = 13.sp,
                    color = caption,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            PxField(
                value = settings.targetHeight,
                min = minTarget,
                max = maxTarget,
                onValueChange = onTargetChange,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                text = "按屏幕高度",
                onClick = { onTargetChange(maxTarget) },
            )
            TextButton(
                text = "紧凑半屏",
                onClick = { onTargetChange(minTarget) },
            )
            Text(
                "滑块快速调整，输入框精确输入",
                fontSize = 12.sp,
                color = caption,
            )
        }
        if (maxTarget > minTarget) {
            ThumbSlider(
                value = settings.targetHeight.coerceIn(minTarget, maxTarget).toFloat(),
                onValueChange = { onTargetChange(it.toInt()) },
                min = minTarget.toFloat(),
                max = maxTarget.toFloat(),
            )
        }
        Text(
            "屏幕长边 $maxTarget px",
            fontSize = 12.sp,
            color = caption,
            maxLines = 1,
            softWrap = false,
        )
        if (contentWidth > displaySpec.shortSidePx) {
            Text(
                "原图宽 $contentWidth px，比屏幕宽。输出保留完整宽度，不缩放、不横向裁切。",
                fontSize = 13.sp,
                color = caption,
            )
        }
    }
}

@Composable
private fun OverlapControl(
    settings: ProcessSettings,
    sliceCount: Int,
    onChange: (Int) -> Unit,
) {
    val caption = MiuixTheme.colorScheme.onSurfaceSecondary
    if (sliceCount <= 1) {
        Column {
            Text("图片重叠", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text("保留连续阅读上下文（当前只需一张）", fontSize = 13.sp, color = caption)
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("图片重叠", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text(if (settings.overlap == 0) "无重复：严格沿蓝色切线分割" else "重叠 ${settings.overlap}px：相邻切片会重复这一段内容", fontSize = 13.sp, color = caption)
            }
            PxField(
                value = settings.overlap,
                min = 0,
                max = 160,
                onValueChange = onChange,
            )
        }
        ThumbSlider(
            value = settings.overlap.coerceIn(0, 160).toFloat(),
            onValueChange = { onChange(it.toInt()) },
            min = 0f,
            max = 160f,
        )
    }
}

@Composable
private fun MoreOptions(
    settings: ProcessSettings,
    src: SourceInfo,
    manualMargins: com.changjietu.app.image.analysis.Margins?,
    onChange: ((ProcessSettings) -> ProcessSettings) -> Unit,
    onManualMarginsChange: ((com.changjietu.app.image.analysis.Margins) -> com.changjietu.app.image.analysis.Margins) -> Unit,
    onClearManualMargins: () -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val caption = MiuixTheme.colorScheme.onSurfaceSecondary
    val eff = manualMargins ?: SafeCropMargins.automatic(src.margins)
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .clickable { open = !open },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("更多选项", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(if (open) "收起" else "页码、裁边、输出格式", fontSize = 13.sp, color = caption)
        }
        if (open) {
            Text(
                "黄色框 = 实际输出边界；自动仅裁上下空白，左右裁边必须手动指定。",
                fontSize = 13.sp,
                color = caption,
                modifier = Modifier.padding(vertical = 6.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("左裁边", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                PxField(
                    value = eff.left,
                    min = 0,
                    max = (src.width / 2).coerceAtLeast(1),
                    onValueChange = { v -> onManualMarginsChange { it.copy(left = v) } },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("右裁边", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                PxField(
                    value = eff.right,
                    min = 0,
                    max = (src.width / 2).coerceAtLeast(1),
                    onValueChange = { v -> onManualMarginsChange { it.copy(right = v) } },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("上裁边", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                PxField(
                    value = eff.top,
                    min = 0,
                    max = (src.height / 2).coerceAtLeast(1),
                    onValueChange = { v -> onManualMarginsChange { it.copy(top = v) } },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("下裁边", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                PxField(
                    value = eff.bottom,
                    min = 0,
                    max = (src.height / 2).coerceAtLeast(1),
                    onValueChange = { v -> onManualMarginsChange { it.copy(bottom = v) } },
                )
            }
            Text(
                "检测值：左 ${src.margins.left} · 右 ${src.margins.right} · 上 ${src.margins.top} · 下 ${src.margins.bottom} px",
                fontSize = 12.sp,
                color = caption,
                maxLines = 1,
                softWrap = false,
            )
            if (manualMargins != null) {
                TextButton(text = "恢复安全自动值", onClick = onClearManualMargins)
            }
            SuperSwitch(
                title = "页码角标",
                summary = "半透明 01/N，自动避开正文",
                checked = settings.drawBadge,
                onCheckedChange = { checked -> onChange { it.copy(drawBadge = checked) } },
            )
            Text(
                if (settings.overlap == 0) "当前裁切设置：严格按线分割 · 无重叠 · 自动编号" else "当前裁切设置：重叠 ${settings.overlap}px · 自动编号",
                fontSize = 13.sp,
                color = caption,
                modifier = Modifier.padding(vertical = 6.dp),
            )
            Text(
                "输出为 JPEG ${settings.jpegQuality}%。像素尺寸与原图一致，不会缩放。" +
                    "JPEG 会重新压缩，文字边缘可能略有损失，这不是无损画质。",
                fontSize = 13.sp,
                color = caption,
            )
        }
    }
}

@Composable
private fun PxField(
    value: Int,
    min: Int,
    max: Int,
    onValueChange: (Int) -> Unit,
) {
    var text by remember { mutableStateOf(value.toString()) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(value, focused) {
        if (!focused) text = value.toString()
    }
    val focusManager = LocalFocusManager.current
    BasicTextField(
        value = text,
        onValueChange = { raw -> text = raw.filter { it.isDigit() }.take(5) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(
            onDone = {
                commitPx(text, min, max, onValueChange)
                focusManager.clearFocus()
            },
        ),
        textStyle = TextStyle(
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onBackground,
            textAlign = TextAlign.End,
        ),
        modifier = Modifier
            .width(112.dp)
            .onFocusChanged { state ->
                if (focused && !state.isFocused) {
                    commitPx(text, min, max, onValueChange)
                }
                focused = state.isFocused
            },
        decorationBox = { inner ->
            Box(
                Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .border(1.dp, MiuixTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { inner() }
                    Spacer(Modifier.width(4.dp))
                    Text("px", fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceSecondary)
                }
            }
        },
    )
}

private fun commitPx(text: String, min: Int, max: Int, onValueChange: (Int) -> Unit) {
    val n = text.toIntOrNull() ?: return
    onValueChange(n.coerceIn(min, max))
}

@Composable
private fun ThumbSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    min: Float,
    max: Float,
) {
    val primary = MiuixTheme.colorScheme.primary
    val fraction = if (max > min) ((value - min) / (max - min)).coerceIn(0f, 1f) else 0f
    Slider(
        progress = value,
        onProgressChange = onValueChange,
        minValue = min,
        maxValue = max,
        height = 6.dp,
        decimalPlaces = 0,
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .drawWithContent {
                drawContent()
                val radius = 9.dp.toPx()
                val cx = (fraction * size.width).coerceIn(radius, (size.width - radius).coerceAtLeast(radius))
                val center = Offset(cx, size.height / 2f)
                drawCircle(color = Color.White, radius = radius, center = center)
                drawCircle(
                    color = primary,
                    radius = radius,
                    center = center,
                    style = Stroke(width = 2.dp.toPx()),
                )
            },
    )
}

@Composable
private fun CutPreviewDialog(
    bitmap: ImageBitmap,
    plan: PreviewPlan,
    showCrop: Boolean,
    manualActive: Boolean,
    uncertainCutEnds: Set<Int>,
    onCutPreview: (Int, Int) -> Unit,
    onAddCutPoint: (Int) -> Unit,
    onClearManualCuts: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        ChangjietuTheme {
            Surface(color = MiuixTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding(),
                ) {
                    SmallTopAppBar(
                        title = "调整切线",
                        color = MiuixTheme.colorScheme.background,
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(MiuixIcons.ArrowBack, contentDescription = "关闭")
                            }
                        },
                    )
                    PreviewCutEditor(
                        bitmap = bitmap,
                        plan = plan,
                        showCrop = showCrop,
                        manualActive = manualActive,
                        uncertainCutEnds = uncertainCutEnds,
                        onPreviewCut = onCutPreview,
                        onAddCutPoint = onAddCutPoint,
                        onClearManualCuts = onClearManualCuts,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp),
                    )
                }
            }
        }
    }
}

private fun marginSummary(
    src: SourceInfo,
    cropEnabled: Boolean,
    manualMargins: com.changjietu.app.image.analysis.Margins?,
): String {
    if (!cropEnabled) return "当前保留原图全部像素；开启后默认仅自动裁上下空白"
    val m = SafeCropMargins.effective(cropEnabled, src.margins, manualMargins)
    if (m.isEmpty()) return "当前没有裁边；左右边缘保留"
    val detail = "左 ${m.left} · 右 ${m.right} · 上 ${m.top} · 下 ${m.bottom} px"
    return "实际将裁掉 $detail（左右只在手动设置后生效）"
}
