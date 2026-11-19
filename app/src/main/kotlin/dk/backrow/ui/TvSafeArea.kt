// Copyright 2026 The Backrow contributors
// SPDX-License-Identifier: Apache-2.0

package dk.backrow.ui

import androidx.compose.ui.unit.dp

/**
 * Overscan-safe margins: many TVs crop the picture edges, so content stays inside
 * roughly 5% horizontally and 5% vertically of a 960x540 dp screen.
 */
object TvSafeArea {
    val horizontal = 48.dp
    val vertical = 27.dp
}
