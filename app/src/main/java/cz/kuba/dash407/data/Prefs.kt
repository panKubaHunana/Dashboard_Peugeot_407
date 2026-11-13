package cz.kuba.dash407.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Drobná nastavení: oblíbené aplikace v doku a ukázkový režim. */
object Prefs {
    private const val KEY_FAVORITES = "favorites"
    private const val KEY_DEMO = "demo"
    const val MAX_FAVORITES = 5

    private lateinit var store: SharedPreferences

    private val _favorites = MutableStateFlow<List<String>>(emptyList())
    val favorites: StateFlow<List<String>> = _favorites

    private val _demo = MutableStateFlow(false)
    val demo: StateFlow<Boolean> = _demo

    fun init(context: Context) {
        if (::store.isInitialized) return
        store = context.applicationContext.getSharedPreferences("dash407", Context.MODE_PRIVATE)
        _favorites.value = (store.getString(KEY_FAVORITES, "") ?: "").split('|').filter { it.isNotBlank() }
        _demo.value = store.getBoolean(KEY_DEMO, false)
    }

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
}
