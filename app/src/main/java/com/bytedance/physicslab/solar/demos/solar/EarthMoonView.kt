// EarthMoonView — the 地月 close-up: Sun, Earth and Moon in a SUN-FIXED frame (the Sun always sits in the
// same place, front-left; Earth at the centre), so the user always sees where the light comes from.
//
// WHAT IS TRUE, WHAT IS NOT (the panel and labels say so):
//   true       every DIRECTION: Sun, Moon (ELP series), Earth's axis, the Moon's tilted orbit and its line
//              of nodes; the Moon's phase is lighting, not a lookup; the eclipse geometry (core/Eclipse).
//   compressed the Sun is ~0.8 m away instead of 389 x the Moon's distance (rays are drawn PARALLEL, as
//              sunlight is); the Moon's orbit is 4.4 Earth radii instead of 60.
//   enlarged   Earth and Moon (true RATIO to each other); the Sun.
// ECLIPSE LOCK. Real eclipses only work with true proportions, which are invisible at this size. So the
// preset view draws the shadow CROSS-SECTION at the Moon to scale in Earth radii (umbra 0.7-0.75, penumbra
// ~1.28, Moon 0.27, the Moon's offset from the axis = the model's gamma) and compresses only the Sun-Earth
// axis; the whole rig zooms in (x1.35) on the alignment.

package com.bytedance.physicslab.solar.demos.solar

import com.bytedance.physicslab.solar.core.Eclipse
import com.bytedance.physicslab.solar.core.Lunar
import com.bytedance.physicslab.solar.core.P3
import com.bytedance.physicslab.solar.core.Sky
import com.bytedance.physicslab.solar.core.Solar
import com.bytedance.physicslab.solar.core.Vec3d
import com.bytedance.physicslab.solar.lab.Q
import com.bytedance.physicslab.solar.lab.spec.Blend
import com.bytedance.physicslab.solar.lab.spec.Mat
import com.bytedance.physicslab.solar.lab.spec.Node
import com.bytedance.physicslab.solar.lab.spec.SoftRender
import com.bytedance.physicslab.solar.render.MeshData
import com.bytedance.physicslab.solar.render.MeshGen
import com.bytedance.physicslab.solar.render.ProcTex
import com.bytedance.physicslab.solar.render.Rgb
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

internal class EarthMoonView(private val tex: SolarTex) {
    companion object {
        /** Earth's centre in volume space (free view). */
        val CENTER = P3(0.02f, 0.17f, -0.10f)
        const val R_E = 0.075f          // drawn Earth radius (m) = one Earth radius in every eclipse cross-section
        const val R_M = R_E * 0.2725f   // Moon: TRUE ratio to Earth
        const val D_MOON = 0.33f        // drawn Moon orbit radius (4.4 Earth radii; really 60)
        const val D_SUN = 0.80f         // drawn Sun distance (true direction)
        const val D_SUN_LUNAR = 0.49f   // eclipse lock: Sun pulled in so the zoomed view still shows it
        const val D_SUN_SOLAR = 0.68f   // (solar: the Moon sits between, keep the Sun clear of it)
        const val R_SUN = 0.12f
        const val R_RING = 0.44f        // the 8-phase markers
        const val TILT_DEG = 22f        // ecliptic tipped toward the viewer
        const val AZ_FREE = 42f         // Sun front-left: Earth's day side and the Moon's lit face both face the viewer
        const val LOCK_SCALE = 1.35f
        /** where the Earth-Moon midpoint goes in the eclipse lock (volume space) */
        val LOCK_MID = P3(0.07f, 0.13f, -0.06f)
        val DISC_POS = P3(0.70f, 0.24f, 0.08f)
        const val DISC_R = 0.088f
        const val CONE_LEN = 1.45f      // Earth's shadow drawn to 1.45 x the Moon's distance
        /** the viewer, for silhouette lines (volume space) */
        val EYE = P3(0f, 0.10f, 2.5f)
        private const val RAYS = 8
        private const val PHOTONS = 16
        private const val LENS_N = 48
        val COPPER = Rgb(1.0f, 0.50f, 0.27f)
    }

    // ---- state ----
    var az = AZ_FREE; private set
    private var azShown = AZ_FREE
    var lock: Eclipse.Event? = null; private set
    private var lockW = 0f
    private var clock = 0.0
    private var builtEvent: Eclipse.Event? = null

    // ---- live geometry (read by the readout / labels) ----
    var elong = 0.0; private set
    var lit = 0.0; private set
    var ageDays = 0.0; private set
    var moonLatDeg = 0.0; private set
    var nodeSunDeg = 90.0; private set        // angle between the line of nodes and the Sun line, 0..90
    var lunarGeo: Eclipse.LunarGeo? = null; private set
    var solarGeo: Eclipse.SolarGeo? = null; private set
    var local: Eclipse.Local? = null; private set
    var distKm = 384400.0; private set
    private var ageJd = Double.NaN
    private var spotPoint = floatArrayOf(-R_E, 0f, 0f)

