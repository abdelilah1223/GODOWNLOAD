package com.abdelilah.godownloader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.abdelilah.godownloader.logic.*
import com.abdelilah.godownloader.ui.*
import com.abdelilah.godownloader.ui.theme.*

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DownloadManager.init(this)
        enableEdgeToEdge()
        
        requestPermissions()

        setContent {
            val viewModel: DownloadViewModel = viewModel()
            val tasks by viewModel.tasks.collectAsState()
            var showAddDialog by remember { mutableStateOf(false) }
            var currentScreen by remember { mutableStateOf("dashboard") }
            val config = ConfigManager.loadConfig()
            
            val context = LocalContext.current
            val localeContext = remember(config.language) {
                LocaleHelper.wrap(context, config.language)
            }

            CompositionLocalProvider(LocalContext provides localeContext) {
                GoDownloaderTheme {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(BackgroundGradientStart, BackgroundGradientEnd)
                                )
                            )
                    ) {
                        if (currentScreen == "settings") {
                            SettingsScreen(
                                onBack = { currentScreen = "dashboard" },
                                onConfigChanged = { viewModel.onSettingsChanged() }
                            )
                        } else {
                            Scaffold(
                                containerColor = Color.Transparent,
                                modifier = Modifier.fillMaxSize(),
                                floatingActionButton = {
                                    FloatingActionButton(
                                        onClick = { showAddDialog = true },
                                        containerColor = PrimaryVibrant,
                                        contentColor = Color.White,
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_download_title))
                                    }
                                },
                                topBar = {
                                    CenterAlignedTopAppBar(
                                        title = {
                                            Text(
                                                stringResource(R.string.app_name),
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 22.sp,
                                                color = TextPrimary
                                            )
                                        },
                                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                            containerColor = Color.Transparent
                                        ),
                                        actions = {
                                            IconButton(onClick = { currentScreen = "settings" }) {
                                                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = TextPrimary)
                                            }
                                        }
                                    )
                                }
                            ) { innerPadding ->
                                DashboardScreen(
                                    tasks = tasks,
                                    viewModel = viewModel,
                                    modifier = Modifier.padding(innerPadding)
                                )

                                if (showAddDialog) {
                                    AddDownloadDialog(
                                        onDismiss = { showAddDialog = false },
                                        onConfirm = { url, startNow ->
                                            viewModel.addDownload(url, context, startNow)
                                            showAddDialog = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(
            android.Manifest.permission.INTERNET
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
            permissions.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            permissions.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        requestPermissions(permissions.toTypedArray(), 100)
    }
}

@Composable
fun AddDownloadDialog(onDismiss: () -> Unit, onConfirm: (String, Boolean) -> Unit) {
    var url by remember { mutableStateOf("") }
    var startNow by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_download_title), color = TextPrimary, fontWeight = FontWeight.Bold) },
        containerColor = DiscordDeep,
        text = {
            Column {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.add_download_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = PrimaryVibrant,
                        unfocusedBorderColor = GlassBorder,
                        focusedLabelColor = PrimaryVibrant,
                        unfocusedLabelColor = TextSecondary
                    )
                )
                
                Spacer(modifier = Modifier.height(20.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (startNow) "Download Immediately" else "Add to Queue",
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = startNow,
                        onCheckedChange = { startNow = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = PrimaryVibrant,
                            checkedTrackColor = PrimaryVibrant.copy(alpha = 0.5f)
                        )
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(url, startNow) },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryVibrant),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(stringResource(R.string.start_btn), color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel_btn), color = TextSecondary)
            }
        }
    )
}

