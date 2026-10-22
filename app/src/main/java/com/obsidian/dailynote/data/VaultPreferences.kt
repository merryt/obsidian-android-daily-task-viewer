package com.obsidian.dailynote.data

data class VaultPreferences(
    val treeUriString: String? = null,
    val vaultName: String = "",
    val dailyNotesFolder: String = "",
    val dateFormat: String = "yyyy-MM-dd",
    val excludedSections: String = "Journaling",
    val hideCompletedTasks: Boolean = true
) {
    val isConfigured: Boolean
        get() = !treeUriString.isNullOrBlank() && vaultName.isNotBlank()

    fun getExcludedSectionsSet(): Set<String> {
        return excludedSections
            .split(",", ";", "\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }
}
