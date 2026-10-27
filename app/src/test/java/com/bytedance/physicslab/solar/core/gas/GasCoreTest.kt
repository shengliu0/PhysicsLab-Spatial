package com.bytedance.physicslab.solar.core.gas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

class GasCoreTest {

    private fun box(cap: Int = 200, d: Double = 0.02, seed: Long = 7L) = HardSphereGas(cap, d, seed).apply {
        setBox(-0.35, 0.35, 0.17, 0.14)              // the demo's full-volume box (centre region)
    }

    @Test
    fun singleCollisionConservesMomentumAndEnergyExactly() {
        val g = box()
        // oblique approach: the line of centres is not along either velocity
        g.put(0, 0.0, 0.0, 0.0, 0.7, 0.1, -0.2)
        g.put(1, 0.015, 0.009, 0.004, -0.3, 0.4, 0.25)
        val p0 = DoubleArray(3); val p1 = DoubleArray(3)
        g.momentum(p0); val e0 = g.kineticEnergy()
        g.collidePair(0, 1)
        g.momentum(p1); val e1 = g.kineticEnergy()
        println("pair: p before ${p0.toList()} after ${p1.toList()}, E $e0 -> $e1")
        for (k in 0 until 3) assertEquals(p0[k], p1[k], 1e-15)
        assertEquals(e0, e1, 1e-15)
        // and the pair now separates along the line of centres
        val dx = g.x[1] - g.x[0]; val dy = g.y[1] - g.y[0]; val dz = g.z[1] - g.z[0]
        assertTrue(dx * (g.vx[1] - g.vx[0]) + dy * (g.vy[1] - g.vy[0]) + dz * (g.vz[1] - g.vz[0]) > 0)
    }

    @Test
    fun closedBoxConservesEnergyOverThousandsOfCollisions() {
        val g = box()
        g.initMaxwell(200, 0.0533)                    // 300 K in model units
        val e0 = g.kineticEnergy()
        g.step(30.0)
        val e1 = g.kineticEnergy()
        println("closed box: ${g.collisionCount} collisions in 30 s, dE/E = ${(e1 - e0) / e0}")
        assertTrue(g.collisionCount > 2000)
        assertEquals(1.0, e1 / e0, 1e-10)
    }

    @Test
    fun wallPressureIsKineticTheoryOnEveryAxis() {
        // point molecules (collisions off): each wall pair feels p_k = N m <v_k²> / V exactly —
        // the textbook derivation, axis by axis (without collisions the axes never equalise)
        val g = box(); g.collisions = false
        g.initMaxwell(200, 1.0)
        g.step(1.0); g.takePressure()
        g.step(400.0)
        val pk = DoubleArray(3); g.pressureAxes(pk)
        val vk = DoubleArray(3)
        for (i in 0 until g.n) { vk[0] += g.vx[i] * g.vx[i]; vk[1] += g.vy[i] * g.vy[i]; vk[2] += g.vz[i] * g.vz[i] }
        for (k in 0 until 3) {
            val kin = vk[k] / g.volume
            println("axis $k: p = ${"%.2f".format(pk[k])}, N<v_k²>/V = ${"%.2f".format(kin)}, ratio ${"%.4f".format(pk[k] / kin)}")
            assertEquals(1.0, pk[k] / kin, 0.01)
        }
    }

    @Test
    fun dilutGasObeysPvEqualsNkTAndBoyle() {
        // tiny molecules (eta ~ 1e-7) that still collide often enough to keep the pressure isotropic
        fun run(xLo: Double): Pair<Double, DoubleArray> {
            val g = HardSphereGas(200, 0.004, 21L).apply { setBox(xLo, 0.35, 0.17, 0.14) }
            g.initMaxwell(200, 1.0)
            g.step(2.0); g.takePressure(); g.step(300.0)
            val pk = DoubleArray(3); g.pressureAxes(pk)
            val p = g.takePressure()
            val z = p * g.volume / (g.n * g.temperature())
            println("dilute gas V = ${"%.4f".format(g.volume)} m3: pV/NkT = ${"%.4f".format(z)}, p_x:p_y:p_z = ${pk.map { "%.3f".format(it / p) }}")
            assertEquals(1.0, z, 0.015)
            for (k in 0 until 3) assertEquals(1.0, pk[k] / p, 0.03)    // Pascal: pressure is isotropic
            return p to pk
        }
        val p1 = run(-0.35).first
        val p2 = run(0.0).first                                         // half the volume
        println("Boyle: V/2 -> p2/p1 = ${p2 / p1}")
        assertEquals(2.0, p2 / p1, 0.04)
    }

