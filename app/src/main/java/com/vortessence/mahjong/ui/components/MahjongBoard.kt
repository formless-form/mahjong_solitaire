package com.vortessence.mahjong.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import com.vortessence.mahjong.ui.theme.TutorialHighlight
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.isOutOfBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vortessence.mahjong.data.model.BoardTile
import com.vortessence.mahjong.data.model.TilePosition
import com.vortessence.mahjong.data.model.GameSettings
import com.vortessence.mahjong.data.model.GameState
import com.vortessence.mahjong.data.model.TableSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.milliseconds

private class SmashAnimationItem(
    val id: String,
    val tile1: BoardTile,
    val tile2: BoardTile,
    val animatable: Animatable<Float, AnimationVector1D>,
) {
    // The pair as drawn in flight: standing and free, whatever the board now says of them
    val flying1 = tile1.copy(isRemoved = false, isFree = true)
    val flying2 = tile2.copy(isRemoved = false, isFree = true)

    // Its click and burst at contact, and its flight: an undo cancels both
    var contact: Job? = null
    var flight: Job? = null
}

private data class SmashFrame(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val scale: Float,
    val alpha: Float,
    val impactFraction: Float
)

@Composable
fun MahjongBoard(
    gameState: GameState,
    modifier: Modifier = Modifier,
    settings: GameSettings = GameSettings(),
    highlightedTileIds: Set<Int> = emptySet(),
    // The tutorial points the zoom-out button out, ringed in its blue
    highlightZoomOut: Boolean = false,
    onZoomedInChange: (Boolean) -> Unit = {},
    onTileClick: (Int) -> Unit,
    onTileMatch: () -> Unit = {},
    // How much of the bottom of the board's area lies under something drawn over it (the game
    // toolbar): the table carries on underneath, but the tiles are fitted above it
    bottomOverlap: Dp = 0.dp,
) {
    val density = LocalDensity.current
    val paddingPx = with(density) { 16.dp.toPx() }

    // Blocked shake/flash state
    var blockedFlashId by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(gameState.blockedTileId) {
        val blockedId = gameState.blockedTileId
        if (blockedId != null) {
            blockedFlashId = blockedId
            try {
                delay(350.milliseconds)
            } finally {
                blockedFlashId = null
            }
        } else {
            blockedFlashId = null
        }
    }

    var boardSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = modifier
            .fillMaxSize()
            // A zoomed-in scene must stay within the board's area
            .clipToBounds()
            .onSizeChanged { boardSize = it }
    ) {
        val canvasW = boardSize.width.toFloat()
        // The table is laid out, lit and seen in perspective in the area above the overlap, and
        // simply continues below it
        val canvasH = (boardSize.height - with(density) { bottomOverlap.toPx() }).coerceAtLeast(1f)

        if ((canvasW > 0f) && (canvasH > 0f) && gameState.tiles.isNotEmpty()) {
            key(gameState.constellation.id) {
                BoardContent(
                    gameState = gameState,
                    settings = settings,
                    highlightedTileIds = highlightedTileIds,
                    highlightZoomOut = highlightZoomOut,
                    onZoomedInChange = onZoomedInChange,
                    onTileClick = onTileClick,
                    onTileMatch = onTileMatch,
                    canvasW = canvasW,
                    canvasH = canvasH,
                    paddingPx = paddingPx,
                    density = density,
                    blockedFlashId = blockedFlashId,
                    bottomOverlap = bottomOverlap,
                )
            }
        }
    }
}

