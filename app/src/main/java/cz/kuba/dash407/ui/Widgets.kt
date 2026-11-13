package cz.kuba.dash407.ui

import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Dehaze
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.WbCloudy
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import cz.kuba.dash407.R
import cz.kuba.dash407.car.CarRepo
import cz.kuba.dash407.car.CarState
import cz.kuba.dash407.car.Doors
import cz.kuba.dash407.data.AppsRepo
import cz.kuba.dash407.data.FuelLog
import cz.kuba.dash407.data.Gps
import cz.kuba.dash407.data.MapSettings
import cz.kuba.dash407.data.NavRepo
import cz.kuba.dash407.data.NowPlaying
import cz.kuba.dash407.data.Prefs
import cz.kuba.dash407.data.TripMeter
import cz.kuba.dash407.data.Weather
import cz.kuba.dash407.data.WidgetType
import cz.kuba.dash407.map.MapHub
import cz.kuba.dash407.map.MapyLinks
import cz.kuba.dash407.map.MapyLogo
import cz.kuba.dash407.map.MapyMapController
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Vykreslí dlaždici daného druhu. [nav] otevírá ostatní obrazovky. */
@Composable
fun WidgetHost(type: WidgetType, modifier: Modifier, nav: (Screen) -> Unit) {
    when (type) {
        WidgetType.MAP -> MapWidget(modifier, nav)
        WidgetType.NAV -> NavWidget(modifier)
        WidgetType.CLOCK -> ClockWidget(modifier)
        WidgetType.WEATHER -> WeatherWidget(modifier)
        WidgetType.MEDIA -> MediaWidget(modifier, nav)
        WidgetType.TRIP -> TripWidget(modifier)
        WidgetType.SPEED -> SpeedWidget(modifier)
        WidgetType.CAR -> CarComputerWidget(modifier, nav)
        WidgetType.DOORS -> DoorsTile(CarRepo.state.collectAsState().value, modifier)
        WidgetType.FUEL -> FuelWidget(modifier, nav)
        WidgetType.DOCK -> DockWidget(modifier, nav)
    }
}

/** Název dlaždice pro katalog a úpravy. */
fun widgetName(type: WidgetType): Int = when (type) {
    WidgetType.MAP -> R.string.widget_map
    WidgetType.NAV -> R.string.widget_nav
    WidgetType.CLOCK -> R.string.widget_clock
    WidgetType.WEATHER -> R.string.widget_weather
    WidgetType.MEDIA -> R.string.widget_media
    WidgetType.TRIP -> R.string.widget_trip
    WidgetType.SPEED -> R.string.widget_speed
    WidgetType.CAR -> R.string.widget_car
    WidgetType.DOORS -> R.string.widget_doors
    WidgetType.FUEL -> R.string.widget_fuel
    WidgetType.DOCK -> R.string.widget_dock
}

fun widgetDescription(type: WidgetType): Int = when (type) {
    WidgetType.MAP -> R.string.widget_map_desc
    WidgetType.NAV -> R.string.widget_nav_desc
    WidgetType.CLOCK -> R.string.widget_clock_desc
    WidgetType.WEATHER -> R.string.widget_weather_desc
    WidgetType.MEDIA -> R.string.widget_media_desc
    WidgetType.TRIP -> R.string.widget_trip_desc
    WidgetType.SPEED -> R.string.widget_speed_desc
    WidgetType.CAR -> R.string.widget_car_desc
    WidgetType.DOORS -> R.string.widget_doors_desc
    WidgetType.FUEL -> R.string.widget_fuel_desc
    WidgetType.DOCK -> R.string.widget_dock_desc
}

/** Aktuální čas, obnovovaný každou sekundu. */
@Composable
fun rememberNow(): LocalDateTime {
    val now by produceState(initialValue = LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now()
            delay(1000L - System.currentTimeMillis() % 1000L)
        }
    }
    return now
}

fun timeText(now: LocalDateTime, seconds: Boolean): String =
    now.format(DateTimeFormatter.ofPattern(if (seconds) "H:mm:ss" else "H:mm", Czech))

