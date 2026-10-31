package com.vortessence.mahjong.ui.components

import android.graphics.BlurMaskFilter
import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import java.util.concurrent.Executors
import kotlin.math.ceil
import kotlin.math.max
import android.graphics.Paint
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.toPath
import java.util.concurrent.ConcurrentHashMap
import com.vortessence.mahjong.ui.components.tileset.MahjongTiles
import com.vortessence.mahjong.ui.components.tileset.*
import com.vortessence.mahjong.data.model.BoardTile
import com.vortessence.mahjong.data.model.Tile
import com.vortessence.mahjong.data.model.TileSuit
import com.vortessence.mahjong.ui.theme.GoldHighlight
import com.vortessence.mahjong.ui.theme.TileBevelHighlight
import com.vortessence.mahjong.ui.theme.TileIvoryBottom
import com.vortessence.mahjong.ui.theme.TileIvoryTop
import com.vortessence.mahjong.ui.theme.TileSideIvoryBottom
import com.vortessence.mahjong.ui.theme.TileSideWoodBacking
import com.vortessence.mahjong.ui.theme.TutorialHighlight

object TileRenderer {


    /**
     * Draws a single Mahjong tile on Canvas with physical 3D depth, authentic enamel coloring,
     * and engraved intaglio details, faithful to classic vintage Mahjong sets.
     */
    fun drawTile(
        scope: DrawScope,
        boardTile: BoardTile,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        isSelected: Boolean = false,
        isHinted: Boolean = false,
        isBlockedHighlight: Boolean = false,
        isTutorialHighlight: Boolean = false,
        highlightFreeTiles: Boolean = false,
        glare: Float = 0f,
        shadows: TileShadows = TileShadows.NONE,
        // How strongly a hinted tile shows its gold this frame, 0..1: the hint pulses
        hintStrength: Float = 1f,
        // Drawn shape by shape, never stamped from the atlas: for tiles shown much larger than
        // the board's (the matched pair in flight), which the atlas's images would soften
        live: Boolean = false,
        // How much darker this tile is drawn for lying below the top layer (0 on top): layers
        // read apart by their tone
        layerShade: Float = 0f,
        // The stretches of this tile's right and lower sides that stand against a neighbour on its
        // layer, as (side, from, to) triples, from and to along the side from 0 to 1
        // (MahjongBoard.seams)
        seams: FloatArray? = null,
        // How much darker the right and lower sides are for facing away from the light reaching
        // this tile (MahjongBoard.sidesAway), 0 when they face it
        rightAway: Float = 0f,
        belowAway: Float = 0f,
    ) {
        if (boardTile.isRemoved) return
        // Square on the grid, a hair inside its place, so neighbours show a fine line between
        // them (about a pixel) rather than running together
        val inset = max(TILE_GAP_MIN, width * TILE_GAP) / 2f
        val ix = x + inset
        val iy = y + inset
        val iw = width - 2f * inset
        val ih = height - 2f * inset
        drawTileBody(
            scope, boardTile, ix, iy, iw, ih, isSelected, isHinted, isBlockedHighlight,
            isTutorialHighlight, highlightFreeTiles, glare, shadows, hintStrength, live,
        )
        if (rightAway > 0f || belowAway > 0f) drawSidesAway(scope, rightAway, belowAway, ix, iy, iw, ih)
        if (seams != null) drawSeams(scope, seams, ix, iy, iw, ih)
        // Lower layers a shade darker; a highlighted or selected tile keeps its colour true
        val highlighted = isSelected || isHinted || isBlockedHighlight || isTutorialHighlight
        if (layerShade > 0f && !highlighted) {
            scope.drawRoundRect(
                color = Color.Black.copy(alpha = layerShade),
                topLeft = Offset(ix, iy),
                size = Size(iw, ih),
                cornerRadius = CornerRadius(iw * 0.085f),
            )
        }
    }

    // The gap between neighbouring tiles: a share of the tile's width, at least a pixel
    private const val TILE_GAP = 0.009f
    private const val TILE_GAP_MIN = 1f

    /*
     * A tile's sides (its right and lower walls, the ones the camera sees) are in shadow where a
     * neighbour stands against them: light can't reach down the crack, and only a sliver of the
     * wall shows between the two. Only along the stretch the neighbour shares; a side open to
     * the table stays lit. The face is untouched.
     */
    private fun drawSeams(
        scope: DrawScope, seams: FloatArray,
        ix: Float, iy: Float, iw: Float, ih: Float,
    ) {
        val shade = Color.Black.copy(alpha = SIDE_SHADOW)
        val depthX = iw * BoardTransform.LAYER_SHIFT_FACTOR_X
        val depthY = ih * BoardTransform.LAYER_SHIFT_FACTOR_Y
        // Shaded within the sides' own outline only (their rounded corners included), never on
        // the face. Each side's stretch and the corner below and to the right are separate pieces
        // that never overlap, so nowhere is shaded twice; where a neighbour's stretch ends partway
        // along a side, the shade fades out over a corner's length rather than stopping dead. The
        // rounded corner turns from facing right to facing down, and its shade turns with it: the
        // right side's where it faces right, the lower side's where it faces down
        val r = iw * 0.085f
        scope.withTransform({ translate(ix, iy) }) {
            clipPath(sidesOutline(iw, ih, depthX, depthY)) {
                var rightToCorner = false
                var belowToCorner = false
                var i = 0
                while (i + 2 < seams.size) {
                    val side = seams[i].toInt()
                    val from = seams[i + 1]
                    val to = seams[i + 2]
                    i += 3
                    if (to >= 1f) {
                        if (side == SEAM_RIGHT) rightToCorner = true else belowToCorner = true
                    }
                    if (side == SEAM_RIGHT) {
                        val start = from * ih
                        val end = if (to >= 1f) ih - r else to * ih
                        if (end <= start) continue
                        drawRect(
                            fadedEnds(shade, start, end, r, from > 0f, to < 1f, vertical = true),
                            Offset(iw - r, start), Size(r + depthX, end - start),
                        )
                    } else {
                        val start = from * iw
                        val end = if (to >= 1f) iw - r else to * iw
                        if (end <= start) continue
                        drawRect(
                            fadedEnds(shade, start, end, r, from > 0f, to < 1f, vertical = false),
                            Offset(start, ih - r), Size(end - start, r + depthY),
                        )
                    }
                }
                if (rightToCorner || belowToCorner) {
                    val clear = shade.copy(alpha = 0f)
                    val facingRight = if (rightToCorner) shade else clear
                    val facingDown = if (belowToCorner) shade else clear
                    drawRect(
                        Brush.sweepGradient(
                            0f to facingRight,
                            0.25f to facingDown,
                            1f to facingRight,
                            center = Offset(iw - r, ih - r),
                        ),
                        Offset(iw - r, ih - r), Size(r + depthX, r + depthY),
                    )
                }
            }
        }
    }

