package com.changjietu.app.feature.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.changjietu.app.core.ProcessMode
import com.changjietu.app.image.analysis.QualityLevel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProcessScreen(
    viewModel: LongshotViewModel,
    onBack: () -> Unit,
    onProcessed: () -> Unit,
) {
    val source by viewModel.source.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val editor by viewModel.editor.collectAsStateWithLifecycle()
    val homeState by viewModel.homeState.collectAsStateWithLifecycle()
    val src = source

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("处理设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        if (src == null) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                when (val st = homeState) {
                    is HomeUiState.Loading -> {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text(st.message)
                    }
                    is HomeUiState.Error -> Text(st.message, color = MaterialTheme.colorScheme.error)
                    HomeUiState.Idle -> Text("未选择图片，请返回首页。")
                }
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            QualityCard(src.quality.summary, src.width, src.height, src.quality.level, src.quality.conclusion, src.quality.suggestion)

            Text("预计输出：${src.estimatedSlices} 张", style = MaterialTheme.typography.titleMedium)

            Text("模式", style = MaterialTheme.typography.titleSmall)
            ModeRow(
                selected = settings.mode,
                onSelect = { mode -> viewModel.updateSettings { it.copy(mode = mode) } },
            )

            Text("单张高度  ${settings.targetHeight} px")
            Slider(
                value = settings.targetHeight.toFloat(),
                onValueChange = { v ->
                    viewModel.updateSettings { it.copy(targetHeight = v.toInt()) }
                },
                valueRange = 800f..4000f,
            )

            Text("相邻重叠  ${settings.overlap} px")
            Slider(
                value = settings.overlap.toFloat(),
                onValueChange = { v ->
                    viewModel.updateSettings { it.copy(overlap = v.toInt()) }
                },
                valueRange = 100f..160f,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("裁剪空白边")
                Switch(
                    checked = settings.cropMargins,
                    onCheckedChange = { c -> viewModel.updateSettings { it.copy(cropMargins = c) } },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("切片角标编号")
                Switch(
                    checked = settings.drawBadge,
                    onCheckedChange = { c -> viewModel.updateSettings { it.copy(drawBadge = c) } },
                )
            }

            if (src.margins.left + src.margins.right + src.margins.top + src.margins.bottom > 0) {
                Text(
                    "检测到空白边缘",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            editor?.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            if (editor?.isProcessing == true) {
                Text(editor?.statusMessage ?: "处理中…")
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    viewModel.startProcess()
                    onProcessed()
                },
                enabled = editor?.isProcessing != true,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("开始处理")
            }
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("返回")
            }
        }
    }
}

@Composable
fun QualityCard(
    summary: String,
    width: Int,
    height: Int,
    level: QualityLevel,
    conclusion: String,
    suggestion: String,
) {
    val container = when (level) {
        QualityLevel.GOOD -> MaterialTheme.colorScheme.primaryContainer
        QualityLevel.USABLE -> MaterialTheme.colorScheme.secondaryContainer
        QualityLevel.LOW -> MaterialTheme.colorScheme.tertiaryContainer
        QualityLevel.UNRECOVERABLE -> MaterialTheme.colorScheme.errorContainer
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = container),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("原图质量：$summary", style = MaterialTheme.typography.titleMedium)
            Text("原图：$width × $height px")
            Text(conclusion)
            Text(suggestion, style = MaterialTheme.typography.bodySmall)
            if (level == QualityLevel.UNRECOVERABLE) {
                Text(
                    "仍可强制切片，但无法可靠识别正文。本工具不会做「AI 超分恢复原文」。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun ModeRow(selected: ProcessMode, onSelect: (ProcessMode) -> Unit) {
    Column {
        ModeOption("给 AI 看", "切片 + 重叠 + 编号，适合多模态模型", ProcessMode.FOR_AI, selected, onSelect)
    }
}

@Composable
private fun ModeOption(
    title: String,
    subtitle: String,
    mode: ProcessMode,
    selected: ProcessMode,
    onSelect: (ProcessMode) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected == mode,
                onClick = { onSelect(mode) },
                role = Role.RadioButton,
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected == mode, onClick = null)
        Column(Modifier.padding(start = 8.dp)) {
            Text(title)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
