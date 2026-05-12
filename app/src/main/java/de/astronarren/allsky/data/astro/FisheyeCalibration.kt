package de.astronarren.allsky.data.astro

/**
 * Calibration parameters that map celestial alt/az onto pixels of the live
 * allsky fisheye frame.
 *
 * Stored as *fractions of image dimensions* rather than absolute pixels so a
 * single solve survives the camera changing resolution (Allsky installs
 * occasionally bump up/down depending on processing budget).
 *
 * Projection model: equidistant — radial distance from the zenith pixel is
 * linear in zenith angle. So a body at altitude `a` sits at radius
 * `radiusFrac · min(w, h) · (90 − a) / 90` from `(cxFrac · w, cyFrac · h)`,
 * measured along the bearing `azimuth + northOffsetDeg` (azimuth from north,
 * clockwise). This is what circular-fisheye allsky lenses produce — the
 * standard "stereographic vs equidistant" wobble is well below our other
 * error sources.
 *
 * Off-axis lenses (zenith not at image centre), non-180° fields of view, and
 * arbitrary camera rotations are all absorbed into the four parameters
 * `(cxFrac, cyFrac, radiusFrac, northOffsetDeg)`.
 */
data class FisheyeCalibration(
    /** Zenith pixel X as a fraction of image width, in [0, 1]. */
    val cxFrac: Double,
    /** Zenith pixel Y as a fraction of image height, in [0, 1]. */
    val cyFrac: Double,
    /**
     * Horizon radius as a fraction of `min(imageWidth, imageHeight)`. For an
     * inscribed circular fisheye that exactly fills the shorter image
     * dimension this is `0.5`.
     */
    val radiusFrac: Double,
    /**
     * Rotation, in degrees, applied to the azimuth before projection.
     * A value of zero means compass-north is at the top of the image
     * (image −y direction). Positive values rotate the sky clockwise as
     * the user looks at the image.
     */
    val northOffsetDeg: Double,
    /** Epoch millis when this calibration was solved. 0 ⇒ never solved. */
    val solvedAtEpochMs: Long = 0L,
    /**
     * RMS residual of the fit in degrees of arc, when known. Null for
     * single-tap quick calibrations (one observation underdetermines the
     * residual). Surfaced in the UI as a confidence badge.
     */
    val rmsErrorDeg: Double? = null,
) {
    /** True if this represents a real solve rather than the inherited default. */
    val isSolved: Boolean get() = solvedAtEpochMs > 0L

    companion object {
        /**
         * Reasonable starting guess for the common "circular fisheye fills the
         * shorter image dimension, north at the top" allsky setup. Used as
         * the seed for the 3-tap precise solver and as the assumed lens
         * geometry for the 1-tap quick calibration (which only solves the
         * rotation).
         */
        val DEFAULT_INSCRIBED = FisheyeCalibration(
            cxFrac = 0.5,
            cyFrac = 0.5,
            radiusFrac = 0.5,
            northOffsetDeg = 0.0,
            solvedAtEpochMs = 0L,
        )
    }
}