    /*
     * The sides facing away from the light are darker: the right side by [right], the lower by
     * [below], and the rounded corner between turning from one to the other as it turns from
     * facing right to facing down. In the sides' own outline only, as the seams' shadow.
     */
    private fun drawSidesAway(scope: DrawScope, right: Float, below: Float, ix: Float, iy: Float, iw: Float, ih: Float) {
        val depthX = iw * BoardTransform.LAYER_SHIFT_FACTOR_X
        val depthY = ih * BoardTransform.LAYER_SHIFT_FACTOR_Y
        val r = iw * 0.085f
        val toRight = Color.Black.copy(alpha = right)
        val down = Color.Black.copy(alpha = below)
        scope.withTransform({ translate(ix, iy) }) {
            clipPath(sidesOutline(iw, ih, depthX, depthY)) {
                drawRect(toRight, Offset(iw - r, 0f), Size(r + depthX, ih - r))
                drawRect(down, Offset(0f, ih - r), Size(iw - r, r + depthY))
                drawRect(
                    Brush.sweepGradient(0f to toRight, 0.25f to down, 1f to toRight, center = Offset(iw - r, ih - r)),
                    Offset(iw - r, ih - r), Size(r + depthX, r + depthY),
                )
            }
        }
    }

    // The shade along a stretch from start to end, fading in over [fade] at a start that falls
    // partway along a side, and out at such an end
    private fun fadedEnds(shade: Color, start: Float, end: Float, fade: Float, fadeIn: Boolean, fadeOut: Boolean, vertical: Boolean): Brush {
        val length = end - start
        val f = (fade / length).coerceAtMost(0.5f)
        val clear = shade.copy(alpha = 0f)
        val stops = arrayOf(
            0f to if (fadeIn) clear else shade,
            f to shade,
            1f - f to shade,
            1f to if (fadeOut) clear else shade,
        )
        return if (vertical) Brush.verticalGradient(*stops, startY = start, endY = end)
        else Brush.horizontalGradient(*stops, startX = start, endX = end)
    }

    // The sides' outline for a tile iw x ih, at the origin: the tile and its sides swept down
    // and to the right, less its face. Made once for each tile size
    private fun sidesOutline(iw: Float, ih: Float, depthX: Float, depthY: Float): Path {
        val made = sidesPath
        if (made != null && sidesW == iw && sidesH == ih) return made
        val r = CornerRadius(iw * 0.085f)
        val whole = Path().apply { addRoundRect(RoundRect(0f, 0f, iw + depthX, ih + depthY, r)) }
        val face = Path().apply { addRoundRect(RoundRect(0f, 0f, iw, ih, r)) }
        val sides = Path().apply { op(whole, face, PathOperation.Difference) }
        sidesPath = sides
        sidesW = iw
        sidesH = ih
        return sides
    }

    private var sidesPath: Path? = null
    private var sidesW = 0f
    private var sidesH = 0f

    const val SEAM_RIGHT = 1
    const val SEAM_BOTTOM = 3

    // How dark a side is in the crack against a neighbour
    private const val SIDE_SHADOW = 0.2f

    /**
     * The soft contact shadow a raised tile casts onto the tiles it rests on, deepest at its foot,
     * where little light reaches (a cheap ambient occlusion). Soft-edged and a little larger than
     * the tile, so it shows on every side. The board draws a whole layer's contact shadows before
     * that layer's tiles, so they fall only on the layer below, never on a neighbour of the same
     * height.
     */
    fun drawContactShadow(scope: DrawScope, x: Float, y: Float, width: Float, height: Float) {
        if (PerfSwitches.isOff("contact")) return
        val stamp = contactStamp(width)
        val left = x + width * BoardTransform.LAYER_SHIFT_FACTOR_X * 1.4f
        val top = y + height * BoardTransform.LAYER_SHIFT_FACTOR_Y * 1.4f
        // The stamp is the blurred shape with its blur's reach all round, scaled to this tile
        val pad = width * CONTACT_BLUR * CONTACT_PAD
        contactRect.set(left - pad, top - pad, left + width + pad, top + height + pad)
        scope.drawContext.canvas.nativeCanvas.drawBitmap(stamp, null, contactRect, contactPaint)
    }

