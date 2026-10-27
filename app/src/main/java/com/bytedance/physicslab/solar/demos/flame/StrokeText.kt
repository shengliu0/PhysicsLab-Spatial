// StrokeText — a tiny single-stroke font, baked ONCE into anti-aliased textures, for the few
// language-neutral marks that live IN the scene: element symbols on the salt tiles and answer cards,
// the nm axis of the spectroscope, the eV values in the level diagram. (Floating LabelSpecs are
// limited to six; these are not text the user reads as a sentence, they are instrument markings.)
//
// Glyphs are polylines in a cap-height-1 box (baseline y = 0, y up); the bake measures the distance
// of every pixel to the strokes (round caps) and writes it as white brightness (RGB, alpha 1): the
// same "intensity in RGB" convention as OptGeo's ramps, drawn with an ADD material on a dark plate.

package com.bytedance.physicslab.solar.demos.flame

import com.bytedance.physicslab.solar.render.Image
import com.bytedance.physicslab.solar.render.ProcTex
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object StrokeText {
    private class Glyph(val w: Float, val strokes: List<FloatArray>)

    private fun arc(cx: Float, cy: Float, rx: Float, ry: Float, a0: Float, a1: Float, n: Int = 18): FloatArray {
        val out = FloatArray((n + 1) * 2)
        for (i in 0..n) {
            val a = Math.toRadians((a0 + (a1 - a0) * i / n).toDouble())
            out[i * 2] = cx + rx * cos(a).toFloat(); out[i * 2 + 1] = cy + ry * sin(a).toFloat()
        }
        return out
    }
    private fun p(vararg v: Float) = v
    private fun cat(a: FloatArray, b: FloatArray) = a + b

    private val G: Map<Char, Glyph> by lazy {
        mapOf(
            '0' to Glyph(0.62f, listOf(arc(0.31f, 0.5f, 0.27f, 0.5f, 0f, 360f, 36))),
            '1' to Glyph(0.50f, listOf(p(0.12f, 0.78f, 0.32f, 1f, 0.32f, 0f))),
            '2' to Glyph(0.62f, listOf(cat(arc(0.30f, 0.72f, 0.26f, 0.27f, 160f, -35f), p(0.04f, 0f, 0.58f, 0f)))),
            '3' to Glyph(0.60f, listOf(arc(0.28f, 0.75f, 0.24f, 0.25f, 150f, -90f), arc(0.28f, 0.27f, 0.28f, 0.27f, 90f, -150f))),
            '4' to Glyph(0.62f, listOf(p(0.46f, 0f, 0.46f, 1f, 0.03f, 0.32f, 0.60f, 0.32f))),
            '5' to Glyph(0.62f, listOf(cat(p(0.55f, 1f, 0.12f, 1f, 0.08f, 0.56f), arc(0.30f, 0.33f, 0.28f, 0.33f, 135f, -145f)))),
            '6' to Glyph(0.62f, listOf(arc(0.31f, 0.31f, 0.27f, 0.31f, 0f, 360f, 30), p(0.52f, 0.97f, 0.30f, 0.84f, 0.12f, 0.60f, 0.04f, 0.32f))),
            '7' to Glyph(0.60f, listOf(p(0.04f, 1f, 0.57f, 1f, 0.22f, 0f))),
            '8' to Glyph(0.62f, listOf(arc(0.31f, 0.76f, 0.22f, 0.24f, 0f, 360f, 28), arc(0.31f, 0.27f, 0.27f, 0.27f, 0f, 360f, 30))),
            '9' to Glyph(0.62f, listOf(arc(0.31f, 0.69f, 0.27f, 0.31f, 0f, 360f, 30), p(0.58f, 0.68f, 0.50f, 0.38f, 0.32f, 0.12f, 0.10f, 0.02f))),
            '.' to Glyph(0.24f, listOf(p(0.12f, 0.03f, 0.12f, 0.04f))),
            ' ' to Glyph(0.30f, emptyList()),
            '?' to Glyph(0.62f, listOf(cat(arc(0.30f, 0.74f, 0.25f, 0.25f, 160f, -55f), p(0.31f, 0.32f)), p(0.31f, 0.03f, 0.31f, 0.04f))),
            '+' to Glyph(0.62f, listOf(p(0.31f, 0.16f, 0.31f, 0.76f), p(0.03f, 0.46f, 0.59f, 0.46f))),
            'B' to Glyph(0.66f, listOf(cat(cat(p(0.36f, 0.5f, 0.07f, 0.5f, 0.07f, 1f, 0.34f, 1f), arc(0.34f, 0.75f, 0.24f, 0.25f, 90f, -90f)),
                p(0.07f, 0.5f)), cat(cat(p(0.36f, 0.5f), arc(0.36f, 0.25f, 0.26f, 0.25f, 90f, -90f)), p(0.07f, 0f, 0.07f, 0.5f)))),
            'C' to Glyph(0.70f, listOf(arc(0.38f, 0.5f, 0.34f, 0.5f, 42f, 318f, 30))),
            'H' to Glyph(0.70f, listOf(p(0.07f, 0f, 0.07f, 1f), p(0.63f, 0f, 0.63f, 1f), p(0.07f, 0.5f, 0.63f, 0.5f))),
            'K' to Glyph(0.64f, listOf(p(0.07f, 0f, 0.07f, 1f), p(0.60f, 1f, 0.07f, 0.38f), p(0.25f, 0.58f, 0.62f, 0f))),
            'L' to Glyph(0.56f, listOf(p(0.07f, 1f, 0.07f, 0f, 0.53f, 0f))),
            'N' to Glyph(0.70f, listOf(p(0.07f, 0f, 0.07f, 1f, 0.63f, 0f, 0.63f, 1f))),
            'O' to Glyph(0.80f, listOf(arc(0.40f, 0.5f, 0.35f, 0.5f, 0f, 360f, 40))),
            'S' to Glyph(0.62f, listOf(cat(arc(0.31f, 0.75f, 0.24f, 0.25f, 15f, 270f), arc(0.31f, 0.25f, 0.26f, 0.25f, 90f, -165f)))),
            'U' to Glyph(0.68f, listOf(cat(cat(p(0.07f, 1f), arc(0.34f, 0.30f, 0.27f, 0.30f, 180f, 360f)), p(0.61f, 1f)))),
            'V' to Glyph(0.66f, listOf(p(0.03f, 1f, 0.33f, 0f, 0.63f, 1f))),
            'a' to Glyph(0.62f, listOf(arc(0.28f, 0.33f, 0.24f, 0.33f, 0f, 360f, 28), p(0.53f, 0.68f, 0.53f, 0f))),
            'e' to Glyph(0.58f, listOf(cat(p(0.05f, 0.34f, 0.52f, 0.34f), arc(0.285f, 0.34f, 0.24f, 0.34f, 0f, 325f, 26)))),
            'i' to Glyph(0.22f, listOf(p(0.11f, 0f, 0.11f, 0.66f), p(0.11f, 0.92f, 0.11f, 0.93f))),
            'l' to Glyph(0.22f, listOf(p(0.11f, 0f, 0.11f, 1f))),
            'm' to Glyph(0.84f, listOf(p(0.06f, 0f, 0.06f, 0.66f), cat(cat(p(0.06f, 0.44f), arc(0.235f, 0.44f, 0.175f, 0.22f, 180f, 0f, 10)), p(0.41f, 0f)),
                cat(cat(p(0.41f, 0.44f), arc(0.585f, 0.44f, 0.175f, 0.22f, 180f, 0f, 10)), p(0.76f, 0f)))),
            'n' to Glyph(0.58f, listOf(p(0.06f, 0f, 0.06f, 0.66f), cat(cat(p(0.06f, 0.40f), arc(0.28f, 0.40f, 0.22f, 0.26f, 180f, 0f, 12)), p(0.50f, 0f)))),
            'r' to Glyph(0.44f, listOf(p(0.07f, 0f, 0.07f, 0.66f), arc(0.33f, 0.36f, 0.26f, 0.30f, 180f, 75f, 10))),
            'u' to Glyph(0.58f, listOf(cat(cat(p(0.06f, 0.66f), arc(0.28f, 0.26f, 0.22f, 0.26f, 180f, 360f, 12)), p(0.50f, 0.66f)), p(0.50f, 0.66f, 0.50f, 0f))),
            '₂' to Glyph(0.42f, listOf(cat(arc(0.20f, 0.30f, 0.15f, 0.15f, 160f, -35f, 10), p(0.04f, -0.12f, 0.36f, -0.12f)))),
        )
    }

    /** Width of [s] in cap heights (glyph advance + spacing). */
    fun widthOf(s: String, spacing: Float = 0.10f): Float = s.sumOf { ((G[it]?.w ?: 0.5f) + spacing).toDouble() }.toFloat() - spacing

    /**
     * Bake [s] into an image [hPx] pixels tall (cap height = 0.66 of it, centred), stroke width
     * [stroke] cap heights. Returns the image; its aspect is w/h.
     */
    fun bake(s: String, hPx: Int = 64, stroke: Float = 0.13f, spacing: Float = 0.10f, padCaps: Float = 0.30f): Image {
        val cap = hPx * 0.62f
        val wCaps = widthOf(s, spacing) + 2 * padCaps
        val w = max(8, (wCaps * cap).toInt() + 1)
        val img = Image(w, hPx)
        // segments in pixel space (y down)
        val segs = ArrayList<FloatArray>()
        var x0 = padCaps
        val base = (hPx + cap) / 2f
        for (ch in s) {
            val g = G[ch] ?: G['?']!!
            for (st in g.strokes) {
                val n = st.size / 2
                if (n == 1) { segs += floatArrayOf((x0 + st[0]) * cap, base - st[1] * cap, (x0 + st[0]) * cap, base - st[1] * cap); continue }
                for (i in 0 until n - 1) segs += floatArrayOf((x0 + st[i * 2]) * cap, base - st[i * 2 + 1] * cap, (x0 + st[i * 2 + 2]) * cap, base - st[i * 2 + 3] * cap)
            }
            x0 += g.w + spacing
        }
        val r = stroke * cap / 2; val aa = 0.8f
        for (y in 0 until hPx) for (x in 0 until w) {
            val px = x + 0.5f; val py = y + 0.5f
            var d = Float.MAX_VALUE
            for (sg in segs) {
                if (px < min(sg[0], sg[2]) - r - 2 || px > max(sg[0], sg[2]) + r + 2 || py < min(sg[1], sg[3]) - r - 2 || py > max(sg[1], sg[3]) + r + 2) continue
                d = min(d, segDist(px, py, sg))
            }
            val a = ((r + aa - d) / (2 * aa)).coerceIn(0f, 1f)
            val v = a.toDouble().pow(1 / 2.2).toFloat()            // encode: the bake is sRGB, the stroke edge stays even
            img.px[y * w + x] = ProcTex.argb(1f, v, v, v)
        }
        return img
    }

    private fun segDist(px: Float, py: Float, s: FloatArray): Float {
        val dx = s[2] - s[0]; val dy = s[3] - s[1]; val l2 = dx * dx + dy * dy
        val t = if (l2 < 1e-9f) 0f else (((px - s[0]) * dx + (py - s[1]) * dy) / l2).coerceIn(0f, 1f)
        val ex = s[0] + t * dx - px; val ey = s[1] + t * dy - py
        return sqrt(ex * ex + ey * ey)
    }
}
