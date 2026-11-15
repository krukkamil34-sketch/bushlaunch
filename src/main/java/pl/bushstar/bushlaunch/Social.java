package pl.bushstar.bushlaunch;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Klient serwera znajomych (friends-server): obecność, lista znajomych i zaproszenia, które druga strona musi
 * potwierdzić w launcherze.
 *
 * Tożsamość gracza potwierdza serwer sesji Mojang (tak jak robią to serwery Minecrafta): launcher "dołącza"
 * do losowego serverId podanego przez nasz serwer, a nasz serwer pyta Mojang, czy ten nick tam dołączył.
 * Token gry trafia więc tylko do Mojangu - nigdy do serwera znajomych.
 */
final class Social {

    record Friend(String uuid, String name, String state, String info) {
    }

    record Request(String uuid, String name) {
    }

    record Snapshot(List<Friend> friends, List<Request> incoming, List<String> outgoing) {
    }

    /** Dane do jednorazowego potwierdzenia konta: nick, UUID i token gry (idzie tylko do Mojangu). */
    record Creds(String name, String uuid, String mcToken) {
    }

    interface CredsSupplier {
        Creds get() throws Exception;
    }

    /** Błąd z komunikatem gotowym do pokazania graczowi. */
    static final class SocialException extends IOException {
        final int status;

        SocialException(int status, String message) {
            super(message);
            this.status = status;
        }
    }

    private static final String JOIN_URL = System.getProperty("bushlaunch.url.sessionjoin",
            "https://sessionserver.mojang.com/session/minecraft/join");

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    private final String base;
    private final CredsSupplier creds;
    private final String userAgent;

    private String token;
    private long tokenUntil;

    Social(String baseUrl, CredsSupplier creds, String userAgent) {
        String b = baseUrl == null ? "" : baseUrl.trim();
        while (b.endsWith("/")) {
            b = b.substring(0, b.length() - 1);
        }
        this.base = b;
        this.creds = creds;
        this.userAgent = userAgent;
    }

    // ------------------------------------------------------------------ API

    Snapshot ping(String state, String info) throws IOException {
        return parse(call("POST", "/v1/ping", "{\"state\":" + q(state) + ",\"info\":" + q(info) + "}", true));
    }

    Snapshot fetch() throws IOException {
        return parse(call("GET", "/v1/social", null, true));
    }

    /** Wysyła zaproszenie. Zwraca true, gdy ta osoba już wcześniej zaprosiła nas i zostaliście znajomymi od razu. */
    boolean request(String name) throws IOException {
        Object r = Json.parse(call("POST", "/v1/friends/request", "{\"name\":" + q(name) + "}", true));
        return Json.bool(Json.get(r, "accepted"), false);
    }

    void accept(String uuid) throws IOException {
        call("POST", "/v1/friends/accept", "{\"uuid\":" + q(uuid) + "}", true);
    }

    void decline(String uuid) throws IOException {
        call("POST", "/v1/friends/decline", "{\"uuid\":" + q(uuid) + "}", true);
    }

    void cancel(String name) throws IOException {
        call("POST", "/v1/friends/cancel", "{\"name\":" + q(name) + "}", true);
    }

    void remove(String uuid) throws IOException {
        call("POST", "/v1/friends/remove", "{\"uuid\":" + q(uuid) + "}", true);
    }

