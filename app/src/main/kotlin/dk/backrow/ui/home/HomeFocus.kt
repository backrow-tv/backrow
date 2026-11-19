// Copyright 2026 The Backrow contributors
// SPDX-License-Identifier: Apache-2.0

package dk.backrow.ui.home

/**
 * Where focus was on Home, so it can be put back after returning from another screen
 * (CLAUDE.md rule 8). Kept as two ints so it fits in saved state and survives navigation
 * and process death. Row [GEAR_ROW] means the Settings gear in the top bar.
 */
data class HomeFocus(val row: Int, val index: Int) {
    val isGear: Boolean get() = row == GEAR_ROW

    fun isCard(card: PlaceholderCard): Boolean = row == card.row && index == card.index

    companion object {
        const val GEAR_ROW = -1
        val Gear = HomeFocus(GEAR_ROW, 0)
        val FirstCard = HomeFocus(0, 0)

        fun of(card: PlaceholderCard) = HomeFocus(card.row, card.index)
    }
}
