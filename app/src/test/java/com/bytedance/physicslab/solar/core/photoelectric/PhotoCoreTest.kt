package com.bytedance.physicslab.solar.core.photoelectric

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoCoreTest {
    private val na = Photo.METALS[1]; private val cs = Photo.METALS[0]; private val zn = Photo.METALS[2]

    @Test
    fun photonEnergiesAndThresholdsAreReal() {
        assertEquals(1239.842, Photo.HC_EV_NM, 1e-3)
        assertEquals(2.4797, Photo.photonEv(500.0), 1e-4)
        assertEquals(5.996e14, Photo.freqHz(500.0), 1e11)
        // threshold wavelengths: Cs 579 nm (yellow), Na 544 nm (green), Zn 286 nm (UV-C)
        println("thresholds: Cs ${"%.1f".format(Photo.thresholdNm(2.14))} nm, Na ${"%.1f".format(Photo.thresholdNm(2.28))} nm, Zn ${"%.1f".format(Photo.thresholdNm(4.33))} nm")
        assertEquals(579.4, Photo.thresholdNm(cs.workEv), 0.1)
        assertEquals(543.8, Photo.thresholdNm(na.workEv), 0.1)
        assertEquals(286.3, Photo.thresholdNm(zn.workEv), 0.1)
        assertEquals(5.513e14, Photo.thresholdHz(na.workEv), 1e11)
        // electron speed for 1 eV: 5.93e5 m/s
        assertEquals(5.931e5, Photo.speedOf(1.0), 1e3)
    }

    @Test
    fun unityAuditDefaultsAndZinc() {
        // Unity: default RED 0.46 PHz on caesium 2.1 eV -> below threshold, nothing on screen
        val hfRed = Photo.H * 0.46e15 / Photo.QE
        println("Unity default: RED 0.46 PHz -> hf = ${"%.3f".format(hfRed)} eV < W(Cs) 2.1 eV")
        assertTrue(hfRed < 2.1)
        // Unity's bluest source 'UV' 0.90 PHz = 3.72 eV can never free an electron from zinc (4.3 eV)
        val unityFreqs = doubleArrayOf(0.46, 0.52, 0.57, 0.64, 0.73, 0.90)
        assertTrue(unityFreqs.all { Photo.H * it * 1e15 / Photo.QE < 4.3 })
        // this port: the default (405 nm on Na) emits, and the 254 nm Hg line does free zinc electrons
        assertEquals(0.781, Photo.ekMax(405.0, na.workEv), 1e-3)
        assertEquals(0.551, Photo.ekMax(254.0, zn.workEv), 1e-3)
        assertEquals(0.0, Photo.ekMax(650.0, cs.workEv), 0.0)
    }

    @Test
    fun millikanSlopeIsPlanckOverE_andSameForEveryMetal() {
        for (m in Photo.METALS) {
            val l1 = 220.0; val l2 = 250.0
            val slope = (Photo.stoppingVoltage(l1, m.workEv) - Photo.stoppingVoltage(l2, m.workEv)) / (Photo.freqHz(l1) - Photo.freqHz(l2))
            assertEquals(Photo.H / Photo.QE, slope, 1e-21)
        }
        println("U_c(f) slope = h/e = ${"%.4e".format(Photo.H / Photo.QE)} V s")
    }

    @Test
    fun stoppingVoltageIndependentOfIntensity_currentProportionalToIntensity() {
        val tube = PhotoTube()
        val ek = Photo.ekMax(405.0, na.workEv)
        for (p in listOf(0.1e-3, 0.5e-3, 1e-3)) {
            val iSat = Photo.saturationCurrent(405.0, na.workEv, p)
            assertEquals(0.0, tube.current(-ek, iSat, ek), 1e-15)                 // zero at -U_c ...
            assertEquals(0.0, tube.current(-ek - 0.3, iSat, ek), 0.0)             // ... and beyond
            assertTrue(tube.current(-0.9 * ek, iSat, ek) > 0)                      // but not before
        }
        val i1 = tube.current(1.0, Photo.saturationCurrent(405.0, na.workEv, 0.3e-3), ek)
        val i2 = tube.current(1.0, Photo.saturationCurrent(405.0, na.workEv, 0.6e-3), ek)
        assertEquals(2.0, i2 / i1, 1e-12)
        val iSat = Photo.saturationCurrent(405.0, na.workEv, 1e-3)
        println("405 nm, 1 mW on Na: yield ${"%.4f".format(Photo.yield(405.0, na.workEv))}, I_sat = ${"%.3f".format(iSat * 1e6)} uA")
        assertTrue(iSat in 1e-7..1e-4)                                                // micro-amps, like a real phototube
    }

    @Test
    fun ivCurveShape_matchesInfinitePlateTheoryAndSaturates() {
        // with a huge anode, the retarding part must equal (1-s) + s ln s exactly (Lambert + uniform E)
        val big = PhotoTube(anodeRatio = 1e6, spotRatio = 0.0, n = 20000)
        for (s in listOf(-0.9, -0.6, -0.3, -0.1)) {
            assertEquals(PhotoTube.infinitePlateFraction(s), big.fraction(s), 0.01)
        }
        // the real tube: monotonic; at U = 0 only electrons within ~50 deg of the normal hit the anode
        // (Lambert: P(theta < a) = sin^2 a), so ~60 % — forward voltage pulls the rest in (saturation)
        val t = PhotoTube()
        var last = -1.0; var s = -1.05
        while (s < 40) { val f = t.fraction(s); assertTrue(f >= last - 1e-9); last = f; s += 0.05 }
        println("tube F(s): F(-0.5)=${"%.3f".format(t.fraction(-0.5))} F(0)=${"%.3f".format(t.fraction(0.0))} F(2)=${"%.3f".format(t.fraction(2.0))} F(5)=${"%.3f".format(t.fraction(5.0))} F(10)=${"%.3f".format(t.fraction(10.0))}")
        assertTrue(t.fraction(0.0) in 0.5..0.7)
        assertTrue(t.fraction(10.0) > 0.98)
        assertEquals(0.0, t.fraction(-1.0), 1e-12)
    }

    @Test
    fun fowlerYieldVanishesAtThresholdAndGrowsQuadratically() {
        val l0 = Photo.thresholdNm(cs.workEv)
        assertEquals(0.0, Photo.yield(l0 + 1, cs.workEv), 0.0)
        val y1 = Photo.yield(Photo.HC_EV_NM / (cs.workEv + 0.1), cs.workEv)
        val y2 = Photo.yield(Photo.HC_EV_NM / (cs.workEv + 0.2), cs.workEv)
        assertEquals(4.0, y2 / y1, 1e-6)
    }

    @Test
    fun spectralColoursLookRight() {
        val red = Photo.spectralRgb(650.0); val green = Photo.spectralRgb(530.0); val blue = Photo.spectralRgb(460.0)
        assertTrue(red[0] > 0.9 && red[1] < 0.1)
        assertTrue(green[1] > 0.9 && green[0] < 0.5)
        assertTrue(blue[2] > 0.9 && blue[0] < 0.1)
    }
}
