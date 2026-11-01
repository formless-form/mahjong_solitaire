package com.vortessence.mahjong

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vortessence.mahjong.engine.TodaysPuzzle
import com.vortessence.mahjong.ui.components.PredictiveBackHandling
import com.vortessence.mahjong.ui.components.PredictiveBackState
import com.vortessence.mahjong.ui.components.predictiveBack
import com.vortessence.mahjong.ui.screens.ConstellationSelectScreen
import com.vortessence.mahjong.ui.screens.GameScreen
import com.vortessence.mahjong.ui.screens.LevelSelectScreen
import com.vortessence.mahjong.ui.screens.MainMenuScreen
import com.vortessence.mahjong.ui.screens.SettingsScreen
import com.vortessence.mahjong.ui.screens.TodaysPuzzleScreen
import com.vortessence.mahjong.ui.theme.MahjongTheme
import com.vortessence.mahjong.ui.viewmodel.MahjongViewModel
import com.vortessence.mahjong.ui.viewmodel.Screen
import com.vortessence.mahjong.update.AppUpdates
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun MahjongApp(viewModel: MahjongViewModel, updates: AppUpdates) {
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val gameState by viewModel.gameState.collectAsStateWithLifecycle()
    val finishing by viewModel.finishing.collectAsStateWithLifecycle()
    val levelIndex by viewModel.currentLevelIndex.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val continueInfo by viewModel.continueInfo.collectAsStateWithLifecycle()
    val tutorialStep by viewModel.tutorialStep.collectAsStateWithLifecycle()
    val winMessage by viewModel.winMessage.collectAsStateWithLifecycle()
    val firstRibbon by viewModel.firstRibbon.collectAsStateWithLifecycle()
    val quickGame by viewModel.quickGame.collectAsStateWithLifecycle()
    val update by updates.notice.collectAsStateWithLifecycle()
    val gameOrigin by viewModel.gameOrigin.collectAsStateWithLifecycle()
    val puzzleDay by viewModel.puzzleDay.collectAsStateWithLifecycle()
    val puzzleStarted by viewModel.todaysPuzzleStarted.collectAsStateWithLifecycle()

    val back = remember { PredictiveBackState() }
    val toMenu = { viewModel.navigateTo(Screen.MainMenu) }
    // Back steps out of a game to where it was started from (Levels, Layouts, or the title), and
    // from every other screen to the title, with the predictive back animation
    val backTarget = if (currentScreen is Screen.Game) gameOrigin else Screen.MainMenu
    val stepBack = {
        viewModel.navigateTo(if (viewModel.currentScreen.value is Screen.Game) viewModel.gameOrigin.value else Screen.MainMenu)
    }
    PredictiveBackHandling(enabled = currentScreen !is Screen.MainMenu, state = back, onBack = stepBack)
    LaunchedEffect(currentScreen) { back.reset() }

    val mainMenu = @Composable {
        MainMenuScreen(
            continueInfo = continueInfo,
            onContinue = { viewModel.continueGame() },
            todaysPuzzle = remember(currentScreen) { todaysPuzzleLine() },
            todaysPuzzleSolved = TodaysPuzzle.today() in progress.puzzleDaysSolved,
            onTodaysPuzzle = { viewModel.navigateTo(Screen.TodaysPuzzle) },
            onQuickGame = { viewModel.startQuickGame(it) },
            onPlayLevels = { viewModel.navigateTo(Screen.LevelSelect) },
            onChooseConstellation = { viewModel.navigateTo(Screen.ConstellationSelect) },
            onSettings = { viewModel.navigateTo(Screen.Settings) },
            onMenuClick = { viewModel.audioManager.playMenuClick() },
            update = update,
            onUpdate = {
                viewModel.audioManager.playMenuClick()
                updates.act()
            },
        )
    }

    // One screen's content, as shown and as revealed behind a back swipe
    val screenContent: @Composable (Screen) -> Unit = { screen ->
        // Every screen but the title (whose scenery runs full-bleed) keeps its content clear
        // of the camera cutout
        val contentModifier = if (screen is Screen.MainMenu) {
            Modifier.fillMaxSize()
        } else {
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .windowInsetsPadding(WindowInsets.displayCutout)
        }
        Box(modifier = contentModifier) {
            when (screen) {
                is Screen.MainMenu -> mainMenu()

                is Screen.LevelSelect -> {
                    LevelSelectScreen(
                        progress = progress,
                        levels = viewModel.currentLevelConstellations,
                        onLevelSelected = { level -> viewModel.startLevel(level) },
                        onBack = toMenu,
                        fireIntensity = settings.fireIntensity,
                        fire = settings.animationsEnabled,
                    )
                }

                is Screen.ConstellationSelect -> {
                    ConstellationSelectScreen(
                        progress = progress,
                        onConstellationSelected = { constellation -> viewModel.startConstellation(constellation) },
                        onBack = toMenu,
                        fireIntensity = settings.fireIntensity,
                        fire = settings.animationsEnabled,
                    )
                }

                is Screen.TodaysPuzzle -> {
                    TodaysPuzzleScreen(
                        progress = progress,
                        inProgress = puzzleStarted,
                        onPlay = { viewModel.startTodaysPuzzle() },
                        onBack = toMenu,
                        fireIntensity = settings.fireIntensity,
                        fire = settings.animationsEnabled,
                    )
                }

                is Screen.Game -> {
                    GameScreen(
                        gameState = gameState,
                        levelIndex = levelIndex,
                        settings = settings,
                        todaysPuzzle = puzzleDay != null,
                        tutorialStep = tutorialStep,
                        winMessage = winMessage,
                        firstRibbon = firstRibbon,
                        // Free in the tutorial; earned everywhere else
                        hints = if (tutorialStep != null) null else progress.hints,
                        quickGame = quickGame,
                        onAnotherQuickGame = { viewModel.anotherQuickGame() },
                        totalLevels = viewModel.currentLevelConstellations.size,
                        onBack = stepBack,
                        onTileClick = { tileId -> viewModel.onTileTapped(tileId) },
                        onTileMatch = { viewModel.playTileMatch() },
                        onHint = { viewModel.requestHint() },
                        onUndo = { viewModel.undo() },
                        onRestart = { viewModel.restartGame() },
                        onReshuffle = { viewModel.reshuffle() },
                        onReplay = { viewModel.replayDeal() },
                        onNextLevel = { viewModel.playNextLevel() },
                        // From Today's Puzzle, its Calendar button
                        onMainMenu = { viewModel.navigateTo(if (puzzleDay != null) Screen.TodaysPuzzle else Screen.MainMenu) },
                        onTutorialContinue = { step -> viewModel.continueTutorial(step) },
                        onBoardZoom = { viewModel.onBoardZoom(it) },
                        onWinShown = { viewModel.playWin() },
                        finishing = finishing,
                        onFinish = { viewModel.finish() },
                        onSettingsChanged = { updated -> viewModel.updateSettings(updated) },
                    )
                }

                is Screen.Settings -> {
                    SettingsScreen(
                        settings = settings,
                        onSettingsChanged = { updated -> viewModel.updateSettings(updated) },
                        onBack = toMenu,
                        onSettingsPreview = { updated -> viewModel.previewSettings(updated) },
                        onSettingsCommit = { viewModel.commitSettings() },
                        onFireAudition = { viewModel.auditionFire(it) },
                    )
                }
            }
        }
    }

    MahjongTheme {
        val motion = MaterialTheme.motionScheme
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            // The screen Back leads to, revealed behind a screen that's being swiped back
            if (back.revealing) {
                Box(Modifier.fillMaxSize()) { screenContent(backTarget) }
            }

            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    when {
                        // The back gesture has already shown this change
                        back.committed -> EnterTransition.None togetherWith ExitTransition.None
                        // Material's shared-axis depth motion: forward comes up from below, back recedes
                        targetState is Screen.MainMenu || initialState is Screen.Game ->
                            (fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.defaultSpatialSpec(), initialScale = 1.06f)) togetherWith
                                (fadeOut(motion.fastEffectsSpec()) + scaleOut(motion.defaultSpatialSpec(), targetScale = 0.94f))
                        else ->
                            (fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.defaultSpatialSpec(), initialScale = 0.94f)) togetherWith
                                (fadeOut(motion.fastEffectsSpec()) + scaleOut(motion.defaultSpatialSpec(), targetScale = 1.06f))
                    }
                },
                modifier = Modifier.predictiveBack(back),
                label = "ScreenTransition",
            ) { screen -> screenContent(screen) }
        }
    }
}

// Today's Puzzle's line on the title screen: "Saturday · Expert"
private fun todaysPuzzleLine(): String {
    val today = TodaysPuzzle.today()
    val weekday = LocalDate.ofEpochDay(today).dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
    return "$weekday · ${TodaysPuzzle.layoutOn(today).difficulty.displayName}"
}
