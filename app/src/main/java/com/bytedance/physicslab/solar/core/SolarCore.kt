// SolarCore — pure Kotlin, double precision, ZERO Android / SpatialSDK references.
//
// A faithful port of the Unity app's C# PhysicsLab.Core.Solar. It is kept dependency-free on
// purpose: it runs as a plain JVM unit test, and the port is verified number-for-number against a
// golden file dumped from the ORIGINAL C# implementation (see SolarCoreParityTest). That is what
// lets us say the two stacks show the same physics, rather than merely similar-looking orbits.
//
// The eight planets ride JPL's "Keplerian Elements for Approximate Positions of the Major Planets"
// (https://ssd.jpl.nasa.gov/planets/approx_pos.html): elements at J2000 plus per-century rates,
// valid 1800-2050 AD to ~10-20 arcsec. Elements (not N-body) are used because they hand a learner
// the real eccentricity, real inclinations, real periods — and Mars retrograde when viewed from
// Earth — for free, with no integration drift.

package com.bytedance.physicslab.solar.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class Planet(
    val name: String, val chinese: String,
    // elements at J2000 (AU, degrees)
    val a: Double, val e: Double, val i: Double, val l: Double, val peri: Double, val node: Double,
    // rates per Julian century
    val da: Double, val de: Double, val di: Double, val dl: Double, val dPeri: Double, val dNode: Double,
    val radiusKm: Double, val periodDays: Double, val axialTiltDeg: Double,
    val hasRings: Boolean = false,
)

/** Heliocentric ecliptic J2000 position in AU. x -> vernal equinox, z -> ecliptic north pole. */
data class Vec3d(val x: Double, val y: Double, val z: Double) {
    val length: Double get() = sqrt(x * x + y * y + z * z)
}

object Solar {
    const val SUN_RADIUS_KM = 695700.0
    const val EARTH_RADIUS_KM = 6371.0
    const val J2000 = 2451545.0

    /** JPL approximate elements, 1800-2050 AD. Order: Mercury..Neptune. */
    val planets: List<Planet> = listOf(
        Planet("MERCURY", "水星",
            0.38709927, 0.20563593, 7.00497902, 252.25032350, 77.45779628, 48.33076593,
            0.00000037, 0.00001906, -0.00594749, 149472.67411175, 0.16047689, -0.12534081,
            2439.7, 87.969, 0.03),
        Planet("VENUS", "金星",
            0.72333566, 0.00677672, 3.39467605, 181.97909950, 131.60246718, 76.67984255,
            0.00000390, -0.00004107, -0.00078890, 58517.81538729, 0.00268329, -0.27769418,
            6051.8, 224.701, 177.4),
        Planet("EARTH", "地球",
            1.00000261, 0.01671123, -0.00001531, 100.46457166, 102.93768193, 0.0,
            0.00000562, -0.00004392, -0.01294668, 35999.37244981, 0.32327364, 0.0,
            6371.0, 365.256, 23.44),
        Planet("MARS", "火星",
            1.52371034, 0.09339410, 1.84969142, -4.55343205, -23.94362959, 49.55953891,
            0.00001847, 0.00007882, -0.00813131, 19140.30268499, 0.44441088, -0.29257343,
            3389.5, 686.980, 25.19),
        Planet("JUPITER", "木星",
            5.20288700, 0.04838624, 1.30439695, 34.39644051, 14.72847983, 100.47390909,
            -0.00011607, -0.00013253, -0.00183714, 3034.74612775, 0.21252668, 0.20469106,
            69911.0, 4332.59, 3.13),
        Planet("SATURN", "土星",
            9.53667594, 0.05386179, 2.48599187, 49.95424423, 92.59887831, 113.66242448,
            -0.00125060, -0.00050991, 0.00193609, 1222.49362201, -0.41897216, -0.28867794,
            58232.0, 10759.22, 26.73, hasRings = true),
        Planet("URANUS", "天王星",
            19.18916464, 0.04725744, 0.77263783, 313.23810451, 170.95427630, 74.01692503,
            -0.00196176, -0.00004397, -0.00242939, 428.48202785, 0.40805281, 0.04240589,
            25362.0, 30688.5, 97.77, hasRings = true),
        Planet("NEPTUNE", "海王星",
            30.06992276, 0.00859048, 1.77004347, -55.12002969, 44.96476227, 131.78422574,
            0.00026291, 0.00005105, 0.00035372, 218.45945325, -0.32241464, -0.00508664,
            24622.0, 60182.0, 28.32),
    )

