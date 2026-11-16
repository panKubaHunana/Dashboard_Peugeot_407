package cz.kuba.dash407.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Drobná nastavení: dok, ukázkový režim, Mapy.com, adresy, jízda na pozadí. */
object Prefs {
    private const val KEY_FAVORITES = "favorites"
    private const val KEY_DEMO = "demo"
    private const val KEY_MAPY = "mapy_key"
    private const val KEY_HOME = "addr_home"
    private const val KEY_WORK = "addr_work"
    private const val KEY_SPLIT = "split_screen"
    private const val KEY_HIDE_TOP = "hide_top_bar"
    private const val KEY_HIDE_BOTTOM = "hide_bottom_bar"
    private const val KEY_DRIVE = "drive_service"
    private const val KEY_NAV_IN_APP = "nav_in_app"
    private const val KEY_NAV_VOICE = "nav_voice"
    private const val KEY_NAV_HEADING = "nav_heading_up"
    private const val KEY_NAV_TARGET = "nav_target"
    private const val KEY_GOOGLE = "google_key"
    private const val KEY_MUSIC = "music_app"
    const val YT_MUSIC = "com.google.android.apps.youtube.music"
    const val SPOTIFY = "com.spotify.music"
    const val MAX_FAVORITES = 8

    private lateinit var store: SharedPreferences

    private val _favorites = MutableStateFlow<List<String>>(emptyList())
    val favorites: StateFlow<List<String>> = _favorites

    private val _demo = MutableStateFlow(false)
    val demo: StateFlow<Boolean> = _demo

    private val _mapyKey = MutableStateFlow("")
    /**
     * API klíč k REST API Mapy.com, který se opravdu používá (dlaždice, hledání, trasy):
     * vlastní klíč z Nastavení, jinak výchozí klíč z GitHub Secrets vložený při sestavení.
     */
    val mapyKey: StateFlow<String> = _mapyKey

    private val _mapyKeyOwn = MutableStateFlow("")
    /** Klíč zadaný ručně v Nastavení (prázdné = použije se výchozí). */
    val mapyKeyOwn: StateFlow<String> = _mapyKeyOwn

    /** Aplikace má výchozí klíč z GitHub Secrets. */
    val hasBuiltInMapyKey: Boolean get() = cz.kuba.dash407.BuildConfig.MAPY_KEY.isNotBlank()

    private val _home = MutableStateFlow("")
    val homeAddress: StateFlow<String> = _home

    private val _work = MutableStateFlow("")
    val workAddress: StateFlow<String> = _work

    private val _split = MutableStateFlow(false)
    val splitScreen: StateFlow<Boolean> = _split

    private val _hideTop = MutableStateFlow(false)
    /** Zkušebně: skrýt horní lištu rádia (stavový řádek systému), dokud je Dash 407 vpředu. */
    val hideTopBar: StateFlow<Boolean> = _hideTop

    private val _hideBottom = MutableStateFlow(false)
    /** Zkušebně: skrýt spodní lištu rádia (navigační lišta systému s klimatizací). */
    val hideBottomBar: StateFlow<Boolean> = _hideBottom

    private val _drive = MutableStateFlow(true)
    val driveService: StateFlow<Boolean> = _drive

    private val _navInApp = MutableStateFlow(true)
    /** Navigovat přímo v Dash 407 (true), nebo předat aplikaci Mapy.com (false). */
    val navInApp: StateFlow<Boolean> = _navInApp

    private val _navVoice = MutableStateFlow(true)
    val navVoice: StateFlow<Boolean> = _navVoice

    private val _navHeadingUp = MutableStateFlow(true)
    val navHeadingUp: StateFlow<Boolean> = _navHeadingUp

    fun setNavInApp(v: Boolean) { setNavTarget(if (v) "dash" else "mapy") }

    private val _navTarget = MutableStateFlow("dash")
    /** Kde se navigace spustí: dash, mapy, google, organic, osmand. */
    val navTarget: StateFlow<String> = _navTarget

    fun setNavTarget(v: String) {
        _navTarget.value = v
        _navInApp.value = v == "dash"
        store.edit().putString(KEY_NAV_TARGET, v).putBoolean(KEY_NAV_IN_APP, v == "dash").apply()
    }

    private val _musicApp = MutableStateFlow(YT_MUSIC)
    /** Hudební aplikace pro widget Právě hraje; prázdné = jakákoli. Výchozí je YouTube Music. */
    val musicApp: StateFlow<String> = _musicApp

    fun setMusicApp(v: String) {
        _musicApp.value = v
        store.edit().putString(KEY_MUSIC, v).apply()
    }

