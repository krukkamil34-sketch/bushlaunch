# BushLaunch

Launcher Minecraft by BushStar (Java, Swing).

**Wersja 0.1.0** - na razie tylko wybór wersji Minecrafta. Wybór zapisuje się w `~/.bushlaunch/config.properties`.

## Pobieranie (Windows)

1. Wejdź w zakładkę **Actions** na GitHubie i otwórz ostatni udany build.
2. Pobierz artefakt **BushLaunch-windows** i rozpakuj go.
3. Uruchom `BushLaunch.exe` (Java jest w środku, nie trzeba jej instalować).

## Budowanie lokalnie

```
mvn package
java -jar target/BushLaunch-0.1.0.jar
```

Wymagana Java 17+ i Maven.

## Struktura

- `src/main/java/pl/bushstar/bushlaunch/BushLaunch.java` - launcher
- `.github/workflows/build.yml` - build `.exe` na Windowsie (jpackage)