@Composable
private fun BoardContent(
    gameState: GameState,
    settings: GameSettings,
    highlightedTileIds: Set<Int>,
    highlightZoomOut: Boolean,
    onZoomedInChange: (Boolean) -> Unit,
    onTileClick: (Int) -> Unit,
    onTileMatch: () -> Unit = {},
    canvasW: Float,
    canvasH: Float,
    paddingPx: Float,
    density: Density,
    blockedFlashId: Int?,
    bottomOverlap: Dp,
) {
    val context = LocalContext.current
    val graphicsLayer = rememberGraphicsLayer()

    // Base transform: unzoomed full layout bounds
    val baseTransform = remember(gameState.tiles.size, canvasW, canvasH, paddingPx) {
        computeBoardTransform(
            tiles = gameState.tiles,
            availableWidth = canvasW,
            availableHeight = canvasH,
            paddingPx = paddingPx
        )
    }

    // Target transform: shrinks/zooms in on remaining active tiles when autoZoomEnabled is true
    val targetTransform = remember(
        gameState.tiles,
        canvasW,
        canvasH,
        paddingPx,
        settings.autoZoomEnabled,
        baseTransform
    ) {
        if (settings.autoZoomEnabled) {
            val activeTiles = gameState.tiles.filter { !it.isRemoved }
            if (activeTiles.isNotEmpty()) {
                computeBoardTransform(
                    tiles = activeTiles,
                    availableWidth = canvasW,
                    availableHeight = canvasH,
                    paddingPx = paddingPx,
                    maxZoomFactor = 1.8f,
                    baseUnitX = baseTransform.unitX
                )
            } else {
                baseTransform
            }
        } else {
            baseTransform
        }
    }

    val zoomAnimSpec = if (settings.animationsEnabled) {
        tween<Float>(durationMillis = 500, easing = FastOutSlowInEasing)
    } else {
        snap()
    }

    val animatedUnitX by animateFloatAsState(
        targetValue = targetTransform.unitX,
        animationSpec = zoomAnimSpec,
        label = "boardUnitX"
    )
    val animatedStartX by animateFloatAsState(
        targetValue = targetTransform.startX,
        animationSpec = zoomAnimSpec,
        label = "boardStartX"
    )
    val animatedStartY by animateFloatAsState(
        targetValue = targetTransform.startY,
        animationSpec = zoomAnimSpec,
        label = "boardStartY"
    )

    val currentTileW = animatedUnitX * 2f
    val currentTileH = currentTileW * BoardTransform.ASPECT_RATIO
    val currentShiftX = currentTileW * BoardTransform.LAYER_SHIFT_FACTOR_X
    val currentShiftY = currentTileH * BoardTransform.LAYER_SHIFT_FACTOR_Y

    val currentTransform = BoardTransform(
        unitX = animatedUnitX,
        tileWidth = currentTileW,
        tileHeight = currentTileH,
        startX = animatedStartX,
        startY = animatedStartY,
        layerShiftX = currentShiftX,
        layerShiftY = currentShiftY,
        depthX = currentShiftX,
        depthY = currentShiftY
    )

    // Matching tiles smash animations, each ending in a small firework burst where they meet
    val matchBursts = remember { MatchBursts() }
    var activeSmashes by remember { mutableStateOf<List<SmashAnimationItem>>(emptyList()) }
    var prevMoveCount by remember { mutableIntStateOf(gameState.moveHistory.size) }

    // Read the latest values inside the gesture detector so it is not restarted on every
    // zoom animation frame (which would drop in-flight taps), and in the match animations, which
    // run on while the board re-fits around what is left
    val latestTiles by rememberUpdatedState(gameState.tiles)
    val latestTransform by rememberUpdatedState(currentTransform)
    val latestOnTileClick by rememberUpdatedState(onTileClick)

    // The smash, the match sound and the burst are launched in the board's own scope, not the
    // effect's: the next match restarts the effect, and must not cut short the one before
    val animationScope = rememberCoroutineScope()
    LaunchedEffect(matchBursts) { matchBursts.run() }
    LaunchedEffect(gameState.moveHistory.size, settings.animationsEnabled) {
        val currentCount = gameState.moveHistory.size
        if (currentCount > prevMoveCount && settings.animationsEnabled) {
            val latestMove = gameState.moveHistory.last()
            val t1 = gameState.tiles.firstOrNull { it.id == latestMove.tileId1 }
            val t2 = gameState.tiles.firstOrNull { it.id == latestMove.tileId2 }
            if (t1 != null && t2 != null) {
                val animId = "${t1.id}_${t2.id}_${System.currentTimeMillis()}"
                val smashItem = SmashAnimationItem(
                    id = animId,
                    tile1 = t1,
                    tile2 = t2,
                    animatable = Animatable(0f)
                )
                activeSmashes += smashItem
                smashItem.contact = animationScope.launch {
                    // Click and burst the moment the raised pair's edges touch, where they meet:
                    // where the board stands by then, as it may be re-fitting around what is left
                    delay((SMASH_MILLIS * smashContactFraction(t1, t2, currentTransform)).toLong().milliseconds)
                    onTileMatch()
                    val transform = latestTransform
                    val lift = with(density) { 16.dp.toPx() }
                    matchBursts.burst(
                        x = (transform.tilePixelX(t1.x, t1.z) + transform.tilePixelX(t2.x, t2.z) + transform.tileWidth) / 2f,
                        y = (transform.tilePixelY(t1.y, t1.z) + transform.tilePixelY(t2.y, t2.z) + transform.tileHeight) / 2f - lift,
                        reach = transform.tileWidth * 1.6f,
                        canvasHeight = canvasH,
                    )
                }
                smashItem.flight = animationScope.launch {
                    smashItem.animatable.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = SMASH_MILLIS, easing = LinearEasing)
                    )
                    activeSmashes = activeSmashes.filter { it.id != animId }
                }
            }
        } else if (currentCount < prevMoveCount) {
            // Undo occurred: cancel in-flight animations so restored tiles display normally, and
            // with them any click and burst still to come
            for (smash in activeSmashes) {
                smash.contact?.cancel()
                smash.flight?.cancel()
            }
            activeSmashes = emptyList()
        }
        prevMoveCount = currentCount
    }

    // Flat table layout -> screen through the tilted camera, and back again for taps
    val planeMatrix = remember(canvasW, canvasH) { TableCamera.planeMatrix(canvasW, canvasH) }
    val screenToPlane = remember(planeMatrix) { android.graphics.Matrix().also { planeMatrix.invert(it) } }
    // As drawn now: the board's area changes height when the toolbar or tutorial card below changes
    val latestScreenToPlane by rememberUpdatedState(screenToPlane)
    // A hint's pair pulses gently in gold for as long as the hint shows (steady with animations
    // off), timed from when it appeared
    val hinting = (gameState.hintPair != null) && settings.animationsEnabled
    var hintShownAt by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(gameState.hintPair) {
        if (gameState.hintPair != null) hintShownAt = withFrameNanos { FrameClock.seconds(it) }
    }
    // Firelight flickers, so the board is redrawn for it, 20 times a second (chosen by eye against
    // 30: the flicker looks the same), and as often for a hint's pulse; the lamp alone never changes
    // The fire goes out with the phone's Remove animations, whatever the setting
    val firelit = settings.fireplaceLit && settings.animationsEnabled
    val ticking = firelit || hinting
    val clockSeconds by produceState(0f, ticking) {
        if (ticking) {
            var last = Long.MIN_VALUE / 2
            val interval = PerfSwitches.fireFps.takeIf { it > 0 }?.let { 1_000_000_000L / it - 2_000_000L } ?: FIRE_FRAME_NANOS
            while (true) withFrameNanos { now ->
                if (now - last >= interval) {
                    last = now
                    value = FrameClock.seconds(now)
                }
            }
        }
    }
    // The board's ambient occlusion and the fire's long shadows, worked out only when tiles are
    // removed (or the screen changes size): the first at once, later ones off the main thread,
    // with the old map shown meanwhile. When the board scales or moves, the map is placed under
    // it as it is now, so the shading keeps up every frame without being rebuilt.
    val tracedShadows = firelit
    // Where neighbours on a layer stand against each other's sides, which are shaded there
    val seams = remember(gameState.tiles) { seams(gameState.tiles) }
    // The highest layer still standing, from which lower layers are shaded darker
    val topLayer = remember(gameState.tiles) { gameState.tiles.filter { !it.isRemoved }.maxOfOrNull { it.z } ?: 0 }
    val shadowMap by produceState<BoardShadowMap?>(null, gameState.tiles, canvasW, canvasH) {
        val layout = currentTransform
        value = if (value == null) {
            BoardShadowMap.build(gameState.tiles, layout, canvasW, canvasH)
        } else {
            withContext(Dispatchers.Default) { BoardShadowMap.build(gameState.tiles, layout, canvasW, canvasH) }
        }
    }
    val placedShadows = shadowMap?.placed(currentTransform, canvasH)
    // The map is of the tiles as drawn (not still being remade after a match or an undo), so
    // the shaders may skip what lies under the tiles
    val shadowsFresh = (shadowMap?.tiles === gameState.tiles) && !PerfSwitches.isOff("cullunder")
    // Or only tiles taken away since the map was made, none put back (as just after a match, while
    // the new map is made): where the map has no tile there is none now either, so the tiles'
    // light can still skip the rest of their layer, though the table can't skip what was under them
    val tilesWithinMap = remember(shadowMap, gameState.tiles) {
        val then = shadowMap?.tiles
        (then != null) && (then.size == gameState.tiles.size) && gameState.tiles.indices.all { i ->
            val tile = gameState.tiles[i]
            (tile.id == then[i].id) && (tile.position == then[i].position) && (tile.isRemoved || !then[i].isRemoved)
        }
    }
    val tilesCulled = shadowsFresh || (tilesWithinMap && !PerfSwitches.isOff("cullunder"))
    val tableAlbedo = remember(graphicsLayer) { TableSurfaceRenderer.AlbedoCache(graphicsLayer) }
    // Only what the player can see is drawn: tiles wholly covered by others are left out
    val visibleTiles = remember(gameState.tiles) {
        if (PerfSwitches.isOff("cull")) gameState.tiles.filter { !it.isRemoved }.sortedWith(TileDrawOrder)
        else TileCulling.visibleInDrawOrder(gameState.tiles)
    }
    // Pinch-to-zoom: a camera zoom over the whole scene (table, tiles and lamp light alike), with
    // screen = scene * zoom + pan. Panning stops at the scene's edges, so the table always fills
    // the view.
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val zoomedIn by remember { derivedStateOf { zoom > 1.01f } }
    // The view has held still a moment (no pinch, pan or spring back): the table's colour is then
    // drawn afresh for it, at its sharpest, rather than following it
    var viewSettled by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        snapshotFlow { zoom to pan }.collectLatest {
            viewSettled = false
            delay(VIEW_SETTLE_MILLIS.milliseconds)
            viewSettled = true
        }
    }
    // For the layers drawn each frame
    val layerPaint = remember { Paint() }
    // The slate's surface map, which way each spot of it faces, so the lamp and the fire catch
    // its ridges: worked out on the GPU for the view once it settles (as the table's colour is
    // drawn afresh), and kept, still placed right, while the view moves
    val surfaceLayer = rememberGraphicsLayer()
    var slateSurface by remember { mutableStateOf<TableSurfaceRenderer.SlateSurface?>(null) }
    val layoutDirection = LocalLayoutDirection.current
    val boardHeight = canvasH + with(density) { bottomOverlap.toPx() }
    LaunchedEffect(settings.tableSurface, canvasW, boardHeight, viewSettled) {
        if (settings.tableSurface != TableSurface.SLATE) {
            slateSurface = null
            return@LaunchedEffect
        }
        if (!viewSettled && (slateSurface != null)) return@LaunchedEffect
        TableSurfaceRenderer.bakeSlateSurface(
            surfaceLayer, density, layoutDirection, IntSize(canvasW.roundToInt(), boardHeight.roundToInt()),
            canvasW, canvasH, zoom, pan,
        )?.let { slateSurface = it }
    }
    val tapAt = { offset: Offset ->
        // Back through the zoom and the camera to the flat layout
        val p = floatArrayOf((offset.x - pan.x) / zoom, (offset.y - pan.y) / zoom)
        latestScreenToPlane.mapPoints(p)
        val tappedId = findTappedTileId(p[0], p[1], latestTiles, latestTransform)
        if (tappedId != null) {
            latestOnTileClick(tappedId)
        }
    }
    val reportZoom by rememberUpdatedState(onZoomedInChange)
    LaunchedEffect(zoomedIn) { reportZoom(zoomedIn) }
    val scope = rememberCoroutineScope()
    var zoomOutJob by remember { mutableStateOf<Job?>(null) }
    val clampPan = { p: Offset, z: Float ->
        Offset(p.x.coerceIn(canvasW * (1f - z), 0f), p.y.coerceIn(canvasH * (1f - z), 0f))
    }
    val latestClampPan by rememberUpdatedState(clampPan)
    val zoomOut = {
        val fromZoom = zoom
        val fromPan = pan
        zoomOutJob?.cancel()
        zoomOutJob = scope.launch {
            animate(
                0f, 1f,
                animationSpec = if (settings.animationsEnabled) {
                    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                } else {
                    snap()
                },
            ) { f, _ ->
                zoom = fromZoom + (1f - fromZoom) * f
                pan = fromPan * (1f - f)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                // Every finger is a tap of its own, so two thumbs can take a pair at once (the
                // standard tap detector keeps only the last finger up)
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val pressed = mutableSetOf(down.id)
                    while (pressed.isNotEmpty()) {
                        val event = awaitPointerEvent()
                        for (change in event.changes) {
                            if (change.changedToDownIgnoreConsumed()) {
                                pressed.add(change.id)
                            } else if (change.id in pressed) {
                                // A pinch or a drag past the touch slop is consumed by the
                                // transform detector, and isn't a tap
                                if (change.isConsumed || change.isOutOfBounds(size, extendedTouchPadding)) {
                                    pressed.remove(change.id)
                                } else if (change.changedToUpIgnoreConsumed()) {
                                    pressed.remove(change.id)
                                    tapAt(change.position)
                                }
                            }
                        }
                        if (event.changes.none { it.pressed }) break
                    }
                }
            }
            // Inside the tap detector, so a pinch or a drag past the touch slop cancels the tap.
            // Keyed on nothing, so a change of size (the tutorial card) doesn't drop a pinch
            .pointerInput(Unit) {
                detectTransformGestures { centroid, panChange, zoomChange, _ ->
                    zoomOutJob?.cancel()
                    val newZoom = (zoom * zoomChange).coerceIn(1f, TableCamera.MAX_ZOOM)
                    // Keep the spot under the fingers where it is, then follow the fingers
                    val scenePoint = (centroid - pan) / zoom
                    pan = latestClampPan(centroid - scenePoint * newZoom + panChange, newZoom)
                    zoom = newZoom
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // The table's colour never flickers: drawn once, in screen pixels, and kept until the
            // zoom, pan, size or surface changes (a pinch or pan is followed by scaling it, and it
            // is drawn afresh once the view settles)
            if (!PerfSwitches.isOff("table")) TableSurfaceRenderer.drawTableAlbedo(
                this, tableAlbedo, settings.tableSurface, canvasW, canvasH, context, zoom, pan, viewSettled,
            )
            drawContext.canvas.save()
            drawContext.canvas.translate(pan.x, pan.y)
            drawContext.canvas.scale(zoom, zoom)

            // On a table wider than tall (played on its side) the hearth is beyond the far edge
            val flame = FlameLight.at(clockSeconds, settings.lampBrightness, firelit, settings.fireIntensity, hearthAbove = canvasW > canvasH)
            val hintStrength = if (hinting) hintPulse(FrameClock.since(clockSeconds, hintShownAt)) else 1f
            if (!PerfSwitches.isOff("table")) TableSurfaceRenderer.drawTableLight(
                scope = this,
                surface = settings.tableSurface,
                width = canvasW,
                height = canvasH,
                zoom = zoom,
                pan = pan,
                flame = flame,
                shadowMap = placedShadows,
                fireShadows = tracedShadows,
                cullUnder = shadowsFresh,
                slateSurface = slateSurface,
            )
            TileRenderer.TileAtlas.screenScale = zoom * TableCamera.nearEdgeScale()

            val sortedTiles = visibleTiles

            val nativeCanvas = drawContext.canvas.nativeCanvas
            nativeCanvas.save()
            nativeCanvas.concat(planeMatrix)
            // One layer for everything on the table, so the lamp light can be applied to it whole
            drawContext.canvas.saveLayer(
                Rect(-canvasW * 0.25f, -canvasH * 0.25f, canvasW * 1.25f, canvasH * 1.25f),
                layerPaint,
            )
            var layerShaded = 0
            if (!PerfSwitches.isOff("tiles")) for (tile in sortedTiles) {
                // On reaching each raised layer, first lay all its contact shadows on the layer
                // below; its own tiles then cover any that reach a neighbour of the same height
                if (tile.z > layerShaded) {
                    layerShaded = tile.z
                    for (above in sortedTiles) {
                        if (above.z != tile.z) continue
                        TileRenderer.drawContactShadow(
                            this,
                            currentTransform.tilePixelX(above.x, above.z),
                            currentTransform.tilePixelY(above.y, above.z),
                            currentTransform.tileWidth,
                            currentTransform.tileHeight,
                        )
                    }
                }
                val px = currentTransform.tilePixelX(tile.x, tile.z)
                val py = currentTransform.tilePixelY(tile.y, tile.z)
                val isSelected = tile.id == gameState.selectedTileId
                val isHinted = gameState.hintPair?.let { it.first == tile.id || it.second == tile.id } ?: false
                val isBlockedFlash = (blockedFlashId != null) && (tile.id == blockedFlashId)

                TileRenderer.drawTile(
                    scope = this,
                    boardTile = tile,
                    x = px,
                    y = py,
                    width = currentTransform.tileWidth,
                    height = currentTransform.tileHeight,
                    isSelected = isSelected,
                    isHinted = isHinted,
                    isBlockedHighlight = isBlockedFlash,
                    isTutorialHighlight = tile.id in highlightedTileIds,
                    highlightFreeTiles = settings.highlightFreeTiles,
                    glare = tileGlare(px + currentTransform.tileWidth / 2f, py + currentTransform.tileHeight / 2f, canvasW, canvasH, flame),
                    shadows = tileShadows(
                        px + currentTransform.tileWidth / 2f, py + currentTransform.tileHeight / 2f, tile.z,
                        currentTransform.tileWidth, canvasW, canvasH, flame,
                    ),
                    hintStrength = hintStrength,
                    layerShade = ((topLayer - tile.z) * LAYER_SHADE).coerceAtMost(LAYER_SHADE_MAX),
                    seams = seams[tile.id],
                    rightAway = sideAway(px + currentTransform.tileWidth, py + currentTransform.tileHeight / 2f, canvasW, canvasH, flame, 1f, 0f),
                    belowAway = sideAway(px + currentTransform.tileWidth / 2f, py + currentTransform.tileHeight, canvasW, canvasH, flame, 0f, -1f),
                )
            }

            // Lamp and firelight: multiplies everything drawn on the table (alpha untouched), full and
            // warm under the lamp, falling off towards the dark edges
            if (!PerfSwitches.isOff("tilelight")) drawRect(
                brush = TableSurfaceRenderer.tileLight(canvasW, canvasH, flame, placedShadows, tracedShadows, fresh = tilesCulled),
                topLeft = Offset(-canvasW * 0.25f, -canvasH * 0.25f),
                size = Size(canvasW * 1.5f, canvasH * 1.5f),
                blendMode = BlendMode.Modulate,
            )

            // Matching pairs being raised, smashed into each other and disappearing: on a small
            // layer of their own over the lit board, lit by the lamp and flame but not shaded by
            // the board's shadows, which would show the tiles beneath through them
            if (activeSmashes.isNotEmpty()) {
                val area = smashBounds(activeSmashes, currentTransform, density)
                drawContext.canvas.saveLayer(area, layerPaint)
                for (smash in activeSmashes) {
                    drawSmashAnimation(
                        smash = smash,
                        transform = currentTransform,
                        density = density,
                    )
                }
                if (!PerfSwitches.isOff("tilelight")) drawRect(
                    brush = TableSurfaceRenderer.raisedTileLight(canvasW, canvasH, flame),
                    topLeft = area.topLeft,
                    size = area.size,
                    blendMode = BlendMode.Modulate,
                )
                drawContext.canvas.restore()
            }
            drawContext.canvas.restore()
            nativeCanvas.restore()
            drawContext.canvas.restore()
        }

        // Match bursts on a layer of their own, so a burst redraws only itself, never the board.
        // Firework light is its own, so it goes on after the lamp's falloff
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (matchBursts.frame == 0L || matchBursts.idle) return@Canvas
            val nativeCanvas = drawContext.canvas.nativeCanvas
            nativeCanvas.save()
            nativeCanvas.translate(pan.x, pan.y)
            nativeCanvas.scale(zoom, zoom)
            nativeCanvas.concat(planeMatrix)
            matchBursts.draw(this, canvasH)
            nativeCanvas.restore()
        }

        // Once zoomed in, one tap at the edge of the screen goes back to the whole board
        AnimatedVisibility(
            visible = zoomedIn,
            enter = fadeIn() + scaleIn(initialScale = 0.6f),
            exit = fadeOut() + scaleOut(targetScale = 0.6f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp + bottomOverlap),
        ) {
            FilledTonalIconButton(
                onClick = zoomOut,
                shapes = IconButtonDefaults.shapes(),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                modifier = Modifier
                    .size(56.dp)
                    .then(if (highlightZoomOut) Modifier.tutorialRing(settings.animationsEnabled) else Modifier),
            ) {
                Icon(Icons.Rounded.ZoomOut, contentDescription = "Zoom out to the whole board", modifier = Modifier.size(28.dp))
            }
        }
    }
}

