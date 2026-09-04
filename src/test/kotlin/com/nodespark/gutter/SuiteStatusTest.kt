package com.nodespark.gutter

import com.nodespark.testtree.TestStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class SuiteStatusTest {

    @Test
    fun `all passed makes the suite green`() {
        assertEquals(TestStatus.PASSED, suiteStatus(listOf(TestStatus.PASSED, TestStatus.PASSED)))
    }

    @Test
    fun `one failure reddens the suite`() {
        assertEquals(
            TestStatus.FAILED,
            suiteStatus(listOf(TestStatus.PASSED, TestStatus.FAILED, null)),
        )
    }

    @Test
    fun `an unrun test leaves the suite without a result`() {
        assertEquals(null, suiteStatus(listOf(TestStatus.PASSED, null)))
    }

    @Test
    fun `a suite skipped throughout is skipped`() {
        assertEquals(TestStatus.SKIPPED, suiteStatus(listOf(TestStatus.SKIPPED, TestStatus.SKIPPED)))
    }

    @Test
    fun `a skip among passes still counts as passed`() {
        assertEquals(TestStatus.PASSED, suiteStatus(listOf(TestStatus.PASSED, TestStatus.SKIPPED)))
    }

    @Test
    fun `an empty describe has no result`() {
        assertEquals(null, suiteStatus(emptyList()))
    }
}
