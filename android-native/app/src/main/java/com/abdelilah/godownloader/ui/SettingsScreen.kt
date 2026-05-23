package com.abdelilah.godownloader.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abdelilah.godownloader.R
import com.abdelilah.godownloader.logic.ConfigManager
import com.abdelilah.godownloader.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onConfigChanged: () -> Unit
) {
    var config by remember { mutableStateOf(ConfigManager.loadConfig()) }
    val scrollState = rememberScrollState()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold, color = TextPrimary) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(scrollState)
                .fillMaxSize()
        ) {
            SettingsSection(title = "App Language") {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LanguageButton(
                        text = "English",
                        isSelected = config.language == "en",
                        onClick = { config = config.copy(language = "en") },
                        modifier = Modifier.weight(1f)
                    )
                    LanguageButton(
                        text = "العربية",
                        isSelected = config.language == "ar",
                        onClick = { config = config.copy(language = "ar") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            SettingsSection(title = "Download Limits") {
                Text("Max Concurrent Downloads: ${config.maxConcurrentDownloads}", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                Text("How many files to download at once", color = TextSecondary, fontSize = 12.sp)
                Slider(
                    value = config.maxConcurrentDownloads.toFloat(),
                    onValueChange = { config = config.copy(maxConcurrentDownloads = it.toInt()) },
                    valueRange = 1f..10f,
                    steps = 9,
                    colors = SliderDefaults.colors(thumbColor = PrimaryVibrant, activeTrackColor = PrimaryVibrant)
                )
                
                Spacer(Modifier.height(16.dp))
                
                Text("Threads per Download: ${config.maxThreads}", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                Text("Number of parts for faster speed", color = TextSecondary, fontSize = 12.sp)
                Slider(
                    value = config.maxThreads.toFloat(),
                    onValueChange = { config = config.copy(maxThreads = it.toInt()) },
                    valueRange = 1f..32f,
                    steps = 31,
                    colors = SliderDefaults.colors(thumbColor = SecondaryVibrant, activeTrackColor = SecondaryVibrant)
                )
            }

            Spacer(Modifier.height(24.dp))

            SettingsSection(title = "Storage & Path") {
                OutlinedTextField(
                    value = config.downloadDir,
                    onValueChange = { config = config.copy(downloadDir = it) },
                    label = { Text("Download Directory") },
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
            }
            
            Spacer(Modifier.height(24.dp))
            
            SettingsSection(title = "Proxy Support") {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Enable Proxy", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        Text("Route downloads through a proxy", color = TextSecondary, fontSize = 12.sp)
                    }
                    Switch(
                        checked = config.proxyEnabled,
                        onCheckedChange = { config = config.copy(proxyEnabled = it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = PrimaryVibrant, checkedTrackColor = PrimaryVibrant.copy(alpha = 0.5f))
                    )
                }
                
                if (config.proxyEnabled) {
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = config.proxyUrl,
                        onValueChange = { config = config.copy(proxyUrl = it) },
                        label = { Text("Proxy URL (e.g. http://1.1.1.1:8080)") },
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
                }
            }
            
            Spacer(Modifier.height(40.dp))
            
            Button(
                onClick = {
                    ConfigManager.saveConfig(config)
                    onConfigChanged()
                    onBack()
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryVibrant)
            ) {
                Text("SAVE & APPLY", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, letterSpacing = 1.sp)
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = GlassBackground,
        border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = PrimaryVibrant, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
fun LanguageButton(text: String, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) PrimaryVibrant else Color.White.copy(alpha = 0.05f),
            contentColor = if (isSelected) Color.White else TextSecondary
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
    ) {
        Text(text, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
    }
}
