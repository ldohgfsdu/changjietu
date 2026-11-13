package com.changjietu.app.feature.importui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.changjietu.app.core.icons.AppIcons
import com.changjietu.app.feature.editor.HomeUiState
import com.changjietu.app.feature.editor.LongshotViewModel
import com.changjietu.app.feature.history.RecentItem
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    viewModel: LongshotViewModel,
    onOpened: () -> Unit,
) {
    val homeState by viewModel.homeState.collectAsStateWithLifecycle()
    val recents by viewModel.recents.collectAsStateWithLifecycle()

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.openPickerUri(uri)
            onOpened()
        }
    }
    val caption = MiuixTheme.colorScheme.onSurfaceSecondary

    Scaffold(
        containerColor = MiuixTheme.colorScheme.background,
        // 首页无 TopAppBar，内容需要自己避开状态栏。
        contentWindowInsets = WindowInsets.statusBars,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(top = 16.dp),
                ) {
                    Text(
                        "长截图",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MiuixTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "原尺寸裁切，支持相册导入和应用分享",
                        fontSize = 14.sp,
                        color = caption,
                    )
                }
            }
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                ) {
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = {
                            picker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                        colors = ButtonDefaults.buttonColorsPrimary(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                    ) {
                        // 文字必须相对按钮边界居中，不能把图标+间距作为同一整体居中。
                        // 首页主操作使用单独的文字，不为装饰图标预留不可见偏移。
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "选择图片",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    when (val state = homeState) {
                        is HomeUiState.Loading -> {
                            Spacer(Modifier.height(12.dp))
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            Spacer(Modifier.height(6.dp))
                            Text(state.message, fontSize = 13.sp, color = caption)
                        }
                        is HomeUiState.Error -> {
                            Spacer(Modifier.height(12.dp))
                            Surface(color = Color(0xFFFFEDEA), shape = RoundedCornerShape(12.dp)) {
                                Text(
                                    state.message,
                                    fontSize = 13.sp,
                                    color = Color(0xFF8C1D18),
                                    modifier = Modifier.padding(12.dp),
                                )
                            }
                        }
                        HomeUiState.Idle -> Unit
                    }
                }
            }
            item {
                Text(
                    "最近处理",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = caption,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
            if (recents.isEmpty()) {
                item {
                    Text(
                        "还没有记录。处理完成后会出现在这里。",
                        fontSize = 14.sp,
                        color = caption,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
            } else {
                items(recents, key = { it.uri + it.timestamp }) { item ->
                    RecentRow(
                        item = item,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    ) {
                        viewModel.openRecent(Uri.parse(item.uri))
                        onOpened()
                    }
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun RecentRow(
    item: RecentItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val fmt = SimpleDateFormat("M月d日 HH:mm", Locale.getDefault())
    val caption = MiuixTheme.colorScheme.onSurfaceSecondary
    var thumbFailed by remember(item.uri) { mutableStateOf(false) }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MiuixTheme.colorScheme.surface,
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 44.dp, height = 58.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MiuixTheme.colorScheme.surfaceContainer),
                contentAlignment = Alignment.Center,
            ) {
                if (thumbFailed) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Icon(
                            AppIcons.Image,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = caption,
                        )
                        Text(
                            "原图不可用",
                            fontSize = 9.sp,
                            color = caption,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                } else {
                    AsyncImage(
                        model = Uri.parse(item.uri),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        onError = { thumbFailed = true },
                        onSuccess = { thumbFailed = false },
                        modifier = Modifier
                            .size(width = 44.dp, height = 58.dp),
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${fmt.format(Date(item.timestamp))}，${item.sliceCount} 张切片",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${item.width} × ${item.height}",
                    fontSize = 13.sp,
                    color = caption,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text("›", fontSize = 22.sp, color = caption)
        }
    }
}
