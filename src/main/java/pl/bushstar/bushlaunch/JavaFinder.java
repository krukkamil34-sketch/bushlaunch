package pl.bushstar.bushlaunch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** Szuka Javy odpowiedniej dla danej wersji Minecrafta. */
public final class JavaFinder {

    public record Found(Path exe, int major, boolean bundled) {
    }

    private JavaFinder() {
    }

    public static Found find(int required, Path baseDir) {
        Found best = null;
        for (Path home : candidateHomes(baseDir)) {
            Path exe = javaExe(home);
            int major = readMajor(home);
            if (exe == null || major <= 0) {
                continue;
            }
            // Minecraft <= 1.16 wymaga dokładnie Javy 8, nowsze działają na >= wymaganej
            boolean ok = required <= 8 ? major == 8 : major >= required;
            if (ok && (best == null || major < best.major())) {
                best = new Found(exe, major, false);
            }
        }
        if (best != null) {
            return best;
        }
        Path home = Path.of(System.getProperty("java.home"));
        Path exe = javaExe(home);
        return new Found(exe != null ? exe : Path.of("java"), Runtime.version().feature(), true);
    }

    private static List<Path> candidateHomes(Path baseDir) {
        List<Path> homes = new ArrayList<>();
        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome != null && !javaHome.isBlank()) {
            homes.add(Path.of(javaHome));
        }
        addChildren(homes, baseDir.resolve("runtime"));

        List<Path> roots = new ArrayList<>();
        for (String env : new String[]{"ProgramFiles", "ProgramFiles(x86)"}) {
            String pf = System.getenv(env);
            if (pf != null) {
                for (String vendor : new String[]{"Java", "Eclipse Adoptium", "Microsoft", "Zulu",
                        "BellSoft", "Amazon Corretto", "Semeru"}) {
                    roots.add(Path.of(pf, vendor));
                }
            }
        }
        roots.add(Path.of("/usr/lib/jvm"));
        for (Path r : roots) {
            addChildren(homes, r);
        }

        // Java z oficjalnego launchera Mojang: .minecraft/runtime/<komponent>/<platforma>/<komponent>
        String appData = System.getenv("APPDATA");
        Path mcRuntime = appData != null
                ? Path.of(appData, ".minecraft", "runtime")
                : Path.of(System.getProperty("user.home"), ".minecraft", "runtime");
        if (Files.isDirectory(mcRuntime)) {
            try (Stream<Path> comps = Files.list(mcRuntime)) {
                for (Path comp : comps.toList()) {
                    if (!Files.isDirectory(comp)) {
                        continue;
                    }
                    try (Stream<Path> platforms = Files.list(comp)) {
                        for (Path platform : platforms.toList()) {
                            addChildren(homes, platform);
                        }
                    }
                }
            } catch (IOException ignored) {
                // brak dostępu - pomijamy
            }
        }
        return homes;
    }

    private static void addChildren(List<Path> out, Path dir) {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> s = Files.list(dir)) {
            s.filter(Files::isDirectory).forEach(out::add);
        } catch (IOException ignored) {
            // brak dostępu - pomijamy
        }
    }

    private static Path javaExe(Path home) {
        boolean win = System.getProperty("os.name", "").toLowerCase().contains("win");
        String[] names = win ? new String[]{"javaw.exe", "java.exe"} : new String[]{"java"};
        for (String n : names) {
            Path p = home.resolve("bin").resolve(n);
            if (Files.isRegularFile(p)) {
                return p;
            }
        }
        return null;
    }

    /** Odczytuje główną wersję Javy z pliku "release" (np. 17.0.9 -> 17, 1.8.0_392 -> 8). */
    private static int readMajor(Path home) {
        Path release = home.resolve("release");
        if (!Files.isRegularFile(release)) {
            return -1;
        }
        try {
            for (String line : Files.readAllLines(release)) {
                if (line.startsWith("JAVA_VERSION=")) {
                    String v = line.substring("JAVA_VERSION=".length()).replace("\"", "").trim();
                    String[] parts = v.split("[._+\\-]");
                    if (parts.length == 0) {
                        return -1;
                    }
                    if (parts[0].equals("1") && parts.length > 1) {
                        return Integer.parseInt(parts[1]);
                    }
                    return Integer.parseInt(parts[0]);
                }
            }
        } catch (IOException | NumberFormatException ignored) {
            // nieczytelny plik - pomijamy tę Javę
        }
        return -1;
    }
}
