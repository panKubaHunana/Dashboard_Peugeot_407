package cz.kuba.dash407.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Panely, které se otevírají přes plochy. */
enum class Panel { APPS, SETTINGS, FUEL, CAR, APPEARANCE, DESTINATION, PICKER, PAGES, OBD, SERVICE }

/** Společný stav uživatelského rozhraní, aby ho widgety mohly ovládat. */
object UiBus {
    var panel by mutableStateOf<Panel?>(null)
    var editing by mutableStateOf(false)
    var slot by mutableStateOf(Slot.CENTER)
    var selected by mutableStateOf<String?>(null)
    var showGrid by mutableStateOf(true)
    /** 0 = widgety, 1 = aplikace do doku. */
    var pickerTab by mutableStateOf(0)

    fun open(p: Panel) {
        panel = p
    }

    fun close() {
        panel = null
    }

    /** Tlačítko Domů: zavřít vše a zpět na střední plochu. */
    fun home() {
        panel = null
        editing = false
        selected = null
        slot = Slot.CENTER
    }
}
