package com.vortessence.mahjong.engine

import com.vortessence.mahjong.data.model.BoardTile
import com.vortessence.mahjong.data.model.Constellation
import com.vortessence.mahjong.data.model.GameState
import com.vortessence.mahjong.data.model.Move
import kotlin.random.Random

sealed class TileTapResult {
    data class Blocked(val tileId: Int) : TileTapResult()
    data class Selected(val tileId: Int) : TileTapResult()
    data object Deselected : TileTapResult()
    data class Matched(val tileId1: Int, val tileId2: Int, val isWon: Boolean, val isStalemate: Boolean) : TileTapResult()
    data class SelectionChanged(val newSelectedTileId: Int) : TileTapResult()
}

object MahjongGameEngine {


    /**
     * Initializes a new game state with a solvable board layout.
     */
    fun startNewGame(constellation: Constellation, random: Random = Random): GameState {
        val deal = BoardSolvabilityGenerator.deal(constellation, random)
        return stateOf(constellation, deal.tiles, plan = deal.plan)
            .copy(dealt = deal.tiles.sortedBy { it.id }.map { it.tile }, dealtPlan = deal.plan)
    }

    /**
     * The same deal again, from the start: every tile back where it was dealt, with the face it
     * was dealt (a reshuffle changes them). A game from an older save, which didn't keep its deal,
     * gets its tiles back as they are now.
     */
    fun replay(state: GameState): GameState {
        val faces = state.dealt.takeIf { it.size == state.tiles.size }
        val tiles = state.tiles.map { t -> t.copy(isRemoved = false, tile = faces?.getOrNull(t.id) ?: t.tile) }
        return stateOf(state.constellation, tiles, plan = if (faces != null) state.dealtPlan else emptyList())
            .copy(dealt = state.dealt, dealtPlan = state.dealtPlan)
    }

    /** A game in play on [tiles], nothing selected: a new deal, or one restored from a save. */
    fun stateOf(
        constellation: Constellation,
        tiles: List<BoardTile>,
        moveHistory: List<Move> = emptyList(),
        plan: List<Move> = emptyList(),
    ): GameState {
        val tilesWithFreedom = recalculateFreedom(tiles)
        val availablePairs = findAvailablePairs(tilesWithFreedom)
        return GameState(
            constellation = constellation,
            tiles = tilesWithFreedom,
            moveHistory = moveHistory,
            isWon = tilesWithFreedom.none { !it.isRemoved },
            isStalemate = isStuck(tilesWithFreedom, availablePairs),
            availablePairsCount = availablePairs.size,
            plan = plan,
        )
    }

    /**
     * Handles user interaction with a tile.
     */
    fun handleTileTap(currentState: GameState, tileId: Int): Pair<GameState, TileTapResult> {
        val targetTile = currentState.tiles.firstOrNull { it.id == tileId }
            ?: return currentState to TileTapResult.Deselected

        if (targetTile.isRemoved) {
            return currentState to TileTapResult.Deselected
        }

        // 1. Tapped a blocked tile
        if (!targetTile.isFree) {
            val updatedState = currentState.copy(
                blockedTileId = tileId
            )
            return updatedState to TileTapResult.Blocked(tileId)
        }

        val currentSelectedId = currentState.selectedTileId

        // The hinted pair stays lit while one of its own tiles is picked, so its partner is easy
        // to find again
        val hintKept = currentState.hintPair?.takeIf { tileId == it.first || tileId == it.second }

        // 2. No tile currently selected: select this one
        if (currentSelectedId == null) {
            val updatedState = currentState.copy(
                selectedTileId = tileId,
                hintPair = hintKept,
                blockedTileId = null
            )
            return updatedState to TileTapResult.Selected(tileId)
        }

        // 3. Tapped the same tile again: deselect it
        if (currentSelectedId == tileId) {
            val updatedState = currentState.copy(
                selectedTileId = null,
                hintPair = hintKept,
                blockedTileId = null
            )
            return updatedState to TileTapResult.Deselected
        }

        // 4. Another free tile was already selected: check for match!
        val selectedTile = currentState.tiles.firstOrNull { it.id == currentSelectedId }
        if (selectedTile != null && (selectedTile.tile.matches(targetTile.tile))) {
            // MATCH!
            val updatedTiles = currentState.tiles.map { t ->
                if (t.id == currentSelectedId || t.id == tileId) {
                    t.copy(isRemoved = true, isFree = false)
                } else {
                    t
                }
            }

            val refreshedTiles = recalculateFreedom(updatedTiles)
            val availablePairs = findAvailablePairs(refreshedTiles)
            val remainingCount = refreshedTiles.count { !it.isRemoved }
            val isWon = remainingCount == 0
            val isStalemate = isStuck(refreshedTiles, availablePairs)

            val newHistory = currentState.moveHistory + Move(currentSelectedId, tileId)

            val updatedState = currentState.copy(
                tiles = refreshedTiles,
                selectedTileId = null,
                hintPair = null,
                blockedTileId = null,
                moveHistory = newHistory,
                isWon = isWon,
                isStalemate = isStalemate,
                availablePairsCount = availablePairs.size
            )

            return updatedState to TileTapResult.Matched(currentSelectedId, tileId, isWon, isStalemate)
        }

        // 5. Does not match: switch selection to the new free tile
        val updatedState = currentState.copy(
            selectedTileId = tileId,
            hintPair = hintKept,
            blockedTileId = null
        )
        return updatedState to TileTapResult.SelectionChanged(tileId)
    }

