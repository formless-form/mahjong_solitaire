package com.vortessence.mahjong.screenshots

import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.performClick
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.vortessence.mahjong.data.model.ContinueInfo
import com.vortessence.mahjong.data.model.GameSettings
import com.vortessence.mahjong.data.model.GameState
import com.vortessence.mahjong.data.model.Progress
import com.vortessence.mahjong.data.model.TableSurface
import com.vortessence.mahjong.data.model.WideBoards
import com.vortessence.mahjong.data.repository.ConstellationRepository
import com.vortessence.mahjong.engine.MahjongGameEngine
import com.vortessence.mahjong.engine.TutorialScript
import com.vortessence.mahjong.ui.components.PredictiveBackState
import com.vortessence.mahjong.ui.components.RestartDialog
import com.vortessence.mahjong.ui.components.StalemateDialog
import com.vortessence.mahjong.ui.components.TableSurfaceRenderer
import com.vortessence.mahjong.ui.components.TileWear
import com.vortessence.mahjong.ui.components.fireworksSnapshot
import com.vortessence.mahjong.ui.components.predictiveBack
import com.vortessence.mahjong.ui.screens.ConstellationSelectScreen
import com.vortessence.mahjong.ui.screens.GameScreen
import com.vortessence.mahjong.ui.screens.LevelSelectScreen
import com.vortessence.mahjong.ui.screens.MainMenuScreen
import com.vortessence.mahjong.data.model.UpdateNotice
import com.vortessence.mahjong.ui.screens.SettingsScreen
import com.vortessence.mahjong.ui.screens.TodaysPuzzleScreen
import com.vortessence.mahjong.ui.screens.TitleMenuStyle
import com.vortessence.mahjong.ui.theme.MahjongTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Renders the app's screens on the JVM through Robolectric's real Android graphics stack (AGSL
 * shaders and render effects included), at the Pixel 9 Pro's portrait size and density, into
 * build/screenshots. Run: ./gradlew testFossDebugUnitTest --tests '*ScreenshotTour*'
 *
 * API 36 rather than 37: Espresso, used by the Compose test rule, still calls an InputManager
 * method that API 37 removed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w427dp-h952dp-port-360dpi")
class ScreenshotTour {
    @get:Rule
    val compose = createComposeRule()

    // The menu firelight's clock never lets the screen settle
    @org.junit.Before
    fun holdMenuFireStill() {
        com.vortessence.mahjong.ui.components.MenuFire.flickers = false
    }

    private fun shoot(name: String) = compose.onRoot().captureRoboImage("build/screenshots/$name.png")

    private fun tabletop(surface: TableSurface) {
        compose.setContent {
            MahjongTheme {
                val context = LocalContext.current
                Canvas(Modifier.fillMaxSize()) {
                    TableSurfaceRenderer.drawTableSurface(this, surface, size.width, size.height, context)
                }
            }
        }
        shoot("tabletop_${surface.name.lowercase()}")
    }

    @Test fun tabletopGreenMat() = tabletop(TableSurface.GREEN_MAT)
    @Test fun tabletopWalnut() = tabletop(TableSurface.WALNUT)
    @Test fun tabletopThaiStraw() = tabletop(TableSurface.THAI_STRAW)
    @Test fun tabletopSlate() = tabletop(TableSurface.SLATE)

    @Composable
    private fun TitleScreen(style: TitleMenuStyle = TitleMenuStyle.SANS) = MainMenuScreen(
        continueInfo = ContinueInfo("Level 3: Celestial Lantern"),
        menuStyle = style,
        onPlayLevels = {},
        onChooseConstellation = {},
        onSettings = {},
    )

    @Test
    fun title() {
        compose.setContent { MahjongTheme { TitleScreen() } }
        shoot("title")
    }

    // A newer version on Google Play (Play builds), at the foot: there, being fetched, fetched
    private fun titleUpdate(update: UpdateNotice, shot: String) {
        compose.setContent {
            MahjongTheme {
                MainMenuScreen(
                    continueInfo = ContinueInfo("Level 3: Celestial Lantern"),
                    onPlayLevels = {},
                    onChooseConstellation = {},
                    onSettings = {},
                    update = update,
                )
            }
        }
        shoot(shot)
    }