    private const val DEG2RAD = PI / 180.0

    /** Julian centuries past J2000 for a Julian Day number. */
    fun centuriesFromJd(jd: Double): Double = (jd - J2000) / 36525.0

    /** Heliocentric ecliptic J2000 position in AU at time [t] (Julian centuries past J2000). */
    fun position(p: Planet, t: Double): Vec3d {
        val a = p.a + p.da * t
        val e = p.e + p.de * t
        val incl = (p.i + p.di * t) * DEG2RAD
        val lMean = p.l + p.dl * t
        val peri = p.peri + p.dPeri * t
        val node = p.node + p.dNode * t

        val w = (peri - node) * DEG2RAD            // argument of perihelion
        val om = node * DEG2RAD
        val m = wrap180(lMean - peri) * DEG2RAD

        val ecc = keplerSolve(m, e)

        // position in the orbital plane
        val xp = a * (cos(ecc) - e)
        val yp = a * sqrt(max(0.0, 1.0 - e * e)) * sin(ecc)

        val cw = cos(w); val sw = sin(w)
        val cO = cos(om); val sO = sin(om)
        val cI = cos(incl); val sI = sin(incl)

        return Vec3d(
            (cw * cO - sw * sO * cI) * xp + (-sw * cO - cw * sO * cI) * yp,
            (cw * sO + sw * cO * cI) * xp + (-sw * sO + cw * cO * cI) * yp,
            (sw * sI) * xp + (cw * sI) * yp,
        )
    }

    /** Newton solve of Kepler's equation M = E - e sin E. */
    fun keplerSolve(m: Double, e: Double): Double {
        var ecc = m + e * sin(m)
        for (k in 0 until 40) {
            val dM = m - (ecc - e * sin(ecc))
            val dE = dM / (1.0 - e * cos(ecc))
            ecc += dE
            if (abs(dE) < 1e-12) break
        }
        return ecc
    }

    /** Matches C#'s `%` semantics exactly: truncated remainder, sign follows the dividend. */
    fun wrap180(deg: Double): Double {
        var d = deg % 360.0
        if (d > 180.0) d -= 360.0
        if (d < -180.0) d += 360.0
        return d
    }

    fun sunDistance(p: Planet, t: Double): Double = position(p, t).length

    // ---------------- display scaling ----------------
    // spread = 0 is EXACTLY proportional (no additive floor — an affine map made the drawn
    // Mercury/Venus and Earth/Mars orbits cross, which real orbits never do). spread = 1 spaces
    // the orbits evenly by rank so all eight are legible. The student morphs between the two.

    const val RIM_METERS = 0.78     // outermost orbit at full spread
    const val INNER_METERS = 0.115  // Mercury's orbit at full spread

    fun orbitDisplayRadius(planetIndex: Int, aAu: Double, spread: Double): Double {
        val trueR = RIM_METERS * (aAu / planets[7].a)
        val rankR = INNER_METERS + (RIM_METERS - INNER_METERS) * ((planetIndex + 1) / 8.0)
        return trueR * (1.0 - spread) + rankR * spread
    }

    /** Sun shrinks with the orbits so the honest (spread=0) setting never swallows Mercury. */
    fun sunDisplayRadius(spread: Double): Double {
        val sunAu = SUN_RADIUS_KM / 1.495978707e8
        val trueR = RIM_METERS * (sunAu / planets[7].a)
        val shown = 0.055
        return max(0.006, trueR * (1.0 - spread) + shown * spread)
    }

    /** 0.4 power keeps size ORDER and the rocky/gas-giant split while staying visible. */
    fun bodyDisplayRadius(radiusKm: Double, exaggeration: Double = 1.0): Double =
        0.0125 * (radiusKm / EARTH_RADIUS_KM).pow(0.4) * exaggeration

    fun degPerSecond(p: Planet, daysPerSecond: Double): Double = 360.0 / p.periodDays * daysPerSecond
}
