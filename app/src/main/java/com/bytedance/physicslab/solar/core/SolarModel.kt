// SolarModel — the whole simulation + display mapping, pure Kotlin (no SDK, no Android).
//
// It is the Unity SolarSystemDemo's maths (OrbitR, SunK, SizeK, MapToDisplay, BakeOrbitShape,
// MoonDirEcl, MoonMetrics, DateString) lifted out of the view so it can be unit-tested and so the
// SpatialSDK scene is a thin "apply these numbers to entities" layer. The scene owns no physics.
//
// DISPLAY FRAME. SpatialSDK is RIGHT-handed (+X right, +Y up, +Z toward the viewer); Unity is
// left-handed. Unity mapped ecliptic (x,y,z) -> (x, z, y). The orientation-preserving equivalent
// here is (x, z, -y): seen from ecliptic north (from +Y, looking down) both draw the planets
// running counter-clockwise, exactly as they do in the sky.

package com.bytedance.physicslab.solar.core

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Display-space point in metres (SpatialSDK right-handed). */
data class P3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: P3) = P3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: P3) = P3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = P3(x * s, y * s, z * s)
    val length get() = sqrt(x * x + y * y + z * z)
}

object Moon {
    const val RADIUS_KM = 1737.4
    const val INC_DEG = 5.145
    const val L0 = 218.3164477
    const val L_RATE = 13.17639648
    const val NODE0 = 125.0445479
    const val NODE_RATE = -0.0529539
    val SYNODIC_DAYS = 360.0 / (L_RATE - Solar.planets[2].dl / 36525.0)

    /** Unit Earth->Moon direction in the ecliptic frame (Unity MoonDirEcl). */
    fun dirEcl(jd: Double): Vec3d {
        val d = jd - Solar.J2000
        val node = Math.toRadians(NODE0 + NODE_RATE * d)
        val u = Math.toRadians(L0 + L_RATE * d) - node
        val inc = Math.toRadians(INC_DEG)
        val cu = cos(u); val su = sin(u); val cn = cos(node); val sn = sin(node)
        val ci = cos(inc); val si = sin(inc)
        return Vec3d(cn * cu - sn * su * ci, sn * cu + cn * su * ci, su * si)
    }

    /** Phase angle 0 = new, 90 = first quarter, 180 = full (Moon longitude minus Sun longitude). */
    fun phaseDeg(jd: Double): Double {
        val e = Solar.position(Solar.planets[2], Solar.centuriesFromJd(jd))
        val m = dirEcl(jd)
        val lamSun = atan2(-e.y, -e.x)
        val lamMoon = atan2(m.y, m.x)
        var ph = Math.toDegrees(lamMoon - lamSun) % 360.0
        if (ph < 0) ph += 360.0
        return ph
    }

    fun illuminatedFraction(jd: Double) = 0.5 * (1.0 - cos(Math.toRadians(phaseDeg(jd))))
}

class SolarModel {
    // ---- user state (defaults = Unity's) ----
    var jd = Solar.J2000
    var daysPerSecond = 15.0
    // Planet size cannot simply be raised: at Unity's defaults the inner planets are already within
    // a few mm of touching (a scan of SCALE x SIZE finds Earth ~11.6 mm is the ceiling with no body
    // overlapping a neighbour's orbit band). Visibility at Shared-Space distance (~2.5 m) therefore
    // comes from SCENE scale + the Earth-Moon close-up in SolarScene, not from fatter planets.
    var spread = 0.45           // SCALE: 0 = true proportion, 1 = even rank spacing
    var sizeExag = 1.8          // SIZE
    var zoom = 1.0              // whole-scene magnifier (UI), on top of SolarScene.BASE_SCALE
    var followEarth = false     // Earth-Moon close-up: keep Earth at the volume centre
    var showMoon = true
    var selected = -1

    // ---- derived scale (Unity SolarSystemDemo) ----
    val sunK get() = lerp(0.12, 1.0, spread)
    val sizeK get() = sizeExag * sunK
    val sunRadius get() = SUN_RADIUS_M * sunK

    fun orbitR(i: Int) = Solar.orbitDisplayRadius(i, Solar.planets[i].a, spread)
    fun bodyR(i: Int) = Solar.bodyDisplayRadius(Solar.planets[i].radiusKm, sizeK)

    fun step(dt: Double) {
        jd = (jd + daysPerSecond * min(dt, 0.05)).coerceIn(JD_MIN, JD_MAX)
    }

    val t get() = Solar.centuriesFromJd(jd)

    /** Unity MapToDisplay: keep the REAL r/a of the moment so ellipses stay ellipses at any SCALE. */
    fun planetPos(i: Int): P3 {
        val p = Solar.planets[i]
        val tt = t
        val e = Solar.position(p, tt)
        val r = max(1e-9, e.length)
        var aNow = p.a + p.da * tt
        if (aNow < 1e-9) aNow = p.a
        val disp = orbitR(i) * (r / aNow)
        return ecl(e.x / r * disp, e.y / r * disp, e.z / r * disp)
    }

