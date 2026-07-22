package com.sleeppulse.app.ui.dashboard

import com.sleeppulse.app.data.model.NightlySummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RecoveryScoreCalculatorTest {

    private fun night(hrv: Double, hr: Int) = NightlySummary(
        date = LocalDate.of(2026, 7, 18),
        sleepScore = 70,
        avgHeartRateBpm = hr,
        avgHrvMillis = hrv,
        totalSleepMinutes = 420,
        deepSleepMinutes = 90,
        remSleepMinutes = 100,
    )

    @Test
    fun `fewer than 3 baseline nights returns null`() {
        val lastNight = night(hrv = 50.0, hr = 60)
        assertNull(RecoveryScoreCalculator.score(lastNight, emptyList()))
        assertNull(RecoveryScoreCalculator.score(lastNight, listOf(night(50.0, 60))))
        assertNull(RecoveryScoreCalculator.score(lastNight, listOf(night(50.0, 60), night(50.0, 60))))
    }

    @Test
    fun `exactly 3 baseline nights with no deviation scores around the midpoint`() {
        val baseline = listOf(night(50.0, 60), night(50.0, 60), night(50.0, 60))
        val lastNight = night(hrv = 50.0, hr = 60)

        val result = RecoveryScoreCalculator.score(lastNight, baseline)

        assertTrue(result != null)
        assertEquals(RecoveryTier.LOW, result!!.tier)
        assertTrue("expected score in 45..55, was ${result.score}", result.score in 45..55)
    }

    @Test
    fun `well-recovered inputs (higher HRV, lower RHR than baseline) score OPTIMAL`() {
        val baseline = listOf(night(50.0, 60), night(50.0, 60), night(50.0, 60))
        val lastNight = night(hrv = 62.5, hr = 54) // +25% HRV, -10% RHR vs baseline

        val result = RecoveryScoreCalculator.score(lastNight, baseline)!!

        assertEquals(RecoveryTier.OPTIMAL, result.tier)
        assertTrue("expected score >= 80, was ${result.score}", result.score >= 80)
    }

    @Test
    fun `poorly-recovered inputs (lower HRV, higher RHR than baseline) score POOR`() {
        val baseline = listOf(night(50.0, 60), night(50.0, 60), night(50.0, 60))
        val lastNight = night(hrv = 37.5, hr = 66) // -25% HRV, +10% RHR vs baseline

        val result = RecoveryScoreCalculator.score(lastNight, baseline)!!

        assertEquals(RecoveryTier.POOR, result.tier)
        assertTrue("expected score <= 39, was ${result.score}", result.score <= 39)
    }

    @Test
    fun `moderately-recovered inputs score ADEQUATE`() {
        val baseline = listOf(night(50.0, 60), night(50.0, 60), night(50.0, 60))
        val lastNight = night(hrv = 55.0, hr = 54) // +10% HRV, -10% RHR vs baseline

        val result = RecoveryScoreCalculator.score(lastNight, baseline)!!

        assertEquals(RecoveryTier.ADEQUATE, result.tier)
        assertTrue("expected score in 60..79, was ${result.score}", result.score in 60..79)
    }

    @Test
    fun `HRV is weighted more heavily than RHR (60-40 split)`() {
        val baseline = listOf(night(50.0, 60), night(50.0, 60), night(50.0, 60))
        // HRV strongly better (+25%, pushes hrvComponent to 100), RHR strongly worse
        // (+16.67%, pushes rhrComponent to 0). A 50/50 average of 100 and 0 would be
        // exactly 50; the 60/40 weighting toward HRV should pull the result above 50.
        val lastNight = night(hrv = 62.5, hr = 70)

        val result = RecoveryScoreCalculator.score(lastNight, baseline)!!

        assertTrue("expected score > 50 (HRV-led weighting), was ${result.score}", result.score > 50)
    }

    @Test
    fun `guidance names HRV as the dominant factor when its deviation is larger`() {
        val baseline = listOf(night(50.0, 60), night(50.0, 60), night(50.0, 60))
        // HRV +25% (dominant), RHR +5% (smaller magnitude)
        val lastNight = night(hrv = 62.5, hr = 63)

        val result = RecoveryScoreCalculator.score(lastNight, baseline)!!

        // hrvDeviation=0.25, rhrDeviation=(60-63)/60=-0.05, hrvComponent=100, rhrComponent=40
        // score=100*0.6+40*0.4=76 -> ADEQUATE (favorable tier)
        assertEquals(RecoveryTier.ADEQUATE, result.tier)
        assertEquals(
            "Your HRV is 25% above your weekly average, suggesting strong recovery.",
            result.guidance,
        )
    }

    @Test
    fun `guidance names resting heart rate as the dominant factor when its deviation is larger`() {
        val baseline = listOf(night(50.0, 60), night(50.0, 60), night(50.0, 60))
        // RHR +20% (dominant), HRV +2% (smaller magnitude)
        val lastNight = night(hrv = 51.0, hr = 72)

        val result = RecoveryScoreCalculator.score(lastNight, baseline)!!

        // hrvDeviation=0.02, rhrDeviation=(60-72)/60=-0.20, hrvComponent=54, rhrComponent=10
        // score=54*0.6+10*0.4=36.4->36 -> POOR (unfavorable tier)
        assertEquals(RecoveryTier.POOR, result.tier)
        assertEquals(
            "Your resting heart rate is 20% above your weekly average, consider an easier day.",
            result.guidance,
        )
    }

    @Test
    fun `guidance falls back to the static tier message when both deviations are within the neutral threshold`() {
        val baseline = listOf(night(50.0, 60), night(50.0, 60), night(50.0, 60))
        // HRV +1%, RHR +1% — both within the +/-3% neutral threshold
        val lastNight = night(hrv = 50.5, hr = 60.6.toInt())

        val result = RecoveryScoreCalculator.score(lastNight, baseline)!!

        assertEquals("Under-recovered — consider an easier day.", result.guidance)
    }

    @Test
    fun `RecoveryResult exposes the raw hrvDeviation and rhrDeviation used to compute the score`() {
        val baseline = listOf(night(50.0, 60), night(50.0, 60), night(50.0, 60))
        val lastNight = night(hrv = 62.5, hr = 54) // +25% HRV, -10% RHR vs baseline

        val result = RecoveryScoreCalculator.score(lastNight, baseline)!!

        assertEquals(0.25, result.hrvDeviation, 0.001)
        assertEquals(0.10, result.rhrDeviation, 0.001)
    }
}
