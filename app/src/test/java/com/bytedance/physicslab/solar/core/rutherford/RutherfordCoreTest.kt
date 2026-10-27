package com.bytedance.physicslab.solar.core.rutherford

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

class RutherfordCoreTest {
    private val au = Rutherford.FOILS[0]; private val ag = Rutherford.FOILS[1]; private val al = Rutherford.FOILS[2]
    private fun deg(r: Double) = r * 180 / PI
    private fun rad(d: Double) = d * PI / 180

    @Test
    fun closestApproachAndFoilData() {
        // e^2/4 pi eps0 = alpha hbar c = 1.43996 MeV fm (CODATA 2018)
        assertEquals(1.439965, Rutherford.KE2_MEV_FM, 1e-6)
        val d = Rutherford.closestApproachFm(79, 5.5)
        println("d(5.5 MeV alpha on Au) = %.3f fm; Ag %.2f fm, Al %.2f fm".format(d,
            Rutherford.closestApproachFm(47, 5.5), Rutherford.closestApproachFm(13, 5.5)))
        assertEquals(41.366, d, 0.005)                                     // 2*79*1.43996/5.5
        assertEquals(24.61, Rutherford.closestApproachFm(47, 5.5), 0.01)
        assertEquals(6.807, Rutherford.closestApproachFm(13, 5.5), 0.005)
        // gold number density 5.90e28 m^-3 (rho 19.32 g/cm^3, M 196.97 g/mol, CRC)
        println("n: Au %.4e, Ag %.4e, Al %.4e m^-3; Au atom (Wigner-Seitz) %.0f fm = d x %.0f".format(au.n, ag.n, al.n, au.atomRadiusFm, au.atomRadiusFm / d))
        assertEquals(5.90e28, au.n, 0.01e28)
        assertEquals(5.86e28, ag.n, 0.02e28)
        assertEquals(6.03e28, al.n, 0.02e28)
        assertEquals(159.0e3, au.atomRadiusFm, 1.0e3)                       // 1.59 Angstrom
        // nuclear sizes (r0 = 1.2 fm): gold 7.0 fm, so the alpha never touches it; aluminium at 8 MeV does
        assertEquals(6.98, au.nuclearRadiusFm, 0.01)
        assertTrue(d > au.contactFm)
        assertTrue(Rutherford.closestApproachFm(13, 8.0) < al.contactFm)
        // b(theta) is the inverse of theta(b)
        for (b in listOf(1.0, 10.0, 41.0, 100.0)) assertEquals(b, Rutherford.impactParameter(Rutherford.deflection(b, d), d), 1e-9 * b)
        assertEquals(PI / 2, Rutherford.deflection(d / 2, d), 1e-12)        // b = d/2 -> 90 deg
    }

    @Test
    fun deflectionFormulaMatchesNumericalOrbitIntegration() {
        // fire from 10^5 d away on the asymptote, RK4 through the encounter, read the outgoing heading
        for (b in listOf(0.05, 0.1, 0.25, 0.5, 1.0, 2.0, 5.0, 20.0)) {
            val num = CoulombOrbit.scatter(b)
            val an = Rutherford.deflection(b, 1.0)
            val h = CoulombHyperbola(b)
            println("b = %5.2f d: theta analytic %8.4f deg, integrated %8.4f deg (diff %.1e rad); r_min %.6f vs %.6f d; |dE/E| %.1e |dL/L| %.1e, %d steps".format(
                b, deg(an), deg(num.heading), abs(num.heading - an), h.rMin, num.rMin, num.maxRelEnergyErr, num.maxRelLErr, num.steps))
            assertEquals(an, num.heading, 1e-4)
            assertEquals(h.rMin, num.rMin, 1e-4 * h.rMin)
            assertTrue("energy drift", num.maxRelEnergyErr < 1e-8)
            assertTrue("angular momentum drift", num.maxRelLErr < 1e-8)
        }
    }

