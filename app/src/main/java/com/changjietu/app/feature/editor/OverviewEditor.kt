package com.changjietu.app.feature.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.changjietu.app.core.EditorState
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.icons.ArrowUpDown
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

/** 单个超高 composable 的显示高度上限（dp），避免超大 layer；超过时纵向压缩，坐标映射仍线性。 */
private const val MAX_VIEW_HEIGHT_DP = 6000f

/** 切点/裁切手柄直径。 */
private val HANDLE_SIZE = 38.dp

private val CutLineColor = Color(0xFF3482FF)
private val CropLineColor = Color(0xFFFFB95C)

/**
 * 剪辑式编辑视图：直接在导入的长截图总览上显示自动切分线，
 * 用「上下箭头」手柄无级拖动切点（向下 = 上片多一点 / 下片少一点），
 * 支持点击新增切入点、头尾整体裁切范围手柄。
 *
 * 坐标统一线性映射：contentY = pointerY / viewHeightPx * contentHeight。
 * 拖动只走 [onPreviewCut] / [onPreviewCrop]（零 IO），松手由 [onCommit] 一次性重切。
 */
@Composable
fun OverviewEditor(
    overview: OverviewState?,
    editor: EditorState,
    enabled: Boolean,
    onPreviewCut: (Int, Int) -> Unit,
    onPreviewCrop: (Int, Int) -> Unit,
    onCommit: () -> Unit,
    onAddCutPoint: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var addMode by remember { mutableStateOf(false) }
    val contentHeight = editor.contentHeight.coerceAtLeast(1)
    val visibleCuts = editor.cutEnds
        .dropLast(1)
        .filter { it > editor.cropTop && it < editor.cropBottom }

    // 用普通 Column 而非 miuix Card：Card 内部 Column 按内容包裹高度，
    // weight(1f) 拿不到有界空间。这里根 Column 直接继承外部传入的有界高度。
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MiuixTheme.colorScheme.surface)
            .border(1.dp, MiuixTheme.colorScheme.outline, RoundedCornerShape(16.dp)),
    ) {
        OverviewHeader(
            cutCount = visibleCuts.size,
            addMode = addMode,
            enabled = enabled,
            onToggleAddMode = { addMode = !addMode },
        )
        if (overview == null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("长图总览生成中…", color = MiuixTheme.colorScheme.onBackgroundVariant)
            }
            return@Column
        }

        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            val density = LocalDensity.current
            val bitmap = overview.bitmap
            val aspect = bitmap.height.toFloat() / bitmap.width.coerceAtLeast(1).toFloat()
            val viewHeightDp = (maxWidth.value * aspect)
                .coerceAtLeast(1f)
                .coerceAtMost(MAX_VIEW_HEIGHT_DP)
            val viewHeightPx = with(density) { viewHeightDp.dp.toPx() }
            val handleSizePx = with(density) { HANDLE_SIZE.toPx() }
            val scrollState = rememberScrollState()

            Box(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(viewHeightDp.dp)
                        .pointerInput(addMode, enabled, contentHeight, viewHeightPx) {
                            if (!enabled) return@pointerInput
                            detectTapGestures(
                                onTap = { tap ->
                                    if (!addMode) return@detectTapGestures
                                    onAddCutPoint(
                                        (tap.y / viewHeightPx * contentHeight).roundToInt(),
                                    )
                                },
                                onLongPress = { tap ->
                                    onAddCutPoint(
                                        (tap.y / viewHeightPx * contentHeight).roundToInt(),
                                    )
                                },
                            )
                        },
                ) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = "导入的长截图总览",
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize(),
                    )

                    OverviewCanvas(
                        viewHeightPx = viewHeightPx,
                        contentHeight = contentHeight,
                        cropTop = editor.cropTop,
                        cropBottom = editor.cropBottom,
                        cuts = visibleCuts,
                    )

                    // 片段序号标签
                    val boundaries = listOf(editor.cropTop) + visibleCuts + listOf(editor.cropBottom)
                    for (i in 0 until boundaries.size - 1) {
                        val center = (boundaries[i] + boundaries[i + 1]) / 2f
                        val yPx = center / contentHeight * viewHeightPx
                        SegmentLabel(
                            label = "%02d".format(i + 1),
                            yPx = yPx,
                        )
                    }

                    // 头尾整体裁切手柄
                    val cropTopState = rememberUpdatedState(editor.cropTop)
                    val cropBottomState = rememberUpdatedState(editor.cropBottom)
                    VerticalDragHandle(
                        yPx = editor.cropTop.toFloat() / contentHeight * viewHeightPx,
                        color = CropLineColor,
                        enabled = enabled,
                        viewHeightPx = viewHeightPx,
                        contentHeight = contentHeight,
                        startValue = editor.cropTop,
                        handleSizePx = handleSizePx,
                        onDelta = { base, delta ->
                            onPreviewCrop(
                                (base + delta).roundToInt(),
                                cropBottomState.value,
                            )
                        },
                        onEnd = onCommit,
                        label = "顶",
                    )
                    VerticalDragHandle(
                        yPx = editor.cropBottom.toFloat() / contentHeight * viewHeightPx,
                        color = CropLineColor,
                        enabled = enabled,
                        viewHeightPx = viewHeightPx,
                        contentHeight = contentHeight,
                        startValue = editor.cropBottom,
                        handleSizePx = handleSizePx,
                        onDelta = { base, delta ->
                            onPreviewCrop(
                                cropTopState.value,
                                (base + delta).roundToInt(),
                            )
                        },
                        onEnd = onCommit,
                        label = "底",
                    )

                    // 自动切分线手柄（上下箭头，无级调节）
                    visibleCuts.forEachIndexed { index, end ->
                        val cutIndex = editor.cutEnds.indexOf(end)
                        if (cutIndex >= 0) {
                            VerticalDragHandle(
                                yPx = end.toFloat() / contentHeight * viewHeightPx,
                                color = CutLineColor,
                                enabled = enabled,
                                viewHeightPx = viewHeightPx,
                                contentHeight = contentHeight,
                                startValue = end,
                                handleSizePx = handleSizePx,
                                onDelta = { base, delta ->
                                    onPreviewCut(cutIndex, (base + delta).roundToInt())
                                },
                                onEnd = onCommit,
                                icon = true,
                            )
                        }
                    }
                }
            }
        }
        Text(
            "拖空白处上下滑动预览。拖 ⇅ 调整切点：向下 = 上片多一点、下片少一点。长按长图新增切入点。橙色手柄是整体截取范围。",
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun OverviewHeader(
    cutCount: Int,
    addMode: Boolean,
    enabled: Boolean,
    onToggleAddMode: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("剪辑视图", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Text(
                if (addMode) "点击长图任意位置新增切入点" else "$cutCount 个切点 · 拖动预览，长按可加切点",
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                fontSize = 11.sp,
            )
        }
        TextButton(
            text = if (addMode) "取消新增" else "＋ 新增切入点",
            onClick = onToggleAddMode,
            enabled = enabled,
        )
    }
}