    /**
     * Undoes the most recent matched pair.
     */
    fun undo(currentState: GameState): GameState {
        if (currentState.moveHistory.isEmpty()) return currentState

        val lastMove = currentState.moveHistory.last()
        val remainingHistory = currentState.moveHistory.dropLast(1)

        val updatedTiles = currentState.tiles.map { t ->
            if (t.id == lastMove.tileId1 || t.id == lastMove.tileId2) {
                t.copy(isRemoved = false)
            } else {
                t
            }
        }

        val refreshedTiles = recalculateFreedom(updatedTiles)
        val availablePairs = findAvailablePairs(refreshedTiles)

        return currentState.copy(
            tiles = refreshedTiles,
            selectedTileId = null,
            hintPair = null,
            blockedTileId = null,
            moveHistory = remainingHistory,
            isWon = false,
            isStalemate = isStuck(refreshedTiles, availablePairs),
            availablePairsCount = availablePairs.size
        )
    }

    /** A pair to hint, and the plan it was taken from (empty if none was found). */
    data class Hint(val pair: Pair<Int, Int>, val plan: List<Move>)

    /**
     * Picks a pair to hint that keeps the board clearable: one of the free pairs of a plan that
     * still holds (the deal's, or one the solver finds), taking the next of them on each repeated
     * hint. If no plan is found in time, the most promising free pair. Null if no pair is free.
     * It can take a moment on a big board, so it's for a background thread.
     */
    fun findHint(state: GameState): Hint? {
        if (findAvailablePairs(state.tiles).isEmpty()) return null
        val plan = state.plan.takeIf { planHolds(state.tiles, it) } ?: HintSolver.solve(state.tiles, hint = state.plan)
        val candidates = if (plan != null) {
            val free = state.tiles.filter { it.isFree }.map { it.id }.toSet()
            plan.filter { it.tileId1 in free && it.tileId2 in free }.map { it.tileId1 to it.tileId2 }
        } else {
            HintSolver.rank(state.tiles)
        }
        if (candidates.isEmpty()) return null
        val shown = candidates.indexOfFirst { samePair(it, state.hintPair) }
        return Hint(candidates[(shown + 1) % candidates.size], plan ?: emptyList())
    }

    /**
     * Whether [plan] still clears what's left of [tiles]: every tile left is in one of its pairs
     * with its planned partner, and the pairs still match. Taking pairs early never blocks a tile,
     * so the pairs left can be taken in the plan's order, and any of them that's free can go now.
     */
    fun planHolds(tiles: List<BoardTile>, plan: List<Move>): Boolean {
        if (plan.isEmpty()) return false
        val byId = tiles.associateBy { it.id }
        val covered = HashSet<Int>()
        for (move in plan) {
            val a = byId[move.tileId1] ?: return false
            val b = byId[move.tileId2] ?: return false
            if (a.isRemoved != b.isRemoved) return false
            if (a.isRemoved) continue
            if (!a.tile.matches(b.tile)) return false
            covered.add(a.id)
            covered.add(b.id)
        }
        return tiles.all { it.isRemoved || it.id in covered }
    }

    private fun samePair(a: Pair<Int, Int>, b: Pair<Int, Int>?) =
        b != null && ((a.first == b.first && a.second == b.second) || (a.first == b.second && a.second == b.first))

