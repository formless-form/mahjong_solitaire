package com.vortessence.mahjong.ui.screens

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.padding
import com.vortessence.mahjong.ui.components.BigButton
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.Icons
import androidx.compose.animation.scaleIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.Box
import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.dp
import com.vortessence.mahjong.data.model.GameSettings
import com.vortessence.mahjong.data.model.GameState
import com.vortessence.mahjong.data.model.WideBoards
import com.vortessence.mahjong.data.repository.ConstellationRepository
import com.vortessence.mahjong.engine.TutorialAction
import com.vortessence.mahjong.engine.TutorialStep
import com.vortessence.mahjong.ui.viewmodel.TODAYS_PUZZLE
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import com.vortessence.mahjong.ui.components.GameHeader
import com.vortessence.mahjong.ui.components.GameRail
import com.vortessence.mahjong.ui.components.HoldOrientation
import com.vortessence.mahjong.ui.components.RotateDialog
import com.vortessence.mahjong.ui.components.isWideBoard
import com.vortessence.mahjong.ui.components.GameToolbar
import com.vortessence.mahjong.ui.components.LoadingPage
import com.vortessence.mahjong.data.repository.levelLabel
import com.vortessence.mahjong.ui.components.WinCelebration
import com.vortessence.mahjong.ui.components.MahjongBoard
import com.vortessence.mahjong.ui.components.RestartDialog
import com.vortessence.mahjong.ui.components.StalemateDialog
import com.vortessence.mahjong.ui.components.TutorialCard

