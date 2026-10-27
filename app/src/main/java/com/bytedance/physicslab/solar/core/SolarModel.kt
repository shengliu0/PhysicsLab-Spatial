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
import kotlin.math.abs
import kotlin.math.acos
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

    /**
     * Unit Earth->Moon direction, J2000 ecliptic. Was the Unity mean-element circle (no equation of
     * centre 6.3 deg, no evection 1.3 deg: up to ~8 deg / ~15 h off) — fine for a phase picture, useless
     * for eclipses (the Earth's shadow is 1.4 deg wide). Now the ELP series of [Lunar].
     */
    fun dirEcl(jd: Double): Vec3d {
        val m = Sky.moonGeoKm(jd); val l = m.length
        return Vec3d(m.x / l, m.y / l, m.z / l)
    }

    /** Elongation 0 = new, 90 = first quarter, 180 = full, 270 = last quarter (Moon minus Sun longitude). */
    fun phaseDeg(jd: Double): Double = Sky.elongationDeg(jd)

    /** Fraction of the disc lit, from the true Sun-Moon-Earth angle. */
    fun illuminatedFraction(jd: Double) = Sky.illuminated(jd)
}

/**
 * Geocentric Moon: the ELP-2000/82 series as published in J. Meeus, "Astronomical Algorithms" (2nd ed.,
 * Willmann-Bell 1998) chapter 47, tables 47.A and 47.B (all 60 + 60 periodic terms, the E factor for
 * terms in the Sun's anomaly, and the A1..A3 planetary/flattening terms). Accuracy ~10" in longitude,
 * ~4" in latitude — a few hundred km at the Moon, i.e. a few hundredths of an Earth radius in an
 * eclipse diagram. Time argument: Julian Day in TT (the JPL planet elements use the same scale).
 */
object Lunar {
    /** Ecliptic longitude / latitude (deg, MEAN EQUINOX OF DATE) and distance (km, centre to centre). */
    data class Pos(val lonDate: Double, val lat: Double, val distKm: Double)

    // D, M, M', F, sum_l (1e-6 deg), sum_r (1e-3 km)     — Meeus table 47.A
    private val LR = intArrayOf(
        0, 0, 1, 0, 6288774, -20905355,   2, 0, -1, 0, 1274027, -3699111,   2, 0, 0, 0, 658314, -2955968,
        0, 0, 2, 0, 213618, -569925,      0, 1, 0, 0, -185116, 48888,       0, 0, 0, 2, -114332, -3149,
        2, 0, -2, 0, 58793, 246158,       2, -1, -1, 0, 57066, -152138,     2, 0, 1, 0, 53322, -170733,
        2, -1, 0, 0, 45758, -204586,      0, 1, -1, 0, -40923, -129620,     1, 0, 0, 0, -34720, 108743,
        0, 1, 1, 0, -30383, 104755,       2, 0, 0, -2, 15327, 10321,        0, 0, 1, 2, -12528, 0,
        0, 0, 1, -2, 10980, 79661,        4, 0, -1, 0, 10675, -34782,       0, 0, 3, 0, 10034, -23210,
        4, 0, -2, 0, 8548, -21636,        2, 1, -1, 0, -7888, 24208,        2, 1, 0, 0, -6766, 30824,
        1, 0, -1, 0, -5163, -8379,        1, 1, 0, 0, 4987, -16675,         2, -1, 1, 0, 4036, -12831,
        2, 0, 2, 0, 3994, -10445,         4, 0, 0, 0, 3861, -11650,         2, 0, -3, 0, 3665, 14403,
        0, 1, -2, 0, -2689, -7003,        2, 0, -1, 2, -2602, 0,            2, -1, -2, 0, 2390, 10056,
        1, 0, 1, 0, -2348, 6322,          2, -2, 0, 0, 2236, -9884,         0, 1, 2, 0, -2120, 5751,
        0, 2, 0, 0, -2069, 0,             2, -2, -1, 0, 2048, -4950,        2, 0, 1, -2, -1773, 4130,
        2, 0, 0, 2, -1595, 0,             4, -1, -1, 0, 1215, -3958,        0, 0, 2, 2, -1110, 0,
        3, 0, -1, 0, -892, 3258,          2, 1, 1, 0, -810, 2616,           4, -1, -2, 0, 759, -1897,
        0, 2, -1, 0, -713, -2117,         2, 2, -1, 0, -700, 2354,          2, 1, -2, 0, 691, 0,
        2, -1, 0, -2, 596, 0,             4, 0, 1, 0, 549, -1423,           0, 0, 4, 0, 537, -1117,
        4, -1, 0, 0, 520, -1571,          1, 0, -2, 0, -487, -1739,         2, 1, 0, -2, -399, 0,
        0, 0, 2, -2, -381, -4421,         1, 1, 1, 0, 351, 0,               3, 0, -2, 0, -340, 0,
        4, 0, -3, 0, 330, 0,              2, -1, 2, 0, 327, 0,              0, 2, 1, 0, -323, 1165,
        1, 1, -1, 0, 299, 0,              2, 0, 3, 0, 294, 0,               2, 0, -1, -2, 0, 8752,
    )

