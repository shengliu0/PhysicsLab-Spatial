// BuoyancyCore — Archimedes with an overflow can (溢水杯) and a spring scale (弹簧测力计), pure Kotlin.
//
// THE EXPERIMENT (the one in every 8th-grade physics book): hang a cube on a spring scale, lower it
// into a can filled exactly to its spout, catch what overflows in a beaker.
//     F_buoy = G - F_scale            (weighing method, 称重法)
//     F_buoy = rho_fluid g V_sub      (Archimedes)
//     G_overflow = rho_fluid g V_sub  (the can was full: every displaced litre leaves by the spout)
// so G - F_scale = G_overflow. For a floater the string goes slack (F_scale = 0) and the overflow
// weighs exactly as much as the object: rho_obj V = rho_fluid V_sub -> V_sub/V = rho_obj/rho_fluid.
//
// MODEL (1 vertical degree of freedom, everything else exact bookkeeping):
//   m_eff y'' = -m g + rho_f g a^2 d + T(spring) - F_drag - F_rad
//   d    = submerged depth, from the IMPLICIT level: the cube itself raises the water it sits in
//   T    = k max(0, stretch) + scale dashpot (a string can pull, never push)
//   drag = 1/2 rho_f Cd a^2 v|v|, Cd = 1.05 (flat-faced cube, tabulated); air uses rho = 1.2
//   F_rad: wave-radiation heave damping while the cube pierces the surface, 10 % of critical
//          (typical of measured heave decay of box-shaped floats; the one tuned constant)
//   m_eff = m + 0.67 rho_f V_sub (added mass of a cube, C_a ~ 0.67)
//   spout: the can drains whatever stands above the spout lip with a 0.4 s time constant;
//          water is conserved exactly (V_tank + V_beaker = const, checked in the tests).
//
// AUDIT OF THE UNITY BUILD (BuoyancyDemo.cs, numbers in BuoyancyCoreTest):
//   * its motion was a fixed spring toward the equilibrium, a = 30 (y_eq - y) - 6 v, for EVERY
//     object/fluid pair: no weight, no buoyancy, no fluid in the dynamics. The ice block fell
//     through AIR at 12.8 m/s^2 (not 9.81) because the "spring" already pulled it from 0.35 m up.
//   * a sinker (SubmergedFraction = 1) was parked with its top FLUSH with the surface (centre
//     at -0.09 m) instead of resting on the tank floor at -0.40 m — while the status said "SINKS".
//   * the buoyancy arrow used the EQUILIBRIUM fraction, so it showed the full buoyant force while
//     the block was still in the air; the comment "the arrow grows with how deep you push it"
//     was false. There was no weight arrow, so a sinker showed a lone upward force.
//   * the free surface never rose: 4.24 L of displaced water in a 0.384 m^2 tank is +11 mm, and
//     "displaced volume" — the quantity in the law — was only a wireframe, never water you could see.

package com.bytedance.physicslab.solar.core.buoyancy

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object Buoy {
    const val G = 9.81
    const val RHO_AIR = 1.2
    const val CD_CUBE = 1.05
    const val CA_CUBE = 0.67
    const val ZETA_RAD = 0.10

    data class Stuff(val name: String, val en: String, val rho: Double)
    /** densities in kg/m^3 (20 C): ethanol 789, water 998 ~ 1000, saturated brine ~1200 */
    val FLUIDS = listOf(Stuff("酒精", "alcohol", 789.0), Stuff("水", "water", 1000.0), Stuff("浓盐水", "brine", 1200.0))
    /** pine ~600, ice 917, nylon-6 1150, aluminium 2700 */
    val SOLIDS = listOf(Stuff("木块", "wood", 600.0), Stuff("冰块", "ice", 917.0), Stuff("尼龙", "nylon", 1150.0), Stuff("铝块", "aluminium", 2700.0))

    fun buoyancy(rhoF: Double, g: Double, vSub: Double) = rhoF * g * vSub
    fun floats(rhoObj: Double, rhoF: Double) = rhoObj < rhoF
    /** fraction of the volume under the surface when floating freely; 1 if it sinks */
    fun floatFraction(rhoObj: Double, rhoF: Double) = if (rhoObj < rhoF) rhoObj / rhoF else 1.0
    /** small heave oscillation of a free-floating cube: omega^2 = rho_f g a^2 / (m + m_added) */
    fun heaveOmega(a: Double, rhoObj: Double, rhoF: Double, g: Double = G): Double {
        val m = rhoObj * a * a * a
        val ma = CA_CUBE * rhoF * a * a * a * floatFraction(rhoObj, rhoF)
        return sqrt(rhoF * g * a * a / (m + ma))
    }
    /** terminal sinking speed: (rho_o - rho_f) g a^3 = 1/2 rho_f Cd a^2 v^2 */
    fun terminalSpeed(a: Double, rhoObj: Double, rhoF: Double, g: Double = G) =
        sqrt(2 * (rhoObj - rhoF) * g * a / (rhoF * CD_CUBE))
}

/**
 * A cube of edge [a] hanging by a string from a spring scale whose top ([hook]) is moved by a
 * crane, above an overflow can of inner area [tankArea] filled to [spoutY], floor at [floorY].
 * Units SI, y up. The cube's position is its BOTTOM face, [y].
 */