    // ---- nodes ----
    lateinit var root: Node; private set
    lateinit var disc: Node; private set
    private lateinit var earth: Node
    private lateinit var earthTilt: Node
    private lateinit var earthBody: Node
    private lateinit var earthShell: Node
    private lateinit var sun: Node
    private lateinit var sunSpin: Node
    private lateinit var sunB: Node
    private lateinit var sunBMat: Mat.Unlit
    private lateinit var moon: Node
    private lateinit var moonBody: Node
    private lateinit var moonShell: Node
    private lateinit var moonDrop: Node
    private lateinit var orbit: Node
    private lateinit var orbitN: Node
    private lateinit var orbitS: Node
    private lateinit var nodeLine: Node
    private lateinit var nodeAsc: Node
    private lateinit var nodeDesc: Node
    private lateinit var ecliptic: Node
    private lateinit var eclipticRim: Node
    private val rays = ArrayList<Node>()
    private val rayY = FloatArray(RAYS); private val rayZ = FloatArray(RAYS); private val rayHit = BooleanArray(RAYS)
    private val rayX0 = FloatArray(RAYS); private val rayX1 = FloatArray(RAYS)
    private val photons = ArrayList<Node>()
    private val marks = ArrayList<Node>()
    private val markBodies = ArrayList<Node>()
    private lateinit var markHi: Node
    private var markShown = -1
    // eclipse geometry
    private lateinit var umbra: Node; private lateinit var umbraMesh: MeshData
    private lateinit var penumbra: Node; private lateinit var penMesh: MeshData
    private lateinit var umbraRing: Node; private lateinit var umbraRingMesh: MeshData
    private lateinit var penRing: Node; private lateinit var penRingMesh: MeshData
    private lateinit var mShadow: Node
    private lateinit var mUmbraMesh: MeshData
    private lateinit var mPenMesh: MeshData
    private lateinit var spot: Node; private lateinit var spotMesh: MeshData
    private lateinit var spotUmbra: Node
    private lateinit var track: Node; private lateinit var trackMesh: MeshData
    /** silhouette lines of the cones (u+, u-, p+, p-), set per frame from the view direction */
    private val edges = ArrayList<Node>()
    private var coneU = floatArrayOf(R_E, R_E * 0.6f, 0.4f)     // r0, r1, length of the visible umbra
    private var coneP = floatArrayOf(R_E, R_E * 1.4f, 0.4f)
    private var coneX0 = 0f                                       // cone start along x (Earth = 0; solar = Moon)
    private var coneY = 0f; private var coneZ = 0f
    // the "as seen from Earth" disc
    private lateinit var discBack: Node
    private lateinit var discMoon: Node
    private lateinit var discMoonBody: Node
    private lateinit var discShell: Node
    private lateinit var discUmbra: Node; private lateinit var discUmbraMesh: MeshData
    private lateinit var discPen: Node; private lateinit var discPenMesh: MeshData
    private lateinit var discSun: Node
    private lateinit var discCorona2: Node
    private lateinit var discMoonDark: Node
    private lateinit var darkMesh: MeshData
    private var darkX = 9f; private var darkY = 9f
    private var moonTintShown = -1
    private var backShown = -1
    private var coronaShown = -1
    private var coneShown = -1
    private var nodeHot = -1
    private var lensX = 9f; private var lensY = 9f

    // ================================================================ build