fun dateText(now: LocalDateTime): String =
    now.format(DateTimeFormatter.ofPattern("EEEE d. MMMM", Czech)).replaceFirstChar { it.uppercase() }

private fun tileShape() = RoundedCornerShape(Palette.Radius)

// ---------------------------------------------------------------- Mapa

@Composable
private fun MapWidget(modifier: Modifier, nav: (Screen) -> Unit) {
    val context = LocalContext.current
    val settings by Prefs.map.collectAsState()
    val allowed by Gps.allowed.collectAsState()
    val controllerRef = remember { mutableStateOf<MapyMapController?>(null) }

    fun bounds(): Rect? {
        if (!settings.windowMode) return null
        val v = controllerRef.value?.view ?: return null
        val xy = IntArray(2)
        v.getLocationOnScreen(xy)
        return Rect(xy[0], xy[1], xy[0] + v.width, xy[1] + v.height)
    }

    Surface(modifier, shape = tileShape(), color = Palette.Tile, border = Palette.TileBorder) {
        Box(Modifier.fillMaxSize()) {
            if (settings.apiKey.isBlank()) {
                MapKeyMissing(nav)
            } else {
                MapSurface(settings, allowed, controllerRef)
                MapAttribution(Modifier.align(Alignment.BottomStart).padding(10.dp))
                Column(
                    Modifier.align(Alignment.BottomEnd).padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    RoundButton(Icons.Filled.MyLocation, stringResource(R.string.map_recenter)) {
                        controllerRef.value?.recenter(settings.zoom)
                    }
                    RoundButton(Icons.Filled.Navigation, stringResource(R.string.map_open_mapy), filled = true) {
                        MapyLinks.open(context, bounds())
                    }
                }
            }
            Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(10.dp)) {
                SearchBar { query -> MapyLinks.search(context, query, bounds()) }
                if (settings.homeAddress.isNotBlank() || settings.workAddress.isNotBlank()) {
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (settings.homeAddress.isNotBlank()) {
                            Chip(Icons.Filled.Home, stringResource(R.string.map_go_home)) {
                                MapyLinks.navigate(context, settings.homeAddress, bounds())
                            }
                        }
                        if (settings.workAddress.isNotBlank()) {
                            Chip(Icons.Filled.Work, stringResource(R.string.map_go_work)) {
                                MapyLinks.navigate(context, settings.workAddress, bounds())
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MapSurface(settings: MapSettings, allowed: Boolean, ref: MutableState<MapyMapController?>) {
    val context = LocalContext.current
    val controller = remember { MapyMapController(context) }
    DisposableEffect(controller) {
        MapHub.register(controller)
        ref.value = controller
        onDispose {
            ref.value = null
            MapHub.unregister(controller)
            controller.destroy()
        }
    }
    AndroidView(
        factory = { controller.view },
        modifier = Modifier.fillMaxSize(),
        update = { controller.apply(settings, allowed) }
    )
}

/** Logo a copyright Mapy.com nad mapou – vyžadují je podmínky REST API. */
@Composable
private fun MapAttribution(modifier: Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val logo by MapyLogo.bitmap.collectAsState()
    val heightPx = with(density) { 30.dp.roundToPx() }
    LaunchedEffect(Unit) { MapyLogo.load(context, heightPx) }
    Column(modifier) {
        val bmp = logo
        if (bmp != null) {
            Image(
                bmp.asImageBitmap(),
                contentDescription = "Mapy.com",
                modifier = Modifier.height(30.dp).clickable { MapyLinks.openWeb(context, "https://mapy.com/") }
            )
        }
        Text(
            stringResource(R.string.map_copyright),
            color = Color.White,
            fontSize = 11.sp,
            modifier = Modifier
                .padding(top = 4.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0x99000000))
                .clickable { MapyLinks.openWeb(context, "https://api.mapy.com/copyright") }
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun MapKeyMissing(nav: (Screen) -> Unit) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(48.dp))
        Icon(Icons.Filled.Map, null, Modifier.size(56.dp), tint = Palette.Accent)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.map_key_missing_title), color = Palette.Text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.map_key_missing_text), color = Palette.Muted, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { nav(Screen.SETTINGS) }) { Text(stringResource(R.string.map_key_missing_button)) }
            OutlinedButton(onClick = { MapyLinks.open(context) }) { Text(stringResource(R.string.map_open_mapy)) }
        }
    }
}

@Composable
private fun SearchBar(onSearch: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xE6101726),
        border = Palette.TileBorder,
        modifier = Modifier.fillMaxWidth().height(56.dp)
    ) {
        Row(Modifier.fillMaxSize().padding(start = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (text.isEmpty()) {
                    Text(stringResource(R.string.map_search_hint), color = Palette.Muted, fontSize = 19.sp)
                }
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Palette.Text, fontSize = 19.sp),
                    cursorBrush = SolidColor(Palette.Accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        onSearch(text)
                        text = ""
                    }),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            IconButton(onClick = {
                onSearch(text)
                text = ""
            }) {
                Icon(Icons.Filled.Search, stringResource(R.string.map_search), tint = Palette.Accent, modifier = Modifier.size(30.dp))
            }
        }
    }
}

