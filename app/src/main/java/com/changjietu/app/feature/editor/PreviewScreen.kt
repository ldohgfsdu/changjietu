package com.changjietu.app.feature.editor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button as MaterialButton
import androidx.compose.material3.ButtonDefaults as MaterialButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.changjietu.app.core.ProcessErrorMessages
import com.changjietu.app.core.ZipNameSanitizer
import com.changjietu.app.core.icons.AppIcons
import com.changjietu.app.feature.export.ExportRepository
import com.changjietu.app.image.analysis.QualityLevel
import com.changjietu.app.image.analysis.displayName
import com.changjietu.app.image.processing.SliceOutput
import com.changjietu.app.ui.theme.ChangjietuTheme
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.icons.ArrowBack
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun PreviewScreen(
    viewModel: LongshotViewModel,
    onBack: () -> Unit,
) {
    val editor by viewModel.editor.collectAsStateWithLifecycle()
    val overview by viewModel.overview.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val ed = editor
    val displaySpec = viewModel.displaySpec
    var busy by remember { mutableStateOf(false) }
    var editingExpanded by rememberSaveable { mutableStateOf(true) }
    // 0=none, 1=保存图片, 2=打包 ZIP；rememberSaveable 保证权限弹窗期间旋转不丢待执行动作。
    var pendingPermissionKind by rememberSaveable { mutableIntStateOf(0) }
    var pendingZipName by rememberSaveable { mutableStateOf("") }
    val showZipDialog = remember { mutableStateOf(false) }
    var zipName by remember { mutableStateOf(viewModel.defaultZipName()) }
    var previewIndex by remember { mutableStateOf<Int?>(null) }

    fun launchShare(intent: Intent?) {
        if (intent == null) {
            scope.launch { snackbar.showSnackbar(ProcessErrorMessages.NO_SELECTION) }
            return
        }
        runCatching {
            context.startActivity(Intent.createChooser(intent, "分享切片到…"))
        }.onFailure {
            scope.launch { snackbar.showSnackbar("没有可接收分享的应用") }
        }
    }

    fun performSave() {
        busy = true
        viewModel.saveToGallery { result ->
            busy = false
            result.onSuccess { summary ->
                val msg = if (summary.failed == 0) {
                    "已保存 ${summary.saved} 张到 Pictures/${ExportRepository.ALBUM_NAME}"
                } else {
                    "已保存 ${summary.saved} 张，${summary.failed} 张失败"
                }
                scope.launch { snackbar.showSnackbar(msg) }
            }.onFailure {
                scope.launch {
                    snackbar.showSnackbar(
                        it.message?.takeIf { m -> m.isNotBlank() }
                            ?: ProcessErrorMessages.SAVE_FAILED,
                    )
                }
            }
        }
    }

    fun doZip(name: String) {
        busy = true
        viewModel.packageZipToDownloads(name) { result ->
            busy = false
            result.onSuccess { saved ->
                scope.launch {
                    val action = snackbar.showSnackbar(
                        message = "已保存 ${saved.locationLabel}",
                        actionLabel = "分享 ZIP",
                        withDismissAction = true,
                        duration = SnackbarDuration.Long,
                    )
                    // SavedZip.file 统一为缓存 zip（FileProvider 覆盖），API 26–28 也可分享。
                    if (action == SnackbarResult.ActionPerformed) {
                        launchShare(viewModel.shareZipIntent(saved.file))
                    }
                }
            }.onFailure {
                scope.launch {
                    snackbar.showSnackbar(
                        it.message?.takeIf { m -> m.isNotBlank() }
                            ?: ProcessErrorMessages.ZIP_FAILED,
                    )
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val kind = pendingPermissionKind
        pendingPermissionKind = 0
        when {
            !granted -> scope.launch {
                snackbar.showSnackbar("需要存储权限才能保存到公共目录")
            }
            kind == 1 -> performSave()
            kind == 2 -> doZip(pendingZipName)
        }
    }

    fun needsWritePermission(): Boolean =
        Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) != PackageManager.PERMISSION_GRANTED

    fun startZip(name: String) {
        if (needsWritePermission()) {
            pendingPermissionKind = 2
            pendingZipName = name
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            doZip(name)
        }
    }

    fun saveSelected() {
        if (needsWritePermission()) {
            pendingPermissionKind = 1
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            performSave()
        }
    }

    Scaffold(
        containerColor = MiuixTheme.colorScheme.background,
        // TopAppBar 自己吃 statusBars insets；内容层不再叠加，避免双重顶部 padding。
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            SmallTopAppBar(
                title = "切片结果",
                color = MiuixTheme.colorScheme.background,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        bottomBar = {
            if (ed != null) {
                BottomActions(
                    enabled = ed.selectedIndices.isNotEmpty() && !ed.isProcessing && !busy,
                    busy = busy,
                    selectedCount = ed.selectedIndices.size,
                    onSave = ::saveSelected,
                    onZip = {
                        zipName = viewModel.defaultZipName()
                        showZipDialog.value = true
                    },
                    onShare = { launchShare(viewModel.shareIntent()) },
                )
            }
        },
    ) { padding ->
        if (ed == null) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
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
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0x66B3261E),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                ) {
                    val level = ed.source.quality.level
                    val prefix = if (level == QualityLevel.UNRECOVERABLE) "不是文件损坏。" else ""
                    Text(
                        "${level.displayName()}。$prefix${ed.source.quality.conclusion}",
                        color = Color.White,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(10.dp),
                    )
                }
                Spacer(Modifier.height(6.dp))
            }

            Row(
                Modifier.fillMaxWidth().height(56.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text(
                        "${ed.slices.size} 张切片",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        maxLines = 1,
                    )
                    Text(
                        "${ed.source.width} × ${ed.source.height} · 已选 ${ed.selectedIndices.size}/${ed.slices.size}",
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (ed.slices.isNotEmpty()) {
                    val allSelected = ed.selectedIndices.size == ed.slices.size
                    Box(
                        modifier = Modifier
                            .height(48.dp)
                            .width(88.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = !ed.isProcessing && !busy) {
                                if (allSelected) viewModel.clearSliceSelection()
                                else viewModel.selectAllSlices()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (allSelected) "取消全选" else "全选",
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.primary,
                        )
                    }
                }
            }
            ed.statusMessage
                ?.takeUnless { it.startsWith("已生成 ") || it.startsWith("已更新为 ") }
                ?.let {
                    Text(it, fontSize = 11.sp, color = MiuixTheme.colorScheme.primary)
                }
            ed.error?.let {
                Text(it, color = Color(0xFFB3261E), fontSize = 11.sp)
            }
            if (ed.isProcessing) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            Spacer(Modifier.height(6.dp))
            val detailOverview by viewModel.detailOverview.collectAsStateWithLifecycle()
            var showDetail by remember { mutableStateOf(false) }
            var detailTop by remember { mutableStateOf(0) }
            if (showDetail) {
                DetailEditorDialog(
                    editor = ed,
                    detailOverview = detailOverview,
                    detailTop = detailTop,
                    windowH = displaySpec.longSidePx.coerceAtLeast(1),
                    enabled = !ed.isProcessing && !busy,
                    onPrev = {
                        detailTop = (detailTop - displaySpec.longSidePx).coerceAtLeast(0)
                        viewModel.loadDetailWindow(detailTop, displaySpec.longSidePx)
                    },
                    onNext = {
                        detailTop = (detailTop + displaySpec.longSidePx)
                            .coerceAtMost((ed.contentHeight - displaySpec.longSidePx).coerceAtLeast(0))
                        viewModel.loadDetailWindow(detailTop, displaySpec.longSidePx)
                    },
                    onPreviewCut = viewModel::previewCut,
                    onPreviewCrop = viewModel::previewCrop,
                    onCommit = viewModel::commitCuts,
                    onAddCutPoint = viewModel::addCutPoint,
                    onDismiss = { showDetail = false },
                )
            }
            if (editingExpanded) {
                OverviewEditor(
                    overview = overview,
                    editor = ed,
                    enabled = !ed.isProcessing && !busy,
                    onPreviewCut = viewModel::previewCut,
                    onPreviewCrop = viewModel::previewCrop,
                    onCommit = viewModel::commitCuts,
                    onAddCutPoint = viewModel::addCutPoint,
                    modifier = Modifier.fillMaxWidth().weight(0.59f),
                    onDetailClick = {
                        detailTop = 0
                        viewModel.loadDetailWindow(0, displaySpec.longSidePx)
                        showDetail = true
                    },
                    onCollapse = { editingExpanded = false },
                )
                Spacer(Modifier.height(8.dp))
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("切片列表", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable(
                                enabled = !ed.isProcessing && !busy,
                                onClickLabel = "展开剪辑预览",
                            ) { editingExpanded = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            AppIcons.ChevronDown,
                            contentDescription = "展开剪辑预览",
                            tint = MiuixTheme.colorScheme.onBackgroundVariant,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(if (editingExpanded) 0.41f else 1f),
                contentPadding = PaddingValues(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(ed.slices, key = { _, s -> s.file.absolutePath }) { index, slice ->
                    SliceCard(
                        slice = slice,
                        selected = index in ed.selectedIndices,
                        canMerge = index < ed.slices.lastIndex,
                        canDelete = ed.slices.size > 1,
                        screenLongSide = displaySpec.longSidePx,
                        onToggle = { viewModel.toggleSliceSelection(index) },
                        onPreview = { previewIndex = index },
                        onMerge = { viewModel.mergeSliceWithNext(index) },
                        onDelete = { viewModel.deleteSlice(index) },
                    )
                }
            }
        }
    }

    // 用平台 Dialog + miuix 组件：miuix 0.3.0 的 SuperDialog 不会自动 dismiss
    // （需额外调内部 dismissDialog），这里避免弹窗关不掉的风险。
    val previewSlice = previewIndex?.let { ed?.slices?.getOrNull(it) }
    if (previewSlice != null) {
        SlicePreviewDialog(
            slice = previewSlice,
            onDismiss = { previewIndex = null },
        )
    }

    if (showZipDialog.value) {
        Dialog(onDismissRequest = { showZipDialog.value = false }) {
            ChangjietuTheme {
                Card(
                    color = MiuixTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text(
                            "导出 ZIP",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "ZIP 文件名（保存到 Download/${ExportRepository.ALBUM_NAME}）",
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                        )
                        Spacer(Modifier.height(16.dp))
                        TextField(
                            value = zipName,
                            onValueChange = { zipName = it },
                            label = "文件名",
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TextButton(
                                text = "取消",
                                onClick = { showZipDialog.value = false },
                                modifier = Modifier.weight(1f),
                            )
                            Button(
                                onClick = {
                                    val name = ZipNameSanitizer.sanitize(
                                        zipName,
                                        viewModel.defaultZipName(),
                                    )
                                    showZipDialog.value = false
                                    startZip(name)
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("保存 ZIP")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomActions(
    enabled: Boolean,
    busy: Boolean,
    selectedCount: Int,
    onSave: () -> Unit,
    onZip: () -> Unit,
    onShare: () -> Unit,
) {
    var moreExpanded by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(enabled) {
        if (!enabled) moreExpanded = false
    }
    Surface(color = MiuixTheme.colorScheme.surface) {
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MaterialButton(
                onClick = onShare,
                enabled = enabled,
                shape = RoundedCornerShape(13.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
                colors = MaterialButtonDefaults.buttonColors(
                    containerColor = MiuixTheme.colorScheme.primary,
                    contentColor = Color.White,
                    disabledContainerColor = MiuixTheme.colorScheme.surfaceVariant,
                    disabledContentColor = MiuixTheme.colorScheme.onBackgroundVariant,
                ),
                modifier = Modifier.weight(1f).height(48.dp),
            ) {
                if (busy) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.White,
                    )
                } else {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = null,
                        tint = if (enabled) Color.White else MiuixTheme.colorScheme.onBackgroundVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "分享 ${selectedCount} 张",
                    fontSize = 15.sp,
                    color = if (enabled) Color.White else MiuixTheme.colorScheme.onBackgroundVariant,
                    maxLines = 1,
                )
            }
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(enabled = enabled, onClickLabel = "保存到相册", onClick = onSave),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    AppIcons.Download,
                    contentDescription = "保存到相册",
                    tint = MiuixTheme.colorScheme.onBackgroundVariant,
                    modifier = Modifier.size(23.dp),
                )
            }
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(
                            enabled = enabled,
                            onClickLabel = "更多导出操作",
                            onClick = { moreExpanded = true },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        AppIcons.MoreVert,
                        contentDescription = "更多导出操作",
                        tint = MiuixTheme.colorScheme.onBackgroundVariant,
                        modifier = Modifier.size(23.dp),
                    )
                }
                DropdownMenu(
                    expanded = moreExpanded,
                    onDismissRequest = { moreExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("导出 ZIP") },
                        onClick = {
                            moreExpanded = false
                            onZip()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailEditorDialog(
    editor: com.changjietu.app.core.EditorState,
    detailOverview: OverviewState?,
    detailTop: Int,
    windowH: Int,
    enabled: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onPreviewCut: (Int, Int) -> Unit,
    onPreviewCrop: (Int, Int) -> Unit,
    onCommit: () -> Unit,
    onAddCutPoint: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        ChangjietuTheme {
            Surface(color = MiuixTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize()) {
                    SmallTopAppBar(
                        title = "精细编辑",
                        color = MiuixTheme.colorScheme.background,
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(MiuixIcons.ArrowBack, contentDescription = "关闭")
                            }
                        },
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(text = "上一区域", onClick = onPrev)
                        Text(
                            "高清区域（窗口分块解码）",
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary,
                        )
                        TextButton(text = "下一区域", onClick = onNext)
                    }
                    OverviewEditor(
                        overview = detailOverview,
                        editor = editor,
                        enabled = enabled,
                        onPreviewCut = onPreviewCut,
                        onPreviewCrop = onPreviewCrop,
                        onCommit = onCommit,
                        onAddCutPoint = onAddCutPoint,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        windowTop = detailTop,
                        windowHeight = windowH,
                    )
                }
            }
        }
    }
}

@Composable
private fun SlicePreviewDialog(
    slice: SliceOutput,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        ChangjietuTheme {
            Surface(color = MiuixTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize()) {
                    SmallTopAppBar(
                        title = slice.label,
                        color = MiuixTheme.colorScheme.background,
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(MiuixIcons.ArrowBack, contentDescription = "关闭")
                            }
                        },
                    )
                    Text(
                        "${slice.width} × ${slice.height} px · 上下滑动查看",
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    Column(
                        Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        AsyncImage(
                            model = slice.file,
                            contentDescription = slice.label,
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(
                                    slice.width.toFloat() / slice.height.coerceAtLeast(1),
                                ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SliceCard(
    slice: SliceOutput,
    selected: Boolean,
    canMerge: Boolean,
    canDelete: Boolean,
    screenLongSide: Int,
    onToggle: () -> Unit,
    onPreview: () -> Unit,
    onMerge: () -> Unit,
    onDelete: () -> Unit,
) {
    val borderColor = if (selected) {
        MiuixTheme.colorScheme.primary.copy(alpha = 0.55f)
    } else {
        MiuixTheme.colorScheme.outline.copy(alpha = 0.22f)
    }
    val percent = (slice.height * 100f / screenLongSide).toInt().coerceIn(1, 100)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = borderColor,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            ),
        insideMargin = PaddingValues(0.dp),
        color = MiuixTheme.colorScheme.surface,
    ) {
        Column {
            Box {
                AsyncImage(
                    model = slice.file,
                    contentDescription = "预览 ${slice.label}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(
                            (slice.width.toFloat() / slice.height).coerceIn(0.4f, 1.2f),
                        )
                        .background(MiuixTheme.colorScheme.surfaceVariant)
                        .clickable(onClick = onPreview),
                    contentScale = ContentScale.Fit,
                )
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(48.dp)
                        .clickable(onClick = onToggle),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(
                                if (selected) MiuixTheme.colorScheme.primary else Color.Black.copy(alpha = 0.32f),
                            )
                            .border(1.dp, Color.White.copy(alpha = 0.85f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "已选择",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                ) {
                    Text(slice.label, color = Color.White, fontSize = 12.sp)
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 4.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${slice.width} × ${slice.height}",
                        fontSize = 12.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "占屏高 $percent%",
                        fontSize = 11.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                }
                if (canMerge) {
                    IconButton(onClick = onMerge, modifier = Modifier.size(48.dp)) {
                        Icon(
                            AppIcons.CallMerge,
                            contentDescription = "与下一片合并",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                if (canDelete) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "删除此片",
                            tint = Color(0xFFB3261E),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}
