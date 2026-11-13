# Dash 407

Vlastní domovské prostředí (launcher) pro **Peugeot 407 SW** s Android autorádiem
Hizpo PG407971G (ROCO K706 / QF001, Android 14, displej 768 × 1024 na výšku,
CAN box Raise PSA-RZ). Celé česky.

## Co umí verze 0.2 (beta)

| Část | Obsah |
|---|---|
| **Plochy v kříži** | 5 ploch (Domů uprostřed, Nahoře, Dole, Vlevo, Vpravo), přepínání přejetím prstem nebo přes ukazatel kříže ve spodní liště |
| **Dlaždice** | Mapa (Mapy.com), Navigace, Hodiny, Počasí, Právě hraje, Jízda (GPS), Rychlost, Palubní počítač, Dveře, Spotřeba, Dok |
| **Úpravy** | přidání z katalogu, posun, šířka ½ / celá, výška 1–4, odebrání, 5 šablon, krok Zpět |
| **Vzhled** | 8 barev akcentu, 4 pozadí, průhlednost a zaoblení dlaždic, velikost písma, animace, sekundy, popisky lišty |
| **Mapy.com** | mapa z REST API Mapy.com (vlastní API klíč), noční ztmavení, hledání a navigace v aplikaci Mapy.com, čtení příštího manévru z jejího oznámení |
| **Lišty rádia** | horní lišta s ikonami i panel klimatizace zůstávají vidět (lze skrýt ve Vzhledu) |
| **Auto, Tankování, Aplikace, Nastavení** | jako ve verzi 0.1, otevírají se z nabídky ⋮ ve spodní liště |

### Mapy.com
1. Na https://developer.mapy.com/account/ se přihlas účtem Seznam, založ projekt a zkopíruj API klíč.
2. V Dash 407: ⋮ → Nastavení → **Mapa Mapy.com** → vlož klíč → Uložit.
3. Pro dlaždici Navigace povol přístup k oznámením a nainstaluj aplikaci Mapy.com.

Klíč se ukládá jen v rádiu, do repozitáře nepatří.

## Instalace do rádia

1. V prohlížeči rádia otevři **Releases** tohoto repozitáře a stáhni nejnovější `Dash407-….apk`.
2. Otevři stažený soubor a povol instalaci z neznámých zdrojů.
3. Spusť Dash 407 → **Nastavení** → projdi tři karty (domovská aplikace, oznámení, poloha).
4. Na Androidu 13+ je před povolením přístupu k oznámením potřeba v *Informacích o aplikaci → ⋮* zvolit **Povolit omezená nastavení**.

Mimo auto (tablet, telefon) zapni v Nastavení **Ukázkový režim**.

## Data z auta

Aplikace se bez rootu připojuje ke službě `mcu_service` jednotky a čte proud z CAN boxu
(protokol RZC pro PSA). Dekódování palubního počítače, dveří a výstrah je **zatím neověřené
na Peugeotu 407** – k ověření slouží obrazovka *Auto → Signály*.

## Sestavení

Každý push do větve `main` spustí GitHub Actions (`.github/workflows/build.yml`),
které sestaví APK a vydají ho jako Release. Na počítači: `gradle :app:assembleDebug`
(Gradle 8.11+, JDK 17, Android SDK 35).

## Původ kódu

Kód, texty i grafika jsou napsané nově pro tento projekt. Mapy: © Seznam.cz a.s. a další (Mapy.com). Počasí: Open-Meteo. Knihovny: osmdroid, AndroidSVG (Apache 2.0).
Rozložení (dlaždice s daty
z auta, hudba, dok) vychází z obecných zvyklostí launcherů do aut. Inspirací byl
projekt Dashwheel; z něj pochází znalost, jak jednotka K706 předává data aplikacím
(služba `mcu_service`, rámce protokolu RZC). Jeho kód, texty ani grafika převzaty nebyly,
protože to jeho licence nedovoluje.
