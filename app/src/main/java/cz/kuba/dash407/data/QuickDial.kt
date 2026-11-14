package cz.kuba.dash407.data

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.widget.Toast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

data class DialContact(val name: String, val number: String)

/**
 * Rychlá volba: pět vlastních kontaktů (uložených jen v rádiu), nebo oblíbené kontakty
 * z databáze kontaktů v rádiu (hvězdička). Číslo se volá přes systém, takže záleží, co v rádiu
 * volání obsluhuje (SIM, nebo aplikace pro Bluetooth telefon).
 */
object QuickDial {
    const val SLOTS = 5
    private const val KEY = "quickdial_v1"

    private val _mode = MutableStateFlow("mine")
    /** "mine" = pět vlastních kontaktů, "starred" = oblíbené kontakty. */
    val mode: StateFlow<String> = _mode
    private val _slots = MutableStateFlow<List<DialContact?>>(List(SLOTS) { null })
    val slots: StateFlow<List<DialContact?>> = _slots
    private var loaded = false

    fun init() {
        if (loaded) return
        loaded = true
        val raw = try { Prefs.shared().getString(KEY, null) } catch (e: Exception) { null } ?: return
        try {
            val o = JSONObject(raw)
            _mode.value = o.optString("mode", "mine").let { if (it == "starred") it else "mine" }
            val a = o.optJSONArray("slots") ?: JSONArray()
            _slots.value = List(SLOTS) { i ->
                a.optJSONObject(i)?.let { c ->
                    val n = c.optString("number")
                    if (n.isBlank()) null else DialContact(c.optString("name").ifBlank { n }, n)
                }
            }
        } catch (e: Exception) {
            // Poškozené nastavení: prázdné sloty.
        }
    }

    private fun save() {
        val a = JSONArray()
        for (c in _slots.value) a.put(if (c == null) JSONObject() else JSONObject().put("name", c.name).put("number", c.number))
        try {
            Prefs.shared().edit().putString(KEY, JSONObject().put("mode", _mode.value).put("slots", a).toString()).apply()
        } catch (e: Exception) {
            // Neuloženo.
        }
    }

    fun setMode(m: String) { _mode.value = m; save() }

    fun setSlot(index: Int, contact: DialContact?) {
        if (index !in 0 until SLOTS) return
        _slots.value = _slots.value.toMutableList().also { it[index] = contact }
        save()
    }

    fun canRead(context: Context) =
        context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    fun canCall(context: Context) =
        context.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED

    /** Oblíbené kontakty (s hvězdičkou) a jejich hlavní číslo, podle abecedy. Volat mimo hlavní vlákno. */
    fun starred(context: Context): List<DialContact> {
        if (!canRead(context)) return emptyList()
        val byContact = LinkedHashMap<Long, Pair<DialContact, Boolean>>()
        try {
            context.contentResolver.query(
                Phone.CONTENT_URI,
                arrayOf(Phone.CONTACT_ID, Phone.DISPLAY_NAME, Phone.NUMBER, Phone.IS_PRIMARY),
                ContactsContract.Contacts.STARRED + "=1", null,
                Phone.DISPLAY_NAME + " COLLATE LOCALIZED ASC"
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val number = c.getString(2) ?: continue
                    val primary = c.getInt(3) != 0
                    val old = byContact[id]
                    if (old == null || (primary && !old.second)) {
                        byContact[id] = DialContact(c.getString(1) ?: number, number) to primary
                    }
                }
            }
        } catch (e: Exception) {
            return emptyList()
        }
        return byContact.values.map { it.first }
    }

    /** Přečte jméno a číslo z výsledku výběru kontaktu (bez nutnosti oprávnění číst všechny kontakty). */
    fun fromPick(context: Context, uri: Uri): DialContact? = try {
        context.contentResolver.query(uri, arrayOf(Phone.DISPLAY_NAME, Phone.NUMBER), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val number = c.getString(1)
                if (number.isNullOrBlank()) null else DialContact(c.getString(0) ?: number, number)
            } else null
        }
    } catch (e: Exception) {
        null
    }

    /** Otevře aplikaci s kontakty v rádiu. */
    fun openContacts(context: Context) {
        val tries = listOf(
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CONTACTS),
            Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI)
        )
        for (i in tries) {
            try {
                context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (e: Exception) {
                // Zkusí další způsob.
            }
        }
        Toast.makeText(context, "Rádio nemá aplikaci s kontakty", Toast.LENGTH_LONG).show()
    }

    fun call(context: Context, number: String, direct: Boolean) {
        val uri = Uri.parse("tel:" + Uri.encode(number))
        val action = if (direct && canCall(context)) Intent.ACTION_CALL else Intent.ACTION_DIAL
        try {
            context.startActivity(Intent(action, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Toast.makeText(context, "Rádio nemá aplikaci pro volání", Toast.LENGTH_LONG).show()
        }
    }
}
