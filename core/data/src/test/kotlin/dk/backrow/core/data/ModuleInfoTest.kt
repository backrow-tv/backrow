// Copyright 2026 The Backrow contributors
// SPDX-License-Identifier: Apache-2.0

package dk.backrow.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ModuleInfoTest {
    @Test
    fun `module reports its name`() {
        assertEquals(":core:data", ModuleInfo.NAME)
    }
}
