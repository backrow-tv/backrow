// Copyright 2026 The Backrow contributors
// SPDX-License-Identifier: Apache-2.0

package dk.backrow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dk.backrow.core.model.Logger
import dk.backrow.ui.BackrowApp
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val logger: Logger by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logger.info("MainActivity created")
        setContent {
            BackrowApp(logger = logger)
        }
    }
}