    // D, M, M', F, sum_b (1e-6 deg)                         — Meeus table 47.B
    private val B = intArrayOf(
        0, 0, 0, 1, 5128122,   0, 0, 1, 1, 280602,    0, 0, 1, -1, 277693,   2, 0, 0, -1, 173237,
        2, 0, -1, 1, 55413,    2, 0, -1, -1, 46271,   2, 0, 0, 1, 32573,     0, 0, 2, 1, 17198,
        2, 0, 1, -1, 9266,     0, 0, 2, -1, 8822,     2, -1, 0, -1, 8216,    2, 0, -2, -1, 4324,
        2, 0, 1, 1, 4200,      2, 1, 0, -1, -3359,    2, -1, -1, 1, 2463,    2, -1, 0, 1, 2211,
        2, -1, -1, -1, 2065,   0, 1, -1, -1, -1870,   4, 0, -1, -1, 1828,    0, 1, 0, 1, -1794,
        0, 0, 0, 3, -1749,     0, 1, -1, 1, -1565,    1, 0, 0, 1, -1491,     0, 1, 1, 1, -1475,
        0, 1, 1, -1, -1410,    0, 1, 0, -1, -1344,    1, 0, 0, -1, -1335,    0, 0, 3, 1, 1107,
        4, 0, 0, -1, 1021,     4, 0, -1, 1, 833,      0, 0, 1, -3, 777,      4, 0, -2, 1, 671,
        2, 0, 0, -3, 607,      2, 0, 2, -1, 596,      2, -1, 1, -1, 491,     2, 0, -2, 1, -451,
        0, 0, 3, -1, 439,      2, 0, 2, 1, 422,       2, 0, -3, -1, 421,     2, 1, -1, 1, -366,
        2, 1, 0, 1, -351,      4, 0, 0, 1, 331,       2, -1, 1, 1, 315,      2, -2, 0, -1, 302,
        0, 0, 1, 3, -283,      2, 1, 1, -1, -229,     1, 1, 0, -1, 223,      1, 1, 0, 1, 223,
        0, 1, -2, -1, -220,    2, 1, -1, -1, -220,    1, 0, 1, 1, -185,      2, -1, -2, -1, 181,
        0, 1, 2, 1, -177,      4, 0, -2, -1, 176,     4, -1, -1, -1, 166,    1, 0, 1, -1, -164,
        4, 0, 1, -1, 132,      1, 0, -1, -1, -119,    4, -1, 0, -1, 115,     2, -2, 0, 1, 107,
    )

    private fun rad(d: Double) = Math.toRadians(d % 360.0)

