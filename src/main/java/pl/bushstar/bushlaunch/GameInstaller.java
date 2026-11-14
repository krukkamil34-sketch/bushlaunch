package pl.bushstar.bushlaunch;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Pobiera Minecrafta (klient, biblioteki, assets, natives) i buduje komendę uruchomienia. */
public final class GameInstaller {

    public interface Listener {
        void log(String message);

        void progress(int done, int total, String what);
    }

    public record VersionEntry(String id, String type, String url) {
        @Override
        public String toString() {
            return id;
        }
    }

    public record Prepared(String versionId, String versionType, String mainClass, String assetsIndex,
                           Path assetsRoot, Path gameAssets, int javaMajor, String classpath,
                           List<String> jvmArgs, List<String> gameArgs, Path nativesDir) {
    }

    /** Dane sesji przekazywane grze: nick, UUID i token (offline: "0"; Microsoft: token Minecraft, typ "msa"). */
    public record Session(String name, String uuid, String accessToken, String userType) {
        public static Session offline(String name) {
            return new Session(name, offlineUuid(name), "0", "legacy");
        }
    }

    private record Task(String url, Path target, long size) {
    }

    private record NativeJar(Path jar, List<String> exclude) {
    }

    private static final String MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    private static final String ASSETS_URL =
            System.getProperty("bushlaunch.assetsUrl", "https://resources.download.minecraft.net/");
    private static final String USER_AGENT = "BushLaunch/" + BushLaunch.LAUNCHER_VERSION;

    private final Path baseDir;
    private final Path gameDir;
    private final Listener listener;
    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    public GameInstaller(Path baseDir, Listener listener) {
        this.baseDir = baseDir;
        this.gameDir = baseDir.resolve("game");
        this.listener = listener;
    }

    public Path gameDir() {
        return gameDir;
    }

    // ------------------------------------------------------------------ lista wersji

    public List<VersionEntry> fetchVersions() throws IOException, InterruptedException {
        Map<String, Object> root = Json.map(Json.parse(getString(MANIFEST_URL)));
        List<VersionEntry> out = new ArrayList<>();
        for (Object o : Json.list(root.get("versions"))) {
            Map<String, Object> v = Json.map(o);
            out.add(new VersionEntry(Json.str(v.get("id")), Json.str(v.get("type")), Json.str(v.get("url"))));
        }
        return out;
    }

    /** Wersje już pobrane na dysk (działa bez internetu). */
    public List<VersionEntry> installedVersions() {
        List<VersionEntry> out = new ArrayList<>();
        Path dir = baseDir.resolve("versions");
        if (!Files.isDirectory(dir)) {
            return out;
        }
        try (Stream<Path> s = Files.list(dir)) {
            for (Path d : s.filter(Files::isDirectory).sorted(Comparator.reverseOrder()).toList()) {
                String id = d.getFileName().toString();
                Path json = d.resolve(id + ".json");
                if (Files.exists(json)) {
                    out.add(new VersionEntry(id, readType(json), null));
                }
            }
        } catch (IOException ignored) {
            // brak dostępu - zwracamy to, co mamy
        }
        return out;
    }

    private static String readType(Path json) {
        try {
            String t = Json.str(Json.map(Json.parse(Files.readString(json))).get("type"));
            return t == null ? "release" : t;
        } catch (Exception e) {
            return "release";
        }
    }

    // ------------------------------------------------------------------ instalacja

