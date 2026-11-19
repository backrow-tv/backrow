// Copyright 2026 The Backrow contributors
// SPDX-License-Identifier: Apache-2.0

package dk.backrow.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dk.backrow.R
import dk.backrow.ui.TvSafeArea

/** Sections the Settings screen will have (ROADMAP v1 item 8). Placeholders until then. */
private val sections =
    listOf(
        R.string.settings_section_playback,
        R.string.settings_section_sponsorblock,
        R.string.settings_section_privacy,
        R.string.settings_section_about,
    )

/** Settings: placeholder sections. Focus starts on the first one; Back returns to Home. */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val firstItem = remember { FocusRequester() }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .testTag(SettingsTestTags.SCREEN)
                .padding(horizontal = TvSafeArea.horizontal, vertical = TvSafeArea.vertical),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        LazyColumn(
            modifier = Modifier.widthIn(max = 480.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(sections) { index, title ->
                if (index == 0) {
                    // Runs after the first item is attached.
                    LaunchedEffect(Unit) { firstItem.requestFocus() }
                }
                SettingsSectionItem(
                    title = title,
                    modifier = if (index == 0) Modifier.focusRequester(firstItem) else Modifier,
                )
            }
        }
    }
}

@Composable
private fun SettingsSectionItem(
    @StringRes title: Int,
    modifier: Modifier = Modifier,
) {
    ListItem(
        selected = false,
        onClick = {},
        modifier = modifier,
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(stringResource(R.string.settings_coming_later)) },
    )
}

/** Test tags identify nodes for UI tests; they are not user-facing text. */
object SettingsTestTags {
    const val SCREEN = "settings"
}