    private val _googleKey = MutableStateFlow("")
    /** API klíč Google Maps Platform (Map Tiles API) pro Google podklady. */
    val googleKey: StateFlow<String> = _googleKey

    fun setGoogleKey(v: String) {
        _googleKey.value = v.trim()
        store.edit().putString(KEY_GOOGLE, v.trim()).apply()
    }
    fun setNavVoice(v: Boolean) { _navVoice.value = v; store.edit().putBoolean(KEY_NAV_VOICE, v).apply() }
    fun setNavHeadingUp(v: Boolean) { _navHeadingUp.value = v; store.edit().putBoolean(KEY_NAV_HEADING, v).apply() }

    fun init(context: Context) {
        if (::store.isInitialized) return
        store = context.applicationContext.getSharedPreferences("dash407", Context.MODE_PRIVATE)
        _favorites.value = (store.getString(KEY_FAVORITES, "") ?: "").split('|').filter { it.isNotBlank() }
        _demo.value = store.getBoolean(KEY_DEMO, false)
        // 3.0: starý (uniklý) klíč uložený v rádiu se jednorázově smaže, když aplikace má nový z GitHub Secrets.
        if (!store.getBoolean("mapy_key_v3", false)) {
            val e = store.edit().putBoolean("mapy_key_v3", true)
            if (hasBuiltInMapyKey) e.remove(KEY_MAPY)
            e.apply()
        }
        _mapyKeyOwn.value = store.getString(KEY_MAPY, "") ?: ""
        _mapyKey.value = _mapyKeyOwn.value.ifBlank { cz.kuba.dash407.BuildConfig.MAPY_KEY }
        _home.value = store.getString(KEY_HOME, "") ?: ""
        _work.value = store.getString(KEY_WORK, "") ?: ""
        _split.value = store.getBoolean(KEY_SPLIT, false)
        _hideTop.value = store.getBoolean(KEY_HIDE_TOP, false)
        _hideBottom.value = store.getBoolean(KEY_HIDE_BOTTOM, false)
        _drive.value = store.getBoolean(KEY_DRIVE, true)
        _navInApp.value = store.getBoolean(KEY_NAV_IN_APP, true)
        _navTarget.value = store.getString(KEY_NAV_TARGET, null) ?: if (_navInApp.value) "dash" else "mapy"
        _navInApp.value = _navTarget.value == "dash"
        _googleKey.value = store.getString(KEY_GOOGLE, "") ?: ""
        _musicApp.value = store.getString(KEY_MUSIC, YT_MUSIC) ?: YT_MUSIC
        _navVoice.value = store.getBoolean(KEY_NAV_VOICE, true)
        _navHeadingUp.value = store.getBoolean(KEY_NAV_HEADING, true)
    }

    /** Společné úložiště i pro rozložení ploch, vzhled a jízdu. */
    fun shared(): SharedPreferences = store

    /** Přidá nebo odebere aplikaci z doku; vrací false, když je dok plný. */
    fun toggleFavorite(packageName: String): Boolean {
        val now = _favorites.value
        val next = when {
            packageName in now -> now - packageName
            now.size >= MAX_FAVORITES -> return false
            else -> now + packageName
        }
        _favorites.value = next
        store.edit().putString(KEY_FAVORITES, next.joinToString("|")).apply()
        return true
    }

    fun setDemo(value: Boolean) {
        _demo.value = value
        store.edit().putBoolean(KEY_DEMO, value).apply()
    }

    fun setMapyKey(value: String) {
        _mapyKeyOwn.value = value.trim()
        _mapyKey.value = value.trim().ifBlank { cz.kuba.dash407.BuildConfig.MAPY_KEY }
        store.edit().putString(KEY_MAPY, value.trim()).apply()
    }


    fun setHomeAddress(value: String) {
        _home.value = value
        store.edit().putString(KEY_HOME, value).apply()
    }

    fun setWorkAddress(value: String) {
        _work.value = value
        store.edit().putString(KEY_WORK, value).apply()
    }

    fun setSplitScreen(value: Boolean) {
        _split.value = value
        store.edit().putBoolean(KEY_SPLIT, value).apply()
    }

    fun setHideTopBar(value: Boolean) {
        _hideTop.value = value
        store.edit().putBoolean(KEY_HIDE_TOP, value).apply()
    }

    fun setHideBottomBar(value: Boolean) {
        _hideBottom.value = value
        store.edit().putBoolean(KEY_HIDE_BOTTOM, value).apply()
    }

    fun setDriveService(value: Boolean) {
        _drive.value = value
        store.edit().putBoolean(KEY_DRIVE, value).apply()
    }
}
