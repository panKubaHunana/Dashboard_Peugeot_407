package cz.kuba.dash407.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Jedna jízda v knize jízd. Souřadnice ve stupních WGS-84 (jako GPS). */
data class DriveRecord(
    val id: Long,
    val start: Long,
    val end: Long,
    val startLat: Double,
    val startLon: Double,
    val endLat: Double,
    val endLon: Double,
    val distanceM: Double,
    val movingMs: Long,
    val avgKmh: Double,
    val maxKmh: Double,
    /** Název místa startu a cíle (obec, ulice) – doplní se, když je internet. */
    val from: String? = null,
    val to: String? = null,
    val note: String = ""
)

/**
 * Kniha jízd: všechny uzavřené jízdy v souboru kniha_jizd.json (nejnovější první).
 * Souřadnice startu a cíle jsou připravené pro pozdější mapu pohybu vozidla.
 */
object DriveLog {
    private const val FILE = "kniha_jizd.json"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _entries = MutableStateFlow<List<DriveRecord>>(emptyList())
    val entries: StateFlow<List<DriveRecord>> = _entries
    private var file: File? = null
    private var geocoding = false

    @Synchronized
    fun init(context: Context) {
        if (file != null) return
        val f = File(context.applicationContext.filesDir, FILE)
        file = f
        try {
            if (f.exists()) {
                val a = JSONArray(f.readText())
                _entries.value = (0 until a.length()).mapNotNull { fromJson(a.getJSONObject(it)) }.sortedByDescending { it.start }
            }
        } catch (e: Exception) {
            // Poškozený soubor necháme být (nepřepisujeme ho prázdným seznamem).
            f.renameTo(File(f.parentFile, "$FILE.poskozeny"))
        }
        fillPlaces()
    }

    @Synchronized
    fun add(r: DriveRecord) {
        _entries.value = (listOf(r) + _entries.value.filter { it.id != r.id }).sortedByDescending { it.start }
        persist()
        fillPlaces()
    }

    @Synchronized
    fun update(r: DriveRecord) {
        _entries.value = _entries.value.map { if (it.id == r.id) r else it }
        persist()
    }

    @Synchronized
    fun delete(id: Long) {
        _entries.value = _entries.value.filter { it.id != id }
        persist()
    }

    private fun persist() {
        val f = file ?: return
        val list = _entries.value
        scope.launch {
            synchronized(this@DriveLog) {
                try {
                    val a = JSONArray()
                    for (r in list) a.put(toJson(r))
                    val tmp = File(f.parentFile, "$FILE.tmp")
                    tmp.writeText(a.toString())
                    tmp.renameTo(f)
                } catch (e: Exception) {
                    // Neuloženo; zkusí se při další změně.
                }
            }
        }
    }

    /** Doplní názvy míst (Nominatim, nejvýš jeden dotaz za sekundu). */
    private fun fillPlaces() {
        if (geocoding) return
        geocoding = true
        scope.launch {
            try {
                for (r in _entries.value.filter { it.from == null || it.to == null }.take(40)) {
                    val from = r.from ?: place(r.startLat, r.startLon).also { delay(1100) }
                    val to = r.to ?: place(r.endLat, r.endLon).also { delay(1100) }
                    if (from == null && to == null) break // bez internetu – zkusí se příště
                    val current = _entries.value.firstOrNull { it.id == r.id } ?: continue
                    update(current.copy(from = current.from ?: from, to = current.to ?: to))
                }
            } finally {
                geocoding = false
            }
        }
    }

    private fun place(lat: Double, lon: Double): String? = try {
        val url = String.format(
            Locale.US,
            "https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=%.6f&lon=%.6f&zoom=17&addressdetails=1&accept-language=cs",
            lat, lon
        )
        val a = JSONObject(Net.getText(url)).optJSONObject("address")
        if (a == null) null else {
            val town = listOf("city", "town", "village", "municipality", "hamlet").firstNotNullOfOrNull { a.optString(it).takeIf { s -> s.isNotBlank() } }
            val street = a.optString("road").takeIf { it.isNotBlank() }
            listOfNotNull(town, street).joinToString(", ").ifBlank { null }
        }
    } catch (e: Exception) {
        null
    }

    private fun toJson(r: DriveRecord) = JSONObject()
        .put("id", r.id).put("start", r.start).put("end", r.end)
        .put("startLat", r.startLat).put("startLon", r.startLon).put("endLat", r.endLat).put("endLon", r.endLon)
        .put("distanceM", r.distanceM).put("movingMs", r.movingMs).put("avgKmh", r.avgKmh).put("maxKmh", r.maxKmh)
        .put("from", r.from ?: JSONObject.NULL).put("to", r.to ?: JSONObject.NULL).put("note", r.note)

    private fun fromJson(o: JSONObject): DriveRecord? = try {
        DriveRecord(
            id = o.getLong("id"), start = o.getLong("start"), end = o.getLong("end"),
            startLat = o.getDouble("startLat"), startLon = o.getDouble("startLon"),
            endLat = o.getDouble("endLat"), endLon = o.getDouble("endLon"),
            distanceM = o.optDouble("distanceM", 0.0), movingMs = o.optLong("movingMs", 0L),
            avgKmh = o.optDouble("avgKmh", 0.0), maxKmh = o.optDouble("maxKmh", 0.0),
            from = if (o.isNull("from")) null else o.optString("from"),
            to = if (o.isNull("to")) null else o.optString("to"),
            note = o.optString("note", "")
        )
    } catch (e: Exception) {
        null
    }