@Composable
fun GameScreen(
    gameState: GameState?,
    levelIndex: Int?,
    settings: GameSettings = GameSettings(),
    // Today's Puzzle: named so, one deal (Restart replays it), and the win leads to its calendar
    todaysPuzzle: Boolean = false,
    tutorialStep: TutorialStep? = null,
    winMessage: String? = null,
    firstRibbon: Boolean = false,
    // Hints left to spend; null where they're free (the tutorial)
    hints: Int? = null,
    // A Quick Game: the win card offers another of the same difficulty
    quickGame: Boolean = false,
    onAnotherQuickGame: () -> Unit = {},
    totalLevels: Int = ConstellationRepository.totalLevels,
    onBack: () -> Unit,
    onTileClick: (Int) -> Unit,
    onTileMatch: () -> Unit = {},
    onHint: () -> Unit,
    onUndo: () -> Unit,
    onRestart: () -> Unit,
    onReshuffle: () -> Unit,
    onReplay: () -> Unit = onRestart,
    onNextLevel: () -> Unit,
    onMainMenu: () -> Unit,
    onTutorialContinue: (TutorialStep) -> Unit = {},
    onSettingsChanged: (GameSettings) -> Unit = {},
    onBoardZoom: (Boolean) -> Unit = {},
    onWinShown: () -> Unit = {},
    // Every tile left is free: offered as a button, the board then clears itself
    finishing: Boolean = false,
    onFinish: () -> Unit = {},
) {
    if (gameState == null) {
        LoadingPage(Modifier.background(MaterialTheme.colorScheme.background))
        return
    }

    val hasNextLevel = (levelIndex != null) && (levelIndex < totalLevels)
    val place = if (todaysPuzzle) TODAYS_PUZZLE else levelLabel(levelIndex)

    val tutorialHighlights = when {
        tutorialStep == null -> emptySet()
        tutorialStep.highlightFreeTiles -> gameState.tiles.filter { it.isFree }.map { it.id }.toSet()
        else -> tutorialStep.targetTileIds
    }

    // The tutorial's zoom steps: each new step is checked against the zoom as it stands, so one
    // already done (zoomed in before the pinch step) passes at once. The pinch step lets the
    // zoom settle a moment first, so its card doesn't change in the middle of the pinch
    var boardZoomedIn by remember { mutableStateOf(false) }
    LaunchedEffect(tutorialStep, boardZoomedIn) {
        if (tutorialStep?.action == TutorialAction.PINCH_ZOOM && boardZoomedIn) delay(700.milliseconds)
        onBoardZoom(boardZoomedIn)
    }

    // The final pair smashes before the celebration and its fanfare begin. Saved, so a recreated
    // screen doesn't play the fanfare again; leaving the board first means it never plays
    var celebrating by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(gameState.isWon) {
        if (!gameState.isWon) {
            celebrating = false
        } else if (!celebrating) {
            // Without the smash there's nothing to wait for
            if (settings.matchSmash) delay(WIN_REVEAL)
            celebrating = true
            onWinShown()
        }
    }

    // Restart asks first (the tutorial always deals the same tiles, so it just starts again);
    // the "no moves" dialog can be closed to look over the board, and Hint brings it back
    var askRestart by remember { mutableStateOf(false) }
    val restart = {
        if (ConstellationRepository.TUTORIAL_ID == gameState.constellation.id || todaysPuzzle) onRestart() else askRestart = true
    }
    var stalemateClosed by remember(gameState.tiles) { mutableStateOf(false) }
    // Out of hints, a tap says how to earn one
    var noHintsNote by remember { mutableStateOf(0) }
    val hint: () -> Unit = {
        when {
            gameState.isStalemate -> stalemateClosed = false
            hints == 0 -> noHintsNote += 1
            else -> onHint()
        }
    }

    // A wide board is held on its side if the player chose so; the first one asks. On its side the
    // header and toolbar become a rail beside the board (the tutorial, never wide, keeps its card)
    val wide = remember(gameState.constellation.id) { isWideBoard(gameState.tiles) }
    HoldOrientation(landscape = wide && settings.wideBoards == WideBoards.LANDSCAPE)
    var askedSideways by rememberSaveable(gameState.constellation.id) { mutableStateOf(false) }
    val sideways = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE && tutorialStep == null

    // When the board is cleared it softly blurs behind the celebration, as Android 17 blurs
    // what sits behind its own panels
    val blur by animateDpAsState(
        targetValue = if (celebrating) 14.dp else 0.dp,
        animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
        label = "BoardBlur",
    )

    if (sideways) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .then(if (blur > 0.dp) Modifier.blur(blur) else Modifier)
        ) {
            GameRail(
                gameState = gameState,
                label = place,
                onBackClick = onBack,
                onHintClick = hint,
                onUndoClick = onUndo,
                onRestartClick = restart,
                hintAndUndoEnabled = !gameState.isWon,
                hints = hints,
            )
            Box(Modifier.weight(1f).fillMaxHeight()) {
                MahjongBoard(
                    gameState = gameState,
                    settings = settings,
                    highlightedTileIds = tutorialHighlights,
                    onZoomedInChange = { boardZoomedIn = it },
                    onTileClick = onTileClick,
                    onTileMatch = onTileMatch,
                    modifier = Modifier.fillMaxSize(),
                )
                FinishButton(
                    visible = gameState.canFinish && !finishing && tutorialStep == null,
                    onClick = onFinish,
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 20.dp),
                )
                NoHintsNote(noHintsNote, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 20.dp))
            }
        }
    } else Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .then(if (blur > 0.dp) Modifier.blur(blur) else Modifier)
    ) {
        GameHeader(
            gameState = gameState,
            label = place,
            onBackClick = onBack,
        )

        // The table runs on to the bottom of the screen, under the toolbar (or tutorial card) and
        // the navigation bar; the tiles are fitted above them
        Box(modifier = Modifier.weight(1f)) {
            val density = LocalDensity.current
            var overlay by remember { mutableIntStateOf(0) }
            // The board refits to the card or toolbar once it has settled, not on every frame of
            // the card growing or shrinking (each refit rebuilds the shadow map); the first
            // measured size is taken at once
            var boardOverlay by remember { mutableIntStateOf(0) }
            LaunchedEffect(Unit) {
                snapshotFlow { overlay }.collectLatest { h ->
                    if (boardOverlay > 0) delay(OVERLAY_SETTLE)
                    boardOverlay = h
                }
            }
            MahjongBoard(
                gameState = gameState,
                settings = settings,
                highlightedTileIds = tutorialHighlights,
                highlightZoomOut = tutorialStep?.highlightZoomOut == true,
                onZoomedInChange = { boardZoomedIn = it },
                onTileClick = onTileClick,
                onTileMatch = onTileMatch,
                bottomOverlap = with(density) { boardOverlay.toDp() },
                modifier = Modifier.fillMaxSize()
            )
            // Just above the toolbar, without taking room from the board
            FinishButton(
                visible = gameState.canFinish && !finishing && tutorialStep == null,
                onClick = onFinish,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = with(density) { overlay.toDp() } + 14.dp),
            )
            NoHintsNote(
                noHintsNote,
                Modifier.align(Alignment.BottomCenter).padding(bottom = with(density) { overlay.toDp() } + 14.dp),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { overlay = it.height }
                    .navigationBarsPadding()
            ) {
                if (tutorialStep != null) {
                    TutorialCard(
                        step = tutorialStep,
                        settings = settings,
                        onContinue = onTutorialContinue,
                        onSettingsChanged = onSettingsChanged
                    )
                } else {
                    GameToolbar(
                        gameState = gameState,
                        onHintClick = hint,
                        onUndoClick = onUndo,
                        onRestartClick = restart,
                        // Nothing to hint or undo once the board is cleared
                        hintAndUndoEnabled = !gameState.isWon,
                        hints = hints,
                    )
                }
            }
        }
    }

    // Fireworks and a warm message when the board is cleared
    if (gameState.isWon && celebrating) {
        WinCelebration(
            message = winMessage,
            subtitle = listOfNotNull(place, gameState.constellation.name).distinct().joinToString(" · "),
            hasNextLevel = hasNextLevel,
            // Low-performance mode keeps a lighter show; the phone's Remove animations, none
            animationsEnabled = settings.matchSmash,
            liteFireworks = !settings.animationsEnabled,
            firstRibbon = firstRibbon,
            onNextLevel = onNextLevel,
            onPlayAgain = if (quickGame) onAnotherQuickGame else onRestart,
            quickGame = quickGame,
            onMainMenu = onMainMenu,
            menuLabel = if (todaysPuzzle) "Calendar" else "Menu",
        )
    }

    // Stalemate / no more moves dialog
    if (gameState.isStalemate && !gameState.isWon && !stalemateClosed) {
        StalemateDialog(
            canUndo = gameState.moveHistory.isNotEmpty(),
            onReshuffle = onReshuffle,
            onUndo = onUndo,
            onRestart = restart,
            onDismiss = { stalemateClosed = true },
        )
    }

    if (wide && settings.wideBoards == WideBoards.ASK && !askedSideways && !gameState.isWon && tutorialStep == null) {
        RotateDialog(
            animate = settings.animationsEnabled,
            onLandscape = {
                askedSideways = true
                onSettingsChanged(settings.copy(wideBoards = WideBoards.LANDSCAPE))
            },
            onPortrait = {
                askedSideways = true
                onSettingsChanged(settings.copy(wideBoards = WideBoards.PORTRAIT))
            },
            // Closed without an answer: asked again with the next wide board
            onDismiss = { askedSideways = true },
        )
    }

    if (askRestart) {
        RestartDialog(
            onSameDeal = { askRestart = false; onReplay() },
            onNewDeal = { askRestart = false; onRestart() },
            onDismiss = { askRestart = false },
        )
    }
}

// How long the final pair has to smash before the celebration
private val WIN_REVEAL = 600.milliseconds

// How long the tutorial card's size has to hold before the board refits to it
private val OVERLAY_SETTLE = 120.milliseconds

// Offered at a board's end (a few pairs left on the lowest layer): the rest clears itself
@Composable
private fun FinishButton(visible: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(initialScale = 0.9f),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        BigButton(text = "Finish", onClick = onClick, primary = true, icon = Icons.Rounded.DoneAll)
    }
}

// Tapping Hint with none left: a short note, for a few seconds, of how to earn one. [tapped]
// counts the taps, so each shows it afresh
@Composable
private fun NoHintsNote(tapped: Int, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(tapped) {
        if (tapped == 0) return@LaunchedEffect
        visible = true
        delay(NO_HINTS_NOTE)
        visible = false
    }
    AnimatedVisibility(visible = visible, enter = fadeIn() + scaleIn(initialScale = 0.95f), exit = fadeOut(), modifier = modifier) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.padding(horizontal = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
                Icon(Icons.Rounded.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Clear a level cleanly to earn a hint", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

private val NO_HINTS_NOTE = 2800.milliseconds
