// Copyright 2026 The Backrow contributors
// SPDX-License-Identifier: Apache-2.0

package dk.backrow.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceholderContentTest {
    @Test
    fun `home has three rows of twelve cards`() {
        val rows = placeholderRows()

        assertEquals(3, rows.size)
        rows.forEach { assertEquals(12, it.cards.size) }
    }

    @Test
    fun `every card knows its own position`() {
        val rows = placeholderRows()

        rows.forEachIndexed { rowIndex, row ->
            assertEquals(rowIndex, row.index)
            row.cards.forEachIndexed { cardIndex, card ->
                assertEquals(PlaceholderCard(rowIndex, cardIndex), card)
            }
        }
    }

    @Test
    fun `home focus tells the gear apart from cards`() {
        assertTrue(HomeFocus.Gear.isGear)
        assertFalse(HomeFocus.FirstCard.isGear)
        assertTrue(HomeFocus.of(PlaceholderCard(2, 5)).isCard(PlaceholderCard(2, 5)))
        assertFalse(HomeFocus.Gear.isCard(PlaceholderCard(0, 0)))
    }
}
