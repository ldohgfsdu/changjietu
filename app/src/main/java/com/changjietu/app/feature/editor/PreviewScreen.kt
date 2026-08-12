package com.changjietu.app.feature.editor

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.changjietu.app.image.analysis.QualityLevel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(
    viewModel: LongshotViewModel,
    onBack: () -> Unit,
) {
    val editor by viewModel.editor.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val ed = editor

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("预览切片") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val intent = viewModel.shareIntent()
                            if (intent != null) {
                                context.startActivity(
                                    Intent.createChooser(intent, "分享切片到…"),
                                )
                            } else {
                                Toast.makeText(context, "暂无切片", Toast.LENGTH_SHORT).show()
                            }
                        },
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "分享")
                    }
                },
            )
        },
    ) { padding ->
        if (ed == null) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("尚未生成切片，请返回处理页。")
            }
            return@Scaffold
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
        ) {
            if (ed.source.quality.level == QualityLevel.UNRECOVERABLE ||
                ed.source.quality.level == QualityLevel.LOW
            ) {
                Text(
                    "质量警告：${ed.source.quality.summary} — ${ed.source.quality.conclusion}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }

            Text(
                "原图 ${ed.source.width}×${ed.source.height} · 内容宽 ${ed.contentWidth} · ${ed.slices.size} 张",
                style = MaterialTheme.typography.bodyMedium,
            )
            ed.statusMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            ed.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            if (ed.isProcessing) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            // 分割线微调：选中 cut 后拖动 Slider，松手后重切
            if (ed.cutEnds.size >= 2 && !ed.isProcessing) {
                var selectedCut by remember { mutableIntStateOf(0) }
                val adjustableCount = (ed.cutEnds.size - 1).coerceAtLeast(0)
                val cutIndex = selectedCut.coerceIn(0, (adjustableCount - 1).coerceAtLeast(0))
                if (adjustableCount > 0) {
                    val minY = if (cutIndex == 0) 50f else ed.cutEnds[cutIndex - 1] + 50f
                    val maxY = if (cutIndex >= ed.cutEnds.lastIndex - 1) {
                        ed.contentHeight - 50f
                    } else {
                        ed.cutEnds[cutIndex + 1] - 50f
                    }
                    var sliderValue by remember(ed.cutEnds, cutIndex) {
                        mutableIntStateOf(ed.cutEnds[cutIndex])
                    }
                    Text(
                        "第 ${cutIndex + 1} 个切点 · $sliderValue px",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(adjustableCount.coerceAtMost(8)) { i ->
                            OutlinedButton(onClick = {
                                selectedCut = i
                            }) { Text("${i + 1}") }
                        }
                    }
                    if (maxY > minY) {
                        Slider(
                            value = sliderValue.toFloat().coerceIn(minY, maxY),
                            onValueChange = { sliderValue = it.toInt() },
                            onValueChangeFinished = {
                                viewModel.updateCutEnd(cutIndex, sliderValue)
                            },
                            valueRange = minY..maxY,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                val v = (sliderValue - 100).coerceIn(minY.toInt(), maxY.toInt())
                                sliderValue = v
                                viewModel.updateCutEnd(cutIndex, v)
                            }) { Text("-100") }
                            OutlinedButton(onClick = {
                                val v = (sliderValue + 100).coerceIn(minY.toInt(), maxY.toInt())
                                sliderValue = v
                                viewModel.updateCutEnd(cutIndex, v)
                            }) { Text("+100") }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(ed.slices, key = { _, s -> s.file.absolutePath }) { index, slice ->
                    Card(Modifier.fillMaxWidth()) {
                        Column {
                            AsyncImage(
                                model = slice.file,
                                contentDescription = slice.label,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(0.55f),
                                contentScale = ContentScale.Crop,
                            )
                            Text(
                                "${slice.label}  ${slice.width}×${slice.height}",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                            Row {
                                if (index < ed.slices.lastIndex) {
                                    IconButton(onClick = { viewModel.mergeSliceWithNext(index) }) {
                                        Icon(Icons.AutoMirrored.Filled.CallMerge, contentDescription = "与下一片合并")
                                    }
                                }
                                if (ed.slices.size > 1) {
                                    IconButton(onClick = { viewModel.deleteSlice(index) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "删除")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        viewModel.saveToGallery { result ->
                            result.onSuccess {
                                Toast.makeText(context, "已保存 $it 张到相册", Toast.LENGTH_SHORT).show()
                            }.onFailure {
                                Toast.makeText(context, it.message ?: "保存失败", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !ed.isProcessing && ed.slices.isNotEmpty(),
                ) {
                    Text("保存全部")
                }
                Button(
                    onClick = {
                        val intent = viewModel.shareIntent()
                        if (intent != null) {
                            context.startActivity(Intent.createChooser(intent, "分享切片到…"))
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !ed.isProcessing && ed.slices.isNotEmpty(),
                ) {
                    Text("批量分享")
                }
            }
        }
    }
}
