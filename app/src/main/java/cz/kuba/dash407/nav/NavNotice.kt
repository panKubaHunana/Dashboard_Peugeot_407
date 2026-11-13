package cz.kuba.dash407.nav

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Poslední oznámení navigace z aplikace Mapy.com (typicky vzdálenost a ulice dalšího manévru). */
data class NavNoticeState(
    val key: String,
    val title: String?,
    val text: String?,
    val sub: String?,
    val icon: ImageBitmap?,
    val at: Long
)

object NavNotice {
    private val _state = MutableStateFlow<NavNoticeState?>(null)
    val state: StateFlow<NavNoticeState?> = _state

    fun set(s: NavNoticeState) {
        _state.value = s
    }

    fun clear(key: String) {
        if (_state.value?.key == key) _state.value = null
    }
}
