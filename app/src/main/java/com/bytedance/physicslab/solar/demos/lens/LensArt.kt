// LensArt — static textures and meshes for the convex-lens bench (pure Kotlin: MeshData / Image only).
//
//   * a tiny stroke font (7-segment digits, F, prime) rasterised into textures: the cm scale on the
//     rail and the F / 2F tags — the headset has no text in 3-D except the 6 label chips
//   * the candle-flame picture and its BLUR LEVELS: the image on the screen is the flame convolved
//     with the blur circle of the lens (diameter c = D·|s − v|/v). Convolving per frame is out of the
//     question on the headset, so 8 levels (blur c / image height = 0 … 12) are baked once and the
//     demo cross-fades the two neighbouring levels on two 4-vertex quads.
//   * the biconvex lens (surface of revolution, real lensmaker curvature for each focal length)

package com.bytedance.physicslab.solar.demos.lens

import com.bytedance.physicslab.solar.core.lens.ThinLens
import com.bytedance.physicslab.solar.render.Image
import com.bytedance.physicslab.solar.render.MeshData
import com.bytedance.physicslab.solar.render.ProcTex
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object LensArt {

    // ================================================================ stroke font

    private const val GW = 0.56f          // glyph width (height = 1), y up

    /** Segments (x0, y0, x1, y1) of one glyph in a GW x 1 box, and its advance. */
    private fun glyph(c: Char): Pair<List<FloatArray>, Float> {
        val a = floatArrayOf(0f, 1f, GW, 1f); val b = floatArrayOf(GW, 1f, GW, 0.5f); val cc = floatArrayOf(GW, 0.5f, GW, 0f)
        val d = floatArrayOf(0f, 0f, GW, 0f); val e = floatArrayOf(0f, 0.5f, 0f, 0f); val f = floatArrayOf(0f, 1f, 0f, 0.5f)
        val g = floatArrayOf(0f, 0.5f, GW, 0.5f)
        val segs = when (c) {
            '0' -> listOf(a, b, cc, d, e, f)
            '1' -> listOf(floatArrayOf(GW * 0.55f, 1f, GW * 0.55f, 0f), floatArrayOf(GW * 0.55f, 1f, GW * 0.2f, 0.8f))
            '2' -> listOf(a, b, g, e, d)
            '3' -> listOf(a, b, g, cc, d)
            '4' -> listOf(f, g, b, cc)
            '5' -> listOf(a, f, g, cc, d)
            '6' -> listOf(a, f, g, cc, d, e)
            '7' -> listOf(a, b, cc)
            '8' -> listOf(a, b, cc, d, e, f, g)
            '9' -> listOf(a, b, cc, d, f, g)
            'F' -> listOf(a, f, e, floatArrayOf(0f, 0.5f, GW * 0.78f, 0.5f))
            '\'' -> listOf(floatArrayOf(0.16f, 1.02f, 0.04f, 0.70f))
            else -> emptyList()
        }
        val adv = when (c) { '\'' -> 0.30f; '1' -> GW * 0.8f; else -> GW }
        return segs to adv
    }

    /** Width of [s] in glyph-height units (letter spacing 0.30). */
    fun textWidth(s: String): Float = s.map { glyph(it).second }.sum() + 0.30f * (s.length - 1).coerceAtLeast(0)

    /** Max-blend an anti-aliased capsule (pixel units, y down) into a luminance buffer. */
    private fun capsule(buf: FloatArray, w: Int, h: Int, x0: Float, y0: Float, x1: Float, y1: Float, hw: Float, value: Float) {
        val xa = max(0, (min(x0, x1) - hw - 2).toInt()); val xb = min(w - 1, (max(x0, x1) + hw + 2).toInt())
        val ya = max(0, (min(y0, y1) - hw - 2).toInt()); val yb = min(h - 1, (max(y0, y1) + hw + 2).toInt())
        val dx = x1 - x0; val dy = y1 - y0; val l2 = max(1e-6f, dx * dx + dy * dy)
        for (y in ya..yb) for (x in xa..xb) {
            val px = x + 0.5f; val py = y + 0.5f
            val t = (((px - x0) * dx + (py - y0) * dy) / l2).coerceIn(0f, 1f)
            val qx = x0 + t * dx - px; val qy = y0 + t * dy - py
            val dist = sqrt(qx * qx + qy * qy)
            val v = (hw + 0.5f - dist).coerceIn(0f, 1f) * value
            val i = y * w + x
            if (v > buf[i]) buf[i] = v
        }
    }

    /** Draw [s] with its left edge at [x], baseline at [yBase] (pixels, y down), glyph height [gh]. */
    private fun text(buf: FloatArray, w: Int, h: Int, s: String, x: Float, yBase: Float, gh: Float, hw: Float) {
        var cx = x
        for (ch in s) {
            val (segs, adv) = glyph(ch)
            for (sg in segs) capsule(buf, w, h, cx + sg[0] * gh, yBase - sg[1] * gh, cx + sg[2] * gh, yBase - sg[3] * gh, hw, 1f)
            cx += (adv + 0.30f) * gh
        }
    }

    /** Coverage buffer -> white image with alpha = coverage (draw with Blend.TRANSPARENT). */
    private fun toImage(buf: FloatArray, w: Int, h: Int): Image {
        val img = Image(w, h)
        for (i in buf.indices) img.px[i] = ProcTex.argb(buf[i].coerceIn(0f, 1f), 1f, 1f, 1f)
        return img
    }

    /** The rail scale: [CM_LO, CM_HI] cm across the texture; 1 / 5 / 10 cm ticks hang from the top edge,
     *  numbers every 10 cm below them. */
    const val CM_LO = -1f
    const val CM_HI = 101f
    fun rulerTex(w: Int = 2048, h: Int = 56): Image {
        val buf = FloatArray(w * h)
        val ppc = w / (CM_HI - CM_LO)
        for (k in 0..100) {
            val x = (k - CM_LO) * ppc
            val len = when { k % 10 == 0 -> 0.42f; k % 5 == 0 -> 0.30f; else -> 0.17f } * h
            capsule(buf, w, h, x, 0f, x, len, if (k % 10 == 0) 1.3f else 0.8f, if (k % 5 == 0) 1f else 0.75f)
        }
        val gh = 0.36f * h
        for (k in 0..100 step 10) {
            val s = k.toString()
            val x = (k - CM_LO) * ppc
            val left = when (k) { 0 -> x - 0.2f * gh; 100 -> x - textWidth(s) * gh + 0.2f * gh; else -> x - textWidth(s) * gh / 2 }
            text(buf, w, h, s, left, h - 0.08f * h, gh, 1.25f)   // 0 and 100 kept inside the strip
        }
        return toImage(buf, w, h)
    }

    /** A small tag like "2F'" (prime written as '), centred, glyph height 70 % of the texture. */
    fun tagTex(s: String, w: Int = 96, h: Int = 48): Image {
        val buf = FloatArray(w * h)
        val gh = 0.70f * h
        text(buf, w, h, s, (w - textWidth(s) * gh) / 2, h - 0.15f * h, gh, 2.2f)
        return toImage(buf, w, h)
    }

    // ================================================================ glows / fades

    /** Radial soft glow, white with alpha = brightness (ADD sprite). */
    fun glowTex(n: Int = 64, k: Float = 3.2f): Image {
        val img = Image(n, n)
        for (y in 0 until n) for (x in 0 until n) {
            val dx = (x + 0.5f) / n * 2 - 1; val dy = (y + 0.5f) / n * 2 - 1
            val r2 = dx * dx + dy * dy
            val v = if (r2 >= 1f) 0f else exp(-k * r2) * (1f - r2)
            val c = enc(v)
            img.px[y * n + x] = ProcTex.argb(1f, c, c, c)
        }
        return img
    }

    /** Fade along U for the ghost candle body (a tube whose path runs top -> bottom): bright at u = 0, gone at u = 1. */
    fun fadeTex(w: Int = 64): Image {
        val img = Image(w, 4)
        for (x in 0 until w) {
            val t = x / (w - 1f)
            val c = enc(0.85f * (1f - ProcTex.smooth01(t / 0.80f)))
            for (y in 0 until 4) img.px[y * w + x] = ProcTex.argb(1f, c, c, c)
        }
        return img
    }

    fun enc(l: Float): Float {
        val v = l.coerceIn(0f, 1f)
        return if (v <= 0.0031308f) 12.92f * v else 1.055f * v.pow(1f / 2.4f) - 0.055f
    }

    // ================================================================ the flame and its blurred images

    /** Flame picture domain in flame heights: X in [-XW, XW], Y in [Y0, Y1] (0 = flame base, 1 = tip). */
    const val XW = 0.30f
    const val Y0 = -0.04f
    const val Y1 = 1.04f
    /** Blur levels: blur-circle diameter / sharp image height. */
    val BLUR = floatArrayOf(0f, 0.06f, 0.15f, 0.35f, 0.8f, 1.8f, 4.5f, 12f)
    const val TW = 40
    const val TH = 64

    /** Linear RGB of the candle flame at (X, Y) in flame heights: orange envelope, yellow luminous zone,
     *  a near-white hot centre and the small blue zone at the base. */
    fun flame(x: Float, y: Float, out: FloatArray) {
        out[0] = 0f; out[1] = 0f; out[2] = 0f
        if (y <= 0f || y >= 1f) return
        val hw = 0.27f * sin(PI.toFloat() * y.pow(0.55f)).pow(0.9f)
        if (hw <= 1e-4f) return
        val q = abs(x) / hw
        if (q >= 1f) return
        val edge = ProcTex.smooth01((1f - q) / 0.35f) * ProcTex.smooth01(y / 0.04f)
        val lum = ProcTex.smooth01((y - 0.10f) / 0.18f)
        val core = exp(-(q / 0.62f).pow(2)) * exp(-((y - 0.40f) / 0.30f).pow(2)) * lum
        val hot = exp(-(q / 0.40f).pow(2)) * exp(-((y - 0.34f) / 0.14f).pow(2)) * lum
        val blue = exp(-((y - 0.07f) / 0.07f).pow(2)) * edge
        out[0] = 0.85f * edge + 0.55f * core + 0.35f * hot + 0.10f * blue
        out[1] = 0.30f * edge + 0.42f * core + 0.33f * hot + 0.18f * blue
        out[2] = 0.04f * edge + 0.10f * core + 0.25f * hot + 0.80f * blue
    }

    /**
     * Blur level k: the flame convolved with a uniform disc of diameter BLUR[k] (flame heights), sampled
     * over the padded domain X in [-(XW + r/2), XW + r/2], Y in [Y0 - r/2, Y1 + r/2], UPRIGHT (row 0 =
     * the tip side). Brightness: the convolution spreads the same light over a larger area (the peak
     * falls); the stored image keeps a compressed version of that fall (gain (peak0/peak)^0.55) so a
     * very defocused patch is visibly dimmer but not invisible.
     */
    fun blurLevels(): List<Image> {
        val out = ArrayList<Image>()
        val tmp = FloatArray(3)
        var peak0 = 1f
        // flame samples for the wide discs (reverse convolution: which flame points fall inside the disc)
        val fx = 24; val fy = 40
        val fs = ArrayList<FloatArray>()
        for (j in 0 until fy) for (i in 0 until fx) {
            val x = -XW + 2 * XW * (i + 0.5f) / fx; val y = (j + 0.5f) / fy
            flame(x, y, tmp)
            if (tmp[0] + tmp[1] + tmp[2] > 1e-4f) fs += floatArrayOf(x, y, tmp[0], tmp[1], tmp[2])
        }
        val cellA = (2 * XW / fx) * (1f / fy)
        for ((k, r) in BLUR.withIndex()) {
            val img = Image(TW, TH)
            val lum = FloatArray(TW * TH * 3)
            val xw = XW + r / 2; val ya = Y0 - r / 2; val yb = Y1 + r / 2
            val texel = max(2 * xw / TW, (yb - ya) / TH)
            // Vogel disc samples for the narrow discs
            val nd = if (r <= 0f) 1 else 96
            val ga = PI.toFloat() * (3f - sqrt(5f))
            for (ty in 0 until TH) for (tx in 0 until TW) {
                val x = -xw + 2 * xw * (tx + 0.5f) / TW
                val y = yb - (yb - ya) * (ty + 0.5f) / TH           // row 0 = top = tip side
                var cr = 0f; var cg = 0f; var cb = 0f
                if (r <= 0.9f) {
                    for (i in 0 until nd) {
                        val rr = if (nd == 1) 0f else r / 2 * sqrt((i + 0.5f) / nd)
                        val a = i * ga
                        flame(x - rr * cos(a), y - rr * sin(a), tmp)
                        cr += tmp[0]; cg += tmp[1]; cb += tmp[2]
                    }
                    cr /= nd; cg /= nd; cb /= nd
                } else {
                    val rad = r / 2; val soft = max(texel, 0.04f * r)
                    val area = PI.toFloat() * rad * rad
                    for (p in fs) {
                        val dx = x - p[0]; val dy = y - p[1]
                        val d = sqrt(dx * dx + dy * dy)
                        val w = ((rad - d) / soft + 0.5f).coerceIn(0f, 1f)
                        if (w > 0f) { cr += w * p[2]; cg += w * p[3]; cb += w * p[4] }
                    }
                    val norm = cellA / area
                    cr *= norm; cg *= norm; cb *= norm
                }
                val o = (ty * TW + tx) * 3
                lum[o] = cr; lum[o + 1] = cg; lum[o + 2] = cb
            }
            val peak = lum.maxOrNull()!!.coerceAtLeast(1e-6f)
            if (k == 0) peak0 = peak
            val gain = (peak0 / peak).pow(0.55f) * 1.25f / peak0
            for (i in 0 until TW * TH) {
                img.px[i] = ProcTex.argb(1f, enc(lum[i * 3] * gain), enc(lum[i * 3 + 1] * gain), enc(lum[i * 3 + 2] * gain))
            }
            out += img
        }
        return out
    }

    // ================================================================ lens

    /**
     * Symmetric biconvex lens about the X axis (optical axis), centred at the origin: clear radius [a],
     * surface radius [rCurv] (lensmaker, R = 2(n − 1)f), edge thickness [edge]. Outward CCW winding.
     */
    fun lens(a: Float, rCurv: Float, edge: Float, rings: Int = 10, seg: Int = 48): MeshData {
        val pos = ArrayList<Float>(); val nrm = ArrayList<Float>(); val uv = ArrayList<Float>(); val idx = ArrayList<Int>()
        val sagA = ThinLens.sag(rCurv.toDouble(), a.toDouble()).toFloat()
        // profile strips: (x, r, nx, nr) per ring
        fun strip(prof: List<FloatArray>) {
            val base = pos.size / 3
            for ((ri, p) in prof.withIndex()) for (s in 0..seg) {
                val ph = 2.0 * PI * s / seg; val c = cos(ph).toFloat(); val sn = sin(ph).toFloat()
                pos += p[0]; pos += p[1] * c; pos += p[1] * sn
                nrm += p[2]; nrm += p[3] * c; nrm += p[3] * sn
                uv += s.toFloat() / seg; uv += ri.toFloat() / (prof.size - 1)
            }
            for (ri in 0 until prof.size - 1) for (s in 0 until seg) {
                val i0 = base + ri * (seg + 1) + s; val i1 = i0 + 1; val j0 = i0 + seg + 1; val j1 = j0 + 1
                for (t in listOf(intArrayOf(i0, j0, i1), intArrayOf(i1, j0, j1))) {
                    // orient each triangle so its geometric normal agrees with the vertex normals
                    val ax = pos[t[1] * 3] - pos[t[0] * 3]; val ay = pos[t[1] * 3 + 1] - pos[t[0] * 3 + 1]; val az = pos[t[1] * 3 + 2] - pos[t[0] * 3 + 2]
                    val bx = pos[t[2] * 3] - pos[t[0] * 3]; val by = pos[t[2] * 3 + 1] - pos[t[0] * 3 + 1]; val bz = pos[t[2] * 3 + 2] - pos[t[0] * 3 + 2]
                    val cx = ay * bz - az * by; val cy = az * bx - ax * bz; val cz = ax * by - ay * bx
                    var dot = 0f
                    for (v in t) dot += cx * nrm[v * 3] + cy * nrm[v * 3 + 1] + cz * nrm[v * 3 + 2]
                    if (abs(cx) + abs(cy) + abs(cz) < 1e-12f) continue          // the degenerate fan at the pole
                    if (dot >= 0f) { idx += t[0]; idx += t[1]; idx += t[2] } else { idx += t[0]; idx += t[2]; idx += t[1] }
                }
            }
        }
        val x0 = edge / 2 + sagA
        val front = (0..rings).map { i ->
            val r = a * i / rings
            val x = edge / 2 + sagA - ThinLens.sag(rCurv.toDouble(), r.toDouble()).toFloat()
            val cxC = x0 - rCurv
            floatArrayOf(x, r, (x - cxC) / rCurv, r / rCurv)
        }
        strip(front)
        strip(front.map { floatArrayOf(-it[0], it[1], -it[2], it[3]) })
        strip(listOf(floatArrayOf(edge / 2, a, 0f, 1f), floatArrayOf(-edge / 2, a, 0f, 1f)))
        return MeshData(pos.toFloatArray(), nrm.toFloatArray(), uv.toFloatArray(), idx.toIntArray())
    }

    /** Closed circle of radius [r] in the YZ plane (lens rim, holder ring), as a tube. */
    fun ringYZ(r: Float, tube: Float, n: Int = 48): MeshData {
        val p = FloatArray(n * 3) { k -> val a = 2 * PI * (k / 3) / n; when (k % 3) { 0 -> 0f; 1 -> (r * cos(a)).toFloat(); else -> (r * sin(a)).toFloat() } }
        return com.bytedance.physicslab.solar.render.MeshGen.tube(p, tube, 8, true)
    }
}
