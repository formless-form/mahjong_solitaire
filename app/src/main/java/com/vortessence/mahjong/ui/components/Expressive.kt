package com.vortessence.mahjong.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Shared building blocks in the Android 17 (Material 3 Expressive) style, so every menu screen
// has the same bar, lists, switches and buttons

/** A round tonal back button, as in Android 17's own Settings. */
@Composable
fun BackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalIconButton(
        onClick = onClick,
        shapes = IconButtonDefaults.shapes(),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = modifier,
    ) {
        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
    }
}

/**
 * A menu screen: a large title that shrinks into the bar as the content scrolls, a back button,
 * and the content below.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpressiveScreen(
    title: String,
    onBack: () -> Unit,
    subtitle: String? = null,
    // Drawn behind the whole screen, the bar included, in place of the plain page colour
    backdrop: (@Composable () -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val page = if (backdrop != null) Color.Transparent else MaterialTheme.colorScheme.background
    Box(Modifier.fillMaxSize()) {
    backdrop?.invoke()
    CompositionLocalProvider(LocalPanelAlpha provides if (backdrop != null) PANEL_ALPHA_OVER_BACKDROP else 1f) {
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(title) },
                subtitle = subtitle?.let { { Text(it) } },
                navigationIcon = { BackButton(onClick = onBack, modifier = Modifier.padding(start = 8.dp)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = page,
                    // Over a backdrop, the collapsed bar lets a little of it through
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = if (backdrop != null) 0.88f else 1f),
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    subtitleContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
        containerColor = page,
        content = content,
    )
    }
    }
}

/**
 * How opaque the panels of a menu screen are: fully, on the plain page; over a backdrop (the
 * stone), only faintly, so it shows through them.
 */
val LocalPanelAlpha = staticCompositionLocalOf { 1f }

// Over a backdrop, a panel is a faint veil: enough to group its rows, little enough that the
// stone reads through
private const val PANEL_ALPHA_OVER_BACKDROP = 0.34f

/**
 * [colour] as a panel on this screen: as it is on the plain page, more see-through over a
 * backdrop, but never below [atLeast] of its own opacity (for controls that must stay legible).
 */
@Composable
fun panel(colour: Color, atLeast: Float = 0f): Color {
    val alpha = LocalPanelAlpha.current
    if (alpha >= 1f) return colour
    // Over the stone, the panels' green tint is half drained, so they read as veils of the stone
    val grey = 0.2126f * colour.red + 0.7152f * colour.green + 0.0722f * colour.blue
    return Color(
        red = grey + (colour.red - grey) * PANEL_SATURATION_OVER_BACKDROP,
        green = grey + (colour.green - grey) * PANEL_SATURATION_OVER_BACKDROP,
        blue = grey + (colour.blue - grey) * PANEL_SATURATION_OVER_BACKDROP,
        alpha = colour.alpha * maxOf(alpha, atLeast),
    )
}

// How much of their colour the panels keep over a backdrop
private const val PANEL_SATURATION_OVER_BACKDROP = 0.5f

/** The small accent-coloured heading above a group of list items. */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        // 6 sp larger than the type scale's small title (chosen 2026-10-30)
        style = MaterialTheme.typography.titleSmallEmphasized.let { it.copy(fontSize = (it.fontSize.value + 6f).sp) },
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp),
    )
}

/** Gap between the segments of a grouped list. */
val SegmentGap: Dp = ListItemDefaults.SegmentedGap

/**
 * Segment colours: slightly raised from the page, so each group reads as one rounded block. A
 * switched-on row looks the same as an off one (the switch shows the state), as in Android's
 * own Settings.
 */
@Composable
fun segmentColors(): ListItemColors = ListItemDefaults.segmentedColors(
    containerColor = panel(MaterialTheme.colorScheme.surfaceContainer),
    supportingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    disabledContainerColor = panel(MaterialTheme.colorScheme.surfaceContainerLow),
    selectedContainerColor = panel(MaterialTheme.colorScheme.surfaceContainer),
    selectedContentColor = MaterialTheme.colorScheme.onSurface,
    selectedSupportingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    selectedLeadingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    selectedTrailingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    selectedOverlineContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
)

/** A list item's leading icon, in a soft circle. */
@Composable
fun LeadingIcon(icon: ImageVector, enabled: Boolean = true) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .background(panel(MaterialTheme.colorScheme.surfaceContainerHighest, atLeast = 0.35f), CircleShape),
    ) {
        // Greyed as Material greys a disabled row's text: the same colour at 38%
        val tint = MaterialTheme.colorScheme.primary.let { if (enabled) it else it.copy(alpha = 0.38f) }
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

/** A switch whose thumb shows a tick when on. */
@Composable
fun CheckSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, enabled: Boolean = true) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        thumbContent = if (checked) {
            { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(SwitchDefaults.IconSize)) }
        } else {
            null
        },
    )
}

/** Height of the big action buttons: Material's medium button size. */
val BigButtonHeight: Dp = ButtonDefaults.MediumContainerHeight

/** A big pill-shaped button whose corners square off slightly when pressed. */
@Composable
fun BigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    icon: ImageVector? = null,
    // Narrower side padding, for buttons sharing a row on a small phone
    compact: Boolean = false,
) {
    val content: @Composable RowScope.() -> Unit = {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.iconSizeFor(BigButtonHeight)))
            Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(BigButtonHeight)))
        }
        ProvideTextStyle(ButtonDefaults.textStyleFor(BigButtonHeight)) { Text(text, maxLines = 1, softWrap = false) }
    }
    val sized = modifier.heightIn(min = BigButtonHeight)
    val padding = if (compact) {
        PaddingValues(horizontal = 12.dp)
    } else {
        ButtonDefaults.contentPaddingFor(BigButtonHeight, hasStartIcon = icon != null)
    }
    if (primary) {
        Button(onClick = onClick, shapes = ButtonDefaults.shapesFor(BigButtonHeight), modifier = sized, contentPadding = padding, content = content)
    } else {
        FilledTonalButton(
            onClick = onClick,
            shapes = ButtonDefaults.shapesFor(BigButtonHeight),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = panel(MaterialTheme.colorScheme.surfaceContainerHighest, atLeast = 0.5f),
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
            modifier = sized,
            contentPadding = padding,
            content = content,
        )
    }
}

/** The page's loading state: Material's shape-morphing indicator, centred. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoadingPage(modifier: Modifier = Modifier) {
    Box(contentAlignment = Alignment.Center, modifier = modifier.fillMaxSize()) {
        LoadingIndicator(color = MaterialTheme.colorScheme.primary)
    }
}