/**
 * The tutorial's pointer for a button: a ring in its sky blue around it, gently pulsing (steady
 * with animations off).
 */
@Composable
private fun Modifier.tutorialRing(animate: Boolean): Modifier {
    val pulse = if (animate) {
        rememberInfiniteTransition(label = "tutorialRing").animateFloat(
            initialValue = 0.6f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "tutorialRingAlpha",
        ).value
    } else {
        1f
    }
    return border(4.dp, TutorialHighlight.copy(alpha = pulse), CircleShape)
}

/**
 * The lamp's shadow, cast away from it as a point light casts it: a tile's top, standing zh
 * above whatever it rests on, throws its shadow (centre - light) * zh / (lightHeight - zh). A
 * raised tile's shadow mostly lands on the tiles beneath it, one tile's height down, so every
 * tile casts as one tile tall. The lamp hangs high and to one side, so its shadows lean away from
 * it, a little more for tiles further from it.
 *
 * It is as dark as the lamp's share of all the light reaching the tile: beside the hearth the
 * fire fills it in. It holds still, weighed against the fire as it burns on average, so it
 * changes only with the two sliders. The fire's own long shadows are traced by the shaders
 * ([BoardShadowMap]).
 */
private fun tileShadows(cx: Float, cy: Float, z: Int, tileW: Float, w: Float, h: Float, flame: FlameLight): TileShadows {
    // A tile stands about an eighth of its width tall; the light's height counts from the
    // surface the tile rests on
    val top = tileW * 0.12f
    val base = z * tileW * 0.12f
    fun cast(lx: Float, ly: Float, lightHeight: Float, cap: Float): Offset {
        val k = top / (lightHeight - base - top).coerceAtLeast(top)
        val s = Offset((cx - lx) * k, (cy - ly) * k)
        val len = s.getDistance()
        return if (len > cap) s * (cap / len) else s
    }
    val stretch = TableSurfaceRenderer.lampStretch(w, h)
    val lamp = cast(w / 2f + LAMP_U * stretch * h, h / 2f - LAMP_V * h, LAMP_HEIGHT * h, tileW * 0.35f)
    if (flame.power <= 0f) return TileShadows(lamp)
    // The light reaching this tile from each, as the shaders light it (the fire by its brightness)
    val u = (cx - w / 2f) / h
    val v = (h / 2f - cy) / h
    val lampHere = flame.lamp * TableSurfaceRenderer.lampLight(u, v, stretch)
    fun fireShare(fu: Float, fv: Float, power: Float): Float {
        val du = u - fu
        val dv = v - fv
        val fireHere = power / (1f + (du * du + dv * dv) / flame.reach) * 0.6f // the fire's brightness
        return fireHere / (fireHere + lampHere)
    }
    val steady = fireShare(flame.steadyU, flame.steadyV, flame.steadyPower)
    // The lamp's resting shadow is partly the tile's own contact shadow, which no light fills
    return TileShadows(lamp, lampDarkness = 1f - 0.6f * steady)
}