    fun position(jd: Double): Pos {
        val t = (jd - Solar.J2000) / 36525.0
        val t2 = t * t; val t3 = t2 * t; val t4 = t3 * t
        val lp = 218.3164477 + 481267.88123421 * t - 0.0015786 * t2 + t3 / 538841.0 - t4 / 65194000.0
        val d = 297.8501921 + 445267.1114034 * t - 0.0018819 * t2 + t3 / 545868.0 - t4 / 113065000.0
        val m = 357.5291092 + 35999.0502909 * t - 0.0001536 * t2 + t3 / 24490000.0
        val mp = 134.9633964 + 477198.8675055 * t + 0.0087414 * t2 + t3 / 69699.0 - t4 / 14712000.0
        val f = 93.2720950 + 483202.0175233 * t - 0.0036539 * t2 - t3 / 3526000.0 + t4 / 863310000.0
        val a1 = 119.75 + 131.849 * t
        val a2 = 53.09 + 479264.290 * t
        val a3 = 313.45 + 481266.484 * t
        val e = 1.0 - 0.002516 * t - 0.0000074 * t2
        val dr = rad(d); val mr = rad(m); val mpr = rad(mp); val fr = rad(f)
        var sl = 0.0; var sr = 0.0; var sb = 0.0
        var k = 0
        while (k < LR.size) {
            val cm = LR[k + 1]
            val arg = LR[k] * dr + cm * mr + LR[k + 2] * mpr + LR[k + 3] * fr
            val ef = when (abs(cm)) { 1 -> e; 2 -> e * e; else -> 1.0 }
            sl += LR[k + 4] * ef * sin(arg); sr += LR[k + 5] * ef * cos(arg)
            k += 6
        }
        k = 0
        while (k < B.size) {
            val cm = B[k + 1]
            val arg = B[k] * dr + cm * mr + B[k + 2] * mpr + B[k + 3] * fr
            val ef = when (abs(cm)) { 1 -> e; 2 -> e * e; else -> 1.0 }
            sb += B[k + 4] * ef * sin(arg)
            k += 5
        }
        val lpr = rad(lp)
        sl += 3958 * sin(rad(a1)) + 1962 * sin(lpr - fr) + 318 * sin(rad(a2))
        sb += -2235 * sin(lpr) + 382 * sin(rad(a3)) + 175 * sin(rad(a1) - fr) + 175 * sin(rad(a1) + fr) +
            127 * sin(lpr - mpr) - 115 * sin(lpr + mpr)
        var lon = (lp + sl / 1e6) % 360.0; if (lon < 0) lon += 360.0
        return Pos(lon, sb / 1e6, 385000.56 + sr / 1000.0)
    }

    /** General precession in longitude since J2000 (deg), Meeus eq. 21.6 / IAU 1976: date -> J2000 frame. */
    fun precessionDeg(jd: Double): Double {
        val t = (jd - Solar.J2000) / 36525.0
        return (5029.0966 * t + 1.11113 * t * t) / 3600.0
    }
}

/**
 * Sun-Earth-Moon geometry in ONE frame: geocentric, J2000 ecliptic, km. The Sun comes from the JPL
 * Earth-Moon-barycentre elements (SolarCore), corrected from the barycentre to the Earth's centre
 * (Earth = EMB - mu*Moon, mu = M_moon/(M_earth+M_moon) = 0.0121506; 4 670 km, 6" of solar direction).
 */
object Sky {
    const val AU_KM = 1.495978707e8
    const val MU_MOON = 0.012150585
    /** Mean obliquity of the ecliptic at J2000 (IAU 1976), deg. */
    const val OBLIQUITY_J2000 = 23.4392911
    val PHASE_ZH = listOf("新月", "蛾眉月", "上弦月", "盈凸月", "满月", "亏凸月", "下弦月", "残月")
    val PHASE_EN = listOf("New Moon", "Waxing crescent", "First quarter", "Waxing gibbous", "Full Moon", "Waning gibbous", "Last quarter", "Waning crescent")

    fun moonGeoKm(jd: Double): Vec3d {
        val p = Lunar.position(jd)
        val lon = Math.toRadians(p.lonDate - Lunar.precessionDeg(jd)); val lat = Math.toRadians(p.lat)
        return Vec3d(p.distKm * cos(lat) * cos(lon), p.distKm * cos(lat) * sin(lon), p.distKm * sin(lat))
    }

    fun sunGeoKm(jd: Double, moon: Vec3d = moonGeoKm(jd)): Vec3d {
        val b = Solar.position(Solar.planets[2], Solar.centuriesFromJd(jd))
        return Vec3d(-b.x * AU_KM + MU_MOON * moon.x, -b.y * AU_KM + MU_MOON * moon.y, -b.z * AU_KM + MU_MOON * moon.z)
    }

    /** Ecliptic longitude (deg, J2000) of a geocentric vector. */
    fun lonDeg(v: Vec3d): Double { var l = Math.toDegrees(atan2(v.y, v.x)) % 360.0; if (l < 0) l += 360.0; return l }
    fun latDeg(v: Vec3d): Double = Math.toDegrees(kotlin.math.asin((v.z / v.length).coerceIn(-1.0, 1.0)))