    @Test fun titleUpdateAvailable() = titleUpdate(UpdateNotice.Available, "title_update_available")
    @Test fun titleUpdateDownloading() = titleUpdate(UpdateNotice.Downloading(0.42f), "title_update_downloading")
    @Test fun titleUpdateReady() = titleUpdate(UpdateNotice.Ready, "title_update_ready")

    @Test
    fun titleSerifMenu() {
        compose.setContent { MahjongTheme { TitleScreen(TitleMenuStyle.SERIF) } }
        shoot("title_serif_menu")
    }

    private val someProgress = Progress(highestLevelReached = 4, completedConstellations = setOf("tutorial"))

    @Test
    fun levels() {
        compose.setContent {
            MahjongTheme {
                LevelSelectScreen(
                    progress = someProgress.copy(
                        highestLevelReached = 4,
                        completedConstellations = ConstellationRepository.levelConstellations.take(3).map { it.id }.toSet() + "tutorial",
                    ),
                    levels = ConstellationRepository.levelConstellations,
                    onLevelSelected = {},
                    onBack = {},
                )
            }
        }
        shoot("levels")
    }


    // Gold ribbons on the levels cleared cleanly
    @Test
    fun levelsWithRibbons() {
        compose.setContent {
            MahjongTheme {
                LevelSelectScreen(
                    progress = someProgress.copy(
                        highestLevelReached = 4,
                        completedConstellations = ConstellationRepository.levelConstellations.take(3).map { it.id }.toSet() + "tutorial",
                        // Levels 1 and 3 cleared cleanly; 2 with help
                        cleanConstellations = ConstellationRepository.levelConstellations.let { setOf(it[0].id, it[2].id) },
                    ),
                    levels = ConstellationRepository.levelConstellations,
                    onLevelSelected = {},
                    onBack = {},
                )
            }
        }
        shoot("levels_ribbons")
    }

    @Test
    fun layouts() {
        compose.setContent {
            MahjongTheme { ConstellationSelectScreen(progress = someProgress, onConstellationSelected = {}, onBack = {}) }
        }
        shoot("layouts")
    }

    // Boards ported from Mah: a tall one, and one of the widest kept (tiles about 30 dp)
    private fun mahGame(name: String, surface: TableSurface, shot: String) {
        TileWear.prepareNow()
        val state = MahjongGameEngine.startNewGame(ConstellationRepository.mahConstellations.first { it.name == name })
        compose.setContent {
            MahjongTheme {
                GameScreen(
                    gameState = state,
                    levelIndex = null,
                    settings = GameSettings(tableSurface = surface, fireplaceLit = false),
                    onBack = {}, onTileClick = {}, onHint = {}, onUndo = {}, onRestart = {},
                    onReshuffle = {}, onNextLevel = {}, onMainMenu = {},
                )
            }
        }
        shoot(shot)
    }

    // The end of a board: three pairs left on the lowest layer, and the Finish button offered
    @Test
    fun finishOffered() = boardEnd(ConstellationRepository.mahConstellations.first { it.name == "Cactus" }, "finish_offered")

    // Auto-zoom on the last tiles of Jade Pillar, a board with big tiles: held to a comfortable size
    @Test
    fun autoZoomCapped() = boardEnd(ConstellationRepository.getById("jade_pillar")!!, "auto_zoom_jade_pillar")

    private fun boardEnd(layout: com.vortessence.mahjong.data.model.Constellation, shot: String) {
        TileWear.prepareNow()
        val start = MahjongGameEngine.startNewGame(layout)
        val ground = start.tiles.minOf { it.position.z }
        val random = kotlin.random.Random(3)
        val state = generateSequence {
            val pairs = start.tiles.filter { it.position.z == ground }.groupBy { it.tile.suit to it.tile.value }
                .values.filter { it.size >= 2 }.shuffled(random).take(3).map { it.take(2) }.flatten().map { it.id }.toSet()
            val tiles = MahjongGameEngine.recalculateFreedom(start.tiles.map { it.copy(isRemoved = it.id !in pairs) })
            start.copy(tiles = tiles, availablePairsCount = MahjongGameEngine.findAvailablePairs(tiles).size)
        }.take(200).first { it.canFinish }
        compose.setContent {
            MahjongTheme {
                GameScreen(
                    // Unlit, so the screen settles for its picture (the fire flickers for ever)
                    settings = GameSettings(fireplaceLit = false),
                    gameState = state,
                    levelIndex = 12,
                    onBack = {}, onTileClick = {}, onHint = {}, onUndo = {}, onRestart = {},
                    onReshuffle = {}, onNextLevel = {}, onMainMenu = {},
                )
            }
        }
        compose.mainClock.advanceTimeBy(1000)
        shoot(shot)
    }

