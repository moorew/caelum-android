package de.astronarren.allsky.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import de.astronarren.allsky.data.UserPreferences
import de.astronarren.allsky.data.astro.AstroMath
import de.astronarren.allsky.data.astro.FisheyeCalibration
import de.astronarren.allsky.data.astro.FisheyeProjection
import de.astronarren.allsky.data.astro.HorizontalCoords
import de.astronarren.allsky.data.astro.MoonAlmanac
import de.astronarren.allsky.data.astro.Planet
import de.astronarren.allsky.data.astro.PlanetAlmanac
import de.astronarren.allsky.network.AllskyAuth
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Calibration of the live-image sky overlay.
 *
 * Two modes share this screen behind a chip toggle:
 *
 *  * **Quick (1-tap):** the user taps a single bright body — Sun by day,
 *    Moon at night, or a bright planet — and
 *    [FisheyeProjection.quickCalibrate] solves only the rotation angle,
 *    assuming the standard "inscribed circular fisheye, centred" geometry.
 *    Right answer for the great majority of Allsky installs, where the
 *    capture software already crops the sensor frame to the fisheye circle.
 *
 *  * **Precise (3-tap):** the user taps a known body, then due-north on the
 *    horizon, then due-east on the horizon, and
 *    [FisheyeProjection.preciseCalibrate] runs a 4-parameter LM solve over
 *    the whole `(cx, cy, radius, rotation)` set. Right answer for off-axis
 *    or non-inscribed lenses, or when the user just wants a tighter fit
 *    with a confidence number on it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalibrationScreen(
    userPreferences: UserPreferences,
    liveImageUrl: String,
    onNavigateBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val latStr by userPreferences.getLatitudeFlow().collectAsStateWithLifecycle(initialValue = "")
    val lonStr by userPreferences.getLongitudeFlow().collectAsStateWithLifecycle(initialValue = "")
    val allskyUsername by userPreferences.getUsernameFlow().collectAsStateWithLifecycle(initialValue = "")
    val allskyPassword by userPreferences.getPasswordFlow().collectAsStateWithLifecycle(initialValue = "")
    val allskyAuthHeader = remember(allskyUsername, allskyPassword) {
        AllskyAuth.basicAuthHeader(allskyUsername, allskyPassword)
    }
    val currentCalibration by userPreferences.getFisheyeCalibrationFlow()
        .collectAsStateWithLifecycle(initialValue = FisheyeCalibration.DEFAULT_INSCRIBED)

    val lat = latStr.toDoubleOrNull()
    val lon = lonStr.toDoubleOrNull()

    // Re-derive the target body every minute so the prompt and the saved
    // alt/az stay current if the user lingers on the screen. The key is
    // chosen as `now / 60s` so most recompositions reuse the cached value
    // and we don't re-run Meeus on every frame.
    val minuteKey = remember { mutableStateOf(Instant.now().epochSecond / 60) }
    LaunchedEffect(Unit) {
        // Tick the key once per minute. Cheap, and it keeps the body label
        // honest as twilight crosses noon → night.
        while (true) {
            kotlinx.coroutines.delay(60_000)
            minuteKey.value = Instant.now().epochSecond / 60
        }
    }

    val target: CalibrationTarget? = remember(lat, lon, minuteKey.value) {
        if (lat == null || lon == null) null else pickTargetBody(lat, lon)
    }

    // Mode is local screen state — there's no global default to persist; the
    // user picks per-session. Quick is the default because it's what most
    // installs need and what the Settings copy advertises.
    var mode by remember { mutableStateOf(CalibrationMode.QUICK) }

    // Display geometry, captured by onSizeChanged on the image Box.
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    // Source-image intrinsics — populated by Coil once the JPEG loads.
    var imageSize by remember { mutableStateOf(IntSize.Zero) }

    // Quick mode collects a single tap; precise mode collects up to three.
    // Both are in display-pixel coordinates relative to the image Box and
    // are converted to image pixels at SAVE time, against the dimensions
    // we captured when the JPEG loaded.
    var quickTap by remember { mutableStateOf<Offset?>(null) }
    var preciseTaps by remember { mutableStateOf<List<Offset>>(emptyList()) }

    // Bag the two horizon-target alt/az once. These are the celestial
    // positions of "due north on the horizon" and "due east on the
    // horizon" — fixed regardless of time or location.
    val northHorizon = remember { HorizontalCoords(altitudeDeg = 0.0, azimuthDeg = 0.0) }
    val eastHorizon = remember { HorizontalCoords(altitudeDeg = 0.0, azimuthDeg = 90.0) }

    // Reset taps whenever the mode flips so the user isn't carrying stale
    // crosshairs into the new flow. The keep-target-body-tap-on-flip
    // version was tried and felt magical-in-a-bad-way; this is clearer.
    LaunchedEffect(mode) {
        quickTap = null
        preciseTaps = emptyList()
    }

    var snackbarText by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(snackbarText) {
        val msg = snackbarText ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        snackbarText = null
    }

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = "CALIBRATE",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 4.sp,
                            ),
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color.White,
                    ),
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CalibrationInstructions(
                    hasLocation = lat != null && lon != null,
                    target = target,
                    currentCalibration = currentCalibration,
                    mode = mode,
                    preciseStep = preciseTaps.size,
                )

                ModeToggleRow(
                    selected = mode,
                    onSelected = { mode = it },
                )

                // The frozen-frame image. We use the same URL Coil already
                // has in its memory cache (set from MainScreen's live view),
                // so this typically renders instantly without a network hit.
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.3f))
                        .onSizeChanged { boxSize = it }
                        .pointerInput(mode, target, imageSize, boxSize) {
                            if (target == null || imageSize.width <= 0 || boxSize.width <= 0) {
                                return@pointerInput
                            }
                            detectTapGestures { tapOffset ->
                                when (mode) {
                                    CalibrationMode.QUICK -> quickTap = tapOffset
                                    CalibrationMode.PRECISE -> {
                                        // Cap at three; further taps replace
                                        // the third so the user can refine
                                        // east without re-tapping body and
                                        // north.
                                        preciseTaps = if (preciseTaps.size < 3) {
                                            preciseTaps + tapOffset
                                        } else {
                                            preciseTaps.dropLast(1) + tapOffset
                                        }
                                    }
                                }
                            }
                        },
                ) {
                    if (liveImageUrl.isNotEmpty()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(liveImageUrl)
                                .listener(
                                    onSuccess = { _, result ->
                                        val drawable = result.drawable
                                        if (drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0) {
                                            imageSize = IntSize(
                                                drawable.intrinsicWidth,
                                                drawable.intrinsicHeight
                                            )
                                        }
                                    },
                                )
                                .apply {
                                    allskyAuthHeader?.let {
                                        setHeader("Authorization", it)
                                    }
                                }
                                .build(),
                            contentDescription = "Live frame to calibrate",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                            colorFilter = de.astronarren.allsky.ui.theme.caelumImageColorFilter(),
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "No live image URL configured — set it in Settings first.",
                                color = Color.White.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }

                    // Tap markers. Drawn last so they're always on top.
                    // Quick mode shows a single unnumbered crosshair; precise
                    // shows numbered crosshairs so the user can see which
                    // step each tap belongs to.
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        when (mode) {
                            CalibrationMode.QUICK -> {
                                quickTap?.let { tap ->
                                    val r = 14.dp.toPx()
                                    drawCircle(
                                        color = Color(0xFF80DEEA),
                                        radius = r,
                                        center = tap,
                                        style = Stroke(width = 2.dp.toPx()),
                                    )
                                    drawCircle(Color(0xFF80DEEA), radius = 2.dp.toPx(), center = tap)
                                }
                            }
                            CalibrationMode.PRECISE -> {
                                val r = 14.dp.toPx()
                                val paint = android.graphics.Paint().apply {
                                    color = android.graphics.Color.WHITE
                                    textSize = 12.sp.toPx()
                                    textAlign = android.graphics.Paint.Align.CENTER
                                    isFakeBoldText = true
                                    isAntiAlias = true
                                }
                                preciseTaps.forEachIndexed { idx, tap ->
                                    drawCircle(
                                        color = Color(0xFF80DEEA),
                                        radius = r,
                                        center = tap,
                                        style = Stroke(width = 2.dp.toPx()),
                                    )
                                    drawCircle(Color(0xFF80DEEA), radius = 2.dp.toPx(), center = tap)
                                    drawContext.canvas.nativeCanvas.drawText(
                                        (idx + 1).toString(),
                                        tap.x + 18.dp.toPx(),
                                        tap.y - 14.dp.toPx(),
                                        paint,
                                    )
                                }
                            }
                        }
                    }
                }

                ActionRow(
                    mode = mode,
                    canSave = canSave(mode, quickTap, preciseTaps, target, imageSize),
                    onReset = {
                        scope.launch {
                            userPreferences.saveFisheyeCalibration(
                                FisheyeCalibration.DEFAULT_INSCRIBED,
                            )
                            snackbarText = "Calibration reset to defaults"
                            quickTap = null
                            preciseTaps = emptyList()
                        }
                    },
                    onSave = {
                        val tgt = target ?: return@ActionRow
                        val img = imageSize
                        val box = boxSize
                        if (img.width <= 0 || box.width <= 0) return@ActionRow
                        val (scale, offX, offY) = FisheyeProjection
                            .transformImagePixelToDisplay(
                                imageWidthPx = img.width,
                                imageHeightPx = img.height,
                                displayWidthPx = box.width.toFloat(),
                                displayHeightPx = box.height.toFloat(),
                                fit = true,
                            )
                        // Convert a display-space tap back to image-pixel
                        // coords using the Fit transform we mirrored.
                        // Returns null when the tap landed in the letterbox
                        // gutter outside the image proper.
                        fun toImagePx(tap: Offset): Pair<Double, Double>? {
                            val imgX = (tap.x - offX) / scale
                            val imgY = (tap.y - offY) / scale
                            if (imgX < 0 || imgY < 0 ||
                                imgX > img.width || imgY > img.height
                            ) return null
                            return imgX.toDouble() to imgY.toDouble()
                        }

                        when (mode) {
                            CalibrationMode.QUICK -> {
                                val tap = quickTap ?: return@ActionRow
                                val (ix, iy) = toImagePx(tap) ?: run {
                                    snackbarText = "Tap landed outside the image — try again"
                                    return@ActionRow
                                }
                                val cal = FisheyeProjection.quickCalibrate(
                                    observedBody = tgt.coords,
                                    tappedPxFrac = ix / img.width,
                                    tappedPyFrac = iy / img.height,
                                )
                                scope.launch {
                                    userPreferences.saveFisheyeCalibration(cal)
                                    snackbarText = "Saved — overlay will use ${"%.1f".format(cal.northOffsetDeg)}° rotation"
                                    onNavigateBack()
                                }
                            }
                            CalibrationMode.PRECISE -> {
                                if (preciseTaps.size < 3) return@ActionRow
                                val (bx, by_) = toImagePx(preciseTaps[0]) ?: run {
                                    snackbarText = "Body tap is outside the image — try again"
                                    return@ActionRow
                                }
                                val nIm = toImagePx(preciseTaps[1]) ?: run {
                                    snackbarText = "North tap is outside the image — try again"
                                    return@ActionRow
                                }
                                val eIm = toImagePx(preciseTaps[2]) ?: run {
                                    snackbarText = "East tap is outside the image — try again"
                                    return@ActionRow
                                }
                                val observations = listOf(
                                    FisheyeProjection.Observation(tgt.coords, bx, by_),
                                    FisheyeProjection.Observation(northHorizon, nIm.first, nIm.second),
                                    FisheyeProjection.Observation(eastHorizon, eIm.first, eIm.second),
                                )
                                val solved = FisheyeProjection.preciseCalibrate(
                                    observations = observations,
                                    imageWidthPx = img.width,
                                    imageHeightPx = img.height,
                                )
                                if (solved != null) {
                                    scope.launch {
                                        userPreferences.saveFisheyeCalibration(solved)
                                        snackbarText = "Saved — RMS ±${"%.2f".format(solved.rmsErrorDeg ?: 0.0)}° fit"
                                        onNavigateBack()
                                    }
                                } else {
                                    // LM rejected the fit as out-of-bounds.
                                    // Fall back to a quick rotation-only
                                    // solve from the body tap so the user
                                    // still walks away with *something*
                                    // useful — better than blocking on the
                                    // perfect.
                                    val cal = FisheyeProjection.quickCalibrate(
                                        observedBody = tgt.coords,
                                        tappedPxFrac = bx / img.width,
                                        tappedPyFrac = by_ / img.height,
                                    )
                                    scope.launch {
                                        userPreferences.saveFisheyeCalibration(cal)
                                        snackbarText = "Precise solve failed — kept quick rotation only"
                                        onNavigateBack()
                                    }
                                }
                            }
                        }
                    },
                )

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