    /** Moon longitude minus Sun longitude, 0..360: 0 new, 90 first quarter, 180 full, 270 last quarter. */
    fun elongationDeg(jd: Double): Double {
        val m = moonGeoKm(jd); val s = sunGeoKm(jd, m)
        var e = (lonDeg(m) - lonDeg(s)) % 360.0; if (e < 0) e += 360.0
        return e
    }

    /** Phase angle i = Sun-Moon-Earth angle at the Moon (deg): 0 full, 180 new. */
    fun phaseAngleDeg(jd: Double): Double {
        val m = moonGeoKm(jd); val s = sunGeoKm(jd, m)
        return angleDeg(Vec3d(s.x - m.x, s.y - m.y, s.z - m.z), Vec3d(-m.x, -m.y, -m.z))
    }

    /** Illuminated fraction of the Moon's disc, (1 + cos i) / 2 (Meeus 48.1). */
    fun illuminated(jd: Double) = 0.5 * (1.0 + cos(Math.toRadians(phaseAngleDeg(jd))))

    /** 8 named phases, each a 45 deg bin centred on 0/45/90/... deg of elongation. */
    fun phaseIndex(elongDeg: Double): Int = (((elongDeg + 22.5) % 360.0 + 360.0) % 360.0 / 45.0).toInt().coerceIn(0, 7)

    /** Days since the last new moon (true conjunction in longitude), found by Newton iteration. */
    fun moonAgeDays(jd: Double): Double {
        var t = jd - elongationDeg(jd) / 12.19
        repeat(6) {
            var e = elongationDeg(t); if (e > 180) e -= 360.0
            t -= e / 12.19
        }
        return jd - t
    }

    /** J2000 ecliptic -> J2000 equatorial (rotation about x by the obliquity). */
    fun eclToEq(v: Vec3d): Vec3d {
        val ep = Math.toRadians(OBLIQUITY_J2000); val c = cos(ep); val s = sin(ep)
        return Vec3d(v.x, v.y * c - v.z * s, v.y * s + v.z * c)
    }

    /**
     * Equatorial frame of date -> J2000 (IAU 1976 precession, Meeus eq. 21.2-21.4 run backwards):
     * v_date = Rz(z) Ry(theta) Rz(zeta) v_J2000.
     */
    fun dateToJ2000Eq(v: Vec3d, jd: Double): Vec3d {
        val t = (jd - Solar.J2000) / 36525.0
        val zeta = Math.toRadians((2306.2181 * t + 0.30188 * t * t + 0.017998 * t * t * t) / 3600.0)
        val z = Math.toRadians((2306.2181 * t + 1.09468 * t * t + 0.018203 * t * t * t) / 3600.0)
        val th = Math.toRadians((2004.3109 * t - 0.42665 * t * t - 0.041833 * t * t * t) / 3600.0)
        fun rz(p: Vec3d, a: Double) = Vec3d(p.x * cos(a) - p.y * sin(a), p.x * sin(a) + p.y * cos(a), p.z)
        val a = rz(v, -z)
        val b = Vec3d(a.x * cos(-th) - a.z * sin(-th), a.y, a.x * sin(-th) + a.z * cos(-th))
        return rz(b, -zeta)
    }

    /** Right ascension / declination (deg) of a vector in an equatorial frame. */
    fun raDec(v: Vec3d): Pair<Double, Double> {
        var ra = Math.toDegrees(atan2(v.y, v.x)); if (ra < 0) ra += 360.0
        return ra to Math.toDegrees(kotlin.math.asin((v.z / v.length).coerceIn(-1.0, 1.0)))
    }

    /**
     * Position angle (deg, from celestial north through east) of the Moon's bright limb — the direction
     * of the Sun seen from the Moon, projected on the sky (Meeus eq. 48.5).
     */
    fun brightLimbDeg(jd: Double): Double {
        val m = moonGeoKm(jd); val s = sunGeoKm(jd, m)
        val (a, d) = raDec(eclToEq(m)); val (a0, d0) = raDec(eclToEq(s))
        val da = Math.toRadians(a0 - a); val dr = Math.toRadians(d); val d0r = Math.toRadians(d0)
        var chi = Math.toDegrees(atan2(cos(d0r) * sin(da), sin(d0r) * cos(dr) - cos(d0r) * sin(dr) * cos(da)))
        if (chi < 0) chi += 360.0
        return chi
    }