    fun build(parent: Node): Node {
        az = AZ_FREE; azShown = AZ_FREE; lock = null; lockW = 0f; clock = 0.0; builtEvent = null; ageJd = Double.NaN
        markShown = -1; moonTintShown = -1; backShown = -1; coronaShown = -1; coneShown = -1; nodeHot = -1; lensX = 9f; lensY = 9f; darkX = 9f; darkY = 9f
        rays.clear(); photons.clear(); marks.clear(); markBodies.clear(); edges.clear()
        val sphere = MeshGen.sphere(1f, 48, 96, MeshGen.SphereUv.EQUIRECT)
        val small = MeshGen.sphere(1f, 20, 40, MeshGen.SphereUv.EQUIRECT)
        val shellMesh = MeshGen.sphere(1f, 48, 48, MeshGen.SphereUv.POLAR_U)
        val shellMat = Mat.Unlit(tex = tex.light, blend = Blend.TRANSPARENT)
        val iconShell = Mat.Unlit(tex = tex.lightIcon, blend = Blend.TRANSPARENT)
        val cyl = MeshGen.cylinder(1f, 1f, 10)
        val alongX = Q.axisAngle(0f, 0f, 1f, (-PI / 2).toFloat())         // +Y -> +X

        root = parent.add(Node("EM_Root")).also { it.pos = CENTER }
        // ---- Earth ----
        earth = root.add(Node("EM_Earth"))
        earth.tapRadius = R_E * 1.25f
        earthTilt = earth.add(Node("EM_EarthTilt"))
        earthBody = earthTilt.add("EM_EarthBody", sphere, Mat.Unlit(tex = tex.planets[2])).also { it.uniformScale(R_E) }
        earthShell = earth.add("EM_EarthShell", shellMesh, shellMat).also { it.uniformScale(R_E * 1.006f) }
        // eclipse spot (solar): penumbra cap + exaggerated umbra dot, on the sunward hemisphere
        spotMesh = SolarArt.cap(0.5f, R_E * 1.012f, 10, 48)
        spot = earth.add("EM_Spot", spotMesh, Mat.Unlit(tex = tex.spot, blend = Blend.TRANSPARENT)).also { it.visible = false }
        spotUmbra = earth.add("EM_SpotUmbra", SolarArt.cap(0.11f, R_E * 1.016f, 3, 24), Mat.Unlit(Rgb(0f, 0f, 0f), alpha = 0.92f, blend = Blend.TRANSPARENT)).also { it.visible = false }

        // ---- Sun (direction true, distance compressed) ----
        sun = root.add(Node("EM_Sun")).also { it.pos = P3(-D_SUN, 0f, 0f); it.tapRadius = R_SUN * 1.15f }
        sunSpin = sun.add(Node("EM_SunSpin"))
        sunSpin.add("EM_SunA", sphere, Mat.Unlit(tex = tex.sunA)).uniformScale(R_SUN)
        sunBMat = Mat.Unlit(tex = tex.sunB, alpha = 0f, blend = Blend.TRANSPARENT)
        sunB = sunSpin.add("EM_SunB", sphere, sunBMat).also { it.uniformScale(R_SUN * 1.0015f) }
        val sv = sun.add(Node("EM_SunView")).also { it.billboard = true }
        sv.add("EM_SunLimb", MeshGen.sphere(1f, 48, 48, MeshGen.SphereUv.POLAR_U), Mat.Unlit(tex = tex.limb, blend = Blend.TRANSPARENT)).also {
            it.rot = Q.axisAngle(1f, 0f, 0f, (PI / 2).toFloat()); it.uniformScale(R_SUN * 1.003f)
        }
        sv.add("EM_SunCorona", SolarArt.quad(2 * 2.3f), Mat.Unlit(tex = tex.corona, blend = Blend.ADD)).uniformScale(R_SUN)

        // ---- parallel sunlight: rays that land on the day side, rays that pass by (into the night) ----
        val rayMat = Mat.Unlit(Rgb(1f, 0.86f, 0.55f), alpha = 0.42f, tex = tex.ray, blend = Blend.ADD)
        for (k in 0 until RAYS) {
            val hit = k % 2 == 0
            val a = (k / RAYS.toDouble() * 2 * PI + 0.3).toFloat()
            val rr = if (hit) R_E * 0.55f else R_E * 1.45f
            rayY[k] = rr * sin(a); rayZ[k] = rr * cos(a); rayHit[k] = hit
            rays += root.add("EM_Ray_$k", cyl, rayMat).also { it.rot = alongX }
        }
        val glowQ = SolarArt.quad(1f)
        val photonMat = Mat.Unlit(Rgb(1f, 0.92f, 0.70f), alpha = 0.9f, tex = tex.glow, blend = Blend.ADD)
        for (k in 0 until PHOTONS) photons += root.add("EM_Photon_$k", glowQ, photonMat).also { it.billboard = true; it.uniformScale(0.018f) }

        // ---- ecliptic plane + the Moon's tilted orbit, line of nodes ----
        ecliptic = root.add("EM_Ecliptic", MeshGen.annulus(R_E * 0.5f, D_MOON * 1.2f, 96), Mat.Unlit(Rgb(0.55f, 0.68f, 0.95f), alpha = 0.045f, blend = Blend.TRANSPARENT))
        eclipticRim = root.add("EM_EclipticRim", MeshGen.tube(circleXZ(D_MOON * 1.2f, 96), 0.0012f, 4, true), Mat.Unlit(Rgb(0.55f, 0.68f, 0.95f), alpha = 0.30f, blend = Blend.TRANSPARENT))
        orbit = root.add(Node("EM_Orbit")).also { it.uniformScale(D_MOON) }
        fun half(north: Boolean): FloatArray {
            val n = 48; val out = FloatArray((n + 1) * 3)
            for (k in 0..n) { val u = PI * k / n + if (north) 0.0 else PI; out[k * 3] = cos(u).toFloat(); out[k * 3 + 2] = -sin(u).toFloat() }
            return out
        }
        orbitN = orbit.add("EM_OrbitN", MeshGen.tube(half(true), 0.0050f, 6, false), Mat.Unlit(Rgb(0.85f, 0.90f, 1f), alpha = 0.90f, blend = Blend.TRANSPARENT))
        orbitS = orbit.add("EM_OrbitS", MeshGen.tube(half(false), 0.0040f, 6, false), Mat.Unlit(Rgb(0.55f, 0.62f, 0.80f), alpha = 0.40f, blend = Blend.TRANSPARENT))
        nodeLine = orbit.add("EM_NodeLine", cyl, nodeMat(false)).also { it.rot = alongX; it.scale = P3(0.0050f, 2.3f, 0.0050f) }
        val dia = MeshGen.sphere(1f, 8, 16, MeshGen.SphereUv.EQUIRECT)
        nodeAsc = orbit.add("EM_NodeAsc", dia, Mat.Unlit(Rgb(1f, 0.82f, 0.35f))).also { it.pos = P3(1f, 0f, 0f); it.uniformScale(0.024f) }
        nodeDesc = orbit.add("EM_NodeDesc", dia, Mat.Unlit(Rgb(0.55f, 0.70f, 1f))).also { it.pos = P3(-1f, 0f, 0f); it.uniformScale(0.024f) }
        moonDrop = root.add("EM_MoonDrop", cyl, Mat.Unlit(Rgb(0.80f, 0.86f, 1f), alpha = 0.7f, blend = Blend.TRANSPARENT))

        // ---- Moon ----
        moon = root.add(Node("EM_Moon")).also { it.tapRadius = 0.05f }
        moonBody = moon.add("EM_MoonBody", sphere, Mat.Unlit(tex = tex.moonHi)).also { it.uniformScale(R_M) }
        moonShell = moon.add("EM_MoonShell", shellMesh, shellMat).also { it.uniformScale(R_M * 1.006f) }

        // ---- the classic 8-phase ring (what each position looks like FROM EARTH), current one highlighted ----
        val markRim = SolarArt.ring(1.12f, 1.22f, 48)
        val markRimMat = Mat.Unlit(Rgb(0.55f, 0.65f, 0.85f), alpha = 0.55f, blend = Blend.ADD)
        for (k in 0 until 8) {
            val e = Math.toRadians(45.0 * k)
            val m = root.add(Node("EM_Phase_$k")).also {
                it.pos = P3((-R_RING * cos(e)).toFloat(), 0f, (R_RING * sin(e)).toFloat()); it.billboard = true
                if (k == 0 || k == 4) it.tapRadius = 0.05f
            }
            val body = m.add("EM_PhaseMoon_$k", small, Mat.Unlit(tex = tex.moonHi)).also {
                it.uniformScale(0.030f); it.rot = Q.fromTo(-1f, 0f, 0f, 0f, 0f, 1f)
            }
            val i = if (k <= 4) 180.0 - 45.0 * k else 45.0 * k - 180.0
            val chi = if (k <= 4) 270.0 else 90.0
            m.add("EM_PhaseShell_$k", shellMesh, iconShell).also { it.uniformScale(0.0302f); it.rot = lightRot(i, chi) }
            m.add("EM_PhaseRim_$k", markRim, markRimMat).uniformScale(0.030f)
            marks += m; markBodies += body
        }
        markHi = root.add("EM_PhaseHi", SolarArt.ring(0.039f, 0.045f, 48), Mat.Unlit(Rgb(1f, 0.80f, 0.35f), alpha = 0.95f, blend = Blend.ADD)).also { it.billboard = true }

        // ---- eclipse geometry (shown only in the eclipse lock) ----
        umbraMesh = SolarArt.frustum(R_E, R_E * 0.6f, D_MOON * CONE_LEN, 40)
        penMesh = SolarArt.frustum(R_E, R_E * 1.4f, D_MOON * CONE_LEN, 40)
        penumbra = root.add("EM_Penumbra", penMesh, penMat(1f)).also { it.rot = alongX; it.visible = false }
        umbra = root.add("EM_Umbra", umbraMesh, umbraMat(1f)).also { it.rot = alongX; it.visible = false }
        umbraRingMesh = MeshGen.tube(circleYZ(1f, 64), 0.002f, 5, true)
        penRingMesh = MeshGen.tube(circleYZ(1f, 64), 0.002f, 5, true)
        umbraRing = root.add("EM_UmbraRing", umbraRingMesh, Mat.Unlit(Rgb(1f, 0.40f, 0.22f), alpha = 0.95f, blend = Blend.ADD)).also { it.visible = false }
        penRing = root.add("EM_PenRing", penRingMesh, Mat.Unlit(Rgb(0.70f, 0.76f, 0.92f), alpha = 0.75f, blend = Blend.ADD)).also { it.visible = false }
        mShadow = root.add(Node("EM_MoonShadow")).also { it.rot = alongX; it.visible = false }
        mPenMesh = SolarArt.frustum(R_M, R_E * 0.55f, D_MOON, 40)
        mUmbraMesh = SolarArt.frustum(R_M, 0.0005f, D_MOON, 40)
        mShadow.add("EM_MPen", mPenMesh, penMat(1f))
        mShadow.add("EM_MUmbra", mUmbraMesh, Mat.Unlit(Rgb(0.02f, 0.02f, 0.03f), alpha = 0.72f, blend = Blend.TRANSPARENT))
        for (k in 0 until 4) edges += root.add("EM_ConeEdge_$k", cyl, Mat.Unlit(if (k < 2) Rgb(1f, 0.42f, 0.25f) else Rgb(0.70f, 0.76f, 0.92f),
            alpha = if (k < 2) 0.85f else 0.55f, blend = Blend.ADD)).also { it.visible = false }
        trackMesh = MeshGen.tube(FloatArray(37 * 3) { it * 1e-4f }, 0.0018f, 5, false)
        track = root.add("EM_Track", trackMesh, Mat.Unlit(Rgb(0.95f, 0.95f, 1f), alpha = 0.50f, blend = Blend.ADD)).also { it.visible = false }

        // ---- the Moon as seen from Earth (north up, east left), in volume space beside the rig ----
        disc = parent.add(Node("EM_Disc")).also { it.pos = DISC_POS; it.billboard = true }
        discBack = disc.add("EM_DiscBack", SolarArt.disc(DISC_R * 1.32f, 64), backMat(0f)).also { it.pos = P3(0f, 0f, -0.03f) }
        disc.add("EM_DiscRim", SolarArt.ring(DISC_R * 1.32f, DISC_R * 1.38f, 64), Mat.Unlit(Rgb(0.55f, 0.72f, 1f), alpha = 0.55f, blend = Blend.ADD)).also { it.pos = P3(0f, 0f, -0.03f) }
        discMoon = disc.add(Node("EM_DiscMoon"))
        discMoonBody = discMoon.add("EM_DiscMoonBody", sphere, Mat.Unlit(tex = tex.moonHi)).also { it.uniformScale(DISC_R); it.rot = Q.fromTo(-1f, 0f, 0f, 0f, 0f, 1f) }
        discShell = discMoon.add("EM_DiscShell", shellMesh, Mat.Unlit(tex = tex.lightDisc, blend = Blend.TRANSPARENT)).also { it.uniformScale(DISC_R * 1.006f) }
        discPenMesh = SolarArt.lens(LENS_N); discUmbraMesh = SolarArt.lens(LENS_N)
        discPen = discMoon.add("EM_DiscPen", discPenMesh, Mat.Unlit(Rgb(0f, 0f, 0f), alpha = 0.28f, blend = Blend.TRANSPARENT)).also { it.visible = false }
        discUmbra = discMoon.add("EM_DiscUmbra", discUmbraMesh, Mat.Unlit(Rgb(0.45f, 0.10f, 0.04f), alpha = 0.38f, blend = Blend.TRANSPARENT)).also { it.visible = false }
        discSun = disc.add(Node("EM_DiscSun")).also { it.visible = false }
        discSun.add("EM_DiscSunA", sphere, Mat.Unlit(tex = tex.sunA)).uniformScale(DISC_R)
        // the inner corona: pearly glow hugging the black Moon, only in totality
        discCorona2 = discSun.add("EM_DiscCorona2", SolarArt.quad(2f), coronaRingMat(1f)).also { it.uniformScale(DISC_R * 3.2f); it.pos = P3(0f, 0f, DISC_R * 1.05f); it.visible = false }
        // the Moon's silhouette, clipped to the sky disc (outside it there is the room, not sky)
        darkMesh = SolarArt.lens(LENS_N)
        discMoonDark = discSun.add("EM_DiscMoonDark", darkMesh, Mat.Unlit(Rgb(0.015f, 0.015f, 0.02f)))
        return root
    }

