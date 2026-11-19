// Copyright 2026 The Backrow contributors
// SPDX-License-Identifier: Apache-2.0

package dk.backrow.di

import dk.backrow.core.model.Logger
import dk.backrow.log.AndroidLogger
import org.koin.dsl.module

val appModule =
    module {
        single<Logger> { AndroidLogger() }
    }
