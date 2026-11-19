// Copyright 2026 The Backrow contributors
// SPDX-License-Identifier: Apache-2.0

package dk.backrow.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import dk.backrow.core.model.Logger
import dk.backrow.ui.home.HomeScreen
import dk.backrow.ui.settings.SettingsScreen
import dk.backrow.ui.theme.BackrowTheme
import kotlinx.serialization.Serializable

@Serializable
object HomeRoute

@Serializable
object SettingsRoute

// Short cross-fades: the defaults are slow enough to feel laggy with a remote, and
// cheap sticks drop frames on long animations.
private const val TRANSITION_MILLIS = 150

/**
 * App root: theme plus the navigation graph (ADR-0010). Back is handled by the navigation
 * back stack; on Home it leaves the app.
 */
@Composable
fun BackrowApp(logger: Logger) {
    BackrowTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        ) {
            val navController = rememberNavController()
            NavHost(
                navController = navController,
                startDestination = HomeRoute,
                enterTransition = { fadeIn(tween(TRANSITION_MILLIS)) },
                exitTransition = { fadeOut(tween(TRANSITION_MILLIS)) },
                popEnterTransition = { fadeIn(tween(TRANSITION_MILLIS)) },
                popExitTransition = { fadeOut(tween(TRANSITION_MILLIS)) },
            ) {
                composable<HomeRoute> {
                    HomeScreen(
                        onOpenSettings = { navController.navigate(SettingsRoute) { launchSingleTop = true } },
                        onCardSelected = { card -> logger.debug("Selected placeholder card row=${card.row} index=${card.index}") },
                    )
                }
                composable<SettingsRoute> {
                    SettingsScreen()
                }
            }
        }
    }
}
