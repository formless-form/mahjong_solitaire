package com.vortessence.mahjong.ui.components

import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.size
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Shown over the board when it is cleared: a fireworks light show, then a quiet card with a warm
 * message and big buttons, over the board blurred behind it. With animations off, the card appears straight away and there are no
 * fireworks.
 */
@Composable
fun WinCelebration(
    message: String?,
    subtitle: String,
    hasNextLevel: Boolean,
    animationsEnabled: Boolean,
    onNextLevel: () -> Unit,
    onPlayAgain: () -> Unit,
    onMainMenu: () -> Unit,
    // Where the last button goes: the title, or for Today's Puzzle its calendar
    menuLabel: String = "Menu",
    // Low-performance mode: a smaller show
    liteFireworks: Boolean = false,
    // The player's first clean clear: the card introduces the gold ribbon
    firstRibbon: Boolean = false,
    // A Quick Game: its button deals another, rather than playing this one again
    quickGame: Boolean = false,
) {
    var showCard by remember { mutableStateOf(!animationsEnabled) }
    LaunchedEffect(Unit) {
        if (animationsEnabled) {
            delay(1100)
            showCard = true
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.28f))
            // Taps around the card stop here rather than reaching the toolbar under the blur
            .pointerInput(Unit) {},
    ) {
        if (animationsEnabled) {
            FireworksShow(modifier = Modifier.fillMaxSize(), lite = liteFireworks)
        }

        AnimatedVisibility(
            visible = showCard,
            enter = fadeIn(MaterialTheme.motionScheme.slowEffectsSpec()) +
                scaleIn(MaterialTheme.motionScheme.defaultSpatialSpec(), initialScale = 0.9f),
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLargeIncreased,
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.82f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.07f)),
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .widthIn(max = 460.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 32.dp),
                ) {
                    if (firstRibbon) {
                        GoldRibbon(Modifier.size(width = 46.dp, height = 60.dp))
                        Spacer(Modifier.height(16.dp))
                    }
                    Text(
                        text = message ?: " ",
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = subtitle,
                        // The level's name, 4 sp up from body text so it reads clearly under the message
                        style = MaterialTheme.typography.bodyMedium.let { it.copy(fontSize = (it.fontSize.value + 4f).sp) },
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (firstRibbon) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "Your first gold ribbon: cleared without hints or reshuffles. Each level you clean earns its ribbon and a hint.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = RibbonText,
                        )
                    }
                    Spacer(Modifier.height(28.dp))
                    // The buttons share the card's width, so they fit on a small phone too
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        if (hasNextLevel) {
                            BigButton("Next", onClick = onNextLevel, primary = true, compact = true, modifier = Modifier.weight(1f))
                            BigButton("Replay", onClick = onPlayAgain, compact = true, modifier = Modifier.weight(1f))
                        } else {
                            BigButton(if (quickGame) "Another?" else "Play again", onClick = onPlayAgain, primary = true, compact = true, modifier = Modifier.weight(1.4f))
                        }
                        BigButton(menuLabel, onClick = onMainMenu, compact = true, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

// The first ribbon's line, in the ribbon's own pale gold
private val RibbonText = Color(0xFFF2D58C)
