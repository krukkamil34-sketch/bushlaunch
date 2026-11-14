package pl.bushstar.bushlaunch;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Logowanie kontem Microsoft (premium) metodą "device code": launcher pokazuje kod, gracz wpisuje go
 * na stronie Microsoftu, a launcher w tle wymienia tokeny: Microsoft -> Xbox Live -> XSTS -> Minecraft.
 *
 * Wymaga własnego Client ID aplikacji z Azure (publiczny klient) zatwierdzonego przez Mojang -
 * patrz README. Hasło gracza nigdy nie trafia do launchera; zapisujemy tylko refresh token.
 */
public final class MicrosoftAuth {

    /** Zapisane konto: nazwa, UUID (bez myślników) i refresh token Microsoftu. */
    public record Account(String name, String uuid, String refreshToken) {
    }

    /** Kod do wpisania przez gracza na stronie Microsoftu. */
    public record DeviceCode(String userCode, String verificationUri, String deviceCode, int interval,
                             int expiresIn) {
    }

    /** Wynik logowania: profil Minecraft, token do gry i (nowy) refresh token. */
    public record Login(String name, String uuid, String mcToken, String refreshToken) {
    }

    /** Błąd z komunikatem gotowym do pokazania graczowi. */
    public static final class AuthException extends IOException {
        private static final long serialVersionUID = 1L;
        private final boolean relogin;

        public AuthException(String message, boolean relogin) {
            super(message);
            this.relogin = relogin;
        }

        /** Czy trzeba zalogować się od nowa (refresh token wygasł lub został unieważniony). */
        public boolean reloginRequired() {
            return relogin;
        }
    }

    public static final class CancelledException extends IOException {
        private static final long serialVersionUID = 1L;

        public CancelledException() {
            super("Anulowano");
        }
    }

    private record Resp(int status, String body, Map<String, Object> json) {
    }

    private static final String SCOPE = "XboxLive.signin offline_access";
    private static final String FORM = "application/x-www-form-urlencoded";
    private static final String JSON = "application/json";

    // adresy można podmienić właściwościami systemowymi (używane tylko do testów z fałszywym serwerem)
    private static final String DEVICE_URL = System.getProperty("bushlaunch.url.device",
            "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode");
    private static final String TOKEN_URL = System.getProperty("bushlaunch.url.token",
            "https://login.microsoftonline.com/consumers/oauth2/v2.0/token");
    private static final String XBL_URL = System.getProperty("bushlaunch.url.xbl",
            "https://user.auth.xboxlive.com/user/authenticate");
    private static final String XSTS_URL = System.getProperty("bushlaunch.url.xsts",
            "https://xsts.auth.xboxlive.com/xsts/authorize");
    private static final String MC_LOGIN_URL = System.getProperty("bushlaunch.url.mclogin",
            "https://api.minecraftservices.com/authentication/login_with_xbox");
    private static final String MC_PROFILE_URL = System.getProperty("bushlaunch.url.mcprofile",
            "https://api.minecraftservices.com/minecraft/profile");

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private MicrosoftAuth() {
    }

    // ------------------------------------------------------------------ Client ID

