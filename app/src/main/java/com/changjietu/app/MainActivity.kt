package com.changjietu.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.changjietu.app.core.ShareContract
import com.changjietu.app.feature.editor.LongshotViewModel
import com.changjietu.app.feature.editor.PreviewScreen
import com.changjietu.app.feature.editor.ProcessScreen
import com.changjietu.app.feature.importui.HomeScreen
import com.changjietu.app.ui.theme.ChangjietuTheme

class MainActivity : ComponentActivity() {

    private val viewModel: LongshotViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // targetSdk 35 默认边到边，系统会在对比度不够时给状态栏加一层遮罩，看起来像顶栏没贴合。
        if (Build.VERSION.SDK_INT >= 29) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        enableEdgeToEdge()
        // 配置重建时 ViewModel 存活并已消费过分享，跳过以避免重复处理原始 Intent；
        // 进程死亡重建时 ViewModel 新建、consumed=false，则重新消费（cold-start 恢复）。
        if (!viewModel.hasConsumedShare()) {
            handleShareIntent(intent)
        }
        setContent {
            val darkTheme = isSystemInDarkTheme()
            // 边到边布局下状态栏/导航栏透明，图标深浅必须随系统深浅色显式设置，
            // 否则浅色主题会出现白底白图标（顶栏状态栏不适配）。
            LaunchedEffect(darkTheme) {
                WindowInsetsControllerCompat(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
            ChangjietuTheme(darkTheme = darkTheme) {
                val nav = rememberNavController()
                val source by viewModel.source.collectAsStateWithLifecycle()
                val pendingShareId by viewModel.pendingShareId.collectAsStateWithLifecycle()
                val pendingProcessResultId by viewModel.pendingProcessResultId.collectAsStateWithLifecycle()

                // 分享进入导航的 one-shot：仅当有待处理请求时导航一次，随后 ack 清空。
                // pending 存于 ViewModel，collector 未建立时也不丢失；ack 后配置重建/重组合
                // 不再导航、不再重开 URI；新 onNewIntent 产生新 pending → 必然触发。
                LaunchedEffect(pendingShareId) {
                    val id = pendingShareId
                    if (id != null) {
                        nav.navigate("process") {
                            popUpTo("home") { inclusive = false }
                            launchSingleTop = true
                        }
                        viewModel.ackSharePending(id)
                    }
                }

                // 处理完成导航的 one-shot：pendingProcessResultId 由初始 processing 成功结束时产生，
                // 与 slices 持久业务状态解耦。观察一次导航进 preview 后 ack 清空；
                // 返回后旧 slices 不再把用户弹回 preview；配置重建/重组合不重放；
                // 手动切片编辑不产生新 pending → 不误导航。失败不产生 pending → 不导航。
                LaunchedEffect(pendingProcessResultId) {
                    val id = pendingProcessResultId
                    if (id != null) {
                        nav.navigate("preview") {
                            launchSingleTop = true
                        }
                        viewModel.ackProcessResult(id)
                    }
                }

                NavHost(navController = nav, startDestination = "home") {
                    composable("home") {
                        HomeScreen(
                            viewModel = viewModel,
                            onOpened = {
                                nav.navigate("process") {
                                    launchSingleTop = true
                                }
                            },
                        )
                    }
                    composable("process") {
                        ProcessScreen(
                            viewModel = viewModel,
                            onBack = {
                                nav.popBackStack("home", inclusive = false)
                            },
                        )
                    }
                    composable("preview") {
                        PreviewScreen(
                            viewModel = viewModel,
                            onBack = {
                                if (!nav.popBackStack()) {
                                    nav.navigate("home")
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // singleTop：Activity 已存在时新分享经此入口到达；更新 intent 以便进程重建后仍可取最新。
        setIntent(intent)
        handleShareIntent(intent)
    }

    /**
     * 统一消费外部分享 Intent（cold-start 走 [onCreate]，warm/singleTop 走 [onNewIntent]）。
     * 从 Intent 提取纯数据后交给 [ShareContract] 判定，有效则加载并持久化读权限，
     * 无效则 graceful fail。同一个 Intent 只在一次生命周期交付中消费一次。
     */
    private fun handleShareIntent(intent: Intent?) {
        val action = intent?.action
        val mime = intent?.type
        if (action == null) return
        val streams: List<String?> = when (action) {
            Intent.ACTION_SEND -> listOf(readStreamUri(intent)?.toString())
            Intent.ACTION_SEND_MULTIPLE -> readStreamUris(intent).map { it.toString() }
            else -> return
        }
        when (val input = ShareContract.resolve(action, mime, streams)) {
            is ShareContract.Input.Single -> viewModel.consumeShare(Uri.parse(input.uri), intent.flags)
            ShareContract.Input.Invalid -> viewModel.openInvalidShare()
        }
    }

    private fun readStreamUri(intent: Intent): Uri? =
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
        }

    @Suppress("DEPRECATION")
    private fun readStreamUris(intent: Intent): List<Uri> {
        val list: List<Uri?> =
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            } else {
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
            }
        return list.filterNotNull()
    }
}
