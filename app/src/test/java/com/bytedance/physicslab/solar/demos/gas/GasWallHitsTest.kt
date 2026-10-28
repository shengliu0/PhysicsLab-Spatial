package com.bytedance.physicslab.solar.demos.gas

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import kotlin.math.PI
import kotlin.math.sqrt

/**
 * The wall-hit counter that drives the gas "pressure texture" sound must count real wall collisions:
 * kinetic theory says the hit rate on area A is Γ = ¼ (N/V) <v> A with <v> = √(8T/π) (m = k = 1), and
 * halving V must double it PER UNIT AREA (Boyle, by ear).
 */
class GasWallHitsTest {
    private fun rate(secs: Float): Triple<Double, Double, Double> {
        val g = GasDemo.gas
        val h0 = GasDemo.wallHits
        var theory = 0.0
        val n = (secs * 60).toInt()
        repeat(n) {
            theory += 0.25 * g.n / g.volume * sqrt(8 * g.temperature() / PI) * g.wallArea * (1.0 / 60)
            GasDemo.update(1f / 60f)
        }
        return Triple((GasDemo.wallHits - h0) / (secs.toDouble()), theory / secs, g.wallArea)
    }

    @Test
    fun countedWallHitsMatchKineticTheory() {
        GasDemo.tSetK = 300f; GasDemo.vFrac = 1f; GasDemo.nIdx = 2; GasDemo.speedIdx = 2   // ×1: sim time = wall time
        GasDemo.runHeadless(File("build/review-cache").apply { mkdirs() }, 1f)
        GasDemo.resetAll()
        GasDemo.update(0f); repeat(120) { GasDemo.update(1f / 60f) }
        val (full, fullTh, aFull) = rate(20f)
        GasDemo.vFrac = 0.5f; repeat(600) { GasDemo.update(1f / 60f) }                     // piston in, settle
        val (half, halfTh, aHalf) = rate(20f)
        println("wall hits/s: full V %.0f (theory %.0f), half V %.0f (theory %.0f), per-area ratio %.2f; ticks/s at ×0.5 full V %.1f"
            .format(full, fullTh, half, halfTh, half / aHalf / (full / aFull), full * 0.5 / GasDemo.HITS_PER_TICK))
        assertEquals(1.0, full / fullTh, 0.15)
        assertEquals(1.0, half / halfTh, 0.15)
        assertEquals(2.0, half / aHalf / (full / aFull), 0.3)
        GasDemo.vFrac = 1f; GasDemo.speedIdx = 1
    }
}
