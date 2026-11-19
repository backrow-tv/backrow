// Copyright 2026 The Backrow contributors
// SPDX-License-Identifier: Apache-2.0

package dk.backrow.ui.home

import androidx.activity.compose.ReportDrawn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dk.backrow.R
import dk.backrow.ui.TvSafeArea

private val CardWidth = 196.dp
private val CardHeight = 110.dp

/**
 * Home: a top bar with the Settings gear, then rows of placeholder cards.
 *
 * Focus rules (CLAUDE.md rule 8):
 * - First launch: focus starts on the first card of the first row.
 * - Returning from another screen: focus goes back to the card or gear that had it.
 * - Moving between rows: each row remembers its last focused card (`focusRestorer`).
 * - Menu (Fire TV remotes) is a shortcut to Settings; the gear works on every remote.
 */
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onCardSelected: (PlaceholderCard) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = remember { placeholderRows() }

    // Saved across navigation (the back stack entry keeps it) and process death.
    var focusedRow by rememberSaveable { mutableIntStateOf(HomeFocus.FirstCard.row) }
    var focusedIndex by rememberSaveable { mutableIntStateOf(HomeFocus.FirstCard.index) }
    val savedFocus = HomeFocus(focusedRow, focusedIndex)
    val saveFocus = { focus: HomeFocus ->
        focusedRow = focus.row
        focusedIndex = focus.index
    }

    // Not saved: each time Home is composed again, the saved target takes focus once.
    var restorePending by remember { mutableStateOf(true) }
    val restoreDone = { restorePending = false }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .testTag(HomeTestTags.SCREEN)
                .onPreviewKeyEvent { event ->
                    if (event.key == Key.Menu) {
                        if (event.type == KeyEventType.KeyUp) onOpenSettings()
                        true
                    } else {
                        false
                    }
                },
    ) {
        HomeTopBar(
            takeFocus = restorePending && savedFocus.isGear,
            onFocusTaken = restoreDone,
            onFocused = { saveFocus(HomeFocus.Gear) },
            onOpenSettings = onOpenSettings,
        )
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = TvSafeArea.vertical),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            items(rows, key = { it.index }) { row ->
                PlaceholderCardRow(
                    row = row,
                    focusTarget = if (restorePending) savedFocus else null,
                    onFocusTaken = restoreDone,
                    onCardFocused = { card -> saveFocus(HomeFocus.of(card)) },
                    onCardSelected = onCardSelected,
                )
            }
        }
    }

    // Marks Home as interactive, so `am start -W` and the "Fully drawn" logcat line
    // measure cold start to interactive Home (CLAUDE.md rule 10).
    ReportDrawn()
}

@Composable
private fun HomeTopBar(
    takeFocus: Boolean,
    onFocusTaken: () -> Unit,
    onFocused: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    if (takeFocus) {
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
            onFocusTaken()
        }
    }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = TvSafeArea.horizontal, vertical = TvSafeArea.vertical),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.app_name_wordmark),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = onOpenSettings,
            modifier =
                Modifier
                    .testTag(HomeTestTags.SETTINGS_GEAR)
                    .focusRequester(focusRequester)
                    .onFocusChanged { if (it.isFocused) onFocused() },
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_settings),
                contentDescription = stringResource(R.string.home_open_settings),
            )
        }
    }
}

@Composable
private fun PlaceholderCardRow(
    row: PlaceholderRow,
    focusTarget: HomeFocus?,
    onFocusTaken: () -> Unit,
    onCardFocused: (PlaceholderCard) -> Unit,
    onCardSelected: (PlaceholderCard) -> Unit,
) {
    Column {
        Text(
            text = stringResource(R.string.home_row_title, row.index + 1),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = TvSafeArea.horizontal, bottom = 12.dp),
        )
        LazyRow(
            modifier = Modifier.focusRestorer(),
            contentPadding = PaddingValues(horizontal = TvSafeArea.horizontal, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            items(row.cards, key = { it.index }) { card ->
                PlaceholderCardItem(
                    card = card,
                    takeFocus = focusTarget?.isCard(card) == true,
                    onFocusTaken = onFocusTaken,
                    onFocused = { onCardFocused(card) },
                    onClick = { onCardSelected(card) },
                )
            }
        }
    }
}

@Composable
private fun PlaceholderCardItem(
    card: PlaceholderCard,
    takeFocus: Boolean,
    onFocusTaken: () -> Unit,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    if (takeFocus) {
        // Effects run after this card's modifiers are attached, so the request is safe here.
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
            onFocusTaken()
        }
    }
    Card(
        onClick = onClick,
        modifier =
            Modifier
                .size(CardWidth, CardHeight)
                .testTag(HomeTestTags.card(card))
                .focusRequester(focusRequester)
                .onFocusChanged { if (it.isFocused) onFocused() },
        scale = CardDefaults.scale(focusedScale = 1.08f),
        border =
            CardDefaults.border(
                focusedBorder = Border(BorderStroke(3.dp, MaterialTheme.colorScheme.border)),
            ),
        colors = CardDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            contentAlignment = Alignment.BottomStart,
        ) {
            Text(
                text = stringResource(R.string.home_card_title, card.index + 1),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** Test tags identify nodes for UI tests; they are not user-facing text. */
object HomeTestTags {
    const val SCREEN = "home"
    const val SETTINGS_GEAR = "home-settings-gear"

    fun card(card: PlaceholderCard) = "home-card-${card.row}-${card.index}"
}
