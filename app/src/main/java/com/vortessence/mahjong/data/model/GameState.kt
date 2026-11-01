package com.vortessence.mahjong.data.model

data class Move(
    val tileId1: Int,
    val tileId2: Int,
)

data class GameState(
    val constellation: Constellation,
    val tiles: List<BoardTile> = emptyList(),
    val selectedTileId: Int? = null,
    val hintPair: Pair<Int, Int>? = null,
    val moveHistory: List<Move> = emptyList(),
    val isWon: Boolean = false,
    val isStalemate: Boolean = false,
    val availablePairsCount: Int = 0,
    val blockedTileId: Int? = null,
    // A hint was shown or the tiles reshuffled this game, so a win won't count as clean (undo is fine)
    val usedHelp: Boolean = false,
    // An order the pairs can be taken in to clear the board, which hints follow: from the deal or
    // the reshuffle, or found by the hint solver. Not saved; a restored game finds one again
    val plan: List<Move> = emptyList(),
    // The deal as it was first laid out, for replaying it: each tile's face (by tile id) and the
    // deal's plan. Empty for a game restored from a save made before deals were kept
    val dealt: List<Tile> = emptyList(),
    val dealtPlan: List<Move> = emptyList(),
) {
    val remainingTilesCount: Int get() = tiles.count { !it.isRemoved }

    /**
     * The end of a board: two or three pairs left, all on the lowest layer, and a way to clear
     * them. The game then offers to finish it (in [finishPlan]'s order).
     */
    val canFinish: Boolean get() = finishPlan != null

    val finishPlan: List<Pair<Int, Int>>? by lazy {
        val left = tiles.filter { !it.isRemoved }
        val ground = tiles.minOfOrNull { it.position.z } ?: return@lazy null
        if (isWon || left.size !in FINISH_MIN_TILES..FINISH_MAX_TILES || left.any { it.position.z != ground }) {
            null
        } else {
            com.vortessence.mahjong.engine.MahjongGameEngine.clearingOrder(tiles)
        }
    }
    val totalTilesCount: Int get() = tiles.size
}

// Finish is offered from 3 pairs left down to 2 (fewer than 4, and one pair is just a tap)
private const val FINISH_MIN_TILES = 4
private const val FINISH_MAX_TILES = 6