    public Prepared install(VersionEntry v) throws IOException, InterruptedException {
        Path vdir = baseDir.resolve("versions").resolve(v.id());
        Path vjson = vdir.resolve(v.id() + ".json");
        if (!Files.exists(vjson)) {
            if (v.url() == null) {
                throw new IOException("Wersja " + v.id() + " nie jest pobrana, a brak połączenia z internetem");
            }
            listener.log("Pobieranie opisu wersji " + v.id() + "...");
            download(new Task(v.url(), vjson, -1));
        }
        Map<String, Object> ver = Json.map(Json.parse(Files.readString(vjson, StandardCharsets.UTF_8)));
        if (ver.containsKey("inheritsFrom")) {
            throw new IOException("Wersje modowane (inheritsFrom) nie są jeszcze obsługiwane");
        }

        Map<Path, Task> tasks = new LinkedHashMap<>();
        LinkedHashSet<String> classpath = new LinkedHashSet<>();
        List<NativeJar> nativeJars = new ArrayList<>();

        // biblioteki
        Path libsDir = baseDir.resolve("libraries");
        List<Object> libs = Json.list(ver.get("libraries"));
        if (libs != null) {
            for (Object o : libs) {
                Map<String, Object> lib = Json.map(o);
                if (lib == null || !rulesAllow(Json.list(lib.get("rules")))) {
                    continue;
                }
                Map<String, Object> dl = Json.map(lib.get("downloads"));
                if (dl == null) {
                    continue;
                }
                Map<String, Object> art = Json.map(dl.get("artifact"));
                if (art != null && Json.str(art.get("path")) != null) {
                    Path p = libsDir.resolve(Json.str(art.get("path")));
                    tasks.putIfAbsent(p, new Task(Json.str(art.get("url")), p, Json.num(art.get("size"), -1)));
                    classpath.add(p.toString());
                }
                Map<String, Object> natives = Json.map(lib.get("natives"));
                if (natives != null) {
                    String classifier = Json.str(natives.get(osName()));
                    Map<String, Object> classifiers = Json.map(dl.get("classifiers"));
                    if (classifier != null && classifiers != null) {
                        classifier = classifier.replace("${arch}", is64() ? "64" : "32");
                        Map<String, Object> nat = Json.map(classifiers.get(classifier));
                        if (nat != null && Json.str(nat.get("path")) != null) {
                            Path p = libsDir.resolve(Json.str(nat.get("path")));
                            tasks.putIfAbsent(p, new Task(Json.str(nat.get("url")), p, Json.num(nat.get("size"), -1)));
                            List<String> exclude = new ArrayList<>();
                            List<Object> ex = Json.list(Json.get(lib, "extract", "exclude"));
                            if (ex != null) {
                                for (Object e : ex) {
                                    exclude.add(String.valueOf(e));
                                }
                            }
                            nativeJars.add(new NativeJar(p, exclude));
                        }
                    }
                }
            }
        }

        // klient
        Map<String, Object> client = Json.map(Json.get(ver, "downloads", "client"));
        if (client == null) {
            throw new IOException("Wersja " + v.id() + " nie ma pliku klienta do pobrania");
        }
        Path clientJar = vdir.resolve(v.id() + ".jar");
        tasks.putIfAbsent(clientJar, new Task(Json.str(client.get("url")), clientJar, Json.num(client.get("size"), -1)));
        classpath.add(clientJar.toString());

        // assets
        Map<String, Object> assetIndex = Json.map(ver.get("assetIndex"));
        if (assetIndex == null) {
            throw new IOException("Brak informacji o assets w wersji " + v.id());
        }
        String assetsId = Json.str(assetIndex.get("id"));
        Path assetsDir = baseDir.resolve("assets");
        Path objectsDir = assetsDir.resolve("objects");
        Path indexFile = assetsDir.resolve("indexes").resolve(assetsId + ".json");
        listener.log("Sprawdzanie plików gry...");
        download(new Task(Json.str(assetIndex.get("url")), indexFile, Json.num(assetIndex.get("size"), -1)));
        Map<String, Object> index = Json.map(Json.parse(Files.readString(indexFile, StandardCharsets.UTF_8)));
        Map<String, Object> objects = Json.map(index.get("objects"));
        if (objects != null) {
            for (Object o : objects.values()) {
                Map<String, Object> obj = Json.map(o);
                String hash = Json.str(obj.get("hash"));
                String prefix = hash.substring(0, 2);
                Path p = objectsDir.resolve(prefix).resolve(hash);
                tasks.putIfAbsent(p, new Task(ASSETS_URL + prefix + "/" + hash, p, Json.num(obj.get("size"), -1)));
            }
        }

        runTasks(tasks.values());

        // natives
        Path nativesDir = vdir.resolve("natives");
        extractNatives(nativeJars, nativesDir);

        // stare wersje (assets "virtual" / "map_to_resources")
        boolean virtual = Json.bool(index.get("virtual"), false);
        boolean mapToResources = Json.bool(index.get("map_to_resources"), false);
        Path virtualDir = assetsDir.resolve("virtual").resolve("legacy");
        if ((virtual || mapToResources) && objects != null) {
            Path target = mapToResources ? gameDir.resolve("resources") : virtualDir;
            for (Map.Entry<String, Object> e : objects.entrySet()) {
                String hash = Json.str(Json.map(e.getValue()).get("hash"));
                Path src = objectsDir.resolve(hash.substring(0, 2)).resolve(hash);
                Path dst = target.resolve(e.getKey()).normalize();
                if (dst.startsWith(target.normalize()) && !Files.exists(dst)) {
                    Files.createDirectories(dst.getParent());
                    Files.copy(src, dst);
                }
            }
        }

        // argumenty
        String mainClass = Json.str(ver.get("mainClass"));
        List<String> jvm;
        List<String> game;
        Map<String, Object> arguments = Json.map(ver.get("arguments"));
        if (arguments != null) {
            game = collectArgs(Json.list(arguments.get("game")));
            jvm = arguments.get("jvm") != null ? collectArgs(Json.list(arguments.get("jvm"))) : defaultJvmArgs();
        } else {
            String old = Json.str(ver.get("minecraftArguments"));
            game = new ArrayList<>(List.of((old == null ? "" : old).trim().split("\\s+")));
            jvm = defaultJvmArgs();
        }

        Path gameAssets = virtual ? virtualDir : assetsDir;
        return new Prepared(v.id(), Json.str(ver.get("type")), mainClass, assetsId,
                gameAssets, gameAssets, (int) Json.num(Json.get(ver, "javaVersion", "majorVersion"), 8),
                String.join(java.io.File.pathSeparator, classpath), jvm, game, nativesDir);
    }

