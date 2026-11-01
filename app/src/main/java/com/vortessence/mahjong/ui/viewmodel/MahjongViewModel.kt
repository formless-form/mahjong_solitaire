package com.vortessence.mahjong.ui.viewmodel

import android.app.Application
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vortessence.mahjong.audio.AudioManager
import com.vortessence.mahjong.data.model.Difficulty
import com.vortessence.mahjong.data.model.Constellation
import com.vortessence.mahjong.data.model.ContinueInfo
import com.vortessence.mahjong.data.model.GameSettings
import com.vortessence.mahjong.data.model.GameState
import com.vortessence.mahjong.data.model.Progress
import com.vortessence.mahjong.data.model.SavedGameState
import com.vortessence.mahjong.data.model.TableSurface
import com.vortessence.mahjong.data.repository.ConstellationRepository
import com.vortessence.mahjong.data.repository.PreferencesRepository
import com.vortessence.mahjong.data.repository.levelLabel
import com.vortessence.mahjong.engine.MahjongGameEngine
import com.vortessence.mahjong.engine.TileTapResult
import com.vortessence.mahjong.audio.FireRoom
import com.vortessence.mahjong.engine.TodaysPuzzle
import com.vortessence.mahjong.engine.TutorialScript
import com.vortessence.mahjong.engine.TutorialStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

sealed class Screen {
    data object MainMenu : Screen()
    data object LevelSelect : Screen()
    data object ConstellationSelect : Screen()
    data object TodaysPuzzle : Screen()
    data object Game : Screen()
    data object Settings : Screen()
}

class MahjongViewModel(application: Application) : AndroidViewModel(application) {

    private val prefsRepo = PreferencesRepository(application)
    val audioManager = AudioManager(application)

    // Dark Mahjong is portrait-only, so levels use upstream's tall, phone-shaped layouts
    // (most mahjong layouts are taller than wide); the wide classics stay under Layouts
    val currentLevelConstellations: List<Constellation>
        get() = ConstellationRepository.levelConstellations

    private val _currentScreen = MutableStateFlow<Screen>(Screen.MainMenu)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private val _gameState = MutableStateFlow<GameState?>(null)
    val gameState: StateFlow<GameState?> = _gameState.asStateFlow()

    private val _currentLevelIndex = MutableStateFlow<Int?>(null)
    val currentLevelIndex: StateFlow<Int?> = _currentLevelIndex.asStateFlow()

    // The day (an epoch day) whose Today's Puzzle is on the table, null for any other board
    private val _puzzleDay = MutableStateFlow<Long?>(null)
    val puzzleDay: StateFlow<Long?> = _puzzleDay.asStateFlow()

