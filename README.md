# Dash 407

Vlastní domovské prostředí (launcher) pro **Peugeot 407 SW** s Android autorádiem
Hizpo PG407971G (ROCO K706 / QF001, Android 14, displej 768 × 1024 na výšku,
CAN box Raise PSA-RZ). Celé česky.

## Co umí verze 2.0

| Oblast | Obsah |
|---|---|
| **Plochy v kříži** | 5 ploch (Domů uprostřed, Mapa nahoře, Jízda dole, Auto vlevo, Hudba vpravo), přechod tahem prstu, kříž ve spodní liště ukazuje aktuální plochu |
| **Rozložení** | mřížka 4 × 12, widget jde přidat, posunout, roztáhnout, zkopírovat, smazat, zvětšit text; krok zpět; přejmenování ploch |
| **Widgety** | Mapa, Navigace, Povolená rychlost, Kompas, GPS, Rychlost, Rychloměr, Palubní počítač a jízda, Přetížení, Časomíra, Dveře (Peugeot 407 SW), Napětí, Spotřeba, Servis, Zvuk/volant/klima, Hodiny, Počasí s předpovědí, Právě hraje, Rychlá volba, Dok |
| **Úvodní obrazovka** | logo Peugeot (vlastní obrázek), styly Moderní, Retro, Vlastní obrázek |
| **Vzhled** | tmavý / světlý / podle systému, 7 písem, barva zvýraznění, pozadí, průhlednost, zaoblení, mezery, velikost písma, animace, styl mapy |
| **Navigace** | Mapy.com (dlaždice, hledání, trasy), navigace krok za krokem v launcheru (Valhalla/OSM), Google mapy, OpenStreetMap, OpenTopoMap |
| **Auto** | data z CAN boxu (palubní počítač, dveře, výstrahy), OBD adaptér ELM327, servisní knížka, kniha tankování |
| **Telefon a hudba** | rychlá volba (5 kontaktů / oblíbené), výchozí YouTube Music |
| **Systémové lišty** | horní lišta s ikonami a spodní lišta klimatizace/hlasitosti zůstávají vidět |

## Instalace do rádia

1. V prohlížeči rádia otevři **Releases** tohoto repozitáře a stáhni nejnovější `Dash407-….apk`.
2. Nainstaluj ho přes starou verzi (podpis je stejný, nastavení zůstane).
3. **Nastavení › Mapy.com**: vlož API klíč z developer.mapy.com a klepni na *Uložit a ověřit*.
4. Mimo auto (tablet, telefon) zapni v Nastavení **Ukázkový režim**.

## Zdroje dat

- Mapové podklady, hledání a trasy: © Seznam.cz a.s. a další – REST API Mapy.com (developer.mapy.com)
- Počasí: Open-Meteo (bez klíče), název obce: Nominatim / © přispěvatelé OpenStreetMap
- Rychlostní limity: © přispěvatelé OpenStreetMap, služba Overpass
- Data z auta: služba `mcu_service` jednotky a protokol RZC (dekódování zatím ověřujeme)

## Sestavení

Každý push do `main` sestaví APK v GitHub Actions a vydá ho jako Release s verzí podle `versionName` v `app/build.gradle.kts`. Zdroje jde nahrát i jako `upload/src.zip`, workflow je rozbalí.

## Původ kódu

Kód, texty i grafika jsou napsané nově pro tento projekt. Inspirací byl projekt Dashwheel
(plochy v kříži, bohaté přizpůsobení); jeho kód, texty ani grafika převzaty nebyly.
Logo Mapy.com je oficiální logo z api.mapy.com, povinné u mapových podkladů.