@Composable
private fun OverviewCanvas(
    viewHeightPx: Float,
    contentHeight: Int,
    cropTop: Int,
    cropBottom: Int,
    cuts: List<Int>,
) {
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        // 与手柄/手势统一使用 viewHeightPx，避免 Canvas 实际像素与理论值有偏差时线条错位。
        val h = viewHeightPx
        val topY = cropTop.toFloat() / contentHeight * h
        val bottomY = cropBottom.toFloat() / contentHeight * h
        // 裁掉的区域压暗
        drawRect(
            Color.Black.copy(alpha = 0.45f),
            Offset(0f, 0f),
            Size(w, topY.coerceAtLeast(0f)),
        )
        drawRect(
            Color.Black.copy(alpha = 0.45f),
            Offset(0f, bottomY),
            Size(w, (h - bottomY).coerceAtLeast(0f)),
        )
        // 整体截取范围边界
        drawLine(CropLineColor, Offset(0f, topY), Offset(w, topY), strokeWidth = 3.dp.toPx())
        drawLine(CropLineColor, Offset(0f, bottomY), Offset(w, bottomY), strokeWidth = 3.dp.toPx())
        // 自动切分线
        for (end in cuts) {
            val y = end.toFloat() / contentHeight * h
            drawLine(CutLineColor, Offset(0f, y), Offset(w, y), strokeWidth = 3.dp.toPx())
        }
    }
}

@Composable
private fun BoxScope.SegmentLabel(label: String, yPx: Float) {
    Box(
        Modifier
            .align(Alignment.TopStart)
            .offset { IntOffset(6.dp.toPx().roundToInt(), (yPx - 10.dp.toPx()).roundToInt()) }
            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(label, color = Color.White, fontSize = 10.sp)
    }
}

@Composable
private fun BoxScope.VerticalDragHandle(
    yPx: Float,
    color: Color,
    enabled: Boolean,
    viewHeightPx: Float,
    contentHeight: Int,
    startValue: Int,
    handleSizePx: Float,
    onDelta: (baseValue: Int, deltaContentPx: Float) -> Unit,
    onEnd: () -> Unit,
    icon: Boolean = false,
    label: String = "",
) {
    val valueState = rememberUpdatedState(startValue)
    Box(
        Modifier
            .align(Alignment.TopCenter)
            .offset {
                val top = (yPx - handleSizePx / 2f)
                    .coerceIn(0f, (viewHeightPx - handleSizePx).coerceAtLeast(0f))
                IntOffset(0, top.roundToInt())
            }
            .size(HANDLE_SIZE)
            .border(2.dp, Color.White, CircleShape)
            .background(color, CircleShape)
            .pointerInput(contentHeight, viewHeightPx, enabled) {
                if (!enabled) return@pointerInput
                // 在 Initial 阶段吃掉按下，避免外层纵向滚动把切点拖动当成「拖动预览」。
                awaitEachGesture {
                    val down = awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial,
                    )
                    down.consume()
                    var accumulated = 0f
                    val base = valueState.value
                    verticalDrag(down.id) { change ->
                        val dy = change.position.y - change.previousPosition.y
                        change.consume()
                        accumulated += dy
                        onDelta(base, accumulated / viewHeightPx * contentHeight)
                    }
                    onEnd()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (icon) {
            Icon(
                MiuixIcons.ArrowUpDown,
                contentDescription = "拖动调整切点",
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Text(label, color = Color.White, fontSize = 11.sp)
        }
    }
}
