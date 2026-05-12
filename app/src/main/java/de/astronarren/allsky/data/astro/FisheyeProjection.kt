package de.astronarren.allsky.data.astro

import androidx.compose.ui.geometry.Offset
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Maps celestial horizontal coordinates ↔ pixel coordinates on the live
 * allsky fisheye frame, using a [FisheyeCalibration].
 *
 * Pixel-space convention: origin top-left, +x right, +y down — the standard
 * Android image-coordinate frame.
 *
 * Azimuth convention: degrees, from north clockwise (matches everything in
 * [AstroMath]). After applying [FisheyeCalibration.northOffsetDeg] the result
 * is the bearing on the image plane, where bearing 0° points toward image
 * −y (up) and bearing 90° points toward image +x (right). The y-down sign
 * flip is folded into the cos term of the projection equations below.
 *
 * No display-side scaling is applied here. Callers that draw onto a
 * Compose Canvas at a different size than the source image must compose
 * this with the appropriate `ContentScale` transform themselves — see
 * [transformImagePixelToDisplay] for the Crop case used in the live view.
 */
object FisheyeProjection {

    /**
     * Forward projection: `(alt, az) → image pixel`.
     *
     * Returns null when the body is below the horizon — the equidistant
     * projection isn't defined past the 90° zenith distance and the caller
     * almost always wants "nothing to draw" rather than a clamped point on
     * the horizon ring.
     */
    fun altAzToImagePixel(
        coords: HorizontalCoords,
        calibration: FisheyeCalibration,
        imageWidthPx: Int,
        imageHeightPx: Int,
    ): Offset? {
        if (coords.altitudeDeg < 0.0) return null
        val cx = imageWidthPx * calibration.cxFrac
        val cy = imageHeightPx * calibration.cyFrac
        val radius = min(imageWidthPx, imageHeightPx) * calibration.radiusFrac
        val r = radius * (90.0 - coords.altitudeDeg) / 90.0
        val bearingRad = Math.toRadians(coords.azimuthDeg + calibration.northOffsetDeg)
        val x = cx + r * sin(bearingRad)
        val y = cy - r * cos(bearingRad)
        return Offset(x.toFloat(), y.toFloat())
    }

    /**
     * Inverse projection: image pixel → `(alt, az)`. The user taps the sun
     * (or another body of known position) and we use this to derive what
     * alt/az the calibration *would currently* assign to that pixel —
     * comparing it against the body's true alt/az gives us the rotation
     * error the quick-calibration solves out.
     *
     * Returns null for taps outside the horizon ring.
     */
    fun imagePixelToAltAz(
        px: Double,
        py: Double,
        calibration: FisheyeCalibration,
        imageWidthPx: Int,
        imageHeightPx: Int,
    ): HorizontalCoords? {
        val cx = imageWidthPx * calibration.cxFrac
        val cy = imageHeightPx * calibration.cyFrac
        val radius = min(imageWidthPx, imageHeightPx) * calibration.radiusFrac
        val dx = px - cx
        val dy = py - cy
        val r = hypot(dx, dy)
        if (r > radius) return null
        val zenDist = 90.0 * (r / radius)
        val altitude = 90.0 - zenDist
        // atan2(dx, -dy) gives the bearing measured from "image up" clockwise,
        // matching the convention used in altAzToImagePixel above. Subtract
        // the rotation to recover the celestial azimuth.
        val bearingDeg = Math.toDegrees(atan2(dx, -dy))
        val azimuth = AstroMath.normalizeDegrees(bearingDeg - calibration.northOffsetDeg)
        return HorizontalCoords(altitudeDeg = altitude, azimuthDeg = azimuth)
    }

    /**
     * One-tap quick calibration. Assumes the standard inscribed circular
     * fisheye geometry (cxFrac = cyFrac = radiusFrac = 0.5) and solves only
     * the rotation angle from a single observed body.
     *
     * @param observedBody the celestial alt/az the body actually has *now*
     *   (from [AstroMath.sunEquatorial] + [AstroMath.equatorialToHorizontal],
     *   for example).
     * @param tappedPxFrac fractional X of the tap relative to image width,
     *   in [0, 1]. Caller is expected to have already mapped the screen tap
     *   into image-pixel coordinates.
     * @param tappedPyFrac fractional Y of the tap relative to image height.
     */
    fun quickCalibrate(
        observedBody: HorizontalCoords,
        tappedPxFrac: Double,
        tappedPyFrac: Double,
        now: Long = System.currentTimeMillis(),
    ): FisheyeCalibration {
        val base = FisheyeCalibration.DEFAULT_INSCRIBED
        // dx, dy in fractions of min(w,h). Using fractions (rather than
        // pixels) keeps the result independent of the unknown image size at
        // calibration time.
        val dxFrac = tappedPxFrac - base.cxFrac
        val dyFrac = tappedPyFrac - base.cyFrac
        val bearingDeg = Math.toDegrees(atan2(dxFrac, -dyFrac))
        val northOffset = AstroMath.signedDegrees(bearingDeg - observedBody.azimuthDeg)
        return base.copy(
            northOffsetDeg = northOffset,
            solvedAtEpochMs = now,
            rmsErrorDeg = null, // one observation gives no residual
        )
    }

