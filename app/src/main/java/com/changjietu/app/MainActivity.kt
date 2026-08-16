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
        // 配置重建时 ViewModel 存活并已消费过分享，跳过以避免重复处理原始 Intent；
        // 进程死亡重建时 ViewModel 新建、consumed=false，则重新消费（cold-start 恢复）。
        if (!viewModel.hasConsumedShare()) {
            handleShareIntent(intent)
        }
        setContent {
            ChangjietuTheme {
                val nav = rememberNavController()
                val source by viewModel.source.collectAsStateWithLifecycle()
                val editor by viewModel.editor.collectAsStateWithLifecycle()
                val pendingShareId by viewModel.pendingShareId.collectAsStateWithLifecycle()

                // 分享进入导航的 one-shot：仅当有待处理请求时导航一次，随后 ack 清空。
                // pending 存于 ViewModel，collector 未建立时也不丢失；ack 后配置重建/重组合
                // 不再导航、不再重开 URI；新 onNewIntent 产生新 pending → 必然触发。
                LaunchedEffect(pendingShareId) {
                    val id = pendingShareId
                    if (id != null) {
                        nav.navigate("process") {
                            launchSingleTop = true
                        }
                        viewModel.ackSharePending(id)
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