    private fun nodeMat(hot: Boolean) = if (hot) Mat.Unlit(Rgb(1f, 0.78f, 0.30f), alpha = 0.95f, blend = Blend.ADD)
                                        else Mat.Unlit(Rgb(0.62f, 0.72f, 1f), alpha = 0.75f, blend = Blend.ADD)
    private fun coronaRingMat(a: Float) = Mat.Unlit(Rgb(a, a, a), tex = tex.coronaRing, blend = Blend.ADD)
    private fun umbraMat(a: Float) = Mat.Unlit(Rgb(0.62f, 0.10f, 0.06f), alpha = 0.16f * a, blend = Blend.TRANSPARENT)
    private fun penMat(a: Float) = Mat.Unlit(Rgb(0.55f, 0.60f, 0.75f), alpha = 0.06f * a, blend = Blend.TRANSPARENT)

    /** Sky behind the disc: night blue, or daylight blue that darkens with the eclipse. */
    private fun backMat(day: Float) = Mat.Unlit(ProcTex.okLerp(Rgb(0.025f, 0.035f, 0.08f), Rgb(0.26f, 0.45f, 0.78f), day))

    private fun circleYZ(r: Float, n: Int) = FloatArray(n * 3) { k ->
        val a = 2 * PI * (k / 3) / n
        when (k % 3) { 0 -> 0f; 1 -> (r * cos(a)).toFloat(); else -> (r * sin(a)).toFloat() }
    }
    private fun circleXZ(r: Float, n: Int) = FloatArray(n * 3) { k ->
        val a = 2 * PI * (k / 3) / n
        when (k % 3) { 0 -> (r * cos(a)).toFloat(); 1 -> 0f; else -> (r * sin(a)).toFloat() }
    }