    /**
     * Multi-tap precise calibration. Each observation is one
     * `(known body alt/az, tap pixel)` pair. Three or more observations
     * cover the four-parameter fit; the canonical "precise" UX uses
     * (sun, north horizon, east horizon).
     *
     * Solves with Levenberg–Marquardt (Gauss–Newton + diagonal damping).
     * Converges from the inscribed-circle seed in well under 20 iterations
     * for any reasonable tap set; we cap at 30 as a safety net.
     *
     * Returns null when the observations are degenerate (fewer than three
     * pairs, or the linear system is rank-deficient) so the caller can fall
     * back to the quick calibration rather than store nonsense.
     */
    fun preciseCalibrate(
        observations: List<Observation>,
        imageWidthPx: Int,
        imageHeightPx: Int,
        now: Long = System.currentTimeMillis(),
    ): FisheyeCalibration? {
        if (observations.size < 3) return null
        val minDim = min(imageWidthPx, imageHeightPx).toDouble()

        // Parameter vector in pixels for cx/cy/radius, degrees for theta —
        // putting them on comparable numeric scales improves LM conditioning.
        var cx = imageWidthPx * 0.5
        var cy = imageHeightPx * 0.5
        var radius = minDim * 0.5
        var theta = 0.0
        var lambda = 1e-3

        repeat(30) {
            // Build J^T J (4×4) and J^T r (4×1) one observation at a time.
            val jtj = Array(4) { DoubleArray(4) }
            val jtr = DoubleArray(4)
            var sse = 0.0
            for (obs in observations) {
                val zd = (90.0 - obs.coords.altitudeDeg) / 90.0
                val bearing = Math.toRadians(obs.coords.azimuthDeg + theta)
                val s = sin(bearing); val c = cos(bearing)
                val predX = cx + radius * zd * s
                val predY = cy - radius * zd * c
                val rx = obs.pxImage - predX
                val ry = obs.pyImage - predY
                sse += rx * rx + ry * ry

                // Partial derivatives — kept as plain locals for readability;
                // the optimiser inlines this anyway.
                val dXdTheta = radius * zd * c * Math.PI / 180.0
                val dYdTheta = radius * zd * s * Math.PI / 180.0
                // d(predX)/d(cx, cy, radius, theta)
                val jx = doubleArrayOf(1.0, 0.0, zd * s, dXdTheta)
                // d(predY)/d(cx, cy, radius, theta)
                val jy = doubleArrayOf(0.0, 1.0, -zd * c, dYdTheta)

                for (i in 0..3) {
                    jtr[i] += jx[i] * rx + jy[i] * ry
                    for (j in 0..3) {
                        jtj[i][j] += jx[i] * jx[j] + jy[i] * jy[j]
                    }
                }
            }
            // LM damping: add λ * diag(JᵀJ) to the diagonal.
            for (i in 0..3) jtj[i][i] *= (1.0 + lambda)

            val delta = solve4x4(jtj, jtr) ?: return null
            // Trial step
            val tcx = cx + delta[0]
            val tcy = cy + delta[1]
            val tradius = radius + delta[2]
            val ttheta = theta + delta[3]

            // Accept step if it reduces SSE; otherwise grow damping and retry
            // next iteration with the same parameters (standard LM update).
            val trialSse = sseAt(observations, tcx, tcy, tradius, ttheta)
            if (trialSse < sse) {
                cx = tcx; cy = tcy; radius = tradius; theta = ttheta
                lambda = (lambda * 0.5).coerceAtLeast(1e-6)
                // Stop if we've converged — parameter step has fallen below
                // ~0.05 px and 0.01°.
                if (kotlin.math.abs(delta[0]) < 0.05 &&
                    kotlin.math.abs(delta[1]) < 0.05 &&
                    kotlin.math.abs(delta[2]) < 0.05 &&
                    kotlin.math.abs(delta[3]) < 0.01
                ) {
                    return@repeat
                }
            } else {
                lambda = (lambda * 4.0).coerceAtMost(1e6)
            }
        }

        // Final residual converted from pixels to degrees for the UI badge.
        val finalSse = sseAt(observations, cx, cy, radius, theta)
        val rmsPx = sqrt(finalSse / observations.size)
        val rmsDeg = rmsPx * 90.0 / radius

        // Sanity guard — pathological tap sets can push the fit outside the
        // image. Refuse and let the caller fall back to quick calibration.
        val cxF = cx / imageWidthPx
        val cyF = cy / imageHeightPx
        val rF = radius / minDim
        if (cxF !in 0.0..1.0 || cyF !in 0.0..1.0 || rF !in 0.1..1.5) return null

        return FisheyeCalibration(
            cxFrac = cxF,
            cyFrac = cyF,
            radiusFrac = rF,
            northOffsetDeg = AstroMath.signedDegrees(theta),
            solvedAtEpochMs = now,
            rmsErrorDeg = rmsDeg,
        )
    }

