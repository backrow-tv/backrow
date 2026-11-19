// Copyright 2026 The Backrow contributors
// SPDX-License-Identifier: Apache-2.0

package dk.backrow.ui.home

/** A placeholder card on Home, identified by its position. Replaced by real videos later. */
data class PlaceholderCard(val row: Int, val index: Int)

data class PlaceholderRow(val index: Int, val cards: List<PlaceholderCard>)

const val PLACEHOLDER_ROW_COUNT = 3
const val PLACEHOLDER_CARDS_PER_ROW = 12

fun placeholderRows(
    rowCount: Int = PLACEHOLDER_ROW_COUNT,
    cardsPerRow: Int = PLACEHOLDER_CARDS_PER_ROW,
): List<PlaceholderRow> =
    List(rowCount) { row ->
        PlaceholderRow(index = row, cards = List(cardsPerRow) { index -> PlaceholderCard(row, index) })
    }
