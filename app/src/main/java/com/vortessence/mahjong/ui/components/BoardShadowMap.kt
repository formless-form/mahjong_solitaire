package com.vortessence.mahjong.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RuntimeShader
import android.graphics.Shader
import com.vortessence.mahjong.data.model.BoardTile
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * The board's shading, worked out once each time the tiles change (see FLAME_AGSL).
 *
 * - **The height map:** how tall the stack stands at each point, in layers (31 per layer, up to
 *   8), at the flat table's full resolution. Each tile is drawn into it exactly as TileRenderer
 *   draws it: square on the grid, its rounded corners, and its side walls swept below
 *   and to the right. So the fire's long shadows, traced through it per pixel every frame, follow
 *   each tile's curved edges; they stop at a taller stack, fall across lower ones, soften as they
 *   reach away (the fire is broad) and sway with the flames.
 * - **Ambient occlusion on the table:** how hemmed in each point of the table is by the tiles
 *   around it, which darkens the table at the board's edge and in the gaps. A cheap, soft stand-in
 *   at a quarter resolution, used by the table only (the tiles' own walls and contact shadows darken
 *   their foot instead), so no tile picks up a rim from it.
 *
 * Both are made for the board as it was laid out when they were built, and only rebuilt when
 * tiles are removed. When the board grows or moves (its automatic scaling), that is only a
 * scale and a shift, so [placed] tells the shaders how to find the map from the board as it is
 * now, and the shading keeps up every frame. Building one takes some tens of milliseconds, so
 * the board makes it off the main thread.
 */
