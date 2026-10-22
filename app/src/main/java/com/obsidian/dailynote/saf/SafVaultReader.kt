package com.obsidian.dailynote.saf

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.obsidian.dailynote.data.VaultPreferences
import com.obsidian.dailynote.deeplink.ObsidianDeepLinkHelper
import com.obsidian.dailynote.markdown.MarkdownSanitizer
import com.obsidian.dailynote.markdown.SanitizedBlock
import java.io.InputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter

sealed class NoteReadResult {
    data class Success(
        val fileName: String,
        val relativePath: String,
        val rawContent: String,
        val sanitizedText: String,
        val blocks: List<SanitizedBlock>,
        val lastModified: Long
    ) : NoteReadResult()

    object NotConfigured : NoteReadResult()

    data class FileNotFound(
        val fileName: String,
        val relativePath: String,
        val searchedFolder: String
    ) : NoteReadResult()

    data class FolderNotFound(
        val missingSegment: String,
        val fullFolder: String
    ) : NoteReadResult()

    data class PermissionRevoked(
        val uriString: String,
        val error: String
    ) : NoteReadResult()

    data class Error(
        val message: String,
        val exception: Throwable? = null
    ) : NoteReadResult()
}

object SafVaultReader {

    /**
     * Reads today's Obsidian daily note using the configured SAF Tree URI and preferences.
     *
     * @param context Application/Activity context
     * @param preferences Current VaultPreferences
     * @param date The date to resolve (defaults to today's date)
     */
    fun readDailyNote(
        context: Context,
        preferences: VaultPreferences,
        date: LocalDate = LocalDate.now()
    ): NoteReadResult {
        val uriString = preferences.treeUriString
        if (uriString.isNullOrBlank()) {
            return NoteReadResult.NotConfigured
        }

        val treeUri = Uri.parse(uriString)

        // Verify root DocumentFile from Tree URI
        val rootDoc = try {
            DocumentFile.fromTreeUri(context, treeUri)
        } catch (e: SecurityException) {
            return NoteReadResult.PermissionRevoked(
                uriString = uriString,
                error = "Permission to access vault directory was revoked: ${e.localizedMessage}"
            )
        } catch (e: Exception) {
            return NoteReadResult.Error("Failed to open vault root: ${e.localizedMessage}", e)
        }

        if (rootDoc == null || !rootDoc.exists() || !rootDoc.isDirectory) {
            return NoteReadResult.Error("Vault root directory is invalid or inaccessible.")
        }

        // Navigate to daily notes subfolder if specified
        var targetDir: DocumentFile = rootDoc
        val folderConfig = preferences.dailyNotesFolder.trim()
        if (folderConfig.isNotEmpty()) {
            val segments = folderConfig.split("/", "\\").map { it.trim() }.filter { it.isNotEmpty() }
            for (segment in segments) {
                val nextDir = targetDir.findFile(segment)
                if (nextDir == null || !nextDir.isDirectory) {
                    return NoteReadResult.FolderNotFound(
                        missingSegment = segment,
                        fullFolder = folderConfig
                    )
                }
                targetDir = nextDir
            }
        }

        // Format date to filename, e.g. "2026-10-22.md"
        val formatter = try {
            DateTimeFormatter.ofPattern(preferences.dateFormat.ifBlank { "yyyy-MM-dd" })
        } catch (e: Exception) {
            DateTimeFormatter.ofPattern("yyyy-MM-dd")
        }

        val formattedDate = date.format(formatter)
        val fileName = "$formattedDate.md"
        val relativePath = ObsidianDeepLinkHelper.resolveRelativePath(folderConfig, fileName)

        // Locate today's markdown file
        val fileDoc = targetDir.findFile(fileName)
        if (fileDoc == null || !fileDoc.exists() || !fileDoc.isFile) {
            return NoteReadResult.FileNotFound(
                fileName = fileName,
                relativePath = relativePath,
                searchedFolder = folderConfig.ifEmpty { "/" }
            )
        }

        // Read content stream
        return try {
            val rawText = context.contentResolver.openInputStream(fileDoc.uri)?.use { stream: InputStream ->
                stream.bufferedReader(Charsets.UTF_8).readText()
            } ?: ""

            val excludedSections = preferences.getExcludedSectionsSet()
            val sanitizedText = MarkdownSanitizer.sanitizeToString(
                rawMarkdown = rawText,
                excludedSections = excludedSections,
                hideCompletedTasks = preferences.hideCompletedTasks
            )
            val blocks = MarkdownSanitizer.parseBlocks(
                rawMarkdown = rawText,
                excludedSections = excludedSections,
                hideCompletedTasks = preferences.hideCompletedTasks
            )

            NoteReadResult.Success(
                fileName = fileName,
                relativePath = relativePath,
                rawContent = rawText,
                sanitizedText = sanitizedText,
                blocks = blocks,
                lastModified = fileDoc.lastModified()
            )
        } catch (e: SecurityException) {
            NoteReadResult.PermissionRevoked(fileDoc.uri.toString(), e.localizedMessage ?: "SecurityException")
        } catch (e: Exception) {
            NoteReadResult.Error("Error reading daily note file: ${e.localizedMessage}", e)
        }
    }
}
