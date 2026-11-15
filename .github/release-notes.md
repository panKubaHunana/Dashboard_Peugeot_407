## Dash 407 – verze 2.13

- Navigace: mapa se už nesmyslně neotáčí
- Navigace: nové tlačítko kompas – sever nahoře / mapa ve směru jízdy
- Rychlost: widget nově ukazuje okamžitou spotřebu a dojezd

Stáhni `Dash407-2.13.apk` (nabídne se samo).

## Dash 407 – verze 2.12

### Oprava
- **Mřížka se v režimu úprav nepřepočítává.** Lišta úprav plochu zmenší; dřív se pak počet řádků změnil z 21 na 18 a po „Hotovo“ zpět na 21, takže se widgety zaokrouhlením mohly posunout o řádek. Teď zůstává mřížka stejná a rozložení se nemění.

Stáhni `Dash407-2.12.apk` (nabídne se samo).

## Dash 407 – verze 2.11

### Opravy po prvním testu v autě
- **Mřížka ze skutečných čtverců**: 24 sloupců a počet řádků se dopočítá z tvaru plochy v rádiu. Když se podaří skrýt lišty, mřížka se sama přepočítá a widgety zůstanou na místě.
- **Domů se všemi šesti widgety** podle vzoru (rychlá volba, rychloměr, jízda, počasí, přehrávač, stav vozu), přizpůsobené skutečnému tvaru obrazovky. Plocha Domů se jednorázově přepíše.
- **Rychloměr** ukazuje spotřebu, ujeto a dojezd i v nižším widgetu.
- **Rychlá volba – kontakty**: vlastní výběr kontaktů (rádio nemá aplikaci pro výběr). Tlačítko **Kontakty** otevírá aplikaci telefonu v rádiu; při prvním klepnutí (nebo podržením) si ji vybereš.
- **Lišty rádia**: nový pokus o skrytí (starší způsob přes příznaky okna) a systémové skrytí, které funguje po jednorázovém povolení z počítače přes ADB.
- **Nastavení › Telefon a kontakty** a **Lišty rádia**: diagnostika (verze Androidu, oprávnění, počet čitelných kontaktů, která aplikace obslouží volání).

Stáhni `Dash407-2.11.apk` (od 2.7 se nabídne samo).

## Dash 407 – verze 2.10

### Novinka ve 2.10 – automatické tankování
- Když auto **stojí aspoň minutu u čerpací stanice** (do 70 m, podle OpenStreetMap), ukáže se okno **„Tankoval jsi?“** s názvem stanice a předvyplněným stavem tachometru. Doplníš litry a cenu, rovnou vidíš Kč/l.
- Když rádio naběhne u pumpy po nastartování, okno se ukáže hned.
- **Později** – okno se vrátí při další zastávce po odjezdu (nejdéle hodinu). **Netankoval jsem** – u té stanice 3 hodiny klid. Při rozjezdu nad 15 km/h okno samo zmizí a vrátí se na další zastávce.
- Nic se nenabízí, když už je tankování zapsané za poslední 2 hodiny.
- Tankování si pamatuje **název stanice a její polohu** (Tankování, Kniha jízd, teplotní mapa, Excel – nový sloupec Čerpací stanice).
- Zapnutí a vypnutí: Menu › Nastavení › Automatické tankování.


## Dash 407 – verze 2.9

### Novinky ve 2.9 – Kniha jízd naplno
- **Skutečná trasa každé jízdy**: během jízdy se ukládá bod zhruba každých 40 m. V detailu jízdy tlačítko **Trasa na mapě** (start zeleně, cíl červeně, posun prstem, přiblížení dvěma prsty).
- **Teplotní mapa** (Kniha jízd › Teplotní mapa): kudy jezdíš nejčastěji – režimy Trasy, Starty a cíle, Tankování; za zvolené období.
- **Stav tachometru**: na začátku a na konci každé jízdy. Počítá se z posledního známého stavu (tankování, servisní knížka nebo ručně „Zadat stav“) + km z GPS. Každé tankování odhad opraví.
- **Propojení s Tankováním**: tankování jsou v seznamu jízd, u tankování se ukládá poloha pumpy, kolonka tachometru se předvyplní odhadem. U každé jízdy odhad paliva a nákladů (průměr z knihy tankování × cena za litr z posledního tankování).
- **Statistika**: celkový čas jízdy, čas v pohybu, ujeté km a počet jízd po dnech, týdnech, měsících a rocích; nahoře přehled Dnes / Tento týden / Tento měsíc / Letos.
- **Excel**: tři listy – Kniha jízd (nově tachometr, čas jízdy, palivo a náklady), Tankování a Statistika.
- Filtr období nově i **Rok**; Týden začíná pondělím.


