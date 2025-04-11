package io.github.xororz.localdream.ui.screens

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.github.xororz.localdream.data.*
import io.github.xororz.localdream.navigation.Screen
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import io.github.xororz.localdream.R
import kotlinx.coroutines.withTimeout
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.core.content.edit

@Composable
private fun DeleteConfirmDialog(
    selectedCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_model)) },
        text = { Text(stringResource(R.string.delete_confirm, selectedCount)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(stringResource(R.string.delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

@Composable
fun ModelListScreen(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var downloadingModel by remember { mutableStateOf<Model?>(null) }
    var currentProgress by remember { mutableStateOf<DownloadProgress?>(null) }
    var downloadError by remember { mutableStateOf<String?>(null) }
    var showDownloadConfirm by remember { mutableStateOf<Model?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedModels by remember { mutableStateOf(setOf<Model>()) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    var showSettingsDialog by remember { mutableStateOf(false) }
    var tempBaseUrl by remember { mutableStateOf("") }
    val generationPreferences = remember { GenerationPreferences(context) }
    val currentBaseUrl by generationPreferences.getBaseUrl()
        .collectAsState(initial = "https://huggingface.co/")

    var version by remember { mutableStateOf(0) }
    val modelRepository = remember(version) { ModelRepository(context) }

    var showHelpDialog by remember { mutableStateOf(false) }

    val isFirstLaunch = remember {
        val preferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val isFirst = preferences.getBoolean("is_first_launch", true)
        if (isFirst) {
            preferences.edit() { putBoolean("is_first_launch", false) }
        }
        isFirst
    }

    LaunchedEffect(Unit) {
        if (isFirstLaunch) {
            showHelpDialog = true
        }
    }

    // 기존에 CPU/NPU 모델 구분 코드와 관련된 부분 제거
    // 이제 ModelRepository.initializeModels()를 수정하여 SD2.1 모델만 반환하도록 하였으므로,
    // 단일 모델(sd21Model)만 사용합니다.
    val sd21Model = modelRepository.models.firstOrNull()

    BackHandler(enabled = isSelectionMode) {
        isSelectionMode = false
        selectedModels = emptySet()
    }
    LaunchedEffect(downloadError) {
        downloadError?.let {
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = it,
                    duration = SnackbarDuration.Short
                )
                downloadError = null
            }
        }
    }
    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.about_app),
                    style = MaterialTheme.typography.headlineSmall
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.must_read),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) {
                    Text(stringResource(R.string.got_it))
                }
            }
        )
    }

    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text(stringResource(R.string.download_settings)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.download_settings_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = tempBaseUrl,
                        onValueChange = { tempBaseUrl = it },
                        label = { Text(stringResource(R.string.download_from)) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("https://your-mirror-site.com/") },
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(32.dp))
                    Text(
                        "Use img2img feature. Turn off if you don't need or encounter any problems.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Use img2img",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        val preferences = LocalContext.current.getSharedPreferences(
                            "app_prefs",
                            Context.MODE_PRIVATE
                        )
                        var useImg2img by remember {
                            mutableStateOf(preferences.getBoolean("use_img2img", true).also {
                                if (!preferences.contains("use_img2img")) {
                                    preferences.edit { putBoolean("use_img2img", true) }
                                }
                            })
                        }
                        Switch(
                            checked = useImg2img,
                            onCheckedChange = {
                                useImg2img = it
                                preferences.edit {
                                    putBoolean("use_img2img", it)
                                }
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            if (tempBaseUrl.isNotEmpty()) {
                                generationPreferences.saveBaseUrl(tempBaseUrl)
                                modelRepository.updateBaseUrl(tempBaseUrl)
                                version += 1
                                showSettingsDialog = false
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
    LaunchedEffect(showSettingsDialog) {
        if (showSettingsDialog) {
            tempBaseUrl = currentBaseUrl
        }
    }
    if (showDeleteConfirm && selectedModels.isNotEmpty()) {
        DeleteConfirmDialog(
            selectedCount = selectedModels.size,
            onConfirm = {
                showDeleteConfirm = false
                isSelectionMode = false

                scope.launch {
                    var successCount = 0
                    selectedModels.forEach { model ->
                        if (model.deleteModel(context)) {
                            successCount++
                        }
                    }

                    modelRepository.refreshAllModels()

                    snackbarHostState.showSnackbar(
                        if (successCount == selectedModels.size) context.getString(R.string.delete_success)
                        else context.getString(R.string.delete_failed)
                    )

                    selectedModels = emptySet()
                }
            },
            onDismiss = {
                showDeleteConfirm = false
            }
        )
    }

    showDownloadConfirm?.let { model ->
        if (downloadingModel != null) {
            AlertDialog(
                onDismissRequest = { showDownloadConfirm = null },
                title = { Text(stringResource(R.string.cannot_download)) },
                text = { Text(stringResource(R.string.cannot_download_hint)) },
                confirmButton = {
                    TextButton(onClick = { showDownloadConfirm = null }) {
                        Text(stringResource(R.string.confirm))
                    }
                }
            )
        } else {
            AlertDialog(
                onDismissRequest = { showDownloadConfirm = null },
                title = { Text(stringResource(R.string.download_model)) },
                text = {
                    Text(stringResource(R.string.download_model_hint, model.name))
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDownloadConfirm = null
                            scope.launch {
                                downloadingModel = model
                                currentProgress = null

                                model.download(context).collect { result ->
                                    when (result) {
                                        is DownloadResult.Progress -> {
                                            currentProgress = result.progress
                                        }

                                        is DownloadResult.Success -> {
                                            modelRepository.refreshModelState(model.id)
                                            downloadingModel = null
                                            snackbarHostState.showSnackbar(context.getString(R.string.download_done))
                                        }

                                        is DownloadResult.Error -> {
                                            downloadingModel = null
                                            downloadError = result.message
                                        }
                                    }
                                }
                            }
                        }
                    ) {
                        Text(stringResource(R.string.confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDownloadConfirm = null }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text("상상 동물✨")
                        // 상단 제목을 SD 2.1 모델로 고정
                        Text(
                            "Stable Diffusion 2.1",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                },
                navigationIcon = {
                    if (isSelectionMode) {
                        IconButton(onClick = {
                            isSelectionMode = false
                            selectedModels = emptySet()
                        }) {
                            Icon(Icons.Default.Close, stringResource(R.string.cancel))
                        }
                    }
                },
                actions = {
                    if (isSelectionMode && selectedModels.isNotEmpty()) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Default.Delete, stringResource(R.string.delete))
                        }
                    }
                    IconButton(onClick = { showHelpDialog = true }) {
                        Icon(Icons.AutoMirrored.Filled.Help, stringResource(R.string.help))
                    }
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Default.Settings, stringResource(R.string.settings))
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        // 기존 TabRow 및 HorizontalPager, LazyColumn를 제거하고 단일 모델 카드만 표시
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
        ) {
            sd21Model?.let { model ->
                ModelCard(
                    model = model,
                    isDownloading = model == downloadingModel,
                    downloadProgress = if (model == downloadingModel) currentProgress else null,
                    isSelected = selectedModels.contains(model),
                    isSelectionMode = isSelectionMode,
                    onClick = {
                        if (!Model.isDeviceSupported() && !model.runOnCpu) {
                            scope.launch {
                                snackbarHostState.showSnackbar(context.getString(R.string.unsupport_npu))
                            }
                            return@ModelCard
                        }
                        if (isSelectionMode) {
                            if (model.isDownloaded) {
                                selectedModels = if (selectedModels.contains(model)) {
                                    selectedModels - model
                                } else {
                                    selectedModels + model
                                }

                                if (selectedModels.isEmpty()) {
                                    isSelectionMode = false
                                }
                            }
                        } else {
                            if (downloadingModel != null) {
                                scope.launch {
                                    snackbarHostState.showSnackbar(context.getString(R.string.cannot_download_hint))
                                }
                                return@ModelCard
                            }

                            val isModelDownloaded = Model.checkModelExists(
                                context,
                                model.id,
                                model.files
                            )

                            if (!isModelDownloaded) {
                                showDownloadConfirm = model
                            } else {
                                navController.navigate(Screen.ModelRun.createRoute(model.id))
                            }
                        }
                    },
                    onLongClick = {
                        if (model.isDownloaded && !isSelectionMode) {
                            isSelectionMode = true
                            selectedModels = setOf(model)
                        }
                    }
                )
            } ?: run {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "모델을 불러올 수 없습니다.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}


@Composable
fun TabPageIndicator(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        repeat(pageCount) { index ->
            Box(
                modifier = Modifier
                    .size(if (currentPage == index) 10.dp else 8.dp)
                    .background(
                        color = if (currentPage == index)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                        shape = CircleShape
                    )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelCard(
    model: Model,
    isDownloading: Boolean,
    downloadProgress: DownloadProgress?,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val elevation by animateFloatAsState(
        targetValue = if (isSelected) 8f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )

    val containerColor = when {
        isSelected -> MaterialTheme.colorScheme.secondaryContainer
        isDownloading -> MaterialTheme.colorScheme.surfaceContainerLow
        !model.isDownloaded && isSelectionMode -> MaterialTheme.colorScheme.surfaceContainerLow
        else -> MaterialTheme.colorScheme.surfaceContainerLowest
    }

    val contentColor = when {
        isSelected -> MaterialTheme.colorScheme.onSecondaryContainer
        !model.isDownloaded && isSelectionMode -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.onSurface
    }

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(isDownloading, isSelectionMode) {
                detectTapGestures(
                    onLongPress = {
                        if (model.isDownloaded && !isDownloading && !isSelectionMode) onLongClick()
                    },
                    onTap = {
                        if (!isSelectionMode || (isSelectionMode && model.isDownloaded)) {
                            onClick()
                        }
                    }
                )
            },
        colors = CardDefaults.elevatedCardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = elevation.dp
        )
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // CPU/NPU Badge
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer
            ) {
                Text(
                    text = if (model.runOnCpu) "CPU" else "NPU",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    fontWeight = FontWeight.Medium
                )
            }

            // Main content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = model.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = contentColor
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = model.description,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = contentColor.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.model_size, model.approximateSize),
                        style = MaterialTheme.typography.labelMedium,
                        color = contentColor.copy(alpha = 0.7f)
                    )
                    if (model.isDownloaded) {
                        Text(
                            text = stringResource(R.string.downloaded),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                if (isDownloading && downloadProgress != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Column {
                        Text(
                            text = stringResource(
                                R.string.downloading_file,
                                downloadProgress.currentFileIndex,
                                downloadProgress.totalFiles,
                                downloadProgress.displayName
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        LinearProgressIndicator(
                            progress = { downloadProgress.progress },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )

                        if (downloadProgress.totalBytes > 0) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${formatFileSize(downloadProgress.downloadedBytes)} / ${
                                    formatFileSize(
                                        downloadProgress.totalBytes
                                    )
                                }",
                                style = MaterialTheme.typography.labelSmall,
                                color = contentColor.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatFileSize(size: Long): String {
    val df = DecimalFormat("#.##")
    return when {
        size < 1024 -> "${size}B"
        size < 1024 * 1024 -> "${df.format(size / 1024.0)}KB"
        size < 1024 * 1024 * 1024 -> "${df.format(size / (1024.0 * 1024.0))}MB"
        else -> "${df.format(size / (1024.0 * 1024.0 * 1024.0))}GB"
    }
}