    @Test
    fun closedFormHyperbolaIsANewtonianOrbitWithTheRightClock() {
        val v = DoubleArray(2)
        for (b in listOf(0.1, 0.7, 3.0)) {
            val h = CoulombHyperbola(b)
            // energy 1/2 v^2 + (1/2)/r = 1/2 and angular momentum L = -b along the whole curve
            for (f in listOf(-4.0, -1.0, 0.0, 0.5, 3.0)) {
                h.velocity(f, v)
                val x = h.x(f); val y = h.y(f)
                assertEquals(h.r(f), sqrt(x * x + y * y), 1e-9 * h.r(f))
                assertEquals(0.5, 0.5 * (v[0] * v[0] + v[1] * v[1]) + 0.5 / h.r(f), 1e-12)
                assertEquals(-b, x * v[1] - y * v[0], 1e-9 * maxOf(1.0, b))
            }
            // start ON the hyperbola at F = -3, integrate Newton for t(3) - t(-3): must land on F = +3
            val f0 = -3.0; val f1 = 3.0
            h.velocity(f0, v)
            val r = CoulombOrbit.propagate(h.x(f0), h.y(f0), v[0], v[1], tEnd = h.time(f1) - h.time(f0), eta = 5e-4)
            val err = sqrt((r.x - h.x(f1)) * (r.x - h.x(f1)) + (r.y - h.y(f1)) * (r.y - h.y(f1)))
            println("b = %.1f d: after %.3f time units the integrated alpha is %.1e d from the closed form (r = %.2f d)".format(b, r.t, err, h.r(f1)))
            assertTrue(err < 1e-7 * h.r(f1))
            // asymptotes: incoming along +x on y = b; outgoing direction theta
            h.velocity(-30.0, v); assertEquals(0.0, kotlin.math.atan2(v[1], v[0]), 1e-9)
            h.velocity(30.0, v); assertEquals(h.theta, kotlin.math.atan2(v[1], v[0]), 1e-9)
            assertEquals(b, h.y(-20.0), 1e-6)   // far away the incoming line is y = b
        }
    }

    /** n t * integral of dsigma/dOmega over [t1,t2] by Simpson — independent of the cot^2 closed form. */
    private fun integrateRutherford(t1: Double, t2: Double, dFm: Double, nt: Double): Double {
        val n = 2000; val h = (t2 - t1) / n
        var s = 0.0
        for (i in 0..n) {
            val t = t1 + i * h
            val w = if (i == 0 || i == n) 1.0 else if (i % 2 == 1) 4.0 else 2.0
            s += w * Rutherford.dSigmaDOmega(t, dFm * 1e-15) * 2 * PI * sin(t)
        }
        return nt * s * h / 3
    }

    @Test
    fun backscatterFractionFormula() {
        val d = Rutherford.closestApproachFm(79, 5.5); val nt = Rutherford.arealDensity(au)
        val p = Rutherford.fractionBeyond(PI / 2, d, nt)
        val direct = nt * PI * (d / 2 * 1e-15) * (d / 2 * 1e-15)                     // n t pi (d/2)^2
        val integ = integrateRutherford(PI / 2, PI, d, nt)
        println("Au 1 um, 5.5 MeV: P(>90 deg) = %.4e = n t pi (d/2)^2 = %.4e, integral of dsigma/dOmega %.4e -> 1 in %.0f".format(p, direct, integ, 1 / p))
        assertEquals(direct, p, 1e-12 * direct)
        assertEquals(p, integ, 1e-6 * p)
        assertEquals(7.94e-5, p, 0.03e-5)
        for (f in Rutherford.FOILS) for (e in listOf(4.0, 5.5, 8.0)) {
            val dd = Rutherford.closestApproachFm(f.z, e)
            println("  %s %.1f MeV: d = %.2f fm, P(>90) = %.3e (1 in %,.0f)".format(f.symbol, e, dd, Rutherford.fractionBeyond(PI / 2, dd, Rutherford.arealDensity(f)), 1 / Rutherford.fractionBeyond(PI / 2, dd, Rutherford.arealDensity(f))))
        }
        // Monte Carlo agrees: 5e7 alphas -> ~3970 backscattered
        val c = AlphaCounter(7)
        c.fire(5e7, d, nt, Rutherford.multipleScatteringSigma(au, d), true)
        val exp = 5e7 * p
        println("MC: %d backscattered of 5e7 (expected %.0f +- %.0f)".format(c.beyond90, exp, sqrt(exp)))
        assertEquals(exp, c.beyond90.toDouble(), 4 * sqrt(exp))
        // and the Z^2 / E^2 scaling: aluminium backscatters (13/79)^2 * (n_Al/n_Au) as often
        val pal = Rutherford.fractionBeyond(PI / 2, Rutherford.closestApproachFm(13, 5.5), Rutherford.arealDensity(al))
        assertEquals((13.0 / 79) * (13.0 / 79) * al.n / au.n, pal / p, 1e-9)
    }

