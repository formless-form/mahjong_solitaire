package com.vortessence.mahjong.data.model

/**
 * Everything Dark Mahjong records about the player: how far the level journey has got, which
 * boards have been cleared, and the days whose puzzle was solved (epoch days, for Today's Puzzle's
 * calendar). Deliberately no times, counts, scores or streaks.
 */
data class Progress(
    val highestLevelReached: Int = 1,
    val completedConstellations: Set<String> = emptySet(),
    val puzzleDaysSolved: Set<Long> = emptySet(),
    // Layouts cleared cleanly (no hints, no reshuffles) since that was first kept: the gold ribbon
    val cleanConstellations: Set<String> = emptySet(),
    // Hints to spend: one earned for each first clean clear, at most MAX_HINTS
    val hints: Int = 0,
)

const val MAX_HINTS = 6