    /**
     * Light-shell rotation for a disc seen face-on (+Z toward the viewer, +Y = celestial north, -X = east):
     * the Sun seen from the Moon lies at phase angle [iDeg] from the viewer, toward position angle [chiDeg].
     */
    private fun lightRot(iDeg: Double, chiDeg: Double): Q {
        val i = Math.toRadians(iDeg); val c = Math.toRadians(chiDeg)
        return Q.fromTo(0f, 1f, 0f, (-sin(c) * sin(i)).toFloat(), (cos(c) * sin(i)).toFloat(), cos(i).toFloat())
    }

    // ================================================================ control

    fun setFree() { lock = null }

    /** Lock onto a real eclipse: the caller moves the clock to [e].jd. */
    fun setLock(e: Eclipse.Event) { lock = e }

    /** Turn the view about the ecliptic pole (pinch-drag on Earth, or a tap = 90 deg). */
    fun turn(deg: Float) { az += deg }

    /** Best view azimuth per preset (Sun front-left; day side / copper face toward the viewer). */
    private fun azTarget() = az + when { lock == null -> 0f; lock!!.solar -> -2f; else -> -14f }

    // ================================================================ per frame

    fun update(dt: Float, jd: Double, edit: (MeshData) -> Unit) {
        clock += dt
        val m = Sky.moonGeoKm(jd); val s = Sky.sunGeoKm(jd, m)
        val lamS = Sky.lonDeg(s)
        distKm = m.length
        elong = run { var e = (Sky.lonDeg(m) - lamS) % 360.0; if (e < 0) e += 360.0; e }
        val iDeg = Sky.angleDeg(Vec3d(s.x - m.x, s.y - m.y, s.z - m.z), Vec3d(-m.x, -m.y, -m.z))
        lit = 0.5 * (1 + cos(Math.toRadians(iDeg)))
        if (ageJd.isNaN() || abs(jd - ageJd) > 0.01) { ageDays = Sky.moonAgeDays(jd); ageJd = jd }
        moonLatDeg = Sky.latDeg(m)
        val lg = Eclipse.lunar(jd); val sg = Eclipse.solar(jd)
        lunarGeo = lg; solarGeo = sg
        val ev = lock

        // ---- view: tilt + azimuth (smoothed), lock blend: zoom in on the alignment ----
        val target = azTarget()
        azShown = if (dt == 0f) target else azShown + (target - azShown) * min(1f, dt * 5f)
        val rot = Q.axisAngle(1f, 0f, 0f, Math.toRadians(TILT_DEG.toDouble()).toFloat()) * Q.axisAngle(0f, 1f, 0f, Math.toRadians(azShown.toDouble()).toFloat())
        root.rot = rot
        val lw = if (ev != null) 1f else 0f
        lockW = if (dt == 0f) lw else lockW + (lw - lockW) * min(1f, dt * 4f)
        if (abs(lockW - lw) < 0.002f) lockW = lw
        val w = ProcTex.smooth01(lockW)
        val sc = 1f + (LOCK_SCALE - 1f) * w
        root.uniformScale(sc)
        val midLocal = if (ev?.solar == true) -D_MOON / 2 else D_MOON / 2
        val mr = rot.rotate(floatArrayOf(midLocal * LOCK_SCALE, 0f, 0f))
        val lockPos = P3(LOCK_MID.x - mr[0], LOCK_MID.y - mr[1], LOCK_MID.z - mr[2])
        root.pos = SolarArt.lerp(CENTER, lockPos, w)
        val sunD = D_SUN + ((if (ev?.solar == true) D_SUN_SOLAR else D_SUN_LUNAR) - D_SUN) * w
        sun.pos = P3(-sunD, 0f, 0f)

        // ---- Earth: axis (true, rotates once a year in this frame), spin, day side toward -X ----
        val pole = SolarArt.sunFixed(Vec3d(0.0, sin(Math.toRadians(Sky.OBLIQUITY_J2000)), cos(Math.toRadians(Sky.OBLIQUITY_J2000))), lamS)
        earthTilt.rot = Q.fromTo(0f, 1f, 0f, pole[0], pole[1], pole[2])
        earthBody.rot = Q.axisAngle(0f, 1f, 0f, Math.toRadians(clock * 20.0).toFloat())
        earthShell.rot = Q.fromTo(0f, 1f, 0f, -1f, 0f, 0f)

        // ---- Sun: spin + boil; rays from its limb (transform only) ----
        sunSpin.rot = Q.axisAngle(0f, 1f, 0f, Math.toRadians(clock * 2.44).toFloat())
        sunB.setMat(sunBMat.copy(alpha = ((sin(clock * 0.045 * 2 * PI) * 0.5 + 0.5) * 20).toInt() / 20f))
        for (k in 0 until RAYS) {
            val x0 = -sunD + R_SUN * 0.8f
            // passing rays run on into the night side; in the solar lock they stop just past the Earth (volume depth)
            val x1 = if (rayHit[k]) -sqrt(max(0f, R_E * R_E - rayY[k] * rayY[k] - rayZ[k] * rayZ[k]))
                     else D_MOON * (1.55f - (if (ev?.solar == true) 0.85f else 0f) * w)
            rayX0[k] = x0; rayX1[k] = x1
            rays[k].pos = P3((x0 + x1) / 2, rayY[k], rayZ[k]); rays[k].scale = P3(0.0015f, x1 - x0, 0.0015f)
        }
        for (k in 0 until PHOTONS) {
            val r = k % RAYS
            val f = ((clock * 0.35 + k * 0.618) % 1.0).toFloat()
            photons[k].pos = P3(rayX0[r] + (rayX1[r] - rayX0[r]) * f, rayY[r], rayZ[r])
            photons[k].uniformScale(0.016f * (0.4f + 0.6f * sin(PI.toFloat() * f)))
        }

        // ---- the Moon's orbit plane and line of nodes (mean elements, true tilt 5.145 deg) ----
        val t = (jd - Solar.J2000) / 36525.0
        val nodeDeg = 125.0445479 - 1934.1362891 * t + 0.0020754 * t * t - Lunar.precessionDeg(jd)
        val nd = Math.toRadians(nodeDeg); val inc = Math.toRadians(5.145)
        val nAsc = SolarArt.sunFixed(Vec3d(cos(nd), sin(nd), 0.0), lamS)
        val nPole = SolarArt.sunFixed(Vec3d(sin(inc) * sin(nd), -sin(inc) * cos(nd), cos(inc)), lamS)
        orbit.rot = SolarArt.basis(nAsc, nPole)
        nodeSunDeg = Math.toDegrees(kotlin.math.acos(abs(nAsc[0]).toDouble().coerceIn(0.0, 1.0)))
        val hot = if (nodeSunDeg < 17.0) 1 else 0
        if (hot != nodeHot) { nodeHot = hot; nodeLine.setMat(nodeMat(hot == 1)) }

        // ---- Moon: free = true direction on the drawn orbit; lock = true cross-section in Earth radii ----
        val md = SolarArt.sunFixed(m, lamS, 1.0 / m.length)
        val free = P3(md[0] * D_MOON, md[1] * D_MOON, md[2] * D_MOON)
        var lockP = free
        if (ev != null) {
            lockP = if (!ev.solar) {
                val o = SolarArt.sunFixed(lg.offsetRe, lamS, R_E.toDouble()); P3(D_MOON, o[1], o[2])
            } else {
                val o = SolarArt.sunFixed(sg.axisRe, lamS, R_E.toDouble()); P3(-D_MOON, o[1], o[2])
            }
        }
        val mp = SolarArt.lerp(free, lockP, w)
        moon.pos = mp
        moonBody.rot = Q.fromTo(-1f, 0f, 0f, -mp.x, -mp.y, -mp.z)          // near side faces Earth
        moonShell.rot = Q.fromTo(0f, 1f, 0f, -1f, 0f, 0f)
        // drop line: Moon -> ecliptic plane (its height is the Moon's ecliptic latitude)
        val h = mp.y
        moonDrop.visible = abs(h) > 0.002f && lockW < 0.5f
        moonDrop.pos = P3(mp.x, h / 2, mp.z); moonDrop.scale = P3(0.0014f, max(abs(h), 1e-4f), 0.0014f)
        orbitN.visible = lockW < 0.5f; orbitS.visible = lockW < 0.5f
        ecliptic.visible = lockW < 0.5f; eclipticRim.visible = lockW < 0.5f

        // copper tint when the Moon is in the umbra (the true fraction of its disc)
        val f = (lg.umbraFraction * 20).toInt()
        if (f != moonTintShown) {
            moonTintShown = f
            val k = f / 20f
            val c = Rgb(ProcTex.lerp(1f, COPPER.r, k), ProcTex.lerp(1f, COPPER.g, k), ProcTex.lerp(1f, COPPER.b, k))
            moonBody.setMat(Mat.Unlit(c, tex = tex.moonHi))
            val kd = ProcTex.smooth01((k - 0.85f) / 0.15f)
            discMoonBody.setMat(Mat.Unlit(Rgb(ProcTex.lerp(1f, COPPER.r, kd), ProcTex.lerp(1f, COPPER.g, kd), ProcTex.lerp(1f, COPPER.b, kd)), tex = tex.moonHi))
            discUmbra.setMat(Mat.Unlit(Rgb(0.45f, 0.10f, 0.04f), alpha = ProcTex.lerp(0.62f, 0.38f, kd), blend = Blend.TRANSPARENT))
        }

        // ---- phase ring: highlight the current octant ----
        val bin = Sky.phaseIndex(elong)
        if (bin != markShown) {
            markShown = bin
            for (k in 0 until 8) markBodies[k].setMat(Mat.Unlit(if (k == bin) Rgb(1f, 1f, 1f) else Rgb(0.80f, 0.80f, 0.84f), tex = tex.moonHi))
            markHi.pos = marks[bin].pos
        }
        for (k in 0 until 8) marks[k].visible = lockW < 0.5f
        markHi.visible = lockW < 0.5f

        // ---- eclipse geometry ----
        if (ev != null && ev !== builtEvent) { buildEclipse(ev, lg, sg, edit); builtEvent = ev }
        val lunarOn = ev != null && !ev.solar && lockW > 0.05f
        val solarOn = ev != null && ev.solar && lockW > 0.05f
        umbra.visible = lunarOn; penumbra.visible = lunarOn; umbraRing.visible = lunarOn; penRing.visible = lunarOn
        mShadow.visible = solarOn
        track.visible = ev != null && lockW > 0.5f
        val cone = ((if (ev != null) lockW else 0f) * 10).toInt()
        if (cone != coneShown) { coneShown = cone; val a = cone / 10f; umbra.setMat(umbraMat(a)); penumbra.setMat(penMat(a)) }
        if (solarOn) {
            mShadow.pos = mp
            coneX0 = mp.x; coneY = mp.y; coneZ = mp.z
            // the spot: where the Moon's shadow axis meets the sunward hemisphere
            val o = SolarArt.sunFixed(sg.axisRe, lamS)
            val g = sqrt(o[1] * o[1] + o[2] * o[2])
            val reach = sg.moonBetween && g < 1f + sg.penumbraRe.toFloat()
            spot.visible = reach; spotUmbra.visible = reach && g < 1f && sg.umbraRe > -0.02
            if (reach) {
                val dir = if (g < 1f) floatArrayOf(-sqrt(1f - g * g), o[1], o[2]) else floatArrayOf(0f, o[1] / g, o[2] / g)
                spotPoint = floatArrayOf(dir[0] * R_E, dir[1] * R_E, dir[2] * R_E)
                val q = Q.fromTo(0f, 1f, 0f, dir[0], dir[1], dir[2])
                spot.rot = q; spotUmbra.rot = q
            }
        } else { spot.visible = false; spotUmbra.visible = false; coneX0 = 0f; coneY = 0f; coneZ = 0f }
        updateEdges(lunarOn || solarOn, rot, sc)

        updateDisc(jd, iDeg, lg, ev, edit)
    }