    /** Client ID wbudowany w jar podczas budowania (auth.properties) albo null. */
    public static String bundledClientId() {
        try (InputStream in = MicrosoftAuth.class.getResourceAsStream("/auth.properties")) {
            if (in == null) {
                return null;
            }
            Properties p = new Properties();
            p.load(in);
            String id = p.getProperty("clientId", "").trim();
            return id.isEmpty() ? null : id;
        } catch (IOException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ logowanie (device code)

    public static DeviceCode startDeviceLogin(String clientId) throws IOException, InterruptedException {
        Resp r = post(DEVICE_URL, FORM, form("client_id", clientId, "scope", SCOPE));
        String userCode = Json.str(r.json().get("user_code"));
        String deviceCode = Json.str(r.json().get("device_code"));
        if (r.status() != 200 || userCode == null || deviceCode == null) {
            throw new AuthException(oauthError(r), false);
        }
        String uri = Json.str(r.json().get("verification_uri"));
        if (uri == null) {
            uri = "https://www.microsoft.com/link";
        }
        return new DeviceCode(userCode, uri, deviceCode,
                (int) Json.num(r.json().get("interval"), 5), (int) Json.num(r.json().get("expires_in"), 900));
    }

    /** Czeka, aż gracz wpisze kod, i kończy logowanie. Blokuje wątek - wołać poza wątkiem UI. */
    public static Login pollLogin(String clientId, DeviceCode dc, BooleanSupplier cancelled,
                                  Consumer<String> status) throws IOException, InterruptedException {
        status.accept("Czekam na zalogowanie w przeglądarce...");
        long deadline = System.currentTimeMillis() + dc.expiresIn() * 1000L;
        int interval = Math.max(dc.interval(), 3);
        while (true) {
            // czekamy w kawałkach, żeby anulowanie działało od razu
            long until = System.currentTimeMillis() + interval * 1000L;
            while (System.currentTimeMillis() < until) {
                if (cancelled.getAsBoolean()) {
                    throw new CancelledException();
                }
                Thread.sleep(250);
            }
            if (cancelled.getAsBoolean()) {
                throw new CancelledException();
            }
            if (System.currentTimeMillis() > deadline) {
                throw new AuthException("Kod wygasł. Spróbuj zalogować się jeszcze raz.", false);
            }
            Resp r = post(TOKEN_URL, FORM, form("grant_type", "urn:ietf:params:oauth:grant-type:device_code",
                    "client_id", clientId, "device_code", dc.deviceCode()));
            String access = Json.str(r.json().get("access_token"));
            if (r.status() == 200 && access != null) {
                return exchange(access, Json.str(r.json().get("refresh_token")), status);
            }
            String err = Json.str(r.json().get("error"));
            if ("authorization_pending".equals(err)) {
                continue;
            }
            if ("slow_down".equals(err)) {
                interval += 5;
                continue;
            }
            if ("authorization_declined".equals(err) || "access_denied".equals(err)) {
                throw new AuthException("Logowanie zostało odrzucone na stronie Microsoftu.", false);
            }
            if ("expired_token".equals(err)) {
                throw new AuthException("Kod wygasł. Spróbuj zalogować się jeszcze raz.", false);
            }
            throw new AuthException(oauthError(r), false);
        }
    }

    /** Odświeża sesję zapisanym refresh tokenem (bez pytania gracza o cokolwiek). */
    public static Login refresh(String clientId, Account account, Consumer<String> status)
            throws IOException, InterruptedException {
        Resp r = post(TOKEN_URL, FORM, form("client_id", clientId, "grant_type", "refresh_token",
                "refresh_token", account.refreshToken(), "scope", SCOPE));
        String access = Json.str(r.json().get("access_token"));
        if (r.status() != 200 || access == null) {
            String err = Json.str(r.json().get("error"));
            boolean relogin = "invalid_grant".equals(err) || "interaction_required".equals(err);
            throw new AuthException(relogin
                    ? "Sesja Microsoft wygasła - zaloguj się ponownie (kliknij konto w lewym dolnym rogu)."
                    : oauthError(r), relogin);
        }
        String rt = Json.str(r.json().get("refresh_token"));
        return exchange(access, rt == null ? account.refreshToken() : rt, status);
    }

    // ------------------------------------------------------------------ Xbox Live -> XSTS -> Minecraft

    private static Login exchange(String msToken, String msRefresh, Consumer<String> status)
            throws IOException, InterruptedException {
        status.accept("Logowanie do Xbox Live...");
        Resp xbl = post(XBL_URL, JSON, "{\"Properties\":{\"AuthMethod\":\"RPS\",\"SiteName\":\"user.auth.xboxlive.com\","
                + "\"RpsTicket\":" + q("d=" + msToken) + "},\"RelyingParty\":\"http://auth.xboxlive.com\","
                + "\"TokenType\":\"JWT\"}");
        String xblToken = Json.str(xbl.json().get("Token"));
        String xblUhs = uhs(xbl.json());
        if (xbl.status() != 200 || xblToken == null || xblUhs == null) {
            throw new AuthException("Xbox Live odrzuciło logowanie (kod " + xbl.status() + ").", false);
        }

        status.accept("Autoryzacja Xbox (XSTS)...");
        Resp xsts = post(XSTS_URL, JSON, "{\"Properties\":{\"SandboxId\":\"RETAIL\",\"UserTokens\":["
                + q(xblToken) + "]},\"RelyingParty\":\"rp://api.minecraftservices.com/\",\"TokenType\":\"JWT\"}");
        if (xsts.status() != 200) {
            throw new AuthException(xstsMessage(Json.num(xsts.json().get("XErr"), 0), xsts.status()), false);
        }
        String xstsToken = Json.str(xsts.json().get("Token"));
        String xstsUhs = uhs(xsts.json());
        if (xstsToken == null || xstsUhs == null) {
            throw new AuthException("Nieprawidłowa odpowiedź XSTS.", false);
        }

        status.accept("Logowanie do Minecraft...");
        Resp mc = post(MC_LOGIN_URL, JSON,
                "{\"identityToken\":" + q("XBL3.0 x=" + xstsUhs + ";" + xstsToken) + "}");
        String mcToken = Json.str(mc.json().get("access_token"));
        if (mc.status() != 200 || mcToken == null) {
            if (mc.status() == 403 || mc.body().contains("Invalid app registration")) {
                throw new AuthException("Mojang nie zatwierdził jeszcze tej aplikacji (Invalid app registration). "
                        + "Client ID trzeba zgłosić na https://aka.ms/mce-reviewappid i poczekać na akceptację.", false);
            }
            throw new AuthException("Minecraft odrzucił logowanie (kod " + mc.status() + ").", false);
        }

        status.accept("Sprawdzanie profilu Minecraft...");
        Resp prof = get(MC_PROFILE_URL, mcToken);
        if (prof.status() == 404) {
            throw new AuthException("To konto Microsoft nie ma Minecrafta Java Edition "
                    + "(albo nie ustawiono jeszcze nazwy gracza na minecraft.net).", false);
        }
        String name = Json.str(prof.json().get("name"));
        String id = Json.str(prof.json().get("id"));
        if (prof.status() != 200 || name == null || id == null) {
            throw new AuthException("Nie udało się pobrać profilu Minecraft (kod " + prof.status() + ").", false);
        }
        return new Login(name, id.replace("-", ""), mcToken, msRefresh);
    }

    private static String uhs(Map<String, Object> m) {
        Map<String, Object> claims = Json.map(m.get("DisplayClaims"));
        List<Object> xui = claims == null ? null : Json.list(claims.get("xui"));
        if (xui == null || xui.isEmpty()) {
            return null;
        }
        Map<String, Object> first = Json.map(xui.get(0));
        return first == null ? null : Json.str(first.get("uhs"));
    }

    private static String xstsMessage(long code, int status) {
        if (code == 2148916233L) {
            return "To konto Microsoft nie ma profilu Xbox. Zaloguj się na minecraft.net i utwórz profil, "
                    + "a potem spróbuj ponownie.";
        }
        if (code == 2148916235L) {
            return "Xbox Live jest niedostępne w Twoim kraju.";
        }
        if (code == 2148916236L || code == 2148916237L) {
            return "To konto wymaga weryfikacji wieku na stronie Xbox.";
        }
        if (code == 2148916238L) {
            return "To konto należy do dziecka - dodaj je do rodziny Microsoft (Xbox Family), żeby mogło grać.";
        }
        return "Xbox (XSTS) odrzucił logowanie (kod " + (code != 0 ? code : status) + ").";
    }

    private static String oauthError(Resp r) {
        String err = Json.str(r.json().get("error"));
        String desc = Json.str(r.json().get("error_description"));
        String first = desc == null ? "" : desc.split("\\r?\\n")[0];
        String all = (err == null ? "" : err) + " " + (desc == null ? "" : desc);
        if (all.contains("AADSTS70002") || all.contains("AADSTS70043") || all.contains("unauthorized_client")) {
            return "Aplikacja Azure nie ma włączonych publicznych przepływów klienta. W Azure: Authentication -> "
                    + "Allow public client flows = Yes. (" + first + ")";
        }
        if (all.contains("AADSTS700016") || all.contains("invalid_client")) {
            return "Nieprawidłowy Client ID aplikacji Microsoft (msClientId). (" + first + ")";
        }
        if (first.isEmpty()) {
            return "Serwer Microsoft odpowiedział kodem " + r.status() + ".";
        }
        return "Microsoft: " + first;
    }

    // ------------------------------------------------------------------ zapis konta

    private static Path accountFile(Path baseDir) {
        return baseDir.resolve("microsoft.properties");
    }

    public static Account loadAccount(Path baseDir) {
        Path f = accountFile(baseDir);
        if (!Files.isRegularFile(f)) {
            return null;
        }
        try (InputStream in = Files.newInputStream(f)) {
            Properties p = new Properties();
            p.load(in);
            String name = p.getProperty("name", "");
            String uuid = p.getProperty("uuid", "");
            String rt = p.getProperty("refreshToken", "");
            if (name.isEmpty() || uuid.isEmpty() || rt.isEmpty()) {
                return null;
            }
            return new Account(name, uuid, rt);
        } catch (IOException e) {
            return null;
        }
    }

    public static void saveAccount(Path baseDir, Account a) throws IOException {
        Files.createDirectories(baseDir);
        Properties p = new Properties();
        p.setProperty("name", a.name());
        p.setProperty("uuid", a.uuid());
        p.setProperty("refreshToken", a.refreshToken());
        Path f = accountFile(baseDir);
        try (java.io.OutputStream out = Files.newOutputStream(f)) {
            p.store(out, "BushLaunch - konto Microsoft (nie udostepniaj tego pliku)");
        }
        try {
            Files.setPosixFilePermissions(f, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | IOException ignored) {
            // Windows - dostęp ogranicza już folder użytkownika w %APPDATA%
        }
    }

    public static void deleteAccount(Path baseDir) {
        try {
            Files.deleteIfExists(accountFile(baseDir));
        } catch (IOException ignored) {
            // nie udało się usunąć - zostanie nadpisany przy następnym logowaniu
        }
    }

    // ------------------------------------------------------------------ HTTP

    private static Resp post(String url, String contentType, String body) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "BushLaunch/" + BushLaunch.LAUNCHER_VERSION)
                .header("Content-Type", contentType)
                .header("Accept", JSON)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        return wrap(HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)));
    }

    private static Resp get(String url, String bearer) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "BushLaunch/" + BushLaunch.LAUNCHER_VERSION)
                .header("Authorization", "Bearer " + bearer)
                .header("Accept", JSON)
                .GET().build();
        return wrap(HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)));
    }

    private static Resp wrap(HttpResponse<String> r) {
        Map<String, Object> m = null;
        try {
            m = Json.map(Json.parse(r.body()));
        } catch (RuntimeException ignored) {
            // odpowiedź nie jest JSON-em - zostaje pusta mapa
        }
        return new Resp(r.statusCode(), r.body() == null ? "" : r.body(), m == null ? Map.of() : m);
    }

    private static String form(String... kv) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(URLEncoder.encode(kv[i], StandardCharsets.UTF_8)).append('=')
                    .append(URLEncoder.encode(kv[i + 1], StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    /** Tekst jako literał JSON (w cudzysłowie, z ucieczkami). */
    private static String q(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            if (c == '"' || c == '\\') {
                sb.append('\\').append(c);
            } else if (c < 0x20) {
                sb.append(String.format("\\u%04x", (int) c));
            } else {
                sb.append(c);
            }
        }
        return sb.append('"').toString();
    }
}