@Composable
fun DashboardScreen(
    tasks: List<DownloadTask>,
    viewModel: DownloadViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        val downloadingTasks = tasks.filter { it.status == Status.Downloading }
        val downloadingCount = downloadingTasks.size
        val maxSpeed = if (downloadingTasks.isEmpty()) 0f else downloadingTasks.maxOf { it.speed }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatsCard(
                title = "Files",
                value = tasks.size.toString(),
                icon = Icons.AutoMirrored.Filled.List,
                modifier = Modifier.weight(1f)
            )
            StatsCard(
                title = "Active",
                value = downloadingCount.toString(),
                icon = Icons.Default.Refresh,
                modifier = Modifier.weight(1f)
            )
            StatsCard(
                title = "Speed",
                value = String.format(java.util.Locale.getDefault(), "%.1f", maxSpeed) + " MB/s",
                icon = Icons.Default.KeyboardArrowUp,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { viewModel.startAll() },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Start All", fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = { viewModel.stopAll() },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
            ) {
                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Stop All", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = stringResource(R.string.activity_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (tasks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.empty_list), color = TextSecondary)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(tasks, key = { it.id }) { task ->
                    DownloadItem(
                        task = task,
                        onPause = { viewModel.pauseTask(task.id) },
                        onStart = { viewModel.resumeTask(task.id) },
                        onDelete = { viewModel.deleteTask(task.id, true) },
                        onOpen = { viewModel.openFile(context, task) }
                    )
                }
            }
        }
    }
}

@Composable
fun StatsCard(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = GlassBackground,
        border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(icon, contentDescription = null, tint = PrimaryVibrant, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text(value, fontWeight = FontWeight.Black, fontSize = 18.sp, color = TextPrimary)
            Text(title, fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun DownloadItem(
    task: DownloadTask,
    onPause: () -> Unit,
    onStart: () -> Unit,
    onDelete: () -> Unit,
    onOpen: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = GlassBackground,
        border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = when(task.status) {
                        Status.Completed -> SuccessGreen.copy(alpha = 0.2f)
                        Status.Downloading -> PrimaryVibrant.copy(alpha = 0.2f)
                        Status.Paused -> WarningOrange.copy(alpha = 0.2f)
                        else -> Color.White.copy(alpha = 0.1f)
                    },
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = when(task.status) {
                                Status.Completed -> Icons.Default.Check
                                Status.Downloading -> Icons.Default.KeyboardArrowDown
                                Status.Error -> Icons.Default.Warning
                                Status.Paused -> Icons.Default.PlayArrow // Small play for paused
                                else -> Icons.Default.Info
                            },
                            contentDescription = null,
                            tint = when(task.status) {
                                Status.Completed -> SuccessGreen
                                Status.Downloading -> PrimaryVibrant
                                Status.Error -> ErrorRed
                                Status.Paused -> WarningOrange
                                else -> TextSecondary
                            },
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        task.fileName,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = TextPrimary
                    )
                    Text(
                        "${task.status.label} • ${formatSize(task.downloaded)} / ${formatSize(task.size)}",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (task.status == Status.Completed) {
                        IconButton(onClick = onOpen) { 
                            Icon(Icons.Default.Share, contentDescription = "Open", tint = SuccessGreen) 
                        }
                    } else if (task.status == Status.Downloading) {
                        IconButton(onClick = onPause) { 
                            Icon(Icons.Default.Close, contentDescription = "Pause", tint = TextPrimary) 
                        }
                    } else if (task.status == Status.Paused || task.status == Status.Error || task.status == Status.Queued) {
                        IconButton(onClick = onStart) { 
                            Icon(Icons.Default.PlayArrow, contentDescription = "Start", tint = SuccessGreen) 
                        }
                    }
                    IconButton(onClick = onDelete) { 
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = ErrorRed) 
                    }
                }
            }
            
            if (task.status == Status.Downloading || task.status == Status.Paused || task.status == Status.Queued) {
                Spacer(modifier = Modifier.height(16.dp))
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = 0.1f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(task.progress)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(PrimaryVibrant, SecondaryVibrant)
                                )
                            )
                    )
                }
                
                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        String.format(java.util.Locale.getDefault(), "%.1f%%", task.progress * 100),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryVibrant
                    )
                    if (task.status == Status.Downloading) {
                        Text(
                            "${String.format(java.util.Locale.getDefault(), "%.1f", task.speed)} MB/s • ${task.eta}",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }
}

fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(java.util.Locale.getDefault(), "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}