    /**
     * Reshuffles remaining unremoved tiles in case of stalemate.
     */
    fun reshuffleRemaining(currentState: GameState): GameState {
        // Some layouts cannot be solved by any arrangement (e.g. the last two tiles stacked on
        // top of each other), so take back matched pairs until a solvable redistribution exists.
        var state = currentState
        var solvable: Assignment?
        while (true) {
            val activeTiles = state.tiles.filter { !it.isRemoved }
            if (activeTiles.isEmpty()) return currentState
            solvable = BoardSolvabilityGenerator.reassignSolvable(
                positions = activeTiles.map { it.position },
                tiles = activeTiles.map { it.tile },
            )
            if (solvable != null || state.moveHistory.isEmpty()) break
            state = undo(state)
        }

        // Prefer a solvable redistribution; fall back to a plain shuffle if none exists
        val activeTiles = state.tiles.filter { !it.isRemoved }
        val solvableMap = solvable
        val activeMap = if (solvableMap != null) {
            activeTiles.associate { it.id to solvableMap.tiles.getValue(it.position) }
        } else {
            val activeTilesValues = activeTiles.map { it.tile }.shuffled()
            activeTiles.indices.associate { i ->
                activeTiles[i].id to activeTilesValues[i]
            }
        }

        val updatedTiles = state.tiles.map { t ->
            if (activeMap.containsKey(t.id)) {
                t.copy(tile = activeMap[t.id]!!)
            } else {
                t
            }
        }

        val refreshedTiles = recalculateFreedom(updatedTiles)
        val availablePairs = findAvailablePairs(refreshedTiles)

        // The new arrangement comes with the order it can be cleared in
        val ids = activeTiles.associate { it.position to it.id }
        val plan = solvableMap?.order?.map { (p, q) -> Move(ids.getValue(p), ids.getValue(q)) } ?: emptyList()

        return state.copy(
            tiles = refreshedTiles,
            selectedTileId = null,
            hintPair = null,
            blockedTileId = null,
            isStalemate = isStuck(refreshedTiles, availablePairs),
            availablePairsCount = availablePairs.size,
            plan = plan,
        )
    }

    /**
     * Whether play has run out: tiles are left but no free pair matches. While any pair can
     * still be taken the player keeps going, even if the board can no longer be cleared in the
     * end (e.g. two matching tiles stacked on each other); the reshuffle then fixes it.
     */
    fun isStuck(tiles: List<BoardTile>, availablePairs: List<Pair<Int, Int>>): Boolean =
        availablePairs.isEmpty() && tiles.any { !it.isRemoved }

    /**
     * An order that clears every tile left, pair by pair, or null if there is none. Searched
     * exhaustively, so only for the few tiles at a board's end.
     */
    fun clearingOrder(tiles: List<BoardTile>): List<Pair<Int, Int>>? {
        val board = recalculateFreedom(tiles)
        if (board.none { !it.isRemoved }) return emptyList()
        for (pair in findAvailablePairs(board)) {
            val after = board.map { if (it.id == pair.first || it.id == pair.second) it.copy(isRemoved = true) else it }
            clearingOrder(after)?.let { return listOf(pair) + it }
        }
        return null
    }

    /**
     * Recalculates the `isFree` status for every active tile on the board.
     */
    fun recalculateFreedom(tiles: List<BoardTile>): List<BoardTile> {
        val activePositions = tiles.asSequence().filter { !it.isRemoved }.map { it.position }.toSet()
        return tiles.map { tile ->
            if (tile.isRemoved) {
                tile.copy(isFree = false)
            } else {
                val free = BoardSolvabilityGenerator.isTileFree(tile.position, activePositions)
                tile.copy(isFree = free)
            }
        }
    }

    /**
     * Finds all currently available matching free tile pairs.
     */
    fun findAvailablePairs(tiles: List<BoardTile>): List<Pair<Int, Int>> {
        val freeTiles = tiles.filter { !it.isRemoved && it.isFree }
        val pairs = mutableListOf<Pair<Int, Int>>()

        for (i in freeTiles.indices) {
            for (j in (i + 1) until freeTiles.size) {
                if (freeTiles[i].tile.matches(freeTiles[j].tile)) {
                    pairs.add(Pair(freeTiles[i].id, freeTiles[j].id))
                }
            }
        }
        return pairs
    }
}
