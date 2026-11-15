package pl.bushstar.bushlaunch;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Logowanie kontem Microsoft (premium). Główna metoda: przeglądarka (OAuth + PKCE, przekierowanie na
 * localhost) - gracz widzi stronę Microsoftu z wyborem konta. Zapasowa: "device code" (kod do wpisania).
 * W obu przypadkach launcher w tle wymienia tokeny: Microsoft -> Xbox Live -> XSTS -> Minecraft.
 *
 * Wymaga własnego Client ID aplikacji z Azure (publiczny klient) zatwierdzonego przez Mojang -
 * patrz README. Hasło gracza nigdy nie trafia do launchera; zapisujemy tylko refresh token.
 */
public final class MicrosoftAuth {

    /** Zapisane konto: nazwa, UUID (bez myślników) i refresh token Microsoftu. */
    public record Account(String name, String uuid, String refreshToken) {
    }

    /** Kod do wpisania przez gracza na stronie Microsoftu. */
    public record DeviceCode(String userCode, String verificationUri, String verificationUriComplete,
                             String deviceCode, int interval, int expiresIn) {
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
    private static final String AUTH_URL = System.getProperty("bushlaunch.url.authorize",
            "https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize");
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

    // ------------------------------------------------------------------ logowanie w przeglądarce (PKCE)

    /**
     * Jedno logowanie w przeglądarce: lokalny serwer na 127.0.0.1 odbiera przekierowanie z Microsoftu
     * (http://localhost:PORT), a kod autoryzacji jest wymieniany na tokeny z weryfikatorem PKCE.
     * Zamknij obiekt (close) po zakończeniu - zatrzymuje serwer.
     */
    public static final class BrowserLogin implements AutoCloseable {
        private final String clientId;
        private final String verifier;
        private final String redirectUri;
        private final String state;
        private final String authUrl;
        private final HttpServer server;
        private final CompletableFuture<String> code = new CompletableFuture<>();

        private BrowserLogin(String clientId) throws IOException {
            this.clientId = clientId;
            SecureRandom rnd = new SecureRandom();
            this.verifier = randomUrlToken(rnd, 48);
            this.state = randomUrlToken(rnd, 24);
            // port wybiera system; Microsoft dla http://localhost ignoruje numer portu
            this.server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            this.redirectUri = "http://localhost:" + server.getAddress().getPort();
            this.authUrl = AUTH_URL + "?" + form("client_id", clientId, "response_type", "code",
                    "redirect_uri", redirectUri, "response_mode", "query", "scope", SCOPE,
                    "state", state, "code_challenge", challenge(verifier), "code_challenge_method", "S256",
                    "prompt", "select_account");
            server.createContext("/", this::handle);
            server.start();
        }

        /** Adres strony logowania Microsoftu (z wyborem konta). */
        public String authUrl() {
            return authUrl;
        }

        private void handle(HttpExchange ex) throws IOException {
            try {
                Map<String, String> q = parseQuery(ex.getRequestURI().getRawQuery());
                if (!"GET".equals(ex.getRequestMethod()) || (!q.containsKey("code") && !q.containsKey("error"))) {
                    reply(ex, 404, "text/plain; charset=utf-8", "Nie znaleziono.");
                    return;
                }
                if (!state.equals(q.get("state"))) {
                    // obce lub stare żądanie - ignorujemy, nie przerywamy oczekiwania na właściwe
                    reply(ex, 400, "text/html; charset=utf-8", page(false, "Nieprawidłowa odpowiedź logowania. "
                            + "Wróć do launchera i spróbuj jeszcze raz."));
                    return;
                }
                if (q.containsKey("error")) {
                    String msg = callbackError(q.get("error"), q.get("error_description"));
                    reply(ex, 200, "text/html; charset=utf-8", page(false, msg));
                    code.completeExceptionally(new AuthException(msg, false));
                    return;
                }
                reply(ex, 200, "text/html; charset=utf-8", page(true, "Możesz zamknąć tę kartę i wrócić do launchera."));
                code.complete(q.get("code"));
            } finally {
                ex.close();
            }
        }

        /** Czeka na zatwierdzenie w przeglądarce i kończy logowanie. Blokuje wątek - wołać poza wątkiem UI. */
        public Login await(BooleanSupplier cancelled, Consumer<String> status)
                throws IOException, InterruptedException {
            status.accept("Czekam na zalogowanie w przeglądarce...");
            long deadline = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(5);
            String authCode;
            while (true) {
                if (cancelled.getAsBoolean()) {
                    throw new CancelledException();
                }
                if (System.currentTimeMillis() > deadline) {
                    throw new AuthException("Minął czas na zalogowanie. Spróbuj jeszcze raz.", false);
                }
                try {
                    authCode = code.get(250, TimeUnit.MILLISECONDS);
                    break;
                } catch (TimeoutException e) {
                    // czekamy dalej
                } catch (ExecutionException e) {
                    Throwable c = e.getCause();
                    if (c instanceof IOException io) {
                        throw io;
                    }
                    throw new IOException(c);
                }
            }
            status.accept("Logowanie do Microsoft...");
            Resp r = post(TOKEN_URL, FORM, form("client_id", clientId, "grant_type", "authorization_code",
                    "code", authCode, "redirect_uri", redirectUri, "code_verifier", verifier, "scope", SCOPE));
            String access = Json.str(r.json().get("access_token"));
            if (r.status() != 200 || access == null) {
                throw new AuthException(oauthError(r), false);
            }
            return exchange(access, Json.str(r.json().get("refresh_token")), status);
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }

    /** Uruchamia lokalny serwer i przygotowuje adres logowania. Rzuca IOException, gdy nie da się go uruchomić. */
    public static BrowserLogin startBrowserLogin(String clientId) throws IOException {
        return new BrowserLogin(clientId);
    }

    private static String randomUrlToken(SecureRandom rnd, int bytes) {
        byte[] b = new byte[bytes];
        rnd.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static String challenge(String verifier) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(h);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Map<String, String> parseQuery(String raw) {
        Map<String, String> out = new HashMap<>();
        if (raw == null || raw.isEmpty()) {
            return out;
        }
        for (String part : raw.split("&")) {
            int i = part.indexOf('=');
            String k = i < 0 ? part : part.substring(0, i);
            String v = i < 0 ? "" : part.substring(i + 1);
            out.put(URLDecoder.decode(k, StandardCharsets.UTF_8), URLDecoder.decode(v, StandardCharsets.UTF_8));
        }
        return out;
    }

    private static void reply(HttpExchange ex, int status, String type, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", type);
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.getResponseHeaders().set("Connection", "close");
        ex.sendResponseHeaders(status, b.length);
        try (java.io.OutputStream out = ex.getResponseBody()) {
            out.write(b);
        }
    }

    private static String callbackError(String err, String desc) {
        String first = desc == null ? "" : desc.split("\\r?\\n")[0];
        String all = (err == null ? "" : err) + " " + (desc == null ? "" : desc);
        if ("access_denied".equals(err)) {
            return "Logowanie zostało anulowane lub odrzucone.";
        }
        if (all.contains("AADSTS50011") || all.contains("redirect_uri")) {
            return "Aplikacja Azure nie ma adresu przekierowania http://localhost. W Azure: Authentication -> "
                    + "Add a platform -> Mobile and desktop applications -> http://localhost. (" + first + ")";
        }
        if (all.contains("AADSTS700016") || all.contains("invalid_client")) {
            return "Nieprawidłowy Client ID aplikacji Microsoft. (" + first + ")";
        }
        return first.isEmpty() ? "Microsoft zwrócił błąd: " + err : "Microsoft: " + first;
    }

    /** Strona wyświetlana w przeglądarce po powrocie z logowania. */
    private static String page(boolean ok, String text) {
        String color = ok ? "#ff7a1f" : "#ff6b5a";
        String title = ok ? "Zalogowano!" : "Nie udało się zalogować";
        String safe = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return "<!doctype html><html lang=\"pl\"><head><meta charset=\"utf-8\"><title>BushLaunch</title>"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><style>"
                + "html,body{height:100%;margin:0}body{display:flex;align-items:center;justify-content:center;"
                + "background:radial-gradient(circle at 50% 35%,#2a160c,#0c0807 65%);color:#f4eeea;"
                + "font-family:Inter,'Segoe UI',system-ui,sans-serif}"
                + ".c{max-width:420px;text-align:center;padding:40px 36px;border:1px solid #34251d;border-radius:18px;"
                + "background:linear-gradient(#1f1511,#150e0b);box-shadow:0 20px 60px rgba(0,0,0,.5)}"
                + "h1{margin:14px 0 8px;font-size:26px;color:" + color + "}p{margin:0;color:#9c8f89;line-height:1.5}"
                + ".b{font-size:20px;font-weight:700;letter-spacing:.3px}.b span{color:#ff7a1f}"
                + "</style></head><body><div class=\"c\"><div class=\"b\"><span>Bush</span>Launch</div>"
                + "<h1>" + title + "</h1><p>" + safe + "</p></div></body></html>";
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
        if (uri == null || uri.isBlank()) {
            uri = "https://www.microsoft.com/link";
        }
        // Microsoft może zwrócić adres z kodem już wpisanym - użytkownik trafia
        // bezpośrednio na ekran zatwierdzenia logowania.
        String completeUri = Json.str(r.json().get("verification_uri_complete"));
        return new DeviceCode(userCode, uri, completeUri, deviceCode,
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
        if (all.contains("AADSTS50011")) {
            return "Aplikacja Azure nie ma adresu przekierowania http://localhost (Authentication -> Mobile and "
                    + "desktop applications). (" + first + ")";
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