@Composable
private fun Chip(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xE6101726),
        border = Palette.TileBorder,
        modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable { onClick() }
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(20.dp), tint = Palette.Accent)
            Spacer(Modifier.width(6.dp))
            Text(label, color = Palette.Text, fontSize = 16.sp)
        }
    }
}

@Composable
fun RoundButton(icon: ImageVector, description: String, filled: Boolean = false, size: Int = 56, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = if (filled) Palette.Accent else Color(0xE6101726),
        border = if (filled) null else Palette.TileBorder,
        modifier = Modifier.size(size.dp).clip(CircleShape).clickable { onClick() }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, description, Modifier.size((size * 0.5f).dp), tint = if (filled) Color(0xFF06101C) else Palette.Text)
        }
    }
}

// ---------------------------------------------------------------- Navigace

@Composable
private fun NavWidget(modifier: Modifier) {
    val context = LocalContext.current
    val nav by NavRepo.state.collectAsState()
    val look = LookState.look
    val now = rememberNow()
    Tile("", modifier.clip(tileShape()).clickable { MapyLinks.open(context) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TileTitle(stringResource(R.string.widget_nav))
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (nav.active) Palette.Good else Palette.Muted))
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(if (nav.active) R.string.nav_active else R.string.nav_no_route),
                color = Palette.Muted,
                fontSize = 13.sp,
                maxLines = 1
            )
        }
        if (nav.active) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                val icon = nav.icon
                if (icon != null) {
                    Image(icon.asImageBitmap(), null, Modifier.size(76.dp))
                } else {
                    Icon(Icons.Filled.Navigation, null, Modifier.size(64.dp), tint = Palette.Accent)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(nav.title ?: "", color = Palette.Text, fontSize = 32.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val text = nav.text
                    if (!text.isNullOrBlank()) {
                        Text(text, color = Palette.Text, fontSize = 17.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                    val sub = nav.subText
                    if (!sub.isNullOrBlank()) {
                        Text(sub, color = Palette.Muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        } else {
            BigClock(now, look.showSeconds, Modifier.fillMaxSize())
        }
    }
}

/** Velké hodiny s datem, velikost písma podle výšky dlaždice. */
@Composable
private fun BigClock(now: LocalDateTime, seconds: Boolean, modifier: Modifier) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val size = (maxHeight.value * 0.42f).coerceIn(28f, 110f).coerceAtMost(maxWidth.value * (if (seconds) 0.24f else 0.34f))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(timeText(now, seconds), color = Palette.Text, fontSize = size.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(dateText(now), color = Palette.Muted, fontSize = (size * 0.22f).coerceIn(13f, 22f).sp, maxLines = 1)
        }
    }
}

// ---------------------------------------------------------------- Hodiny

@Composable
private fun ClockWidget(modifier: Modifier) {
    val now = rememberNow()
    Tile(stringResource(R.string.widget_clock), modifier) {
        BigClock(now, LookState.look.showSeconds, Modifier.fillMaxSize())
    }
}

// ---------------------------------------------------------------- Počasí

@Composable
private fun WeatherWidget(modifier: Modifier) {
    val weather by Weather.state.collectAsState()
    val location by Gps.location.collectAsState()
    val roughKey = location?.let { (it.latitude * 20).toInt() * 10_000 + (it.longitude * 20).toInt() }
    LaunchedEffect(roughKey) {
        val loc = Gps.location.value ?: return@LaunchedEffect
        Weather.maybeRefresh(loc.latitude, loc.longitude)
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(5 * 60_000L)
            Gps.location.value?.let { Weather.maybeRefresh(it.latitude, it.longitude) }
        }
    }
    Tile("", modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TileTitle(stringResource(R.string.widget_weather))
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { Gps.location.value?.let { Weather.maybeRefresh(it.latitude, it.longitude, force = true) } }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Refresh, stringResource(R.string.weather_refresh), tint = Palette.Muted)
            }
        }
        val temp = weather.tempC
        if (temp == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(
                        when {
                            location == null -> R.string.weather_waiting_location
                            weather.failed -> R.string.weather_failed
                            else -> R.string.weather_loading
                        }
                    ),
                    color = Palette.Muted,
                    fontSize = 15.sp
                )
            }
        } else {
            val kind = Weather.kindOf(weather.code)
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Icon(weatherIcon(kind, weather.isDay), null, Modifier.size(64.dp), tint = Palette.Accent)
                Spacer(Modifier.width(14.dp))
                Column {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(decimal(temp, 0), color = Palette.Text, fontSize = 48.sp, fontWeight = FontWeight.Bold)
                        Text("°C", color = Palette.Text, fontSize = 22.sp, modifier = Modifier.padding(top = 8.dp, start = 2.dp))
                    }
                    Text(stringResource(weatherName(kind)), color = Palette.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    val feels = weather.feelsC
                    val wind = weather.windKmh
                    if (feels != null && wind != null) {
                        Text(
                            stringResource(R.string.weather_detail, decimal(feels, 0), decimal(wind, 0)),
                            color = Palette.Muted,
                            fontSize = 14.sp,
                            maxLines = 2
                        )
                    }
                }
            }
        }
    }
}