/** How strongly a tile's glossy face reflects the lamp and the fire, by its centre in flat table pixels. */
private fun tileGlare(cx: Float, cy: Float, w: Float, h: Float, flame: FlameLight): Float {
    val u = (cx - w / 2f) / h
    val v = (h / 2f - cy) / h
    return TableSurfaceRenderer.flameGlare(u, v, flame, TableSurfaceRenderer.lampStretch(w, h))
}

/**
 * For each standing tile, the stretches of its right and lower sides that stand against a
 * standing neighbour on its layer: (side, from, to) triples for TileRenderer, from and to along the
 * side from 0 to 1. A neighbour half a row off shares only part of a side; a matched-away one
 * shares none. (Left and upper sides aren't seen: the camera looks down at the right and lower.)
 */
private fun seams(tiles: List<BoardTile>): Map<Int, FloatArray> {
    val out = HashMap<Int, FloatArray>()
    for ((_, layer) in tiles.filter { !it.isRemoved }.groupBy { it.z }) {
        for (a in layer) {
            val right = ArrayList<ClosedFloatingPointRange<Float>>()
            val below = ArrayList<ClosedFloatingPointRange<Float>>()
            for (b in layer) {
                if (b === a) continue
                val dx = b.x - a.x
                val dy = b.y - a.y
                if (abs(dx - 2f) < 0.15f && abs(dy) < 2f) right += max(0f, dy) / 2f..min(2f, dy + 2f) / 2f
                else if (abs(dy - 2f) < 0.15f && abs(dx) < 2f) below += max(0f, dx) / 2f..min(2f, dx + 2f) / 2f
            }
            val spans = ArrayList<Float>()
            for ((side, stretches) in listOf(TileRenderer.SEAM_RIGHT to right, TileRenderer.SEAM_BOTTOM to below)) {
                // Stretches that meet (two neighbours half a row off) run on as one, so the shade
                // fades only where the side truly opens
                for (run in merged(stretches)) {
                    spans += side.toFloat()
                    spans += run.start
                    spans += run.endInclusive
                }
            }
            if (spans.isNotEmpty()) out[a.id] = spans.toFloatArray()
        }
    }
    return out
}