    /** Greenwich mean sidereal time (deg) for a UT Julian Day (IAU 1982, Meeus eq. 12.4). */
    fun gmstDeg(jdUt: Double): Double {
        val t = (jdUt - Solar.J2000) / 36525.0
        var g = (280.46061837 + 360.98564736629 * (jdUt - Solar.J2000) + 0.000387933 * t * t - t * t * t / 38710000.0) % 360.0
        if (g < 0) g += 360.0
        return g
    }

    fun angleDeg(a: Vec3d, b: Vec3d): Double =
        Math.toDegrees(acos(((a.x * b.x + a.y * b.y + a.z * b.z) / (a.length * b.length)).coerceIn(-1.0, 1.0)))

    /** Calendar date + time (TT) -> Julian Day (Meeus ch. 7, Gregorian). */
    fun jd(y: Int, mo: Int, d: Int, h: Int = 0, mi: Int = 0, s: Double = 0.0): Double {
        var yy = y; var mm = mo
        if (mm <= 2) { yy -= 1; mm += 12 }
        val a = yy / 100; val b = 2 - a + a / 4
        return floor(365.25 * (yy + 4716)).toLong() + floor(30.6001 * (mm + 1)).toLong() + d + b - 1524.5 +
            (h + mi / 60.0 + s / 3600.0) / 24.0
    }
}

/**
 * Eclipse geometry from the Sun and Moon vectors above, with the conventions of the NASA eclipse
 * catalogues (F. Espenak, "Five Millennium Canon of Lunar/Solar Eclipses", NASA TP-2006-214141/-214172;
 * eclipse.gsfc.nasa.gov). Earth's shadow uses Danjon's enlargement (radius x 1.01 incl. oblateness):
 *   umbra radius     Ru = 1.01 pi_M + pi_S - s_S      penumbra Rp = 1.01 pi_M + pi_S + s_S
 * (pi = horizontal parallax, s = semi-diameter, angles seen from the Earth's centre at the Moon's distance).
 * Gamma = distance of the Moon's centre from the shadow axis (lunar) / of the Moon's shadow axis from
 * the Earth's centre (solar), in equatorial Earth radii, + = north (toward the celestial pole).
 */
object Eclipse {
    const val R_EARTH = 6378.14
    const val R_SUN = 696000.0
    const val K_MOON = 0.2725076                   // Moon radius / Earth equatorial radius (IAU)
    val R_MOON get() = K_MOON * R_EARTH
    /** Celestial north pole in the J2000 ecliptic frame. */
    private val POLE = Vec3d(0.0, sin(Math.toRadians(Sky.OBLIQUITY_J2000)), cos(Math.toRadians(Sky.OBLIQUITY_J2000)))

    class LunarGeo(
        val sepDeg: Double, val umbraDeg: Double, val penumbraDeg: Double, val moonSdDeg: Double, val moonHpDeg: Double,
        val gamma: Double, val umbralMag: Double, val penumbralMag: Double, val umbraFraction: Double,
        /** Moon centre minus shadow axis, perpendicular to the axis, in Earth radii (J2000 ecliptic). */
        val offsetRe: Vec3d,
        /** shadow radii AT the Moon's distance, Earth radii */
        val umbraRe: Double, val penumbraRe: Double,
    ) {
        val type get() = when { umbralMag >= 1.0 -> 'T'; umbralMag > 0.0 -> 'P'; penumbralMag > 0.0 -> 'N'; else -> '-' }
    }

    fun lunar(jd: Double): LunarGeo {
        val m = Sky.moonGeoKm(jd); val s = Sky.sunGeoKm(jd, m)
        val dm = m.length; val ds = s.length
        val hp = Math.toDegrees(kotlin.math.asin(R_EARTH / dm))
        val sdM = Math.toDegrees(kotlin.math.asin(R_MOON / dm))
        val sdS = Math.toDegrees(kotlin.math.asin(R_SUN / ds))
        val piS = Math.toDegrees(kotlin.math.asin(R_EARTH / ds))
        val ru = 1.01 * hp + piS - sdS
        val rp = 1.01 * hp + piS + sdS
        val anti = Vec3d(-s.x / ds, -s.y / ds, -s.z / ds)
        val sep = Sky.angleDeg(m, anti)
        val along = m.x * anti.x + m.y * anti.y + m.z * anti.z
        val off = Vec3d((m.x - along * anti.x) / R_EARTH, (m.y - along * anti.y) / R_EARTH, (m.z - along * anti.z) / R_EARTH)
        val sign = if (off.x * POLE.x + off.y * POLE.y + off.z * POLE.z >= 0) 1.0 else -1.0
        val toRe = dm / R_EARTH * Math.PI / 180.0              // degrees at the Moon -> Earth radii
        return LunarGeo(sep, ru, rp, sdM, hp, sign * off.length, (ru + sdM - sep) / (2 * sdM), (rp + sdM - sep) / (2 * sdM),
            discOverlap(sdM, ru, sep), off, ru * toRe, rp * toRe)
    }