    private static List<String> defaultJvmArgs() {
        return new ArrayList<>(List.of(
                "-Djava.library.path=${natives_directory}",
                "-Dminecraft.launcher.brand=${launcher_name}",
                "-Dminecraft.launcher.version=${launcher_version}",
                "-cp", "${classpath}"));
    }

    // ------------------------------------------------------------------ komenda uruchomienia

    public List<String> buildCommand(Prepared p, Path javaExe, String username, int ramMb) throws IOException {
        return buildCommand(p, javaExe, Session.offline(username), ramMb);
    }

    public List<String> buildCommand(Prepared p, Path javaExe, Session session, int ramMb) throws IOException {
        Files.createDirectories(gameDir);
        boolean online = "msa".equals(session.userType());
        Map<String, String> vars = new HashMap<>();
        vars.put("auth_player_name", session.name());
        vars.put("version_name", p.versionId());
        vars.put("game_directory", gameDir.toString());
        vars.put("assets_root", p.assetsRoot().toString());
        vars.put("game_assets", p.gameAssets().toString());
        vars.put("assets_index_name", p.assetsIndex());
        vars.put("auth_uuid", session.uuid());
        vars.put("auth_access_token", session.accessToken());
        vars.put("auth_session", online ? "token:" + session.accessToken() + ":" + session.uuid() : "0");
        vars.put("auth_xuid", "0");
        vars.put("clientid", "0");
        vars.put("user_type", session.userType());
        vars.put("user_properties", "{}");
        vars.put("version_type", p.versionType() == null ? "release" : p.versionType());
        vars.put("natives_directory", p.nativesDir().toString());
        vars.put("launcher_name", "BushLaunch");
        vars.put("launcher_version", BushLaunch.LAUNCHER_VERSION);
        vars.put("classpath", p.classpath());
        vars.put("classpath_separator", java.io.File.pathSeparator);
        vars.put("library_directory", baseDir.resolve("libraries").toString());

        List<String> cmd = new ArrayList<>();
        cmd.add(javaExe.toString());
        cmd.add("-Xmx" + ramMb + "M");
        cmd.add("-Xms" + Math.min(512, ramMb) + "M");
        cmd.add("-Dlog4j2.formatMsgNoLookups=true");
        for (String a : p.jvmArgs()) {
            cmd.add(subst(a, vars));
        }
        cmd.add(p.mainClass());
        for (String a : p.gameArgs()) {
            cmd.add(subst(a, vars));
        }
        return cmd;
    }