    /**
     * The contact shadow's soft rounded shape, blurred once into an alpha image for tiles about
     * [width] wide (made again only when tiles are shown a good deal larger or smaller), so
     * drawing one is a single stamp rather than a blur every frame.
     */
    private fun contactStamp(width: Float): Bitmap {
        val made = contactBitmap
        if (made != null && width < contactWidth * 1.15f && width > contactWidth * 0.85f) return made
        val w = width.coerceAtLeast(8f)
        val h = w * BoardTransform.ASPECT_RATIO
        val blur = w * CONTACT_BLUR
        val pad = blur * CONTACT_PAD
        val bitmap = Bitmap.createBitmap(ceil(w + 2f * pad).toInt(), ceil(h + 2f * pad).toInt(), Bitmap.Config.ALPHA_8)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
        }
        val r = w * 0.085f * 1.5f
        android.graphics.Canvas(bitmap).drawRoundRect(pad, pad, pad + w, pad + h, r, r, paint)
        contactBitmap = bitmap
        contactWidth = w
        return bitmap
    }

    private fun drawTileBody(
        scope: DrawScope,
        boardTile: BoardTile,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        isSelected: Boolean = false,
        isHinted: Boolean = false,
        isBlockedHighlight: Boolean = false,
        isTutorialHighlight: Boolean = false,
        highlightFreeTiles: Boolean = false,
        glare: Float = 0f,
        shadows: TileShadows = TileShadows.NONE,
        hintStrength: Float = 1f,
        live: Boolean = false,
    ) {
        if (boardTile.isRemoved) return

        // 0. Highlight / Tint Color Logic
        val isImportantHighlight = isSelected || isHinted || isBlockedHighlight || isTutorialHighlight
        val freeTint = highlightFreeTiles && boardTile.isFree
        // The selected tile is drawn plain, then marked in polished gold (drawGoldSelection)
        val highlight = when {
            isSelected -> Highlight.NONE
            isHinted -> Highlight.HINT
            isBlockedHighlight -> Highlight.BLOCKED
            isTutorialHighlight -> Highlight.TUTORIAL
            else -> Highlight.NONE
        }
        val highlightColor = highlight.colour ?: if (freeTint && !isImportantHighlight) FreeTint else null
        // A hinted tile's gold pulses
        val strength = if (highlight == Highlight.HINT) hintStrength.coerceIn(0f, 1f) else 1f
        val tintAlpha = if (isImportantHighlight) 0.5f * strength else 0.15f

        // 1. Physically Grounded 3D Shadows
        drawRestingShadows(scope, boardTile.z, x, y, width, height, shadows)
        if (isSelected) drawGoldGlow(scope, x, y, width, height)

        // Tiles are stamped from the atlas, highlighted ones too: their bodies come tinted
        if (!live && !PerfSwitches.isOff("atlas")) {
            val atlas = TileAtlas.ready(width)
            if (atlas != null &&
                atlas.stamp(scope, boardTile, x, y, width, height, highlight, freeTint && !isSelected, strength, glare, goldFace = isSelected)
            ) {
                if (isSelected) drawGoldRim(scope, x, y, width, height)
                return
            }
        }

        // 2. Solid 3D Body Extrusion (Backing -> Sides -> Face)
        drawWalls(scope, x, y, width, height, highlightColor, tintAlpha)
        drawFace(scope, x, y, width, height, highlightColor, tintAlpha, freeTint && !isSelected)
        if (isSelected) drawGoldFace(scope, x, y, width, height)

        val tile = boardTile.tile
        // 6. Detailed Classic Mahjong Artwork
        if (!PerfSwitches.isOff("art")) getTileVector(tile)?.let { vector ->
            drawArtwork(scope, vector, x, y, width, height)
        }

        // 7. Wear from years of play: grime, hairline scratches, dings, fingerprint haze
        val look = TileWear.look(boardTile.id)
        val face = faceRect(x, y, width, height)
        if (!PerfSwitches.isOff("wear")) drawWear(scope, look, face.topLeft, face.size)

        // 8. The clear glaze, over everything painted on the face. The face is flat; its gloss
        // shows in sharp reflections of the lamp (streaked by tiny ripples in the glaze, strongest
        // near the lamp's mirror point, with a hotspot there), scratch flares and edge glints
        if (!PerfSwitches.isOff("glaze")) drawGlaze(scope, look, face.topLeft, face.size, width, glare, GlazePart.ALL)
        if (isSelected) drawGoldRim(scope, x, y, width, height)
    }

    /*
     * The selected tile, in polished gold (chosen 2026-10-29 over a flat amber tint): a warm
     * golden glow behind it, its face washed with gold, and a gold rim with a fine glint.
     */

    // Polished gold: bright highlights and deep shadows in turn across the tile, as a curved
    // metal edge catches the lamp
    private fun goldMetal(x: Float, y: Float, w: Float, h: Float) = Brush.linearGradient(
        0f to Color(0xFFFFF3C4),
        0.2f to Color(0xFFF2C14E),
        0.42f to Color(0xFFB8860B),
        0.6f to Color(0xFFF7D774),
        0.82f to Color(0xFF9C6F12),
        1f to Color(0xFFE9C46A),
        start = Offset(x, y),
        end = Offset(x + w, y + h),
    )

    private fun drawGoldRim(scope: DrawScope, x: Float, y: Float, w: Float, h: Float) {
        val r = w * 0.085f
        val stroke = w * 0.05f
        scope.drawRoundRect(
            brush = goldMetal(x, y, w, h),
            topLeft = Offset(x + stroke / 2f, y + stroke / 2f),
            size = Size(w - stroke, h - stroke),
            cornerRadius = CornerRadius(r - stroke / 2f),
            style = Stroke(width = stroke),
        )
        // A fine glint along its inner edge
        scope.drawRoundRect(
            color = Color(0xFFFFF6D0).copy(alpha = 0.7f),
            topLeft = Offset(x + stroke, y + stroke),
            size = Size(w - 2f * stroke, h - 2f * stroke),
            cornerRadius = CornerRadius(r - stroke),
            style = Stroke(width = w * 0.008f),
        )
    }

    private fun drawGoldGlow(scope: DrawScope, x: Float, y: Float, w: Float, h: Float) {
        val stamp = glowStamp(w, h)
        // The stamp holds the glow's shape and its blur's reach all round, for tiles glowWidth wide
        val k = w / glowWidth
        val reach = glowWidth * (GLOW_GROW + GLOW_BLUR * GLOW_PAD) * k
        glowRect.set(x - reach, y - reach, x - reach + stamp.width * k, y - reach + stamp.height * k)
        scope.drawContext.canvas.nativeCanvas.drawBitmap(stamp, null, glowRect, goldGlowPaint)
    }

    /**
     * The gold glow's soft rounded shape, blurred once into an alpha image for tiles about [w]
     * wide (made again only when tiles are shown a good deal larger or smaller), as the contact
     * shadow is: a stamp rather than a blur every frame.
     */
    private fun glowStamp(w: Float, h: Float): Bitmap {
        val made = glowBitmap
        if (made != null && w < glowWidth * 1.15f && w > glowWidth * 0.85f) return made
        val tw = w.coerceAtLeast(8f)
        val th = tw * h / w
        val grow = tw * GLOW_GROW
        val blur = tw * GLOW_BLUR
        val reach = grow + blur * GLOW_PAD
        val bitmap = Bitmap.createBitmap(ceil(tw + 2f * reach).toInt(), ceil(th * 1.13f + 2f * reach).toInt(), Bitmap.Config.ALPHA_8)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL) }
        android.graphics.Canvas(bitmap).drawRoundRect(reach - grow, reach - grow, reach + tw + grow, reach + th * 1.13f + grow, tw * 0.15f, tw * 0.15f, paint)
        glowBitmap = bitmap
        glowWidth = tw
        return bitmap
    }


    private fun drawGoldFace(scope: DrawScope, x: Float, y: Float, w: Float, h: Float, alpha: Float = 0.30f) {
        val face = faceRect(x, y, w, h)
        scope.drawRoundRect(
            brush = goldMetal(x, y, w, h),
            topLeft = face.topLeft,
            size = face.size,
            cornerRadius = CornerRadius(w * 0.077f),
            alpha = alpha,
        )
    }


    private val goldGlowPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { color = android.graphics.Color.argb(230, 0xF5, 0xB8, 0x2E) }
    private val glowRect = RectF()
    private var glowBitmap: Bitmap? = null
    private var glowWidth = 0f

    // The glow reaches this far beyond the tile, and is blurred by this much, in tile widths; the
    // blur fades out within GLOW_PAD of its radius
    private const val GLOW_GROW = 0.07f
    private const val GLOW_BLUR = 0.12f
    private const val GLOW_PAD = 2.2f

    /**
     * A highlight's tint over a tile's walls and face: the hinted pair's gold, a blocked tile's
     * red flash, the tutorial's blue. The atlas holds a body for each, with and without free
     * tiles' green.
     */
    internal enum class Highlight(val colour: Color?) {
        NONE(null),
        HINT(GoldHighlight),
        BLOCKED(Color(0xFFDC2626)),
        TUTORIAL(TutorialHighlight),
    }

    /** The shadows under a tile: its resting shadow, cast from the lamp, and on the table a soft base. */
    private fun drawRestingShadows(scope: DrawScope, layerZ: Int, x: Float, y: Float, width: Float, height: Float, shadows: TileShadows) {
        val depthX = width * BoardTransform.LAYER_SHIFT_FACTOR_X
        val depthY = height * BoardTransform.LAYER_SHIFT_FACTOR_Y
        val cornerR = width * 0.085f
        // The resting shadow under the lamp, filled in a little where firelight reaches
        val shadowAlpha = (0.28f + (layerZ * 0.05f)).coerceAtMost(0.65f) * shadows.lampDarkness
        if (layerZ == 0) {
            scope.drawRoundRect(
                color = Color.Black.copy(alpha = 0.2f * shadows.lampDarkness),
                topLeft = Offset(x + (depthX * 1.6f), y + (depthY * 1.6f)),
                size = Size(width, height),
                cornerRadius = CornerRadius(cornerR * 1.8f, cornerR * 1.8f),
            )
        }
        scope.drawRoundRect(
            color = Color.Black.copy(alpha = shadowAlpha),
            topLeft = Offset(x + depthX + (width * 0.01f) + shadows.lamp.x, y + depthY + (height * 0.01f) + shadows.lamp.y),
            size = Size(width, height),
            cornerRadius = CornerRadius(cornerR, cornerR),
        )
    }

    /** The tile's solid body: wooden backing, then ivory sides, swept out to its depth. */
    private fun drawWalls(scope: DrawScope, x: Float, y: Float, width: Float, height: Float, highlightColor: Color?, tintAlpha: Float) {
        val depthX = width * BoardTransform.LAYER_SHIFT_FACTOR_X
        val depthY = height * BoardTransform.LAYER_SHIFT_FACTOR_Y
        val cornerR = width * 0.085f
        val corner = CornerRadius(cornerR, cornerR)
        // A) Bamboo / Wood Backing Layer
        val woodDepthStart = 0.7f
        val woodSteps = if (PerfSwitches.isOff("walls")) 1 else 8
        for (i in woodSteps downTo 0) {
            val stepDepth = woodDepthStart + ((1f - woodDepthStart) * (i / woodSteps.toFloat()))
            scope.drawRoundRect(
                color = TileSideWoodBacking,
                topLeft = Offset(x + (depthX * stepDepth), y + (depthY * stepDepth)),
                size = Size(width, height),
                cornerRadius = corner,
            )
        }
        if (highlightColor != null) {
            scope.drawRoundRect(
                color = highlightColor.copy(alpha = tintAlpha),
                topLeft = Offset(x + (depthX * 0.85f), y + (depthY * 0.85f)),
                size = Size(width, height),
                cornerRadius = corner,
            )
        }

        // B) Ivory Side Extrusion
        val ivorySteps = if (PerfSwitches.isOff("walls")) 1 else 16
        for (i in ivorySteps downTo 0) {
            val progress = (i / ivorySteps.toFloat()) * woodDepthStart
            val stepX = depthX * progress
            val stepY = depthY * progress

            scope.drawRoundRect(
                color = TileSideIvoryBottom,
                topLeft = Offset(x + stepX, y + stepY),
                size = Size(width, height),
                cornerRadius = corner,
            )

            if (i == ivorySteps) {
                scope.drawRoundRect(
                    color = Color.Black.copy(alpha = 0.3f),
                    topLeft = Offset(x + stepX, y + stepY),
                    size = Size(width, height),
                    cornerRadius = corner,
                    style = Stroke(width = width * 0.012f),
                )
            }
        }
        // C) Contact shade: the side walls darken towards their foot, where little light reaches
        // into the narrow gap against the table or the next tile (ambient occlusion). One
        // gradient under the face, so only the walls show it.
        scope.drawRoundRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                1f to Color.Black.copy(alpha = WALL_SHADE_ALPHA),
                startY = y + height - depthY * 0.2f,
                endY = y + height + depthY,
            ),
            topLeft = Offset(x + depthX, y + depthY),
            size = Size(width, height),
            cornerRadius = corner,
        )
        if (highlightColor != null) {
            scope.drawRoundRect(
                color = highlightColor.copy(alpha = tintAlpha),
                topLeft = Offset(x + (depthX * 0.35f), y + (depthY * 0.35f)),
                size = Size(width, height),
                cornerRadius = corner,
            )
        }
    }

    /** The face inside its bevel, where the artwork, wear and glaze go. */
    private fun faceRect(x: Float, y: Float, width: Float, height: Float): androidx.compose.ui.geometry.Rect {
        val bevelInset = width * 0.015f
        return androidx.compose.ui.geometry.Rect(x + bevelInset, y + bevelInset, x + width - bevelInset, y + height - bevelInset)
    }

    /** 3. The ivory face with its bevelled edge, any highlight over it, and the ivory's age. */
    private fun drawFace(
        scope: DrawScope, x: Float, y: Float, width: Float, height: Float,
        highlightColor: Color?, tintAlpha: Float, freeTint: Boolean,
    ) {
        val cornerR = width * 0.085f
        val corner = CornerRadius(cornerR, cornerR)
        val face = faceRect(x, y, width, height)
        val faceTopLeft = face.topLeft
        val faceSize = face.size
        val faceCorner = CornerRadius(cornerR * 0.9f, cornerR * 0.9f)

        val faceBrush = Brush.verticalGradient(
            colors = listOf(TileIvoryTop, TileIvoryBottom),
            startY = y,
            endY = y + height,
        )
        scope.drawRoundRect(
            brush = faceBrush,
            topLeft = faceTopLeft,
            size = faceSize,
            cornerRadius = faceCorner,
        )

        // 4. Face Highlights & Selection Overlay
        if (highlightColor != null) {
            scope.drawRoundRect(
                color = highlightColor.copy(alpha = tintAlpha),
                topLeft = Offset(x, y),
                size = Size(width, height),
                cornerRadius = corner,
            )
        } else {
            scope.drawRoundRect(
                color = TileBevelHighlight.copy(alpha = 0.6f),
                topLeft = faceTopLeft,
                size = faceSize,
                cornerRadius = faceCorner,
                style = Stroke(width = width * 0.008f),
            )
        }

        // Free tile subtle tint
        if (freeTint) {
            scope.drawRoundRect(
                color = Color(0x0C0F6B38),
                topLeft = Offset(x, y),
                size = Size(width, height),
                cornerRadius = corner,
            )
        }

        // The ivory's age: the same warm tint on every tile (TileWear.ageing)
        val ageing = TileWear.ageing(0)
        if (ageing > 0.01f) {
            scope.drawRoundRect(
                color = Color(0xFFB08A4A).copy(alpha = ageing),
                topLeft = faceTopLeft,
                size = faceSize,
                cornerRadius = faceCorner,
            )
        }
    }

    /**
     * The face's artwork over the tile at ([x], [y]). It is engraved: paint sits in carved grooves.
     * Lit from above, each groove's upper wall falls into a fine shadow and its lower lip catches a
     * fine light edge.
     */
    private fun drawArtwork(scope: DrawScope, vector: ImageVector, x: Float, y: Float, width: Float, height: Float) {
        val cx = x + (width * 0.5f)
        val cy = y + (height * 0.5f)
        val innerW = width * 0.85f
        val innerH = height * 0.85f
        val e = width * 0.009f
        drawVectorTile(scope, vector, cx, cy - e * 0.7f, innerW, innerH, tint = EngraveShadow)
        drawVectorTile(scope, vector, cx, cy + e, innerW, innerH, tint = EngraveLight)
        drawVectorTile(scope, vector, cx, cy, innerW, innerH)
    }

    /**
     * Each tile drawn from a few images made once, rather than from some fifty shapes, gradients
     * and paths a frame (most of a frame's work, through the tilted camera). Tiles share them:
     *
     * - **body:** walls, outline, face and the ivory's age, the same for every tile, and again
     *   tinted for each highlight, each with and without free tiles' green;
     * - **artwork:** each face's, engraving and all;
     * - **surface:** each of the [TileWear.LOOKS] looks' wear and the glaze's fixed parts;
     * - **glare:** each look's reflections of the lamp and the fire, stamped as strongly as the
     *   tile catches them that frame.
     *
     * The images are made at the size the tiles are shown, off the main thread: again only when
     * they are shown much larger (zoomed in, or the board grown as it empties) or much smaller,
     * the current ones being stamped scaled meanwhile. Until the first are ready, tiles are drawn
     * shape by shape, as are the matched pair in flight, shown larger than the images.
     */
    object TileAtlas {
        /** On-screen pixels per flat table pixel: the zoom times the camera's nearest magnification. */
        @Volatile
        var screenScale = 1f

        /** Make images on the calling thread (tests and previews). */
        @Volatile
        var bakeInline = false

        class Baked internal constructor(
            internal val width: Int,
            private val pad: Int,
            // Each Highlight's, untinted first, each followed by the same with free tiles' green
            private val bodies: Array<Bitmap>,
            private val art: Map<Tile, Bitmap>,
            private val surfaces: Array<Bitmap>,
            private val glares: Array<Bitmap>,
        ) {
            /**
             * Stamps [tile] at ([x], [y]), its body tinted by [highlight] as strongly as
             * [strength] (and with free tiles' green if [free]), the selection's gold over its
             * face if [goldFace]; false if this atlas lacks its artwork.
             */
            internal fun stamp(
                scope: DrawScope,
                tile: BoardTile,
                x: Float,
                y: Float,
                width: Float,
                height: Float,
                highlight: Highlight,
                free: Boolean,
                strength: Float,
                glare: Float,
                goldFace: Boolean,
            ): Boolean {
                val art = art[tile.tile] ?: return false
                val canvas = scope.drawContext.canvas.nativeCanvas
                val k = width / this.width
                val freeIndex = if (free) 1 else 0
                val body = bodies[highlight.ordinal * 2 + freeIndex]
                stampRect.set(x - pad * k, y - pad * k, x - pad * k + body.width * k, y - pad * k + body.height * k)
                if (highlight != Highlight.NONE && strength < 0.995f) {
                    // A tint only partly shown: the untinted body, with the tinted one over it
                    canvas.drawBitmap(bodies[freeIndex], null, stampRect, stampPaint)
                    tintPaint.alpha = (strength * 255f).roundToInt()
                    canvas.drawBitmap(body, null, stampRect, tintPaint)
                } else {
                    canvas.drawBitmap(body, null, stampRect, stampPaint)
                }
                if (goldFace) drawGoldFace(scope, x, y, width, height)
                stampRect.set(x, y, x + width, y + height)
                canvas.drawBitmap(art, null, stampRect, stampPaint)
                val look = TileWear.look(tile.id)
                canvas.drawBitmap(surfaces[look], null, stampRect, stampPaint)
                val g = glare.coerceIn(0f, 1f)
                if (g > 0.01f) {
                    glarePaint.alpha = (g * 255f).roundToInt()
                    canvas.drawBitmap(glares[look], null, stampRect, glarePaint)
                }
                return true
            }
        }

        @Volatile
        private var baked: Baked? = null

        @Volatile
        private var wanted = 0
        private val worker = Executors.newSingleThreadExecutor { Thread(it, "tile-atlas").apply { isDaemon = true; priority = Thread.MIN_PRIORITY } }

        /**
         * The atlas for tiles [width] flat table pixels wide, ordering a new one if it no longer
         * suits; null while none is ready.
         */
        internal fun ready(width: Float): Baked? {
            if (!TileWear.ready) return null
            // Rounded up to whole steps, so a growing board asks for new images only now and then
            val want = (ceil(width * screenScale / STEP) * STEP).toInt().coerceIn(STEP, MAX_WIDTH)
            val now = baked
            val suits = now != null && want <= now.width && want >= now.width * 0.6f
            if (!suits && wanted != want) {
                wanted = want
                if (bakeInline) bake(want) else worker.execute { if (wanted == want) bake(want) }
            }
            return baked
        }

        private fun bake(width: Int) {
            val w = width.toFloat()
            val h = ceil(w * BoardTransform.ASPECT_RATIO)
            val pad = 2
            val depthX = w * BoardTransform.LAYER_SHIFT_FACTOR_X
            val depthY = h * BoardTransform.LAYER_SHIFT_FACTOR_Y
            fun image(iw: Int, ih: Int, draw: DrawScope.() -> Unit): Bitmap {
                val bitmap = Bitmap.createBitmap(iw, ih, Bitmap.Config.ARGB_8888)
                CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap.asImageBitmap()), Size(iw.toFloat(), ih.toFloat()), draw)
                // Stamped smaller than made, it stays smooth rather than shimmering
                bitmap.setHasMipMap(true)
                return bitmap.asShared()
            }
            val bodyW = ceil(w + depthX).toInt() + 2 * pad
            val bodyH = ceil(h + depthY).toInt() + 2 * pad
            // As drawTileBody tints them: free tiles' faint green alone, or a highlight's stronger tint
            fun body(highlight: Highlight, free: Boolean) = image(bodyW, bodyH) {
                val p = pad.toFloat()
                val colour = highlight.colour ?: if (free) FreeTint else null
                val alpha = if (highlight == Highlight.NONE) 0.15f else 0.5f
                drawWalls(this, p, p, w, h, colour, alpha)
                drawFace(this, p, p, w, h, colour, alpha, free)
            }
            val bodies = Array(Highlight.entries.size * 2) { body(Highlight.entries[it / 2], it % 2 == 1) }
            val ih = h.toInt()
            val face = faceRect(0f, 0f, w, h)
            val art = allTiles.associateWith { tile ->
                image(width, ih) {
                    getTileVector(tile)?.let { drawArtwork(this, it, 0f, 0f, w, h) }
                }
            }
            val surfaces = Array(TileWear.LOOKS) { look ->
                image(width, ih) {
                    drawWear(this, look, face.topLeft, face.size)
                    drawGlaze(this, look, face.topLeft, face.size, w, 0f, GlazePart.FIXED)
                }
            }
            val glares = Array(TileWear.LOOKS) { look ->
                image(width, ih) { drawGlaze(this, look, face.topLeft, face.size, w, 1f, GlazePart.GLARE) }
            }
            baked = Baked(width, pad, bodies, art, surfaces, glares)
        }

        // Every face once: a Season or Flower of each kind, a dragon of each colour, and so on
        private val allTiles: List<Tile> by lazy {
            TileSuit.entries.flatMap { suit -> (1..9).map { Tile(suit, it) } }.filter { getTileVector(it) != null }
        }

        private val stampRect = RectF()
        private val stampPaint = Paint(Paint.FILTER_BITMAP_FLAG)
        private val tintPaint = Paint(Paint.FILTER_BITMAP_FLAG)
        private val glarePaint = Paint(Paint.FILTER_BITMAP_FLAG)

        private const val STEP = 16
        // Enough for tiles a little magnified; zoomed right in they soften slightly, which keeps the
        // images to some 30 MB at most
        private const val MAX_WIDTH = 256
    }

    /** Which parts of the glaze to draw: all of it, or (for the atlas) its fixed parts or its glare. */
    private enum class GlazePart { ALL, FIXED, GLARE }

    private fun drawWear(scope: DrawScope, look: Int, topLeft: Offset, size: Size) {
        val wear = TileWear.overlay(look) ?: return
        val pivot = Offset(topLeft.x + size.width / 2f, topLeft.y + size.height / 2f)
        val (sx, sy) = when (TileWear.orientation(look)) {
            1 -> -1f to 1f
            2 -> 1f to -1f
            3 -> -1f to -1f
            else -> 1f to 1f
        }
        scope.withTransform(
            transformBlock = { scale(sx, sy, pivot) },
            drawBlock = {
                drawImage(
                    image = wear,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(TileWear.WIDTH, TileWear.HEIGHT),
                    dstOffset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()),
                    dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                    filterQuality = FilterQuality.Medium,
                )
            },
        )
    }

    /**
     * The glaze over a face. Its fixed parts and its glare (the lamp's and the fire's reflections,
     * as strong as [glare]) can be drawn apart for the atlas ([part]): the glare drawn at full
     * strength there, to be stamped as strongly as each tile catches it.
     */
    private fun drawGlaze(
        scope: DrawScope,
        look: Int,
        topLeft: Offset,
        size: Size,
        width: Float,
        glare: Float,
        part: GlazePart,
    ) {
        val cornerR = width * 0.085f
        val corner = CornerRadius(cornerR * 0.9f, cornerR * 0.9f)
        val fixed = part != GlazePart.GLARE
        // A reflection as strong as [still] whatever the light, plus [lit] times the glare
        fun reflect(still: Float, lit: Float) = when (part) {
            GlazePart.ALL -> still + lit * glare
            GlazePart.FIXED -> still
            GlazePart.GLARE -> lit
        }
        val wobbleRaw = TileWear.wobble(look)
        if (fixed) scope.drawRoundRect(
            brush = Brush.verticalGradient(
                0f to Color.White.copy(alpha = 0.14f),
                0.2f to Color.White.copy(alpha = 0.03f),
                0.85f to Color.Transparent,
                1f to Color.Black.copy(alpha = 0.06f),
                startY = topLeft.y,
                endY = topLeft.y + size.height,
            ),
            topLeft = topLeft,
            size = size,
            cornerRadius = corner,
        )
        TileWear.sheen(look)?.let { sheen ->
            val mirrorX = if (TileWear.orientation(look) % 2 == 1) -1f else 1f
            scope.withTransform(
                transformBlock = { scale(mirrorX, 1f, Offset(topLeft.x + size.width / 2f, topLeft.y)) },
                drawBlock = {
                    drawImage(
                        image = sheen,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(TileWear.WIDTH, TileWear.HEIGHT),
                        dstOffset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()),
                        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                        alpha = reflect(0.5f, 0.5f).coerceAtMost(1f),
                        filterQuality = FilterQuality.Medium,
                    )
                },
            )
        }
        val band = reflect(0.05f, 0.15f)
        val bandTopLeft = Offset(topLeft.x + size.width * 0.06f, topLeft.y + size.height * 0.03f)
        val bandSize = Size(size.width * 0.88f, size.height * 0.34f)
        scope.drawOval(
            brush = Brush.radialGradient(
                0f to Color.White.copy(alpha = band),
                0.55f to Color.White.copy(alpha = band * 0.45f),
                1f to Color.Transparent,
                center = Offset(bandTopLeft.x + bandSize.width / 2f, bandTopLeft.y + bandSize.height * 0.42f),
                radius = bandSize.width * 0.5f,
            ),
            topLeft = bandTopLeft,
            size = bandSize,
        )
        // A crisp, mirror-like reflection of the lamp's opening: hard-edged like real glaze
        // Because the surface undulates, the lamp's reflection wavers along its lower edge and
        // sits at a slight angle, differently on every tile
        val mirror = reflect(0.17f, 0.3f)
        val mLeft = topLeft.x + size.width * 0.12f
        val mTop = topLeft.y + size.height * 0.045f
        val mW = size.width * 0.76f
        val mH = size.height * 0.145f
        val wobble = mH * (0.08f + 0.14f * wobbleRaw)
        val phase = wobbleRaw * 6.28f
        val reflection = Path().apply {
            moveTo(mLeft + mH * 0.5f, mTop)
            lineTo(mLeft + mW - mH * 0.5f, mTop)
            quadraticTo(mLeft + mW, mTop, mLeft + mW, mTop + mH * 0.5f)
            val steps = 10
            for (i in steps downTo 0) {
                val f = i / steps.toFloat()
                val px = mLeft + mW * (0.04f + 0.92f * f)
                val py = mTop + mH * 0.85f + wobble * kotlin.math.sin(f * 7.5f + phase) +
                    wobble * 0.5f * kotlin.math.sin(f * 15f + phase * 1.7f)
                lineTo(px, py)
            }
            lineTo(mLeft, mTop + mH * 0.5f)
            quadraticTo(mLeft, mTop, mLeft + mH * 0.5f, mTop)
            close()
        }
        val tilt = (wobbleRaw - 0.5f) * 5f
        scope.withTransform(
            transformBlock = { rotate(tilt, Offset(mLeft + mW / 2f, mTop + mH / 2f)) },
            drawBlock = {
                drawPath(
                    path = reflection,
                    brush = Brush.verticalGradient(
                        0f to Color.White.copy(alpha = mirror),
                        0.65f to Color.White.copy(alpha = mirror * 0.75f),
                        1f to Color.White.copy(alpha = 0f),
                        startY = mTop,
                        endY = mTop + mH + wobble,
                    ),
                )
            },
        )
        val hotGlare = if (part == GlazePart.FIXED) 0f else glare
        if (hotGlare > 0.05f) {
            val hot = Offset(topLeft.x + size.width * 0.5f, topLeft.y + size.height * 0.15f)
            scope.drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.55f * hotGlare), Color.Transparent),
                    center = hot,
                    radius = size.width * 0.22f,
                ),
                radius = size.width * 0.22f,
                center = hot,
            )
            scope.drawCircle(
                brush = Brush.radialGradient(
                    0f to Color.White.copy(alpha = 0.75f * hotGlare),
                    0.6f to Color.White.copy(alpha = 0.35f * hotGlare),
                    1f to Color.Transparent,
                    center = hot,
                    radius = size.width * 0.07f,
                ),
                radius = size.width * 0.07f,
                center = hot,
            )
        }
        if (fixed) {
            // The clear coat ends in a fine, crisp edge glint (a flat face, not a rounded cushion)
            val rimWidth = width * 0.012f
            scope.drawRoundRect(
                brush = Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.5f),
                    0.3f to Color.White.copy(alpha = 0.16f),
                    1f to Color.White.copy(alpha = 0.05f),
                    startY = topLeft.y,
                    endY = topLeft.y + size.height,
                ),
                topLeft = Offset(topLeft.x + rimWidth * 0.5f, topLeft.y + rimWidth * 0.5f),
                size = Size(size.width - rimWidth, size.height - rimWidth),
                cornerRadius = CornerRadius(corner.x - rimWidth * 0.5f, corner.y - rimWidth * 0.5f),
                style = Stroke(width = rimWidth),
            )
            // Bevel glints: bright along the top edge, softer down the left, a touch of shade below
            scope.drawLine(
                color = Color.White.copy(alpha = 0.7f),
                start = Offset(topLeft.x + cornerR, topLeft.y + width * 0.006f),
                end = Offset(topLeft.x + size.width - cornerR, topLeft.y + width * 0.006f),
                strokeWidth = width * 0.013f,
            )
            scope.drawLine(
                color = Color.White.copy(alpha = 0.28f),
                start = Offset(topLeft.x + width * 0.006f, topLeft.y + cornerR),
                end = Offset(topLeft.x + width * 0.006f, topLeft.y + size.height - cornerR),
                strokeWidth = width * 0.01f,
            )
            scope.drawLine(
                color = Color.Black.copy(alpha = 0.07f),
                start = Offset(topLeft.x + cornerR, topLeft.y + size.height - width * 0.006f),
                end = Offset(topLeft.x + size.width - cornerR, topLeft.y + size.height - width * 0.006f),
                strokeWidth = width * 0.012f,
            )
        }
    }

    private fun getTileVector(tile: Tile): ImageVector? = when (tile.suit) {
        TileSuit.CHARACTER -> when (tile.value) {
            1 -> MahjongTiles.`08Characters1`
            2 -> MahjongTiles.`09Characters2`
            3 -> MahjongTiles.`10Characters3`
            4 -> MahjongTiles.`11Characters4`
            5 -> MahjongTiles.`12Characters5`
            6 -> MahjongTiles.`13Characters6`
            7 -> MahjongTiles.`14Characters7`
            8 -> MahjongTiles.`15Characters8`
            9 -> MahjongTiles.`16Characters9`
            else -> null
        }
        TileSuit.CIRCLE -> when (tile.value) {
            1 -> MahjongTiles.`17Circles1`
            2 -> MahjongTiles.`18Circles2`
            3 -> MahjongTiles.`19Circles3`
            4 -> MahjongTiles.`20Circles4`
            5 -> MahjongTiles.`21Circles5`
            6 -> MahjongTiles.`22Circles6`
            7 -> MahjongTiles.`23Circles7`
            8 -> MahjongTiles.`24Circles8`
            9 -> MahjongTiles.`25Circles9`
            else -> null
        }
        TileSuit.BAMBOO -> when (tile.value) {
            1 -> MahjongTiles.`26Bamboos1`
            2 -> MahjongTiles.`27Bamboos2`
            3 -> MahjongTiles.`28Bamboos3`
            4 -> MahjongTiles.`29Bamboos4`
            5 -> MahjongTiles.`30Bamboos5`
            6 -> MahjongTiles.`31Bamboos6`
            7 -> MahjongTiles.`32Bamboos7`
            8 -> MahjongTiles.`33Bamboos8`
            9 -> MahjongTiles.`34Bamboos9`
            else -> null
        }
        TileSuit.WIND -> when (tile.value) {
            1 -> MahjongTiles.`04EastWind`
            2 -> MahjongTiles.`05SouthWind`
            3 -> MahjongTiles.`06WestWind`
            4 -> MahjongTiles.`07NorthWind`
            else -> null
        }
        TileSuit.DRAGON -> when (tile.value) {
            1 -> MahjongTiles.`03RedDragon`
            2 -> MahjongTiles.`02GreenDragon`
            3 -> MahjongTiles.`01WhiteDragon`
            else -> null
        }
        TileSuit.SEASON -> when (tile.value) {
            1 -> MahjongTiles.`35Spring`
            2 -> MahjongTiles.`36Summer`
            3 -> MahjongTiles.`37Autumn`
            4 -> MahjongTiles.`38Winter`
            else -> null
        }
        TileSuit.FLOWER -> when (tile.value) {
            1 -> MahjongTiles.`39Plum`
            2 -> MahjongTiles.`40Orchid`
            3 -> MahjongTiles.`41Chrysanthemum`
            4 -> MahjongTiles.`42Bamboo`
            else -> null
        }
    }

    // ==========================================
    // VECTOR DRAWING RENDERER & CACHE
    // ==========================================
    private sealed interface CachedVectorNode

    private class CachedGroupNode(
        val clipPath: Path?,
        val translationX: Float,
        val translationY: Float,
        val rotation: Float,
        val pivotX: Float,
        val pivotY: Float,
        val scaleX: Float,
        val scaleY: Float,
        val children: List<CachedVectorNode>,
    ) : CachedVectorNode

    private class CachedPathNode(
        val path: Path,
        val fill: Brush?,
        val fillAlpha: Float,
        val stroke: Brush?,
        val strokeAlpha: Float,
        val strokeLineWidth: Float,
        val strokeLineCap: StrokeCap,
        val strokeLineJoin: StrokeJoin,
        val strokeLineMiter: Float,
    ) : CachedVectorNode

    private val vectorCache = ConcurrentHashMap<ImageVector, CachedGroupNode>()

    private fun getOrCreateCachedVector(imageVector: ImageVector): CachedGroupNode {
        return vectorCache.computeIfAbsent(imageVector) { vector ->
            vector.root.toCachedGroup()
        }
    }

    private fun VectorGroup.toCachedGroup(): CachedGroupNode {
        val clipPath = if (clipPathData.isNotEmpty()) {
            clipPathData.toPath()
        } else null
        val childrenNodes = map { node ->
            when (node) {
                is VectorGroup -> node.toCachedGroup()
                is VectorPath -> {
                    val path = node.pathData.toPath()
                    path.fillType = node.pathFillType
                    CachedPathNode(
                        path = path,
                        fill = node.fill,
                        fillAlpha = node.fillAlpha,
                        stroke = node.stroke,
                        strokeAlpha = node.strokeAlpha,
                        strokeLineWidth = node.strokeLineWidth,
                        strokeLineCap = node.strokeLineCap,
                        strokeLineJoin = node.strokeLineJoin,
                        strokeLineMiter = node.strokeLineMiter,
                    )
                }
            }
        }
        return CachedGroupNode(
            clipPath = clipPath,
            translationX = translationX,
            translationY = translationY,
            rotation = rotation,
            pivotX = pivotX,
            pivotY = pivotY,
            scaleX = scaleX,
            scaleY = scaleY,
            children = childrenNodes
        )
    }

    /** Draws a cached vector; with [tint], every fill and stroke is drawn in that one colour. */
    private fun drawCachedNode(scope: DrawScope, node: CachedVectorNode, tint: Color? = null) {
        when (node) {
            is CachedGroupNode -> {
                scope.withTransform(
                    transformBlock = {
                        if ((node.translationX != 0f) || (node.translationY != 0f)) {
                            translate(node.translationX, node.translationY)
                        }
                        if (node.rotation != 0f) {
                            rotate(node.rotation, Offset(node.pivotX, node.pivotY))
                        }
                        if ((node.scaleX != 1f) || (node.scaleY != 1f)) {
                            scale(node.scaleX, node.scaleY, Offset(node.pivotX, node.pivotY))
                        }
                        if (node.clipPath != null) {
                            clipPath(node.clipPath)
                        }
                    },
                    drawBlock = {
                        for (child in node.children) {
                            drawCachedNode(this, child, tint)
                        }
                    }
                )
            }
            is CachedPathNode -> {
                if (node.fill != null) {
                    scope.drawPath(
                        path = node.path,
                        brush = if (tint != null) SolidColor(tint) else node.fill,
                        alpha = node.fillAlpha
                    )
                }
                if (node.stroke != null) {
                    scope.drawPath(
                        path = node.path,
                        brush = if (tint != null) SolidColor(tint) else node.stroke,
                        alpha = node.strokeAlpha,
                        style = Stroke(
                            width = node.strokeLineWidth,
                            cap = node.strokeLineCap,
                            join = node.strokeLineJoin,
                            miter = node.strokeLineMiter
                        )
                    )
                }
            }
        }
    }

    // Free tiles' green, when they are highlighted
    private val FreeTint = Color(0xFF0F6B38)
    private val EngraveShadow = Color(0x5A3A2A18)

    private const val WALL_SHADE_ALPHA = 0.6f
    private const val CONTACT_SHADOW_ALPHA = 0.45f
    private const val CONTACT_BLUR = 0.07f
    // How far past the shape the blur reaches, in blur radii
    private const val CONTACT_PAD = 3f
    private val contactPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        color = android.graphics.Color.argb((CONTACT_SHADOW_ALPHA * 255).roundToInt(), 0, 0, 0)
    }
    private val contactRect = RectF()
    private var contactBitmap: Bitmap? = null
    private var contactWidth = 0f
    private val EngraveLight = Color(0x99FFFDF6)

    private fun drawVectorTile(
        scope: DrawScope,
        imageVector: ImageVector,
        cx: Float,
        cy: Float,
        maxW: Float,
        maxH: Float,
        tint: Color? = null,
    ) {
        val scale = minOf(maxW / imageVector.viewportWidth, maxH / imageVector.viewportHeight)
        val targetW = imageVector.viewportWidth * scale
        val targetH = imageVector.viewportHeight * scale
        val drawX = cx - (targetW * 0.5f)
        val drawY = cy - (targetH * 0.5f)

        val cached = getOrCreateCachedVector(imageVector)
        scope.withTransform(
            transformBlock = {
                translate(drawX, drawY)
                scale(scale, scale, Offset.Zero)
            },
            drawBlock = {
                drawCachedNode(this, cached, tint)
            }
        )
    }
}

/**
 * Where a tile's resting shadow falls under the lamp ([lamp], in flat table pixels), and how dark
 * it is beside the fire ([lampDarkness] of its darkness under the lamp alone). The fire's own long
 * shadows are traced by the shaders (BoardShadowMap).
 */
data class TileShadows(val lamp: Offset, val lampDarkness: Float = 1f) {
    companion object {
        val NONE = TileShadows(Offset.Zero)
    }
}