private fun weatherIcon(kind: Weather.Kind, day: Boolean): ImageVector = when (kind) {
    Weather.Kind.CLEAR -> if (day) Icons.Filled.WbSunny else Icons.Filled.NightsStay
    Weather.Kind.PARTLY -> Icons.Filled.WbCloudy
    Weather.Kind.CLOUDY -> Icons.Filled.Cloud
    Weather.Kind.FOG -> Icons.Filled.Dehaze
    Weather.Kind.DRIZZLE, Weather.Kind.RAIN -> Icons.Filled.Grain
    Weather.Kind.SNOW -> Icons.Filled.AcUnit
    Weather.Kind.STORM -> Icons.Filled.FlashOn
    Weather.Kind.UNKNOWN -> Icons.Filled.Cloud
}

private fun weatherName(kind: Weather.Kind): Int = when (kind) {
    Weather.Kind.CLEAR -> R.string.weather_clear
    Weather.Kind.PARTLY -> R.string.weather_partly
    Weather.Kind.CLOUDY -> R.string.weather_cloudy
    Weather.Kind.FOG -> R.string.weather_fog
    Weather.Kind.DRIZZLE -> R.string.weather_drizzle
    Weather.Kind.RAIN -> R.string.weather_rain
    Weather.Kind.SNOW -> R.string.weather_snow
    Weather.Kind.STORM -> R.string.weather_storm
    Weather.Kind.UNKNOWN -> R.string.weather_unknown
}

// ---------------------------------------------------------------- Hudba

