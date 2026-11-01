package com.vortessence.mahjong.screenshots

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import com.github.takahirom.roborazzi.captureRoboImage
import com.vortessence.mahjong.data.model.ContinueInfo
import com.vortessence.mahjong.data.model.GameSettings
import com.vortessence.mahjong.data.model.GameState
import com.vortessence.mahjong.data.model.Progress
import com.vortessence.mahjong.data.model.TableSurface
import com.vortessence.mahjong.data.repository.ConstellationRepository
import com.vortessence.mahjong.engine.MahjongGameEngine
import com.vortessence.mahjong.ui.components.TileWear
import com.vortessence.mahjong.ui.screens.GameScreen
import com.vortessence.mahjong.ui.screens.LevelSelectScreen
import com.vortessence.mahjong.ui.screens.MainMenuScreen
import com.vortessence.mahjong.ui.theme.MahjongTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Google Play listing screenshots: 1080 x 1920 px (Play wants phone screenshots at exactly 9:16,
 * and the Pixel's own screen is taller than that). Rendered into
 * build/store-screenshots. Run: ./gradlew testFossDebugUnitTest --tests '*StoreScreenshots*'
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-port-xxhdpi")
class StoreScreenshots {
    @get:Rule
    val compose = createComposeRule()

    // The menu firelight's clock never lets the screen settle
    @org.junit.Before
    fun holdMenuFireStill() {
        com.vortessence.mahjong.ui.components.MenuFire.flickers = false
    }

    private fun shoot(name: String) = compose.onRoot().captureRoboImage("build/store-screenshots/$name.png")

    private fun game(surface: TableSurface, state: GameState, levelIndex: Int?, message: String? = null) {
        TileWear.prepareNow()
        compose.setContent {
            MahjongTheme {
                GameScreen(
                    gameState = state,
                    levelIndex = levelIndex,
                    settings = GameSettings(tableSurface = surface, fireplaceLit = false),
                    winMessage = message,
                    onBack = {}, onTileClick = {}, onHint = {}, onUndo = {}, onRestart = {},
                    onReshuffle = {}, onNextLevel = {}, onMainMenu = {},
                )
            }
        }
    }

    private fun level(index: Int) = MahjongGameEngine.startNewGame(ConstellationRepository.getLevel(index))

    // Layouts are picked by id, so their level numbers follow the current order
    private fun numberOf(id: String) = ConstellationRepository.levelIndexOf(id)!!

    private fun game(surface: TableSurface, id: String, message: String? = null, won: Boolean = false) {
        val n = numberOf(id)
        val state = level(n).let { s -> if (won) s.copy(tiles = s.tiles.map { it.copy(isRemoved = true) }, isWon = true) else s }
        game(surface, state, n, message)
    }

    @Test
    fun s1Title() {
        compose.setContent {
            MahjongTheme {
                MainMenuScreen(
                    continueInfo = ContinueInfo("Level ${numberOf("mah_2283982827")}: Heart"),
                    onPlayLevels = {}, onChooseConstellation = {}, onSettings = {},
                )
            }
        }
        shoot("1-title")
    }

    @Test fun s2Walnut() { game(TableSurface.WALNUT, "pillar_of_heaven"); shoot("2-walnut") }

    @Test
    fun s3Zoomed() {
        game(TableSurface.THAI_STRAW, "mah_1088330854")
        compose.waitForIdle()
        compose.onRoot().performTouchInput {
            val c = Offset(centerX, height * 0.4f)
            pinch(c - Offset(60f, 0f), c - Offset(140f, 0f), c + Offset(60f, 0f), c + Offset(140f, 0f), durationMillis = 400)
        }
        compose.waitForIdle()
        shoot("3-zoomed-thai-straw")
    }

    @Test fun s4GreenMat() { game(TableSurface.GREEN_MAT, "cascading_falls"); shoot("4-green-mat") }

    @Test
    fun s5Celebration() {
        compose.mainClock.autoAdvance = false
        game(TableSurface.WALNUT, "torii_arch", message = "The mist lifts, and so do the tiles.", won = true)
        // The 600 ms smash, then the card's 1.1 s, and a little of its fade
        compose.mainClock.advanceTimeBy(2200)
        shoot("5-celebration")
    }

    @Test
    fun s6Levels() {
        val levels = ConstellationRepository.levelConstellations
        compose.setContent {
            MahjongTheme {
                LevelSelectScreen(
                    progress = Progress(highestLevelReached = 4, completedConstellations = levels.take(3).map { it.id }.toSet() + "tutorial"),
                    levels = levels,
                    onLevelSelected = {},
                    onBack = {},
                )
            }
        }
        shoot("6-levels")
    }
}