    /** One (known body, tapped pixel) pair fed to [preciseCalibrate]. */
    data class Observation(
        val coords: HorizontalCoords,
        val pxImage: Double,
        val pyImage: Double,
    )

    /**
     * Compose-Canvas helper: given the original image dimensions and the
     * display rect those pixels are being painted into (via
     * [androidx.compose.ui.layout.ContentScale.Crop] in the live view, or
     * `Fit` in the calibration screen), return the affine that takes an
     * image-pixel offset to a display-pixel offset.
     *
     * Encoded as `(scale, offsetX, offsetY)`. Apply as
     * `(scale·imgX + offsetX, scale·imgY + offsetY)`.
     */
    fun transformImagePixelToDisplay(
        imageWidthPx: Int,
        imageHeightPx: Int,
        displayWidthPx: Float,
        displayHeightPx: Float,
        fit: Boolean,
    ): Triple<Float, Float, Float> {
        if (imageWidthPx <= 0 || imageHeightPx <= 0) return Triple(1f, 0f, 0f)
        val sx = displayWidthPx / imageWidthPx
        val sy = displayHeightPx / imageHeightPx
        // Fit uses the smaller scale so the entire image is visible (letterbox);
        // Crop uses the larger so the image fills the display (overflow clipped).
        val scale = if (fit) minOf(sx, sy) else maxOf(sx, sy)
        val drawnW = imageWidthPx * scale
        val drawnH = imageHeightPx * scale
        val offsetX = (displayWidthPx - drawnW) * 0.5f
        val offsetY = (displayHeightPx - drawnH) * 0.5f
        return Triple(scale, offsetX, offsetY)
    }

    // ---- internals ----------------------------------------------------

    private fun sseAt(
        obs: List<Observation>,
        cx: Double, cy: Double, radius: Double, theta: Double,
    ): Double {
        var sse = 0.0
        for (o in obs) {
            val zd = (90.0 - o.coords.altitudeDeg) / 90.0
            val bearing = Math.toRadians(o.coords.azimuthDeg + theta)
            val predX = cx + radius * zd * sin(bearing)
            val predY = cy - radius * zd * cos(bearing)
            val rx = o.pxImage - predX
            val ry = o.pyImage - predY
            sse += rx * rx + ry * ry
        }
        return sse
    }

    /**
     * Solve A·x = b for a 4×4 symmetric positive-(semi)definite [a] via
     * partial-pivot Gaussian elimination. Returns null if the matrix is
     * singular — caller falls back to the quick calibration.
     *
     * Stays in-place / column-major-agnostic because [a] is square and
     * symmetric here. Hand-rolled rather than pulling in linear-algebra
     * dependencies for four equations.
     */
    private fun solve4x4(a: Array<DoubleArray>, b: DoubleArray): DoubleArray? {
        val n = 4
        val m = Array(n) { i -> DoubleArray(n + 1).also { row ->
            for (j in 0 until n) row[j] = a[i][j]
            row[n] = b[i]
        } }
        for (i in 0 until n) {
            // Pivot — largest absolute value in column i, rows i..n-1
            var pivot = i
            for (k in i + 1 until n) {
                if (kotlin.math.abs(m[k][i]) > kotlin.math.abs(m[pivot][i])) pivot = k
            }
            if (kotlin.math.abs(m[pivot][i]) < 1e-12) return null
            if (pivot != i) {
                val tmp = m[i]; m[i] = m[pivot]; m[pivot] = tmp
            }
            // Eliminate column i below the pivot row
            for (k in i + 1 until n) {
                val f = m[k][i] / m[i][i]
                for (j in i..n) m[k][j] -= f * m[i][j]
            }
        }
        // Back-substitute
        val x = DoubleArray(n)
        for (i in n - 1 downTo 0) {
            var s = m[i][n]
            for (j in i + 1 until n) s -= m[i][j] * x[j]
            x[i] = s / m[i][i]
        }
        return x
    }
}