    // Index into TutorialScript.steps while the guided tutorial runs, null otherwise
    private val _tutorialStepIndex = MutableStateFlow<Int?>(null)
    val tutorialStep: StateFlow<TutorialStep?> = _tutorialStepIndex
        .map { index -> index?.let { TutorialScript.steps.getOrNull(it) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val currentTutorialStep: TutorialStep?
        get() = _tutorialStepIndex.value?.let { TutorialScript.steps.getOrNull(it) }

    private val _settings = MutableStateFlow(GameSettings())
    val settings: StateFlow<GameSettings> = _settings.asStateFlow()

    // Congratulations for the board just cleared; null until it has been chosen
    private val _winMessage = MutableStateFlow<String?>(null)
    val winMessage: StateFlow<String?> = _winMessage.asStateFlow()

    // This win was the player's first clean clear: its card introduces the gold ribbon
    private val _firstRibbon = MutableStateFlow(false)
    val firstRibbon: StateFlow<Boolean> = _firstRibbon.asStateFlow()

    private val _progress = MutableStateFlow(Progress())
    val progress: StateFlow<Progress> = _progress.asStateFlow()

    private val _savedGame = MutableStateFlow<SavedGameState?>(null)

    /** Today's Puzzle started (a move made) and not yet solved: Continue rather than Play. */
    val todaysPuzzleStarted: StateFlow<Boolean> = combine(_gameState, _puzzleDay, _savedGame) { state, day, saved ->
        val today = TodaysPuzzle.today()
        (day == today && state != null && !state.isWon && state.moveHistory.isNotEmpty()) ||
            (state == null && saved?.puzzleDay == today && saved.moveHistory.isNotEmpty())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // A day's puzzle left unfinished is gone once its day is over
    private fun puzzleGone(day: Long?) = day != null && day != TodaysPuzzle.today()

    val continueInfo: StateFlow<ContinueInfo?> = combine(
        _gameState,
        _savedGame,
        _currentLevelIndex,
        _puzzleDay,
    ) { state, saved, levelIdx, day ->
        if (state != null && !state.isWon && (state.remainingTilesCount > 0) && !puzzleGone(day)) {
            ContinueInfo(title = if (day != null) TODAYS_PUZZLE else continueTitle(levelIdx, state.constellation.name))
        } else if (saved != null && saved.tiles.any { !it.isRemoved } && !puzzleGone(saved.puzzleDay)) {
            ConstellationRepository.getById(saved.constellationId)?.let { constellation ->
                ContinueInfo(title = if (saved.puzzleDay != null) TODAYS_PUZZLE else continueTitle(savedLevelIndex(saved), constellation.name))
            }
        } else {
            null
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // The phone's Remove animations (Settings > Accessibility) sets its animator scale to 0; the
    // game's own motion (match bursts, fireworks, the hint pulse, zoom glides) follows it, and
    // changes with it while the game is open
    private val resolver = application.contentResolver
    private fun readPhoneAnimates() =
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    private var phoneAnimates = readPhoneAnimates()
    private val animationScaleObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            phoneAnimates = readPhoneAnimates()
            showSettings(_settings.value)
        }
    }.also {
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, it)
    }

    // "Level 9: Heart", or just "Tutorial"
    private fun continueTitle(level: Int?, name: String): String =
        listOfNotNull(levelLabel(level), name).distinct().joinToString(": ")

    init {
        // Levels were reordered by measured difficulty: carry the highest level over, once
        viewModelScope.launch { prefsRepo.migrateLevelOrder() }
        // Hints became earned: players from before start with 2
        viewModelScope.launch { prefsRepo.migrateHints() }
        // Collect settings. While the player's own changes are still being written, what's read
        // back is older than what they see, so it waits until the writes are done
        viewModelScope.launch {
            prefsRepo.settingsFlow.distinctUntilChanged().collect { s ->
                if (settingsWrites == 0) {
                    savedSettings = s
                    showSettings(s)
                }
            }
        }
        // The fireplace is heard at the table, and softly on the menu screens
        viewModelScope.launch {
            currentScreen.collect { screen ->
                audioManager.setFireRoom(
                    when (screen) {
                        is Screen.Game -> FireRoom.TABLE
                        is Screen.Settings, is Screen.LevelSelect, is Screen.ConstellationSelect, is Screen.TodaysPuzzle -> FireRoom.MENU
                        else -> FireRoom.NONE
                    },
                )
            }
        }
        // Collect progress
        viewModelScope.launch {
            prefsRepo.progressFlow.collect { p ->
                _progress.value = p
            }
        }
        // Collect saved game
        viewModelScope.launch {
            prefsRepo.savedGameFlow.collect { saved ->
                _savedGame.value = saved
            }
        }
    }

    // Where the game on the table was started from, which Back returns to: Levels, Layouts, or the
    // title (Continue)
    private val _gameOrigin = MutableStateFlow<Screen>(Screen.MainMenu)
    val gameOrigin: StateFlow<Screen> = _gameOrigin.asStateFlow()

    fun navigateTo(screen: Screen) {
        perfAutoplay?.cancel()
        if (screen != Screen.Game) {
            saveCurrentGame()
        }
        _currentScreen.value = screen
    }

    // A level game saved before the levels were reordered keeps its layout; its number follows it
    private fun savedLevelIndex(saved: SavedGameState): Int? =
        saved.levelIndex?.let { ConstellationRepository.levelIndexOf(saved.constellationId) ?: it }

    fun continueGame() {
        perfAutoplay?.cancel()
        val current = _gameState.value
        if (current != null && !current.isWon && current.remainingTilesCount > 0 && !puzzleGone(_puzzleDay.value)) {
            _gameOrigin.value = Screen.MainMenu
            _currentScreen.value = Screen.Game
            return
        }
        val saved = _savedGame.value
        if (saved != null && !puzzleGone(saved.puzzleDay)) {
            val restored = saved.toGameState()
            if (!restored.isWon && restored.remainingTilesCount > 0) {
                _currentLevelIndex.value = savedLevelIndex(saved)
                _puzzleDay.value = saved.puzzleDay
                _gameState.value = restored
                _tutorialStepIndex.value = TutorialScript.resumeStep(restored, saved.tutorialStep)
                _gameOrigin.value = Screen.MainMenu
                _currentScreen.value = Screen.Game
            }
        }
    }

    fun startLevel(levelIndex: Int) {
        _gameOrigin.value = Screen.LevelSelect
        beginLevel(levelIndex)
    }

    // Also the next level from the win card, which keeps where the first was started from
    private fun beginLevel(levelIndex: Int) {
        _currentLevelIndex.value = levelIndex
        val constellation = ConstellationRepository.getLevel(levelIndex)
        initGame(constellation)
        _currentScreen.value = Screen.Game
    }

    /**
     * Today's Puzzle: carried on if it's on the table or saved unfinished, otherwise dealt (again,
     * once solved: the same deal, ticked only once).
     */
    fun startTodaysPuzzle() {
        perfAutoplay?.cancel()
        val today = TodaysPuzzle.today()
        _gameOrigin.value = Screen.TodaysPuzzle
        val current = _gameState.value
        val saved = _savedGame.value
        when {
            current != null && _puzzleDay.value == today && !current.isWon && current.remainingTilesCount > 0 -> Unit
            saved != null && saved.puzzleDay == today && saved.tiles.any { !it.isRemoved } -> {
                _gameState.value = saved.toGameState()
                _winMessage.value = null
        _firstRibbon.value = false
                _tutorialStepIndex.value = null
            }
            else -> {
                _gameState.value = TodaysPuzzle.dealOn(today)
                _winMessage.value = null
        _firstRibbon.value = false
                _tutorialStepIndex.value = null
            }
        }
        _currentLevelIndex.value = null
        _puzzleDay.value = today
        saveCurrentGame()
        _currentScreen.value = Screen.Game
    }

    // The last Quick Game's layout, so the next one is different
    private var lastQuickGame: String? = null

    // Playing a Quick Game: its win card offers another, of the same difficulty
    private val _quickGame = MutableStateFlow(false)
    val quickGame: StateFlow<Boolean> = _quickGame.asStateFlow()

    /** Quick Game: a random layout of [difficulty] (any, if null), never the one just played. */
    fun startQuickGame(difficulty: Difficulty?) {
        val pool = ConstellationRepository.constellations
            .filter { it.id != ConstellationRepository.tutorialConstellation.id }
            .filter { difficulty == null || it.difficulty == difficulty }
        val choice = (pool.filter { it.id != lastQuickGame }.ifEmpty { pool }).randomOrNull() ?: return
        lastQuickGame = choice.id
        _gameOrigin.value = Screen.MainMenu
        _currentLevelIndex.value = null
        initGame(choice)
        _quickGame.value = true
        _currentScreen.value = Screen.Game
    }

    /** From a Quick Game's win card: another random layout as hard as the one just cleared. */
    fun anotherQuickGame() {
        startQuickGame(_gameState.value?.constellation?.difficulty)
    }

    fun startConstellation(constellation: Constellation) {
        _gameOrigin.value = Screen.ConstellationSelect
        _currentLevelIndex.value = null
        initGame(constellation)
        _currentScreen.value = Screen.Game
    }

    /**
     * Test builds only (BuildConfig.PERF_HOOKS): the same deal of [layoutName] every time, with the
     * fireplace lit or not on [surface], so frame timings before and after a change compare alike.
     */
    fun startPerfScene(layoutName: String, fireplace: Boolean, surface: TableSurface?, autoplay: Boolean = false) {
        val constellation = ConstellationRepository.constellations.firstOrNull { it.name.equals(layoutName, ignoreCase = true) } ?: return
        // From the saved settings, which may not have loaded yet (a scene opens with the app), so
        // that only the fire and the table change
        viewModelScope.launch {
            val saved = prefsRepo.settingsFlow.first()
            prefsRepo.updateSettings(saved, saved.copy(fireplaceLit = fireplace, tableSurface = surface ?: saved.tableSurface))
        }
        _currentLevelIndex.value = null
        _gameOrigin.value = Screen.MainMenu
        initGame(constellation, Random(7))
        _currentScreen.value = Screen.Game
        // Matches a free pair every two seconds, so match animations can be timed alike
        if (autoplay) perfAutoplay = viewModelScope.launch {
            delay(3000.milliseconds)
            while (true) {
                val state = _gameState.value ?: break
                val (a, b) = MahjongGameEngine.findAvailablePairs(state.tiles).firstOrNull() ?: break
                onTileTapped(a)
                delay(250.milliseconds)
                onTileTapped(b)
                delay(1750.milliseconds)
            }
        }
    }

    private var perfAutoplay: Job? = null

    private fun initGame(constellation: Constellation, random: Random = Random) {
        perfAutoplay?.cancel()
        val state = MahjongGameEngine.startNewGame(constellation, random)
        _puzzleDay.value = null
        _quickGame.value = false
        _winMessage.value = null
        _firstRibbon.value = false
        _gameState.value = state
        _tutorialStepIndex.value = if (TutorialScript.isTutorial(state)) 0 else null
        saveCurrentGame()
    }

    private fun saveCurrentGame() {
        val current = _gameState.value ?: return
        if (current.isWon || current.remainingTilesCount == 0) return
        val step = if (TutorialScript.isTutorial(current)) _tutorialStepIndex.value ?: SavedGameState.FREE_PLAY else null
        val saved = SavedGameState.fromGameState(current, _currentLevelIndex.value, step, _puzzleDay.value)
        _savedGame.value = saved
        viewModelScope.launch {
            prefsRepo.saveGame(saved)
        }
    }

    private fun clearSavedGame() {
        _savedGame.value = null
        viewModelScope.launch {
            prefsRepo.clearSavedGame()
        }
    }

    // Finishing a board whose tiles are all free: the pairs are taken one by one, as taps would
    private val _finishing = MutableStateFlow(false)
    val finishing: StateFlow<Boolean> = _finishing.asStateFlow()
    private var finishJob: Job? = null

    fun finish() {
        val start = _gameState.value ?: return
        if (!start.canFinish || currentTutorialStep != null || finishJob?.isActive == true) return
        _finishing.value = true
        val plan = start.finishPlan ?: return
        finishJob = viewModelScope.launch {
            try {
                for (pair in plan) {
                    val state = _gameState.value ?: break
                    if (state.isWon || _currentScreen.value != Screen.Game) break
                    // A tile the player had picked is put down first
                    if (state.selectedTileId != null && state.selectedTileId != pair.first) tap(state.selectedTileId)
                    tap(pair.first)
                    delay(FINISH_PICK)
                    tap(pair.second)
                    delay(FINISH_STEP)
                }
            } finally {
                _finishing.value = false
            }
        }
    }

    fun onTileTapped(tileId: Int) {
        // The player's taps wait while the board finishes itself
        if (_finishing.value) return
        tap(tileId)
    }

    private fun tap(tileId: Int) {
        val current = _gameState.value ?: return
        if (current.isWon) return

        val step = currentTutorialStep
        if (step != null && !TutorialScript.isTapAllowed(step, tileId)) return

        val (newState, result) = MahjongGameEngine.handleTileTap(current, tileId)
        _gameState.value = newState

        if (step != null && TutorialScript.isStepComplete(step, result, newState)) {
            advanceTutorial()
        }

        when (result) {
            is TileTapResult.Blocked -> {
                audioManager.playTileBlocked()
                // One clear at a time, so an earlier tap's can't cut this flash short
                blockedClear?.cancel()
                blockedClear = viewModelScope.launch {
                    delay(350.milliseconds)
                    if (_gameState.value?.blockedTileId == result.tileId) {
                        _gameState.value = _gameState.value?.copy(blockedTileId = null)
                    }
                }
            }
            is TileTapResult.Selected -> {
                audioManager.playTileSelect()
            }
            is TileTapResult.Deselected -> {
                audioManager.playTileDeselect()
            }
            is TileTapResult.SelectionChanged -> {
                audioManager.playTileSelect()
            }
            is TileTapResult.Matched -> {
                audioManager.playTileSelect()
                // Without the smash there's no moment of contact to play it at
                if (!_settings.value.matchSmash) {
                    audioManager.playTileMatch()
                }
                if (result.isWon) {
                    clearSavedGame()
                    val levelIndex = _currentLevelIndex.value
                    val day = _puzzleDay.value
                    viewModelScope.launch {
                        if (day != null) prefsRepo.recordPuzzleSolved(day)
                        _firstRibbon.value = prefsRepo.recordGameWon(
                            constellationId = current.constellation.id,
                            levelIndex = levelIndex,
                            clean = !newState.usedHelp && !TutorialScript.isTutorial(current),
                            puzzleDay = day,
                        )
                        _winMessage.value = prefsRepo.nextCongratsMessage(
                            isTutorial = TutorialScript.isTutorial(current),
                        )
                    }
                } else {
                    saveCurrentGame()
                }
            }
        }
    }

    private var blockedClear: Job? = null

    fun playTileMatch() {
        audioManager.playTileMatch()
    }

    /** The celebration has appeared over a cleared board (not one fading out behind the menu). */
    fun playWin() {
        if (_currentScreen.value == Screen.Game) audioManager.playWin()
    }

    /** The board was zoomed in or back out: the tutorial's zoom steps wait on this. */
    fun onBoardZoom(zoomedIn: Boolean) {
        val step = currentTutorialStep ?: return
        if (TutorialScript.isZoomStepComplete(step, zoomedIn)) advanceTutorial()
    }

    /**
     * The button on the tutorial card showing [step]: it moves on only from that step, so a second
     * tap on a card already fading out can't skip the one after.
     */
    fun continueTutorial(step: TutorialStep) {
        if (step == currentTutorialStep) advanceTutorial()
    }

    private fun advanceTutorial() {
        val index = _tutorialStepIndex.value ?: return
        val state = _gameState.value ?: return
        _tutorialStepIndex.value = TutorialScript.nextStep(index, state)
        // Saved with the step, so Continue picks up where the player was
        saveCurrentGame()
    }

    fun undo() {
        // Undo and hints would get in the way of the guided tutorial steps
        if (_tutorialStepIndex.value != null) return
        val current = _gameState.value ?: return
        // A cleared board stays cleared: Replay or Play again deal afresh
        if (current.isWon) return
        if (current.moveHistory.isNotEmpty()) {
            val updated = MahjongGameEngine.undo(current)
            _gameState.value = updated
            saveCurrentGame()
            audioManager.playTileSelect()
        }
    }

    private var hintSearch: Job? = null

    fun requestHint() {
        if (_tutorialStepIndex.value != null) return
        val current = _gameState.value ?: return
        if (current.isWon) return
        // The pair already shown is shown again, free; a new one costs a hint (the screen
        // doesn't ask with none left)
        if (current.hintPair != null) return
        if (_progress.value.hints <= 0) return
        audioManager.playTileSelect()
        // Finding a plan can take a moment on a big board, so it's done in the background, and
        // shown only if the board hasn't changed meanwhile
        hintSearch?.cancel()
        hintSearch = viewModelScope.launch {
            val hint = withContext(Dispatchers.Default) { MahjongGameEngine.findHint(current) } ?: return@launch
            if (_gameState.value?.tiles !== current.tiles || !prefsRepo.spendHint()) return@launch
            _gameState.update { state ->
                if (state?.tiles === current.tiles) {
                    state.copy(hintPair = hint.pair, plan = hint.plan.ifEmpty { state.plan }, blockedTileId = null, usedHelp = true)
                } else {
                    state
                }
            }
        }
    }

    fun restartGame() {
        val current = _gameState.value ?: return
        // Today's Puzzle is one deal: starting again is that deal again
        if (_puzzleDay.value != null) replayDeal() else initGame(current.constellation)
    }

    /** Restart with the same deal: the tiles back as they were first laid out. */
    fun replayDeal() {
        val current = _gameState.value ?: return
        hintSearch?.cancel()
        perfAutoplay?.cancel()
        _winMessage.value = null
        _firstRibbon.value = false
        val state = MahjongGameEngine.replay(current)
        _gameState.value = state
        _tutorialStepIndex.value = if (TutorialScript.isTutorial(state)) 0 else null
        saveCurrentGame()
    }

    fun reshuffle() {
        val current = _gameState.value ?: return
        val updated = MahjongGameEngine.reshuffleRemaining(current).copy(usedHelp = true)
        _gameState.value = updated
        saveCurrentGame()
        audioManager.playTileSelect()
    }

    fun playNextLevel() {
        val currentLevel = _currentLevelIndex.value ?: return
        if (currentLevel < ConstellationRepository.totalLevels) {
            beginLevel(currentLevel + 1)
        }
    }

    // The settings as last written (or read), which each change is told apart from; and how many
    // writes are on their way
    private var savedSettings = GameSettings()
    private var settingsWrites = 0

    private fun showSettings(s: GameSettings) {
        // Low-performance mode stills everything the phone's Remove animations does
        val shown = s.copy(animationsEnabled = phoneAnimates && !s.lowPerformance, phoneAnimates = phoneAnimates)
        _settings.value = shown
        audioManager.updateSettings(shown)
    }

    /** A setting changed: shown and heard at once, and only what changed is saved. */
    fun updateSettings(newSettings: GameSettings) {
        showSettings(newSettings)
        val from = savedSettings
        savedSettings = newSettings
        settingsWrites++
        viewModelScope.launch {
            try {
                prefsRepo.updateSettings(from, newSettings)
            } finally {
                settingsWrites--
            }
        }
    }

    /** A slider being dragged: shown and heard as it moves, saved when it's let go. */
    fun previewSettings(newSettings: GameSettings) {
        showSettings(newSettings)
    }

    /** The Fire sound slider is being dragged, so the fire is heard at table loudness. */
    fun auditionFire(on: Boolean) {
        audioManager.auditionFire(on)
    }

    /** The slider is let go: what it shows is saved. */
    fun commitSettings() {
        updateSettings(_settings.value)
    }

    fun onResume() {
        audioManager.onResume()
    }

    fun onPause() {
        saveCurrentGame()
        audioManager.onPause()
    }

    override fun onCleared() {
        super.onCleared()
        resolver.unregisterContentObserver(animationScaleObserver)
        audioManager.release()
    }
}

/** What Today's Puzzle is called wherever it's named. */
const val TODAYS_PUZZLE = "Today's Puzzle"

// Auto-finish: the pause between a pair's two picks, and between pairs (long enough for each
// match's animation to be seen)
private val FINISH_PICK = 140.milliseconds
private val FINISH_STEP = 420.milliseconds
