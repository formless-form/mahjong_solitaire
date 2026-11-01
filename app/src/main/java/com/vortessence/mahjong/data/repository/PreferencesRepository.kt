package com.vortessence.mahjong.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vortessence.mahjong.data.model.GameSettings
import com.vortessence.mahjong.data.model.MAX_HINTS
import com.vortessence.mahjong.data.model.Progress
import com.vortessence.mahjong.data.model.SavedGameState
import com.vortessence.mahjong.data.model.TableSurface
import com.vortessence.mahjong.data.model.WideBoards
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.LocalDateTime
import kotlin.random.Random

// A corrupted file starts afresh rather than crashing every launch
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "mahjong_preferences",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

private const val MEASURED_LEVEL_ORDER = 2

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val SOUND_VOLUME = floatPreferencesKey("sound_volume")
        val MUSIC_ENABLED = booleanPreferencesKey("music_enabled")
        val MUSIC_VOLUME = floatPreferencesKey("music_volume")
        val AUTO_ZOOM = booleanPreferencesKey("auto_zoom")
        val HIGHLIGHT_FREE_TILES = booleanPreferencesKey("highlight_free_tiles")
        val VIBRATION = booleanPreferencesKey("vibration")

        val HIGHEST_LEVEL = intPreferencesKey("highest_level")
        val COMPLETED_CONSTELLATIONS = stringSetPreferencesKey("completed_constellations")
        // The epoch days whose Today's Puzzle was solved
        val PUZZLE_DAYS_SOLVED = stringSetPreferencesKey("puzzle_days_solved")
        val SAVED_GAME = stringPreferencesKey("saved_game")
        val TABLE_SURFACE = stringPreferencesKey("table_surface")
        val LAMP_BRIGHTNESS = floatPreferencesKey("lamp_brightness")
        val FIREPLACE = booleanPreferencesKey("fireplace")
        val LOW_PERFORMANCE = booleanPreferencesKey("low_performance")
        val CLEAN_CONSTELLATIONS = stringSetPreferencesKey("clean_constellations")
        val HINTS = intPreferencesKey("hints")
        val FIRE_VOLUME = floatPreferencesKey("fire_volume")
        val FIRE_INTENSITY = floatPreferencesKey("fire_intensity")
        val WIDE_BOARDS = stringPreferencesKey("wide_boards")
        val LAST_WIN_DAY = longPreferencesKey("last_win_epoch_day")
        // Which level numbering the stored highest level is in: 2 since levels were ordered by
        // measured difficulty (absent means the order before)
        val LEVEL_ORDER = intPreferencesKey("level_order")
        fun congratsBag(pool: CongratsPool) = stringPreferencesKey("congrats_bag_${pool.key}")
    }

    // Storage that can't be read plays on with the defaults, and a write that fails (a full phone)
    // is lost rather than crashing the game
    private val data: Flow<Preferences> = context.dataStore.data.catch {
        if (it is IOException) emit(emptyPreferences()) else throw it
    }

    private suspend fun write(transform: suspend (MutablePreferences) -> Unit) {
        try {
            context.dataStore.edit(transform)
        } catch (_: IOException) {
        }
    }

    // A setting never changed reads as GameSettings' own default, so a new default reaches it
    val settingsFlow: Flow<GameSettings> = data.map { prefs ->
        val d = GameSettings()
        GameSettings(
            soundEnabled = prefs[Keys.SOUND_ENABLED] ?: d.soundEnabled,
            soundVolume = prefs[Keys.SOUND_VOLUME] ?: d.soundVolume,
            musicEnabled = prefs[Keys.MUSIC_ENABLED] ?: d.musicEnabled,
            musicVolume = prefs[Keys.MUSIC_VOLUME] ?: d.musicVolume,
            autoZoomEnabled = prefs[Keys.AUTO_ZOOM] ?: d.autoZoomEnabled,
            highlightFreeTiles = prefs[Keys.HIGHLIGHT_FREE_TILES] ?: d.highlightFreeTiles,
            vibrationEnabled = prefs[Keys.VIBRATION] ?: d.vibrationEnabled,
            tableSurface = prefs[Keys.TABLE_SURFACE]
                ?.let { name -> TableSurface.entries.firstOrNull { it.name == name } }
                ?: d.tableSurface,
            lampBrightness = prefs[Keys.LAMP_BRIGHTNESS] ?: d.lampBrightness,
            fireplaceLit = prefs[Keys.FIREPLACE] ?: d.fireplaceLit,
            lowPerformance = prefs[Keys.LOW_PERFORMANCE] ?: d.lowPerformance,
            fireVolume = prefs[Keys.FIRE_VOLUME] ?: d.fireVolume,
            fireIntensity = prefs[Keys.FIRE_INTENSITY] ?: d.fireIntensity,
            wideBoards = prefs[Keys.WIDE_BOARDS]
                ?.let { name -> WideBoards.entries.firstOrNull { it.name == name } }
                ?: d.wideBoards,
        )
    }

    val progressFlow: Flow<Progress> = data.map { prefs ->
        Progress(
            highestLevelReached = prefs[Keys.HIGHEST_LEVEL] ?: 1,
            completedConstellations = prefs[Keys.COMPLETED_CONSTELLATIONS] ?: emptySet(),
            cleanConstellations = prefs[Keys.CLEAN_CONSTELLATIONS] ?: emptySet(),
            hints = prefs[Keys.HINTS] ?: 0,
            puzzleDaysSolved = prefs[Keys.PUZZLE_DAYS_SOLVED]?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet(),
        )
    }.distinctUntilChanged()

    /**
     * Writes the settings that differ between [from] and [to], and only those: the others keep
     * following their defaults, and a write can't put back a value changed in the meantime.
     */
    suspend fun updateSettings(from: GameSettings, to: GameSettings) {
        if (from == to) return
        write { prefs ->
            fun <T> put(key: Preferences.Key<T>, value: (GameSettings) -> T) {
                if (value(to) != value(from)) prefs[key] = value(to)
            }
            put(Keys.SOUND_ENABLED) { it.soundEnabled }
            put(Keys.SOUND_VOLUME) { it.soundVolume }
            put(Keys.MUSIC_ENABLED) { it.musicEnabled }
            put(Keys.MUSIC_VOLUME) { it.musicVolume }
            put(Keys.AUTO_ZOOM) { it.autoZoomEnabled }
            put(Keys.HIGHLIGHT_FREE_TILES) { it.highlightFreeTiles }
            put(Keys.VIBRATION) { it.vibrationEnabled }
            put(Keys.TABLE_SURFACE) { it.tableSurface.name }
            put(Keys.LAMP_BRIGHTNESS) { it.lampBrightness }
            put(Keys.FIREPLACE) { it.fireplaceLit }
            put(Keys.LOW_PERFORMANCE) { it.lowPerformance }
            put(Keys.FIRE_VOLUME) { it.fireVolume }
            put(Keys.FIRE_INTENSITY) { it.fireIntensity }
            put(Keys.WIDE_BOARDS) { it.wideBoards.name }
        }
    }

    /**
     * Once, after levels were ordered by measured difficulty: the stored highest level is carried
     * over to the new numbering, so no layout a player could play becomes locked.
     */
    /**
     * Hints became earned in 1.4.6: a player who had already played starts with 2, a new one
     * with none. Settled once, on the first launch that has hints.
     */
    suspend fun migrateHints() {
        write { prefs ->
            if (prefs[Keys.HINTS] != null) return@write
            val played = (prefs[Keys.HIGHEST_LEVEL] ?: 1) > 1 ||
                !prefs[Keys.COMPLETED_CONSTELLATIONS].isNullOrEmpty() ||
                !prefs[Keys.PUZZLE_DAYS_SOLVED].isNullOrEmpty()
            prefs[Keys.HINTS] = if (played) EXISTING_PLAYER_HINTS else 0
        }
    }

    /** One hint spent; false if there were none. */
    suspend fun spendHint(): Boolean {
        var spent = false
        write { prefs ->
            val hints = prefs[Keys.HINTS] ?: 0
            if (hints > 0) {
                prefs[Keys.HINTS] = hints - 1
                spent = true
            }
        }
        return spent
    }

    suspend fun migrateLevelOrder() {
        write { prefs ->
            if ((prefs[Keys.LEVEL_ORDER] ?: 1) >= MEASURED_LEVEL_ORDER) return@write
            prefs[Keys.LEVEL_ORDER] = MEASURED_LEVEL_ORDER
            val highest = prefs[Keys.HIGHEST_LEVEL] ?: return@write
            prefs[Keys.HIGHEST_LEVEL] = LayoutDifficulty.highestAfterReorder(highest.coerceAtLeast(0)) { ConstellationRepository.levelIndexOf(it) }
        }
    }

    /** Today's Puzzle for [day] (an epoch day) solved: ticked on the calendar. */
    suspend fun recordPuzzleSolved(day: Long) {
        write { prefs ->
            prefs[Keys.PUZZLE_DAYS_SOLVED] = (prefs[Keys.PUZZLE_DAYS_SOLVED] ?: emptySet()) + day.toString()
        }
    }

    /** Records a win; true if it was the player's first clean clear (their first gold ribbon). */
    suspend fun recordGameWon(constellationId: String, levelIndex: Int?, clean: Boolean = false, puzzleDay: Long? = null): Boolean {
        var firstRibbon = false
        write { prefs ->
            if (clean) {
                val cleaned = prefs[Keys.CLEAN_CONSTELLATIONS] ?: emptySet()
                // Today's Puzzle is cleaned per day (no ribbon on the layout itself); anything else
                // per layout. The first clean clear of each earns a hint
                val key = puzzleDay?.let { "$PUZZLE_DAY_PREFIX$it" } ?: constellationId
                firstRibbon = cleaned.none { !it.startsWith(PUZZLE_DAY_PREFIX) } && puzzleDay == null
                if (key !in cleaned) {
                    prefs[Keys.CLEAN_CONSTELLATIONS] = cleaned + key
                    prefs[Keys.HINTS] = ((prefs[Keys.HINTS] ?: 0) + 1).coerceAtMost(MAX_HINTS)
                }
            }
            // Level progression
            if (levelIndex != null) {
                val currentHighest = prefs[Keys.HIGHEST_LEVEL] ?: 1
                if (levelIndex >= currentHighest) {
                    prefs[Keys.HIGHEST_LEVEL] = levelIndex + 1
                }
            }

            // Completed set
            val completedSet = (prefs[Keys.COMPLETED_CONSTELLATIONS] ?: emptySet()).toMutableSet()
            completedSet.add(constellationId)
            prefs[Keys.COMPLETED_CONSTELLATIONS] = completedSet
        }
        return firstRibbon
    }

    /**
     * Picks the congratulations for a cleared board. Context pools (tutorial, first win of the day,
     * late night, morning) are used some of the time; every pool is dealt as a shuffled deck that
     * persists across launches, so no message repeats until its whole pool has been seen.
     */
    suspend fun nextCongratsMessage(isTutorial: Boolean, now: LocalDateTime = LocalDateTime.now()): String {
        var message = CongratsPool.GENERAL.messages.first()
        write { prefs ->
            // A day runs from 4 am, so a win just after midnight belongs to the night before
            val today = now.minusHours(4).toLocalDate().toEpochDay()
            val firstToday = (prefs[Keys.LAST_WIN_DAY] ?: Long.MIN_VALUE) != today
            prefs[Keys.LAST_WIN_DAY] = today
            val hour = now.hour
            val pool = when {
                isTutorial -> CongratsPool.TUTORIAL
                firstToday && Random.nextFloat() < 0.5f -> CongratsPool.FIRST_TODAY
                (hour >= 22 || hour < 4) && Random.nextFloat() < 0.4f -> CongratsPool.LATE_NIGHT
                hour in 5..10 && Random.nextFloat() < 0.3f -> CongratsPool.MORNING
                else -> CongratsPool.GENERAL
            }
            message = pool.messages[dealFromBag(prefs, pool)]
        }
        return message
    }

    // Bag state "seed,position,lastOfPreviousBag": the order is rebuilt from the seed, and a new
    // bag never starts with the message the previous bag ended on
    private fun dealFromBag(prefs: MutablePreferences, pool: CongratsPool): Int {
        val size = pool.messages.size
        val parts = prefs[Keys.congratsBag(pool)]?.split(",")?.mapNotNull { it.toLongOrNull() }
        var seed = parts?.getOrNull(0) ?: 0L
        var position = parts?.getOrNull(1)?.toInt()?.coerceAtLeast(0) ?: size
        var previousLast = parts?.getOrNull(2)?.toInt() ?: -1
        fun order(): List<Int> {
            val o = (0 until size).shuffled(Random(seed)).toMutableList()
            if (size > 1 && o[0] == previousLast) o[0] = o[1].also { o[1] = o[0] }
            return o
        }
        if (position >= size) {
            if (parts != null) previousLast = order().last()
            seed = Random.nextLong()
            position = 0
        }
        val index = order()[position]
        prefs[Keys.congratsBag(pool)] = "$seed,${position + 1},$previousLast"
        return index
    }

    // Parsed again only when the save itself changed, not on every other write
    val savedGameFlow: Flow<SavedGameState?> = data.map { prefs -> prefs[Keys.SAVED_GAME] ?: "" }
        .distinctUntilChanged()
        .map { raw -> if (raw.isNotEmpty()) SavedGameState.deserialize(raw) else null }

    suspend fun saveGame(savedState: SavedGameState) {
        write { prefs ->
            prefs[Keys.SAVED_GAME] = savedState.serialize()
        }
    }

    suspend fun clearSavedGame() {
        write { prefs ->
            prefs.remove(Keys.SAVED_GAME)
        }
    }
}

// Players from before hints were earned start with these
private const val EXISTING_PLAYER_HINTS = 2

// Clean-clear keys for Today's Puzzle days, beside the layout ids
private const val PUZZLE_DAY_PREFIX = "day:"