    /** Silhouette lines of the shadow cones, facing the viewer (transform only). */
    private fun updateEdges(on: Boolean, rot: Q, sc: Float) {
        for (e in edges) e.visible = on && lockW > 0.5f
        if (!on) return
        // view direction in rig-local coordinates
        val c = SoftRender.worldOf(root).apply(coneX0, coneY, coneZ)
        val v = rot.conj().rotate(floatArrayOf(EYE.x - c[0], EYE.y - c[1], EYE.z - c[2]))
        // the silhouette sits where the surface normal is perpendicular to the view: p = x × v (normalised)
        val p = SolarArt.norm(floatArrayOf(0f, -v[2], v[1]))
        for (k in 0 until 4) {
            val cone = if (k < 2) coneU else coneP
            val sgn = if (k % 2 == 0) 1f else -1f
            val a = floatArrayOf(coneX0, coneY + sgn * p[1] * cone[0], coneZ + sgn * p[2] * cone[0])
            val b = floatArrayOf(coneX0 + cone[2], coneY + sgn * p[1] * cone[1], coneZ + sgn * p[2] * cone[1])
            val d = floatArrayOf(b[0] - a[0], b[1] - a[1], b[2] - a[2]); val l = SolarArt.len3(d)
            edges[k].pos = P3((a[0] + b[0]) / 2, (a[1] + b[1]) / 2, (a[2] + b[2]) / 2)
            edges[k].rot = Q.fromTo(0f, 1f, 0f, d[0], d[1], d[2])
            edges[k].scale = P3(0.0013f / sc, l, 0.0013f / sc)
        }
    }