    /** Unity BakeOrbitShape: the osculating ellipse at unit a, frozen at the current epoch. */
    fun orbitShapeUnit(i: Int, n: Int = 128): FloatArray {
        val p = Solar.planets[i]
        val tt = t
        val q = p.copy(
            a = p.a + p.da * tt, e = p.e + p.de * tt, i = p.i + p.di * tt,
            peri = p.peri + p.dPeri * tt, node = p.node + p.dNode * tt,
            da = 0.0, de = 0.0, di = 0.0, dl = 0.0, dPeri = 0.0, dNode = 0.0)
        val aNow = if (q.a < 1e-9) p.a else q.a
        val out = FloatArray(n * 3)
        for (k in 0 until n) {
            val lq = q.copy(l = q.peri + 360.0 * k / n)   // closed loop: n distinct points
            val e = Solar.position(lq, 0.0)
            val r = max(1e-9, e.length)
            val f = (r / aNow) / r
            val d = ecl(e.x * f, e.y * f, e.z * f)
            out[k * 3] = d.x; out[k * 3 + 1] = d.y; out[k * 3 + 2] = d.z
        }
        return out
    }

    /** Speed of planet i in km/s by central difference over a small slice of its own period. */
    fun speedKms(i: Int): Double {
        val sp = Solar.planets[i]
        val dDay = max(0.01, sp.periodDays / 2000.0)
        val a = Solar.position(sp, Solar.centuriesFromJd(jd - dDay))
        val b = Solar.position(sp, Solar.centuriesFromJd(jd + dDay))
        val d = Vec3d(b.x - a.x, b.y - a.y, b.z - a.z).length
        return d * AU_KM / (2.0 * dDay * 86400.0)
    }

    fun sunDistanceAu(i: Int) = Solar.sunDistance(Solar.planets[i], t)

    // ---- Moon (Unity MoonMetrics / UpdateMoon) ----

    /** (orbit radius, moon radius) in metres, or null when there is genuinely no room. */
    fun moonMetrics(): Pair<Float, Float>? {
        val eR = Solar.bodyDisplayRadius(Solar.EARTH_RADIUS_KM, sizeK)
        val mR = Solar.bodyDisplayRadius(Moon.RADIUS_KM, sizeK)
        val sep = eR + mR
        val rE = orbitR(2)
        val lo = sep * 1.70
        val crashR = sunRadius * 1.13
        val hi = min(rE * 0.40, rE - crashR - mR - 0.004)
        if (hi < lo) return null
        return (sep * 2.35).coerceIn(lo, hi).toFloat() to mR.toFloat()
    }

    /** Moon position relative to the Earth, display metres. */
    fun moonOffset(orbitR: Float): P3 {
        val m = Moon.dirEcl(jd)
        return ecl(m.x * orbitR, m.y * orbitR, m.z * orbitR)
    }

    fun moonOrbitPath(orbitR: Float, n: Int = 96): FloatArray {
        val node = Math.toRadians(Moon.NODE0 + Moon.NODE_RATE * (jd - Solar.J2000))
        val inc = Math.toRadians(Moon.INC_DEG)
        val cn = cos(node); val sn = sin(node); val ci = cos(inc); val si = sin(inc)
        val out = FloatArray(n * 3)
        for (k in 0 until n) {
            val u = 2.0 * PI * k / n
            val cu = cos(u); val su = sin(u)
            val d = ecl((cn * cu - sn * su * ci) * orbitR, (sn * cu + cn * su * ci) * orbitR, su * si * orbitR)
            out[k * 3] = d.x; out[k * 3 + 1] = d.y; out[k * 3 + 2] = d.z
        }
        return out
    }

    companion object {
        const val SUN_RADIUS_M = 0.055          // drawn photosphere radius at spread = 1
        const val AU_KM = 1.495978707e8
        const val JD_MIN = 2378496.5            // 1800-01-01, start of the JPL element fit
        const val JD_MAX = 2469807.5            // 2050-01-01, end of it

        /** Real flattening (Req-Rpol)/Req — Saturn 9.8%, Jupiter 6.5%. */
        val OBLATE = floatArrayOf(0f, 0f, 0.0034f, 0.0059f, 0.0649f, 0.0980f, 0.0229f, 0.0171f)
        /** Visual spin rates, deg/s (rotation periods are not in the JPL set; axes ARE real). */
        val SPIN = floatArrayOf(9f, 7f, 22f, 21f, 34f, 31f, 26f, 25f)

        fun ecl(x: Double, y: Double, z: Double) = P3(x.toFloat(), z.toFloat(), (-y).toFloat())
        fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t

        /** Unity DateString (Richards' JD -> Gregorian). */
        fun dateString(jd: Double): String {
            val jj = floor(jd + 0.5).toLong()
            val f = jj + 1401 + (((4 * jj + 274277) / 146097) * 3) / 4 - 38
            val e = 4 * f + 3
            val g = (e % 1461) / 4
            val h = 5 * g + 2
            val day = (h % 153) / 5 + 1
            val month = ((h / 153 + 2) % 12) + 1
            val year = e / 1461 - 4716 + (12 + 2 - month) / 12
            return "%04d-%02d-%02d".format(year, month, day)
        }
    }
}
