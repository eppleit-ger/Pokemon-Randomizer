# Änderungen am Universal Pokemon Randomizer ZX

`engine/upr/` enthält den Quellcode von UPR ZX v4.6.1
(Commit `7f00eb8` von https://github.com/Ajarmar/universal-pokemon-randomizer-zx, GPLv3).
`src/launcher` wurde entfernt. Alle Änderungen sind im Code mit `[web]` markiert und
nur nötig, weil TeaVM einige Java-APIs nicht anbietet:

| Datei | Änderung |
|---|---|
| `com/dabomstew/pkrandom/Settings.java` | `clazz.getMethod("values").invoke(null)` → `clazz.getEnumConstants()` (keine Reflection) |
| `com/dabomstew/pkrandom/SysConstants.java` | `getRootPath()` liefert fest `./` (kein `getProtectionDomain()` im Browser) |
| `cuecompressors/BLZCoder.java` | `EXIT()` wirft eine Exception statt `System.exit(0)` |
| `com/dabomstew/pkrandom/MiscTweak.java` | `ResourceBundle.getBundle(...)` → `WebBundle.get()` |
| `com/dabomstew/pkrandom/WebBundle.java` | neu: lädt `newgui/Bundle.properties` von Hand (TeaVM findet `.properties`-Bundles nicht) |
| `com/dabomstew/pkrandom/RandomSource.java` | `new Random()` → `new JdkRandom()` (gleiche Zufallszahlen wie am PC) |
| `com/dabomstew/pkrandom/JdkRandom.java` | neu: `java.util.Random` mit exakt den OpenJDK-Algorithmen (TeaVM liefert für gleiche Seeds andere Werte) |
| `com/dabomstew/pkrandom/JavaMath.java` + alle `Math.round(`-Aufrufe | Java-Rundung (TeaVM: `Math.round(-2.5) == -3`, Java: `-2`) |
| `pptxt/PPTxtHandler.java` | `Character.UnicodeBlock.of(c) != null` durch gleichwertige Prüfung ersetzt (BMP: nur 0x2FE0–0x2FEF ohne Block, Stand Java 21) |

TeaVM läuft im `strict`-Modus, damit Array-Grenzen und `null` wie in Java geprüft werden
(der Code fängt an einigen Stellen `ArrayIndexOutOfBoundsException`/`NullPointerException` ab).

Zusätzlich (außerhalb von `upr/`):

- `src/main/java/org/teavm/classlib/java/util/TScanner.java` – zeilenweiser Ersatz für `java.util.Scanner`
- `src/main/java/de/nuzlocke/web/WebFileSystem.java` – Dateisystem im Arbeitsspeicher, ohne ROM-Kopien
- `src/main/java/de/nuzlocke/web/WebRandomizer.java` – Einstiegspunkt für JavaScript (entspricht `CliRandomizer`)
- `src/main/java/de/nuzlocke/web/build/UprResourceSupplier.java` – bettet Config-Tabellen und Patches ein
