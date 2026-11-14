# BushLaunch

Launcher Minecraft by BushStar (Java, Swing).

**Wersja 0.2.0** - wybierasz wersję, wpisujesz nick i klikasz **GRAJ**. Launcher sam pobiera grę
(klient, biblioteki, assets) z serwerów Mojang i ją uruchamia.

- lista wszystkich wersji z oficjalnego manifestu Mojang (domyślnie tylko release, snapshoty po zaznaczeniu)
- tryb offline (sam nick, bez logowania Microsoft)
- ustawienie RAM-u, pasek postępu, log gry w oknie
- dane w `%APPDATA%\.bushlaunch` (gra, wersje, biblioteki, assets, `config.properties`)
- sam dobiera Javę do wersji gry (szuka zainstalowanej, w razie braku używa wbudowanej)

Nie obsługuje jeszcze: logowania Microsoft (serwery premium), modów/Forge/Fabric, skinów.

## Pobieranie (Windows)

1. Wejdź w zakładkę **Actions** na GitHubie i otwórz ostatni udany build.
2. Pobierz artefakt **BushLaunch-windows** i rozpakuj go.
3. Uruchom `BushLaunch.exe` (Java jest w środku, nie trzeba jej instalować).

## Budowanie lokalnie

```
mvn package
java -jar target/BushLaunch-0.2.0.jar
```

Wymagana Java 17+ i Maven.

## Struktura

- `BushLaunch.java` - okno launchera
- `GameInstaller.java` - pobieranie plików gry i budowa komendy uruchomienia
- `JavaFinder.java` - wybór Javy dla danej wersji
- `Json.java` - mały parser JSON
- `.github/workflows/build.yml` - build `.exe` na Windowsie (jlink + jpackage)
