# Dash 407

Vlastní domovské prostředí (launcher) pro **Peugeot 407 SW** s Android autorádiem
Hizpo PG407971G (ROCO K706 / QF001, Android 14, displej 768 × 1024 na výšku,
CAN box Raise PSA-RZ). Celé česky.

## Co umí verze 0.1

| Obrazovka | Obsah |
|---|---|
| **Domů** | hodiny a datum, napětí, rychlost z GPS, palubní počítač (okamžitá spotřeba, dojezd, průměr), právě hrající hudba s ovládáním, dveře, spotřeba z tankování, dok s oblíbenými aplikacemi |
| **Auto** | Trip 1 a Trip 2, stav vozu (napětí, světla, ruční brzda), výstrahy vozu (zatím jen kódy), **Signály** – surová data z MCU a CAN boxu |
| **Tankování** | kniha tankování, spotřeba mezi plnými nádržemi, dlouhodobý průměr, cena za km |
| **Aplikace** | všechny nainstalované aplikace; podržením se přidávají do doku |
| **Nastavení** | výchozí domovská aplikace, přístup k oznámením, poloha, ukázkový režim |

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

Kód, texty i grafika jsou napsané nově pro tento projekt. Rozložení (dlaždice s daty
z auta, hudba, dok) vychází z obecných zvyklostí launcherů do aut. Inspirací byl
projekt Dashwheel; z něj pochází znalost, jak jednotka K706 předává data aplikacím
(služba `mcu_service`, rámce protokolu RZC). Jeho kód, texty ani grafika převzaty nebyly,
protože to jeho licence nedovoluje.
