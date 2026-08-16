package com.changjietu.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
        enableEdgeToEdge()
        handleShareIntent(intent)
        setContent {
            ChangjietuTheme {
                val nav = rememberNavController()
                val source by viewModel.source.collectAsStateWithLifecycle()
                val editor by viewModel.editor.collectAsStateWithLifecycle()
                val shareRequestId by viewModel.shareRequestId.collectAsStateWithLifecycle()

                // 分享进入导航的单一事实来源：任何新外部分享（有效或无效）都回到处理页。
                // 序号由 ViewModel 单调递增；配置变更后序号不变，故不会重复导航。
                LaunchedEffect(shareRequestId) {
                    if (shareRequestId > 0) {
                        nav.navigate("process") {
                            launchSingleTop = true
                        }
                    }
                }

                // 处理完成后自动进 preview
                LaunchedEffect(editor?.slices?.size, editor?.isProcessing) {
                    val ed = editor
                    if (ed != null && !ed.isProcessing && ed.slices.isNotEmpty()) {
                        val route = nav.currentBackStackEntry?.destination?.route
                        if (route == "process" || route == "home") {
                            nav.navigate("preview") {
                                launchSingleTop = true
                            }
                        }
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
                            onProcessed = {
                                // 导航由 LaunchedEffect 在切片完成后触发
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