    @Test
    fun hardSphereExcessPressureIsTheExcludedVolume() {
        // real molecules have size: Z = pV/NkT > 1 (van der Waals' b). Bulk theory: Carnahan-Starling.
        // In a closed box the molecules within one diameter of a wall have fewer neighbours, so the
        // excess is smaller and approaches the bulk value as d/L -> 0.
        fun z(n: Int, d: Double, half: Double, secs: Double, seed: Long): Pair<Double, Double> {
            val g = HardSphereGas(n, d, seed).apply { setBox(-half, half, half, half) }
            g.maxStep = 1.0 / 4000
            g.initMaxwell(n, 1.0)
            g.step(1.0); g.takePressure(); g.step(secs)
            val zz = g.takePressure() * g.volume / (g.n * g.temperature())
            val eta = HardSphereEos.packing(g.n / g.volume, d)
            return zz to (zz - 1) / (HardSphereEos.zCarnahanStarling(eta) - 1)
        }
        val (z16, r16) = z(188, 0.025, 0.2, 60.0, 31L)
        val (z32, r32) = z(1500, 0.0125, 0.2, 8.0, 32L)
        println("hard spheres eta~0.024: d/L=1/16 Z=${"%.4f".format(z16)} (excess ${"%.2f".format(r16)} of bulk); d/L=1/32 Z=${"%.4f".format(z32)} (excess ${"%.2f".format(r32)} of bulk)")
        assertTrue(r32 in 0.85..1.05)
        assertTrue(r16 in 0.55..1.0)
        // the demo's own box (d = 20 mm, 200 molecules, full volume): the readout's pV/NkT
        val g = box(); g.initMaxwell(200, 0.0533); g.step(1.0); g.takePressure(); g.step(200.0)
        val zd = g.takePressure() * g.volume / (g.n * g.temperature())
        val eta = HardSphereEos.packing(g.n / g.volume, g.diameter)
        println("demo box: eta = ${"%.4f".format(eta)}, pV/NkT = ${"%.4f".format(zd)} (bulk CS ${"%.4f".format(HardSphereEos.zCarnahanStarling(eta))})")
        assertTrue(zd > 1 + 0.5 * 4 * eta && zd < HardSphereEos.zCarnahanStarling(eta) + 0.01)
    }

    @Test
    fun equalSpeedsRelaxToMaxwellBoltzmann() {
        // Boltzmann 1872: start every molecule at the same speed; collisions alone produce Maxwell
        val t = 0.0533
        val g = box(); g.initEqualSpeed(200, t)
        val kurt0 = moment4(g) / moment2(g).pow(2)
        g.step(20.0)                                   // ~25 collision times
        var mean = 0.0; var m2 = 0.0; var m4 = 0.0; var samples = 0
        val nb = 12; val vMax = 4 * sqrt(t); val h = DoubleArray(nb); val acc = DoubleArray(nb)
        repeat(400) {
            g.step(0.1)
            for (i in 0 until g.n) { val s = g.speed(i); mean += s; m2 += s * s; m4 += s * s * s * s }
            g.speedHistogram(h, vMax); for (k in 0 until nb) acc[k] += h[k]
            samples += g.n
        }
        mean /= samples; m2 /= samples; m4 /= samples
        val tv = (0 until nb).sumOf { k -> abs(acc[k] / 400 - Maxwell.binFraction(k * vMax / nb, (k + 1) * vMax / nb, t)) } / 2
        println("relaxation: kurtosis <v^4>/<v^2>^2 ${"%.3f".format(kurt0)} -> ${"%.3f".format(m4 / (m2 * m2))} (Maxwell 5/3)")
        println("  mean speed ${"%.5f".format(mean)} vs sqrt(8T/pi) ${"%.5f".format(Maxwell.meanSpeed(t))}; rms ${"%.5f".format(sqrt(m2))} vs sqrt(3T) ${"%.5f".format(Maxwell.rmsSpeed(t))}")
        println("  histogram total-variation distance from Maxwell bins = ${"%.4f".format(tv)}")
        assertEquals(1.0, kurt0, 1e-9)                // a delta distribution at the start
        assertEquals(Maxwell.meanSpeed(t), mean, 0.01 * Maxwell.meanSpeed(t))
        assertEquals(Maxwell.rmsSpeed(t), sqrt(m2), 1e-6) // energy is conserved exactly
        assertEquals(5.0 / 3.0, m4 / (m2 * m2), 0.05)
        assertTrue(tv < 0.03)
    }

    @Test
    fun collisionsEstablishEquipartition() {
        val t = 0.0533
        val g = box(); g.initAllInX(200, t)
        g.step(20.0)
        var sx = 0.0; var sy = 0.0; var sz = 0.0
        repeat(300) { g.step(0.1); for (i in 0 until g.n) { sx += g.vx[i] * g.vx[i]; sy += g.vy[i] * g.vy[i]; sz += g.vz[i] * g.vz[i] } }
        val tot = sx + sy + sz
        println("equipartition from all-in-x: <vx²>:<vy²>:<vz²> = ${"%.3f".format(3 * sx / tot)} : ${"%.3f".format(3 * sy / tot)} : ${"%.3f".format(3 * sz / tot)}")
        for (s in listOf(sx, sy, sz)) assertEquals(1.0, 3 * s / tot, 0.05)
    }

    @Test
    fun heaterFloorBringsGasToWallTemperature() {
        val g = box(); g.initMaxwell(150, 0.0533)
        g.thermalFloor = true; g.wallT = 2 * 0.0533
        g.step(40.0)
        var tm = 0.0; repeat(200) { g.step(0.1); tm += g.temperature() }
        tm /= 200
        println("heater: T_gas/T_wall = ${tm / g.wallT} after ${g.floorHits} floor hits")
        assertEquals(1.0, tm / g.wallT, 0.03)
    }

