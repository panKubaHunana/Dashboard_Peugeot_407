# Dash 407

Vlastní domovské prostředí (launcher) pro **Peugeot 407 SW** s Android autorádiem
Hizpo PG407971G (ROCO K706 / QF001, Android 14, displej 768 × 1024 na výšku,
CAN box Raise PSA-RZ). Celé česky.

## Co umí verze 0.2 (beta)

| Oblast | Obsah |
|---|---|
| **Plochy v kříži** | 5 ploch (Domů uprostřed, Mapa nahoře, Jízda dole, Auto vlevo, Hudba vpravo), přechod tahem prstu, kříž ve spodní liště ukazuje aktuální plochu |
| **Rozložení** | mřížka 4 × 12, widget jde přidat, posunout, roztáhnout rohem, zkopírovat, smazat, zvětšit text; krok zpět; přejmenování ploch |
| **Widgety (18)** | Mapa, Navigace, Povolená rychlost, Kompas, GPS, Rychlost, Rychloměr, Jízda, Přetížení, Časomíra, Palubní počítač, Dveře, Napětí, Spotřeba, Hodiny, Počasí, Právě hraje, Dok |
| **Vzhled** | barva zvýraznění, 7 pozadí, průhlednost karet, zaoblení, mezery, velikost písma, rámečky, nadpisy, animace, tmavá mapa, styl mapy |
| **Mapy.com** | dlaždice, hledání míst a plánování trasy přes REST API (vlastní API klíč), hlasová navigace předáním do aplikace Mapy.com, další manévr z oznámení Mapy.com |
| **Systémové lišty** | horní lišta s ikonami a spodní lišta klimatizace/hlasitosti zůstávají vidět |

## Instalace do rádia

1. V prohlížeči rádia otevři **Releases** tohoto repozitáře a stáhni nejnovější `Dash407-….apk`.
2. Nainstaluj ho přes starou verzi (podpis je stejný, nastavení zůstane).
3. **Nastavení › Mapy.com**: vlož API klíč z developer.mapy.com a klepni na *Uložit a ověřit*.
4. Mimo auto (tablet, telefon) zapni v Nastavení **Ukázkový režim**.

## Zdroje dat

- Mapové podklady, hledání a trasy: © Seznam.cz a.s. a další – REST API Mapy.com (developer.mapy.com)
- Počasí: Open-Meteo (bez klíče)
- Rychlostní limity: © přispěvatelé OpenStreetMap, služba Overpass
- Data z auta: služba `mcu_service` jednotky a protokol RZC (dekódování zatím ověřujeme)

## Sestavení

Zdroje se nahrávají jako `upload/src.zip`; GitHub Actions je rozbalí, sestaví APK a vydají ho jako Release.

## Původ kódu

Kód, texty i grafika jsou napsané nově pro tento projekt. Inspirací byl projekt Dashwheel
(plochy v kříži, bohaté přizpůsobení); jeho kód, texty ani grafika převzaty nebyly.
Logo Mapy.com je oficiální logo z api.mapy.com, povinné u mapových podkladů.