    // --- Export do Excelu ----------------------------------------------------------------------

    /**
     * Uloží jízdy jako .xlsx do Stažených souborů (složka Dash407) a vrátí adresu souboru pro sdílení.
     * Volat mimo hlavní vlákno.
     */
    fun exportXlsx(context: Context, list: List<DriveRecord>, name: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, XLSX_MIME)
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/Dash407")
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Do Stažených souborů nejde zapisovat")
        resolver.openOutputStream(uri)?.use { writeXlsx(it, list.sortedBy { r -> r.start }) }
            ?: throw IllegalStateException("Soubor nejde otevřít")
        return uri
    }

    const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    private val HEADERS = listOf(
        "Datum", "Začátek", "Konec", "Odkud", "Kam", "Ujeto km", "V pohybu (h:mm)", "Průměr km/h", "Maximum km/h",
        "Start zeměpisná šířka", "Start zeměpisná délka", "Cíl zeměpisná šířka", "Cíl zeměpisná délka", "Poznámka"
    )
    private val WIDTHS = listOf(11, 9, 9, 28, 28, 10, 15, 12, 14, 20, 20, 20, 20, 30)

    /** Minimální soubor Office Open XML (jeden list) bez knihoven. */
    fun writeXlsx(out: OutputStream, list: List<DriveRecord>) {
        val zone = ZoneId.systemDefault()
        val dateF = DateTimeFormatter.ofPattern("d. M. yyyy")
        val timeF = DateTimeFormatter.ofPattern("H:mm")
        val sheet = StringBuilder()
        sheet.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sheet.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        sheet.append("""<sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>""")
        sheet.append("<cols>")
        WIDTHS.forEachIndexed { i, w -> sheet.append("""<col min="${i + 1}" max="${i + 1}" width="$w" customWidth="1"/>""") }
        sheet.append("</cols><sheetData>")
        fun col(i: Int): String = ('A' + i).toString()
        fun str(row: Int, c: Int, v: String?, style: Int = 0) {
            if (v.isNullOrEmpty()) return
            sheet.append("""<c r="${col(c)}$row" t="inlineStr" s="$style"><is><t xml:space="preserve">${esc(v)}</t></is></c>""")
        }
        fun num(row: Int, c: Int, v: Double, style: Int) {
            sheet.append("""<c r="${col(c)}$row" s="$style"><v>${String.format(Locale.US, "%.6f", v)}</v></c>""")
        }
        sheet.append("""<row r="1">""")
        HEADERS.forEachIndexed { i, h -> str(1, i, h, 1) }
        sheet.append("</row>")
        var row = 2
        for (r in list) {
            val s = Instant.ofEpochMilli(r.start).atZone(zone)
            val e = Instant.ofEpochMilli(r.end).atZone(zone)
            val min = r.movingMs / 60_000
            sheet.append("""<row r="$row">""")
            str(row, 0, dateF.format(s))
            str(row, 1, timeF.format(s))
            str(row, 2, timeF.format(e))
            str(row, 3, r.from)
            str(row, 4, r.to)
            num(row, 5, r.distanceM / 1000.0, 2)
            str(row, 6, String.format(Locale.US, "%d:%02d", min / 60, min % 60))
            num(row, 7, r.avgKmh, 3)
            num(row, 8, r.maxKmh, 3)
            num(row, 9, r.startLat, 4)
            num(row, 10, r.startLon, 4)
            num(row, 11, r.endLat, 4)
            num(row, 12, r.endLon, 4)
            str(row, 13, r.note)
            sheet.append("</row>")
            row++
        }
        if (list.isNotEmpty()) {
            sheet.append("""<row r="$row">""")
            str(row, 0, "Celkem", 1)
            sheet.append("""<c r="F$row" s="5"><f>SUM(F2:F${row - 1})</f></c>""")
            sheet.append("</row>")
        }
        sheet.append("</sheetData></worksheet>")

        ZipOutputStream(out).use { zip ->
            fun put(path: String, text: String) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(text.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            put("[Content_Types].xml", CONTENT_TYPES)
            put("_rels/.rels", ROOT_RELS)
            put("xl/workbook.xml", WORKBOOK)
            put("xl/_rels/workbook.xml.rels", WORKBOOK_RELS)
            put("xl/styles.xml", STYLES)
            put("xl/worksheets/sheet1.xml", sheet.toString())
        }
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private const val CONTENT_TYPES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/></Types>"""
    private const val ROOT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""
    private const val WORKBOOK = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Kniha jízd" sheetId="1" r:id="rId1"/></sheets></workbook>"""
    private const val WORKBOOK_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>"""
    // Styly: 0 běžný, 1 tučné záhlaví, 2 km na desetiny, 3 celé číslo, 4 souřadnice na 6 míst, 5 tučný součet km.
    private const val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><numFmts count="2"><numFmt numFmtId="164" formatCode="0.0"/><numFmt numFmtId="165" formatCode="0.000000"/></numFmts><fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts><fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FFDDEBF7"/><bgColor indexed="64"/></patternFill></fill></fills><borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="6"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/><xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/><xf numFmtId="1" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/><xf numFmtId="165" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/><xf numFmtId="164" fontId="1" fillId="0" borderId="0" xfId="0" applyNumberFormat="1" applyFont="1"/></cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>"""
}
