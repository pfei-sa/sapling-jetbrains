package io.github.pfeisa.sapling

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class SaplingVcsProvidersTest : BasePlatformTestCase() {
    fun testRollbackEnvironmentIsWired() {
        // Accessing the property forces lazy construction; our override is non-null.
        val vcs = SaplingVcs(project)
        assertEquals("Revert", vcs.rollbackEnvironment.rollbackOperationName)
    }

    fun testMergeProviderIsWired() {
        val vcs = SaplingVcs(project)
        assertNotNull(vcs.mergeProvider)
    }
}