    /** Rebuild the per-eclipse geometry (cones sized to THIS eclipse's true cross-section, the Moon's track). */
    private fun buildEclipse(ev: Eclipse.Event, lg: Eclipse.LunarGeo, sg: Eclipse.SolarGeo, ed: (MeshData) -> Unit) {
        val len = D_MOON * CONE_LEN
        if (!ev.solar) {
            val u = lg.umbraRe.toFloat(); val p = lg.penumbraRe.toFloat()
            coneU = floatArrayOf(R_E, R_E * (1f - (1f - u) * CONE_LEN), len)
            coneP = floatArrayOf(R_E, R_E * (1f + (p - 1f) * CONE_LEN), len)
            SolarArt.frustumInto(umbraMesh, coneU[0], coneU[1], len, 40); ed(umbraMesh)
            SolarArt.frustumInto(penMesh, coneP[0], coneP[1], len, 40); ed(penMesh)
            MeshGen.tubeInto(umbraRingMesh, circleYZ(R_E * u, 64).also { shiftX(it, D_MOON) }, 0.0016f, 5, true); ed(umbraRingMesh)
            MeshGen.tubeInto(penRingMesh, circleYZ(R_E * p, 64).also { shiftX(it, D_MOON) }, 0.0013f, 5, true); ed(penRingMesh)
        } else {
            val gg = abs(sg.gamma).toFloat()
            val toSurf = if (gg < 1f) sqrt(1f - gg * gg) * R_E else 0f
            coneU = floatArrayOf(R_M, 0.0008f, D_MOON - toSurf)
            coneP = floatArrayOf(R_M, R_E * sg.penumbraRe.toFloat(), D_MOON)
            SolarArt.frustumInto(mPenMesh, coneP[0], coneP[1], coneP[2], 40); ed(mPenMesh)
            SolarArt.frustumInto(mUmbraMesh, coneU[0], coneU[1], coneU[2], 40); ed(mUmbraMesh)
            SolarArt.capInto(spotMesh, asin(min(0.98, sg.penumbraRe)).toFloat(), R_E * 1.012f, 10, 48); ed(spotMesh)
        }
        // the Moon's path through the shadow, +-3 h, in the same cross-section scale
        val path = FloatArray(37 * 3)
        for (k in 0 until 37) {
            val tj = ev.jd + (k - 18) * (10.0 / 1440.0)
            val mm = Sky.moonGeoKm(tj); val ls = Sky.lonDeg(Sky.sunGeoKm(tj, mm))
            val o = if (!ev.solar) SolarArt.sunFixed(Eclipse.lunar(tj).offsetRe, ls, R_E.toDouble()) else SolarArt.sunFixed(Eclipse.solar(tj).axisRe, ls, R_E.toDouble())
            path[k * 3] = if (ev.solar) -D_MOON else D_MOON; path[k * 3 + 1] = o[1]; path[k * 3 + 2] = o[2]
        }
        MeshGen.tubeInto(trackMesh, path, 0.0016f, 5, false); ed(trackMesh)
    }

