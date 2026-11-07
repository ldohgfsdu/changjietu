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

    // 结果页工具栏的轻量图标：使用本地矢量，避免重新引入 material-icons-extended。
    val Expand: ImageVector by lazy {
        vector("AppIcons.Expand", "M4 10V4h6v2H6v4H4zm10-6h6v6h-2V6h-4V4zM4 14h2v4h4v2H4v-6zm14 0h2v6h-6v-2h4v-4z")
    }
    val Plus: ImageVector by lazy {
        vector("AppIcons.Plus", "M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z")
    }
    val ChevronUp: ImageVector by lazy {
        vector("AppIcons.ChevronUp", "M12 8l6 6-1.41 1.41L12 10.83l-4.59 4.58L6 14z")
    }
    val ChevronDown: ImageVector by lazy {
        vector("AppIcons.ChevronDown", "M6 10l1.41-1.41L12 13.17l4.59-4.58L18 10l-6 6z")
    }
    val MoreVert: ImageVector by lazy {
        vector("AppIcons.MoreVert", "M12 8a2 2 0 1 0 0-4 2 2 0 0 0 0 4zm0 2a2 2 0 1 0 0 4 2 2 0 0 0 0-4zm0 6a2 2 0 1 0 0 4 2 2 0 0 0 0-4z")
    }
    val Download: ImageVector by lazy {
        vector("AppIcons.Download", "M5 20h14v-2H5v2zm7-3 5-5-1.41-1.41L13 13.17V3h-2v10.17l-2.59-2.58L7 12l5 5z")
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