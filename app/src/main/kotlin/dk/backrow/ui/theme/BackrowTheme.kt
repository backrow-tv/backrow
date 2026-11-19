// Copyright 2026 The Backrow contributors
// SPDX-License-Identifier: Apache-2.0

package dk.backrow.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

// Original palette (ADR-0001): warm amber accent on a dark blue-grey. No red.
internal val Amber = Color(0xFFF2B84B)
private val OnAmber = Color(0xFF1F1606)
private val Night = Color(0xFF12161F)
private val NightRaised = Color(0xFF1B212C)
private val NightCard = Color(0xFF252D3A)
private val Paper = Color(0xFFEDE7DC)
private val PaperMuted = Color(0xFFB4AEA4)

private val BackrowColors =
    darkColorScheme(
        primary = Amber,
        onPrimary = OnAmber,
        background = Night,
        onBackground = Paper,
        surface = NightRaised,
        onSurface = Paper,
        surfaceVariant = NightCard,
        onSurfaceVariant = PaperMuted,
        border = Amber,
    )

@Composable
fun BackrowTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = BackrowColors, content = content)
}
