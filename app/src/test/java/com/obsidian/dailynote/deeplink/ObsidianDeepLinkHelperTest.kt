package com.obsidian.dailynote.deeplink

import org.junit.Assert.assertEquals
import org.junit.Test

class ObsidianDeepLinkHelperTest {

    @Test
    fun resolveRelativePath_combinesFolderAndFileName() {
        assertEquals(
            "Daily Notes/2026-10-22",
            ObsidianDeepLinkHelper.resolveRelativePath("Daily Notes", "2026-10-22.md")
        )
        assertEquals(
            "2026-10-22",
            ObsidianDeepLinkHelper.resolveRelativePath("", "2026-10-22.md")
        )
        assertEquals(
            "Journal/Daily/2026-10-22",
            ObsidianDeepLinkHelper.resolveRelativePath("Journal/Daily/", "2026-10-22.md")
        )
    }

    @Test
    fun buildOpenNoteUriString_encodesVaultAndFilePath() {
        val uriString = ObsidianDeepLinkHelper.buildOpenNoteUriString(
            vaultName = "My Vault",
            relativeNotePath = "Daily Notes/2026-10-22.md"
        )

        assertEquals("obsidian://open?vault=My%20Vault&file=Daily%20Notes/2026-10-22", uriString)
    }

    @Test
    fun buildNewNoteUriString_encodesVaultAndFilePath() {
        val uriString = ObsidianDeepLinkHelper.buildNewNoteUriString(
            vaultName = "My Vault",
            relativeNotePath = "Daily Notes/2026-10-22"
        )

        assertEquals("obsidian://new?vault=My%20Vault&name=Daily%20Notes/2026-10-22", uriString)
    }
}
