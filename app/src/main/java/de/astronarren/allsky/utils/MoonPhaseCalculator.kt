package de.astronarren.allsky.utils

import de.astronarren.allsky.R
import de.astronarren.allsky.data.astro.MoonAlmanac
import java.time.Instant

/**
 * Thin, backwards-compatible facade in front of
 * [MoonAlmanac.phaseAt][de.astronarren.allsky.data.astro.MoonAlmanac.phaseAt].
 *
 * Historically this class carried its own simple "seconds since the
 * 2000-01-06 new moon, modulo 29.53 days" model. That gave one answer for the
 * home-screen moon card while the new Tonight card derived its phase label
 * from Meeus illumination % — the two disagreed near the phase boundaries
 * and were genuinely confusing. The fix is to make this object a passthrough
 * so every surface in the app reads from one source.
 *
 * Public API preserved verbatim so [MoonPhaseDisplay] and any future caller
 * don't need to change. New callers should prefer
 * [MoonAlmanac.phaseAt] directly — it returns the full bundle without the
 * round-trip via four method calls.
 */
class MoonPhaseCalculator {
    companion object {
        fun calculateMoonPhase(): MoonPhase =
            MoonAlmanac.phaseAt(Instant.now()).phase

        fun getDaysUntilNewMoon(): Double =
            MoonAlmanac.phaseAt(Instant.now()).daysUntilNewMoon

        fun getCurrentMoonCycleFraction(): Double =
            MoonAlmanac.phaseAt(Instant.now()).synodicFraction

        fun getIllumination(): Double =
            MoonAlmanac.phaseAt(Instant.now()).illuminatedFraction * 100.0
    }
}

enum class MoonPhase(val stringResId: Int, val emoji: String) {
    NEW_MOON(R.string.moon_phase_new_moon, "🌑"),
    WAXING_CRESCENT(R.string.moon_phase_waxing_crescent, "🌒"),
    FIRST_QUARTER(R.string.moon_phase_first_quarter, "🌓"),
    WAXING_GIBBOUS(R.string.moon_phase_waxing_gibbous, "🌔"),
    FULL_MOON(R.string.moon_phase_full_moon, "🌕"),
    WANING_GIBBOUS(R.string.moon_phase_waning_gibbous, "🌖"),
    LAST_QUARTER(R.string.moon_phase_last_quarter, "🌗"),
    WANING_CRESCENT(R.string.moon_phase_waning_crescent, "🌘")
}