    @Test fun mahCactus() = mahGame("Cactus", TableSurface.GREEN_MAT, "mah_cactus")
    @Test fun mahFence() = mahGame("Fence", TableSurface.WALNUT, "mah_fence")

    @Test
    fun layoutsMah() {
        compose.setContent {
            MahjongTheme { ConstellationSelectScreen(progress = someProgress, onConstellationSelected = {}, onBack = {}) }
        }
        compose.onAllNodes(hasScrollAction()).onLast().performScrollToNode(hasText("Clover"))
        shoot("layouts_mah")
    }

    @Test
    fun tutorial() {
        TileWear.prepareNow()
        val tutorial = ConstellationRepository.tutorialConstellation
        compose.setContent {
            MahjongTheme {
                GameScreen(
                    // Unlit, so the screen settles for its picture (the fire flickers for ever)
                    settings = GameSettings(fireplaceLit = false),
                    gameState = MahjongGameEngine.startNewGame(tutorial),
                    levelIndex = ConstellationRepository.TUTORIAL_LEVEL,
                    tutorialStep = TutorialScript.steps.first(),
                    onBack = {}, onTileClick = {}, onHint = {}, onUndo = {}, onRestart = {},
                    onReshuffle = {}, onNextLevel = {}, onMainMenu = {},
                )
            }
        }
        shoot("tutorial")
    }

    // The tutorial's zoom steps: the pinch prompt, then pinched in, with the zoom-out button ringed
    private fun tutorialStep(title: String, pinched: Boolean, shot: String) {
        TileWear.prepareNow()
        val tutorial = ConstellationRepository.tutorialConstellation
        compose.setContent {
            MahjongTheme {
                GameScreen(
                    // Unlit, so the screen settles for its picture (the fire flickers for ever)
                    settings = GameSettings(fireplaceLit = false),
                    gameState = MahjongGameEngine.startNewGame(tutorial),
                    levelIndex = ConstellationRepository.TUTORIAL_LEVEL,
                    tutorialStep = TutorialScript.steps.single { it.title == title },
                    onBack = {}, onTileClick = {}, onHint = {}, onUndo = {}, onRestart = {},
                    onReshuffle = {}, onNextLevel = {}, onMainMenu = {},
                )
            }
        }
        if (pinched) {
            compose.waitForIdle()
            compose.onRoot().performTouchInput {
                val c = Offset(centerX, height * 0.35f)
                pinch(
                    start0 = c - Offset(60f, 0f), end0 = c - Offset(132f, 0f),
                    start1 = c + Offset(60f, 0f), end1 = c + Offset(132f, 0f),
                    durationMillis = 400,
                )
            }
        }
        compose.waitForIdle()
        shoot(shot)
    }

    @Test fun tutorialZoomIn() = tutorialStep("Zoom In", pinched = false, shot = "tutorial_zoom_in")
    @Test fun tutorialZoomOut() = tutorialStep("Zoom Out", pinched = true, shot = "tutorial_zoom_out")

    @Test
    fun noMovesDialog() {
        TileWear.prepareNow()
        compose.setContent {
            MahjongTheme {
                Box(Modifier.fillMaxSize().background(Color(0xFF0C1110)))
                StalemateDialog(canUndo = true, onReshuffle = {}, onUndo = {}, onRestart = {})
            }
        }
        compose.waitForIdle()
        captureScreenRoboImage("build/screenshots/no_moves.png")
    }

    @Test
    fun restartDialog() {
        compose.setContent {
            MahjongTheme {
                Box(Modifier.fillMaxSize().background(Color(0xFF0C1110)))
                RestartDialog(onSameDeal = {}, onNewDeal = {}, onDismiss = {})
            }
        }
        compose.waitForIdle()
        captureScreenRoboImage("build/screenshots/restart.png")
    }

