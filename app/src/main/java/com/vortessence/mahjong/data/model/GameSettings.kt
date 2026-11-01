package com.vortessence.mahjong.data.model

// Order matters: the table shader selects the surface by ordinal
// shortName: one word, for the four buttons in Settings, where the full names don't fit
enum class TableSurface(val displayName: String, val shortName: String) {
    GREEN_MAT("Green mat", "Felt"),
    WALNUT("Walnut", "Walnut"),
    THAI_STRAW("Thai straw", "Straw"),
    SLATE("Slate", "Slate"),
}

// How a wide board (one that plays much bigger with the phone on its side) is shown: the player is
// asked the first time one comes up, and the answer is kept
enum class WideBoards(val shortName: String) {
    ASK("Ask"),
    LANDSCAPE("Landscape"),
    PORTRAIT("Portrait"),
}

data class GameSettings(
    val soundEnabled: Boolean = true,
    val soundVolume: Float = 0.65f,
    val musicEnabled: Boolean = true,
    val musicVolume: Float = 0.6f,
    val autoZoomEnabled: Boolean = true,
    val highlightFreeTiles: Boolean = false,
    // Not a setting of the game's own: it follows the phone's Remove animations (accessibility),
    // and is never saved
    val animationsEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val tableSurface: TableSurface = TableSurface.GREEN_MAT,
    // The lamp over the table is always lit; how brightly, 0..1, the middle its natural strength
    val lampBrightness: Float = 0.5f,
    // A fire in a hearth beside the table, lit or not, and how brightly it burns (as the lamp)
    val fireplaceLit: Boolean = true,
    val fireIntensity: Float = 0.6f,
    // The fire's crackle, on its own volume (subtle by default)
    val fireVolume: Float = 0.4f,
    val wideBoards: WideBoards = WideBoards.ASK,
    // For slower phones: the fire, fireworks and other moving effects off (as with the phone's
    // Remove animations); the lamp and the tiles' look stay
    val lowPerformance: Boolean = false,
    // The phone's own Remove animations, as read (never saved): low-performance mode keeps the
    // match smash, with a lighter sparkle, unless the phone wants no motion at all
    val phoneAnimates: Boolean = true,
) {
    /** A match lifts the pair and smashes them together (with or without the full sparkle). */
    val matchSmash: Boolean get() = animationsEnabled || (lowPerformance && phoneAnimates)
}
