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
    val note: String = "",
    /** Odhad stavu tachometru na začátku a na konci jízdy. */
    val odoStart: Int? = null,
    val odoEnd: Int? = null
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
        DriveTracks.delete(id)
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

    /** Znovu zkusit doplnit názvy míst (po připojení k internetu). */
    fun retryPlaces() = fillPlaces()

    /** Doplní názvy míst (Nominatim, nejvýš jeden dotaz za sekundu). */
    private fun fillPlaces() {
        if (geocoding) return
        geocoding = true
        scope.launch {
            try {
                for (r in _entries.value.filter { it.from == null || it.to == null }.take(40)) {
                    val from = r.from ?: place(r.startLat, r.startLon).also { delay(1100) }
                    val to = r.to ?: place(r.endLat, r.endLon).also { delay(1100) }
                    if (from == null && to == null) {
                        // Bez internetu: počká se ve frontě, až se rádio připojí.
                        NetQueue.add(NetQueue.Job("places"))
                        break
                    }
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
        .put("odoStart", r.odoStart ?: JSONObject.NULL).put("odoEnd", r.odoEnd ?: JSONObject.NULL)

    private fun fromJson(o: JSONObject): DriveRecord? = try {
        DriveRecord(
            id = o.getLong("id"), start = o.getLong("start"), end = o.getLong("end"),
            startLat = o.getDouble("startLat"), startLon = o.getDouble("startLon"),
            endLat = o.getDouble("endLat"), endLon = o.getDouble("endLon"),
            distanceM = o.optDouble("distanceM", 0.0), movingMs = o.optLong("movingMs", 0L),
            avgKmh = o.optDouble("avgKmh", 0.0), maxKmh = o.optDouble("maxKmh", 0.0),
            from = if (o.isNull("from")) null else o.optString("from"),
            to = if (o.isNull("to")) null else o.optString("to"),
            note = o.optString("note", ""),
            odoStart = if (o.isNull("odoStart")) null else o.optInt("odoStart"),
            odoEnd = if (o.isNull("odoEnd")) null else o.optInt("odoEnd")
        )
    } catch (e: Exception) {
        null
    }

    // --- Palivo a náklady (propojení s Tankováním) -------------------------------------------

    /** Odhad paliva a ceny jízdy: průměr z knihy tankování a cena za litr z posledního tankování před jízdou. */
    fun fuelFor(r: DriveRecord, refuels: List<Refuel>, avgL100: Double?): Pair<Double?, Double?> {
        val litres = avgL100?.let { r.distanceM / 1000.0 * it / 100.0 }
        val before = refuels.filter { it.at <= r.start && it.litres > 0 }.maxByOrNull { it.at }
            ?: refuels.filter { it.litres > 0 }.minByOrNull { it.at }
        val perLitre = before?.let { it.price / it.litres }?.takeIf { it > 0 }
        return litres to (if (litres != null && perLitre != null) litres * perLitre else null)
    }

    // --- Statistika ----------------------------------------------------------------------------

    enum class Period(val label: String) { DAY("Den"), WEEK("Týden"), MONTH("Měsíc"), YEAR("Rok") }

    /** Součty za jedno období: počet jízd, km, čas jízdy (od startu do cíle) a čas v pohybu. */
    data class PeriodStat(val key: String, val label: String, val drives: Int, val distanceM: Double, val driveMs: Long, val movingMs: Long)

    fun stats(list: List<DriveRecord>, period: Period): List<PeriodStat> {
        val zone = ZoneId.systemDefault()
        val wf = java.time.temporal.WeekFields.ISO
        val months = listOf("leden", "únor", "březen", "duben", "květen", "červen", "červenec", "srpen", "září", "říjen", "listopad", "prosinec")
        return list.groupBy { r ->
            val d = Instant.ofEpochMilli(r.start).atZone(zone).toLocalDate()
            when (period) {
                Period.DAY -> d.toString()
                Period.WEEK -> String.format(Locale.US, "%04d-%02d", d.get(wf.weekBasedYear()), d.get(wf.weekOfWeekBasedYear()))
                Period.MONTH -> String.format(Locale.US, "%04d-%02d", d.year, d.monthValue)
                Period.YEAR -> d.year.toString()
            }
        }.map { (key, rs) ->
            val d = Instant.ofEpochMilli(rs.minOf { it.start }).atZone(zone).toLocalDate()
            val label = when (period) {
                Period.DAY -> DateTimeFormatter.ofPattern("EEEE d. M. yyyy", Locale("cs", "CZ")).format(d).replaceFirstChar { it.uppercase() }
                Period.WEEK -> {
                    val mon = d.with(java.time.DayOfWeek.MONDAY)
                    val sun = mon.plusDays(6)
                    "${d.get(wf.weekOfWeekBasedYear())}. týden (${mon.dayOfMonth}. ${mon.monthValue}. – ${sun.dayOfMonth}. ${sun.monthValue}. ${sun.year})"
                }
                Period.MONTH -> months[d.monthValue - 1].replaceFirstChar { it.uppercase() } + " ${d.year}"
                Period.YEAR -> d.year.toString()
            }
            PeriodStat(key, label, rs.size, rs.sumOf { it.distanceM }, rs.sumOf { (it.end - it.start).coerceAtLeast(0) }, rs.sumOf { it.movingMs })
        }.sortedByDescending { it.key }
    }

    // --- Export do Excelu ----------------------------------------------------------------------

    /**
     * Uloží jízdy jako .xlsx do Stažených souborů (složka Dash407) a vrátí adresu souboru pro sdílení.
     * Listy: Kniha jízd, Tankování (za stejné období) a Statistika (dny, týdny, měsíce, roky).
     * Volat mimo hlavní vlákno.
     */
    fun exportXlsx(context: Context, list: List<DriveRecord>, refuels: List<Refuel>, name: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, XLSX_MIME)
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/Dash407")
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Do Stažených souborů nejde zapisovat")
        resolver.openOutputStream(uri)?.use { writeXlsx(it, list.sortedBy { r -> r.start }, refuels.sortedBy { r -> r.at }) }
            ?: throw IllegalStateException("Soubor nejde otevřít")
        return uri
    }

    const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    /** Jeden list sešitu: řádky buněk (text, číslo nebo vzorec) se stylem. */
    private class Sheet(val name: String, val widths: List<Int>) {
        val rows = StringBuilder()
        private var row = 0
        private var cells = StringBuilder()
        private var col = 0
        val count: Int get() = row
        private fun ref() = ('A' + col).toString() + row
        fun begin() { row++; col = 0; cells = StringBuilder() }
        fun text(v: String?, style: Int = 0) {
            if (!v.isNullOrEmpty()) cells.append("""<c r="${ref()}" t="inlineStr" s="$style"><is><t xml:space="preserve">${esc(v)}</t></is></c>""")
            col++
        }
        fun num(v: Double?, style: Int) {
            if (v != null) cells.append("""<c r="${ref()}" s="$style"><v>${String.format(Locale.US, "%.6f", v)}</v></c>""")
            col++
        }
        fun formula(f: String, style: Int) {
            cells.append("""<c r="${ref()}" s="$style"><f>$f</f></c>""")
            col++
        }
        fun skip() { col++ }
        fun end() { rows.append("""<row r="$row">""").append(cells).append("</row>") }
        fun header(vararg h: String) { begin(); for (x in h) text(x, 1); end() }
        fun xml(): String {
            val sb = StringBuilder()
            sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
            sb.append("""<sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>""")
            sb.append("<cols>")
            widths.forEachIndexed { i, w -> sb.append("""<col min="${i + 1}" max="${i + 1}" width="$w" customWidth="1"/>""") }
            sb.append("</cols><sheetData>").append(rows).append("</sheetData></worksheet>")
            return sb.toString()
        }
    }

    private fun hm(ms: Long): String {
        val min = ms / 60_000
        return String.format(Locale.US, "%d:%02d", min / 60, min % 60)
    }

    /** Sešit Office Open XML bez knihoven. Styly: 0 běžný, 1 záhlaví, 2 0,0, 3 celé, 4 souřadnice, 5 tučný součet 0,0, 6 tučný celé, 7 Kč 0,00. */
    fun writeXlsx(out: OutputStream, list: List<DriveRecord>, refuels: List<Refuel>) {
        val zone = ZoneId.systemDefault()
        val dateF = DateTimeFormatter.ofPattern("d. M. yyyy")
        val timeF = DateTimeFormatter.ofPattern("H:mm")
        val avg = FuelLog.stats(FuelLog.entries.value).averageL100
        val allRefuels = FuelLog.entries.value

        val drives = Sheet("Kniha jízd", listOf(11, 8, 8, 28, 28, 12, 12, 10, 12, 11, 11, 10, 11, 13, 13, 13, 13, 30))
        drives.header(
            "Datum", "Začátek", "Konec", "Odkud", "Kam", "Tachometr začátek", "Tachometr konec", "Ujeto km", "Čas jízdy (h:mm)",
            "V pohybu (h:mm)", "Průměr km/h", "Max km/h", "Palivo l (odhad)", "Náklady Kč (odhad)",
            "Start šířka", "Start délka", "Cíl šířka", "Cíl délka", "Poznámka"
        )
        for (r in list) {
            val s = Instant.ofEpochMilli(r.start).atZone(zone)
            val e = Instant.ofEpochMilli(r.end).atZone(zone)
            val (litres, cost) = fuelFor(r, allRefuels, avg)
            drives.begin()
            drives.text(dateF.format(s)); drives.text(timeF.format(s)); drives.text(timeF.format(e))
            drives.text(r.from); drives.text(r.to)
            drives.num(r.odoStart?.toDouble(), 3); drives.num(r.odoEnd?.toDouble(), 3)
            drives.num(r.distanceM / 1000.0, 2); drives.text(hm(r.end - r.start)); drives.text(hm(r.movingMs))
            drives.num(r.avgKmh, 3); drives.num(r.maxKmh, 3)
            drives.num(litres, 2); drives.num(cost, 7)
            drives.num(r.startLat, 4); drives.num(r.startLon, 4); drives.num(r.endLat, 4); drives.num(r.endLon, 4)
            drives.text(r.note)
            drives.end()
        }
        if (list.isNotEmpty()) {
            val last = drives.count
            drives.begin()
            drives.text("Celkem", 1)
            repeat(6) { drives.skip() }
            drives.formula("SUM(H2:H$last)", 5)
            repeat(4) { drives.skip() }
            drives.formula("SUM(M2:M$last)", 5)
            drives.formula("SUM(N2:N$last)", 7)
            drives.end()
        }

        val fuel = Sheet("Tankování", listOf(11, 8, 28, 12, 10, 11, 10, 11, 12, 13, 13))
        fuel.header("Datum", "Čas", "Čerpací stanice", "Tachometr", "Litry", "Cena Kč", "Kč/l", "Plná nádrž", "Spotřeba l/100", "Šířka", "Délka")
        val perFill = FuelLog.consumptions(allRefuels)
        for (f in refuels) {
            val t = Instant.ofEpochMilli(f.at).atZone(zone)
            fuel.begin()
            fuel.text(dateF.format(t)); fuel.text(timeF.format(t)); fuel.text(f.station)
            fuel.num(f.odometerKm.toDouble(), 3); fuel.num(f.litres, 2); fuel.num(f.price, 7)
            fuel.num(if (f.litres > 0) f.price / f.litres else null, 7)
            fuel.text(if (f.full) "ano" else "ne"); fuel.num(perFill[f.id], 2)
            fuel.num(f.lat, 4); fuel.num(f.lon, 4)
            fuel.end()
        }

        val stat = Sheet("Statistika", listOf(10, 40, 8, 12, 15, 15))
        stat.header("Období", "", "Jízd", "Ujeto km", "Čas jízdy (h:mm)", "V pohybu (h:mm)")
        for (p in Period.values()) {
            for (x in stats(list, p)) {
                stat.begin()
                stat.text(p.label); stat.text(x.label); stat.num(x.drives.toDouble(), 3); stat.num(x.distanceM / 1000.0, 2)
                stat.text(hm(x.driveMs)); stat.text(hm(x.movingMs))
                stat.end()
            }
        }

        val sheets = listOf(drives, fuel, stat)
        ZipOutputStream(out).use { zip ->
            fun put(path: String, text: String) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(text.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            put("[Content_Types].xml", CT_HEAD + sheets.indices.joinToString("") {
                """<Override PartName="/xl/worksheets/sheet${it + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>"""
            } + "</Types>")
            put("_rels/.rels", ROOT_RELS)
            put("xl/workbook.xml", WB_HEAD + sheets.mapIndexed { i, sh ->
                """<sheet name="${esc(sh.name)}" sheetId="${i + 1}" r:id="rId${i + 1}"/>"""
            }.joinToString("") + "</sheets></workbook>")
            put("xl/_rels/workbook.xml.rels", RELS_HEAD + sheets.indices.joinToString("") {
                """<Relationship Id="rId${it + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${it + 1}.xml"/>"""
            } + """<Relationship Id="rId${sheets.size + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>""")
            put("xl/styles.xml", STYLES)
            sheets.forEachIndexed { i, sh -> put("xl/worksheets/sheet${i + 1}.xml", sh.xml()) }
        }
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private const val CT_HEAD = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>"""
    private const val ROOT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""
    private const val WB_HEAD = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>"""
    private const val RELS_HEAD = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">"""
    private const val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><numFmts count="3"><numFmt numFmtId="164" formatCode="0.0"/><numFmt numFmtId="165" formatCode="0.000000"/><numFmt numFmtId="166" formatCode="#,##0.00"/></numFmts><fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts><fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FFDDEBF7"/><bgColor indexed="64"/></patternFill></fill></fills><borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="8"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/><xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/><xf numFmtId="1" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/><xf numFmtId="165" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/><xf numFmtId="164" fontId="1" fillId="0" borderId="0" xfId="0" applyNumberFormat="1" applyFont="1"/><xf numFmtId="1" fontId="1" fillId="0" borderId="0" xfId="0" applyNumberFormat="1" applyFont="1"/><xf numFmtId="166" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/></cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>"""
}
