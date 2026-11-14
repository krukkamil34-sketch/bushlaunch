package pl.bushstar.bushlaunch;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * Automatyczne aktualizacje: sprawdza plik latest.json w GitHub Releases, pobiera instalator,
 * sprawdza sumę SHA-256 i uruchamia go po cichu (instalator sam zamyka i ponownie otwiera launcher).
 *
 * Adres latest.json trafia do pliku update.properties w jar-ze podczas budowania w GitHub Actions
 * (z nazwy repozytorium). W zwykłym buildzie lokalnym pliku nie ma, więc aktualizacje są wyłączone.
 */
public final class Updater {

    /** Dane o najnowszej wersji z latest.json. */
    public record Info(String version, String url, String sha256, String notes) {
    }

    public interface Progress {
        void update(long done, long total);
    }

    private static final String ALLOWED_PREFIX = "https://github.com/";
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private Updater() {
    }

    /** Adres latest.json albo null, gdy aktualizacje nie są skonfigurowane. */
    public static String endpoint() {
        try (InputStream in = Updater.class.getResourceAsStream("/update.properties")) {
            if (in == null) {
                return null;
            }
            Properties p = new Properties();
            p.load(in);
            String url = p.getProperty("url", "").trim();
            return url.startsWith(ALLOWED_PREFIX) ? url : null;
        } catch (IOException e) {
            return null;
        }
    }

    public static boolean enabled() {
        return endpoint() != null;
    }

    /** Strona wydania (do ręcznego pobrania), wyprowadzona z adresu latest.json. */
    public static String releasePage() {
        String e = endpoint();
        if (e == null) {
            return null;
        }
        int i = e.indexOf("/releases/");
        return i < 0 ? e : e.substring(0, i) + "/releases/latest";
    }

    /** Zwraca informacje o nowszej wersji albo null, jeśli mamy już najnowszą. */
    public static Info check(String currentVersion) throws IOException, InterruptedException {
        String endpoint = endpoint();
        if (endpoint == null) {
            return null;
        }
        HttpRequest req = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", "BushLaunch/" + currentVersion)
                .header("Cache-Control", "no-cache")
                .GET().build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) {
            throw new IOException("serwer aktualizacji odpowiedział kodem " + res.statusCode());
        }
        Map<String, Object> m;
        try {
            m = Json.map(Json.parse(res.body()));
        } catch (RuntimeException e) {
            throw new IOException("nieprawidłowy plik latest.json", e);
        }
        String version = Json.str(m.get("version"));
        String url = Json.str(m.get("url"));
        String sha = Json.str(m.get("sha256"));
        String notes = Json.str(m.get("notes"));
        if (version == null || url == null || sha == null || !url.startsWith(ALLOWED_PREFIX)) {
            throw new IOException("niepełny lub niezaufany plik latest.json");
        }
        if (compare(version, currentVersion) <= 0) {
            return null;
        }
        return new Info(version, url, sha.toLowerCase(Locale.ROOT), notes == null ? "" : notes);
    }

    /** Porównanie wersji liczbowych: 0.3.10 > 0.3.9. */
    static int compare(String a, String b) {
        List<Integer> x = parts(a);
        List<Integer> y = parts(b);
        for (int i = 0; i < Math.max(x.size(), y.size()); i++) {
            int p = i < x.size() ? x.get(i) : 0;
            int q = i < y.size() ? y.get(i) : 0;
            if (p != q) {
                return Integer.compare(p, q);
            }
        }
        return 0;
    }

    private static List<Integer> parts(String v) {
        List<Integer> out = new ArrayList<>();
        for (String s : v.trim().replaceFirst("^[vV]", "").split("[.\\-+]")) {
            try {
                out.add(Integer.parseInt(s));
            } catch (NumberFormatException e) {
                break;
            }
        }
        return out;
    }

    /** Czy launcher działa z instalacji (obok jest unins000.exe), a nie z wersji przenośnej / jar-a. */
    public static boolean isInstalled() {
        Path dir = appDir();
        return dir != null && Files.isRegularFile(dir.resolve("unins000.exe"));
    }

    private static Path appDir() {
        String exe = System.getProperty("jpackage.app-path");
        if (exe == null || exe.isBlank()) {
            return null;
        }
        return Path.of(exe).toAbsolutePath().getParent();
    }

    /** Pobiera instalator do katalogu tymczasowego i sprawdza SHA-256. */
    public static Path download(Info info, Progress progress) throws Exception {
        Path dir = Files.createTempDirectory("bushlaunch-update");
        Path file = dir.resolve("BushLaunch-Setup.exe");
        HttpRequest req = HttpRequest.newBuilder(URI.create(info.url()))
                .timeout(Duration.ofMinutes(10))
                .header("User-Agent", "BushLaunch")
                .GET().build();
        HttpResponse<InputStream> res = HTTP.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (res.statusCode() != 200) {
            throw new IOException("pobieranie nie powiodło się (kod " + res.statusCode() + ")");
        }
        long total = res.headers().firstValueAsLong("Content-Length").orElse(-1);
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        Path part = dir.resolve("download.part");
        try (InputStream in = res.body(); var out = Files.newOutputStream(part)) {
            byte[] buf = new byte[64 * 1024];
            long done = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                md.update(buf, 0, n);
                done += n;
                if (progress != null) {
                    progress.update(done, total);
                }
            }
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : md.digest()) {
            hex.append(String.format("%02x", b));
        }
        if (!hex.toString().equals(info.sha256())) {
            Files.deleteIfExists(part);
            throw new IOException("suma SHA-256 pobranego pliku się nie zgadza - przerwano aktualizację");
        }
        Files.move(part, file, StandardCopyOption.REPLACE_EXISTING);
        return file;
    }

    /** Uruchamia instalator po cichu i kończy launcher; instalator otworzy go ponownie. */
    public static void installAndExit(Path setup) throws IOException {
        List<String> cmd = new ArrayList<>();
        cmd.add(setup.toString());
        cmd.add("/VERYSILENT");
        cmd.add("/SUPPRESSMSGBOXES");
        cmd.add("/NORESTART");
        cmd.add("/CLOSEAPPLICATIONS");
        cmd.add("/RELAUNCH=1");
        Path dir = appDir();
        if (dir != null) {
            cmd.add("/DIR=" + dir);
            boolean machine = false;
            for (String env : new String[]{"ProgramFiles", "ProgramFiles(x86)", "ProgramW6432"}) {
                String pf = System.getenv(env);
                if (pf != null && dir.startsWith(Path.of(pf))) {
                    machine = true;
                }
            }
            // ten sam tryb instalacji co poprzednio, żeby nie powstała druga kopia
            cmd.add(machine ? "/ALLUSERS" : "/CURRENTUSER");
        }
        new ProcessBuilder(cmd).start();
        try {
            Thread.sleep(600);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        System.exit(0);
    }
}
