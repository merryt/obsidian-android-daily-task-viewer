package com.obsidian.dailynote.deeplink

import android.content.Context
import android.content.Intent
import android.net.Uri

object ObsidianDeepLinkHelper {

    private fun encodeUriComponent(value: String): String {
        return java.net.URLEncoder.encode(value, "UTF-8")
            .replace("+", "%20")
            .replace("%2F", "/")
    }

    /**
     * Constructs the Obsidian deep link URI string:
     * obsidian://open?vault=<VAULT_NAME>&file=<RELATIVE_PATH>
     */
    fun buildOpenNoteUriString(vaultName: String, relativeNotePath: String): String {
        val cleanPath = relativeNotePath
            .trim()
            .trimStart('/')
            .removeSuffix(".md")

        val encodedVault = encodeUriComponent(vaultName.trim())
        val encodedFile = encodeUriComponent(cleanPath)

        return "obsidian://open?vault=$encodedVault&file=$encodedFile"
    }

    /**
     * Constructs the Obsidian deep link URI:
     * obsidian://open?vault=<VAULT_NAME>&file=<RELATIVE_PATH>
     */
    fun buildOpenNoteUri(vaultName: String, relativeNotePath: String): Uri {
        return Uri.parse(buildOpenNoteUriString(vaultName, relativeNotePath))
    }

    /**
     * Constructs the Obsidian deep link URI string to create a new note if it doesn't exist:
     * obsidian://new?vault=<VAULT_NAME>&name=<RELATIVE_PATH>
     */
    fun buildNewNoteUriString(vaultName: String, relativeNotePath: String): String {
        val cleanPath = relativeNotePath
            .trim()
            .trimStart('/')
            .removeSuffix(".md")

        val encodedVault = encodeUriComponent(vaultName.trim())
        val encodedFile = encodeUriComponent(cleanPath)

        return "obsidian://new?vault=$encodedVault&name=$encodedFile"
    }

    /**
     * Constructs the Obsidian deep link URI to create a new note if it doesn't exist:
     * obsidian://new?vault=<VAULT_NAME>&file=<RELATIVE_PATH>
     */
    fun buildNewNoteUri(vaultName: String, relativeNotePath: String): Uri {
        return Uri.parse(buildNewNoteUriString(vaultName, relativeNotePath))
    }

    /**
     * Creates an Intent to launch Obsidian directly to the daily note.
     */
    fun createOpenNoteIntent(vaultName: String, relativeNotePath: String): Intent {
        val uri = buildOpenNoteUri(vaultName, relativeNotePath)
        return Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Creates an Intent to launch Obsidian to create the daily note.
     */
    fun createNewNoteIntent(vaultName: String, relativeNotePath: String): Intent {
        val uri = buildNewNoteUri(vaultName, relativeNotePath)
        return Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Helper to compute the relative note path given a daily notes folder and date string.
     */
    fun resolveRelativePath(dailyNotesFolder: String, dateFileName: String): String {
        val folder = dailyNotesFolder.trim().trim('/')
        val baseFileName = dateFileName.removeSuffix(".md")
        return if (folder.isEmpty()) {
            baseFileName
        } else {
            "$folder/$baseFileName"
        }
    }
}