    @Test
    fun monteCarloHistogramFollowsOneOverSinToTheFourth() {
        val d = Rutherford.closestApproachFm(79, 5.5); val nt = Rutherford.arealDensity(au)
        val c = AlphaCounter(1911)
        val n = 2e7
        // in 200 batches, like the demo does frame by frame
        repeat(200) { c.fire(n / 200, d, nt, Rutherford.multipleScatteringSigma(au, d), true) }
        var chi2 = 0.0; var dof = 0
        val sb = StringBuilder()
        var lo = 10.0
        while (lo < 180.0) {
            val hi = lo + 5
            val o = c.countBetween(lo, hi).toDouble()
            val e = n * integrateRutherford(rad(lo), rad(hi), d, nt)
            chi2 += (o - e) * (o - e) / e; dof++
            if (lo in listOf(10.0, 30.0, 60.0, 90.0, 120.0, 150.0, 175.0)) sb.append("  %3.0f-%3.0f deg: MC %7.0f  theory %9.1f\n".format(lo, hi, o, e))
            lo = hi
        }
        println("MC vs 1/sin^4(theta/2), 2e7 alphas on 1 um Au:\n$sb  chi2/dof = %.2f (%d bins)".format(chi2 / dof, dof))
        assertTrue("chi2/dof = ${chi2 / dof}", chi2 / dof < 1.6)
        // shape ratio: 20-25 deg vs 120-125 deg must equal the 1/sin^4 integral ratio within statistics
        val oA = c.countBetween(20.0, 25.0).toDouble(); val oB = c.countBetween(120.0, 125.0).toDouble()
        val r = integrateRutherford(rad(20.0), rad(25.0), d, nt) / integrateRutherford(rad(120.0), rad(125.0), d, nt)
        val sig = oA / oB * sqrt(1 / oA + 1 / oB)
        println("ratio 20-25 / 120-125 deg: MC %.1f, theory %.1f (+- %.1f)".format(oA / oB, r, sig))
        assertEquals(r, oA / oB, 4 * sig)
        // nothing below the cut is histogrammed except the (tiny) Gaussian core tail
        assertEquals(0L, c.countBetween(0.0, 5.0))
    }

    @Test
    fun thomsonModelNeverScattersBeyondAFewDegrees() {
        val d = Rutherford.closestApproachFm(79, 5.5); val nt = Rutherford.arealDensity(au)
        val sigma = Rutherford.multipleScatteringSigma(au, d)
        println("multiple-scattering sigma: Au 1 um 5.5 MeV %.2f deg, Au 4 MeV %.2f deg, Al 8 MeV %.2f deg".format(
            deg(sigma), deg(Rutherford.multipleScatteringSigma(au, Rutherford.closestApproachFm(79, 4.0))),
            deg(Rutherford.multipleScatteringSigma(al, Rutherford.closestApproachFm(13, 8.0)))))
        assertEquals(1.27, deg(sigma), 0.03)
        // Geiger (1910): most probable angle 0.87 deg for 4e-5 cm of gold (quoted by Rutherford 1911); same scale
        val sGeiger = deg(Rutherford.multipleScatteringSigma(au, d, 0.4e-6))
        println("sigma for 0.4 um gold: %.2f deg (Geiger measured 0.87 deg)".format(sGeiger))
        assertTrue(sGeiger > 0.87 / 2 && sGeiger < 0.87 * 2)

        val c = AlphaCounter(3)
        var maxTh = 0.0
        repeat(100) { c.fire(1e5, d, nt, sigma, rutherford = false) { th -> if (th > maxTh) maxTh = th } }
        val lg = Rutherford.thomsonLog10Beyond(PI / 2, sigma)
        println("Thomson: 1e7 alphas, largest deflection %.2f deg, backscattered %d; P(>90 deg) = 10^%.0f vs Rutherford %.1e".format(
            deg(maxTh), c.beyond90, lg, Rutherford.fractionBeyond(PI / 2, d, nt)))
        assertEquals(0L, c.beyond90)
        assertTrue("largest Thomson deflection ${deg(maxTh)} deg", deg(maxTh) < 9.0)
        assertTrue(lg < -500)
        // one Thomson atom (charge spread over the whole atom) deflects by at most d/2R ~ 1e-4 rad
        val single = Rutherford.thomsonAtomDeflection(au.atomRadiusFm / sqrt(2.0), d, au.atomRadiusFm)
        println("one Thomson gold atom: max deflection %.2e rad = %.4f deg".format(single, deg(single)))
        assertEquals(d / (2 * au.atomRadiusFm), single, 1e-12)
        assertTrue(single < 2e-4)
        // the same counter with the nucleus: 30 deg and beyond is common
        val c2 = AlphaCounter(3)
        c2.fire(1e7, d, nt, sigma, rutherford = true)
        assertTrue(c2.countBetween(30.0, 180.0) > 5000)
    }

    @Test
    fun poissonSamplerHasTheRightMeanAndSpread() {
        val c = AlphaCounter(5)
        for (m in listOf(0.3, 4.0, 25.0, 300.0)) {
            var s = 0.0; var s2 = 0.0; val n = 20000
            repeat(n) { val k = c.poisson(m).toDouble(); s += k; s2 += k * k }
            val mean = s / n; val varr = s2 / n - mean * mean
            println("Poisson(%.1f): mean %.3f var %.3f".format(m, mean, varr))
            assertEquals(m, mean, 5 * sqrt(m / n) + 0.01 * m)
            assertEquals(m, varr, 0.08 * m + 0.02)
        }
    }
}