class OverflowRig(
    val a: Double,
    var rhoObj: Double,
    var rhoF: Double,
    val tankArea: Double,
    val floorY: Double,
    val spoutY: Double,
    val k: Double = 200.0,            // spring scale: 20 N over 0.10 m
    val restLen: Double = 0.16,       // hook -> cube top with the spring relaxed
    val tauSpout: Double = 0.4,
    val g: Double = Buoy.G,
    var spoutOpen: Boolean = true,
) {
    val vol get() = a * a * a
    val mass get() = rhoObj * vol
    val weight get() = mass * g

    // ---- state
    var y = 0.0; var v = 0.0
    var hook = 0.0
    var hookTarget = 0.0
    var hookSpeed = 0.05
    var vTank = 0.0          // water in the can
    var vOut = 0.0           // water in the beaker
    var q = 0.0              // current outflow, m^3/s
    var time = 0.0
    var tension = 0.0
    var maxVsub = 0.0

    init { vTank = fullVolume(); hangAt(spoutY + 0.03); refill() }

    fun fullVolume() = tankArea * (spoutY - floorY)
    /** pour the beaker back: can exactly full again (with the cube where it is) */
    fun refill() { vOut = 0.0; vTank = fullVolume() - a * a * (spoutY - y).coerceIn(0.0, a); q = 0.0; maxVsub = vSubAt(y, vTank) }

    /** static hanging length of the spring for the current buoyancy (used to place the cube) */
    fun hangAt(bottomY: Double) {
        y = bottomY; v = 0.0
        val f = weight - Buoy.buoyancy(rhoF, g, vSubAt(y, vTank))
        hook = y + a + restLen + max(0.0, f) / k
        hookTarget = hook
    }

    /** Free surface height with the cube at bottom y: solves level = floor + (V_tank + V_sub(level)) / A. */
    fun levelAt(yb: Double, vt: Double): Double {
        val l0 = floorY + vt / tankArea                      // cube out of the water
        if (l0 <= yb) return l0
        val l1 = floorY + (vt + vol) / tankArea              // fully under
        if (l1 >= yb + a) return l1
        return (tankArea * floorY + vt - a * a * yb) / (tankArea - a * a)   // pierces the surface
    }
    fun vSubAt(yb: Double, vt: Double) = a * a * (levelAt(yb, vt) - yb).coerceIn(0.0, a)

    val level get() = levelAt(y, vTank)
    val depth get() = (level - y).coerceIn(0.0, a)
    val vSub get() = a * a * depth
    val buoyancy get() = Buoy.buoyancy(rhoF, g, vSub)
    val overflowWeight get() = rhoF * g * vOut
    /** spring extension (what the pointer shows) */
    val stretch get() = max(0.0, hook - restLen - (y + a))
    val scaleReading get() = k * stretch
    val slack get() = hook - restLen - (y + a) < 0

    /** Advance [dt] with fixed substeps (semi-implicit Euler, h <= 0.5 ms). */
    fun step(dt: Double) {
        // no time, no change: dt = 0 used to reach `move / h` with h = 0 and poison the whole
        // state with NaN (the headset build calls update(0) once before the first frame)
        if (!(dt > 0.0)) return
        val n = max(1, kotlin.math.ceil(dt / 5e-4).toInt())
        val h = dt / n
        repeat(n) { sub(h) }
    }

    private fun sub(h: Double) {
        // crane: constant speed toward the target, eased over the last 2 cm so it never jerks
        val dh = hookTarget - hook
        var hookVel = 0.0
        if (abs(dh) > 1e-9) {
            val sp = hookSpeed * min(1.0, 0.25 + abs(dh) / 0.02)
            val move = dh.coerceIn(-sp * h, sp * h)
            hook += move; hookVel = move / h
        }
        val lvl = levelAt(y, vTank)
        val d = (lvl - y).coerceIn(0.0, a)
        val vs = a * a * d
        val fb = Buoy.buoyancy(rhoF, g, vs)
        // spring scale: k x, plus a small dashpot inside the scale (zeta ~ 0.08 on the hanging mass)
        val ext = hook - restLen - (y + a)
        val cS = 2 * 0.08 * sqrt(k * mass)
        tension = if (ext > 0) max(0.0, k * ext + cS * (hookVel - v)) else 0.0
        // drag: water on the part that is in it (the bottom face leads in heave), air otherwise
        val rho = if (d > 0) rhoF else Buoy.RHO_AIR
        val drag = 0.5 * rho * Buoy.CD_CUBE * a * a * v * abs(v)
        val mEff = mass + Buoy.CA_CUBE * rhoF * vs
        // wave-radiation damping only while the cube pierces the surface (it makes the waves)
        val piercing = d > 0 && d < a
        val rad = if (piercing) Buoy.ZETA_RAD * 2 * sqrt(rhoF * g * a * a * mEff) * v else 0.0
        val f = -mass * g + fb + tension - drag - rad
        v += f / mEff * h
        y += v * h
        if (y < floorY) { y = floorY; if (v < 0) v = 0.0 }          // resting on the can's floor
        // spout: whatever stands above the lip drains into the beaker
        val lv = levelAt(y, vTank)
        q = if (spoutOpen && lv > spoutY) tankArea * (lv - spoutY) / tauSpout else 0.0
        val dv = min(q * h, max(0.0, (lv - spoutY) * tankArea))
        vTank -= dv; vOut += dv
        maxVsub = max(maxVsub, a * a * (levelAt(y, vTank) - y).coerceIn(0.0, a))
        time += h
    }

    /** Hook height at which the cube is held fully submerged with its top [clear] below the spout level,
     *  or (floaters) where the string has [slackBy] of slack around the free-floating cube. */
    fun loweredHook(clear: Double = 0.02, slackBy: Double = 0.03): Double {
        if (Buoy.floats(rhoObj, rhoF)) {
            val top = spoutY - Buoy.floatFraction(rhoObj, rhoF) * a + a
            return top + restLen - slackBy
        }
        val t = weight - Buoy.buoyancy(rhoF, g, vol)
        return (spoutY - clear) + restLen + t / k
    }
}