## Dash 407 – verze 2.8

### Novinky ve 2.8 – Kniha jízd
- **Widget vpravo nahoře je denní kniha jízd**: Ujeto, Průměr km/h, Maximum km/h a Začátek patří k aktuální jízdě a vynulují se, když začne nová jízda. V pohybu a Počet jízd jsou za dnešek (nulují se o půlnoci).
- **Jízda** začne rozjezdem a skončí, když auto stojí déle než 5 minut (nebo podržením widgetu › Ukončit). Přejezdy kratší než 300 m se nezapisují.
- **Kniha jízd** (Menu › Kniha jízd, nebo klepnutím na widget): tabulka jízd s datem, začátkem a koncem, odkud a kam, ujetými km, průměrem a maximem. Filtr Dnes / Týden / Měsíc / Vše a součty za období.
- **GPS souřadnice startu a cíle** u každé jízdy (podklad pro budoucí mapu pohybu vozidla). Názvy míst se doplní z OpenStreetMap, když je internet.
- **Detail jízdy**: souřadnice, poznámka (účel jízdy), smazání.
- **Export do Excelu** (.xlsx): uloží se do Stažené soubory/Dash407 a rovnou se nabídne odeslání. Obsahuje i souřadnice a řádek Celkem.


## Dash 407 – verze 2.7

### Novinky ve 2.7 – aktualizace přímo v rádiu
- **Kontrola aktualizací při každém spuštění**: Dash 407 se podívá na GitHub. Když je tam novější verze, nabídne ji okno s novinkami a tlačítkem **Stáhnout a nainstalovat**. Postup stahování ukazuje lišta s procenty, pak se rovnou spustí instalace.
- **Povolení instalace**: při první aktualizaci rádio může chtít povolit „Instalovat neznámé aplikace“ pro Dash 407. Tlačítko otevře správné nastavení; po návratu stahování pokračuje samo.
- **Novinky po aktualizaci**: po prvním spuštění nové verze se ukáže okno „Co je nového“.
- **Ruční kontrola**: Menu › Nastavení › Aktualizace › Zkontrolovat teď.
- Číslo verze v aplikaci teď přesně odpovídá vydání na GitHubu (např. 2.7.23).

Od verze 2.7 se další verze nabízejí samy.

## Dash 407 – verze 2.6

### Novinky ve 2.6 – nový vzhled widgetů podle vzoru
- **Rychlá volba**: zelená kolečka s bílými iniciálami, nad nimi celé jméno (až na dva řádky), Kontakty v bílém kolečku.
- **Rychloměr a spotřeba**: oblouk s rychlostí a pod ním okamžitá a průměrná spotřeba, ujeto a dojezd z auta.
- **Jízda**: sedm řádků – Ujeto km, V pohybu, Průměr km/h, Maximum km/h, Začátek, Počet jízd (nové) a Průměr l/100km. Vynulování podržením widgetu.
- **Počasí**: místo a obnovení vpravo nahoře, předpověď na 5 dní s barevnými ikonami, velká ikona a teplota. Bez popisu počasí, větru a vlhkosti.
- **Přehrávač**: ♪ a název uprostřed, tlačítka předchozí – přehrát – další přes celou šířku.
- **Stav vozu** (dřív Dveře): auto shora se značkami dveří a vedle Výstrahy vozu, Světla, Ruční brzda a Napětí v barvách (zelená = v pořádku, červená = pozor).
- Výchozí rozložení plochy Domů odpovídá vzoru (Nastavení › obnovit výchozí rozložení).
- **Čtverečková mřížka 16 × 32**: každý obdélník mřížky je rozdělený na dvě poloviny, takže buňky jsou čtverečky a widgety jde umístit a roztáhnout dvakrát jemněji do šířky. Uložené rozložení se při prvním spuštění samo převede, widgety zůstanou na svém místě. V režimu úprav jsou výraznější čáry původního dělení 4 × 12.

Stáhni nejnovější `Dash407-2.6….apk` a nainstaluj ho přes starou verzi.

### Novinka ve 2.5