class BoardShadowMap private constructor(
    private val heights: BitmapShader,
    private val occlusion: BitmapShader,
    // The board's layout when the map was made
    private val built: BoardTransform,
    // The number of layers in the tallest stack
    private val layers: Int,
    // Where tiles stand in the map (left, top, right, bottom, in map pixels), with a margin; left
    // beyond right when none do. The shaders look for shadows only within it
    private val box: FloatArray,
    /** The tiles it was made from. */
    val tiles: List<BoardTile>,
) {

    /** The map, placed under the board as it is laid out [now], on a table [tableHeight] tall. */
    fun placed(now: BoardTransform, tableHeight: Float) = Placed(now, tableHeight)

    inner class Placed(now: BoardTransform, tableHeight: Float) {
        // Table pixels now to map pixels: the map scaled and shifted with the board
        private val scale = built.unitX / now.unitX
        private val offsetX = built.startX - now.startX * scale
        private val offsetY = built.startY - now.startY * scale
        // One layer's height in table heights (as the shaders' q), at the board's size now
        private val layer = now.tileWidth * TILE_HEIGHT / tableHeight

        /** [fireShadows]: trace the fire's long shadows as well as the table's ambient occlusion. */
        fun setUniforms(s: RuntimeShader, fireShadows: Boolean) {
            s.setInputShader("heightMap", heights)
            s.setInputShader("aoMap", occlusion)
            s.setFloatUniform("mapXform", scale, scale, offsetX, offsetY)
            s.setFloatUniform("shade", if (fireShadows) 1f else 0f, AO_SCALE, layer, layers * layer)
            s.setFloatUniform("aoStrength", AMBIENT_OCCLUSION)
            s.setFloatUniform("boardBox", box)
        }
    }

    companion object {
        private const val AO_SCALE = 0.25f
        private const val PER_LAYER = 31
        private const val AMBIENT_OCCLUSION = 0.8f

        // A tile stands about this much of its width tall
        private const val TILE_HEIGHT = 0.15f

        private val none by lazy {
            BitmapShader(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }

        /** Leaves the shaders' light unshaded. */
        fun setNone(s: RuntimeShader) {
            s.setInputShader("heightMap", none)
            s.setInputShader("aoMap", none)
            s.setFloatUniform("mapXform", 1f, 1f, 0f, 0f)
            s.setFloatUniform("shade", 0f, AO_SCALE, 1f, 1f)
            s.setFloatUniform("aoStrength", 0f)
            s.setFloatUniform("boardBox", 1f, 1f, 0f, 0f)
        }

        fun build(tiles: List<BoardTile>, transform: BoardTransform, w: Float, h: Float): BoardShadowMap? {
            if (w <= 0f || h <= 0f) return null
            val standing = tiles.filter { !it.isRemoved }.sortedBy { it.z }
            val layer = transform.tileWidth * TILE_HEIGHT / h
            val box = bounds(standing, transform)
            return BoardShadowMap(
                heightMap(standing, transform, w, h),
                occlusionMap(standing, transform, w, h, layer, box),
                transform,
                (standing.maxOfOrNull { it.z } ?: 0) + 1,
                box,
                tiles,
            )
        }

        /**
         * Where the standing tiles lie, faces and walls, in map pixels: wide enough for the map's
         * smoothing, so nothing of theirs lies outside it.
         */
        private fun bounds(standing: List<BoardTile>, transform: BoardTransform): FloatArray {
            if (standing.isEmpty()) return floatArrayOf(1f, 1f, 0f, 0f)
            var left = Float.MAX_VALUE
            var top = Float.MAX_VALUE
            var right = -Float.MAX_VALUE
            var bottom = -Float.MAX_VALUE
            for (tile in standing) {
                val x = transform.tilePixelX(tile.x, tile.z)
                val y = transform.tilePixelY(tile.y, tile.z)
                left = minOf(left, x)
                top = minOf(top, y)
                right = maxOf(right, x + transform.tileWidth + transform.depthX)
                bottom = maxOf(bottom, y + transform.tileHeight + transform.depthY)
            }
            val margin = transform.tileWidth * 0.04f + 3f
            return floatArrayOf(left - margin, top - margin, right + margin, bottom + margin)
        }

        /** The full-resolution height map, each tile drawn exactly as on screen. */
        private fun heightMap(standing: List<BoardTile>, transform: BoardTransform, w: Float, h: Float): BitmapShader {
            val bitmap = Bitmap.createBitmap(w.toInt().coerceAtLeast(1), h.toInt().coerceAtLeast(1), Bitmap.Config.ALPHA_8)
            val canvas = Canvas(bitmap)
            // Each tile replaces what's below it (lowest first), so a point holds the top tile's height
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC) }
            val tw = transform.tileWidth
            val th = transform.tileHeight
            val depthX = tw * BoardTransform.LAYER_SHIFT_FACTOR_X
            val depthY = th * BoardTransform.LAYER_SHIFT_FACTOR_Y
            val corner = tw * 0.085f
            for (tile in standing) {
                val x = transform.tilePixelX(tile.x, tile.z)
                val y = transform.tilePixelY(tile.y, tile.z)
                paint.alpha = ((tile.z + 1) * PER_LAYER).coerceAtMost(255)
                // The face, and the side walls swept out to their full depth
                for (k in 0..3) {
                    val dx = depthX * k / 3f
                    val dy = depthY * k / 3f
                    canvas.drawRoundRect(x + dx, y + dy, x + dx + tw, y + dy + th, corner, corner, paint)
                }
            }
            return BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                filterMode = BitmapShader.FILTER_MODE_LINEAR
            }
        }

        /**
         * The table's ambient occlusion at a quarter resolution: two rings of neighbours around each
         * point, each darkening by how steeply it rises above the table. Worked out only around
         * the board ([box]): further out, nothing rises.
         */
        private fun occlusionMap(
            standing: List<BoardTile>,
            transform: BoardTransform,
            w: Float,
            h: Float,
            layer: Float,
            box: FloatArray,
        ): BitmapShader {
            val mw = (w * AO_SCALE).toInt().coerceAtLeast(2)
            val mh = (h * AO_SCALE).toInt().coerceAtLeast(2)
            val cell = 1f / (h * AO_SCALE) // table heights per cell
            val tileCells = transform.tileWidth * AO_SCALE
            val depthX = transform.tileWidth * BoardTransform.LAYER_SHIFT_FACTOR_X
            val depthY = transform.tileHeight * BoardTransform.LAYER_SHIFT_FACTOR_Y
            val radius = transform.tileWidth * 0.085f * AO_SCALE

            // A coarse height map with rounded tiles (no nudges: the occlusion is soft anyway)
            val height = FloatArray(mw * mh)
            for (tile in standing) {
                val top = (tile.z + 1) * layer
                val x = transform.tilePixelX(tile.x, tile.z)
                val y = transform.tilePixelY(tile.y, tile.z)
                val left = x * AO_SCALE
                val right = (x + transform.tileWidth + depthX) * AO_SCALE
                val upper = y * AO_SCALE
                val lower = (y + transform.tileHeight + depthY) * AO_SCALE
                val cx = (left + right) / 2f
                val cy = (upper + lower) / 2f
                val innerX = (right - left) / 2f - radius
                val innerY = (lower - upper) / 2f - radius
                for (j in upper.toInt().coerceIn(0, mh - 1)..lower.toInt().coerceIn(0, mh - 1)) {
                    for (i in left.toInt().coerceIn(0, mw - 1)..right.toInt().coerceIn(0, mw - 1)) {
                        val dx = maxOf(abs(i + 0.5f - cx) - innerX, 0f)
                        val dy = maxOf(abs(j + 0.5f - cy) - innerY, 0f)
                        if (dx * dx + dy * dy > radius * radius) continue
                        val k = j * mw + i
                        if (height[k] < top) height[k] = top
                    }
                }
            }

            fun heightAt(fx: Float, fy: Float): Float {
                val x = fx.coerceIn(0f, mw - 1.001f)
                val y = fy.coerceIn(0f, mh - 1.001f)
                val i = floor(x).toInt()
                val j = floor(y).toInt()
                val tx = x - i
                val ty = y - j
                val a = height[j * mw + i] * (1 - tx) + height[j * mw + i + 1] * tx
                val b = height[(j + 1) * mw + i] * (1 - tx) + height[(j + 1) * mw + i + 1] * tx
                return a * (1 - ty) + b * ty
            }

            val ringX = FloatArray(8) { cos(it * 0.785398f + 0.3f) }
            val ringY = FloatArray(8) { sin(it * 0.785398f + 0.3f) }
            val pixels = IntArray(mw * mh)
            // The board's bounds in map cells, out as far as the outer ring reaches
            val ringReach = tileCells * 0.22f + 2f
            val i0 = (box[0] * AO_SCALE - ringReach).toInt().coerceIn(0, mw)
            val i1 = (box[2] * AO_SCALE + ringReach).toInt().coerceIn(0, mw - 1)
            val j0 = (box[1] * AO_SCALE - ringReach).toInt().coerceIn(0, mh)
            val j1 = (box[3] * AO_SCALE + ringReach).toInt().coerceIn(0, mh - 1)
            for (j in j0..j1) for (i in i0..i1) {
                val px = i + 0.5f
                val py = j + 0.5f
                var occ = 0f
                for (d in 0 until 8) for (ring in 0..1) {
                    val r = tileCells * (if (ring == 0) 0.07f else 0.22f)
                    val rise = heightAt(px + ringX[d] * r, py + ringY[d] * r)
                    occ += (rise / (r * cell + 0.4f * layer)).coerceIn(0f, 1f)
                }
                pixels[j * mw + i] = android.graphics.Color.rgb(0, (occ / 16f * 255).toInt(), 0)
            }
            val bitmap = Bitmap.createBitmap(pixels, mw, mh, Bitmap.Config.ARGB_8888)
            return BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                filterMode = BitmapShader.FILTER_MODE_LINEAR
            }
        }
    }
}
