package com.changjietu.app.feature.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.changjietu.app.core.CutPreviewGeometry
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

private val CropLineColor = Color(0xFFE6A23C)

/**
 * 把整图总览和预计切线画在同一个框里。
 * 位图用 [Image]，不交给 Coil。调用方负责点击放大。
 */
@Composable
fun CutPreview(
    bitmap: ImageBitmap?,
    plan: PreviewPlan?,
    showCrop: Boolean,
    emptyText: String,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        if (bitmap == null || plan == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    emptyText,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    fontSize = 13.sp,
                )
            }
            return@BoxWithConstraints
        }
        val density = LocalDensity.current
        val boxW = with(density) { maxWidth.toPx() }
        val boxH = with(density) { maxHeight.toPx() }
        if (boxW <= 1f || boxH <= 1f) return@BoxWithConstraints
        // Small numbers of cuts are more useful as cut-adjacent excerpts than as
        // a full 1:12 long strip. Each tile shows the actual content around one seam.
        if (plan.cutEnds.size in 2..9) {
            CutBoundaryPreview(
                bitmap = bitmap,
                plan = plan,
                showCrop = showCrop,
                modifier = Modifier.fillMaxSize(),
            )
            return@BoxWithConstraints
        }
        val frame = CutPreviewGeometry.fit(boxW, boxH, plan.sourceWidth, plan.sourceHeight)
        Image(
            bitmap = bitmap,
            contentDescription = "长截图切线预览",
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .offset {
                    IntOffset(frame.offsetX.roundToInt(), frame.offsetY.roundToInt())
                }
                .size(
                    with(density) { frame.drawWidth.toDp() },
                    with(density) { frame.drawHeight.toDp() },
                ),
        )
        val primary = MiuixTheme.colorScheme.primary
        val cropLeft = CutPreviewGeometry.xOf(frame, plan.contentLeft, plan.sourceWidth)
        val cropTop = CutPreviewGeometry.yOf(frame, plan.contentTop, plan.sourceHeight)
        val cropRight = CutPreviewGeometry.xOf(
            frame,
            plan.contentLeft + plan.contentWidth,
            plan.sourceWidth,
        )
        val cropBottom = CutPreviewGeometry.yOf(
            frame,
            plan.contentTop + plan.contentHeight,
            plan.sourceHeight,
        )
        val hasMargin = plan.contentLeft > 0 ||
            plan.contentTop > 0 ||
            plan.contentLeft + plan.contentWidth < plan.sourceWidth ||
            plan.contentTop + plan.contentHeight < plan.sourceHeight
        Canvas(Modifier.fillMaxSize()) {
            if (showCrop && hasMargin) {
                drawRect(
                    color = CropLineColor,
                    topLeft = Offset(cropLeft, cropTop),
                    size = androidx.compose.ui.geometry.Size(
                        (cropRight - cropLeft).coerceAtLeast(1f),
                        (cropBottom - cropTop).coerceAtLeast(1f),
                    ),
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
            plan.cutEnds.dropLast(1).forEach { cut ->
                val y = CutPreviewGeometry.yOf(frame, plan.contentTop + cut, plan.sourceHeight)
                drawLine(
                    color = primary,
                    start = Offset(cropLeft, y),
                    end = Offset(cropRight, y),
                    strokeWidth = 2.5.dp.toPx(),
                )
            }
        }
        val boundaries = buildList {
            add(plan.contentTop)
            plan.cutEnds.dropLast(1).forEach { add(plan.contentTop + it) }
            add(plan.contentTop + plan.contentHeight)
        }
        val minBand = with(density) { 14.dp.toPx() }
        boundaries.zipWithNext().forEachIndexed { index, (start, end) ->
            val y0 = CutPreviewGeometry.yOf(frame, start, plan.sourceHeight)
            val y1 = CutPreviewGeometry.yOf(frame, end, plan.sourceHeight)
            if (y1 - y0 < minBand) return@forEachIndexed
            val mid = (y0 + y1) / 2f
            // 编号放在正文区左侧外缘（小气泡），不遮挡正文中心。
            val badgeX = with(density) { (cropLeft - 26.dp.toPx()).coerceAtLeast(frame.offsetX + 2.dp.toPx()) }
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            badgeX.roundToInt(),
                            mid.roundToInt() - 7.dp.roundToPx(),
                        )
                    }
                    .defaultMinSize(minWidth = 22.dp, minHeight = 14.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(primary.copy(alpha = 0.85f))
                    .padding(horizontal = 3.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "%02d".format(index + 1),
                    color = Color.White,
                    fontSize = 9.sp,
                )
            }
        }
    }
}