@Composable
private fun MediaWidget(modifier: Modifier, nav: (Screen) -> Unit) {
    val context = LocalContext.current
    val media by NowPlaying.state.collectAsState()
    Box(modifier.clip(tileShape())) {
        val art = media.art
        if (art != null) {
            Image(art.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.sweepGradient(listOf(Palette.Accent, Palette.Accent2, Color(0xFFE0806A), Palette.Accent))
                )
            )
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x33000000), Color(0xCC0A0F1C)))))
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.MusicNote, null, Modifier.size(18.dp), tint = Color.White)
                Spacer(Modifier.width(6.dp))
                TileTitle(stringResource(R.string.home_media_title), color = Color.White)
            }
            Spacer(Modifier.weight(1f))
            val title = when {
                !media.allowed -> stringResource(R.string.home_media_not_allowed)
                !media.hasSession -> stringResource(R.string.home_media_nothing)
                else -> media.title ?: stringResource(R.string.home_media_unknown)
            }
            Text(
                title,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable {
                    val pkg = media.packageName
                    if (!media.allowed) nav(Screen.SETTINGS) else if (pkg != null) AppsRepo.launch(context, pkg)
                }
            )
            val artist = media.artist
            if (media.hasSession && !artist.isNullOrBlank()) {
                Text(artist, color = Color(0xCCFFFFFF), fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                MediaButton(Icons.Filled.SkipPrevious, stringResource(R.string.media_previous), false) { NowPlaying.previous() }
                Spacer(Modifier.width(14.dp))
                MediaButton(
                    if (media.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    stringResource(if (media.playing) R.string.media_pause else R.string.media_play),
                    true
                ) { NowPlaying.playPause() }
                Spacer(Modifier.width(14.dp))
                MediaButton(Icons.Filled.SkipNext, stringResource(R.string.media_next), false) { NowPlaying.next() }
            }
        }
    }
}

@Composable
private fun MediaButton(icon: ImageVector, description: String, main: Boolean, onClick: () -> Unit) {
    val size = if (main) 64.dp else 50.dp
    Surface(
        shape = CircleShape,
        color = if (main) Color.White else Color(0x40FFFFFF),
        modifier = Modifier.size(size).clip(CircleShape).clickable { onClick() }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, description, Modifier.size(if (main) 38.dp else 28.dp), tint = if (main) Color(0xFF0A0F1C) else Color.White)
        }
    }
}

// ---------------------------------------------------------------- Jízda

@Composable
private fun TripWidget(modifier: Modifier) {
    val trip by TripMeter.state.collectAsState()
    val car by CarRepo.state.collectAsState()
    // Čtení času zajistí překreslení každou sekundu.
    val now = rememberNow()
    val elapsedMs = (now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() - trip.startedAt).coerceAtLeast(0L)
    Tile("", modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TileTitle(stringResource(R.string.widget_trip))
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { TripMeter.reset() }) {
                Text(stringResource(R.string.trip_reset), color = Palette.Accent, fontSize = 15.sp)
            }
        }
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(decimal(trip.distanceM / 1000.0, 1), color = Palette.Text, fontSize = 46.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(" km", color = Palette.Text, fontSize = 18.sp, modifier = Modifier.padding(bottom = 8.dp))
                }
                val started = LocalDateTime.ofInstant(Instant.ofEpochMilli(trip.startedAt), ZoneId.systemDefault())
                Text(stringResource(R.string.trip_since, timeText(started, false)), color = Palette.Muted, fontSize = 14.sp)
                val instant = car.instantL100
                if (instant != null) {
                    Text(stringResource(R.string.trip_instant, decimal(instant)), color = Palette.Muted, fontSize = 14.sp)
                }
            }
            Column(Modifier.weight(1.1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TripLine(stringResource(R.string.trip_time), duration(elapsedMs))
                TripLine(stringResource(R.string.trip_moving), duration(trip.movingMs))
                TripLine(stringResource(R.string.trip_average), trip.averageKmh?.let { "$it km/h" } ?: DASH)
                TripLine(stringResource(R.string.trip_top), if (trip.topKmh > 0) "${trip.topKmh} km/h" else DASH)
            }
        }
    }
}

