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
    private const val KEY_DRIVE = "drive_service"
    const val MAX_FAVORITES = 8

    private lateinit var store: SharedPreferences

    private val _favorites = MutableStateFlow<List<String>>(emptyList())
    val favorites: StateFlow<List<String>> = _favorites

    private val _demo = MutableStateFlow(false)
    val demo: StateFlow<Boolean> = _demo

    private val _mapyKey = MutableStateFlow("")
    /** API klíč k REST API Mapy.com (dlaždice, hledání, trasy). Uložený jen v rádiu. */
    val mapyKey: StateFlow<String> = _mapyKey

    private val _home = MutableStateFlow("")
    val homeAddress: StateFlow<String> = _home

    private val _work = MutableStateFlow("")
    val workAddress: StateFlow<String> = _work

    private val _split = MutableStateFlow(false)
    val splitScreen: StateFlow<Boolean> = _split

    private val _drive = MutableStateFlow(true)
    val driveService: StateFlow<Boolean> = _drive

    fun init(context: Context) {
        if (::store.isInitialized) return
        store = context.applicationContext.getSharedPreferences("dash407", Context.MODE_PRIVATE)
        _favorites.value = (store.getString(KEY_FAVORITES, "") ?: "").split('|').filter { it.isNotBlank() }
        _demo.value = store.getBoolean(KEY_DEMO, false)
        _mapyKey.value = store.getString(KEY_MAPY, "") ?: ""
        _home.value = store.getString(KEY_HOME, "") ?: ""
        _work.value = store.getString(KEY_WORK, "") ?: ""
        _split.value = store.getBoolean(KEY_SPLIT, false)
        _drive.value = store.getBoolean(KEY_DRIVE, true)
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
        _mapyKey.value = value.trim()
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

    fun setDriveService(value: Boolean) {
        _drive.value = value
        store.edit().putBoolean(KEY_DRIVE, value).apply()
    }
}
