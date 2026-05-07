package com.pocketweibo.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.reminderQuickPresetStore: DataStore<Preferences> by preferencesDataStore(
    name = "reminder_quick_presets"
)

/**
 * Relative-time quick actions in the post reminder sheet (excluding "tomorrow 9:00").
 * Usage counts drive the first three slots; slot four is always tomorrow.
 */
enum class ReminderQuickPresetId {
    M15,
    M30,
    H1,
    H3,
    H6,
    H10;

    companion object {
        /** Tie-break when counts are equal: earlier = higher priority for top-3. */
        val DEFAULT_ORDER: List<ReminderQuickPresetId> = listOf(M15, M30, H1, H3, H6, H10)
    }
}

data class ReminderQuickBarState(
    /** Three most-used presets (by local counts), stable tie-break. */
    val topThree: List<ReminderQuickPresetId>,
    /** The two presets not in [topThree], in [DEFAULT_ORDER]. */
    val remainder: List<ReminderQuickPresetId>
) {
    init {
        require(topThree.size == 3 && topThree.toSet().size == 3) { "topThree must be 3 distinct presets" }
        val n = ReminderQuickPresetId.entries.size
        require(remainder.size == n - 3 && remainder.toSet().size == n - 3) {
            "remainder must hold all presets not in topThree"
        }
        require((topThree.toSet() + remainder.toSet()).size == n)
    }
}

object ReminderQuickPresetStats {

    fun defaultBarState(): ReminderQuickBarState {
        val order = ReminderQuickPresetId.DEFAULT_ORDER
        return ReminderQuickBarState(
            topThree = order.take(3),
            remainder = order.drop(3)
        )
    }

    fun millisOffsetMillis(id: ReminderQuickPresetId): Long {
        val now = System.currentTimeMillis()
        return when (id) {
            ReminderQuickPresetId.M15 -> now + 15 * 60_000L
            ReminderQuickPresetId.M30 -> now + 30 * 60_000L
            ReminderQuickPresetId.H1 -> now + 60 * 60_000L
            ReminderQuickPresetId.H3 -> now + 3 * 60 * 60_000L
            ReminderQuickPresetId.H6 -> now + 6 * 60 * 60_000L
            ReminderQuickPresetId.H10 -> now + 10 * 60 * 60_000L
        }
    }

    private fun key(id: ReminderQuickPresetId) = intPreferencesKey("uses_${id.name}")

    suspend fun recordUse(context: Context, id: ReminderQuickPresetId) {
        context.applicationContext.reminderQuickPresetStore.edit { prefs ->
            val k = key(id)
            prefs[k] = (prefs[k] ?: 0) + 1
        }
    }

    fun barStateFlow(context: Context): Flow<ReminderQuickBarState> =
        context.applicationContext.reminderQuickPresetStore.data.map { prefs -> barFromPreferences(prefs) }

    internal fun barFromCounts(counts: Map<ReminderQuickPresetId, Int>): ReminderQuickBarState {
        val order = ReminderQuickPresetId.DEFAULT_ORDER
        val scored = order.map { id -> id to (counts[id] ?: 0) }
        val topThree = scored
            .sortedWith(
                compareByDescending<Pair<ReminderQuickPresetId, Int>> { it.second }
                    .thenBy { order.indexOf(it.first) }
            )
            .take(3)
            .map { it.first }
        val remainder = order.filter { it !in topThree }
        return ReminderQuickBarState(topThree = topThree, remainder = remainder)
    }

    private fun barFromPreferences(prefs: Preferences): ReminderQuickBarState {
        val counts = ReminderQuickPresetId.DEFAULT_ORDER.associateWith { id -> prefs[key(id)] ?: 0 }
        return barFromCounts(counts)
    }
}
