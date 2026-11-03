package com.obsidian.dailynote.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.obsidian.dailynote.MainActivity
import com.obsidian.dailynote.R
import com.obsidian.dailynote.data.VaultPreferencesRepository
import com.obsidian.dailynote.deeplink.ObsidianDeepLinkHelper
import com.obsidian.dailynote.markdown.BlockType
import com.obsidian.dailynote.markdown.SanitizedBlock
import com.obsidian.dailynote.saf.NoteReadResult
import com.obsidian.dailynote.saf.SafVaultReader
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class DailyNoteGlanceWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    companion object {
        val IS_REFRESHING_KEY = booleanPreferencesKey("is_refreshing")

        private val ColorBg = ColorProvider(Color(0xFF161622))
        private val ColorHeader = ColorProvider(Color(0xFF202030))
        private val ColorCard = ColorProvider(Color(0xFF1E1E2D))
        private val ColorPurple = ColorProvider(Color(0xFFA78BFA))
        private val ColorTextPrimary = ColorProvider(Color(0xFFF1F5F9))
        private val ColorTextSecondary = ColorProvider(Color(0xFF94A3B8))
        private val ColorTextDone = ColorProvider(Color(0xFF64748B))
        private val ColorTaskBox = ColorProvider(Color(0xFF818CF8))
        private val ColorDivider = ColorProvider(Color(0xFF2E2E44))
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = VaultPreferencesRepository(context)
        val prefs = repo.getPreferencesSnapshot()
        val readResult = SafVaultReader.readDailyNote(context, prefs)
        val today = LocalDate.now()
        val friendlyDate = today.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

        provideContent {
            val glancePrefs = currentState<Preferences>()
            val isRefreshing = glancePrefs[IS_REFRESHING_KEY] ?: false

            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .cornerRadius(18.dp)
                    .background(ColorBg)
                    .padding(8.dp)
            ) {
                Column(modifier = GlanceModifier.fillMaxSize()) {
                    // Top Bar: Obsidian Badge, Date, and Actions
                    HeaderBar(
                        context = context,
                        dateString = friendlyDate,
                        vaultName = prefs.vaultName,
                        isRefreshing = isRefreshing,
                        onOpenSettings = actionStartActivity(Intent(context, MainActivity::class.java))
                    )

                    Spacer(modifier = GlanceModifier.height(6.dp))

                    // Content Body based on reading state
                    Box(
                        modifier = GlanceModifier
                            .fillMaxSize()
                            .cornerRadius(12.dp)
                            .background(ColorCard)
                            .padding(8.dp)
                    ) {
                        when (readResult) {
                            is NoteReadResult.Success -> {
                                val openIntent = ObsidianDeepLinkHelper.createOpenNoteIntent(
                                    vaultName = prefs.vaultName,
                                    relativeNotePath = readResult.relativePath
                                )
                                DailyNoteContentView(
                                    result = readResult,
                                    modifier = GlanceModifier
                                        .fillMaxSize()
                                        .clickable(actionStartActivity(openIntent))
                                )
                            }
                            is NoteReadResult.FileNotFound -> {
                                val createIntent = ObsidianDeepLinkHelper.createNewNoteIntent(
                                    vaultName = prefs.vaultName,
                                    relativeNotePath = readResult.relativePath
                                )
                                EmptyStateView(
                                    title = "No note for today",
                                    subtitle = "${readResult.fileName} does not exist yet.",
                                    actionLabel = "Tap to create in Obsidian",
                                    onClick = actionStartActivity(createIntent)
                                )
                            }
                            is NoteReadResult.NotConfigured -> {
                                EmptyStateView(
                                    title = "Vault not configured",
                                    subtitle = "Please select your Obsidian vault directory.",
                                    actionLabel = "Tap to setup in app",
                                    onClick = actionStartActivity(Intent(context, MainActivity::class.java))
                                )
                            }
                            is NoteReadResult.FolderNotFound -> {
                                EmptyStateView(
                                    title = "Folder not found",
                                    subtitle = "Could not find folder '${readResult.missingSegment}'.",
                                    actionLabel = "Tap to configure path",
                                    onClick = actionStartActivity(Intent(context, MainActivity::class.java))
                                )
                            }
                            is NoteReadResult.PermissionRevoked -> {
                                EmptyStateView(
                                    title = "Storage permission revoked",
                                    subtitle = "Please re-select your Obsidian vault folder.",
                                    actionLabel = "Tap to grant permission",
                                    onClick = actionStartActivity(Intent(context, MainActivity::class.java))
                                )
                            }
                            is NoteReadResult.Error -> {
                                EmptyStateView(
                                    title = "Error reading note",
                                    subtitle = readResult.message,
                                    actionLabel = "Tap to open settings",
                                    onClick = actionStartActivity(Intent(context, MainActivity::class.java))
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun HeaderBar(
        context: Context,
        dateString: String,
        vaultName: String,
        isRefreshing: Boolean,
        onOpenSettings: androidx.glance.action.Action
    ) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .cornerRadius(10.dp)
                .background(ColorHeader)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Obsidian Crystal Logo
            Image(
                provider = ImageProvider(R.drawable.ic_obsidian_logo),
                contentDescription = "Obsidian",
                modifier = GlanceModifier.size(18.dp)
            )

            Spacer(modifier = GlanceModifier.width(8.dp))

            // Date and Vault Title
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = dateString,
                    style = TextStyle(
                        color = ColorTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                if (vaultName.isNotBlank()) {
                    Text(
                        text = vaultName,
                        style = TextStyle(
                            color = ColorPurple,
                            fontSize = 10.sp
                        )
                    )
                }
            }

            // Settings Button
            Image(
                provider = ImageProvider(R.drawable.ic_settings),
                contentDescription = "Settings",
                modifier = GlanceModifier
                    .size(22.dp)
                    .clickable(onOpenSettings)
            )

            Spacer(modifier = GlanceModifier.width(12.dp))

            // Refresh Button (animates while updating)
            if (isRefreshing) {
                AndroidRemoteViews(
                    remoteViews = RemoteViews(context.packageName, R.layout.widget_refresh_spinner),
                    modifier = GlanceModifier.size(22.dp)
                )
            } else {
                Image(
                    provider = ImageProvider(R.drawable.ic_refresh),
                    contentDescription = "Refresh",
                    modifier = GlanceModifier
                        .size(22.dp)
                        .clickable(actionRunCallback<RefreshDailyNoteAction>())
                )
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun DailyNoteContentView(
        result: NoteReadResult.Success,
        modifier: GlanceModifier
    ) {
        if (result.blocks.isEmpty() || result.sanitizedText.isBlank()) {
            Box(
                modifier = modifier,
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Daily note is empty.\nTap to edit in Obsidian.",
                    style = TextStyle(
                        color = ColorTextSecondary,
                        fontSize = 12.sp
                    )
                )
            }
            return
        }

        LazyColumn(modifier = modifier) {
            items(result.blocks) { block ->
                BlockItem(block)
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun BlockItem(block: SanitizedBlock) {
        val indentPadding = (block.indentLevel * 16).dp

        when (block.type) {
            BlockType.HEADING_1 -> {
                Text(
                    text = block.text,
                    style = TextStyle(
                        color = ColorPurple,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = GlanceModifier.padding(top = 6.dp, bottom = 2.dp)
                )
            }
            BlockType.HEADING_2 -> {
                Text(
                    text = block.text,
                    style = TextStyle(
                        color = ColorPurple,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = GlanceModifier.padding(top = 4.dp, bottom = 2.dp)
                )
            }
            BlockType.HEADING_3 -> {
                Text(
                    text = block.text,
                    style = TextStyle(
                        color = ColorTextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = GlanceModifier.padding(top = 3.dp, bottom = 1.dp)
                )
            }
            BlockType.TASK_TODO -> {
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (block.indentLevel > 0) {
                        Spacer(modifier = GlanceModifier.width(indentPadding))
                    }
                    Text(
                        text = "☐",
                        style = TextStyle(
                            color = ColorTaskBox,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = GlanceModifier.width(6.dp))
                    Text(
                        text = block.text,
                        style = TextStyle(
                            color = ColorTextPrimary,
                            fontSize = 12.sp
                        )
                    )
                }
            }
            BlockType.TASK_DONE -> {
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .padding(top = 1.dp, bottom = 1.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (block.indentLevel > 0) {
                        Spacer(modifier = GlanceModifier.width(indentPadding))
                    }
                    Text(
                        text = "☑",
                        style = TextStyle(
                            color = ColorTextDone,
                            fontSize = 13.sp
                        )
                    )
                    Spacer(modifier = GlanceModifier.width(6.dp))
                    Text(
                        text = block.text,
                        style = TextStyle(
                            color = ColorTextDone,
                            fontSize = 12.sp
                        )
                    )
                }
            }
            BlockType.BULLET_ITEM -> {
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .padding(top = 1.dp, bottom = 1.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (block.indentLevel > 0) {
                        Spacer(modifier = GlanceModifier.width(indentPadding))
                    }
                    Text(
                        text = "•",
                        style = TextStyle(
                            color = ColorPurple,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = GlanceModifier.width(6.dp))
                    Text(
                        text = block.text,
                        style = TextStyle(
                            color = ColorTextPrimary,
                            fontSize = 12.sp
                        )
                    )
                }
            }
            BlockType.NUMBERED_ITEM -> {
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .padding(top = 1.dp, bottom = 1.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (block.indentLevel > 0) {
                        Spacer(modifier = GlanceModifier.width(indentPadding))
                    }
                    Text(
                        text = block.text,
                        style = TextStyle(
                            color = ColorTextPrimary,
                            fontSize = 12.sp
                        )
                    )
                }
            }
            BlockType.BLOCKQUOTE -> {
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (block.indentLevel > 0) {
                        Spacer(modifier = GlanceModifier.width(indentPadding))
                    }
                    Box(
                        modifier = GlanceModifier
                            .width(3.dp)
                            .height(14.dp)
                            .background(ColorPurple)
                    ) {}
                    Spacer(modifier = GlanceModifier.width(6.dp))
                    Text(
                        text = block.text,
                        style = TextStyle(
                            color = ColorTextSecondary,
                            fontSize = 11.sp
                        )
                    )
                }
            }
            BlockType.DIVIDER -> {
                Box(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(ColorDivider)
                        .padding(vertical = 4.dp)
                ) {}
            }
            BlockType.PARAGRAPH -> {
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .padding(top = 1.dp, bottom = 1.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (block.indentLevel > 0) {
                        Spacer(modifier = GlanceModifier.width(indentPadding))
                    }
                    Text(
                        text = block.text,
                        style = TextStyle(
                            color = ColorTextPrimary,
                            fontSize = 12.sp
                        )
                    )
                }
            }
            BlockType.EMPTY_LINE -> {
                Spacer(modifier = GlanceModifier.height(4.dp))
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun EmptyStateView(
        title: String,
        subtitle: String,
        actionLabel: String,
        onClick: androidx.glance.action.Action
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .clickable(onClick)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = TextStyle(
                    color = ColorTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = subtitle,
                style = TextStyle(
                    color = ColorTextSecondary,
                    fontSize = 11.sp
                )
            )
            Spacer(modifier = GlanceModifier.height(8.dp))
            Text(
                text = actionLabel,
                style = TextStyle(
                    color = ColorPurple,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}
