# BushLaunch

Launcher Minecraft by BushStar (Java, Swing).

**Wersja 0.2.0** - wybierasz wersję, wpisujesz nick i klikasz **GRAJ**. Launcher sam pobiera grę
(klient, biblioteki, assets) z serwerów Mojang i ją uruchamia.

- lista wszystkich wersji z oficjalnego manifestu Mojang (domyślnie tylko release, snapshoty po zaznaczeniu)
- tryb offline (sam nick, bez logowania Microsoft)
- ustawienie RAM-u, pasek postępu, log gry w oknie
- dane w `%APPDATA%\.bushlaunch` (gra, wersje, biblioteki, assets, `config.properties`)
- sam dobiera Javę do wersji gry

Nie obsługuje jeszcze: logowania Microsoft (serwery premium), modów/Forge/Fabric, skinów.

## Pobieranie (Windows)

Wejdź w **Releases** na GitHubie (prawa strona repozytorium):

- `BushLaunch.exe` - jeden plik, uruchamiasz dwuklikiem. Wymaga zainstalowanej Javy 17 lub nowszej
  (jeśli jej nie ma, wyskoczy komunikat z linkiem do pobrania).
- `BushLaunch-Setup.exe` - instalator z wbudowaną Javą, dla osób bez Javy.

## Budowanie lokalnie

```
mvn package
```

Powstaje `target/BushLaunch-0.2.0.jar` oraz `target/BushLaunch.exe`. Wymagana Java 17+ i Maven.

## Struktura

- `BushLaunch.java` - okno launchera
- `GameInstaller.java` - pobieranie plików gry i budowa komendy uruchomienia
- `JavaFinder.java` - wybór Javy dla danej wersji
- `Json.java` - mały parser JSON
- `.github/workflows/build.yml` - build na Windowsie i publikacja w Releases