    public static String offlineUuid(String username) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8))
                .toString().replace("-", "");
    }

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}]+)}");

    private static String subst(String template, Map<String, String> vars) {
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(vars.getOrDefault(m.group(1), "")));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static List<String> collectArgs(List<Object> raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) {
            return out;
        }
        for (Object o : raw) {
            if (o instanceof String s) {
                out.add(s);
                continue;
            }
            Map<String, Object> m = Json.map(o);
            if (m == null || !rulesAllow(Json.list(m.get("rules")))) {
                continue;
            }
            Object value = m.get("value");
            if (value instanceof String s) {
                out.add(s);
            } else if (value instanceof List<?> l) {
                for (Object x : l) {
                    out.add(String.valueOf(x));
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ reguły systemowe

    static boolean rulesAllow(List<Object> rules) {
        if (rules == null || rules.isEmpty()) {
            return true;
        }
        boolean allowed = false;
        for (Object o : rules) {
            Map<String, Object> rule = Json.map(o);
            if (rule != null && ruleApplies(rule)) {
                allowed = "allow".equals(Json.str(rule.get("action")));
            }
        }
        return allowed;
    }

    private static boolean ruleApplies(Map<String, Object> rule) {
        if (rule.get("features") != null) {
            return false; // żadna z opcji (demo, własna rozdzielczość...) nie jest włączona
        }
        Map<String, Object> os = Json.map(rule.get("os"));
        if (os == null) {
            return true;
        }
        String name = Json.str(os.get("name"));
        if (name != null && !name.equals(osName())) {
            return false;
        }
        String arch = Json.str(os.get("arch"));
        if (arch != null && !arch.equals(osArch())) {
            return false;
        }
        String version = Json.str(os.get("version"));
        return version == null
                || Pattern.compile(version).matcher(System.getProperty("os.version", "")).find();
    }

    static String osName() {
        String n = System.getProperty("os.name", "").toLowerCase();
        if (n.contains("win")) {
            return "windows";
        }
        if (n.contains("mac") || n.contains("darwin")) {
            return "osx";
        }
        return "linux";
    }

    static String osArch() {
        String a = System.getProperty("os.arch", "").toLowerCase();
        if (a.equals("x86") || a.equals("i386") || a.equals("i686")) {
            return "x86";
        }
        if (a.contains("aarch64") || a.contains("arm64")) {
            return "arm64";
        }
        return "x86_64";
    }

    private static boolean is64() {
        return !osArch().equals("x86");
    }

    // ------------------------------------------------------------------ pobieranie

    private String getString(String url) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30)).header("User-Agent", USER_AGENT).GET().build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() != 200) {
            throw new IOException("HTTP " + resp.statusCode() + " dla " + url);
        }
        return resp.body();
    }

    private static boolean upToDate(Task t) throws IOException {
        return Files.exists(t.target()) && (t.size() <= 0 || Files.size(t.target()) == t.size());
    }

    private void download(Task t) throws IOException, InterruptedException {
        if (upToDate(t)) {
            return;
        }
        Files.createDirectories(t.target().getParent());
        Path part = t.target().resolveSibling(t.target().getFileName() + ".part");
        IOException last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(t.url()))
                        .timeout(Duration.ofSeconds(60)).header("User-Agent", USER_AGENT).GET().build();
                HttpResponse<Path> resp = http.send(req, HttpResponse.BodyHandlers.ofFile(part,
                        StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING));
                if (resp.statusCode() != 200) {
                    throw new IOException("HTTP " + resp.statusCode() + " dla " + t.url());
                }
                if (t.size() > 0 && Files.size(part) != t.size()) {
                    throw new IOException("Niepełny plik: " + t.url());
                }
                Files.move(part, t.target(), StandardCopyOption.REPLACE_EXISTING);
                return;
            } catch (IOException e) {
                last = e;
            }
        }
        throw last;
    }

    private void runTasks(Collection<Task> tasks) throws IOException, InterruptedException {
        List<Task> todo = new ArrayList<>();
        for (Task t : tasks) {
            if (!upToDate(t)) {
                todo.add(t);
            }
        }
        int total = todo.size();
        if (total == 0) {
            listener.progress(1, 1, "Pliki gotowe");
            return;
        }
        listener.log("Do pobrania: " + total + " plików");
        listener.progress(0, total, "Pobieranie");
        ExecutorService pool = Executors.newFixedThreadPool(16);
        AtomicInteger done = new AtomicInteger();
        try {
            List<Future<Void>> futures = new ArrayList<>();
            for (Task t : todo) {
                futures.add(pool.submit((Callable<Void>) () -> {
                    download(t);
                    listener.progress(done.incrementAndGet(), total, "Pobieranie");
                    return null;
                }));
            }
            for (Future<Void> f : futures) {
                try {
                    f.get();
                } catch (ExecutionException e) {
                    Throwable c = e.getCause();
                    throw new IOException(c.getMessage(), c);
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private void extractNatives(List<NativeJar> jars, Path dir) throws IOException {
        Files.createDirectories(dir);
        Path root = dir.normalize();
        for (NativeJar nj : jars) {
            try (ZipInputStream zin = new ZipInputStream(Files.newInputStream(nj.jar()))) {
                ZipEntry e;
                while ((e = zin.getNextEntry()) != null) {
                    String name = e.getName();
                    if (e.isDirectory() || excluded(name, nj.exclude())) {
                        continue;
                    }
                    Path out = dir.resolve(name).normalize();
                    if (!out.startsWith(root) || Files.exists(out)) {
                        continue;
                    }
                    Files.createDirectories(out.getParent());
                    Files.copy(zin, out);
                }
            }
        }
    }

    private static boolean excluded(String name, List<String> exclude) {
        for (String ex : exclude) {
            if (name.startsWith(ex)) {
                return true;
            }
        }
        return false;
    }
}
