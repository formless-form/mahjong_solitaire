package com.vortessence.mahjong.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vortessence.mahjong.data.model.Constellation
import com.vortessence.mahjong.data.model.Progress
import com.vortessence.mahjong.data.repository.ConstellationRepository
import com.vortessence.mahjong.ui.components.BadgeState
import com.vortessence.mahjong.ui.components.DifficultyStars
import com.vortessence.mahjong.ui.components.ExpressiveScreen
import com.vortessence.mahjong.ui.components.MenuBackdrop
import com.vortessence.mahjong.ui.components.LevelBadge
import com.vortessence.mahjong.ui.components.SegmentGap
import com.vortessence.mahjong.ui.components.segmentColors

@Composable
fun LevelSelectScreen(
    progress: Progress,
    levels: List<Constellation> = ConstellationRepository.levelConstellations,
    tutorial: Constellation = ConstellationRepository.tutorialConstellation,
    onLevelSelected: (Int) -> Unit,
    onBack: () -> Unit,
    fireIntensity: Float = 0.5f,
    // The menus' fire goes out with the phone's Remove animations
    fire: Boolean = true,
) {
    val highestUnlocked = progress.highestLevelReached.coerceAtLeast(1)
    // The tutorial first (always open), then the levels in order
    val rows = listOf(ConstellationRepository.TUTORIAL_LEVEL to tutorial) + levels.mapIndexed { i, c -> (i + 1) to c }
    // Open near the level you're up to rather than at level 1
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (highestUnlocked - 1).coerceIn(0, rows.lastIndex))

    ExpressiveScreen(title = "Levels", onBack = onBack, backdrop = { MenuBackdrop(fireIntensity, fire) }) { padding ->
        // Below the title bar, as on Layouts, so the list never shows through it
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 12.dp,
                bottom = padding.calculateBottomPadding() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(SegmentGap),
        ) {
            itemsIndexed(rows, key = { _, row -> row.first }) { index, (levelIndex, constellation) ->
                val isTutorial = levelIndex == ConstellationRepository.TUTORIAL_LEVEL
                val unlocked = isTutorial || levelIndex <= highestUnlocked
                val cleared = progress.completedConstellations.contains(constellation.id)
                // Locked and current come first: a layout cleared under Layouts may be a level
                // not reached yet, or the one being played now
                val state = when {
                    !unlocked -> BadgeState.LOCKED
                    levelIndex == highestUnlocked -> BadgeState.CURRENT
                    cleared -> BadgeState.CLEARED
                    else -> BadgeState.OPEN
                }
                SegmentedListItem(
                    onClick = { onLevelSelected(levelIndex) },
                    enabled = unlocked,
                    shapes = ListItemDefaults.segmentedShapes(index = index, count = rows.size),
                    colors = segmentColors(),
                    leadingContent = {
                        LevelBadge(
                            state,
                            label = levelIndex.toString(),
                            icon = if (isTutorial) Icons.Rounded.School else null,
                            ribbon = constellation.id in progress.cleanConstellations,
                        )
                    },
                    overlineContent = {
                        Text(
                            if (isTutorial) "Tutorial" else "Level $levelIndex",
                            color = if (unlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    supportingContent = { Text("${constellation.difficulty.displayName} · ${constellation.tileCount} tiles") },
                    trailingContent = { DifficultyStars(constellation.difficulty.stars, dimmed = !unlocked) },
                ) {
                    Text(constellation.name)
                }
            }
        }
    }
}