/** True iff the SAVE button should be enabled for the current mode + state. */
private fun canSave(
    mode: CalibrationMode,
    quickTap: Offset?,
    preciseTaps: List<Offset>,
    target: CalibrationTarget?,
    imageSize: IntSize,
): Boolean {
    if (target == null || imageSize.width <= 0) return false
    return when (mode) {
        CalibrationMode.QUICK -> quickTap != null
        CalibrationMode.PRECISE -> preciseTaps.size == 3
    }
}

@Composable
private fun ModeToggleRow(
    selected: CalibrationMode,
    onSelected: (CalibrationMode) -> Unit,
) {
    // FilterChip pair rather than SegmentedButton — matches the chip idiom
    // already used in MediaScreen.kt and keeps the look consistent.
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CalibrationModeChip(
            label = "QUICK · 1 TAP",
            selected = selected == CalibrationMode.QUICK,
            onClick = { onSelected(CalibrationMode.QUICK) },
            modifier = Modifier.weight(1f),
        )
        CalibrationModeChip(
            label = "PRECISE · 3 TAPS",
            selected = selected == CalibrationMode.PRECISE,
            onClick = { onSelected(CalibrationMode.PRECISE) },
            modifier = Modifier.weight(1f),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalibrationModeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                    ),
                )
            }
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.White.copy(alpha = 0.05f),
            labelColor = Color.White.copy(alpha = 0.7f),
            selectedContainerColor = Color.White.copy(alpha = 0.18f),
            selectedLabelColor = Color.White,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = Color.White.copy(alpha = 0.25f),
            selectedBorderColor = Color.White.copy(alpha = 0.6f),
            borderWidth = 1.dp,
            selectedBorderWidth = 1.dp,
        ),
        modifier = modifier,
    )
}