private fun merged(stretches: List<ClosedFloatingPointRange<Float>>): List<ClosedFloatingPointRange<Float>> {
    val runs = ArrayList<ClosedFloatingPointRange<Float>>()
    for (s in stretches.sortedBy { it.start }) {
        val last = runs.lastOrNull()
        if (last != null && s.start <= last.endInclusive + 0.01f) runs[runs.size - 1] = last.start..max(last.endInclusive, s.endInclusive)
        else runs += s
    }
    return runs
}

// How much darker each layer below the top is drawn, and the most any is, so layers read apart
// by tone without the lowest going murky
private const val LAYER_SHADE = 0.05f
private const val LAYER_SHADE_MAX = 0.21f

/**
 * How much darker a tile's side is for facing away from the light reaching it: the side at flat
 * table pixel ([sx], [sy]) faces direction ([nu], [nv]) across the table (u right, v up the
 * table). The light is the lamp's and the fire's, each weighed by how brightly it reaches there
 * (as the shaders light the table). A side facing a light keeps its brightness, one it skims is a
 * little darker, one facing away clearly so, up to [SIDE_AWAY].
 */
private fun sideAway(sx: Float, sy: Float, w: Float, h: Float, flame: FlameLight, nu: Float, nv: Float): Float {
    val u = (sx - w / 2f) / h
    val v = (h / 2f - sy) / h
    val stretch = TableSurfaceRenderer.lampStretch(w, h)
    fun facing(lu: Float, lv: Float, lh: Float): Float {
        val du = lu - u
        val dv = lv - v
        val length = sqrt(du * du + dv * dv + lh * lh)
        return (du * nu + dv * nv) / length
    }
    val lampWeight = flame.lamp * TableSurfaceRenderer.lampLight(u, v, stretch)
    val lampFacing = facing(LAMP_U * stretch, LAMP_V, LAMP_HEIGHT)
    var weight = lampWeight
    var facing = lampWeight * lampFacing
    if (flame.power > 0f) {
        val du = u - flame.u
        val dv = v - flame.v
        val fireWeight = flame.power / (1f + (du * du + dv * dv) / flame.reach) * 0.6f
        weight += fireWeight
        facing += fireWeight * facing(flame.u, flame.v, flame.height)
    }
    if (weight <= 0f) return 0f
    return SIDE_AWAY * (0.5f - 1.5f * facing / weight).coerceIn(0f, 1f)
}

