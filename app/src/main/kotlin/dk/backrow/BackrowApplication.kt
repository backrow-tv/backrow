// Copyright 2026 The Backrow contributors
// SPDX-License-Identifier: Apache-2.0

package dk.backrow

import android.app.Application
import dk.backrow.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class BackrowApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@BackrowApplication)
            modules(appModule)
        }
    }
}
