# BushLaunch

Launcher Minecraft by BushStar (Java, Swing).

**Wersja 0.5.0** - wybierasz profil (wersję), wpisujesz nick i klikasz **GRAJ**. Launcher sam pobiera grę
(klient, biblioteki, assets) z serwerów Mojang i ją uruchamia.

- **profile**: tworzysz własne profile (nazwa + wersja gry), zmieniasz im wersję, duplikujesz i usuwasz; zapisują się w `config.properties`
- lista wszystkich wersji z oficjalnego manifestu Mojang (domyślnie tylko release, snapshoty po zaznaczeniu)
- logowanie kontem **Microsoft** (premium, kod w przeglądarce, sesja zapamiętana) albo tryb offline (sam nick)
- ustawienie RAM-u, pasek postępu, log gry w oknie
- dane w `%APPDATA%\.bushlaunch` (gra, wersje, biblioteki, assets, `config.properties`)
- sam dobiera Javę do wersji gry

Nie obsługuje jeszcze: modów/Forge/Fabric i własnych skinów.

## Konta Microsoft (serwery premium)

Kliknij swoje konto w lewym dolnym rogu → **Zaloguj przez Microsoft**. Launcher pokaże kod i otworzy
stronę Microsoftu; po wpisaniu kodu okno samo się zamknie, a launcher zapamięta sesję (refresh token
w `%APPDATA%\.bushlaunch\microsoft.properties`, hasło nigdy nie trafia do launchera). Można w każdej
chwili przełączyć się na grę offline albo się wylogować.

**Jednorazowa konfiguracja (właściciel launchera)** - potrzebny własny Client ID aplikacji:

1. [Azure Portal](https://portal.azure.com) → *App registrations* → *New registration*. Nazwa dowolna
   (bez słów „Minecraft” i „Mojang”), konta: **Personal Microsoft accounts only**, Redirect URI puste.
2. W *Authentication* włącz **Allow public client flows = Yes** i zapisz.
3. Skopiuj **Application (client) ID**.
4. Zgłoś ten Client ID do Mojangu na https://aka.ms/mce-reviewappid i poczekaj na akceptację (zwykle
   do tygodnia). Dopóki jej nie ma, logowanie kończy się błędem „Invalid app registration”.
5. Wstaw Client ID do launchera - jedno z dwóch:
   - GitHub: *Settings → Secrets and variables → Actions → Variables → New variable* `MS_CLIENT_ID`
     (workflow wbuduje go w jar), albo
   - lokalnie: linijka `msClientId=TWOJ-CLIENT-ID` w `%APPDATA%\.bushlaunch\config.properties`.

Client ID jest publiczny (nie jest sekretem), więc może być wbudowany w jar.

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

## Automatyczne aktualizacje

Zainstalowany launcher przy starcie sprawdza plik `latest.json` w Releases. Gdy jest nowsza wersja,
pokazuje okno z opisem i przyciskiem **Aktualizuj teraz**: pobiera instalator, sprawdza sumę SHA-256,
instaluje po cichu (w tym samym trybie i folderze co poprzednio) i sam się uruchamia ponownie.
Można też wybrać „Pomiń tę wersję” albo wyłączyć sprawdzanie w Ustawieniach (jest tam też ręczne
„Sprawdź aktualizacje”). Wersja przenośna i sam `BushLaunch.exe` nie aktualizują się same - przy
nowej wersji otwierają stronę pobierania.

Żeby wydać aktualizację: **podnieś `<version>` w `pom.xml`** (np. 0.3.1) i zrób push - workflow zbuduje
instalator i `latest.json`. Bez zmiany numeru wersji launchery nie zobaczą aktualizacji.
Adres aktualizacji wstawia workflow (z nazwy repozytorium); w buildzie lokalnym aktualizacje są wyłączone.

## Budowanie lokalnie

```
mvn package
```

Powstaje `target/BushLaunch-0.5.0.jar` oraz `target/BushLaunch.exe`. Wymagana Java 17+ i Maven.

Instalator (Windows, JDK 21 + [Inno Setup 6](https://jrsoftware.org/isdl.php)):

```
jlink --module-path "%JAVA_HOME%\jmods" --add-modules ALL-MODULE-PATH --output runtime --strip-debug --no-header-files --no-man-pages --compress zip-6
mkdir input && copy target\BushLaunch-0.5.0.jar input\
jpackage --type app-image --name BushLaunch --app-version 0.5.0 --vendor BushStar --input input --main-jar BushLaunch-0.5.0.jar --main-class pl.bushstar.bushlaunch.BushLaunch --runtime-image runtime --icon installer\bushlaunch.ico --dest dist
iscc /DAppVersion=0.5.0 installer\BushLaunch.iss
```

Wynik: `dist/BushLaunch-Setup.exe`. Na GitHubie robi to samo `.github/workflows/build.yml`
(każdy push buduje i wystawia pliki w Releases).

## Struktura

- `BushLaunch.java` - okno launchera
- `GameInstaller.java` - pobieranie plików gry i budowa komendy uruchomienia
- `MicrosoftAuth.java` - logowanie kontem Microsoft (kod urządzenia → Xbox → XSTS → Minecraft, odświeżanie sesji)
- `Updater.java` - automatyczne aktualizacje (latest.json, pobieranie, SHA-256, cichy instalator)
- `JavaFinder.java` - wybór Javy dla danej wersji
- `Json.java` - mały parser JSON
- `installer/` - skrypt instalatora (`BushLaunch.iss`), ikona i grafiki kreatora
- `.github/workflows/build.yml` - build na Windowsie i publikacja w Releases