// The darkest a side facing away from the light is drawn
private const val SIDE_AWAY = 0.35f

private const val SMASH_MILLIS = 840

// A hint's pulse: one slow swell and fade of its gold every HINT_PULSE_SECONDS, never fainter than
// HINT_PULSE_LOW of its full strength
private const val HINT_PULSE_SECONDS = 1.6f
private const val HINT_PULSE_LOW = 0.35f

/** How strongly a hinted pair shows its gold, [seconds] after the hint appeared: full at first. */
private fun hintPulse(seconds: Float): Float {
    val wave = 0.5f + 0.5f * cos(2f * PI.toFloat() * seconds.coerceAtLeast(0f) / HINT_PULSE_SECONDS)
    return HINT_PULSE_LOW + (1f - HINT_PULSE_LOW) * wave
}

// How long the view must hold still before the table's colour is drawn afresh for it
private const val VIEW_SETTLE_MILLIS = 150

// The smash's phases, as fractions of its progress: until RUSH_END the pair rises and rushes
// together in one movement, starting slowly and gathering speed all the way to the clash; then it
// shrinks into the burst and is gone
private const val RUSH_END = 0.70f

// Along that movement (its distance travelled, 0..1), the pair has risen fully by LIFT_END and
// starts drawing together at RUSH_START
private const val LIFT_END = 0.17f
private const val RUSH_START = 0.05f

// The pair's size at the clash, from rising and arching in, as if nearer the eye
private const val PEAK_SCALE = 1.4f

// How sharply that movement gathers speed: its distance grows with time to this power
private const val EASE_IN_POWER = 3.5f

// The firelight's frame interval: 20 a second, less a little so a 60 or 120 Hz screen's frames
// land on it (every 3rd or 6th)
private const val FIRE_FRAME_NANOS = 1_000_000_000L / 20 - 2_000_000L

/**
 * The point in the smash (0..1 of [drawSmashAnimation]'s progress) when the two raised tiles,
 * rushing together, first touch edge to edge. Never before the rush is half done, so even a pair
 * that starts side by side visibly collides.
 */
private fun smashContactFraction(t1: BoardTile, t2: BoardTile, transform: BoardTransform): Float {
    val dx = abs(transform.tilePixelX(t1.x, t1.z) - transform.tilePixelX(t2.x, t2.z))
    val dy = abs(transform.tilePixelY(t1.y, t1.z) - transform.tilePixelY(t2.y, t2.z))
    // The rectangles touch once both gaps are within a raised tile's width and height (at about
    // its full size by then)
    val apart = minOf(
        if (dx > 0f) PEAK_SCALE * transform.tileWidth / dx else 1f,
        if (dy > 0f) PEAK_SCALE * transform.tileHeight / dy else 1f,
    ).coerceIn(0f, 1f)
    // The rush closes the gap by its share of the way squared (see drawSmashAnimation)
    val share = sqrt(1f - apart).coerceAtLeast(0.5f)
    return RUSH_END * (RUSH_START + (1f - RUSH_START) * share).pow(1f / EASE_IN_POWER)
}

