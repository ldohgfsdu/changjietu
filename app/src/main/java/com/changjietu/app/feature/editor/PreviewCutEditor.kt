package com.changjietu.app.feature.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.icons.ArrowUpDown
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

private const val MAX_VIEW_HEIGHT_DP = 6000f

private val HANDLE_SIZE = 38.dp
private val CutLineColor = Color(0xFF3482FF)
private val CropLineColor = Color(0xFFE6A23C)

/**
 * 处理页全屏精细切线编辑：生成前直接在长图上拖动切点（零 IO）。
 * 坐标：plan 切点为 content 坐标，位图为整图源坐标，需加 contentTop。
 */
@Composable
fun PreviewCutEditor(
    bitmap: ImageBitmap,
    plan: PreviewPlan,
    showCrop: Boolean,
    manualActive: Boolean,
    onPreviewCut: (Int, Int) -> Unit,
    onAddCutPoint: (Int) -> Unit,
    onClearManualCuts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cuts = plan.cutEnds.dropLast(1)
    val contentHeight = plan.contentHeight.coerceAtLeast(1)
    val sourceHeight = plan.sourceHeight.coerceAtLeast(1)
    val contentTop = plan.contentTop
    val scrollState = rememberScrollState()
    var selectedCutIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(cuts.size) {
        if (selectedCutIndex >= cuts.size) selectedCutIndex = (cuts.size - 1).coerceAtLeast(0)
    }
    val scope = rememberCoroutineScope()

    fun jumpTo(index: Int, viewHeightPx: Float, viewportHeightPx: Float) {
        if (index !in cuts.indices) return
        val yPx = (contentTop + cuts[index]).toFloat() / sourceHeight * viewHeightPx
        val maxScroll = (viewHeightPx - viewportHeightPx).coerceAtLeast(0f).toInt()
        val target = (yPx - viewportHeightPx / 2f).toInt().coerceIn(0, maxScroll)
        scope.launch { scrollState.animateScrollTo(target) }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            "拖 ⇅ 调整切点 · 长按长图新增切入点",
            color = MiuixTheme.colorScheme.onSurfaceSecondary,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            fontSize = 12.sp,
        )
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val density = LocalDensity.current
            val aspect = bitmap.height.toFloat() / bitmap.width.coerceAtLeast(1).toFloat()
            val viewHeightDp = (maxWidth.value * aspect).coerceAtLeast(1f).coerceAtMost(MAX_VIEW_HEIGHT_DP)
            val viewHeightPx = with(density) { viewHeightDp.dp.toPx() }
            val viewportHeightPx = with(density) { maxHeight.toPx() }
            val handleSizePx = with(density) { HANDLE_SIZE.toPx() }

            Column(Modifier.fillMaxSize()) {
                // 实机 v0.2.6 证明自定义箭头图标不可见。
                // 使用固定宽度的文字触控控件，避免自定义 ImageVector 色彩兼容风险。
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val canPrevious = cuts.isNotEmpty() && selectedCutIndex > 0
                    val canNext = cuts.isNotEmpty() && selectedCutIndex < cuts.lastIndex
                    Box(
                        modifier = Modifier
                            .width(78.dp)
                            .height(48.dp)
                            .clickable(enabled = canPrevious, onClickLabel = "上一条切线") {
                                val next = selectedCutIndex - 1
                                selectedCutIndex = next
                                jumpTo(next, viewHeightPx, viewportHeightPx)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "上一条",
                            fontSize = 14.sp,
                            color = if (canPrevious) MiuixTheme.colorScheme.primary
                                else MiuixTheme.colorScheme.onSurfaceSecondary,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clickable(enabled = cuts.isNotEmpty(), onClickLabel = "定位当前切线") {
                                jumpTo(selectedCutIndex, viewHeightPx, viewportHeightPx)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (cuts.isEmpty()) "无切线" else "第 ${selectedCutIndex + 1}/${cuts.size} 条 · 定位",
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .width(78.dp)
                            .height(48.dp)
                            .clickable(enabled = canNext, onClickLabel = "下一条切线") {
                                val next = selectedCutIndex + 1
                                selectedCutIndex = next
                                jumpTo(next, viewHeightPx, viewportHeightPx)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "下一条",
                            fontSize = 14.sp,
                            color = if (canNext) MiuixTheme.colorScheme.primary
                                else MiuixTheme.colorScheme.onSurfaceSecondary,
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(scrollState),
                ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(viewHeightDp.dp)
                        .pointerInput(contentTop, sourceHeight, viewHeightPx) {
                            detectTapGestures(
                                onLongPress = { tap ->
                                    val contentY = (tap.y / viewHeightPx * sourceHeight - contentTop).roundToInt()
                                    onAddCutPoint(contentY)
                                },
                            )
                        },
                ) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = "长截图切线编辑",
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Canvas(Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = viewHeightPx
                        val cropLeft = plan.contentLeft.toFloat() / plan.sourceWidth.coerceAtLeast(1) * w
                        val cropRight = (plan.contentLeft + plan.contentWidth).toFloat() /
                            plan.sourceWidth.coerceAtLeast(1) * w
                        val cropTopY = contentTop.toFloat() / sourceHeight * h
                        val cropBottomY = (contentTop + contentHeight).toFloat() / sourceHeight * h
                        if (showCrop) {
                            drawRect(
                                color = CropLineColor,
                                topLeft = androidx.compose.ui.geometry.Offset(cropLeft, cropTopY),
                                size = androidx.compose.ui.geometry.Size(
                                    (cropRight - cropLeft).coerceAtLeast(1f),
                                    (cropBottomY - cropTopY).coerceAtLeast(1f),
                                ),
                                style = Stroke(width = 2.dp.toPx()),
                            )
                        }
                        for (i in cuts.indices) {
                            val y = (contentTop + cuts[i]).toFloat() / sourceHeight * h
                            val selected = i == selectedCutIndex
                            drawLine(
                                color = CutLineColor,
                                start = androidx.compose.ui.geometry.Offset(cropLeft, y),
                                end = androidx.compose.ui.geometry.Offset(cropRight, y),
                                strokeWidth = (if (selected) 4.dp else 3.dp).toPx(),
                            )
                        }
                    }

                    val boundaries = buildList {
                        add(0)
                        cuts.forEach { add(it) }
                        add(contentHeight)
                    }
                    for (i in 0 until boundaries.size - 1) {
                        val midContent = (boundaries[i] + boundaries[i + 1]) / 2f
                        val yPx = (contentTop + midContent) / sourceHeight * viewHeightPx
                        Box(
                            Modifier
                                .align(Alignment.TopStart)
                                .offset { IntOffset(6.dp.toPx().roundToInt(), (yPx - 10.dp.toPx()).roundToInt()) }
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text("%02d".format(i + 1), color = Color.White, fontSize = 10.sp)
                        }
                    }

                    cuts.forEachIndexed { index, end ->
                        val selected = index == selectedCutIndex
                        val startValueState = rememberUpdatedState(end)
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .offset {
                                    val top = ((contentTop + end).toFloat() / sourceHeight * viewHeightPx - handleSizePx / 2f)
                                        .coerceIn(0f, (viewHeightPx - handleSizePx).coerceAtLeast(0f))
                                    IntOffset(0, top.roundToInt())
                                }
                                .size(HANDLE_SIZE)
                                .border(if (selected) 3.dp else 2.dp, Color.White, CircleShape)
                                .background(CutLineColor, CircleShape)
                                .pointerInput(index, contentTop, sourceHeight, viewHeightPx) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown(
                                            requireUnconsumed = false,
                                            pass = PointerEventPass.Initial,
                                        )
                                        down.consume()
                                        selectedCutIndex = index
                                        var accumulated = 0f
                                        val base = startValueState.value
                                        verticalDrag(down.id) { change ->
                                            val dy = change.position.y - change.previousPosition.y
                                            change.consume()
                                            accumulated += dy
                                            onPreviewCut(index, (base + accumulated / viewHeightPx * sourceHeight).roundToInt())
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                MiuixIcons.ArrowUpDown,
                                contentDescription = "拖动调整切点",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
            }
        }
        if (manualActive) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "已手动调整 · 恢复自动重回自动",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                )
                TextButton(text = "恢复自动", onClick = onClearManualCuts)
            }
        }
    }
}