@Composable
private fun ActionRow(
    mode: CalibrationMode,
    canSave: Boolean,
    onReset: () -> Unit,
    onSave: () -> Unit,
) {
    val saveLabel = when (mode) {
        CalibrationMode.QUICK -> "SAVE TAP"
        CalibrationMode.PRECISE -> "SOLVE & SAVE"
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(
            onClick = onReset,
            modifier = Modifier.weight(1f).height(52.dp),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
        ) {
            Text("RESET", color = Color.White, fontWeight = FontWeight.Bold)
        }
        Button(
            onClick = onSave,
            enabled = canSave,
            modifier = Modifier.weight(1f).height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = MaterialTheme.colorScheme.background,
            ),
        ) {
            Text(saveLabel, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
        }
    }
}

@Composable
private fun CalibrationInstructions(
    hasLocation: Boolean,
    target: CalibrationTarget?,
    currentCalibration: FisheyeCalibration,
    mode: CalibrationMode,
    preciseStep: Int,
) {
    GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 18.dp) {
        Column(modifier = Modifier.padding(16.dp)) {
            val headline: String
            val detail: String
            when {
                !hasLocation -> {
                    headline = "Set your location first"
                    detail = "Calibration needs latitude and longitude. " +
                        "Open Settings → Location, save, then come back."
                }
                target == null -> {
                    headline = "Nothing bright above the horizon right now"
                    detail = "Try again at night with the Moon or a bright " +
                        "planet visible, or in the daytime with the Sun up."
                }
                mode == CalibrationMode.QUICK -> {
                    headline = "Tap ${target.label} on the live frame"
                    detail = "Altitude ${"%.0f".format(target.coords.altitudeDeg)}°, " +
                        "azimuth ${"%.0f".format(target.coords.azimuthDeg)}°. " +
                        "One tap is enough — the app solves the rotation from it."
                }
                else -> {
                    // Precise: rotate the prompt through the three steps so
                    // the user always knows what to tap next.
                    when (preciseStep) {
                        0 -> {
                            headline = "1 / 3 — Tap ${target.label}"
                            detail = "Altitude ${"%.0f".format(target.coords.altitudeDeg)}°, " +
                                "azimuth ${"%.0f".format(target.coords.azimuthDeg)}°."
                        }
                        1 -> {
                            headline = "2 / 3 — Tap due-north horizon"
                            detail = "Where compass-north meets the horizon ring " +
                                "in the live frame. Use a landmark you know is " +
                                "due north of the camera."
                        }
                        2 -> {
                            headline = "3 / 3 — Tap due-east horizon"
                            detail = "Where compass-east meets the horizon ring. " +
                                "Tap SOLVE when all three crosshairs look right."
                        }
                        else -> {
                            headline = "Three taps captured"
                            detail = "Tap SOLVE & SAVE to run the fit, or tap " +
                                "again to refine the east marker."
                        }
                    }
                }
            }
            Text(
                text = headline,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.75f),
            )
            if (currentCalibration.isSolved) {
                Spacer(modifier = Modifier.height(8.dp))
                val rms = currentCalibration.rmsErrorDeg
                val suffix = if (rms != null) " · ±${"%.2f".format(rms)}° fit" else ""
                Text(
                    text = "Current: rotation ${"%.1f".format(currentCalibration.northOffsetDeg)}°$suffix",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.55f),
                )
            }
        }
    }
}