    class SolarGeo(
        val gamma: Double,
        /** closest point of the Moon's shadow axis to the Earth's centre, Earth radii (J2000 ecliptic) */
        val axisRe: Vec3d,
        /** penumbra / umbra radius on the plane through the Earth's centre normal to the axis, Earth radii (umbra < 0 = beyond the vertex) */
        val penumbraRe: Double, val umbraRe: Double,
        val sepDeg: Double,
        /** Moon/Sun apparent diameter ratio for an observer where the axis meets the surface (> 1 total, < 1 annular) */
        val centralMagnitude: Double,
        /** the Moon is on the Sun's side of the Earth (near new moon) — else the shadow points away from us */
        val moonBetween: Boolean,
    ) {
        val anywhere get() = moonBetween && abs(gamma) < 1.0 + penumbraRe
        val central get() = abs(gamma) < 1.0
        val type get() = when { !anywhere -> '-'; !central -> 'P'; centralMagnitude >= 1.0 -> 'T'; else -> 'A' }
    }

    fun solar(jd: Double): SolarGeo {
        val m = Sky.moonGeoKm(jd); val s = Sky.sunGeoKm(jd, m)
        val axis = Vec3d(m.x - s.x, m.y - s.y, m.z - s.z); val la = axis.length
        val u = Vec3d(axis.x / la, axis.y / la, axis.z / la)          // Sun -> Moon -> beyond
        val mu = m.x * u.x + m.y * u.y + m.z * u.z                     // < 0 when the Moon is sunward of the Earth
        val p = Vec3d(m.x - mu * u.x, m.y - mu * u.y, m.z - mu * u.z)  // axis point closest to the Earth's centre
        val g = p.length / R_EARTH
        val sign = if (p.x * POLE.x + p.y * POLE.y + p.z * POLE.z >= 0) 1.0 else -1.0
        val z = -mu                                                    // Moon -> fundamental plane along the axis, km
        val f1 = kotlin.math.asin((R_SUN + R_MOON) / la); val f2 = kotlin.math.asin((R_SUN - R_MOON) / la)
        val l1 = (z * kotlin.math.tan(f1) + R_MOON / cos(f1)) / R_EARTH
        val l2 = (R_MOON / cos(f2) - z * kotlin.math.tan(f2)) / R_EARTH
        val toSurface = if (g < 1.0) kotlin.math.sqrt(1.0 - g * g) * R_EARTH else 0.0
        val dMoonObs = z - toSurface; val dSunObs = la + z - toSurface
        val ratio = (R_MOON / dMoonObs) / (R_SUN / dSunObs)
        return SolarGeo(sign * g, Vec3d(p.x / R_EARTH, p.y / R_EARTH, p.z / R_EARTH), l1, l2, Sky.angleDeg(m, s), ratio, z > 0)
    }

    class Local(val sepDeg: Double, val sunSdDeg: Double, val moonSdDeg: Double, val magnitude: Double, val obscuration: Double,
                /** Moon centre minus Sun centre on the observer's sky, deg (east, north) */
                val eastDeg: Double, val northDeg: Double)

