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
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Quick 1-tap calibration of the live-image sky overlay.
 *
 * The user is asked to tap a single bright body whose true alt/az we can
 * compute from their location and the current time — Sun by day, Moon at
 * night, and the brightest above-horizon naked-eye planet as a fallback.
 * [FisheyeProjection.quickCalibrate] then solves the rotation angle from
 * that single observation, assuming the standard "inscribed circular
 * fisheye, centred" geometry.
 *
 * Why one tap rather than three: the inscribed-centred assumption holds for
 * the great majority of Allsky installs (the Allsky software itself crops
 * the sensor frame to the fisheye circle), so only the orientation of the
 * mount is unknown. The advanced 3-tap solver in [FisheyeProjection.preciseCalibrate]
 * is wired but not yet exposed here — coming in the next feature drop.
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

    // Display geometry, captured by onSizeChanged on the image Box.
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    // Source-image intrinsics — populated by Coil once the JPEG loads.
    var imageSize by remember { mutableStateOf(IntSize.Zero) }
    // Last tap (in display-pixel coordinates relative to the image Box).
    // Drawn as a small crosshair so the user can see what was registered
    // before they hit "Save".
    var lastTap by remember { mutableStateOf<Offset?>(null) }
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
                        .pointerInput(target, imageSize, boxSize) {
                            if (target == null || imageSize.width <= 0 || boxSize.width <= 0) {
                                return@pointerInput
                            }
                            detectTapGestures { tapOffset ->
                                lastTap = tapOffset
                            }
                        },
                ) {
                    if (liveImageUrl.isNotEmpty()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(liveImageUrl)
                                .allowHardware(false)
                                .listener(
                                    onSuccess = { _, result ->
                                        val bmp = (result.drawable as? android.graphics.drawable.BitmapDrawable)
                                            ?.bitmap
                                        if (bmp != null) {
                                            imageSize = IntSize(bmp.width, bmp.height)
                                        }
                                    },
                                )
                                .build(),
                            contentDescription = "Live frame to calibrate",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
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

                    // Crosshair for the tap. Drawn last so it's always on top.
                    lastTap?.let { tap ->
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val r = 14.dp.toPx()
                            drawCircle(
                                color = Color(0xFF80DEEA),
                                radius = r,
                                center = tap,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
                            )
                            drawCircle(Color(0xFF80DEEA), radius = 2.dp.toPx(), center = tap)
                        }
                    }
                }

                // Action row: Reset (left) / Save tap (right). Save is only
                // enabled once the user has tapped *and* we have a target.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                userPreferences.saveFisheyeCalibration(
                                    FisheyeCalibration.DEFAULT_INSCRIBED,
                                )
                                snackbarText = "Calibration reset to defaults"
                                lastTap = null
                            }
                        },
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                    ) {
                        Text("RESET", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = {
                            val tap = lastTap ?: return@Button
                            val tgt = target ?: return@Button
                            val img = imageSize
                            val box = boxSize
                            if (img.width <= 0 || box.width <= 0) return@Button

                            // Tap is in Box coords; convert back to image-pixel
                            // coords using the Fit transform we mirrored.
                            val (scale, offX, offY) = FisheyeProjection
                                .transformImagePixelToDisplay(
                                    imageWidthPx = img.width,
                                    imageHeightPx = img.height,
                                    displayWidthPx = box.width.toFloat(),
                                    displayHeightPx = box.height.toFloat(),
                                    fit = true,
                                )
                            val imgX = (tap.x - offX) / scale
                            val imgY = (tap.y - offY) / scale
                            if (imgX < 0 || imgY < 0 ||
                                imgX > img.width || imgY > img.height
                            ) {
                                snackbarText = "Tap landed outside the image — try again"
                                return@Button
                            }
                            val cal = FisheyeProjection.quickCalibrate(
                                observedBody = tgt.coords,
                                tappedPxFrac = imgX / img.width,
                                tappedPyFrac = imgY / img.height,
                            )
                            scope.launch {
                                userPreferences.saveFisheyeCalibration(cal)
                                snackbarText = "Saved — overlay will use ${"%.1f".format(cal.northOffsetDeg)}° rotation"
                                onNavigateBack()
                            }
                        },
                        enabled = lastTap != null && target != null && imageSize.width > 0,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = MaterialTheme.colorScheme.background,
                        ),
                    ) {
                        Text("SAVE TAP", fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun CalibrationInstructions(
    hasLocation: Boolean,
    target: CalibrationTarget?,
    currentCalibration: FisheyeCalibration,
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
                else -> {
                    headline = "Tap ${target.label} on the live frame"
                    detail = "Altitude ${"%.0f".format(target.coords.altitudeDeg)}°, " +
                        "azimuth ${"%.0f".format(target.coords.azimuthDeg)}°. " +
                        "One tap is enough — the app solves the rotation from it."
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
                Text(
                    text = "Current: rotation ${"%.1f".format(currentCalibration.northOffsetDeg)}°",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.55f),
                )
            }
        }
    }
}

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