@Composable
private fun TripLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, color = Palette.Muted, fontSize = 16.sp, modifier = Modifier.weight(1f), maxLines = 1)
        Text(value, color = Palette.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** „1 h 05 min“, „15 min 35 s“, „42 s“. */
fun duration(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return when {
        h > 0 -> String.format(Czech, "%d h %02d min", h, m)
        m > 0 -> String.format(Czech, "%d min %02d s", m, s)
        else -> "$s s"
    }
}

// ---------------------------------------------------------------- Rychlost

@Composable
private fun SpeedWidget(modifier: Modifier) {
    val speed by Gps.speedKmh.collectAsState()
    Tile(stringResource(R.string.home_speed_title), modifier) {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val big = (minOf(maxHeight.value, maxWidth.value) * 0.36f).coerceIn(28f, 96f)
            Canvas(Modifier.fillMaxSize().padding(6.dp)) {
                val stroke = 12.dp.toPx()
                val side = minOf(size.width, size.height) - stroke
                val topLeft = Offset((size.width - side) / 2f, (size.height - side) / 2f)
                val arc = Size(side, side)
                drawArc(Palette.TileHigh, 135f, 270f, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Round))
                val part = ((speed ?: 0).coerceIn(0, 200)) / 200f
                if (part > 0f) {
                    drawArc(
                        if ((speed ?: 0) > 135) Palette.Amber else Palette.Accent,
                        135f, 270f * part, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Round)
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(speed?.toString() ?: DASH, color = Palette.Text, fontSize = big.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(stringResource(R.string.unit_kmh), color = Palette.Muted, fontSize = 16.sp)
            }
        }
    }
}

// ---------------------------------------------------------------- Palubní počítač

@Composable
private fun CarComputerWidget(modifier: Modifier, nav: (Screen) -> Unit) {
    val car by CarRepo.state.collectAsState()
    Tile(stringResource(R.string.home_trip_title), modifier.clip(tileShape()).clickable { nav(Screen.CAR) }) {
        Spacer(Modifier.height(6.dp))
        Reading(car.instantL100?.let { decimal(it) } ?: DASH, stringResource(R.string.home_trip_instant))
        Spacer(Modifier.height(6.dp))
        Reading(
            car.rangeKm?.toString() ?: DASH,
            stringResource(R.string.home_trip_range),
            valueColor = if ((car.rangeKm ?: 999) < 80) Palette.Amber else Palette.Text
        )
        Spacer(Modifier.height(6.dp))
        Reading(car.trip1?.avgL100?.let { decimal(it) } ?: DASH, stringResource(R.string.home_trip_average))
    }
}

// ---------------------------------------------------------------- Spotřeba

@Composable
private fun FuelWidget(modifier: Modifier, nav: (Screen) -> Unit) {
    val refuels by FuelLog.entries.collectAsState()
    Tile(stringResource(R.string.home_fuel_title), modifier.clip(tileShape()).clickable { nav(Screen.FUEL) }) {
        val stats = FuelLog.stats(refuels)
        Spacer(Modifier.height(6.dp))
        Reading(stats.averageL100?.let { decimal(it) } ?: DASH, stringResource(R.string.home_fuel_average))
        Spacer(Modifier.height(6.dp))
        Reading(stats.lastL100?.let { decimal(it) } ?: DASH, stringResource(R.string.home_fuel_last))
        if (refuels.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.home_fuel_empty), color = Palette.Muted, fontSize = 13.sp)
        }
    }
}

// ---------------------------------------------------------------- Dveře

