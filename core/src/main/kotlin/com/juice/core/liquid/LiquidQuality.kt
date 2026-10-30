package com.juice.core.liquid

/**
 * How much work the juice does. Bigger particles ([h], in dp) mean fewer of them for the same fill, so less
 * physics; a lower [fieldScale] shades the liquid at that fraction of the card's resolution, so less GPU work.
 */
enum class LiquidQuality(val h: Float, val fieldScale: Float) {
    FAST(40f, 0.5f),
    BALANCED(34f, 0.6f),
    BEST(30f, 1f),
}