### Novinka ve 2.5
- **Jemnější mřížka ploch 8 × 32** (dřív 4 × 12): widgety jdou umístit a zmenšit mnohem přesněji. Stávající rozložení se při prvním spuštění samo převede, nic se neztratí ani nepřekryje. V režimu úprav jsou výraznější čáry původního dělení, slabší ty nové.

Stáhni `Dash407-2.5.apk` a nainstaluj ho přes starou verzi.

### Novinka ve 2.4
- **Skrytí lišt rádia (zkušebně)**: Menu › Nastavení › Lišty rádia. Zvlášť jde skrýt horní lišta (napětí, hlasitost, ikony) a spodní lišta (klimatizace). Tahem od okraje obrazovky se lišta na chvíli ukáže. Funguje jen, pokud je jednotka kreslí jako systémové lišty Androidu.

### Novinka ve 2.3
- **Rychlá volba svisle**: když je widget úzký a vysoký (třeba 1 × 7), kontakty jsou pod sebou ve sloupci, kolečka těsně u sebe a jméno hned nad kolečkem. Široký widget zůstává v řadě, čtvercový jako mřížka 3 × 2. Nově přidaný widget má výchozí velikost 1 × 7.

### Novinky ve 2.2
- **Plocha Menu** místo spodní lišty: plochy mají o lištu víc místa na widgety. Menu je pod plochou „dole“ (tah prstem nahoru) a obsahuje Aplikace, Rozložení (výběr plochy k úpravě), Vzhled, Nastavení, Tankování, Auto a signály, Servisní knížku, Zvuk/volant/klima, OBD a Plochy. Po přechodu na jinou plochu se na chvíli ukáže kříž s jejím názvem.
- **Nové mapové podklady bez klíče**: CARTO Voyager / Světlá / Tmavá a Esri Silniční / Satelit. Mapy.com a OpenStreetMap se jednorázově přepnou na CARTO Voyager; s tmavou mapou se kreslí rovnou CARTO Tmavá (bez převracení barev). Navigace a hledání dál jedou přes Mapy.com.
- **Výstrahy vozu**: u každého kódu je celý rámec z CAN boxu a čas; kód jde pojmenovat podle toho, co ukázal displej auta.
- **OBD adaptér**: telefony, sluchátka a počítače jsou v seznamu označené („není OBD adaptér“), spojení zkouší tři způsoby (SPP, SPP bez zabezpečení, kanál 1) a chyby jsou česky.
- **Úvodní obrazovka**: ostřejší obrázek lva PEUGEOT, bez nápisu Dash 407.

### Novinka ve 2.1
- **Rychlá volba** ve stylu doku (4 × 2): pět vybraných kontaktů a šesté tlačítko otevře seznam kontaktů. Nad tlačítkem jen jméno; klepnutí volá, podržení kontakt upraví.

Stáhni `Dash407-2.1.apk` a nainstaluj ho v autorádiu přes starou verzi (podpis je stejný, nastavení zůstane).

### Obsah verze 2.0 (novinky od 0.4)
- **Úvodní obrazovka**: lev a nápis PEUGEOT (tvůj obrázek), další styly Moderní, Retro a Vlastní obrázek; délka 2–8 s, náhled, přeskočení klepnutím.
- **Vzhled**: motiv Tmavý / Světlý / Podle systému, 7 systémových písem.
- **OBD adaptér ELM327** (Bluetooth / Wi-Fi): výběr adaptéru, test spojení, živá data (otáčky, teplota chladicí kapaliny, MAP, zatížení…).
- **Servisní knížka**: SPZ, VIN, motorizace, prohlídka, olej, vzduchový a palivový filtr, brzdy – termíny podle km i data, historie, widget Servis.
- **Dveře**: realistický pohled shora na Peugeot 407 SW s červenými značkami otevřených dveří, kapoty a pátých dveří.
- **Palubní počítač a jízda** v jednom widgetu (bez velké číslice km). Staré widgety se na plochách převedou samy.
- **Počasí**: název obce podle GPS a denní předpověď na 3–4 dny.
- **Hudba**: výchozí YouTube Music (volba v Nastavení › Hudba).
- **Rychlá volba**: 5 vlastních kontaktů.
- **Zvuk, volant a klimatizace**: zkratky na původní aplikace rádia (DSP, učení tlačítek, klimatizace, nastavení vozu) + widget.
- **Bezpečnost**: API klíče Mapy.com a Google jsou v nastavení skryté tečkami.
