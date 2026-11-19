// Copyright 2026 The Backrow contributors
// SPDX-License-Identifier: Apache-2.0

package dk.backrow.core.sponsorblock

import org.junit.Assert.assertEquals
import org.junit.Test

class ModuleInfoTest {
    @Test
    fun `module reports its name`() {
        assertEquals(":core:sponsorblock", ModuleInfo.NAME)
    }
}
