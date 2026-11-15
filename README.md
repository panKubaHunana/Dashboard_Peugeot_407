# Dash 407

Vlastní domovské prostředí (launcher) pro **Peugeot 407 SW** s Android autorádiem
Hizpo PG407971G (ROCO K706 / QF001, Android 14, displej 768 × 1024 na výšku,
CAN box Raise PSA-RZ). Celé česky.

Aktuální verze: **2.13** (viz [Releases](../../releases)).

## Co umí

| Oblast | Obsah |
|---|---|
| **Plochy v kříži** | Domů uprostřed, Mapa nahoře, Jízda dole, Auto vlevo, Hudba vpravo a pod Jízdou plocha **Menu** (Aplikace, Vzhled, Nastavení, Kam jedeme, Tankování, Kniha jízd, Auto a signály, Servisní knížka, Zvuk/volant/klima, OBD, Plochy). Přechod tahem prstu. |
| **Rozložení** | čtvercová mřížka 24 sloupců (řádky podle tvaru plochy, s lištami rádia 24 × 21). Widget jde přidat, posunout, roztáhnout, zkopírovat, smazat, zvětšit text; krok zpět; přejmenování a vypnutí ploch. |
| **Widgety** | Mapa, Navigace (rychlost, spotřeba, dojezd, příjezd do cíle, zbývající km), Navigační pokyny, Povolená rychlost, Kompas, GPS, Rychloměr a spotřeba, Jízda (denní kniha jízd), Přetížení, Časomíra, Stav vozu (dveře, výstrahy, světla, ruční brzda, napětí), Napětí, Spotřeba z tankování, Servis, Zvuk/volant/klima, Hodiny, Počasí (5 dní), Právě hraje, Rychlá volba, Dok |
| **Navigace** | navigace krok za krokem v launcheru (trasa a pokyny Valhalla/OSM, hlas), hledání Mapy.com; mapa po směru jízdy se stabilním směrem (GPS od 8 km/h) nebo sever nahoře – přepínač kompasem na mapě. Podklady Mapy.com, CARTO, Esri, OpenStreetMap, OpenTopoMap, Google. |
| **Kniha jízd** | jízdy s časem, odkud/kam, tachometrem, skutečnou trasou a GPS souřadnicemi startu a cíle; statistika po dnech, týdnech, měsících a letech; teplotní mapa (trasy, starty a cíle, tankování); export do Excelu (Kniha jízd, Tankování, Statistika). |
| **Tankování** | kniha tankování se spotřebou mezi plnými nádržemi; **automatické tankování** – zastávka u čerpací stanice (OpenStreetMap) nabídne zápis s předvyplněným tachometrem. |
| **Auto** | data z CAN boxu (palubní počítač Trip 1/2, okamžitá spotřeba, dojezd, dveře, výstrahy, napětí), OBD adaptér ELM327 přes Bluetooth, servisní knížka, odhad tachometru. |
| **Telefon a hudba** | rychlá volba (5 kontaktů z rádia + aplikace telefonu přes Kontakty), přehrávač s ovládáním (výchozí YouTube Music). |
| **Vzhled** | tmavý / světlý / podle systému, písma, barva zvýraznění, pozadí, průhlednost, zaoblení, mezery, velikost písma, animace, úvodní obrazovka s logem Peugeot. |
| **Aktualizace** | při každém spuštění kontrola GitHubu; novější verzi rádio nabídne, stáhne, rovnou nainstaluje a po aktualizaci ukáže novinky. |

Horní lišta (stav, ikony aplikací) a spodní lišta (klimatizace, hlasitost, Domů) patří softwaru výrobce rádia a zůstávají vidět; plocha pro widgety je asi 768 × 680 px.

## Instalace do rádia

1. První instalace: v prohlížeči rádia otevři **Releases** a stáhni nejnovější `Dash407-….apk`, nainstaluj ho a nastav Dash 407 jako domovskou aplikaci.
2. Další verze se nabídnou samy při spuštění (Nastavení › Aktualizace › Zkontrolovat teď). Při první aktualizaci může Android chtít povolit „Instalovat neznámé aplikace“.
3. **Nastavení › Mapy.com**: vlož API klíč z developer.mapy.com (bez klíče jdou podklady CARTO a Esri).
4. Mimo auto (tablet, telefon) zapni v Nastavení **Ukázkový režim**.

## Sandbox a schvalování změn

- **Sandbox** ([Sansbox_Dash407](https://github.com/panKubaHunana/Sansbox_Dash407)) je webová kopie launcheru 1:1 s rádiem – změny jde vyzkoušet a poslat bez auta.
- Úpravy se nejdřív připraví jako návrh (větev `navrh-<verze>` + náhled v sandboxu). Po klepnutí na **Schválit a nahrát** je workflow sloučí do `main` a sestaví.

## Sestavení a verze

- Každý push do `main` sestaví APK v GitHub Actions a vydá ho jako Release. Verze se bere z `versionName` v `app/build.gradle.kts`; když vydání s touto verzí už existuje, přidá se číslo sestavení (např. 2.13.29).
- Commity, které nemají vytvořit novou verzi (dokumentace, workflow), mají v popisu `[skip ci]`.
- Poznámky k vydání: `.github/release-notes.md` – stručně, jedna věta na řádek ve tvaru „Oblast: co je nového“.
- Podpisový klíč je v `keystore/` (stejný podpis = instalace přes starou verzi). Zdroje jde nahrát i jako `upload/src.zip`, workflow je rozbalí.

## Zdroje dat

- Mapové podklady, hledání: © Seznam.cz a.s. a další (Mapy.com), © OpenStreetMap a CARTO, Esri, OpenTopoMap, Google
- Trasy a pokyny: Valhalla nad daty © přispěvatelé OpenStreetMap
- Počasí: Open-Meteo; názvy míst: Nominatim (OpenStreetMap)
- Rychlostní limity a čerpací stanice: © přispěvatelé OpenStreetMap, služba Overpass
- Data z auta: služba `mcu_service` jednotky a protokol RZC (dekódování se postupně ověřuje), OBD-II přes ELM327

## Původ kódu

Kód, texty i grafika jsou napsané nově pro tento projekt. Inspirací byl projekt Dashwheel
(plochy v kříži, bohaté přizpůsobení); jeho kód, texty ani grafika převzaty nebyly.
Logo Mapy.com je oficiální logo z api.mapy.com, povinné u mapových podkladů.
