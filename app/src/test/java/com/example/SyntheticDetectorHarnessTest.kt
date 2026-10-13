package com.example

import com.example.recoveryshield.protection.detector.DeterministicRuleDetector
import com.example.recoveryshield.protection.detector.SyntheticDetectorHarness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyntheticDetectorHarnessTest {

    @Test
    fun `synthetic test harness benchmark achieves 100 percent precision on standard fixtures`() {
        val detector = DeterministicRuleDetector()
        val result = SyntheticDetectorHarness.evaluate(detector)

        assertEquals("All synthetic fixtures must pass:\n" + result.details.joinToString("\n"), result.totalFixtures, result.passed)
        assertEquals("Zero failures allowed in benchmark", 0, result.failed)
        assertTrue("Precision must be 1.0 (no false positives)", result.precision >= 1.0f)
        assertTrue("Recall must be 1.0 (no false negatives)", result.recall >= 1.0f)
    }
}
