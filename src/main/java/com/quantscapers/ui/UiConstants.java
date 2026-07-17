package com.quantscapers.ui;

/**
 * Real usable text width inside a card, measured empirically after two
 * rounds of overflow (RuneLite's 225px PANEL_WIDTH, minus our own
 * scrollbar, minus the panel's outer padding, minus the card's own
 * border+padding, leaves less room than the naive arithmetic suggests).
 * Every HTML-wrapped label and fixed-width component in a card should use
 * this instead of guessing its own width — a single line running wider
 * than its parent drags the whole card's border wider with it, since
 * BoxLayout never shrinks a child below its reported preferred size.
 */
public final class UiConstants {
    private UiConstants() {}

    public static final int CARD_TEXT_WIDTH_PX = 150;
}
