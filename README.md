# BushLaunch

Launcher Minecraft by BushStar (Java, Swing).

**Wersja 0.8.0** - wybierasz profil (wersję), wpisujesz nick i klikasz **GRAJ**. Launcher sam pobiera grę
(klient, biblioteki, assets) z serwerów Mojang i ją uruchamia.

- **nowy wygląd (0.8.0)**: zielony panel - menu boczne (Graj, Wersje, Mody, Aktualności, Ustawienia) z listą ostatnio granych wersji, nocna scena z plakietką JAVA EDITION, karty aktualności oraz dolny pasek z wyborem wersji, RAM-em, postępem i przyciskiem GRAJ; konsola jest w Ustawieniach
- **znajomi**: kliknij „znajomych” w prawym górnym rogu sceny. Z serwerem znajomych (patrz niżej) zaproszenia **musi potwierdzić druga osoba w swoim launcherze**, a przy każdym graczu widać głowę z kropką statusu (zielona = w grze, niebieska = online, szara = offline). Bez serwera lista jest lokalna. Skiny pobierane z serwerów Mojang
- **profile**: tworzysz własne profile (nazwa + wersja gry), zmieniasz im wersję, duplikujesz i usuwasz; zapisują się w `config.properties`
- lista wszystkich wersji z oficjalnego manifestu Mojang (domyślnie tylko release, snapshoty po zaznaczeniu)
- logowanie kontem **Microsoft** (tylko konta premium, w przeglądarce z wyborem konta, sesja zapamiętana); trybu offline już nie ma
- ustawienie RAM-u, pasek postępu, log gry w oknie
- dane w `%APPDATA%\.bushlaunch` (gra, wersje, biblioteki, assets, `config.properties`)
- sam dobiera Javę do wersji gry

Nie obsługuje jeszcze: modów/Forge/Fabric i własnych skinów.

## Konta Microsoft (serwery premium)

Kliknij swoje konto w lewym dolnym rogu → **Zaloguj przez Microsoft**. Launcher otworzy przeglądarkę
na stronie Microsoftu z wyborem konta; po zatwierdzeniu przeglądarka wróci na adres `localhost`, okno
launchera samo się zamknie, a launcher zapamięta sesję (refresh token
w `%APPDATA%\.bushlaunch\microsoft.properties`, hasło nigdy nie trafia do launchera). Można w każdej
chwili zmienić konto albo się wylogować. Bez zalogowania przycisk GRAJ otwiera okno logowania. Gdyby przeglądarka nie zadziałała (np. zapora
blokuje lokalny port), jest zapasowa opcja **Nie działa? Zaloguj kodem** - kod wpisuje się na stronie
Microsoftu.

**Konfiguracja logowania (tylko właściciel launchera)** - gracz nie wpisuje żadnego Client ID. Launcher ma je wbudowane podczas kompilacji. Potrzebny jest własny Client ID aplikacji:

1. [Azure Portal](https://portal.azure.com) → *App registrations* → *New registration*. Nazwa dowolna
   (bez słów „Minecraft” i „Mojang”), konta: **Personal Microsoft accounts only**.
2. W *Authentication* kliknij *Add a platform* → **Mobile and desktop applications** i dodaj adres
   przekierowania `http://localhost` (bez portu i bez ukośnika - port jest losowy, Microsoft go ignoruje).
   Dodatkowo włącz **Allow public client flows = Yes** (potrzebne dla zapasowego logowania kodem) i zapisz.
3. Skopiuj **Application (client) ID**.
4. Zgłoś ten Client ID do Mojangu na https://aka.ms/mce-reviewappid i poczekaj na akceptację (zwykle
   do tygodnia). Dopóki jej nie ma, logowanie kończy się błędem „Invalid app registration”.
5. W repozytorium GitHub otwórz *Settings → Secrets and variables → Actions → Variables → New repository variable* i ustaw nazwę `MS_CLIENT_ID`, a jako wartość wklej Client ID (opcjonalne - domyślnie wbudowane jest `a33244ce-944d-4cf7-81c4-b5893f5668e4`). Workflow wbuduje go do launchera. Przy kompilacji lokalnej utwórz plik `src/main/resources/auth.properties` z zawartością `clientId=TWOJ-CLIENT-ID`.

**Ukryte nadpisanie Client ID (dla zaawansowanych):** w `%APPDATA%\\.bushlaunch\\config.properties` można dopisać linię
`msClientId=TWOJ-CLIENT-ID`. Wtedy launcher używa tego ID zamiast wbudowanego (bez przebudowywania). Aplikacja
musi mieć dodany adres `http://localhost` (Mobile and desktop applications) i być zatwierdzona przez Mojang.
Używaj tylko własnego ID albo takiego, na które masz zgodę właściciela.

Gracz nie zobaczy ani nie wpisze Client ID. Kliknięcie „Zaloguj przez Microsoft” otworzy przeglądarkę na stronie Microsoft, a launcher sam dokończy logowanie po zatwierdzeniu. Client ID jest identyfikatorem publicznym aplikacji, a nie hasłem ani sekretem.

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

Powstaje `target/BushLaunch-0.8.0.jar` oraz `target/BushLaunch.exe`. Wymagana Java 17+ i Maven.

Instalator (Windows, JDK 21 + [Inno Setup 6](https://jrsoftware.org/isdl.php)):

```
jlink --module-path "%JAVA_HOME%\jmods" --add-modules ALL-MODULE-PATH --output runtime --strip-debug --no-header-files --no-man-pages --compress zip-6
mkdir input && copy target\BushLaunch-0.8.0.jar input\
jpackage --type app-image --name BushLaunch --app-version 0.8.0 --vendor BushStar --input input --main-jar BushLaunch-0.8.0.jar --main-class pl.bushstar.bushlaunch.BushLaunch --runtime-image runtime --icon installer\bushlaunch.ico --dest dist
iscc /DAppVersion=0.8.0 installer\BushLaunch.iss
```

Wynik: `dist/BushLaunch-Setup.exe`. Na GitHubie robi to samo `.github/workflows/build.yml`
(każdy push buduje i wystawia pliki w Releases).

## Struktura

- `BushLaunch.java` - okno launchera
- `GameInstaller.java` - pobieranie plików gry i budowa komendy uruchomienia
- `MicrosoftAuth.java` - logowanie kontem Microsoft (przeglądarka z PKCE lub kod urządzenia → Xbox → XSTS → Minecraft, odświeżanie sesji)
- `Updater.java` - automatyczne aktualizacje (latest.json, pobieranie, SHA-256, cichy instalator)
- `JavaFinder.java` - wybór Javy dla danej wersji
- `Json.java` - mały parser JSON
- `installer/` - skrypt instalatora (`BushLaunch.iss`), ikona i grafiki kreatora
- `.github/workflows/build.yml` - build na Windowsie i publikacja w Releases

## Znajomi (serwer znajomych)

Zaproszenia z potwierdzeniem i statusy znajomych działają przez mały serwer z folderu `friends-server`
(Node.js, bez zależności - patrz jego `README.md`). Tożsamość gracza potwierdza serwer sesji Mojang,
więc token gry nigdy nie trafia na ten serwer.

Adres serwera launcher bierze (od najważniejszego): z `config.properties` (`socialUrl=https://...`),
z pliku `social.properties` wbudowanego w jar albo - gdy brak - działa w trybie lokalnym.
W GitHub Actions plik tworzy krok **Adres serwera znajomych** ze zmiennej repozytorium **FRIENDS_URL**
(Settings > Secrets and variables > Actions > Variables).
