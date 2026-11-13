package cz.kuba.dash407.map

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import cz.kuba.dash407.data.Gps
import cz.kuba.dash407.data.MapSettings
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.File

/**
 * Mapové dlaždice z REST API Mapy.com:
 * https://api.mapy.com/v1/maptiles/{mapset}/256/{z}/{x}/{y}?apikey=…
 * Název zdroje obsahuje jen sadu map, klíč ne – změna klíče tak nemaže mezipaměť.
 */
class MapyTileSource(private val mapset: String, private val apiKey: String) : OnlineTileSourceBase(
    "MapyCom-$mapset", 0, 19, 256, ".png",
    arrayOf("https://api.mapy.com/v1/maptiles/"),
    "© Seznam.cz a.s. a další"
) {
    override fun getTileURLString(pMapTileIndex: Long): String =
        baseUrl + mapset + "/256/" +
            MapTileIndex.getZoom(pMapTileIndex) + "/" +
            MapTileIndex.getX(pMapTileIndex) + "/" +
            MapTileIndex.getY(pMapTileIndex) +
            "?apikey=" + apiKey
}

/** Všechny mapy, které jsou právě na obrazovce (kvůli onResume/onPause). */
object MapHub {
    private val maps = LinkedHashSet<MapyMapController>()
    private var resumed = false

    @Synchronized
    fun register(map: MapyMapController) {
        maps.add(map)
        if (resumed) map.resume()
    }

    @Synchronized
    fun unregister(map: MapyMapController) {
        maps.remove(map)
        map.pause()
    }

    @Synchronized
    fun resume() {
        resumed = true
        maps.forEach { it.resume() }
    }

    @Synchronized
    fun pause() {
        resumed = false
        maps.forEach { it.pause() }
    }

    /** Nastavení knihovny map: vlastní složka mezipaměti a identifikace aplikace. */
    fun configure(context: Context) {
        val app = context.applicationContext
        val config = Configuration.getInstance()
        config.load(app, app.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        config.userAgentValue = app.packageName
        val base = File(app.filesDir, "osmdroid")
        config.osmdroidBasePath = base
        config.osmdroidTileCache = File(base, "tiles")
    }
}

/** Jedna mapa (osmdroid MapView) s dlaždicemi Mapy.com a značkou polohy. */
class MapyMapController(context: Context) {
    val view: MapView = MapView(context)
    private var myLocation: MyLocationNewOverlay? = null
    private var appliedSource: String? = null
    private var appliedDark: Boolean? = null
    private var appliedFollow: Boolean? = null
    private var centered = false

    init {
        view.setMultiTouchControls(true)
        view.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        view.isTilesScaledToDpi = true
        view.isHorizontalMapRepetitionEnabled = false
        view.isVerticalMapRepetitionEnabled = false
        view.minZoomLevel = 3.0
        view.maxZoomLevel = 19.0
        view.setBackgroundColor(Color.rgb(12, 18, 32))
        view.overlayManager.tilesOverlay.loadingBackgroundColor = Color.rgb(12, 18, 32)
        view.overlayManager.tilesOverlay.loadingLineColor = Color.rgb(24, 32, 50)
        // Výchozí střed: Hradec Králové, než přijde poloha.
        view.controller.setZoom(14.0)
        view.controller.setCenter(GeoPoint(50.2092, 15.8328))
    }

    /** Použije nastavení; volá se při každé změně a při prvním vykreslení. */
    @SuppressLint("MissingPermission")
    fun apply(settings: MapSettings, locationAllowed: Boolean) {
        val sourceKey = settings.mapset + "|" + settings.apiKey
        if (sourceKey != appliedSource && settings.apiKey.isNotBlank()) {
            view.setTileSource(MapyTileSource(settings.mapset, settings.apiKey))
            appliedSource = sourceKey
        }
        if (appliedDark != settings.dark) {
            view.overlayManager.tilesOverlay.setColorFilter(if (settings.dark) darkFilter() else null)
            appliedDark = settings.dark
        }
        if (locationAllowed && myLocation == null) {
            val overlay = MyLocationNewOverlay(GpsMyLocationProvider(view.context), view)
            overlay.enableMyLocation()
            overlay.setDrawAccuracyEnabled(true)
            view.overlays.add(overlay)
            myLocation = overlay
        }
        val overlay = myLocation
        if (overlay != null && appliedFollow != settings.follow) {
            if (settings.follow) overlay.enableFollowLocation() else overlay.disableFollowLocation()
            appliedFollow = settings.follow
        }
        if (!centered) {
            val loc = Gps.location.value
            if (loc != null) {
                view.controller.setZoom(settings.zoom)
                view.controller.setCenter(GeoPoint(loc.latitude, loc.longitude))
                centered = true
            }
        }
    }

    /** Vycentruje mapu na aktuální polohu a zapne sledování. */
    fun recenter(zoom: Double) {
        val loc = Gps.location.value ?: return
        view.controller.animateTo(GeoPoint(loc.latitude, loc.longitude), zoom, 600L)
        myLocation?.enableFollowLocation()
    }

    fun resume() = view.onResume()

    fun pause() = view.onPause()

    fun destroy() {
        myLocation?.disableMyLocation()
        view.onDetach()
    }

    companion object {
        /**
         * Noční vzhled: převrácení barev, otočení odstínu o 180° (zelená zůstane
         * zelenou) a mírné ztmavení do modra.
         */
        fun darkFilter(): ColorMatrixColorFilter {
            val invert = ColorMatrix(
                floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            val hue180 = ColorMatrix(
                floatArrayOf(
                    -0.574f, 1.430f, 0.144f, 0f, 0f,
                    0.426f, 0.430f, 0.144f, 0f, 0f,
                    0.426f, 1.430f, -0.856f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            val tint = ColorMatrix().apply { setScale(0.78f, 0.84f, 0.98f, 1f) }
            invert.postConcat(hue180)
            invert.postConcat(tint)
            return ColorMatrixColorFilter(invert)
        }
    }
}