@Composable
fun DoorsTile(car: CarState, modifier: Modifier) {
    Tile(stringResource(R.string.home_doors_title), modifier) {
        val doors = car.doors
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.fillMaxHeight().aspectRatio(0.55f).padding(vertical = 8.dp)) {
                drawCar(doors ?: Doors())
            }
            Spacer(Modifier.width(12.dp))
            Column {
                val text = when {
                    doors == null -> stringResource(R.string.home_doors_unknown)
                    doors.anyOpen -> stringResource(R.string.home_doors_open)
                    else -> stringResource(R.string.home_doors_closed)
                }
                Text(
                    text,
                    color = when {
                        doors == null -> Palette.Muted
                        doors.anyOpen -> Palette.Bad
                        else -> Palette.Good
                    },
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (car.handbrake == true) {
                    Text(stringResource(R.string.home_handbrake_on), color = Palette.Amber, fontSize = 15.sp)
                }
                if (car.warnings.isNotEmpty()) {
                    Text(stringResource(R.string.home_warnings_count, car.warnings.size), color = Palette.Amber, fontSize = 15.sp)
                }
            }
        }
    }
}

/** Auto při pohledu shora; otevřené dveře jsou červené. */
private fun DrawScope.drawCar(doors: Doors) {
    val w = size.width
    val h = size.height
    val bodyLeft = w * 0.2f
    val bodyWidth = w * 0.6f
    val closed = Palette.TileHigh
    val open = Palette.Bad
    drawRoundRect(
        color = Palette.Muted,
        topLeft = Offset(bodyLeft, 0f),
        size = Size(bodyWidth, h),
        cornerRadius = CornerRadius(bodyWidth * 0.35f),
        style = Stroke(3.dp.toPx())
    )
    drawRoundRect(if (doors.bonnet) open else closed, Offset(bodyLeft + bodyWidth * 0.15f, h * 0.04f), Size(bodyWidth * 0.7f, h * 0.16f), CornerRadius(12f))
    drawRoundRect(if (doors.tailgate) open else closed, Offset(bodyLeft + bodyWidth * 0.15f, h * 0.84f), Size(bodyWidth * 0.7f, h * 0.12f), CornerRadius(12f))
    val doorW = w * 0.14f
    val doorH = h * 0.2f
    drawRoundRect(if (doors.frontLeft) open else closed, Offset(0f, h * 0.3f), Size(doorW, doorH), CornerRadius(8f))
    drawRoundRect(if (doors.frontRight) open else closed, Offset(w - doorW, h * 0.3f), Size(doorW, doorH), CornerRadius(8f))
    drawRoundRect(if (doors.rearLeft) open else closed, Offset(0f, h * 0.55f), Size(doorW, doorH), CornerRadius(8f))
    drawRoundRect(if (doors.rearRight) open else closed, Offset(w - doorW, h * 0.55f), Size(doorW, doorH), CornerRadius(8f))
}

// ---------------------------------------------------------------- Dok

@Composable
private fun DockWidget(modifier: Modifier, nav: (Screen) -> Unit) {
    val context = LocalContext.current
    val apps by AppsRepo.apps.collectAsState()
    val favorites by Prefs.favorites.collectAsState()
    Surface(shape = tileShape(), color = Palette.Tile, border = Palette.TileBorder, modifier = modifier) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val shown = favorites.mapNotNull { pkg -> apps.firstOrNull { it.packageName == pkg } }
            if (shown.isEmpty()) {
                Text(
                    stringResource(R.string.home_dock_empty),
                    color = Palette.Muted,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )
            }
            for (app in shown) {
                val icon = app.icon
                Box(
                    Modifier.size(72.dp).clip(CircleShape).background(Color(0x1FFFFFFF)).clickable { AppsRepo.launch(context, app.packageName) },
                    contentAlignment = Alignment.Center
                ) {
                    if (icon != null) {
                        Image(icon, contentDescription = app.label, modifier = Modifier.size(52.dp))
                    } else {
                        Text(app.label.take(2), color = Palette.Text, fontSize = 22.sp)
                    }
                }
            }
            IconButton(onClick = { nav(Screen.APPS) }, modifier = Modifier.size(56.dp)) {
                Icon(if (shown.isEmpty()) Icons.Filled.Apps else Icons.Filled.Edit, stringResource(R.string.dock_edit), Modifier.size(30.dp), tint = Palette.Muted)
            }
        }
    }
}