    /**
     * The eclipse seen from one place: topocentric Moon (observer on a sphere of 6378 km at geocentric
     * latitude; GMST from UT = TT - deltaT gives its position in the equator of date, precessed
     * back to J2000). Magnitude as NASA uses it: Moon/Sun diameter ratio during the central phase,
     * otherwise the fraction of the Sun's diameter covered.
     */
    fun localSolar(jd: Double, latDeg: Double, lonEastDeg: Double, deltaTsec: Double = 75.0): Local {
        val m = Sky.moonGeoKm(jd); val s = Sky.sunGeoKm(jd, m)
        val lat = Math.toRadians(latDeg - 0.1924 * sin(Math.toRadians(2 * latDeg)))   // geodetic -> geocentric
        val rho = 0.99833 + 0.00167 * cos(Math.toRadians(2 * latDeg))
        // observer in the equatorial frame OF DATE (local mean sidereal time), precessed back to J2000
        val lst = Math.toRadians(Sky.gmstDeg(jd - deltaTsec / 86400.0) + lonEastDeg)
        val oEq = Sky.dateToJ2000Eq(Vec3d(rho * R_EARTH * cos(lat) * cos(lst), rho * R_EARTH * cos(lat) * sin(lst), rho * R_EARTH * sin(lat)), jd)
        val mEq = Sky.eclToEq(m); val sEq = Sky.eclToEq(s)
        val mt = Vec3d(mEq.x - oEq.x, mEq.y - oEq.y, mEq.z - oEq.z); val st = Vec3d(sEq.x - oEq.x, sEq.y - oEq.y, sEq.z - oEq.z)
        val sep = Sky.angleDeg(mt, st)
        val sdS = Math.toDegrees(kotlin.math.asin(R_SUN / st.length)); val sdM = Math.toDegrees(kotlin.math.asin(R_MOON / mt.length))
        // NASA usage: central (total/annular) phase -> ratio of apparent diameters, else fraction of the diameter covered
        val mag = if (sep <= abs(sdM - sdS)) sdM / sdS else ((sdS + sdM - sep) / (2 * sdS)).coerceAtLeast(0.0)
        val obsc = discOverlap(sdS, sdM, sep)
        val (am, dm) = Sky.raDec(mt); val (asn, ds) = Sky.raDec(st)
        var dra = am - asn; if (dra > 180) dra -= 360.0; if (dra < -180) dra += 360.0
        return Local(sep, sdS, sdM, mag, obsc, dra * cos(Math.toRadians(ds)), dm - ds)
    }

    /** Fraction of disc A (radius a) covered by disc B (radius b), centres d apart (same units). */
    fun discOverlap(a: Double, b: Double, d: Double): Double {
        if (d >= a + b) return 0.0
        if (d <= abs(b - a)) return if (b >= a) 1.0 else (b * b) / (a * a)
        val ca = ((d * d + a * a - b * b) / (2 * d * a)).coerceIn(-1.0, 1.0)
        val cb = ((d * d + b * b - a * a) / (2 * d * b)).coerceIn(-1.0, 1.0)
        val area = a * a * acos(ca) + b * b * acos(cb) - 0.5 * kotlin.math.sqrt(((-d + a + b) * (d + a - b) * (d - a + b) * (d + a + b)).coerceAtLeast(0.0))
        return (area / (Math.PI * a * a)).coerceIn(0.0, 1.0)
    }

    /** Instant (TT JD) near [jd] where |gamma| is smallest — the greatest eclipse (golden-section, +-[spanH] h). */
    fun greatest(jd: Double, solar: Boolean, spanH: Double = 8.0): Double {
        fun f(t: Double) = if (solar) abs(solar(t).gamma) else lunar(t).sepDeg
        var a = jd - spanH / 24; var b = jd + spanH / 24
        val r = (kotlin.math.sqrt(5.0) - 1) / 2
        var c = b - r * (b - a); var d = a + r * (b - a)
        repeat(40) { if (f(c) < f(d)) b = d else a = c; c = b - r * (b - a); d = a + r * (b - a) }
        return (a + b) / 2
    }

    /** Instant (TT JD) of the syzygy (elongation 0 = new, 180 = full) nearest [jd]. */
    fun syzygy(jd: Double, full: Boolean): Double {
        var t = jd
        repeat(8) {
            var e = Sky.elongationDeg(t) - if (full) 180.0 else 0.0
            e = ((e % 360.0) + 540.0) % 360.0 - 180.0
            t -= e / 12.19
        }
        return t
    }

    /**
     * Preset eclipses — NASA Five Millennium Canon / eclipse.gsfc.nasa.gov (catalogue rows and the
     * per-eclipse plots): time of greatest eclipse in TD, gamma, (umbral) magnitude, totality, where.
     */
    class Event(val solar: Boolean, val jd: Double, val gamma: Double, val magnitude: Double, val totalMin: Double,
                val latDeg: Double, val lonDeg: Double, val pathKm: Double, val whereZh: String, val whereEn: String) {
        val date get() = SolarModel.dateString(jd)
    }