/** The area the smashes in flight can reach: both tiles, raised and enlarged, their glow and shadows. */
private fun smashBounds(smashes: List<SmashAnimationItem>, transform: BoardTransform, density: Density): Rect {
    val pad = transform.tileWidth * 0.6f + with(density) { 24.dp.toPx() }
    var left = Float.MAX_VALUE
    var top = Float.MAX_VALUE
    var right = -Float.MAX_VALUE
    var bottom = -Float.MAX_VALUE
    for (smash in smashes) for (k in 0..1) {
        val tile = if (k == 0) smash.tile1 else smash.tile2
        val x = transform.tilePixelX(tile.x, tile.z)
        val y = transform.tilePixelY(tile.y, tile.z)
        left = minOf(left, x)
        top = minOf(top, y)
        right = maxOf(right, x + transform.tileWidth)
        bottom = maxOf(bottom, y + transform.tileHeight)
    }
    return Rect(left - pad, top - pad, right + pad, bottom + pad)
}

private fun DrawScope.drawSmashAnimation(
    smash: SmashAnimationItem,
    transform: BoardTransform,
    density: Density
) {
    val p = smash.animatable.value
    val t1 = smash.tile1
    val t2 = smash.tile2

    val start1X = transform.tilePixelX(t1.x, t1.z)
    val start1Y = transform.tilePixelY(t1.y, t1.z)
    val start2X = transform.tilePixelX(t2.x, t2.z)
    val start2Y = transform.tilePixelY(t2.y, t2.z)

    val midX = (start1X + start2X) / 2f
    val midY = (start1Y + start2Y) / 2f

    val maxLift = with(density) { 16.dp.toPx() }

    // One movement from the first lift to the clash: the distance travelled grows with time to
    // the power 3.5, so it starts very slowly and keeps accelerating. Along it the pair rises first
    // (most of its height early on), and draws together ever faster once it is up
    val travelled = (p / RUSH_END).coerceIn(0f, 1f).pow(EASE_IN_POWER)
    val liftT = (travelled / LIFT_END).coerceIn(0f, 1f)
    val liftFraction = 1f - (1f - liftT) * (1f - liftT)
    val liftY = -maxLift * liftFraction
    val share = ((travelled - RUSH_START) / (1f - RUSH_START)).coerceIn(0f, 1f)
    val rushEased = share * share
    // How high the pair seems: it rises, then keeps growing as it arches in towards the clash,
    // its shadow falling further away, as if still gaining height
    val height = 0.6f * liftFraction + 0.4f * rushEased

    val frame = if (p < RUSH_END) {
        SmashFrame(
            x1 = start1X + (midX - start1X) * rushEased,
            y1 = start1Y + (midY - start1Y) * rushEased + liftY,
            x2 = start2X + (midX - start2X) * rushEased,
            y2 = start2Y + (midY - start2Y) * rushEased + liftY,
            scale = 1f + (PEAK_SCALE - 1f) * height,
            alpha = 1f,
            impactFraction = 0f
        )
    } else {
        // Shrinking into the burst, solid until it is half gone, then fading out
        val impact = (p - RUSH_END) / (1f - RUSH_END)
        SmashFrame(
            x1 = midX,
            y1 = midY + liftY,
            x2 = midX,
            y2 = midY + liftY,
            scale = PEAK_SCALE * (1f - impact * 0.7f),
            alpha = (2f - 2f * impact).coerceIn(0f, 1f),
            impactFraction = impact
        )
    }

    // 1. Raised shadow cast on table plane during lift and rush
    if (frame.alpha > 0.05f && p < RUSH_END) {
        val shadowAlpha = 0.35f * liftFraction
        val shadowCorner = CornerRadius(transform.tileWidth * 0.085f)
        val shadowW = transform.tileWidth * frame.scale
        val shadowH = transform.tileHeight * frame.scale
        val shadowOffset = with(density) { (6.dp + 16.dp * height).toPx() }

        drawRoundRect(
            color = Color.Black.copy(alpha = shadowAlpha),
            topLeft = Offset(frame.x1 + shadowOffset, frame.y1 - liftY + shadowOffset * 0.7f),
            size = Size(shadowW, shadowH),
            cornerRadius = shadowCorner
        )
        drawRoundRect(
            color = Color.Black.copy(alpha = shadowAlpha),
            topLeft = Offset(frame.x2 + shadowOffset, frame.y2 - liftY + shadowOffset * 0.7f),
            size = Size(shadowW, shadowH),
            cornerRadius = shadowCorner
        )
    }

    // 2. Draw colliding raised tiles: the lower first (then the one further left, then further
    // up); fading out, both together on one layer
    if (frame.alpha > 0.01f) {
        val fading = frame.alpha < 0.99f
        if (fading) {
            // They have met by now: one tile's size around them reaches past their glow
            val w = transform.tileWidth
            val h = transform.tileHeight
            fadePaint.alpha = frame.alpha
            drawContext.canvas.saveLayer(Rect(frame.x1 - w, frame.y1 - h, frame.x1 + 2f * w, frame.y1 + 2f * h), fadePaint)
        }
        val oneFirst = (t1.z < t2.z) || ((t1.z == t2.z) &&
            ((frame.x1 < frame.x2) || ((frame.x1 == frame.x2) && (frame.y1 <= frame.y2))))
        if (oneFirst) {
            drawSingleAnimatingTile(smash.flying1, frame.x1, frame.y1, frame.scale, transform)
            drawSingleAnimatingTile(smash.flying2, frame.x2, frame.y2, frame.scale, transform)
        } else {
            drawSingleAnimatingTile(smash.flying2, frame.x2, frame.y2, frame.scale, transform)
            drawSingleAnimatingTile(smash.flying1, frame.x1, frame.y1, frame.scale, transform)
        }
        if (fading) drawContext.canvas.restore()
    }
}