    private fun shiftX(a: FloatArray, dx: Float) { for (k in 0 until a.size / 3) a[k * 3] += dx }

    private fun updateDisc(jd: Double, iDeg: Double, lg: Eclipse.LunarGeo, ev: Eclipse.Event?, ed: (MeshData) -> Unit) {
        val solarView = ev != null && ev.solar && lockW > 0.5f
        discMoon.visible = !solarView; discSun.visible = solarView
        if (!solarView) {
            discMoon.uniformScale((384400.0 / distKm).toFloat())                      // perigee "supermoon" is bigger
            discShell.rot = lightRot(iDeg, Sky.brightLimbDeg(jd))
            // Earth's shadow on the disc (true sky geometry: anti-solar point relative to the Moon)
            val inPen = lg.sepDeg < lg.penumbraDeg + lg.moonSdDeg
            discPen.visible = inPen; discUmbra.visible = inPen && lg.sepDeg < lg.umbraDeg + lg.moonSdDeg
            if (inPen) {
                val m = Sky.moonGeoKm(jd); val s = Sky.sunGeoKm(jd, m)
                val (am, dm) = Sky.raDec(Sky.eclToEq(m)); val (aa, da) = Sky.raDec(Sky.eclToEq(Vec3d(-s.x, -s.y, -s.z)))
                var dra = aa - am; if (dra > 180) dra -= 360.0; if (dra < -180) dra += 360.0
                val k = (DISC_R / lg.moonSdDeg).toFloat()
                val ex = (-dra * cos(Math.toRadians(dm)) * k).toFloat(); val ny = ((da - dm) * k).toFloat()
                // re-upload only when the shadow moved by > 0.3 mm on the disc (paused = no uploads at all)
                if (abs(ex - lensX) > 3e-4f || abs(ny - lensY) > 3e-4f) {
                    lensX = ex; lensY = ny
                    if (SolarArt.lensInto(discPenMesh, DISC_R, ex, ny, (lg.penumbraDeg * k).toFloat(), DISC_R * 1.012f)) ed(discPenMesh)
                    if (discUmbra.visible && SolarArt.lensInto(discUmbraMesh, DISC_R, ex, ny, (lg.umbraDeg * k).toFloat(), DISC_R * 1.02f)) ed(discUmbraMesh)
                }
            }
            if (backShown != 0) { backShown = 0; discBack.setMat(backMat(0f)) }
            local = null
        } else {
            val e = ev!!
            val l = Eclipse.localSolar(jd, e.latDeg, e.lonDeg)
            local = l
            val k = (DISC_R / l.sunSdDeg).toFloat()
            val over = l.sepDeg < l.sunSdDeg + l.moonSdDeg
            discMoonDark.visible = over
            val mx = (-l.eastDeg * k).toFloat(); val my = (l.northDeg * k).toFloat()
            if (over && (abs(mx - darkX) > 3e-4f || abs(my - darkY) > 3e-4f)) {
                darkX = mx; darkY = my
                // flat lens in front of the Sun: (sky disc) ∩ (Moon disc)
                if (SolarArt.lensInto(darkMesh, DISC_R * 1.32f, mx, my, (l.moonSdDeg * k).toFloat(), DISC_R * 1.1f)) ed(darkMesh)
            }
            val tot = ((l.obscuration - 0.985) / 0.015).coerceIn(0.0, 1.0)
            val c = (tot * 10).toInt()
            if (c != coronaShown) {
                coronaShown = c
                discCorona2.setMat(coronaRingMat(c / 10f)); discCorona2.visible = c > 0
            }
            val b = ((1.0 - l.obscuration).coerceIn(0.0, 1.0) * 0.95 * 20).toInt() + 1
            if (b != backShown) { backShown = b; discBack.setMat(backMat((b - 1) / 20f)) }
        }
    }

    // ================================================================ labels / anchors

    fun worldOf(n: Node): P3 { val a = SoftRender.worldOf(n).t; return P3(a[0], a[1], a[2]) }
    /** A rig-local point (Sun-fixed frame, Earth at the origin) in volume space. */
    fun rigToWorld(x: Float, y: Float, z: Float): P3 { val a = SoftRender.worldOf(root).apply(x, y, z); return P3(a[0], a[1], a[2]) }
    val sunNode get() = sun
    val moonNode get() = moon
    val earthNode get() = earth
    fun mark(k: Int) = marks[k]
    /** the node marker that appears higher (labels go there) */
    /** halfway out along the line of nodes (the upper half): clear of the orbit, the phase ring and its labels */
    val nodeLabelWorld: P3 get() { val a = worldOf(nodeAsc); val b = worldOf(nodeDesc); return SolarArt.lerp(worldOf(earth), if (a.y >= b.y) a else b, 0.55f) }
    /** the node marker on the Sun's side of the Earth */
    val nodeSunSideWorld: P3 get() {
        val a = worldOf(nodeAsc); val b = worldOf(nodeDesc); val sn = worldOf(sun)
        val da = (a.x - sn.x) * (a.x - sn.x) + (a.z - sn.z) * (a.z - sn.z); val db = (b.x - sn.x) * (b.x - sn.x) + (b.z - sn.z) * (b.z - sn.z)
        return if (da < db) a else b
    }
    /** the node marker on the far side from the Sun (solar lock: nothing else there) */
    val nodeFarSideWorld: P3 get() { val a = nodeSunSideWorld; val b1 = worldOf(nodeAsc); return if (a == b1) worldOf(nodeDesc) else b1 }
    val spotWorld: P3 get() = rigToWorld(spotPoint[0], spotPoint[1], spotPoint[2])
    val locked get() = lockW > 0.5f
}
