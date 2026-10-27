// SolarArt — geometry, textures and small maths shared by the solar system's view modes (Earth-Moon
// close-up, true scale, size lineup). Pure Kotlin (Spec meshes + render.Image), no SDK.

package com.bytedance.physicslab.solar.demos.solar

import com.bytedance.physicslab.solar.core.P3
import com.bytedance.physicslab.solar.core.Vec3d
import com.bytedance.physicslab.solar.lab.Q
import com.bytedance.physicslab.solar.render.Image
import com.bytedance.physicslab.solar.render.MeshData
import com.bytedance.physicslab.solar.render.ProcTex
import com.bytedance.physicslab.solar.render.V3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Textures the views share (the solar bakes + the close-up's own). */
internal class SolarTex(
    val sunA: Image, val sunB: Image, val limb: Image, val corona: Image, val light: Image, val ring: Image,
    val planets: List<Image>, val moon: Image, val moonHi: Image, val glow: Image, val ray: Image, val spot: Image,
    /** light shells with earthshine: the phase icons (ambient 0.12) and the big phase disc (0.05) */
    val lightIcon: Image, val lightDisc: Image, val coronaRing: Image,
)

internal object SolarArt {
    // ---------------------------------------------------------------- meshes

    /** Square in the XY plane facing +Z, side [size], unit UV. */
    fun quad(size: Float): MeshData {
        val h = size / 2
        return MeshData(floatArrayOf(-h, -h, 0f, h, -h, 0f, h, h, 0f, -h, h, 0f),
            floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f),
            floatArrayOf(0f, 1f, 1f, 1f, 1f, 0f, 0f, 0f), intArrayOf(0, 1, 2, 0, 2, 3))
    }

    /** Filled disc in the XY plane facing +Z (CCW), planar UV. */
    fun disc(r: Float, seg: Int): MeshData {
        val n = seg + 2
        val pos = FloatArray(n * 3); val nrm = FloatArray(n * 3); val uv = FloatArray(n * 2)
        uv[0] = 0.5f; uv[1] = 0.5f; nrm[2] = 1f
        for (k in 0..seg) {
            val a = 2.0 * PI * k / seg; val v = k + 1
            pos[v * 3] = (r * cos(a)).toFloat(); pos[v * 3 + 1] = (r * sin(a)).toFloat(); nrm[v * 3 + 2] = 1f
            uv[v * 2] = (0.5 + 0.5 * cos(a)).toFloat(); uv[v * 2 + 1] = (0.5 - 0.5 * sin(a)).toFloat()
        }
        val idx = IntArray(seg * 3)
        for (k in 0 until seg) { idx[k * 3] = 0; idx[k * 3 + 1] = k + 1; idx[k * 3 + 2] = k + 2 }
        return MeshData(pos, nrm, uv, idx)
    }

    /** Annulus in the XY plane facing +Z; u = radial (0 inner .. 1 outer), v = angle. */
    fun ring(rIn: Float, rOut: Float, seg: Int): MeshData {
        val pos = FloatArray((seg + 1) * 2 * 3); val nrm = FloatArray(pos.size); val uv = FloatArray((seg + 1) * 2 * 2)
        for (k in 0..seg) for (j in 0..1) {
            val a = 2.0 * PI * k / seg; val r = if (j == 0) rIn else rOut; val v = k * 2 + j
            pos[v * 3] = (r * cos(a)).toFloat(); pos[v * 3 + 1] = (r * sin(a)).toFloat(); nrm[v * 3 + 2] = 1f
            uv[v * 2] = j.toFloat(); uv[v * 2 + 1] = k.toFloat() / seg
        }
        val idx = IntArray(seg * 6); var t = 0
        for (k in 0 until seg) { val a0 = k * 2; idx[t++] = a0; idx[t++] = a0 + 1; idx[t++] = a0 + 2; idx[t++] = a0 + 1; idx[t++] = a0 + 3; idx[t++] = a0 + 2 }
        return MeshData(pos, nrm, uv, idx)
    }

    /** Open frustum along +Y: radius [r0] at y = 0, [r1] at y = [len]; u = angle, v = 0 .. 1 along the length. */
    fun frustum(r0: Float, r1: Float, len: Float, seg: Int): MeshData {
        val md = MeshData(FloatArray((seg + 1) * 2 * 3), FloatArray((seg + 1) * 2 * 3), FloatArray((seg + 1) * 2 * 2), IntArray(seg * 6))
        var t = 0
        for (k in 0 until seg) { val a0 = k * 2; md.idx[t++] = a0; md.idx[t++] = a0 + 2; md.idx[t++] = a0 + 1; md.idx[t++] = a0 + 1; md.idx[t++] = a0 + 2; md.idx[t++] = a0 + 3 }
        for (k in 0..seg) for (j in 0..1) { val v = k * 2 + j; md.uv[v * 2] = k.toFloat() / seg; md.uv[v * 2 + 1] = j.toFloat() }
        frustumInto(md, r0, r1, len, seg)
        return md
    }

    /** Rewrite a [frustum] in place (same segment count). */
    fun frustumInto(md: MeshData, r0: Float, r1: Float, len: Float, seg: Int) {
        val slope = (r0 - r1) / max(len, 1e-6f)
        for (k in 0..seg) for (j in 0..1) {
            val a = 2.0 * PI * k / seg; val c = cos(a).toFloat(); val s = sin(a).toFloat()
            val r = max(if (j == 0) r0 else r1, 1e-5f); val v = k * 2 + j
            md.pos[v * 3] = c * r; md.pos[v * 3 + 1] = if (j == 0) 0f else len; md.pos[v * 3 + 2] = s * r
            val nl = sqrt(1f + slope * slope)
            md.nrm[v * 3] = c / nl; md.nrm[v * 3 + 1] = slope / nl; md.nrm[v * 3 + 2] = s / nl
        }
    }

    /** Spherical cap of half-angle [angle] (rad) on a sphere of radius [r] about +Y; u = 0 at the pole .. 1 at the rim. */
    fun cap(angle: Float, r: Float, rings: Int, seg: Int): MeshData {
        val md = MeshData(FloatArray((rings + 1) * (seg + 1) * 3), FloatArray((rings + 1) * (seg + 1) * 3),
            FloatArray((rings + 1) * (seg + 1) * 2), IntArray(rings * seg * 6))
        var t = 0
        for (i in 0 until rings) for (j in 0 until seg) {
            val a = i * (seg + 1) + j; val b = a + seg + 1
            md.idx[t++] = a; md.idx[t++] = a + 1; md.idx[t++] = b; md.idx[t++] = a + 1; md.idx[t++] = b + 1; md.idx[t++] = b
        }
        capInto(md, angle, r, rings, seg)
        return md
    }

    fun capInto(md: MeshData, angle: Float, r: Float, rings: Int, seg: Int) {
        var k = 0
        for (i in 0..rings) {
            val th = angle * i / rings; val st = sin(th); val ct = cos(th)
            for (j in 0..seg) {
                val ph = 2.0 * PI * j / seg; val x = (st * cos(ph)).toFloat(); val z = (st * sin(ph)).toFloat()
                md.pos[k * 3] = x * r; md.pos[k * 3 + 1] = ct * r; md.pos[k * 3 + 2] = z * r
                md.nrm[k * 3] = x; md.nrm[k * 3 + 1] = ct; md.nrm[k * 3 + 2] = z
                md.uv[k * 2] = i.toFloat() / rings; md.uv[k * 2 + 1] = j.toFloat() / seg
                k++
            }
        }
    }

    /** Fan mesh with [n] rim points (for [lensInto]). */
    fun lens(n: Int): MeshData {
        val md = MeshData(FloatArray((n + 1) * 3), FloatArray((n + 1) * 3), FloatArray((n + 1) * 2), IntArray(n * 3))
        for (k in 0..n) md.nrm[k * 3 + 2] = 1f
        for (k in 0 until n) { md.idx[k * 3] = 0; md.idx[k * 3 + 1] = 1 + k; md.idx[k * 3 + 2] = 1 + (k + 1) % n }
        return md
    }

    /**
     * Intersection of disc A (centre 0, radius [ra]) and disc B (centre ([bx],[by]), radius [rb]) in the XY
     * plane at height [z], written into a [lens] fan. The overlap of two discs is convex, so it is the
     * star of a point inside it: each rim point is the nearer of the two circle exits along its ray.
     * Returns false (and collapses the mesh) when the discs do not overlap.
     */
    fun lensInto(md: MeshData, ra: Float, bx: Float, by: Float, rb: Float, z: Float): Boolean {
        val n = md.vertexCount - 1
        val d = sqrt(bx * bx + by * by)
        val ux = if (d > 1e-7f) bx / d else 1f; val uy = if (d > 1e-7f) by / d else 0f
        val lo = max(-ra, d - rb); val hi = min(ra, d + rb)
        if (hi <= lo + 1e-6f) { md.pos.fill(0f); for (k in 0..n) md.pos[k * 3 + 2] = z; return false }
        val m = (lo + hi) / 2; val cx = ux * m; val cy = uy * m
        md.pos[0] = cx; md.pos[1] = cy; md.pos[2] = z
        for (k in 0 until n) {
            val a = 2.0 * PI * k / n; val dx = cos(a).toFloat(); val dy = sin(a).toFloat()
            val ta = exit(cx, cy, dx, dy, 0f, 0f, ra); val tb = exit(cx, cy, dx, dy, bx, by, rb)
            val t = min(ta, tb)
            md.pos[(k + 1) * 3] = cx + dx * t; md.pos[(k + 1) * 3 + 1] = cy + dy * t; md.pos[(k + 1) * 3 + 2] = z
        }
        return true
    }

    private fun exit(px: Float, py: Float, dx: Float, dy: Float, ox: Float, oy: Float, r: Float): Float {
        val qx = px - ox; val qy = py - oy
        val b = qx * dx + qy * dy; val c = qx * qx + qy * qy - r * r
        return max(0f, -b + sqrt(max(0f, b * b - c)))
    }

    // ---------------------------------------------------------------- orientation

    /** Rotation taking local +X to [x] and local +Y to (the part of [y] orthogonal to) [x]. */
    fun basis(x: FloatArray, y: FloatArray): Q {
        val xl = norm(x)
        val d = y[0] * xl[0] + y[1] * xl[1] + y[2] * xl[2]
        val yl = norm(floatArrayOf(y[0] - d * xl[0], y[1] - d * xl[1], y[2] - d * xl[2]))
        val zl = floatArrayOf(xl[1] * yl[2] - xl[2] * yl[1], xl[2] * yl[0] - xl[0] * yl[2], xl[0] * yl[1] - xl[1] * yl[0])
        // columns = images of the axes; matrix -> quaternion (Shepperd)
        val m00 = xl[0]; val m10 = xl[1]; val m20 = xl[2]
        val m01 = yl[0]; val m11 = yl[1]; val m21 = yl[2]
        val m02 = zl[0]; val m12 = zl[1]; val m22 = zl[2]
        val tr = m00 + m11 + m22
        return when {
            tr > 0 -> { val s = sqrt(tr + 1f) * 2; Q((m21 - m12) / s, (m02 - m20) / s, (m10 - m01) / s, 0.25f * s) }
            m00 > m11 && m00 > m22 -> { val s = sqrt(1f + m00 - m11 - m22) * 2; Q(0.25f * s, (m01 + m10) / s, (m02 + m20) / s, (m21 - m12) / s) }
            m11 > m22 -> { val s = sqrt(1f + m11 - m00 - m22) * 2; Q((m01 + m10) / s, 0.25f * s, (m12 + m21) / s, (m02 - m20) / s) }
            else -> { val s = sqrt(1f + m22 - m00 - m11) * 2; Q((m02 + m20) / s, (m12 + m21) / s, 0.25f * s, (m10 - m01) / s) }
        }
    }

    fun norm(v: FloatArray): FloatArray { val l = max(sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]), 1e-12f); return floatArrayOf(v[0] / l, v[1] / l, v[2] / l) }

    /**
     * The close-up's SUN-FIXED frame: a geocentric J2000-ecliptic vector, turned about the ecliptic pole so
     * the Sun lies along -X, then mapped to display axes (x, z_ecl, -y_ecl) like SolarModel.ecl.
     */
    fun sunFixed(v: Vec3d, sunLonDeg: Double, scale: Double = 1.0): FloatArray {
        val a = Math.toRadians(180.0 - sunLonDeg); val c = cos(a); val s = sin(a)
        val x = v.x * c - v.y * s; val y = v.x * s + v.y * c
        return floatArrayOf((x * scale).toFloat(), (v.z * scale).toFloat(), (-y * scale).toFloat())
    }

    fun lerp(a: P3, b: P3, t: Float) = P3(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t)

    // ---------------------------------------------------------------- textures

    /** Soft round glow (white, alpha falls off as (1 - r)^2.2). */
    fun bakeGlow(n: Int): Image {
        val img = Image(n, n)
        for (y in 0 until n) for (x in 0 until n) {
            val dx = (x + 0.5f) / n * 2 - 1; val dy = (y + 0.5f) / n * 2 - 1
            val a = (1f - sqrt(dx * dx + dy * dy)).coerceIn(0f, 1f).pow(2.2f)
            img.px[y * n + x] = ProcTex.argb(a, 1f, 1f, 1f)
        }
        return img
    }

    /** Light-ray strip along V (cylinder v: 1 at y = -h/2 .. 0 at +h/2): fades in from the Sun, out at the far end. */
    fun bakeRay(h: Int): Image {
        val img = Image(4, h)
        for (y in 0 until h) {
            val t = 1f - (y + 0.5f) / h                      // 0 at the Sun end .. 1 at the far end
            val a = ProcTex.smooth01(t / 0.12f) * (1f - ProcTex.smooth01((t - 0.70f) / 0.30f))
            for (x in 0 until 4) img.px[y * 4 + x] = ProcTex.argb(a, 1f, 1f, 1f)
        }
        return img
    }

    /**
     * Shadow spot on the Earth along U (cap pole -> rim): dark in the middle (the deeper the eclipse), soft
     * toward the edge, and an orange rim = the edge of the penumbra (outside it: no eclipse at all).
     */
    fun bakeSpot(w: Int): Image {
        val img = Image(w, 4)
        for (x in 0 until w) {
            val t = (x + 0.5f) / w
            val px = if (t > 0.90f) ProcTex.argb(0.85f * (1f - ProcTex.smooth01((t - 0.96f) / 0.04f)), 1f, 0.62f, 0.25f)
                     else ProcTex.argb(0.82f * (1f - t / 0.9f).pow(0.7f) + 0.10f, 0f, 0f, 0f)
            for (y in 0 until 4) img.px[y * w + x] = px
        }
        return img
    }

    /**
     * Near-side maria at their real places (selenographic latitude, longitude east+, radius in deg; LROC /
     * IAU nomenclature centres, radius ~ half the named diameter). Their dark pattern is what makes a disc
     * read as OUR Moon ("the man in the Moon"), so the phase panel looks right at a glance.
     */
    private val MARIA = arrayOf(
        floatArrayOf(33f, -16f, 17f), floatArrayOf(28f, 17f, 10.5f), floatArrayOf(8.5f, 31f, 12.5f), floatArrayOf(17f, 59f, 8.5f),
        floatArrayOf(-8f, 51f, 12f), floatArrayOf(-15f, 35f, 5.5f), floatArrayOf(-21f, -17f, 11f), floatArrayOf(-24f, -39f, 6.5f),
        floatArrayOf(13f, 4f, 4.5f), floatArrayOf(8f, -31f, 8f), floatArrayOf(-10f, -23f, 6f),
        // Oceanus Procellarum (big, irregular: three overlapping blobs) and Mare Frigoris (a strip in the north)
        floatArrayOf(28f, -55f, 15f), floatArrayOf(10f, -58f, 14f), floatArrayOf(-5f, -48f, 10f),
        floatArrayOf(56f, -30f, 6f), floatArrayOf(57f, -2f, 7f), floatArrayOf(56f, 22f, 6f),
    )
    /** bright ray craters: Tycho, Copernicus, Kepler, Aristarchus (lat, lon east+, brightness) */
    private val RAYED = arrayOf(floatArrayOf(-43.3f, -11.4f, 1f), floatArrayOf(9.6f, -20.1f, 0.8f), floatArrayOf(8.1f, -38f, 0.6f), floatArrayOf(23.7f, -47.4f, 0.9f))

    /** Selenographic (lat, lon east+) -> direction on the mesh, near side centred on mesh -X (u = 0.5). */
    private fun seleno(lat: Float, lonE: Float): V3 = ProcTex.sphereDir(Math.toRadians(lat.toDouble()).toFloat(), Math.toRadians((180.0 - lonE)).toFloat())

    /**
     * The Moon at 17 cm across: bright cratered highlands, the real maria (soft, slightly irregular edges),
     * crater bowls with bright rims, four ray craters. Near side centred on u = 0.5 (mesh -X); with the near
     * side turned to the viewer, lunar north is up and Mare Crisium (east) on the right, as seen from Earth.
     */
    fun bakeMoonHi(w: Int, h: Int, seed: Int = 1969): Image {
        val img = Image(w, h)
        val mc = MARIA.map { seleno(it[0], it[1]) }
        val rc = RAYED.map { seleno(it[0], it[1]) }
        java.util.stream.IntStream.range(0, h).parallel().forEach { y ->
            val lat = PI.toFloat() * (0.5f - (y + 0.5f) / h)
            for (x in 0 until w) {
                val lon = 2f * PI.toFloat() * ((x + 0.5f) / w)
                val d = ProcTex.sphereDir(lat, lon)
                val high = 0.62f + 0.14f * (ProcTex.fbm(d * 5.5f, 4, seed) - 0.5f) + 0.06f * (ProcTex.fbm(d * 22f, 3, seed + 7) - 0.5f)
                // irregular coastlines: two octaves of noise on the distance field, overlapping basins merge
                val wobble = 0.55f * (ProcTex.fbm(d * 4.5f, 4, seed + 3) - 0.5f) + 0.25f * (ProcTex.fbm(d * 14f, 3, seed + 4) - 0.5f)
                // domain warp: basins are not circles
                val wx = d.x + 0.30f * (ProcTex.fbm(d * 2.6f, 3, seed + 20) - 0.5f)
                val wy = d.y + 0.30f * (ProcTex.fbm(d * 2.6f, 3, seed + 21) - 0.5f)
                val wz = d.z + 0.30f * (ProcTex.fbm(d * 2.6f, 3, seed + 22) - 0.5f)
                val wl = sqrt(wx * wx + wy * wy + wz * wz)
                var field = -1f
                for (k in MARIA.indices) {
                    val c = mc[k]
                    val ang = Math.toDegrees(kotlin.math.acos(((wx * c.x + wy * c.y + wz * c.z) / wl).coerceIn(-1f, 1f)).toDouble()).toFloat()
                    field = max(field, 1f - ang / MARIA[k][2])
                }
                val mare = ProcTex.smooth01((field + wobble + 0.05f) / 0.30f)
                val dark = 0.34f + 0.07f * (ProcTex.fbm(d * 9f, 3, seed + 5) - 0.5f)
                var v = ProcTex.lerp(high, dark, mare * 0.9f)
                // craters at two scales: dark floor, bright rim (fewer on the maria, which are younger)
                for ((sc, amp) in listOf(9f to 1f, 23f to 0.55f)) {
                    val (f1, _) = ProcTex.worley(d * sc, seed + (sc * 10).toInt())
                    val rim = exp(-((f1 - 0.30f) / 0.06f).pow(2)); val floor = exp(-(f1 / 0.20f).pow(2))
                    v += amp * (0.08f * rim - 0.06f * floor) * (1f - 0.7f * mare)
                }
                for (k in RAYED.indices) {
                    val c = rc[k]
                    val ang = kotlin.math.acos((d.x * c.x + d.y * c.y + d.z * c.z).coerceIn(-1f, 1f))
                    val spokes = 0.5f + 0.5f * sin(ProcTex.fbm(d * 30f, 2, seed + 11 + k) * 40f)
                    v += RAYED[k][2] * (0.22f * exp(-(ang / 0.025f).pow(2)) + 0.06f * exp(-(ang / 0.30f)) * spokes)
                }
                v = v.coerceIn(0.05f, 0.95f)
                img.px[y * w + x] = ProcTex.argb(1f, v * 1.0f, v * 0.975f, v * 0.94f)
            }
        }
        return img
    }

    /**
     * The inner corona around a TOTALLY eclipsed Sun, for a quad whose half-width = 3.2 solar radii: nothing
     * inside the limb (the Moon covers it), brightest at the limb, falling ~r^-3, with soft streamers.
     */
    fun bakeCoronaRing(n: Int, seed: Int = 1919): Image {
        val img = Image(n, n)
        val limb = 1f / 3.2f
        for (y in 0 until n) for (x in 0 until n) {
            val dx = (x + 0.5f) / n * 2 - 1; val dy = (y + 0.5f) / n * 2 - 1
            val r = sqrt(dx * dx + dy * dy); val phi = kotlin.math.atan2(dy, dx)
            val streak = 0.65f + 0.35f * ProcTex.fbm(V3(cos(phi) * 3f, sin(phi) * 3f, r * 2f), 3, seed) +
                0.25f * sin(phi * 2f).let { it * it }                      // equatorial streamers (solar-minimum look)
            val a = 0.95f * ProcTex.smooth01((r - limb * 0.98f) / (limb * 0.04f)) * (limb / max(r, limb)).pow(4.5f) * streak
            img.px[y * n + x] = ProcTex.argb(a.coerceIn(0f, 1f), 1f, 0.97f, 0.92f)
        }
        return img
    }

    fun len3(v: FloatArray) = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
}
