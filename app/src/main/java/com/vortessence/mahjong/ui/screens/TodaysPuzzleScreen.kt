package com.vortessence.mahjong.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.vortessence.mahjong.data.model.Progress
import com.vortessence.mahjong.engine.TodaysPuzzle
import com.vortessence.mahjong.ui.components.BigButton
import com.vortessence.mahjong.ui.components.ExpressiveScreen
import com.vortessence.mahjong.ui.components.MenuBackdrop
import com.vortessence.mahjong.ui.components.panel
import com.vortessence.mahjong.ui.viewmodel.TODAYS_PUZZLE
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * Today's Puzzle: a month's calendar, a tick on each day whose puzzle was solved and today ringed,
 * then today's puzzle and the button to play it. Only today's can be played, and there is no
 * streak to keep: the ticks are all there is. Earlier months back to the first solved day.
 */
@Composable
fun TodaysPuzzleScreen(
    progress: Progress,
    // Today's puzzle, started and not yet solved
    inProgress: Boolean,
    onPlay: () -> Unit,
    onBack: () -> Unit,
    today: Long = TodaysPuzzle.today(),
    fireIntensity: Float = 0.5f,
    // The menus' fire goes out with the phone's Remove animations
    fire: Boolean = true,
) {
    val todayDate = LocalDate.ofEpochDay(today)
    val solvedToday = today in progress.puzzleDaysSolved
    val layout = remember(today) { TodaysPuzzle.layoutOn(today) }
    val thisMonth = YearMonth.from(todayDate)
    val firstMonth = progress.puzzleDaysSolved.minOrNull()?.let { YearMonth.from(LocalDate.ofEpochDay(it)) }
        ?.takeIf { it < thisMonth } ?: thisMonth
    var month by rememberSaveable(today) { mutableStateOf(thisMonth) }

    ExpressiveScreen(title = TODAYS_PUZZLE, onBack = onBack, backdrop = { MenuBackdrop(fireIntensity, fire) }) { padding ->
        // Near phone-width on a tablet or open foldable, so the calendar's square days leave room for the
        // button: a little wider where the screen is tall enough, as on an upright tablet
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            val width = minOf(maxWidth, (maxHeight * HEIGHT_TO_WIDTH).coerceIn(MIN_MAX_WIDTH, MAX_WIDTH))
            // The calendar zoomed to fill that width, its days and numbers growing with it
            val zoom = ((width - 32.dp) / PHONE_CALENDAR).coerceAtLeast(1f)
            val density = LocalDensity.current
            Column(
                modifier = Modifier
                    .width(width)
                    .fillMaxHeight()
                    .padding(top = padding.calculateTopPadding() + 8.dp + ZOOM_GAP * (zoom - 1f), bottom = padding.calculateBottomPadding() + 24.dp)
                    .padding(horizontal = 16.dp),
            ) {
                CompositionLocalProvider(LocalDensity provides Density(density.density * zoom, density.fontScale)) {
                    Calendar(
                        month = month,
                        today = todayDate,
                        solved = progress.puzzleDaysSolved,
                        canGoBack = month > firstMonth,
                        canGoForward = month < thisMonth,
                        onMonth = { month = it },
                    )
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    "${todayDate.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())}'s puzzle · ${layout.difficulty.displayName}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                Text(
                    "${layout.name} · ${layout.tileCount} tiles",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                Spacer(Modifier.weight(1f))
                if (solvedToday) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    ) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Solved today", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                BigButton(
                    text = when {
                        inProgress -> "Continue today's puzzle"
                        solvedToday -> "Play it again"
                        else -> "Play today's puzzle"
                    },
                    onClick = onPlay,
                    primary = !solvedToday || inProgress,
                    icon = Icons.Rounded.PlayArrow,
                    // As wide as its words, centred
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "The same puzzle for everyone today. A new one at 4 am.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                // The button and its line halfway down the space below the puzzle's details
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

private val MIN_MAX_WIDTH = 480.dp
private val MAX_WIDTH = 620.dp
private const val HEIGHT_TO_WIDTH = 0.47f
// The calendar's width on a typical phone
private val PHONE_CALENDAR = 395.dp
// Extra room under the title as the calendar zooms
private val ZOOM_GAP = 48.dp

// A month, Monday first: solved days ticked, today ringed in gold, the rest plain numbers
@Composable
private fun Calendar(
    month: YearMonth,
    today: LocalDate,
    solved: Set<Long>,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onMonth: (YearMonth) -> Unit,
) {
    val faint = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = panel(MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = { onMonth(month.minusMonths(1)) }, enabled = canGoBack) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Previous month")
                }
                Text(
                    "${month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${month.year}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onMonth(month.plusMonths(1)) }, enabled = canGoForward) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Next month")
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp)) {
                for (weekday in java.time.DayOfWeek.entries) {
                    Text(
                        weekday.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                        style = MaterialTheme.typography.labelMedium,
                        color = faint,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            // Blank days before the 1st, so it falls under its weekday
            val lead = month.atDay(1).dayOfWeek.ordinal
            val cells = List(lead) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
            for (week in cells.chunked(7)) {
                Row(Modifier.fillMaxWidth()) {
                    for (i in 0 until 7) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.weight(1f).aspectRatio(1.1f)) {
                            week.getOrNull(i)?.let { date -> Day(date, today, date.toEpochDay() in solved) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Day(date: LocalDate, today: LocalDate, solved: Boolean) {
    val isToday = date == today
    val label = "${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.FULL, Locale.getDefault())}" +
        if (solved) ", solved" else ""
    val ring = if (isToday) {
        Modifier.border(2.dp, MaterialTheme.colorScheme.secondary, CircleShape)
    } else {
        Modifier
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            // A solved day's circle a little smaller than today's ring
            .size(if (solved && !isToday) 32.dp else 38.dp)
            .then(ring)
            .then(if (solved) Modifier.background(MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
            .semantics { contentDescription = label },
    ) {
        if (solved) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
        } else {
            Text(
                "${date.dayOfMonth}",
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    isToday -> MaterialTheme.colorScheme.onSurface
                    date < today -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                },
            )
        }
    }
}