    val LUNAR = listOf(
        Event(false, Sky.jd(2025, 9, 7, 18, 12, 58.0), -0.2752, 1.3619, 82.1, 0.0, 0.0, 0.0, "亚洲、澳洲、东欧、非洲可见", "visible from Asia, Australia, Europe, Africa"),
        Event(false, Sky.jd(2026, 3, 3, 11, 34, 52.0), -0.3765, 1.1507, 58.3, 0.0, 0.0, 0.0, "东亚、澳洲、太平洋、美洲可见", "visible from East Asia, Australia, the Pacific, the Americas"),
        Event(false, Sky.jd(2028, 12, 31, 16, 53, 15.0), 0.3258, 1.2463, 71.3, 0.0, 0.0, 0.0, "欧洲、非洲、亚洲、澳洲可见", "visible from Europe, Africa, Asia, Australia"),
    )
    val SOLAR = listOf(
        Event(true, Sky.jd(2026, 8, 12, 17, 47, 6.0), 0.8977, 1.0386, 2.30, 65.2167, -25.2267, 294.0, "格陵兰—冰岛—西班牙", "Greenland - Iceland - Spain"),
        Event(true, Sky.jd(2027, 8, 2, 10, 7, 50.0), 0.1421, 1.0790, 6.38, 25.4933, 33.22, 258.0, "西班牙南端—北非—埃及卢克索—阿拉伯", "southern Spain - North Africa - Luxor (Egypt) - Arabia"),
    )
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

/**
 * True-scale facts for the "to scale" and "size lineup" views. Mean radii: NASA planetary fact sheet
 * (nssdc.gsfc.nasa.gov/planetary/factsheet, volumetric mean radius; the same numbers as SolarCore),
 * Sun = IAU 2015 nominal radius 695 700 km, Moon 1 737.4 km; distances = J2000 semi-major axes (JPL).
 * Index: 0..7 = Mercury..Neptune, 8 = Moon, 9 = Sun.
 */
object ScaleFacts {
    const val SUN_KM = 695700.0
    const val LIGHT_KMS = 299792.458
    fun radiusKm(i: Int) = when (i) { in 0..7 -> Solar.planets[i].radiusKm; 8 -> Moon.RADIUS_KM; else -> SUN_KM }
    fun nameZh(i: Int) = when (i) { in 0..7 -> Solar.planets[i].chinese; 8 -> "月球"; else -> "太阳" }
    fun nameEn(i: Int) = when (i) { in 0..7 -> Solar.planets[i].name.lowercase().replaceFirstChar { it.uppercase() }; 8 -> "Moon"; else -> "Sun" }
    /** Radius in Earth radii. */
    fun re(i: Int) = radiusKm(i) / Solar.EARTH_RADIUS_KM
    /** How many Earths fit inside by volume, (R/R_E)^3. */
    fun earthVolumes(i: Int) = re(i).let { it * it * it }
    /** Diameter in mm if the Sun were a 1 m ball. */
    fun mmIfSunIsMetre(i: Int) = 1000.0 * radiusKm(i) / SUN_KM
    /** Distance from the Sun in metres if the Sun were a 1 m ball. */
    fun metresIfSunIsMetre(aAu: Double) = aAu * Sky.AU_KM / (2 * SUN_KM)
    /** Light travel time from the Sun, seconds. */
    fun lightSeconds(aAu: Double) = aAu * Sky.AU_KM / LIGHT_KMS

    /** Everyday objects of about the size of each planet when the Sun is 1 m: name zh, en, typical diameter range mm. */
    class Thing(val zh: String, val en: String, val minMm: Double, val maxMm: Double)
    val THINGS = listOf(
        Thing("胡椒粒", "a peppercorn", 3.0, 5.0),       // Mercury
        Thing("豌豆", "a pea", 7.0, 10.0),               // Venus
        Thing("豌豆", "a pea", 7.0, 10.0),               // Earth
        Thing("绿豆", "a mung bean", 4.0, 5.5),          // Mars
        Thing("西柚", "a grapefruit", 95.0, 150.0),      // Jupiter
        Thing("橙子", "an orange", 70.0, 95.0),          // Saturn
        Thing("核桃", "a walnut", 30.0, 40.0),           // Uranus
        Thing("核桃", "a walnut", 30.0, 40.0),           // Neptune
        Thing("芝麻粒", "a sesame seed", 2.0, 3.5),      // Moon
    )
}
