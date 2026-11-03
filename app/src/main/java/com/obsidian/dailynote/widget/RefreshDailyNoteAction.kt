package com.obsidian.dailynote.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import kotlinx.coroutines.delay

class RefreshDailyNoteAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        try {
            // Set refreshing to true so the animated spinner displays
            updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                val mutable = prefs.toMutablePreferences()
                mutable[DailyNoteGlanceWidget.IS_REFRESHING_KEY] = true
                mutable
            }
            DailyNoteGlanceWidget().update(context, glanceId)

            // Brief delay to provide clear visual animation feedback while reading note
            delay(600)
        } finally {
            // Restore static refresh icon and refresh widget content
            updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                val mutable = prefs.toMutablePreferences()
                mutable[DailyNoteGlanceWidget.IS_REFRESHING_KEY] = false
                mutable
            }
            DailyNoteGlanceWidget().update(context, glanceId)
        }
    }
}
