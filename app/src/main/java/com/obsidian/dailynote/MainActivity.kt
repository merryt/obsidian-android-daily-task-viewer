package com.obsidian.dailynote

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.obsidian.dailynote.data.VaultPreferences
import com.obsidian.dailynote.data.VaultPreferencesRepository
import com.obsidian.dailynote.deeplink.ObsidianDeepLinkHelper
import com.obsidian.dailynote.saf.NoteReadResult
import com.obsidian.dailynote.saf.SafVaultReader
import com.obsidian.dailynote.ui.theme.ObsidianBg
import com.obsidian.dailynote.ui.theme.ObsidianBorder
import com.obsidian.dailynote.ui.theme.ObsidianDailyNoteViewerTheme
import com.obsidian.dailynote.ui.theme.ObsidianError
import com.obsidian.dailynote.ui.theme.ObsidianPurple
import com.obsidian.dailynote.ui.theme.ObsidianPurpleLight
import com.obsidian.dailynote.ui.theme.ObsidianSuccess
import com.obsidian.dailynote.ui.theme.ObsidianSurface
import com.obsidian.dailynote.ui.theme.ObsidianSurfaceVariant
import com.obsidian.dailynote.ui.theme.ObsidianText
import com.obsidian.dailynote.ui.theme.ObsidianTextSecondary
import com.obsidian.dailynote.ui.theme.ObsidianWarning
import com.obsidian.dailynote.widget.DailyNoteGlanceWidget
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {

    private lateinit var repository: VaultPreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = VaultPreferencesRepository(applicationContext)

        setContent {
            ObsidianDailyNoteViewerTheme {
                MainScreen(
                    repository = repository,
                    onUpdateWidgets = { updateAllWidgets() }
                )
            }
        }
    }

    private fun updateAllWidgets() {
        val context = applicationContext
        val coroutineScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
        coroutineScope.launch {
            try {
                val glanceManager = GlanceAppWidgetManager(context)
                val glanceIds = glanceManager.getGlanceIds(DailyNoteGlanceWidget::class.java)
                for (glanceId in glanceIds) {
                    DailyNoteGlanceWidget().update(context, glanceId)
                }
            } catch (e: Exception) {
                // If no widgets are placed yet, GlanceAppWidgetManager might return empty
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    repository: VaultPreferencesRepository,
    onUpdateWidgets: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val savedPreferences by repository.preferencesFlow.collectAsState(initial = VaultPreferences())

    // Editable state
    var treeUriString by remember { mutableStateOf<String?>(null) }
    var folderDisplayName by remember { mutableStateOf("") }
    var vaultName by remember { mutableStateOf("") }
    var dailyNotesFolder by remember { mutableStateOf("") }
    var dateFormat by remember { mutableStateOf("yyyy-MM-dd") }
    var excludedSections by remember { mutableStateOf("Journaling") }
    var hideCompletedTasks by remember { mutableStateOf(true) }
    var testResult by remember { mutableStateOf<NoteReadResult?>(null) }

    // Sync state when preferences are loaded
    LaunchedEffect(savedPreferences) {
        if (treeUriString == null && savedPreferences.treeUriString != null) {
            treeUriString = savedPreferences.treeUriString
            vaultName = savedPreferences.vaultName
            dailyNotesFolder = savedPreferences.dailyNotesFolder
            dateFormat = savedPreferences.dateFormat
            excludedSections = savedPreferences.excludedSections
            hideCompletedTasks = savedPreferences.hideCompletedTasks

            // Resolve folder display name
            treeUriString?.let {
                val doc = DocumentFile.fromTreeUri(context, Uri.parse(it))
                folderDisplayName = doc?.name ?: "Selected Directory"
            }
        }
    }

    // SAF Directory Picker Launcher
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            // Take persistable permission
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to persist permission: ${e.message}", Toast.LENGTH_LONG).show()
            }

            treeUriString = uri.toString()
            val doc = DocumentFile.fromTreeUri(context, uri)
            val name = doc?.name ?: ""
            folderDisplayName = name
            if (vaultName.isBlank()) {
                vaultName = name
            }
        }
    }

    val currentConfig = VaultPreferences(
        treeUriString = treeUriString,
        vaultName = vaultName,
        dailyNotesFolder = dailyNotesFolder,
        dateFormat = dateFormat.ifBlank { "yyyy-MM-dd" },
        excludedSections = excludedSections,
        hideCompletedTasks = hideCompletedTasks
    )

    val formattedToday = try {
        LocalDate.now().format(DateTimeFormatter.ofPattern(dateFormat.ifBlank { "yyyy-MM-dd" }))
    } catch (e: Exception) {
        "yyyy-MM-dd"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_obsidian_logo),
                            contentDescription = "Obsidian Logo",
                            tint = Color.Unspecified,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Obsidian Daily Note",
                            style = MaterialTheme.typography.titleLarge,
                            color = ObsidianText
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ObsidianBg
                )
            )
        },
        containerColor = ObsidianBg
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Introduction Card
            Card(
                colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Home Screen Widget Setup",
                        style = MaterialTheme.typography.titleMedium,
                        color = ObsidianPurpleLight
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Configure your Obsidian vault folder to automatically display today's note on your Android home screen.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ObsidianTextSecondary
                    )
                }
            }

            // 1. Vault Directory Picker Card
            Card(
                colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "1. Vault Directory",
                        style = MaterialTheme.typography.titleMedium,
                        color = ObsidianText
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (!treeUriString.isNullOrBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(ObsidianSurfaceVariant)
                                .padding(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = ObsidianPurpleLight
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = folderDisplayName.ifEmpty { "Vault Directory Selected" },
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = ObsidianText,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "SAF Permission Granted",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = ObsidianSuccess
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    OutlinedButton(
                        onClick = { folderPickerLauncher.launch(null) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ObsidianPurpleLight)
                    ) {
                        Icon(imageVector = Icons.Default.Folder, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (treeUriString == null) "Select Vault Folder" else "Change Vault Folder")
                    }
                }
            }

            // 2. Vault & Path Configuration Card
            Card(
                colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "2. Path & Deep Link Settings",
                        style = MaterialTheme.typography.titleMedium,
                        color = ObsidianText
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Vault Name
                    OutlinedTextField(
                        value = vaultName,
                        onValueChange = { vaultName = it },
                        label = { Text("Vault Name in Obsidian") },
                        placeholder = { Text("e.g. My Vault") },
                        supportingText = { Text("Must match your vault name in Obsidian for deep links to work") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ObsidianPurpleLight,
                            unfocusedBorderColor = ObsidianBorder,
                            focusedLabelColor = ObsidianPurpleLight,
                            cursorColor = ObsidianPurpleLight
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Daily Notes Folder
                    OutlinedTextField(
                        value = dailyNotesFolder,
                        onValueChange = { dailyNotesFolder = it },
                        label = { Text("Daily Notes Folder (Optional)") },
                        placeholder = { Text("e.g. Daily Notes or Journal/Daily") },
                        supportingText = { Text("Leave blank if notes are stored in the root folder") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ObsidianPurpleLight,
                            unfocusedBorderColor = ObsidianBorder,
                            focusedLabelColor = ObsidianPurpleLight,
                            cursorColor = ObsidianPurpleLight
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Date Format
                    OutlinedTextField(
                        value = dateFormat,
                        onValueChange = { dateFormat = it },
                        label = { Text("Date Format") },
                        placeholder = { Text("yyyy-MM-dd") },
                        supportingText = { Text("Today resolves to: $formattedToday.md") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ObsidianPurpleLight,
                            unfocusedBorderColor = ObsidianBorder,
                            focusedLabelColor = ObsidianPurpleLight,
                            cursorColor = ObsidianPurpleLight
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Excluded Sections
                    OutlinedTextField(
                        value = excludedSections,
                        onValueChange = { excludedSections = it },
                        label = { Text("Hidden Sections (e.g. Journaling)") },
                        placeholder = { Text("Journaling") },
                        supportingText = { Text("Headers matching these names (e.g. # Journaling) will not appear on the widget") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ObsidianPurpleLight,
                            unfocusedBorderColor = ObsidianBorder,
                            focusedLabelColor = ObsidianPurpleLight,
                            cursorColor = ObsidianPurpleLight
                        )
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Hide Completed Tasks Toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(ObsidianSurfaceVariant)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = "Hide Completed Tasks",
                                style = MaterialTheme.typography.bodyLarge,
                                color = ObsidianText,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Filter out finished checklist items (- [x])",
                                style = MaterialTheme.typography.bodySmall,
                                color = ObsidianTextSecondary
                            )
                        }
                        Switch(
                            checked = hideCompletedTasks,
                            onCheckedChange = { hideCompletedTasks = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = ObsidianPurple,
                                uncheckedThumbColor = ObsidianTextSecondary,
                                uncheckedTrackColor = ObsidianBg
                            )
                        )
                    }
                }
            }

            // 3. Test & Preview Card
            Card(
                colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "3. Live Note Test & Preview",
                            style = MaterialTheme.typography.titleMedium,
                            color = ObsidianText
                        )
                        IconButton(
                            onClick = {
                                testResult = SafVaultReader.readDailyNote(context, currentConfig)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Test reading note",
                                tint = ObsidianPurpleLight
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    when (val result = testResult) {
                        null -> {
                            Text(
                                text = "Tap the refresh icon above or button below to test reading today's note with current settings.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = ObsidianTextSecondary
                            )
                        }
                        is NoteReadResult.Success -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = ObsidianSuccess,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Successfully loaded ${result.fileName}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = ObsidianSuccess,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))

                            // Preview Box
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(ObsidianSurfaceVariant)
                                    .border(1.dp, ObsidianBorder, RoundedCornerShape(8.dp))
                                    .padding(10.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = result.sanitizedText.ifBlank { "(Note is empty)" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = ObsidianText
                                )
                            }
                        }
                        is NoteReadResult.FileNotFound -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = ObsidianWarning,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "File '${result.fileName}' not found today",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = ObsidianWarning,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Searched folder: ${result.searchedFolder}\nTap below to test creating or opening it in Obsidian.",
                                style = MaterialTheme.typography.bodySmall,
                                color = ObsidianTextSecondary
                            )
                        }
                        is NoteReadResult.FolderNotFound -> {
                            Text(
                                text = "Subfolder '${result.missingSegment}' does not exist inside your vault directory.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = ObsidianError
                            )
                        }
                        is NoteReadResult.PermissionRevoked -> {
                            Text(
                                text = "Storage Access Permission was revoked: ${result.error}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = ObsidianError
                            )
                        }
                        is NoteReadResult.NotConfigured -> {
                            Text(
                                text = "Please select a vault directory first.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = ObsidianWarning
                            )
                        }
                        is NoteReadResult.Error -> {
                            Text(
                                text = "Error: ${result.message}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = ObsidianError
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                testResult = SafVaultReader.readDailyNote(context, currentConfig)
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ObsidianText)
                        ) {
                            Text("Test Read")
                        }

                        if (vaultName.isNotBlank()) {
                            val targetPath = ObsidianDeepLinkHelper.resolveRelativePath(
                                dailyNotesFolder,
                                "$formattedToday.md"
                            )
                            val deepLinkIntent = ObsidianDeepLinkHelper.createOpenNoteIntent(
                                vaultName,
                                targetPath
                            )
                            OutlinedButton(
                                onClick = {
                                    try {
                                        context.startActivity(deepLinkIntent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Could not open Obsidian: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = ObsidianPurpleLight)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Open Obsidian")
                            }
                        }
                    }
                }
            }

            // Save & Update Button
            Button(
                onClick = {
                    if (treeUriString.isNullOrBlank()) {
                        Toast.makeText(context, "Please select an Obsidian vault folder first", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (vaultName.isBlank()) {
                        Toast.makeText(context, "Please enter your vault name", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    scope.launch {
                        repository.updatePreferences(
                            treeUriString = treeUriString,
                            vaultName = vaultName,
                            dailyNotesFolder = dailyNotesFolder,
                            dateFormat = dateFormat.ifBlank { "yyyy-MM-dd" },
                            excludedSections = excludedSections,
                            hideCompletedTasks = hideCompletedTasks
                        )
                        onUpdateWidgets()
                        Toast.makeText(context, "Settings saved! Widget refreshed.", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ObsidianPurple,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Save & Update Home Widget",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
