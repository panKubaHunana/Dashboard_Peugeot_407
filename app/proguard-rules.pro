# Dash 407 – pravidla pro R8 (ostré sestavení).

# Názvy tříd a metod se nemění: záznam pádu (crash log) pak ukazuje čitelná místa v kódu
# a hlášení na GitHubu jde rovnou opravit. Zmenšení kódu a zdrojů zůstává.
-dontobfuscate

# Spojení s MCU rádia: rozhraní se hledá podle jména přes Binder.
-keep class cz.kuba.dash407.car.McuLink { *; }

# Uložené výčty (typy widgetů, plochy) se čtou podle názvu.
-keepclassmembers enum cz.kuba.dash407.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keepattributes SourceFile,LineNumberTable