    // Settings part-way through a back swipe from the left edge, the title screen behind it
    @Test
    fun predictiveBackMidGesture() {
        val back = PredictiveBackState().apply { progress = 0.7f }
        compose.setContent {
            MahjongTheme {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    TitleScreen()
                    Box(Modifier.fillMaxSize().predictiveBack(back).background(MaterialTheme.colorScheme.background)) {
                        SettingsScreen(settings = GameSettings(), onSettingsChanged = {}, onBack = {})
                    }
                }
            }
        }
        shoot("predictive_back")
    }

    // A wide layout (Nine Dragon Pagoda) on the walnut table, with the given wide-board setting
    private fun wideGame(wideBoards: WideBoards, animations: Boolean = true, won: Boolean = false, fireplace: Boolean = false) {
        TileWear.prepareNow()
        val dealt = MahjongGameEngine.startNewGame(ConstellationRepository.getById("pagoda")!!)
        val state = if (won) dealt.copy(tiles = dealt.tiles.map { it.copy(isRemoved = true) }, isWon = true) else dealt
        compose.setContent {
            MahjongTheme {
                GameScreen(
                    gameState = state,
                    levelIndex = null,
                    settings = GameSettings(
                        tableSurface = TableSurface.WALNUT, wideBoards = wideBoards, animationsEnabled = animations, fireplaceLit = fireplace,
                    ),
                    winMessage = "The mist lifts, and so do the tiles.",
                    onBack = {}, onTileClick = {}, onHint = {}, onUndo = {}, onRestart = {},
                    onReshuffle = {}, onNextLevel = {}, onMainMenu = {},
                )
            }
        }
    }

    // The first wide board asks; with Remove animations on, the phone is held on its side
    @Test
    fun rotateDialog() {
        wideGame(WideBoards.ASK, animations = false)
        compose.waitForIdle()
        captureScreenRoboImage("build/screenshots/rotate_dialog.png")
    }

    // Part-way through the phone's turn
    @Test
    fun rotateDialogTurning() {
        compose.mainClock.autoAdvance = false
        wideGame(WideBoards.ASK)
        compose.mainClock.advanceTimeBy(1150)
        captureScreenRoboImage("build/screenshots/rotate_dialog_turning.png")
    }

    @Test @Config(qualifiers = "w952dp-h427dp-land-360dpi")
    fun wideBoardLandscape() {
        wideGame(WideBoards.LANDSCAPE)
        compose.waitForIdle()
        shoot("wide_landscape")
    }

    // The hearth beyond the far edge of a table played on its side
    @Test @Config(qualifiers = "w952dp-h427dp-land-360dpi")
    fun wideBoardLandscapeFire() {
        compose.mainClock.autoAdvance = false
        wideGame(WideBoards.LANDSCAPE, fireplace = true)
        compose.mainClock.advanceTimeBy(800)
        shoot("wide_landscape_fire")
    }

    @Test
    fun wideBoardPortrait() {
        wideGame(WideBoards.PORTRAIT)
        compose.waitForIdle()
        shoot("wide_portrait")
    }

    @Test @Config(qualifiers = "w952dp-h427dp-land-360dpi")
    fun wideBoardLandscapeCelebration() {
        compose.mainClock.autoAdvance = false
        wideGame(WideBoards.LANDSCAPE, won = true)
        compose.mainClock.advanceTimeBy(2200)
        shoot("wide_landscape_celebration")
    }

    @Test @Config(qualifiers = "w952dp-h427dp-land-360dpi")
    fun noMovesDialogLandscape() {
        compose.setContent {
            MahjongTheme {
                Box(Modifier.fillMaxSize().background(Color(0xFF0C1110)))
                StalemateDialog(canUndo = true, onReshuffle = {}, onUndo = {}, onRestart = {})
            }
        }
        compose.waitForIdle()
        captureScreenRoboImage("build/screenshots/no_moves_landscape.png")
    }

    // Today's Puzzle on Saturday 31 October 2026, most of the month's puzzles solved
    @Test
    fun todaysPuzzle() = todaysPuzzleShot("todays_puzzle")

    // The same on an open foldable, a near-square screen
    @Test @Config(qualifiers = "w851dp-h882dp-port-360dpi")
    fun todaysPuzzleFoldable() = todaysPuzzleShot("todays_puzzle_foldable")

    // And on a tablet held upright
    @Test @Config(qualifiers = "w800dp-h1280dp-port-240dpi")
    fun todaysPuzzleTablet() = todaysPuzzleShot("todays_puzzle_tablet")

    private fun todaysPuzzleShot(name: String) {
        val today = java.time.LocalDate.of(2026, 10, 31).toEpochDay()
        val solved = (1L..30L).filter { it % 7 != 3L && it % 11 != 0L }.map { today - 31 + it }.toSet()
        compose.setContent {
            MahjongTheme {
                TodaysPuzzleScreen(
                    progress = Progress(puzzleDaysSolved = solved), inProgress = false,
                    onPlay = {}, onBack = {}, today = today,
                )
            }
        }
        shoot(name)
    }

    @Test
    fun titleWithTodaysPuzzle() {
        compose.setContent {
            MahjongTheme {
                MainMenuScreen(
                    continueInfo = ContinueInfo("Level 22: Heart"),
                    todaysPuzzle = "Saturday · Expert", todaysPuzzleSolved = true,
                    onPlayLevels = {}, onChooseConstellation = {}, onSettings = {},
                )
            }
        }
        shoot("title_todays_puzzle")
    }

    // Quick Game: its row on the title menu, and the difficulty picker it opens
    @Test
    fun quickGamePicker() {
        compose.setContent {
            MahjongTheme {
                MainMenuScreen(
                    continueInfo = ContinueInfo("Level 22: Heart"),
                    todaysPuzzle = "Saturday · Expert", todaysPuzzleSolved = true,
                    onPlayLevels = {}, onChooseConstellation = {}, onSettings = {},
                )
            }
        }
        shoot("title_quick_game")
        compose.onNodeWithText("Quick Game").performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.onNode(isDialog()).captureRoboImage("build/screenshots/quick_game_picker.png")
    }

    private fun level(index: Int): GameState =
        MahjongGameEngine.startNewGame(ConstellationRepository.getLevel(index))

    private fun game(surface: TableSurface, levelIndex: Int?, state: GameState = level(levelIndex!!), message: String? = null, firstRibbon: Boolean = false, hints: Int? = null, quickGame: Boolean = false) {
        TileWear.prepareNow()
        compose.setContent {
            MahjongTheme {
                GameScreen(
                    gameState = state,
                    levelIndex = levelIndex,
                    settings = GameSettings(tableSurface = surface, fireplaceLit = false),
                    winMessage = message,
                    firstRibbon = firstRibbon,
                    hints = hints,
                    quickGame = quickGame,
                    totalLevels = ConstellationRepository.totalLevels,
                    onBack = {}, onTileClick = {}, onHint = {}, onUndo = {}, onRestart = {},
                    onReshuffle = {}, onNextLevel = {}, onMainMenu = {},
                )
            }
        }
    }

    @Test fun gameGreenMat() { game(TableSurface.GREEN_MAT, 8); shoot("game_green_mat") }
    @Test fun gameWalnut() { game(TableSurface.WALNUT, 11); shoot("game_walnut") }
    @Test fun gameThaiStraw() { game(TableSurface.THAI_STRAW, 12); shoot("game_thai_straw") }
    @Test fun gameSlate() { game(TableSurface.SLATE, 8); shoot("game_slate") }

    // Pinched in to about 2.2x near the top of the board: the zoom-out button appears
    @Test
    fun gameZoomedIn() {
        game(TableSurface.WALNUT, 11)
        compose.waitForIdle()
        compose.onRoot().performTouchInput {
            val c = Offset(centerX, height * 0.35f)
            pinch(
                start0 = c - Offset(60f, 0f), end0 = c - Offset(132f, 0f),
                start1 = c + Offset(60f, 0f), end1 = c + Offset(132f, 0f),
                durationMillis = 400,
            )
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Zoom out to the whole board").assertExists()
        shoot("game_zoomed")
    }

    // A Quick Game won: a layout outside the levels, so no Next
    @Test
    fun celebrationQuickGame() {
        compose.mainClock.autoAdvance = false
        val won = level(9).let { s -> s.copy(tiles = s.tiles.map { it.copy(isRemoved = true) }, isWon = true) }
        game(TableSurface.WALNUT, null, won, message = "The mist lifts, and so do the tiles.", quickGame = true)
        compose.mainClock.advanceTimeBy(2200)
        shoot("celebration_quick_game")
    }

    @Test
    fun celebration() {
        compose.mainClock.autoAdvance = false
        val won = level(5).let { s -> s.copy(tiles = s.tiles.map { it.copy(isRemoved = true) }, isWon = true) }
        game(TableSurface.WALNUT, 5, won, message = "The mist lifts, and so do the tiles.")
        // The 600 ms smash, then the card's 1.1 s, and a little of its fade
        compose.mainClock.advanceTimeBy(2200)
        shoot("celebration")
    }

    // Earned hints: two to spend, shown on the Hint button
    @Test
    fun hintsLeft() {
        game(TableSurface.GREEN_MAT, 5, hints = 2)
        shoot("hints_two")
    }

    // None left: the button greyed, and a tap says how to earn one
    @Test
    fun hintsNone() {
        game(TableSurface.GREEN_MAT, 5, hints = 0)
        compose.onNodeWithText("Hint").performClick()
        compose.mainClock.advanceTimeBy(600)
        shoot("hints_none")
    }

    // The first clean clear: the card introduces the gold ribbon
    @Test
    fun celebrationFirstRibbon() {
        compose.mainClock.autoAdvance = false
        val won = level(5).let { s -> s.copy(tiles = s.tiles.map { it.copy(isRemoved = true) }, isWon = true) }
        game(TableSurface.WALNUT, 5, won, message = "The mist lifts, and so do the tiles.", firstRibbon = true)
        // The 600 ms smash, then the card's 1.1 s, and a little of its fade
        compose.mainClock.advanceTimeBy(2200)
        shoot("celebration_first_ribbon")
    }

    @Test
    fun fireworksAlone() {
        compose.setContent {
            Box(Modifier.fillMaxSize().background(Color(0xFF0A0C0B))) {
                val draw = fireworksSnapshot(seed = 7L, seconds = 1.9f)
                Canvas(Modifier.fillMaxSize()) { draw(this) }
            }
        }
        shoot("fireworks")
    }

    // Low-performance mode's lighter show, at the same moment
    @Test
    fun fireworksLite() {
        compose.setContent {
            Box(Modifier.fillMaxSize().background(Color(0xFF0A0C0B))) {
                val draw = fireworksSnapshot(seed = 7L, seconds = 1.9f, lite = true)
                Canvas(Modifier.fillMaxSize()) { draw(this) }
            }
        }
        shoot("fireworks_lite")
    }

    @Test
    fun settings() {
        compose.setContent {
            MahjongTheme {
                SettingsScreen(settings = GameSettings(tableSurface = TableSurface.THAI_STRAW), onSettingsChanged = {}, onBack = {})
            }
        }
        compose.onNodeWithText("Straw").performScrollTo()
        shoot("settings")
    }

    // With the phone's Remove animations on: the fire is out, and the Fireplace row says why
    @Test
    fun settingsWithoutAnimations() {
        compose.setContent {
            MahjongTheme {
                SettingsScreen(settings = GameSettings(fireplaceLit = true, animationsEnabled = false), onSettingsChanged = {}, onBack = {})
            }
        }
        compose.onNodeWithText("Fire sound").performScrollTo()
        shoot("settings_no_animations")
    }

    // The whole settings list on one tall page, down to the credits at its foot
    // Low-performance mode on: the fire out (as the app shows it, with animations stilled)
    @Test @Config(qualifiers = "w427dp-h3400dp-port-360dpi")
    fun settingsLowPerformance() {
        compose.setContent {
            MahjongTheme { SettingsScreen(settings = GameSettings(lowPerformance = true, animationsEnabled = false), onSettingsChanged = {}, onBack = {}) }
        }
        shoot("settings_low_performance")
    }

    @Test @Config(qualifiers = "w427dp-h3400dp-port-360dpi")
    fun settingsFullLength() {
        compose.setContent {
            MahjongTheme { SettingsScreen(settings = GameSettings(fireplaceLit = true), onSettingsChanged = {}, onBack = {}) }
        }
        compose.waitForIdle()
        shoot("settings_full")
    }
}
