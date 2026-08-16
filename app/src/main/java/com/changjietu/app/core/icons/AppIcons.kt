package com.changjietu.app.core.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 本地 Material 图标（项目实际使用、且仅存在于 material-icons-extended 的 2 个图标）。
 *
 * C3 尺寸 A/B 结论：material-icons-extended 贡献约 6.9MB / ~39% 的 debug APK（clean A/B），
 * 而项目仅使用 5 个图标，其中只有 Image 与 CallMerge 是 extended-only
 * （material-icons-core=0，extended>0）。故移除 extended 依赖，用本地 ImageVector 提供这
 * 2 个图标；其余 ArrowBack/Delete/Share 来自 material-icons-core（经 material 传递依赖保留）。
 * 视觉语义与 AndroidX 版本一致。
 *
 * 路径数据取自 Material Design Icons 规范（Apache-2.0）Image / CallMerge 的标准 path。
 */
object AppIcons {

    val Image: ImageVector by lazy {
        vector(
            name = "AppIcons.Image",
            pathData = "M21 19V5c0-1.1-.9-2-2-2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2zM8.5 13.5l2.5 3.01L14.5 12l4.5 6H5l3.5-4.5z",
        )
    }

    val CallMerge: ImageVector by lazy {
        vector(
            name = "AppIcons.CallMerge",
            pathData = "M17 20.41L18.41 19 15 15.59 13.59 17 17 20.41zM7.5 8H11v5.59L5.59 19 7 20.41l6-6V8h3.5L12 3.5 7.5 8z",
        )
    }

    private fun vector(name: String, pathData: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                addPathNodes(pathData)
            }
        }.build()
}