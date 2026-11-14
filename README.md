# BushLaunch

Launcher Minecraft by BushStar (Java, Swing).

**Wersja 0.3.0** - wybierasz wersję, wpisujesz nick i klikasz **GRAJ**. Launcher sam pobiera grę
(klient, biblioteki, assets) z serwerów Mojang i ją uruchamia.

- lista wszystkich wersji z oficjalnego manifestu Mojang (domyślnie tylko release, snapshoty po zaznaczeniu)
- tryb offline (sam nick, bez logowania Microsoft)
- ustawienie RAM-u, pasek postępu, log gry w oknie
- dane w `%APPDATA%\.bushlaunch` (gra, wersje, biblioteki, assets, `config.properties`)
- sam dobiera Javę do wersji gry

Nie obsługuje jeszcze: logowania Microsoft (serwery premium), modów/Forge/Fabric, skinów.

## Pobieranie (Windows)

Wejdź w **Releases** na GitHubie (prawa strona repozytorium):

- `BushLaunch-Setup.exe` - **instalator** (zalecany). Kreator po polsku lub angielsku, wbudowana Java
  (nie trzeba nic doinstalowywać), skrót w menu Start i opcjonalnie na pulpicie, wpis w
  „Aplikacje i funkcje”. Domyślnie instaluje się tylko dla Twojego użytkownika (bez administratora);
  w kreatorze można wybrać instalację dla wszystkich. Przy odinstalowaniu launcher pyta, czy skasować
  też dane gry z `%APPDATA%\.bushlaunch` (domyślnie zostają).
- `BushLaunch-Portable.zip` - wersja przenośna z wbudowaną Javą: rozpakuj i uruchom `BushLaunch.exe`.
- `BushLaunch.exe` - jeden mały plik, wymaga zainstalowanej Javy 17 lub nowszej
  (jeśli jej nie ma, wyskoczy komunikat z linkiem do pobrania).

## Budowanie lokalnie

```
mvn package
```

Powstaje `target/BushLaunch-0.3.0.jar` oraz `target/BushLaunch.exe`. Wymagana Java 17+ i Maven.

Instalator (Windows, JDK 21 + [Inno Setup 6](https://jrsoftware.org/isdl.php)):

```
jlink --module-path "%JAVA_HOME%\jmods" --add-modules ALL-MODULE-PATH --output runtime --strip-debug --no-header-files --no-man-pages --compress zip-6
mkdir input && copy target\BushLaunch-0.3.0.jar input\
jpackage --type app-image --name BushLaunch --app-version 0.3.0 --vendor BushStar --input input --main-jar BushLaunch-0.3.0.jar --main-class pl.bushstar.bushlaunch.BushLaunch --runtime-image runtime --icon installer\bushlaunch.ico --dest dist
iscc /DAppVersion=0.3.0 installer\BushLaunch.iss
```

Wynik: `dist/BushLaunch-Setup.exe`. Na GitHubie robi to samo `.github/workflows/build.yml`
(każdy push buduje i wystawia pliki w Releases).

## Struktura

- `BushLaunch.java` - okno launchera
- `GameInstaller.java` - pobieranie plików gry i budowa komendy uruchomienia
- `JavaFinder.java` - wybór Javy dla danej wersji
- `Json.java` - mały parser JSON
- `installer/` - skrypt instalatora (`BushLaunch.iss`), ikona i grafiki kreatora
- `.github/workflows/build.yml` - build na Windowsie i publikacja w Releases