    @Test
    fun slowCompressionHeatsTheGasAlongTheAdiabat() {
        // First law with no heat flow: dU = -p dV, U = 3/2 N k T (hard spheres have no potential
        // energy), p = Z N k T / V. For point molecules Z = 1 -> Poisson T V^(2/3) = const, halving V
        // gives T x 2^(2/3) = 1.587. Our molecules have size: Z = 1 + b n (second virial, exact at
        // low density), so ln(T2/T1) = 2/3 [ln(V1/V2) + b (n2 - n1)]. b is MEASURED in this box first.
        val g = box(seed = 9L); g.initMaxwell(200, 0.0533)
        g.step(1.0); g.takePressure(); g.step(150.0)
        val z1 = g.takePressure() * g.volume / (g.n * g.temperature())
        val b = (z1 - 1) / (g.n / g.volume)
        val t1 = g.temperature(); val v1 = g.volume
        g.pistonMaxSpeed = 0.004                       // 1 % of v_rms: quasi-static
        g.pistonTarget = g.xLo + 0.35
        g.step(0.35 / 0.004 + 1)
        val v2 = g.volume; val t2 = g.temperature()
        val poisson = (v1 / v2).pow(2.0 / 3.0)
        val hs = kotlin.math.exp(2.0 / 3.0 * (kotlin.math.ln(v1 / v2) + b * (g.n / v2 - g.n / v1)))
        println("adiabatic: V1/V2 = ${v1 / v2}, measured Z1 = ${"%.4f".format(z1)}; T2/T1 = ${"%.4f".format(t2 / t1)} vs hard-sphere adiabat ${"%.4f".format(hs)} (point-molecule Poisson ${"%.4f".format(poisson)})")
        assertEquals(2.0, v1 / v2, 1e-6)
        assertEquals(hs, t2 / t1, 0.01 * hs)
        assertTrue(t2 / t1 > poisson)                  // finite-size molecules heat MORE than points
        // releasing it again cools it back (reversible to within the irreversibility of finite speed)
        g.pistonTarget = -0.35; g.step(0.35 / 0.004 + 1)
        println("  expanded back: T/T1 = ${g.temperature() / t1}")
        assertEquals(1.0, g.temperature() / t1, 0.01)
    }

    @Test
    fun maxwellFormulasAndScaling() {
        val t = 0.37
        var s = 0.0; var m = 0.0; val dv = 1e-4
        var v = dv / 2
        while (v < 12 * sqrt(t)) { val f = Maxwell.pdf(v, t); s += f * dv; m += v * f * dv; v += dv }
        assertEquals(1.0, s, 1e-6)
        assertEquals(Maxwell.meanSpeed(t), m, 1e-6)
        assertEquals(1.0, Maxwell.cdf(50.0, t), 1e-7)
        assertEquals(0.0, Maxwell.cdf(0.0, t), 1e-7)
        assertEquals(0.8427007929, Maxwell.erf(1.0), 2e-7)
        // ratios of the three characteristic speeds are fixed: sqrt(2) : sqrt(8/pi) : sqrt(3)
        assertEquals(sqrt(8 / PI / 2), Maxwell.meanSpeed(t) / Maxwell.mostProbable(t), 1e-12)
        // real nitrogen: v_rms(300 K) = 517 m/s (textbook), <v> = 476 m/s
        val vr = GasScale.realVrms(300.0)
        println("N2 at 300 K: v_rms = $vr m/s, <v> = ${vr * sqrt(8 / (3 * PI))} m/s")
        assertEquals(517.0, vr, 1.0)
        // the scaling: 300 K is drawn at v_rms 0.40 m/s
        assertEquals(0.40, sqrt(3 * GasScale.modelT(300.0)), 1e-12)
        val perBall = GasScale.realPerBall(150, 0.70 * 0.34 * 0.28)
        println("each of 150 balls in the 0.0666 m3 box at 1 atm stands for ${"%.3g".format(perBall)} N2 molecules")
        assertEquals(1.07e22, perBall, 0.03e22)
    }

    @Test
    fun unityRandNHadOneThirdTheVariance() {
        // the Unity build's "~N(0,1)": (U+U+U-1.5)*1.1547. Var = 3*(1/12)*1.1547² = 1/3.
        val r = java.util.Random(3)
        var s2 = 0.0; val n = 400000
        repeat(n) { val x = (r.nextDouble() + r.nextDouble() + r.nextDouble() - 1.5) * 1.1547; s2 += x * x }
        println("Unity RandN variance = ${s2 / n} (should have been 1): reset gas sat at T/3")
        assertEquals(1.0 / 3.0, s2 / n, 0.005)
    }

    private fun moment2(g: HardSphereGas) = (0 until g.n).sumOf { g.speed(it).pow(2) } / g.n
    private fun moment4(g: HardSphereGas) = (0 until g.n).sumOf { g.speed(it).pow(4) } / g.n
}
