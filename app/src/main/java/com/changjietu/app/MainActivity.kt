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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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
        val sharedUri = extractSharedUri(intent)
        setContent {
            ChangjietuTheme {
                val nav = rememberNavController()
                var pendingShare by remember { mutableStateOf(sharedUri) }
                val source by viewModel.source.collectAsStateWithLifecycle()
                val editor by viewModel.editor.collectAsStateWithLifecycle()

                LaunchedEffect(pendingShare) {
                    val uri = pendingShare
                    if (uri != null) {
                        viewModel.openUri(uri)
                        pendingShare = null
                        nav.navigate("process") {
                            launchSingleTop = true
                        }
                    }
                }

                LaunchedEffect(source) {
                    // 从首页选图后若已在 process 则刷新
                }

                // 处理完成后若用户在 process 点了开始，自动进 preview
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
        setIntent(intent)
        extractSharedUri(intent)?.let { viewModel.openUri(it) }
    }

    private fun extractSharedUri(intent: Intent?): Uri? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_SEND -> {
                if (android.os.Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> null
            else -> null
        }
    }
}
