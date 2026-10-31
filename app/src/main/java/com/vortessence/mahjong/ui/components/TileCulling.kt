package com.vortessence.mahjong.ui.components

import com.vortessence.mahjong.data.model.BoardTile
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Painter's Algorithm order for tiles:
 * 1. Layer Z ascending (bottom layer first, apex layer last)
 * 2. X + Y ascending: a tile's depth edge extends right and down, so any tile overlapping it from
 *    the east or south must be drawn later. Tiles are 2x2 grid units, so such a neighbor always has
 *    a strictly larger X + Y, even on half-offset rows/columns where plain X-then-Y ordering fails.
 * 3. X ascending as a tie-breaker for diagonal neighbors that only touch at a corner
 */
internal val TileDrawOrder: Comparator<BoardTile> =
    compareBy<BoardTile> { it.z }
        .thenBy { it.x + it.y }
        .thenBy { it.x }

/**
 * Which tiles the player can see. A tile entirely covered by tiles drawn after it is never drawn,
 * nor are its shadows (the tiles over it cast their own). Worked out in the layout's own grid
 * units (BoardTransform.unitX = 1), so it holds at any board size and zoom, and only when tiles
 * are removed or put back.
 *
 * Covering tiles count only where they are surely solid: their rounded corners, and the fraction
 * off square each tile sits at, are allowed for, so a sliver of a tile beneath never goes missing.
 */
internal object TileCulling {
    // Raster cells per grid unit (a tile is 2 units wide)
    private const val CELLS = 16

    // A margin for safety: tiles are drawn a hair inside their places (the gap between neighbours,
    // about 0.01 units). The covering tiles are taken this much smaller, the one beneath this much
    // larger, so a sliver of a lower tile showing through a gap is never left out.
    private const val MARGIN = 0.025f

    /** The standing tiles that are at least partly visible, in draw order. */
    fun visibleInDrawOrder(tiles: List<BoardTile>): List<BoardTile> {
        val standing = tiles.filter { !it.isRemoved }.sortedWith(TileDrawOrder)
        if (standing.size < 2) return standing

        val tileW = 2f
        val tileH = 2f * BoardTransform.ASPECT_RATIO
        val shiftX = tileW * BoardTransform.LAYER_SHIFT_FACTOR_X
        val shiftY = tileH * BoardTransform.LAYER_SHIFT_FACTOR_Y
        val corner = tileW * 0.085f
        fun left(t: BoardTile) = t.x - t.z * shiftX
        fun top(t: BoardTile) = t.y * BoardTransform.ASPECT_RATIO - t.z * shiftY

        // The raster spans every tile, face and walls, with a cell to spare all round
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (t in standing) {
            minX = min(minX, left(t)); minY = min(minY, top(t))
            maxX = max(maxX, left(t) + tileW + shiftX); maxY = max(maxY, top(t) + tileH + shiftY)
        }
        minX -= 1f; minY -= 1f
        val cols = ceil((maxX + 1f - minX) * CELLS).toInt()
        val rows = ceil((maxY + 1f - minY) * CELLS).toInt()

        // The last tile (in draw order) surely solid over each cell's centre
        val top = IntArray(cols * rows) { -1 }
        val inset = MARGIN
        val r = max(corner - inset, 0f)
        standing.forEachIndexed { i, t ->
            // The body is the face swept back to its full depth: three steps along the sweep,
            // each inset, cover it solidly
            for (k in 0..2) {
                val x0 = left(t) + shiftX * k / 2f + inset
                val y0 = top(t) + shiftY * k / 2f + inset
                val x1 = x0 + tileW - 2f * inset
                val y1 = y0 + tileH - 2f * inset
                forCells(x0, y0, x1, y1, minX, minY, cols, rows) { c, cx, cy ->
                    if (insideRounded(cx, cy, x0, y0, x1, y1, r)) top[c] = i
                }
            }
        }

        // A tile is seen if any cell of its outline (allowing for its own slight offset) has
        // nothing drawn after it surely covering that cell
        val visible = ArrayList<BoardTile>(standing.size)
        val grow = MARGIN
        standing.forEachIndexed { i, t ->
            val x0 = left(t) - grow
            val y0 = top(t) - grow
            val x1 = left(t) + tileW + shiftX + grow
            val y1 = top(t) + tileH + shiftY + grow
            var seen = false
            forCells(x0, y0, x1, y1, minX, minY, cols, rows) { c, _, _ ->
                if (top[c] <= i) seen = true
            }
            if (seen) visible += t
        }
        return visible
    }

    private inline fun forCells(
        x0: Float, y0: Float, x1: Float, y1: Float, minX: Float, minY: Float, cols: Int, rows: Int,
        block: (cell: Int, cx: Float, cy: Float) -> Unit,
    ) {
        val c0 = floor((x0 - minX) * CELLS).toInt().coerceIn(0, cols - 1)
        val c1 = ceil((x1 - minX) * CELLS).toInt().coerceIn(0, cols - 1)
        val r0 = floor((y0 - minY) * CELLS).toInt().coerceIn(0, rows - 1)
        val r1 = ceil((y1 - minY) * CELLS).toInt().coerceIn(0, rows - 1)
        for (row in r0..r1) {
            val cy = minY + (row + 0.5f) / CELLS
            if (cy < y0 || cy > y1) continue
            for (col in c0..c1) {
                val cx = minX + (col + 0.5f) / CELLS
                if (cx < x0 || cx > x1) continue
                block(row * cols + col, cx, cy)
            }
        }
    }

    private fun insideRounded(px: Float, py: Float, x0: Float, y0: Float, x1: Float, y1: Float, r: Float): Boolean {
        val dx = max(max(x0 + r - px, px - (x1 - r)), 0f)
        val dy = max(max(y0 + r - py, py - (y1 - r)), 0f)
        return dx * dx + dy * dy <= r * r
    }
}
