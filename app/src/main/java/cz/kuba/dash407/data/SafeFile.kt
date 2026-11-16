package cz.kuba.dash407.data

import java.io.File

/**
 * Bezpečné ukládání datových souborů (kniha jízd, tankování, servisní knížka).
 *
 * Rádio může ztratit napájení uprostřed zápisu (vytažení klíčku). Proto se nejdřív zapíše
 * dočasný soubor a ten se teprve pak přejmenuje na skutečný – původní soubor tak zůstane
 * celý, i když zápis nedoběhne. Poškozený soubor se nikdy nepřepíše prázdným seznamem,
 * jen se odloží stranou jako „….poskozeny“ (obnova pak jde ze zálohy).
 */
object SafeFile {
    /** Během obnovy ze zálohy se nic nezapisuje (jinak by zápis na pozadí vrátil stará data). */
    @Volatile var frozen = false

    /** Zapíše text celý, nebo vůbec (dočasný soubor + přejmenování). */
    fun write(file: File, text: String) {
        if (frozen) return
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            // Některá úložiště neumí přejmenovat přes existující soubor.
            file.delete()
            if (!tmp.renameTo(file)) {
                file.writeText(text)
                tmp.delete()
            }
        }
    }

    /**
     * Soubor se nedá přečíst: odloží se jako „<název>.poskozeny“ (starší odložený se přepíše),
     * aby ho další zápis nepřepsal. Když existuje celý dočasný soubor z přerušeného zápisu,
     * vrátí se jeho obsah k dalšímu pokusu o načtení.
     */
    fun quarantine(file: File): String? {
        try {
            val bad = File(file.parentFile, file.name + ".poskozeny")
            bad.delete()
            file.renameTo(bad)
        } catch (e: Exception) {
        }
        val tmp = File(file.parentFile, file.name + ".tmp")
        return try { if (tmp.exists()) tmp.readText() else null } catch (e: Exception) { null }
    }
}