/** Which calibration flow is active. Local to this screen. */
private enum class CalibrationMode { QUICK, PRECISE }

/** Body the user is being asked to tap, with its current observer-local alt/az. */
private data class CalibrationTarget(
    val label: String,
    val coords: HorizontalCoords,
)

/**
 * Pick the brightest naked-eye body that's usefully above the horizon for
 * calibration. Sun beats moon beats planets — that's the order of how
 * unambiguously the user can identify it in a live frame. Returns null if
 * everything is below the horizon (true polar night corner case + the user
 * caught us between sun-set and moon-rise on a planetless evening).
 */
private fun pickTargetBody(lat: Double, lon: Double): CalibrationTarget? {
    val now = Instant.now()
    val jd = AstroMath.julianDate(now)

    val sunEq = AstroMath.sunEquatorial(jd)
    val sun = AstroMath.equatorialToHorizontal(sunEq.raDeg, sunEq.decDeg, lat, lon, jd)
    if (sun.altitudeDeg > 0.0) return CalibrationTarget("the Sun ☀", sun)

    val moonEq = MoonAlmanac.position(jd).equatorial
    val moon = AstroMath.equatorialToHorizontal(moonEq.raDeg, moonEq.decDeg, lat, lon, jd)
    if (moon.altitudeDeg > 5.0) return CalibrationTarget("the Moon 🌙", moon)

    // Planets, brightest first by typical apparent magnitude. We compute
    // each snapshot once to get its current alt/az; the first above-horizon
    // hit wins. Venus & Jupiter are the only ones that read reliably as a
    // single point against a dark frame, hence the priority order.
    val ranked = listOf(Planet.VENUS, Planet.JUPITER, Planet.MARS, Planet.SATURN, Planet.MERCURY)
    for (planet in ranked) {
        val snap = PlanetAlmanac.snapshot(planet, lat, lon, now)
        if (snap.horizontal.altitudeDeg > 10.0) {
            return CalibrationTarget(planet.displayName, snap.horizontal)
        }
    }
    // Last resort: the Moon even if low (better than nothing).
    if (moon.altitudeDeg > 0.0) return CalibrationTarget("the Moon 🌙 (low)", moon)
    return null
}