    /** Przy zamykaniu launchera: od razu pokazuje nas jako offline (bez logowania, krótki limit czasu). */
    void offline() {
        String t;
        synchronized (this) {
            t = token;
        }
        if (t == null) {
            return;
        }
        try {
            HttpRequest r = HttpRequest.newBuilder(URI.create(base + "/v1/offline"))
                    .timeout(Duration.ofMillis(1500))
                    .header("Authorization", "Bearer " + t)
                    .header("User-Agent", userAgent)
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            HTTP.send(r, HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) {
            // zamykamy się - nic więcej nie zrobimy
        }
    }

    // ------------------------------------------------------------------ logowanie

    private synchronized String session() throws IOException {
        if (token == null || System.currentTimeMillis() > tokenUntil - 60_000) {
            login();
        }
        return token;
    }

    private synchronized void forget() {
        token = null;
    }

    private void login() throws IOException {
        Creds c;
        try {
            c = creds.get();
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(e.getMessage() == null ? "Nie udało się odświeżyć konta." : e.getMessage(), e);
        }
        Object ch = Json.parse(rawCall("GET", "/v1/auth/challenge?name=" + URLEncoder.encode(c.name(), StandardCharsets.UTF_8),
                null, null));
        String serverId = Json.str(Json.get(ch, "serverId"));
        if (serverId == null || serverId.isEmpty()) {
            throw new IOException("Serwer znajomych zwrócił błędną odpowiedź.");
        }
        joinMojang(c, serverId);
        Object r = Json.parse(rawCall("POST", "/v1/auth/login",
                "{\"name\":" + q(c.name()) + ",\"serverId\":" + q(serverId) + "}", null));
        String t = Json.str(Json.get(r, "token"));
        if (t == null || t.isEmpty()) {
            throw new IOException("Serwer znajomych nie wydał sesji.");
        }
        long ttl = Json.num(Json.get(r, "expiresIn"), 3600);
        token = t;
        tokenUntil = System.currentTimeMillis() + ttl * 1000L;
    }

    private void joinMojang(Creds c, String serverId) throws IOException {
        String body = "{\"accessToken\":" + q(c.mcToken()) + ",\"selectedProfile\":" + q(c.uuid())
                + ",\"serverId\":" + q(serverId) + "}";
        try {
            HttpRequest r = HttpRequest.newBuilder(URI.create(JOIN_URL))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", userAgent)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> resp = HTTP.send(r, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                throw new SocialException(resp.statusCode(), "Mojang nie potwierdził konta (kod " + resp.statusCode() + ").");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Przerwano.", e);
        }
    }

    // ------------------------------------------------------------------ HTTP

    private String call(String method, String path, String body, boolean auth) throws IOException {
        try {
            return rawCall(method, path, body, session());
        } catch (SocialException e) {
            if (e.status != 401) {
                throw e;
            }
        }
        // sesja wygasła (np. restart serwera) - logujemy się jeszcze raz i powtarzamy
        forget();
        return rawCall(method, path, body, session());
    }

    private String rawCall(String method, String path, String body, String bearer) throws IOException {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path))
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", userAgent)
                .header("Accept", "application/json");
        if (bearer != null) {
            b.header("Authorization", "Bearer " + bearer);
        }
        if (body != null) {
            b.header("Content-Type", "application/json");
            b.method(method, HttpRequest.BodyPublishers.ofString(body));
        } else if ("POST".equals(method)) {
            b.POST(HttpRequest.BodyPublishers.noBody());
        } else {
            b.GET();
        }
        try {
            HttpResponse<String> r = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() / 100 != 2) {
                String msg = null;
                try {
                    msg = Json.str(Json.get(Json.parse(r.body()), "error"));
                } catch (Exception ignored) {
                    // odpowiedź nie jest JSON-em
                }
                throw new SocialException(r.statusCode(),
                        msg != null ? msg : "Serwer znajomych odpowiedział kodem " + r.statusCode() + ".");
            }
            return r.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Przerwano.", e);
        } catch (SocialException e) {
            throw e;
        } catch (IOException e) {
            throw new IOException("Brak połączenia z serwerem znajomych.", e);
        } catch (IllegalArgumentException e) {
            throw new IOException("Błędny adres serwera znajomych.", e);
        }
    }

    // ------------------------------------------------------------------ JSON

    private static Snapshot parse(String text) {
        Object root = Json.parse(text);
        List<Friend> friends = new ArrayList<>();
        for (Object o : items(Json.get(root, "friends"))) {
            Map<String, Object> m = Json.map(o);
            if (m == null) {
                continue;
            }
            String name = Json.str(m.get("name"));
            if (name != null) {
                friends.add(new Friend(Json.str(m.get("uuid")), name, orDefault(Json.str(m.get("state")), "offline"),
                        orDefault(Json.str(m.get("info")), "")));
            }
        }
        List<Request> incoming = new ArrayList<>();
        for (Object o : items(Json.get(root, "incoming"))) {
            Map<String, Object> m = Json.map(o);
            if (m == null) {
                continue;
            }
            String name = Json.str(m.get("name"));
            if (name != null) {
                incoming.add(new Request(Json.str(m.get("uuid")), name));
            }
        }
        List<String> outgoing = new ArrayList<>();
        for (Object o : items(Json.get(root, "outgoing"))) {
            String name = Json.str(Json.get(o, "name"));
            if (name != null) {
                outgoing.add(name);
            }
        }
        return new Snapshot(friends, incoming, outgoing);
    }

    private static List<Object> items(Object o) {
        List<Object> l = Json.list(o);
        return l == null ? List.of() : l;
    }

    private static String orDefault(String s, String def) {
        return s == null ? def : s;
    }

    /** Tekst jako literał JSON. */
    private static String q(String s) {
        if (s == null) {
            return "\"\"";
        }
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }
}
