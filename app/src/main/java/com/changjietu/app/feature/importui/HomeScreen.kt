package com.changjietu.app.feature.importui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.changjietu.app.feature.editor.HomeUiState
import com.changjietu.app.feature.editor.LongshotViewModel
import com.changjietu.app.feature.history.RecentItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("长截图处理") })
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "相册或任意 App → 分享 → 长截图处理器\n自动切片，再批量发给 ChatGPT / Grok",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    picker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Image, contentDescription = null)
                Spacer(Modifier.padding(6.dp))
                Text("选择图片")
            }
            Spacer(Modifier.height(16.dp))
            when (val st = homeState) {
                is HomeUiState.Loading -> {
                    CircularProgressIndicator()
                    Text(st.message, modifier = Modifier.padding(top = 8.dp))
                }
                is HomeUiState.Error -> {
                    Text(st.message, color = MaterialTheme.colorScheme.error)
                }
                HomeUiState.Idle -> Unit
            }
            Spacer(Modifier.height(24.dp))
            Text(
                "最近处理",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            if (recents.isEmpty()) {
                Text(
                    "暂无记录。从相册分享长截图到本应用即可开始。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(recents, key = { it.uri + it.timestamp }) { item ->
                        RecentRow(item) {
                            viewModel.openRecent(Uri.parse(item.uri))
                            onOpened()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentRow(item: RecentItem, onClick: () -> Unit) {
    val fmt = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(),
    ) {
        ListItem(
            headlineContent = {
                Text("${item.width} × ${item.height} · ${item.sliceCount} 张")
            },
            supportingContent = {
                Text(fmt.format(Date(item.timestamp)))
            },
        )
    }
}