private val fadePaint = Paint()

private fun DrawScope.drawSingleAnimatingTile(
    tile: BoardTile,
    posX: Float,
    posY: Float,
    scale: Float,
    transform: BoardTransform,
) {
    val pivot = Offset(posX + transform.tileWidth / 2f, posY + transform.tileHeight / 2f)
    withTransform({
        scale(scale, scale, pivot = pivot)
    }) {
        // Shape by shape, as the pair grows larger than the atlas's images
        TileRenderer.drawTile(
            scope = this,
            boardTile = tile,
            x = posX,
            y = posY,
            width = transform.tileWidth,
            height = transform.tileHeight,
            isSelected = true,
            live = true,
        )
    }
}

/**
 * Geometric transformation calculating exact tile positions and sizes
 * to guarantee that the constellation fits in the viewport.
 */
data class BoardTransform(
    val unitX: Float,
    val tileWidth: Float,
    val tileHeight: Float,
    val startX: Float,
    val startY: Float,
    val layerShiftX: Float,
    val layerShiftY: Float,
    val depthX: Float,
    val depthY: Float
) {
    fun tilePixelX(gridX: Float, layerZ: Int): Float {
        return startX + gridX * unitX - layerZ * layerShiftX
    }

    fun tilePixelY(gridY: Float, layerZ: Int): Float {
        return startY + gridY * (unitX * ASPECT_RATIO) - layerZ * layerShiftY
    }

    companion object {
        const val ASPECT_RATIO = 1.333f // Mahjong tile ratio (width : height = 3 : 4)
        // The camera looks slightly up the table, so each tile's thickness shows mostly on its
        // front (lower) edge and only a sliver on its right; stacked tiles shift the same way
        const val LAYER_SHIFT_FACTOR_X = 0.03f
        const val LAYER_SHIFT_FACTOR_Y = 0.13f
    }
}

/**
 * Where tiles reach in the board's own units (a tile is 2 wide and 2 x [BoardTransform.ASPECT_RATIO]
 * tall), each layer shifted up and to the left as it's drawn; a 10-unit square when there are none.
 */
internal class BoardBounds(val minX: Float, val maxX: Float, val minY: Float, val maxY: Float) {
    val width get() = max(1f, maxX - minX)
    val height get() = max(1f, maxY - minY)

    companion object {
        fun of(positions: Iterable<TilePosition>): BoardBounds {
            val aspectRatio = BoardTransform.ASPECT_RATIO
            val shiftX = 2f * BoardTransform.LAYER_SHIFT_FACTOR_X
            val shiftY = 2f * aspectRatio * BoardTransform.LAYER_SHIFT_FACTOR_Y
            var minX = Float.MAX_VALUE
            var maxX = -Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxY = -Float.MAX_VALUE
            for (p in positions) {
                minX = min(minX, p.x - p.z * shiftX)
                maxX = max(maxX, p.x + 2f - p.z * shiftX + shiftX)
                minY = min(minY, p.y * aspectRatio - p.z * shiftY)
                maxY = max(maxY, (p.y + 2f) * aspectRatio - p.z * shiftY + shiftY)
            }
            return if (minX == Float.MAX_VALUE) BoardBounds(0f, 10f, 0f, 10f) else BoardBounds(minX, maxX, minY, maxY)
        }
    }
}

internal fun computeBoardTransform(
    tiles: List<BoardTile>,
    availableWidth: Float,
    availableHeight: Float,
    paddingPx: Float,
    maxZoomFactor: Float = 1f,
    baseUnitX: Float = 0f,
    widthSqueeze: Float = TableCamera.nearEdgeScale(),
): BoardTransform {
    val aspectRatio = BoardTransform.ASPECT_RATIO

    val bounds = BoardBounds.of(tiles.map { it.position })
    val minNormX = bounds.minX
    val minNormY = bounds.minY
    val totalNormW = bounds.width
    val totalNormH = bounds.height

    val contentW = max(10f, (availableWidth - paddingPx * 2f) / widthSqueeze)
    val contentH = max(10f, availableHeight - paddingPx * 2f)
    val paddingX = (availableWidth - contentW) * 0.5f

    var unitX = min(contentW / totalNormW, contentH / totalNormH)
    if (baseUnitX > 0f && maxZoomFactor > 1f) {
        unitX = min(unitX, baseUnitX * maxZoomFactor)
    }

    val tileW = unitX * 2f
    val tileH = tileW * aspectRatio

    val layerShiftX = tileW * BoardTransform.LAYER_SHIFT_FACTOR_X
    val layerShiftY = tileH * BoardTransform.LAYER_SHIFT_FACTOR_Y

    val boardPixelW = totalNormW * unitX
    val boardPixelH = totalNormH * unitX

    val startX = paddingX + (contentW - boardPixelW) * 0.5f - minNormX * unitX
    val startY = paddingPx + (contentH - boardPixelH) * 0.5f - minNormY * unitX

    return BoardTransform(
        unitX = unitX,
        tileWidth = tileW,
        tileHeight = tileH,
        startX = startX,
        startY = startY,
        layerShiftX = layerShiftX,
        layerShiftY = layerShiftY,
        depthX = layerShiftX,
        depthY = layerShiftY
    )
}

/**
 * Maps screen touch coordinate to top-most active tile.
 */
private fun findTappedTileId(
    touchX: Float,
    touchY: Float,
    tiles: List<BoardTile>,
    transform: BoardTransform
): Int? {
    // Test in reverse draw order (top-most visible tile first)
    val sorted = tiles
        .filter { !it.isRemoved }
        .sortedWith(TileDrawOrder.reversed())

    for (tile in sorted) {
        val px = transform.tilePixelX(tile.x, tile.z)
        val py = transform.tilePixelY(tile.y, tile.z)
        val right = px + transform.tileWidth + transform.depthX
        val bottom = py + transform.tileHeight + transform.depthY

        if (touchX in px..right && touchY in py..bottom) {
            return tile.id
        }
    }
    return null
}
