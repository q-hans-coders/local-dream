package io.github.xororz.localdream.ui.screens

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
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
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import io.github.xororz.localdream.R
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

@OptIn(ExperimentalMaterial3Api::class)
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

    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

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

    val sd21Model = modelRepository.models.firstOrNull()

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
                    OutlinedTextField(
                        value = tempBaseUrl,
                        onValueChange = { tempBaseUrl = it },
                        label = { Text(stringResource(R.string.download_from)) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("https://your-mirror-site.com/") },
                        singleLine = true
                    )
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

    showDownloadConfirm?.let { model ->
        AlertDialog(
            onDismissRequest = { showDownloadConfirm = null },
            title = { Text(stringResource(R.string.download_model)) },
            text = { Text(stringResource(R.string.download_model_hint, model.name)) },
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
                                        snackbarHostState.showSnackbar(
                                            context.getString(R.string.download_done))
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

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.app_name))
                        Text(
                            "Stable Diffusion 2.1",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showHelpDialog = true }) {
                        Icon(Icons.Default.Help, stringResource(R.string.help))
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
                    downloadProgress = currentProgress,
                    onClick = {
                        if (Model.isDeviceSupported()) {
                            if (model.isDownloaded) {
                                navController.navigate(Screen.ModelRun.createRoute(model.id))
                            } else {
                                showDownloadConfirm = model
                            }
                        } else {
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    context.getString(R.string.unsupport_device))
                            }
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
                        text = stringResource(R.string.model_load_error),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelCard(
    model: Model,
    isDownloading: Boolean,
    downloadProgress: DownloadProgress?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClick() })
            },
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = model.name,
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = model.description,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.model_size, model.approximateSize),
                    style = MaterialTheme.typography.labelMedium
                )

                if (model.isDownloaded) {
                    Text(
                        text = stringResource(R.string.downloaded),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Text(
                        text = stringResource(R.string.download),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (isDownloading && downloadProgress != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Column {
                    LinearProgressIndicator(
                        progress = { downloadProgress.progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${formatFileSize(downloadProgress.downloadedBytes)} / ${
                            formatFileSize(downloadProgress.totalBytes)
                        }",
                        style = MaterialTheme.typography.labelSmall
                    )
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