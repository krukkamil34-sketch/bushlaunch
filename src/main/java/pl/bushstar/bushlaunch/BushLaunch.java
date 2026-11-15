package pl.bushstar.bushlaunch;

import pl.bushstar.bushlaunch.GameInstaller.VersionEntry;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JOptionPane;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * BushLaunch - launcher Minecraft by BushStar.
 * Pobiera wybraną wersję gry z serwerów Mojang i ją uruchamia (wymaga konta Microsoft z Minecraftem Java).
 * Wygląd: ciemny zielony panel (menu boczne z ostatnio granymi, nocna scena, dolny pasek z wersją, RAM-em i przyciskiem GRAJ).
 */
public class BushLaunch {

    public static final String LAUNCHER_VERSION = "0.8.0";

    /** Link do Discorda BushStar - podmień na swój zaproszeniowy. */
    private static final String DISCORD_URL = "https://discord.gg/";

    // ------------------------------------------------------------------ kolory i fonty

    static final Color BG = new Color(0x090D11);
    static final Color SIDEBAR = new Color(0x0B1015);
    static final Color PANEL = new Color(0x0F151B);
    static final Color PANEL_HI = new Color(0x17202A);
    static final Color ACCENT = new Color(0x12D983);
    static final Color ACCENT_HI = new Color(0x2CF59B);
    static final Color ACCENT_DIM = new Color(0x0C5C3A);
    static final Color ON_ACCENT = new Color(0x04180F);
    static final Color TEXT = new Color(0xF2F6F4);
    static final Color MUTED = new Color(0x8592A0);
    static final Color BORDER = new Color(0x1C2630);
    static final Color GLOW = new Color(0x19E68C);
    static final Color DISCORD = new Color(0x5865F2);

    private static final String HEAD_FONT = pickFont("Bahnschrift", "Segoe UI", "Arial");
    private static final String BODY_FONT = pickFont("Segoe UI", "Arial");
    private static final String MONO_FONT = pickMono();

    private static String pickFont(String... want) {
        Set<String> have = new HashSet<>(Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        for (String w : want) {
            if (have.contains(w)) {
                return w;
            }
        }
        return Font.SANS_SERIF;
    }

    private static String pickMono() {
        Set<String> have = new HashSet<>(Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        for (String w : new String[]{"Cascadia Mono", "Consolas", "JetBrains Mono", "DejaVu Sans Mono"}) {
            if (have.contains(w)) {
                return w;
            }
        }
        return Font.MONOSPACED;
    }

    static Font head(int style, float size) {
        return new Font(HEAD_FONT, Font.PLAIN, 12).deriveFont(style, size);
    }

    static Font body(int style, float size) {
        return new Font(BODY_FONT, Font.PLAIN, 12).deriveFont(style, size);
    }

    /** Czcionka techniczna (napisy typu BUSHLAUNCH, JAVA EDITION, etykiety). */
    static Font mono(int style, float size) {
        return new Font(MONO_FONT, Font.PLAIN, 12).deriveFont(style, size);
    }

    /** Ta sama czcionka z rozstrzeleniem liter (tracking, np. 0.14 = 14% rozmiaru). */
    static Font tracked(Font f, float tracking) {
        Map<TextAttribute, Object> m = new HashMap<>();
        m.put(TextAttribute.TRACKING, tracking);
        return f.deriveFont(m);
    }

    /** Szerokość tekstu w aktualnej czcionce g (uwzględnia rozstrzelenie liter). */
    static int tw(Graphics2D g, String s) {
        if (s == null || s.isEmpty()) {
            return 0;
        }
        return (int) Math.ceil(new TextLayout(s, g.getFont(), g.getFontRenderContext()).getAdvance());
    }

    /** Linia bazowa tekstu wyśrodkowanego w pionie wokół cy. */
    static int mid(Graphics2D g, int cy) {
        FontMetrics fm = g.getFontMetrics();
        return cy + (fm.getAscent() - fm.getDescent()) / 2;
    }

    static void aa(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    /** Skraca tekst (z wielokropkiem), żeby zmieścił się w danej szerokości przy aktualnej czcionce g. */
    static String fit(Graphics2D g, String text, int maxW) {
        FontMetrics fm = g.getFontMetrics();
        if (text == null || fm.stringWidth(text) <= maxW) {
            return text;
        }
        String t = text;
        while (t.length() > 1 && fm.stringWidth(t + "…") > maxW) {
            t = t.substring(0, t.length() - 1);
        }
        return t.trim() + "…";
    }

    static Color alpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
    }

    /** Karta: ciemny panel z cienką krawędzią i zaokrąglonymi rogami (hot = zielona krawędź). */
    static void card(Graphics2D g, int x, int y, int w, int h, int c, boolean hot) {
        double arc = Math.min(c * 1.6, 28);
        g.setPaint(new GradientPaint(0, y, hot ? new Color(0x13211E) : new Color(0x111820),
                0, y + h, hot ? new Color(0x0F1A18) : PANEL));
        g.fill(new RoundRectangle2D.Double(x, y, w, h, arc, arc));
        g.setStroke(new BasicStroke(1f));
        g.setColor(hot ? alpha(ACCENT, 150) : BORDER);
        g.draw(new RoundRectangle2D.Double(x + 0.5, y + 0.5, w - 1, h - 1, arc, arc));
    }

    /** Zaokrąglony prostokąt (c = promień zaokrąglenia w przybliżeniu). */
    static Shape cut(double x, double y, double w, double h, double c) {
        double arc = Math.min(c * 1.6, 28);
        return new RoundRectangle2D.Double(x, y, w, h, arc, arc);
    }

    /** Logo BushStar (logo.png w zasobach); gdyby go zabrakło, rysowana jest zielona litera B. */
    private static final BufferedImage LOGO = loadLogo();
    private static final Map<Integer, BufferedImage> LOGO_CACHE = new HashMap<>();

    private static BufferedImage loadLogo() {
        try (InputStream in = BushLaunch.class.getResourceAsStream("/logo.png")) {
            return in == null ? null : ImageIO.read(in);
        } catch (IOException e) {
            return null;
        }
    }

    /** Skaluje logo w kilku krokach (co połowę), dzięki czemu małe ikony są gładkie, a nie poszarpane. */
    private static synchronized BufferedImage logoScaled(int size) {
        BufferedImage cached = LOGO_CACHE.get(size);
        if (cached != null) {
            return cached;
        }
        BufferedImage cur = LOGO;
        while (cur.getWidth() / 2 >= size) {
            BufferedImage half = new BufferedImage(cur.getWidth() / 2, cur.getHeight() / 2, BufferedImage.TYPE_INT_ARGB);
            Graphics2D hg = half.createGraphics();
            hg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            hg.drawImage(cur, 0, 0, half.getWidth(), half.getHeight(), null);
            hg.dispose();
            cur = half;
        }
        BufferedImage fin = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D fg = fin.createGraphics();
        fg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        fg.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        fg.drawImage(cur, 0, 0, size, size, null);
        fg.dispose();
        LOGO_CACHE.put(size, fin);
        return fin;
    }

    /** Logo: ikona BushStar (zielone B z iskrą na ciemnym tle). */
    static void logoMark(Graphics2D g0, int x, int y, int s) {
        Graphics2D g = (Graphics2D) g0.create();
        aa(g);
        if (LOGO != null) {
            g.drawImage(logoScaled(s), x, y, null);
            g.dispose();
            return;
        }
        g.setColor(new Color(0x0B2A1E));
        g.fill(new RoundRectangle2D.Double(x, y, s, s, s * 0.32, s * 0.32));
        g.setColor(ACCENT);
        g.setStroke(new BasicStroke(Math.max(1.2f, s / 18f)));
        g.draw(new RoundRectangle2D.Double(x + 0.8, y + 0.8, s - 1.6, s - 1.6, s * 0.32, s * 0.32));
        g.setFont(mono(Font.BOLD, s * 0.66f));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(ACCENT_HI);
        g.drawString("B", x + (s - fm.stringWidth("B")) / 2, y + s / 2 + (fm.getAscent() - fm.getDescent()) / 2);
        g.dispose();
    }

    // ------------------------------------------------------------------ stan

    private final Path baseDir = defaultBaseDir();
    private final Path configFile = baseDir.resolve("config.properties");
    private final Properties config = new Properties();

    private JFrame frame;
    private GameInstaller installer;
    private List<VersionEntry> allVersions = new ArrayList<>();
    private VersionEntry selected;
    private final List<Profile> profiles = new ArrayList<>();
    private final Map<String, VersionEntry> versionIndex = new LinkedHashMap<>();
    private String activeProfileId = "";
    private volatile MicrosoftAuth.Account msAccount;
    private String nick = "";
    private int ramMb = 2048;
    private boolean showAll;

    private boolean loadingVersions = true;
    private boolean busy;
    private volatile boolean gameRunning;
    private volatile String gameInfo = "";
    private boolean indeterminate;
    private int progDone;
    private int progTotal;
    private String progWhat = "";
    private int modsCount;

    private BufferedImage skin;
    private static final BufferedImage NO_SKIN = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
    private final Map<String, BufferedImage> friendSkins = new java.util.concurrent.ConcurrentHashMap<>();

    private JTextArea logArea;
    private CardLayout cards;
    private JPanel pages;
    private final Map<String, NavButton> navs = new LinkedHashMap<>();
    private JComponent hero;
    private HeroPanel heroPanel;
    private BottomBar bottomBar;
    private boolean online = true;
    private JPanel recentCards;
    private JPanel profilesGrid;
    private JPanel modsList;
    private JLabel modsInfo;
    private Component sidebarUser;
    private Component titleAvatar;

    // znajomi (serwer znajomych)
    private Social socialApi;
    private volatile Social.Snapshot socialSnap;
    private volatile String socialStatus = "";
    private volatile Social.Creds socialCredsCache;
    private volatile long socialCredsAt;
    private final Object authLock = new Object();
    private final Map<String, String> friendUuids = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Set<String> seenRequests = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private java.util.concurrent.ScheduledExecutorService socialExec;
    private boolean socialHookAdded;
    private JDialog friendsDialog;
    private long friendsClosedAt;
    private String friendsNote = "";
    private Runnable friendsRefresh;

    /** Profil gracza: nazwa + wersja gry. Zapisywany w config.properties. */
    private static final class Profile {
        final String id;
        String name;
        String version;
        long used;

        Profile(String id, String name, String version) {
            this.id = id;
            this.name = name;
            this.version = version;
        }
    }

    public static void main(String[] args) {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        SwingUtilities.invokeLater(() -> new BushLaunch().show());
    }

    private static Path defaultBaseDir() {
        String appData = System.getenv("APPDATA");
        if (appData != null && !appData.isBlank()) {
            return Path.of(appData, ".bushlaunch");
        }
        return Path.of(System.getProperty("user.home"), ".bushlaunch");
    }

    // ------------------------------------------------------------------ okno

    private void show() {
        loadConfig();
        loadAccount();
        showAll = Boolean.parseBoolean(config.getProperty("showAll", "false"));
        try {
            ramMb = Math.max(512, Math.min(Integer.parseInt(config.getProperty("ram", "2048")), 65536));
        } catch (NumberFormatException ignored) {
            ramMb = 2048;
        }
        loadProfiles();

        frame = new JFrame("BushLaunch");
        frame.setUndecorated(true);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setIconImage(makeIcon());
        frame.setMinimumSize(new Dimension(1040, 660));
        frame.setSize(1240, 760);
        frame.setLocationRelativeTo(null);
        frame.setMaximizedBounds(GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds());

        JPanel root = buildRoot();
        frame.setContentPane(root);
        showPage("home");
        frame.setVisible(true);

        installer = new GameInstaller(baseDir, new GameInstaller.Listener() {
            @Override
            public void log(String message) {
                appendLog(message);
            }

            @Override
            public void progress(int done, int total, String what) {
                SwingUtilities.invokeLater(() -> {
                    indeterminate = false;
                    progDone = done;
                    progTotal = Math.max(total, 1);
                    progWhat = what;
                    hero.repaint();
                });
            }
        });

        refreshStats();
        loadSkinAsync();
        loadFriendSkins();
        startSocial();
        loadVersions();
        if (Boolean.parseBoolean(config.getProperty("autoUpdate", "true"))) {
            checkForUpdates(false);
        }
    }

    /** Składa całe UI (bez okna) - osobno, żeby dało się je też wyrenderować do obrazka. */
    private JPanel buildRoot() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BG);
        root.setBorder(BorderFactory.createLineBorder(BORDER));

        cards = new CardLayout();
        pages = new JPanel(cards);
        pages.setBackground(BG);
        pages.add(buildHome(), "home");
        pages.add(buildProfilesPage(), "profiles");
        pages.add(buildModsPage(), "mods");
        pages.add(buildNewsPage(), "news");
        pages.add(buildConsolePage(), "console");

        JPanel main = new JPanel(new BorderLayout());
        main.setBackground(BG);
        main.add(buildSidebar(), BorderLayout.WEST);
        main.add(pages, BorderLayout.CENTER);

        root.add(buildTitleBar(), BorderLayout.NORTH);
        root.add(main, BorderLayout.CENTER);

        return root;
    }

    private static BufferedImage makeIcon() {
        BufferedImage im = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = im.createGraphics();
        logoMark(g, 2, 2, 60);
        g.dispose();
        return im;
    }

    // ------------------------------------------------------------------ pasek tytułu

    private JComponent buildTitleBar() {
        JPanel bar = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                aa(g);
                g.setColor(new Color(0x080B0F));
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(BORDER);
                g.fillRect(0, getHeight() - 1, getWidth(), 1);
                logoMark(g, 22, (getHeight() - 28) / 2, 28);
                g.setFont(tracked(mono(Font.BOLD, 20), 0.14f));
                int x = 62;
                int base = mid(g, getHeight() / 2);
                g.setColor(TEXT);
                g.drawString("BUSH", x, base);
                x += tw(g, "BUSH");
                g.setColor(ACCENT_HI);
                g.drawString("LAUNCH", x, base);
                x += tw(g, "LAUNCH") + 14;
                g.setFont(mono(Font.PLAIN, 14));
                g.setColor(MUTED);
                g.drawString("v" + LAUNCHER_VERSION, x, mid(g, getHeight() / 2));
                g.dispose();
            }
        };
        bar.setPreferredSize(new Dimension(10, 46));

        JPanel buttons = new JPanel(new GridLayout(1, 3));
        buttons.setOpaque(false);
        buttons.add(new WinButton(0, () -> frame.setState(Frame.ICONIFIED)));
        buttons.add(new WinButton(1, () -> {
            if ((frame.getExtendedState() & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH) {
                frame.setExtendedState(Frame.NORMAL);
            } else {
                frame.setExtendedState(Frame.MAXIMIZED_BOTH);
            }
        }));
        buttons.add(new WinButton(2, () -> System.exit(0)));
        Clickable avatarBtn = new Clickable(this::openAccountDialog) {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                aa(g);
                int size = 30;
                drawAvatar(g, skin, (getWidth() - size) / 2 - 2, (getHeight() - size) / 2, size,
                        selfState(), new Color(0x080B0F));
                if (hover) {
                    g.setColor(alpha(ACCENT, 170));
                    g.setStroke(new BasicStroke(1.4f));
                    g.draw(new RoundRectangle2D.Double((getWidth() - size) / 2.0 - 2 + 0.5, (getHeight() - size) / 2.0 + 0.5,
                            size - 1, size - 1, size * 0.26, size * 0.26));
                }
                g.dispose();
            }
        };
        avatarBtn.setPreferredSize(new Dimension(56, 46));
        avatarBtn.setToolTipText("Konto");
        titleAvatar = avatarBtn;
        JPanel east = new JPanel(new BorderLayout());
        east.setOpaque(false);
        east.add(avatarBtn, BorderLayout.WEST);
        east.add(buttons, BorderLayout.CENTER);
        bar.add(east, BorderLayout.EAST);

        final int[] press = new int[2];
        MouseAdapter drag = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                press[0] = e.getX();
                press[1] = e.getY();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if ((frame.getExtendedState() & Frame.MAXIMIZED_BOTH) != Frame.MAXIMIZED_BOTH) {
                    frame.setLocation(e.getXOnScreen() - press[0], e.getYOnScreen() - press[1]);
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    if ((frame.getExtendedState() & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH) {
                        frame.setExtendedState(Frame.NORMAL);
                    } else {
                        frame.setExtendedState(Frame.MAXIMIZED_BOTH);
                    }
                }
            }
        };
        bar.addMouseListener(drag);
        bar.addMouseMotionListener(drag);
        return bar;
    }

    // ------------------------------------------------------------------ pasek boczny

    private JComponent buildSidebar() {
        JPanel side = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                g.setColor(SIDEBAR);
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(BORDER);
                g.fillRect(getWidth() - 1, 0, 1, getHeight());
                g.dispose();
            }
        };
        side.setOpaque(false);
        side.setPreferredSize(new Dimension(272, 10));

        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setBorder(new EmptyBorder(14, 0, 0, 0));
        addNav(top, "home", "Graj", 12, null);
        addNav(top, "profiles", "Wersje", 1, null);
        addNav(top, "mods", "Mody", 13, null);
        addNav(top, "news", "Aktualności", 10, null);
        addNav(top, "settings", "Ustawienia", 14, this::openSettings);

        JComponent recentHead = new JComponent() {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                aa(g);
                g.setFont(tracked(mono(Font.PLAIN, 12), 0.16f));
                g.setColor(MUTED);
                g.drawString("OSTATNIO GRANE", 26, 40);
                g.dispose();
            }
        };
        recentHead.setPreferredSize(new Dimension(270, 60));
        recentHead.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
        recentHead.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.add(recentHead);

        recentCards = new JPanel();
        recentCards.setOpaque(false);
        recentCards.setLayout(new BoxLayout(recentCards, BoxLayout.Y_AXIS));
        recentCards.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.add(recentCards);

        JPanel bottom = new JPanel();
        bottom.setOpaque(false);
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
        bottom.setBorder(new EmptyBorder(0, 0, 8, 0));

        Clickable discord = new Clickable(() -> openUrl(DISCORD_URL)) {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                aa(g);
                int cy = getHeight() / 2;
                g.setColor(hover ? DISCORD : alpha(DISCORD, 190));
                g.fill(new RoundRectangle2D.Double(30, cy - 9, 24, 18, 9, 9));
                g.setColor(Color.WHITE);
                g.fillOval(37, cy - 3, 4, 5);
                g.fillOval(45, cy - 3, 4, 5);
                g.setFont(body(Font.PLAIN, 14));
                g.setColor(hover ? TEXT : MUTED);
                g.drawString("Discord BushStar", 66, mid(g, cy));
                g.dispose();
            }
        };
        discord.setPreferredSize(new Dimension(270, 38));
        discord.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        discord.setAlignmentX(Component.LEFT_ALIGNMENT);

        Clickable user = new Clickable(this::openAccountDialog) {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                aa(g);
                int w = getWidth() - 32;
                int h = getHeight() - 10;
                g.translate(16, 5);
                card(g, 0, 0, w, h, 14, hover);
                drawAvatar(g, skin, 14, (h - 40) / 2, 40, selfState(), new Color(0x10171E));
                int tx = 66;
                g.setFont(head(Font.BOLD, 17));
                g.setColor(TEXT);
                g.drawString(fit(g, nick.isEmpty() ? "Zaloguj się" : nick, w - tx - 34), tx, h / 2 - 3);
                int sy = h / 2 + 17;
                if (busy) {
                    g.setFont(body(Font.PLAIN, 13));
                    g.setColor(ACCENT_HI);
                    g.drawString("Przygotowanie...", tx, sy);
                } else if (isMicrosoft()) {
                    // znaczek Microsoft: cztery kolorowe kwadraty
                    int q = 6;
                    int qx = tx;
                    int qy = sy - 10;
                    g.setColor(new Color(0xF25022));
                    g.fillRect(qx, qy, q, q);
                    g.setColor(new Color(0x7FBA00));
                    g.fillRect(qx + q + 1, qy, q, q);
                    g.setColor(new Color(0x00A4EF));
                    g.fillRect(qx, qy + q + 1, q, q);
                    g.setColor(new Color(0xFFB900));
                    g.fillRect(qx + q + 1, qy + q + 1, q, q);
                    g.setFont(body(Font.PLAIN, 14));
                    g.setColor(MUTED);
                    g.drawString("Microsoft", tx + 2 * q + 8, sy);
                } else {
                    g.setFont(body(Font.PLAIN, 13));
                    g.setColor(MUTED);
                    g.drawString("Nie zalogowano", tx, sy);
                }
                g.setColor(hover ? TEXT : MUTED);
                g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int cx = w - 20;
                g.drawLine(cx - 3, h / 2 - 5, cx + 2, h / 2);
                g.drawLine(cx + 2, h / 2, cx - 3, h / 2 + 5);
                g.dispose();
            }
        };
        user.setPreferredSize(new Dimension(270, 84));
        user.setMaximumSize(new Dimension(Integer.MAX_VALUE, 84));
        user.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebarUser = user;

        bottom.add(discord);
        bottom.add(user);

        side.add(top, BorderLayout.CENTER);
        side.add(bottom, BorderLayout.SOUTH);
        return side;
    }

    private void addNav(JPanel nav, String key, String label, int icon, Runnable action) {
        NavButton b = new NavButton(key, label, icon, action);
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        navs.put(key, b);
        nav.add(b);
    }

    private void showPage(String key) {
        cards.show(pages, key);
        for (Map.Entry<String, NavButton> e : navs.entrySet()) {
            e.getValue().active = e.getKey().equals(key);
            e.getValue().repaint();
        }
        if ("mods".equals(key)) {
            refreshStats();
            refreshMods();
        } else if ("profiles".equals(key)) {
            refreshProfiles();
        }
    }

    private final class NavButton extends Clickable {
        private final String label;
        private final int icon;
        boolean active;

        NavButton(String key, String label, int icon, Runnable action) {
            super(null);
            this.label = label;
            this.icon = icon;
            this.onClick = action != null ? action : () -> showPage(key);
            setPreferredSize(new Dimension(270, 56));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            aa(g);
            int x = 16;
            int y = 3;
            int w = getWidth() - 32;
            int h = getHeight() - 6;
            if (active) {
                g.setColor(alpha(ACCENT, 26));
                g.fill(new RoundRectangle2D.Double(x, y, w, h, 24, 24));
                g.setColor(alpha(ACCENT, 170));
                g.setStroke(new BasicStroke(1.2f));
                g.draw(new RoundRectangle2D.Double(x + 0.5, y + 0.5, w - 1, h - 1, 24, 24));
            } else if (hover) {
                g.setColor(alpha(Color.WHITE, 10));
                g.fill(new RoundRectangle2D.Double(x, y, w, h, 24, 24));
            }
            Color c = active ? ACCENT_HI : hover ? TEXT : new Color(0xA3B0BA);
            icon(g, icon, x + 20, y + (h - 24) / 2, 24, c);
            g.setColor(active || hover ? TEXT : new Color(0xC9D3D8));
            g.setFont(head(Font.PLAIN, 18));
            g.drawString(label, x + 58, mid(g, y + h / 2));
            g.dispose();
        }
    }

    /** Wiersz listy "Ostatnio grane" w pasku bocznym. */
    private final class RecentRow extends Clickable {
        private final Profile p;

        RecentRow(Profile p) {
            super(null);
            this.p = p;
            this.onClick = () -> {
                selectProfile(this.p);
                showPage("home");
            };
            setToolTipText(p.name);
            setPreferredSize(new Dimension(270, 44));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
            setAlignmentX(Component.LEFT_ALIGNMENT);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            aa(g);
            boolean sel = p.id.equals(activeProfileId);
            int cy = getHeight() / 2;
            if (hover) {
                g.setColor(alpha(Color.WHITE, 8));
                g.fill(new RoundRectangle2D.Double(12, 2, getWidth() - 24, getHeight() - 4, 20, 20));
            }
            if (sel) {
                g.setPaint(new RadialGradientPaint(34f, cy, 12f,
                        new float[]{0f, 1f}, new Color[]{alpha(GLOW, 90), alpha(GLOW, 0)}));
                g.fillRect(20, cy - 14, 28, 28);
            }
            g.setColor(sel ? ACCENT_HI : new Color(0x3A4654));
            g.fill(new RoundRectangle2D.Double(28, cy - 6, 12, 12, 3, 3));

            String ver = p.version == null || p.version.isBlank() ? p.name : p.version;
            VersionEntry v = p.version == null || p.version.isBlank() ? null : resolveVersion(p.version);
            String type = v == null ? "" : typeShort(v.type());

            g.setFont(mono(Font.PLAIN, 12));
            int typeW = g.getFontMetrics().stringWidth(type);
            g.setFont(body(Font.PLAIN, 16));
            g.setColor(sel ? TEXT : new Color(0xB4BFC8));
            g.drawString(fit(g, ver, getWidth() - 56 - 28 - typeW - 8), 54, mid(g, cy));
            g.setFont(mono(Font.PLAIN, 12));
            g.setColor(MUTED);
            g.drawString(type, getWidth() - 26 - typeW, mid(g, cy));
            g.dispose();
        }
    }

    private static String typeShort(String type) {
        if ("release".equals(type)) {
            return "Release";
        }
        if ("snapshot".equals(type)) {
            return "Snapshot";
        }
        if ("old_beta".equals(type)) {
            return "Beta";
        }
        if ("old_alpha".equals(type)) {
            return "Alpha";
        }
        return "";
    }

    // ------------------------------------------------------------------ strona główna

    private JComponent buildHome() {
        JPanel home = new JPanel(new BorderLayout(0, 12));
        home.setBackground(BG);
        home.setBorder(new EmptyBorder(22, 20, 20, 20));

        heroPanel = new HeroPanel();

        JPanel news = new JPanel(new GridLayout(1, 2, 16, 0));
        news.setOpaque(false);
        news.setPreferredSize(new Dimension(10, 86));
        news.add(new NewsCard("AKTUALIZACJA", "Nowy wygląd launchera", 0));
        news.add(new NewsCard("NOWOŚĆ", "Zapamiętywanie sesji", 1));

        bottomBar = new BottomBar();

        JPanel south = new JPanel(new BorderLayout(0, 12));
        south.setOpaque(false);
        south.add(news, BorderLayout.NORTH);
        south.add(bottomBar, BorderLayout.SOUTH);

        home.add(heroPanel, BorderLayout.CENTER);
        home.add(south, BorderLayout.SOUTH);
        hero = home;

        Timer anim = new Timer(40, e -> {
            if (home.isShowing()) {
                heroPanel.repaint();
                if (busy || loadingVersions) {
                    bottomBar.repaint();
                }
            }
        });
        anim.start();
        return home;
    }

    /** Strona "Aktualności": dziennik zmian launchera. */
    private JComponent buildNewsPage() {
        JPanel p = new JPanel(new BorderLayout(0, 18));
        p.setBackground(BG);
        p.setBorder(new EmptyBorder(26, 30, 26, 30));
        p.add(pageTitle("Aktualności", "Co nowego w BushLaunch."), BorderLayout.NORTH);
        p.add(new InfoCard("Dziennik zmian", 10, new String[][]{
                {"v" + LAUNCHER_VERSION, "Nowy wygląd: zielony motyw, menu boczne i dolny pasek startowy"},
                {"Sesja", "Launcher zapamiętuje logowanie kontem Microsoft"},
                {"Profile", "Własne profile i zmiana ich wersji gry"},
                {"Wersje", "Wszystkie wydania i snapshoty Mojang"},
                {"Wkrótce", "Fabric, Forge i skiny"}}), BorderLayout.CENTER);
        return p;
    }

    /** Karta z tytułem i listą wpisów (nagłówek + opis). */
    private static final class InfoCard extends JComponent {
        private final String title;
        private final int icon;
        private final String[][] rows;

        InfoCard(String title, int icon, String[][] rows) {
            this.title = title;
            this.icon = icon;
            this.rows = rows;
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            aa(g);
            card(g, 0, 0, getWidth(), getHeight(), 24, false);
            icon(g, icon, 22, 22, 22, ACCENT_HI);
            g.setColor(TEXT);
            g.setFont(head(Font.BOLD, 22));
            g.drawString(title, 54, 40);
            g.setColor(BORDER);
            g.fillRect(22, 58, getWidth() - 44, 1);
            int y = 82;
            int step = Math.max(58, Math.min(96, (getHeight() - 90) / Math.max(1, rows.length)));
            for (String[] r : rows) {
                if (y + 40 > getHeight()) {
                    break;
                }
                g.setColor(ACCENT);
                g.fill(new RoundRectangle2D.Double(22, y - 11, 4, 40, 4, 4));
                g.setColor(TEXT);
                g.setFont(body(Font.BOLD, 14));
                g.drawString(r[0], 38, y + 3);
                g.setColor(MUTED);
                g.setFont(body(Font.PLAIN, 13));
                g.drawString(fit(g, r[1], getWidth() - 70), 38, y + 24);
                y += step;
            }
            g.dispose();
        }
    }

    /** Karta wiadomości pod sceną: miniatura, etykieta i tytuł. */
    private final class NewsCard extends Clickable {
        private final String label;
        private final String title;
        private final BufferedImage thumb;

        NewsCard(String label, String title, int kind) {
            super(() -> { });
            this.label = label;
            this.title = title;
            this.thumb = thumb(kind, 200, 160);
            this.onClick = () -> showPage("news");
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            aa(g);
            card(g, 0, 0, getWidth(), getHeight(), 12, hover);
            int th = getHeight() - 24;
            int twd = (int) Math.round(th * 1.22);
            Graphics2D gi = (Graphics2D) g.create();
            gi.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            gi.clip(new RoundRectangle2D.Double(12, 12, twd, th, 14, 14));
            gi.drawImage(thumb, 12, 12, twd, th, null);
            gi.dispose();
            g.setColor(alpha(Color.WHITE, 22));
            g.draw(new RoundRectangle2D.Double(12.5, 12.5, twd - 1, th - 1, 14, 14));
            int tx = 12 + twd + 16;
            g.setFont(tracked(mono(Font.PLAIN, 12), 0.14f));
            g.setColor(ACCENT_HI);
            g.drawString(label, tx, getHeight() / 2 - 5);
            g.setFont(body(Font.BOLD, 17));
            g.setColor(TEXT);
            g.drawString(fit(g, title, getWidth() - tx - 16), tx, getHeight() / 2 + 20);
            g.dispose();
        }
    }

    private static String ago(long ms) {
        long sec = Math.max(0, (System.currentTimeMillis() - ms) / 1000);
        if (sec < 60) {
            return "przed chwilą";
        }
        if (sec < 3600) {
            return (sec / 60) + " min temu";
        }
        if (sec < 86400) {
            return (sec / 3600) + " godz. temu";
        }
        return (sec / 86400) + " dn. temu";
    }

    // ------------------------------------------------------------------ scena (nocny krajobraz, plakietka, tytuł)

    private final class HeroPanel extends JComponent {
        private BufferedImage scene;
        private final long t0 = System.nanoTime();
        /** Świetliki: x (0-1), y (0-1), faza, prędkość. */
        private final double[][] flies = new double[16][4];
        private final Rectangle friendsRect = new Rectangle();
        private boolean hoverFriends;

        HeroPanel() {
            MouseAdapter fm = new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    boolean h = friendsRect.contains(e.getPoint());
                    if (h != hoverFriends) {
                        hoverFriends = h;
                        setCursor(Cursor.getPredefinedCursor(h ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                        repaint();
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hoverFriends = false;
                    setCursor(Cursor.getDefaultCursor());
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (friendsRect.contains(e.getPoint())) {
                        openFriendsDialog();
                    }
                }
            };
            addMouseListener(fm);
            addMouseMotionListener(fm);
            Random r = new Random(11);
            for (double[] f : flies) {
                f[0] = r.nextDouble();
                f[1] = 0.42 + r.nextDouble() * 0.35;
                f[2] = r.nextDouble() * Math.PI * 2;
                f[3] = 0.3 + r.nextDouble() * 0.7;
            }
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            aa(g);
            int w = getWidth();
            int h = getHeight();
            double time = (System.nanoTime() - t0) / 1e9;

            g.setClip(new RoundRectangle2D.Double(0, 0, w, h, 28, 28));
            if (scene == null) {
                scene = smoothScene(SCENE_W, SCENE_H);
            }
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(scene, 0, 0, w, h, null);

            // zielona latarnia przy domu
            float lx = (float) (w * LANTERN_X);
            float ly = (float) (h * LANTERN_Y);
            float flick = (float) (0.88 + 0.12 * Math.sin(time * 4.7) * Math.sin(time * 1.9 + 1));
            float lr = h * 0.30f * flick;
            g.setPaint(new RadialGradientPaint(lx, ly, lr, new float[]{0f, 0.35f, 1f},
                    new Color[]{alpha(GLOW, 120), alpha(GLOW, 42), alpha(GLOW, 0)}));
            g.fillRect(0, 0, w, h);
            g.setPaint(new RadialGradientPaint(lx, ly, 9f, new float[]{0f, 1f},
                    new Color[]{alpha(new Color(0xD8FFEA), 230), alpha(GLOW, 0)}));
            g.fillRect((int) lx - 10, (int) ly - 10, 20, 20);

            // świetliki
            for (double[] f : flies) {
                double fx = ((f[0] * w + Math.sin(time * f[3] + f[2]) * 34) % w + w) % w;
                double fy = h * f[1] + Math.cos(time * f[3] * 1.3 + f[2]) * 16;
                int a = (int) (60 + 150 * (0.5 + 0.5 * Math.sin(time * 1.7 * f[3] + f[2] * 3)));
                g.setColor(alpha(GLOW, a / 4));
                g.fill(new Ellipse2D.Double(fx - 5, fy - 5, 10, 10));
                g.setColor(alpha(ACCENT_HI, a));
                g.fill(new Ellipse2D.Double(fx - 1.2, fy - 1.2, 2.4, 2.4));
            }

            // przyciemnienie pod tekstem (lewa strona i dół)
            g.setPaint(new GradientPaint(0, 0, alpha(BG, 120), (float) (w * 0.55), 0, alpha(BG, 0)));
            g.fillRect(0, 0, w, h);
            g.setPaint(new GradientPaint(0, h * 0.45f, alpha(BG, 0), 0, h, alpha(BG, 200)));
            g.fillRect(0, (int) (h * 0.42), w, h - (int) (h * 0.42));

            drawStatusPill(g, w);

            // plakietka, tytuł i podpis
            int x = 28;
            Profile ap = activeProfile();
            String when = ap == null || ap.used <= 0 ? "Jeszcze nie grano" : "Ostatnio grane: " + ago(ap.used);
            String sub = ap == null ? when : when + " · " + ap.name;
            g.setFont(body(Font.PLAIN, 16));
            g.setColor(new Color(0xD3DCD8));
            g.drawString(fit(g, sub, w - x - 28), x, h - 30);

            float ts = h < 330 ? 34f : 42f;
            g.setFont(head(Font.BOLD, ts));
            g.setColor(TEXT);
            int line = (int) (ts * 1.3);
            int base2 = h - 68;
            g.drawString("czeka.", x, base2);
            g.drawString("Twój świat", x, base2 - line);

            g.setFont(tracked(mono(Font.BOLD, 13), 0.1f));
            String badge = "JAVA EDITION";
            int bw = tw(g, badge) + 26;
            int bh = 28;
            int by = base2 - line - (int) (ts * 0.72) - 18 - bh;
            g.setColor(ACCENT_HI);
            g.fill(new RoundRectangle2D.Double(x, by, bw, bh, 12, 12));
            g.setColor(ON_ACCENT);
            g.drawString(badge, x + 13, mid(g, by + bh / 2));

            g.setClip(null);
            g.setColor(BORDER);
            g.setStroke(new BasicStroke(1f));
            g.draw(new RoundRectangle2D.Double(0.5, 0.5, w - 1, h - 1, 28, 28));
            g.dispose();
        }

        /** Prawy górny róg: stan połączenia i znajomi (kliknięcie otwiera listę znajomych). */
        private void drawStatusPill(Graphics2D g, int w) {
            String state = loadingVersions ? "łączenie" : online ? "online" : "offline";
            boolean ok = !loadingVersions && online;
            List<String> fs = friends();
            int n = fs.size();
            String label = n == 0 ? "dodaj znajomych" : n == 1 ? "1 znajomy" : n + " znajomych";
            int shown = Math.min(3, n);

            g.setFont(mono(Font.PLAIN, 15));
            int stW = g.getFontMetrics().stringWidth(state);
            int lbW = g.getFontMetrics().stringWidth(label);
            int ph = 40;
            int headsW = shown == 0 ? 0 : 28 + (shown - 1) * 36 + 10;
            int pw = 18 + 10 + 10 + stW + 14 + 1 + 14 + headsW + lbW + 18 + 20;
            int px = w - 22 - pw;
            int py = 18;
            int cy = py + ph / 2;
            g.setColor(alpha(new Color(0x070B0F), 205));
            g.fill(new RoundRectangle2D.Double(px, py, pw, ph, ph, ph));
            g.setColor(hoverFriends ? alpha(ACCENT, 160) : alpha(Color.WHITE, 30));
            g.setStroke(new BasicStroke(1f));
            g.draw(new RoundRectangle2D.Double(px + 0.5, py + 0.5, pw - 1, ph - 1, ph, ph));
            Social.Snapshot snap = socialSnap;
            int reqs = snap == null ? 0 : snap.incoming().size();
            if (reqs > 0) {
                int bd = 22;
                int bx = px + pw - bd + 6;
                int by = py - 7;
                g.setColor(BG);
                g.fill(new Ellipse2D.Double(bx - 2, by - 2, bd + 4, bd + 4));
                g.setColor(ACCENT_HI);
                g.fill(new Ellipse2D.Double(bx, by, bd, bd));
                g.setFont(body(Font.BOLD, 13));
                g.setColor(ON_ACCENT);
                String bt = reqs > 9 ? "9+" : String.valueOf(reqs);
                g.drawString(bt, bx + (bd - g.getFontMetrics().stringWidth(bt)) / 2, mid(g, by + bd / 2));
            }

            int x = px + 18;
            if (ok) {
                g.setPaint(new RadialGradientPaint(x + 5f, cy, 12f, new float[]{0f, 1f},
                        new Color[]{alpha(GLOW, 110), alpha(GLOW, 0)}));
                g.fillRect(x - 8, cy - 13, 26, 26);
            }
            g.setColor(ok ? ACCENT_HI : MUTED);
            g.fill(new Ellipse2D.Double(x, cy - 5, 10, 10));
            x += 20;
            g.drawString(state, x, mid(g, cy));
            x += stW + 14;
            g.setColor(alpha(Color.WHITE, 36));
            g.fillRect(x, py + 10, 1, ph - 20);
            x += 15;
            friendsRect.setBounds(x - 8, py, px + pw - x + 8, ph);
            for (int i = 0; i < shown; i++) {
                int hx = x + i * 36;
                BufferedImage sk = friendSkins.get(fs.get(i));
                drawAvatar(g, sk == NO_SKIN ? null : sk, hx, cy - 14, 28,
                        presenceOf(fs.get(i))[0], new Color(0x070B0F));
            }
            x += headsW;
            g.setColor(hoverFriends ? ACCENT_HI : TEXT);
            g.drawString(label, x, mid(g, cy));
            x += lbW + 8;
            g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(x, cy - 4, x + 4, cy);
            g.drawLine(x + 4, cy, x, cy + 4);
        }
    }

    // ------------------------------------------------------------------ dolny pasek (wersja, RAM, postęp, GRAJ)

    private final class BottomBar extends JComponent {
        private final Rectangle verRect = new Rectangle();
        private final Rectangle ramRect = new Rectangle();
        private final Rectangle playRect = new Rectangle();
        private boolean hoverVer;
        private boolean hoverRam;
        private boolean hoverPlay;
        private final long t0 = System.nanoTime();

        BottomBar() {
            setPreferredSize(new Dimension(10, 72));
            MouseAdapter m = new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    boolean v = verRect.contains(e.getPoint());
                    boolean r = ramRect.contains(e.getPoint());
                    boolean p = playRect.contains(e.getPoint()) && !(busy || loadingVersions);
                    if (v != hoverVer || r != hoverRam || p != hoverPlay) {
                        hoverVer = v;
                        hoverRam = r;
                        hoverPlay = p;
                        setCursor(Cursor.getPredefinedCursor(v || r || p ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                        repaint();
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hoverVer = false;
                    hoverRam = false;
                    hoverPlay = false;
                    setCursor(Cursor.getDefaultCursor());
                    repaint();
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (playRect.contains(e.getPoint())) {
                        onPlay();
                    } else if (verRect.contains(e.getPoint())) {
                        openVersionPicker();
                    } else if (ramRect.contains(e.getPoint())) {
                        openSettings();
                    }
                }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
        }

        private String progressLabel() {
            String what = progWhat == null ? "" : progWhat;
            if ("Pobieranie".equals(what)) {
                return "POBIERANIE PLIKÓW";
            }
            return what.toUpperCase(Locale.ROOT);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            aa(g);
            int w = getWidth();
            int h = getHeight();
            int cy = h / 2;
            double time = (System.nanoTime() - t0) / 1e9;

            g.setColor(PANEL);
            g.fill(new RoundRectangle2D.Double(0, 0, w, h, 28, 28));
            g.setColor(BORDER);
            g.setStroke(new BasicStroke(1f));
            g.draw(new RoundRectangle2D.Double(0.5, 0.5, w - 1, h - 1, 28, 28));

            int bh = 46;

            // wybór wersji
            verRect.setBounds(14, cy - bh / 2, 156, bh);
            g.setColor(hoverVer ? new Color(0x1C2832) : PANEL_HI);
            g.fill(new RoundRectangle2D.Double(verRect.x, verRect.y, verRect.width, verRect.height, 20, 20));
            g.setColor(hoverVer ? alpha(ACCENT, 120) : BORDER);
            g.draw(new RoundRectangle2D.Double(verRect.x + 0.5, verRect.y + 0.5, verRect.width - 1, verRect.height - 1, 20, 20));
            boolean hasVer = selected != null;
            if (hasVer) {
                g.setPaint(new RadialGradientPaint(verRect.x + 20f, cy, 11f, new float[]{0f, 1f},
                        new Color[]{alpha(GLOW, 100), alpha(GLOW, 0)}));
                g.fillRect(verRect.x + 8, cy - 12, 24, 24);
            }
            g.setColor(hasVer ? ACCENT_HI : MUTED);
            g.fill(new Ellipse2D.Double(verRect.x + 15, cy - 5, 10, 10));
            String vs = hasVer ? selected.id() : (loadingVersions ? "..." : "Wersja");
            g.setFont(body(Font.PLAIN, 17));
            g.setColor(TEXT);
            g.drawString(fit(g, vs, verRect.width - 36 - 34), verRect.x + 36, mid(g, cy));
            g.setColor(MUTED);
            g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int chx = verRect.x + verRect.width - 22;
            g.drawLine(chx - 5, cy - 2, chx, cy + 3);
            g.drawLine(chx, cy + 3, chx + 5, cy - 2);

            // RAM
            ramRect.setBounds(verRect.x + verRect.width + 10, cy - bh / 2, 108, bh);
            g.setColor(hoverRam ? new Color(0x1C2832) : PANEL_HI);
            g.fill(new RoundRectangle2D.Double(ramRect.x, ramRect.y, ramRect.width, ramRect.height, 20, 20));
            g.setColor(hoverRam ? alpha(ACCENT, 120) : BORDER);
            g.setStroke(new BasicStroke(1f));
            g.draw(new RoundRectangle2D.Double(ramRect.x + 0.5, ramRect.y + 0.5, ramRect.width - 1, ramRect.height - 1, 20, 20));
            icon(g, 15, ramRect.x + 14, cy - 11, 22, ACCENT_HI);
            g.setFont(body(Font.BOLD, 17));
            g.setColor(TEXT);
            g.drawString(formatGb(ramMb), ramRect.x + 46, mid(g, cy));

            // przycisk GRAJ
            int pw = Math.max(150, Math.min(190, w / 6));
            playRect.setBounds(w - 14 - pw, cy - 27, pw, 54);
            boolean disabled = busy || loadingVersions;
            if (!disabled) {
                int gl = hoverPlay ? 16 : 10;
                for (int i = gl; i >= 2; i -= 2) {
                    g.setColor(alpha(GLOW, (int) (10 * (1 - i / (double) (gl + 2)) + 2)));
                    g.setStroke(new BasicStroke(i * 2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g.draw(new RoundRectangle2D.Double(playRect.x, playRect.y, playRect.width, playRect.height, 24, 24));
                }
                g.setStroke(new BasicStroke(1f));
            }
            g.setPaint(disabled ? new GradientPaint(0, playRect.y, ACCENT_DIM, 0, playRect.y + playRect.height, ACCENT_DIM)
                    : new GradientPaint(0, playRect.y, hoverPlay ? new Color(0x5BFFB4) : ACCENT_HI,
                    0, playRect.y + playRect.height, hoverPlay ? ACCENT_HI : ACCENT));
            g.fill(new RoundRectangle2D.Double(playRect.x, playRect.y, playRect.width, playRect.height, 24, 24));
            g.setFont(tracked(head(Font.BOLD, 22), 0.12f));
            int labelW = tw(g, "GRAJ");
            int total = 16 + 12 + labelW;
            int sx = playRect.x + (playRect.width - total) / 2;
            Color fg = disabled ? new Color(0x6FBF9A) : ON_ACCENT;
            g.setColor(fg);
            g.fill(new Polygon(new int[]{sx, sx, sx + 15}, new int[]{cy - 9, cy + 9, cy}, 3));
            g.drawString("GRAJ", sx + 28, mid(g, cy));

            // postęp
            int zx = ramRect.x + ramRect.width + 28;
            int zend = playRect.x - 28;
            if (zend - zx >= 140) {
                String label;
                String pct = "";
                double frac = 0;
                boolean sweep = false;
                if (gameRunning) {
                    label = "GRA URUCHOMIONA";
                    frac = 1;
                } else if (busy) {
                    if (!indeterminate && progTotal > 0) {
                        frac = progDone / (double) progTotal;
                        label = progressLabel();
                        pct = Math.round(frac * 100) + "%";
                    } else {
                        label = "SPRAWDZANIE PLIKÓW";
                        sweep = true;
                    }
                } else if (loadingVersions) {
                    label = "WCZYTYWANIE WERSJI";
                    sweep = true;
                } else if (!isMicrosoft()) {
                    label = "ZALOGUJ SIĘ, ŻEBY ZAGRAĆ";
                } else if (selected == null) {
                    label = "WYBIERZ WERSJĘ";
                } else {
                    label = "GOTOWY DO GRY";
                }
                int zw = zend - zx;
                g.setFont(tracked(mono(Font.PLAIN, 12), 0.14f));
                g.setColor(MUTED);
                g.drawString(fit(g, label, zw - 56), zx, cy - 6);
                if (!pct.isEmpty()) {
                    g.setColor(TEXT);
                    g.drawString(pct, zx + zw - tw(g, pct), cy - 6);
                }
                int by = cy + 8;
                g.setColor(new Color(0x1A242D));
                g.fill(new RoundRectangle2D.Double(zx, by, zw, 6, 6, 6));
                Shape oldClip = g.getClip();
                g.clip(new RoundRectangle2D.Double(zx, by, zw, 6, 6, 6));
                if (sweep) {
                    double seg = zw * 0.3;
                    double pos = ((time * 0.55) % 1.3) * (zw + seg) - seg;
                    g.setPaint(new GradientPaint((float) pos, 0, alpha(ACCENT, 0), (float) (pos + seg / 2), 0, ACCENT_HI));
                    g.fillRect((int) pos, by, (int) (seg / 2) + 1, 6);
                    g.setPaint(new GradientPaint((float) (pos + seg / 2), 0, ACCENT_HI, (float) (pos + seg), 0, alpha(ACCENT, 0)));
                    g.fillRect((int) (pos + seg / 2), by, (int) (seg / 2) + 1, 6);
                } else if (frac > 0) {
                    int fw = (int) Math.max(6, zw * Math.min(1.0, frac));
                    g.setColor(alpha(GLOW, 60));
                    g.fillRect(zx, by - 2, fw, 10);
                    g.setPaint(new GradientPaint(zx, 0, ACCENT, zx + zw, 0, ACCENT_HI));
                    g.fillRect(zx, by, fw, 6);
                }
                g.setClip(oldClip);
            }
            g.dispose();
        }
    }

    // ------------------------------------------------------------------ grafika sceny (gładka, renderowana - bez pikseli)

    private static final double LANTERN_X = 0.786;
    private static final double LANTERN_Y = 0.585;
    private static final int SCENE_W = 1280;
    private static final int SCENE_H = 680;

    private static int mix(int a, int b, double t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private static double hash1(int n) {
        n = (n << 13) ^ n;
        return 1.0 - ((n * (n * n * 15731 + 789221) + 1376312589) & 0x7fffffff) / 1073741824.0;
    }

    private static double vnoise(double x, int seed) {
        int i = (int) Math.floor(x);
        double f = x - i;
        double u = f * f * (3 - 2 * f);
        return hash1(i * 31 + seed * 1013) * (1 - u) + hash1((i + 1) * 31 + seed * 1013) * u;
    }

    private static double fbm(double x, int seed, int oct) {
        double a = 1;
        double s = 0;
        double n = 0;
        for (int o = 0; o < oct; o++) {
            s += a * vnoise(x, seed + o * 17);
            n += a;
            x *= 2.07;
            a *= 0.5;
        }
        return s / n;
    }

    private static int clamp255(int v) {
        return Math.max(0, Math.min(255, v));
    }

    /** Warstwa z przezroczystością (premultiplied - dzięki temu rozmycie nie ciemni krawędzi). */
    private static BufferedImage newLayer(int w, int h) {
        return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB_PRE);
    }

    private static Graphics2D layerGfx(BufferedImage im) {
        Graphics2D g = im.createGraphics();
        aa(g);
        return g;
    }

    private static void boxPass(int[] in, int[] out, int w, int h, int r, boolean horiz) {
        int len = horiz ? w : h;
        int lines = horiz ? h : w;
        int stride = horiz ? 1 : w;
        int lineStep = horiz ? w : 1;
        int div = 2 * r + 1;
        for (int l = 0; l < lines; l++) {
            int base = l * lineStep;
            long sa = 0;
            long sr = 0;
            long sg = 0;
            long sb = 0;
            for (int k = -r; k <= r; k++) {
                int p = in[base + Math.min(len - 1, Math.max(0, k)) * stride];
                sa += p >>> 24;
                sr += (p >> 16) & 255;
                sg += (p >> 8) & 255;
                sb += p & 255;
            }
            for (int i = 0; i < len; i++) {
                out[base + i * stride] = ((int) (sa / div) << 24) | ((int) (sr / div) << 16)
                        | ((int) (sg / div) << 8) | (int) (sb / div);
                int add = in[base + Math.min(len - 1, i + r + 1) * stride];
                int rem = in[base + Math.max(0, i - r) * stride];
                sa += (add >>> 24) - (rem >>> 24);
                sr += ((add >> 16) & 255) - ((rem >> 16) & 255);
                sg += ((add >> 8) & 255) - ((rem >> 8) & 255);
                sb += (add & 255) - (rem & 255);
            }
        }
    }

    /** Rozmycie gaussowskie (3x rozmycie pudełkowe) - daje miękki, "renderowany" wygląd zamiast pikseli. */
    private static void blur(BufferedImage im, double radius) {
        int r = (int) Math.round(radius);
        if (r < 1) {
            return;
        }
        int w = im.getWidth();
        int h = im.getHeight();
        int[] px = ((java.awt.image.DataBufferInt) im.getRaster().getDataBuffer()).getData();
        int[] tmp = new int[px.length];
        for (int i = 0; i < 3; i++) {
            boxPass(px, tmp, w, h, r, true);
            boxPass(tmp, px, w, h, r, false);
        }
    }

    private static void glow(Graphics2D g0, double cx, double cy, double rx, double ry, Color c, int a) {
        Graphics2D g = (Graphics2D) g0.create();
        g.translate(cx, cy);
        g.scale(1, ry / rx);
        g.setPaint(new RadialGradientPaint(0f, 0f, (float) rx, new float[]{0f, 0.4f, 1f},
                new Color[]{alpha(c, a), alpha(c, a / 3), alpha(c, 0)}));
        g.fill(new Ellipse2D.Double(-rx, -rx, rx * 2, rx * 2));
        g.dispose();
    }

    private static double[] ridgeYs(int w, double base, double amp, double freq, int seed, double sharp,
                                    java.util.function.DoubleUnaryOperator extra) {
        int n = w / 3 + 12;
        double[] ys = new double[n];
        for (int i = 0; i < n; i++) {
            double x = -6 + i * 3.0;
            double u = x / w * freq;
            double soft = 0.5 + 0.5 * fbm(u, seed, 5);
            double ridged = 1.15 - 1.6 * Math.abs(fbm(u * 1.3 + 7, seed + 3, 4));
            double v = (1 - sharp) * soft + sharp * Math.max(0, ridged);
            ys[i] = base - amp * v - (extra == null ? 0 : extra.applyAsDouble(x / w));
        }
        return ys;
    }

    private static Path2D areaPath(double[] ys, int h) {
        Path2D p = new Path2D.Double();
        p.moveTo(-6, h + 6);
        for (int i = 0; i < ys.length; i++) {
            p.lineTo(-6 + i * 3.0, ys[i]);
        }
        p.lineTo(-6 + (ys.length - 1) * 3.0, h + 6);
        p.closePath();
        return p;
    }

    private static Path2D linePath(double[] ys) {
        Path2D p = new Path2D.Double();
        p.moveTo(-6, ys[0]);
        for (int i = 1; i < ys.length; i++) {
            p.lineTo(-6 + i * 3.0, ys[i]);
        }
        return p;
    }

    private static double minOf(double[] a) {
        double m = a[0];
        for (double v : a) {
            m = Math.min(m, v);
        }
        return m;
    }

    private static void pineTree(Graphics2D g, Random r, double cx, double baseY, double height, double halfW) {
        double trunkH = height * 0.10;
        double trunkW = Math.max(1.5, halfW * 0.16);
        g.fill(new java.awt.geom.Rectangle2D.Double(cx - trunkW / 2, baseY - trunkH, trunkW, trunkH + 1));
        int tiers = Math.max(4, (int) (height / Math.max(8, halfW * 0.9)));
        double crown = height - trunkH;
        double step = crown / (tiers + 0.8);
        for (int i = 0; i < tiers; i++) {
            double yb = baseY - trunkH - i * step * 0.92;
            double yt = yb - step * 1.9;
            double hw = halfW * (1 - i / (double) (tiers + 0.5)) * (0.86 + 0.14 * r.nextDouble());
            double j = step * 0.10;
            Path2D p = new Path2D.Double();
            p.moveTo(cx + (r.nextDouble() - 0.5) * j, yt);
            p.lineTo(cx + hw * 0.62, yb - step * (0.62 + (r.nextDouble() - 0.5) * 0.12));
            p.lineTo(cx + hw * 0.50, yb - step * 0.55);
            p.lineTo(cx + hw, yb + (r.nextDouble() - 0.5) * j);
            p.lineTo(cx + hw * 0.1, yb - step * 0.22);
            p.lineTo(cx - hw * 0.1, yb - step * 0.22);
            p.lineTo(cx - hw, yb + (r.nextDouble() - 0.5) * j);
            p.lineTo(cx - hw * 0.50, yb - step * 0.55);
            p.lineTo(cx - hw * 0.62, yb - step * (0.62 + (r.nextDouble() - 0.5) * 0.12));
            p.closePath();
            g.fill(p);
        }
    }

    private static BufferedImage smoothScene(int w, int h) {
        double k = h / 470.0;
        double horizon = h * 0.52;
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        aa(g);

        // niebo + poświata za głównym szczytem
        g.setPaint(new GradientPaint(0, 0, new Color(0x02040A), 0, (float) horizon, new Color(0x263A3C)));
        g.fillRect(0, 0, w, h);
        glow(g, w * 0.60, horizon * 0.95, w * 0.36, h * 0.22, new Color(0x5F8580), 62);
        Random sr = new Random(11);
        for (int i = 0; i < 120; i++) {
            double sx = sr.nextDouble() * w;
            double sy = Math.pow(sr.nextDouble(), 1.4) * horizon * 0.85;
            double sz = (0.7 + sr.nextDouble() * 1.3) * k;
            int a = (int) (60 + 150 * (1 - sy / horizon) * sr.nextDouble());
            g.setColor(alpha(new Color(0xE6F4EE), clamp255(a)));
            g.fill(new Ellipse2D.Double(sx, sy, sz, sz));
        }

        // dalekie góry (z wyraźnym szczytem pośrodku)
        {
            BufferedImage L = newLayer(w, h);
            Graphics2D lg = layerGfx(L);
            double[] ys = ridgeYs(w, horizon + 10 * k, h * 0.17, 3.6, 21, 0.6,
                    x -> h * (0.17 * Math.exp(-Math.pow((x - 0.60) / 0.075, 2))
                    + 0.14 * Math.exp(-Math.pow((x - 0.90) / 0.06, 2)))
                    * (0.8 + 0.4 * Math.abs(vnoise(x * 45, 9))));
            lg.setPaint(new GradientPaint(0, (float) minOf(ys), new Color(0x44585F), 0, (float) (horizon + 20 * k), new Color(0x16262A)));
            lg.fill(areaPath(ys, h));
            lg.setStroke(new BasicStroke((float) (1.6 * k), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            lg.setColor(alpha(new Color(0x9DB6BD), 52));
            lg.draw(linePath(ys));
            lg.dispose();
            blur(L, 1.7 * k);
            g.drawImage(L, 0, 0, null);
        }
        g.setPaint(new GradientPaint(0, (float) (horizon - 0.13 * h), alpha(new Color(0x3E5B5C), 0),
                0, (float) (horizon + 0.02 * h), alpha(new Color(0x34504F), 118)));
        g.fillRect(0, (int) (horizon - 0.13 * h), w, (int) (0.16 * h));

        // bliższe góry
        {
            BufferedImage L = newLayer(w, h);
            Graphics2D lg = layerGfx(L);
            double[] ys = ridgeYs(w, horizon + 0.045 * h, 0.17 * h, 4.6, 77, 0.35, null);
            lg.setPaint(new GradientPaint(0, (float) minOf(ys), new Color(0x1E3236), 0, (float) (horizon + 0.07 * h), new Color(0x0B171A)));
            lg.fill(areaPath(ys, h));
            lg.setStroke(new BasicStroke((float) (1.3 * k)));
            lg.setColor(alpha(new Color(0x86A9AF), 46));
            lg.draw(linePath(ys));
            lg.dispose();
            blur(L, 1.5 * k);
            g.drawImage(L, 0, 0, null);
        }
        g.setPaint(new GradientPaint(0, (float) (horizon - 0.02 * h), alpha(new Color(0x35504F), 0),
                0, (float) (horizon + 0.05 * h), alpha(new Color(0x2E4745), 105)));
        g.fillRect(0, (int) (horizon - 0.02 * h), w, (int) (0.08 * h));

        // linia lasu w oddali
        Random rnd = new Random(5);
        {
            BufferedImage L = newLayer(w, h);
            Graphics2D lg = layerGfx(L);
            double[] hill = ridgeYs(w, 0.612 * h, 0.03 * h, 5, 31, 0.0, null);
            lg.setPaint(new GradientPaint(0, (float) (0.57 * h), new Color(0x0F1E19), 0, h, new Color(0x070E0C)));
            lg.fill(areaPath(hill, h));
            lg.setColor(new Color(0x0B1814));
            for (double x = -10; x < w + 10; x += 6.5 * k) {
                int idx = Math.max(0, Math.min(hill.length - 1, (int) ((x + 6) / 3)));
                double th = (0.035 + 0.06 * rnd.nextDouble()) * h;
                pineTree(lg, rnd, x, hill[idx] + 4 * k, th, th * 0.26);
            }
            lg.dispose();
            blur(L, 1.5 * k);
            g.drawImage(L, 0, 0, null);
        }

        // średni plan: sosny
        {
            BufferedImage L = newLayer(w, h);
            Graphics2D lg = layerGfx(L);
            lg.setColor(new Color(0x07110D));
            double[] xs = {0.03, 0.115, 0.205, 0.255, 0.345, 0.395, 0.43, 0.47, 0.51, 0.545, 0.58, 0.62, 0.655, 0.69, 0.725};
            double[] hs = {0.26, 0.19, 0.22, 0.13, 0.20, 0.27, 0.17, 0.21, 0.15, 0.19, 0.13, 0.17, 0.12, 0.15, 0.11};
            for (int i = 0; i < xs.length; i++) {
                double th = hs[i] * h;
                pineTree(lg, rnd, xs[i] * w, (0.675 + 0.02 * rnd.nextDouble()) * h, th, th * 0.24);
            }
            lg.dispose();
            blur(L, 2.4 * k);
            g.drawImage(L, 0, 0, null);
        }

        // pierwszy plan: ziemia, trawa, zielone światło
        {
            BufferedImage L = newLayer(w, h);
            Graphics2D lg = layerGfx(L);
            double[] gy = ridgeYs(w, 0.72 * h, 0.02 * h, 7, 55, 0.0, null);
            lg.setPaint(new GradientPaint(0, (float) (0.69 * h), new Color(0x12231B), 0, h, new Color(0x030706)));
            lg.fill(areaPath(gy, h));
            glow(lg, w * 0.06, 0.82 * h, w * 0.13, h * 0.07, new Color(0x19E68C), 55);
            glow(lg, w * 0.30, 0.79 * h, w * 0.10, h * 0.05, new Color(0x19E68C), 34);
            glow(lg, w * 0.50, 0.77 * h, w * 0.12, h * 0.04, new Color(0x19E68C), 22);
            glow(lg, w * 0.84, 0.86 * h, w * 0.22, h * 0.10, new Color(0x19E68C), 70);
            for (double x = -4; x < w + 4; x += 4.5 * k) {
                if (rnd.nextDouble() < 0.25) {
                    continue;
                }
                int idx = Math.max(0, Math.min(gy.length - 1, (int) ((x + 6) / 3)));
                double bh = (3 + rnd.nextDouble() * 9) * k;
                boolean lit = rnd.nextDouble() < (x > w * 0.6 ? 0.34 : 0.16);
                lg.setColor(lit ? alpha(new Color(0x2C6B47), 200) : new Color(0x0F2018));
                Path2D bl = new Path2D.Double();
                bl.moveTo(x - 1.6 * k, gy[idx] + 2 * k);
                bl.lineTo(x + (rnd.nextDouble() - 0.5) * 3 * k, gy[idx] - bh);
                bl.lineTo(x + 1.6 * k, gy[idx] + 2 * k);
                bl.closePath();
                lg.fill(bl);
            }
            lg.dispose();
            blur(L, 0.9 * k);
            g.drawImage(L, 0, 0, null);
        }

        // duże sosny z lewej (nieco nieostre)
        {
            BufferedImage L = newLayer(w, h);
            Graphics2D lg = layerGfx(L);
            lg.setColor(new Color(0x040907));
            pineTree(lg, rnd, 0.03 * w, 0.82 * h, 0.95 * h, 0.085 * w);
            pineTree(lg, rnd, 0.285 * w, 0.76 * h, 0.40 * h, 0.040 * w);
            lg.dispose();
            blur(L, 1.8 * k);
            g.drawImage(L, 0, 0, null);
        }

        // dom z latarnią
        {
            BufferedImage L = newLayer(w, h);
            Graphics2D lg = layerGfx(L);
            double x0 = 0.772 * w;
            double ground = 0.735 * h;
            double wallTop = 0.535 * h;
            double logH = 0.026 * h;
            for (double y = wallTop; y < ground; y += logH) {
                boolean stone = y > ground - 0.05 * h;
                int c1 = stone ? 0x333B40 : mix(0x43301F, 0x2C1F14, rnd.nextDouble());
                int c2 = mix(c1, 0x000000, 0.35);
                lg.setPaint(new GradientPaint(0, (float) y, new Color(c1), 0, (float) (y + logH), new Color(c2)));
                lg.fill(new RoundRectangle2D.Double(x0, y, w - x0 + 12, logH - 0.6 * k, 3 * k, 3 * k));
            }
            lg.setPaint(new GradientPaint((float) x0, 0, alpha(Color.BLACK, 170), (float) (x0 + 0.018 * w), 0, alpha(Color.BLACK, 0)));
            lg.fill(new java.awt.geom.Rectangle2D.Double(x0, wallTop, 0.018 * w, ground - wallTop));
            lg.setPaint(new GradientPaint(0, (float) wallTop, alpha(Color.BLACK, 170), 0, (float) (wallTop + 0.05 * h), alpha(Color.BLACK, 0)));
            lg.fill(new java.awt.geom.Rectangle2D.Double(x0, wallTop, w - x0 + 12, 0.05 * h));
            // dach
            Path2D roof = new Path2D.Double();
            roof.moveTo(x0 - 0.032 * w, wallTop + 0.012 * h);
            roof.lineTo(x0 + 0.05 * w, 0.445 * h);
            roof.lineTo(w + 16, 0.395 * h);
            roof.lineTo(w + 16, wallTop - 0.006 * h);
            roof.closePath();
            lg.setPaint(new GradientPaint(0, (float) (0.40 * h), new Color(0x1C1612), 0, (float) wallTop, new Color(0x0E0A08)));
            lg.fill(roof);
            lg.setStroke(new BasicStroke((float) (1.6 * k), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            lg.setColor(alpha(new Color(0x7E948E), 90));
            lg.draw(new java.awt.geom.Line2D.Double(x0 - 0.032 * w, wallTop + 0.012 * h, x0 + 0.05 * w, 0.445 * h));
            lg.draw(new java.awt.geom.Line2D.Double(x0 + 0.05 * w, 0.445 * h, w + 16, 0.395 * h));
            // okno
            double wx = x0 + 0.115 * w;
            double wy = 0.595 * h;
            double ww = 0.042 * w;
            double wh = 0.09 * h;
            lg.setColor(new Color(0x4A3626));
            lg.fill(new RoundRectangle2D.Double(wx - 3 * k, wy - 3 * k, ww + 6 * k, wh + 6 * k, 3 * k, 3 * k));
            lg.setPaint(new GradientPaint(0, (float) wy, new Color(0x0B1F19), 0, (float) (wy + wh), new Color(0x040B09)));
            lg.fill(new java.awt.geom.Rectangle2D.Double(wx, wy, ww, wh));
            lg.setColor(new Color(0x4A3626));
            lg.fill(new java.awt.geom.Rectangle2D.Double(wx + ww / 2 - k, wy, 2 * k, wh));
            // latarnia
            double lx = LANTERN_X * w;
            double ly = LANTERN_Y * h;
            lg.setColor(new Color(0x1A1410));
            lg.fill(new java.awt.geom.Rectangle2D.Double(lx - 1 * k, ly - 0.05 * h, 2 * k, 0.04 * h));
            lg.fill(new RoundRectangle2D.Double(lx - 5 * k, ly - 9 * k, 10 * k, 18 * k, 3 * k, 3 * k));
            lg.setColor(new Color(0x5CF2A6));
            lg.fill(new RoundRectangle2D.Double(lx - 3.4 * k, ly - 6.6 * k, 6.8 * k, 13.2 * k, 2 * k, 2 * k));
            lg.setColor(new Color(0xE6FFF1));
            lg.fill(new RoundRectangle2D.Double(lx - 1.6 * k, ly - 3.6 * k, 3.2 * k, 7.2 * k, 1.5 * k, 1.5 * k));
            lg.dispose();
            blur(L, 1.0 * k);
            g.drawImage(L, 0, 0, null);
            // zielone światło latarni na ścianie i ziemi
            glow(g, lx, ly, w * 0.10, h * 0.20, new Color(0x19E68C), 95);
            glow(g, lx, 0.74 * h, w * 0.12, h * 0.05, new Color(0x19E68C), 60);
        }
        g.dispose();

        // winieta i delikatne ziarno (usuwa "pasy" w gradientach)
        int[] px = ((java.awt.image.DataBufferInt) out.getRaster().getDataBuffer()).getData();
        Random nr = new Random(99);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double dx = (x - w / 2.0) / (w * 0.62);
                double dy = (y - h * 0.55) / (h * 0.75);
                double v = 1 - 0.50 * Math.min(1, Math.pow(dx * dx + dy * dy, 1.1));
                double nz = (nr.nextDouble() - 0.5) * 6;
                int p = px[y * w + x];
                int r = clamp255((int) (((p >> 16) & 255) * v + nz));
                int gg = clamp255((int) (((p >> 8) & 255) * v + nz));
                int b = clamp255((int) ((p & 255) * v + nz));
                px[y * w + x] = (r << 16) | (gg << 8) | b;
            }
        }
        return out;
    }

    /** Miniatury kart wiadomości (gładkie): 0 = zielone kryształy w skale, 1 = wioska nocą. */
    private static BufferedImage thumb(int kind, int w, int h) {
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = im.createGraphics();
        aa(g);
        Random rnd = new Random(kind * 77L + 5);
        if (kind == 0) {
            g.setPaint(new GradientPaint(0, 0, new Color(0x0A1411), 0, h, new Color(0x1B2B25)));
            g.fillRect(0, 0, w, h);
            for (int i = 0; i < 46; i++) {
                double cx = rnd.nextDouble() * w;
                double cy = rnd.nextDouble() * h;
                double s = (10 + rnd.nextDouble() * 26) * h / 128.0;
                Path2D t = new Path2D.Double();
                t.moveTo(cx, cy - s);
                t.lineTo(cx + s * (0.6 + rnd.nextDouble() * 0.6), cy + s * 0.5);
                t.lineTo(cx - s * (0.6 + rnd.nextDouble() * 0.6), cy + s * 0.6);
                t.closePath();
                g.setColor(alpha(rnd.nextBoolean() ? Color.BLACK : new Color(0x4A6A60), 34));
                g.fill(t);
            }
            double[][] cs = {{0.30, 0.66, 0.40, -0.1}, {0.58, 0.82, 0.32, 0.18}, {0.72, 0.56, 0.46, 0.12},
                    {0.14, 0.86, 0.28, -0.22}, {0.46, 0.46, 0.30, 0.05}, {0.84, 0.88, 0.26, -0.15}};
            for (double[] c : cs) {
                double cx = c[0] * w;
                double cy = c[1] * h;
                double len = c[2] * h;
                glow(g, cx, cy - len * 0.5, len * 1.7, len * 1.7, new Color(0x19E68C), 120);
                for (int i = -1; i <= 1; i++) {
                    Graphics2D gc = (Graphics2D) g.create();
                    double l2 = len * (1 - Math.abs(i) * 0.28);
                    double bw = len * 0.12;
                    gc.translate(cx + i * len * 0.2, cy + Math.abs(i) * len * 0.08);
                    gc.rotate(c[3] + i * 0.22);
                    Path2D left = new Path2D.Double();
                    left.moveTo(-bw, 0);
                    left.lineTo(-bw, -l2 * 0.72);
                    left.lineTo(0, -l2);
                    left.lineTo(0, 0);
                    left.closePath();
                    Path2D right = new Path2D.Double();
                    right.moveTo(0, 0);
                    right.lineTo(0, -l2);
                    right.lineTo(bw, -l2 * 0.72);
                    right.lineTo(bw, 0);
                    right.closePath();
                    gc.setPaint(new GradientPaint(0, (float) -l2, new Color(0xC9FFE4), 0, 0, new Color(0x2EE59A)));
                    gc.fill(left);
                    gc.setPaint(new GradientPaint(0, (float) -l2, new Color(0x5BE8A8), 0, 0, new Color(0x0F8F5A)));
                    gc.fill(right);
                    gc.dispose();
                }
            }
        } else {
            g.setPaint(new GradientPaint(0, 0, new Color(0x0C1A21), 0, h, new Color(0x264348)));
            g.fillRect(0, 0, w, h);
            glow(g, w * 0.5, h * 0.62, w * 0.55, h * 0.35, new Color(0xFFB35A), 40);
            g.setColor(new Color(0x0A1411));
            for (int i = 0; i < 14; i++) {
                double tx = rnd.nextDouble() * w;
                double th = (0.16 + 0.22 * rnd.nextDouble()) * h;
                pineTree(g, rnd, tx, h * 0.97, th, th * 0.26);
            }
            for (int i = 0; i < 9; i++) {
                double bw = (0.13 + 0.07 * rnd.nextDouble()) * w;
                double bh = (0.14 + 0.07 * rnd.nextDouble()) * h;
                double bx = 0.04 * w + rnd.nextDouble() * (w - bw - 0.08 * w);
                double by = h * (0.50 + 0.38 * rnd.nextDouble());
                g.setPaint(new GradientPaint(0, (float) by, new Color(0x5A3F2B), 0, (float) (by + bh), new Color(0x2E2016)));
                g.fill(new java.awt.geom.Rectangle2D.Double(bx, by, bw, bh));
                Path2D roof = new Path2D.Double();
                roof.moveTo(bx - bw * 0.1, by);
                roof.lineTo(bx + bw / 2, by - bh * 0.55);
                roof.lineTo(bx + bw * 1.1, by);
                roof.closePath();
                g.setColor(new Color(0x1B130E));
                g.fill(roof);
                glow(g, bx + bw * 0.3, by + bh * 0.4, bw * 0.55, bw * 0.55, new Color(0xFFB35A), 150);
                g.setColor(new Color(0xFFD08A));
                g.fill(new java.awt.geom.Rectangle2D.Double(bx + bw * 0.22, by + bh * 0.3, bw * 0.16, bh * 0.22));
            }
            for (int i = 0; i < 26; i++) {
                glow(g, rnd.nextDouble() * w, rnd.nextDouble() * h, 5, 5, new Color(0xFFB35A), 90);
            }
        }
        g.dispose();
        BufferedImage soft = newLayer(w, h);
        Graphics2D sg = soft.createGraphics();
        sg.drawImage(im, 0, 0, null);
        sg.dispose();
        blur(soft, Math.max(1, h / 110.0));
        BufferedImage res = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D rg = res.createGraphics();
        rg.drawImage(soft, 0, 0, null);
        rg.dispose();
        return res;
    }

    // ------------------------------------------------------------------ skin

    private static String httpText(String url) throws IOException {
        URLConnection c = new URL(url).openConnection();
        c.setConnectTimeout(6000);
        c.setReadTimeout(8000);
        c.setRequestProperty("User-Agent", "BushLaunch/" + LAUNCHER_VERSION);
        try (InputStream in = c.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static BufferedImage httpImage(String url) throws IOException {
        URLConnection c = new URL(url).openConnection();
        c.setConnectTimeout(6000);
        c.setReadTimeout(8000);
        c.setRequestProperty("User-Agent", "BushLaunch/" + LAUNCHER_VERSION);
        try (InputStream in = c.getInputStream()) {
            return ImageIO.read(in);
        }
    }

    /** Pobiera prawdziwy skin gracza: najpierw z serwerów Mojang (po UUID), awaryjnie z minotar; z cache na dysku. */
    private BufferedImage fetchSkin(String name, String uuid) {
        Path cache = baseDir.resolve("skins").resolve(name + ".png");
        BufferedImage img = null;
        try {
            String id = uuid;
            if (id == null || id.isBlank()) {
                id = Json.str(Json.get(Json.parse(httpText("https://api.mojang.com/users/profiles/minecraft/" + name)), "id"));
            }
            Object prof = Json.parse(httpText("https://sessionserver.mojang.com/session/minecraft/profile/" + id));
            List<Object> props = Json.list(Json.get(prof, "properties"));
            if (props != null) {
                for (Object prop : props) {
                    if ("textures".equals(Json.str(Json.get(prop, "name")))) {
                        String val = Json.str(Json.get(prop, "value"));
                        Object tex = Json.parse(new String(java.util.Base64.getDecoder().decode(val), StandardCharsets.UTF_8));
                        String url = Json.str(Json.get(tex, "textures", "SKIN", "url"));
                        if (url != null) {
                            img = httpImage(url);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // spróbujemy zapasowego źródła
        }
        if (img == null) {
            try {
                img = httpImage("https://minotar.net/skin/" + name);
            } catch (Exception ignored) {
                // zostaje cache
            }
        }
        try {
            if (img != null) {
                Files.createDirectories(cache.getParent());
                ImageIO.write(img, "png", cache.toFile());
            } else if (Files.exists(cache)) {
                img = ImageIO.read(cache.toFile());
            }
        } catch (IOException ignored) {
            // brak cache
        }
        return img;
    }

    private void loadSkinAsync() {
        final String n = nick;
        final String uuid = msAccount != null ? msAccount.uuid() : null;
        if (!validNick(n)) {
            skin = null;
            frame.repaint();
            return;
        }
        Thread t = new Thread(() -> {
            BufferedImage result = fetchSkin(n, uuid);
            SwingUtilities.invokeLater(() -> {
                if (n.equals(nick)) {
                    skin = result;
                    frame.repaint();
                }
            });
        }, "skin");
        t.setDaemon(true);
        t.start();
    }

    // ------------------------------------------------------------------ znajomi (lista lokalna)

    // ------------------------------------------------------------------ serwer znajomych

    /** Adres serwera znajomych: config.properties (socialUrl) albo social.properties wbudowany w jar. Pusty = tryb lokalny. */
    private String socialUrl() {
        String c = config.getProperty("socialUrl", "").trim();
        if (c.isEmpty()) {
            try (InputStream in = BushLaunch.class.getResourceAsStream("/social.properties")) {
                if (in != null) {
                    Properties p = new Properties();
                    p.load(in);
                    c = p.getProperty("url", "").trim();
                }
            } catch (IOException ignored) {
                // brak pliku = tryb lokalny
            }
        }
        while (c.endsWith("/")) {
            c = c.substring(0, c.length() - 1);
        }
        return c;
    }

    private boolean socialEnabled() {
        return !socialUrl().isEmpty();
    }

    /** Nick, UUID i token gry do potwierdzenia konta u Mojangu (z tą samą blokadą co start gry). */
    private Social.Creds socialCreds() throws Exception {
        synchronized (authLock) {
            MicrosoftAuth.Account acc = msAccount;
            if (acc == null) {
                throw new IOException("Zaloguj się kontem Microsoft, żeby korzystać ze znajomych.");
            }
            Social.Creds c = socialCredsCache;
            if (c != null && c.uuid().equals(acc.uuid()) && System.currentTimeMillis() - socialCredsAt < 6 * 3600_000L) {
                return c;
            }
            String cid = msClientId();
            if (cid == null || cid.isBlank()) {
                throw new IOException("Brak konfiguracji logowania Microsoft.");
            }
            MicrosoftAuth.Login l = MicrosoftAuth.refresh(cid, acc, s -> { });
            MicrosoftAuth.Account fresh = new MicrosoftAuth.Account(l.name(), l.uuid(), l.refreshToken());
            MicrosoftAuth.saveAccount(baseDir, fresh);
            msAccount = fresh;
            c = new Social.Creds(l.name(), l.uuid(), l.mcToken());
            socialCredsCache = c;
            socialCredsAt = System.currentTimeMillis();
            return c;
        }
    }

    private void startSocial() {
        if (socialExec != null) {
            socialExec.shutdownNow();
            socialExec = null;
        }
        socialApi = null;
        socialSnap = null;
        friendUuids.clear();
        if (!socialEnabled()) {
            socialStatus = "";
            refreshSocialUi();
            return;
        }
        if (!isMicrosoft()) {
            socialStatus = "Zaloguj się kontem Microsoft, żeby korzystać ze znajomych.";
            refreshSocialUi();
            return;
        }
        socialStatus = "";
        socialApi = new Social(socialUrl(), this::socialCreds, "BushLaunch/" + LAUNCHER_VERSION);
        if (!socialHookAdded) {
            socialHookAdded = true;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                Social a = socialApi;
                if (a != null) {
                    a.offline();
                }
            }));
        }
        socialExec = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "social");
            t.setDaemon(true);
            return t;
        });
        socialExec.scheduleWithFixedDelay(this::socialTick, 0, 20, java.util.concurrent.TimeUnit.SECONDS);
    }

    /** Natychmiastowy ping (np. po starcie/zakończeniu gry), żeby znajomi od razu widzieli zmianę. */
    private void socialPingNow() {
        java.util.concurrent.ScheduledExecutorService ex = socialExec;
        if (ex != null) {
            try {
                ex.execute(this::socialTick);
            } catch (RuntimeException ignored) {
                // executor właśnie się zamyka
            }
        }
    }

    private void socialTick() {
        Social api = socialApi;
        if (api == null) {
            return;
        }
        try {
            Social.Snapshot sn = api.ping(gameRunning ? "game" : "online", gameInfo);
            if (!"1".equals(config.getProperty("friendsMigrated", ""))) {
                // jednorazowo: dotychczasowa lokalna lista -> zaproszenia (druga strona musi je potwierdzić)
                for (String f : friends()) {
                    try {
                        api.request(f);
                    } catch (IOException ignored) {
                        // np. już wysłane albo limit - pomijamy
                    }
                }
                SwingUtilities.invokeLater(() -> {
                    config.setProperty("friendsMigrated", "1");
                    saveConfig();
                });
                sn = api.fetch();
            }
            socialStatus = "";
            applySnapshot(sn);
        } catch (Exception e) {
            String m = e.getMessage();
            socialStatus = m == null || m.isBlank() ? "Brak połączenia z serwerem znajomych." : m;
            SwingUtilities.invokeLater(this::refreshSocialUi);
        }
    }

    private static String presenceText(Social.Friend f) {
        if ("game".equals(f.state())) {
            return f.info().isEmpty() ? "Gra w Minecraft" : f.info();
        }
        return "online".equals(f.state()) ? "Online w launcherze" : "Offline";
    }

    private void applySnapshot(Social.Snapshot sn) {
        List<String> names = new ArrayList<>();
        for (Social.Friend f : sn.friends()) {
            presence.put(f.name(), new String[]{f.state(), presenceText(f)});
            friendUuids.put(f.name(), f.uuid());
            names.add(f.name());
        }
        boolean fresh = false;
        for (Social.Request r : sn.incoming()) {
            if (seenRequests.add(r.uuid())) {
                fresh = true;
            }
        }
        final boolean newRequest = fresh;
        socialSnap = sn;
        SwingUtilities.invokeLater(() -> {
            // ostatnio znana lista zostaje w config (widać ją też bez połączenia)
            String joined = String.join(",", names);
            if (!joined.equals(config.getProperty("friends", ""))) {
                config.setProperty("friends", joined);
                saveConfig();
            }
            loadFriendSkins();
            refreshSocialUi();
            if (newRequest) {
                appendLog("Nowe zaproszenie do znajomych - otwórz listę znajomych, żeby je potwierdzić.");
                if (frame != null && frame.isActive() && friendsDialog == null) {
                    openFriendsDialog();
                }
            }
        });
    }

    private void refreshSocialUi() {
        if (hero != null) {
            hero.repaint();
        }
        if (friendsRefresh != null) {
            friendsRefresh.run();
        }
    }

    private interface SocialCall {
        void run(Social api) throws Exception;
    }

    /** Wykonuje akcję na serwerze znajomych w tle, odświeża listę i woła done(błąd albo null) w wątku okna. */
    private void socialDo(SocialCall call, java.util.function.Consumer<String> done) {
        Social api = socialApi;
        if (api == null) {
            if (done != null) {
                done.accept("Zaloguj się kontem Microsoft, żeby korzystać ze znajomych.");
            }
            return;
        }
        Thread t = new Thread(() -> {
            String err = null;
            try {
                call.run(api);
                applySnapshot(api.fetch());
            } catch (Exception e) {
                err = e.getMessage() == null ? "Nie udało się wykonać akcji." : e.getMessage();
            }
            final String fe = err;
            SwingUtilities.invokeLater(() -> {
                friendsNote = fe == null || done != null ? "" : fe;
                if (done != null) {
                    done.accept(fe);
                } else if (fe != null) {
                    refreshSocialUi();
                }
            });
        }, "social-action");
        t.setDaemon(true);
        t.start();
    }

    // ------------------------------------------------------------------ znajomi (lista)

    private List<String> friends() {
        List<String> out = new ArrayList<>();
        for (String f : config.getProperty("friends", "").split(",")) {
            if (validNick(f.trim())) {
                out.add(f.trim());
            }
        }
        return out;
    }

    private void saveFriends(List<String> list) {
        config.setProperty("friends", String.join(",", list));
        saveConfig();
        loadFriendSkins();
        if (hero != null) {
            hero.repaint();
        }
    }

    /** Nicki, których głowy potrzebujemy: znajomi oraz osoby z zaproszeń (przychodzących i wysłanych). */
    private List<String> skinNames() {
        List<String> out = friends();
        Social.Snapshot sn = socialSnap;
        if (sn != null) {
            for (Social.Request r : sn.incoming()) {
                if (!out.contains(r.name())) {
                    out.add(r.name());
                }
            }
            for (String o : sn.outgoing()) {
                if (!out.contains(o)) {
                    out.add(o);
                }
            }
        }
        return out;
    }

    private void loadFriendSkins() {
        for (String f : skinNames()) {
            if (friendSkins.containsKey(f)) {
                continue;
            }
            friendSkins.put(f, NO_SKIN);
            Thread t = new Thread(() -> {
                BufferedImage img = fetchSkin(f, null);
                if (img != null) {
                    friendSkins.put(f, img);
                    SwingUtilities.invokeLater(() -> {
                        if (hero != null) {
                            hero.repaint();
                        }
                    });
                }
            }, "friend-skin");
            t.setDaemon(true);
            t.start();
        }
    }

    /** Statusy znajomych: nick -> {stan: game|online|offline, opis}. Launcher nie ma serwera obecności, więc domyślnie "offline". */
    private final Map<String, String[]> presence = new java.util.concurrent.ConcurrentHashMap<>();

    private String[] presenceOf(String nick) {
        String[] p = presence.get(nick);
        return p != null ? p : new String[]{"offline", "Offline"};
    }

    private void openFriendsDialog() {
        if (friendsDialog != null && friendsDialog.isShowing()) {
            friendsDialog.toFront();
            return;
        }
        if (System.currentTimeMillis() - friendsClosedAt < 350) {
            return; // kliknięcie w plakietkę, które właśnie zamknęło listę, nie otwiera jej od nowa
        }
        friendsNote = "";
        JDialog d = new JDialog(frame, false);
        d.setUndecorated(true);
        Runnable[] resize = new Runnable[1];
        JPanel root = buildFriendsPanel(d::dispose, () -> resize[0].run());
        d.setContentPane(root);
        resize[0] = () -> {
            d.pack();
            Point fp = frame.getLocationOnScreen();
            d.setLocation(fp.x + frame.getWidth() - d.getWidth() - 34, fp.y + 126);
        };
        resize[0].run();
        final long opened = System.currentTimeMillis();
        // klik w głównym oknie poza listą zamyka listę (nie polegamy tylko na utracie fokusu)
        java.awt.event.AWTEventListener outside = ev -> {
            if (ev instanceof MouseEvent me && me.getID() == MouseEvent.MOUSE_PRESSED
                    && me.getSource() instanceof Component c
                    && SwingUtilities.getWindowAncestor(c) == frame
                    && System.currentTimeMillis() - opened > 250) {
                d.dispose();
            }
        };
        java.awt.Toolkit.getDefaultToolkit().addAWTEventListener(outside, java.awt.AWTEvent.MOUSE_EVENT_MASK);
        d.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowDeactivated(java.awt.event.WindowEvent e) {
                // tuż po otwarciu okno bywa jeszcze "przekazywane" - nie zamykamy go od razu
                if (System.currentTimeMillis() - opened > 400) {
                    d.dispose();
                }
            }

            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                java.awt.Toolkit.getDefaultToolkit().removeAWTEventListener(outside);
                friendsDialog = null;
                friendsRefresh = null;
                friendsClosedAt = System.currentTimeMillis();
            }
        });
        friendsDialog = d;
        d.setVisible(true);
        d.toFront();
        d.requestFocus();
    }

    private JPanel buildFriendsPanel(Runnable close, Runnable resize) {
        final int W = 306;
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(new Color(0x0D1318));
        root.setBorder(BorderFactory.createLineBorder(new Color(0x1E2A34)));

        // nagłówek: Znajomi · 3/4 online  [x]
        JComponent head = new Clickable(null) {
            private boolean hoverX;

            {
                setPreferredSize(new Dimension(W, 52));
                addMouseMotionListener(new MouseAdapter() {
                    @Override
                    public void mouseMoved(MouseEvent e) {
                        boolean h = e.getX() > getWidth() - 46;
                        if (h != hoverX) {
                            hoverX = h;
                            repaint();
                        }
                    }
                });
                addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseReleased(MouseEvent e) {
                        if (e.getX() > getWidth() - 46) {
                            close.run();
                        }
                    }
                });
            }

            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                aa(g);
                List<String> fs = friends();
                int on = 0;
                for (String f : fs) {
                    if (!"offline".equals(presenceOf(f)[0])) {
                        on++;
                    }
                }
                int cy = getHeight() / 2;
                g.setFont(head(Font.BOLD, 19));
                g.setColor(TEXT);
                g.drawString("Znajomi", 16, mid(g, cy));
                int x = 16 + g.getFontMetrics().stringWidth("Znajomi") + 10;
                g.setFont(body(Font.PLAIN, 14));
                g.setColor(MUTED);
                g.drawString("\u00b7  " + on + "/" + fs.size() + " online", x, mid(g, cy));
                g.setColor(hoverX ? TEXT : MUTED);
                g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int cx = getWidth() - 24;
                g.drawLine(cx - 5, cy - 5, cx + 5, cy + 5);
                g.drawLine(cx - 5, cy + 5, cx + 5, cy - 5);
                g.dispose();
            }
        };
        root.add(head, BorderLayout.NORTH);

        JPanel list = new JPanel();
        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.add(list, BorderLayout.NORTH);
        JScrollPane scroll = darkScroll(wrap);
        scroll.setBorder(new EmptyBorder(0, 0, 0, 0));
        scroll.getViewport().setOpaque(false);
        scroll.getViewport().setBackground(new Color(0x0D1318));
        scroll.getVerticalScrollBar().setBackground(new Color(0x0D1318));

        // dolny przycisk "Dodaj znajomego" z przerywaną ramką, po kliknięciu pole na nick
        CardLayout bottomCards = new CardLayout();
        JPanel bottom = new JPanel(bottomCards);
        bottom.setOpaque(false);
        bottom.setPreferredSize(new Dimension(W, 76));

        Clickable addBtn = new Clickable(null) {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                aa(g);
                int w = getWidth() - 28;
                int h = getHeight() - 24;
                g.translate(14, 6);
                g.setColor(hover ? alpha(ACCENT, 20) : alpha(Color.WHITE, 5));
                g.fill(new RoundRectangle2D.Double(0, 0, w, h, 14, 14));
                g.setColor(hover ? alpha(ACCENT, 170) : new Color(0x2A3844));
                g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{5f, 4f}, 0f));
                g.draw(new RoundRectangle2D.Double(0.6, 0.6, w - 1.2, h - 1.2, 14, 14));
                g.setFont(body(Font.PLAIN, 15));
                String t = "Dodaj znajomego";
                int tw = g.getFontMetrics().stringWidth(t);
                int total = 26 + 12 + tw;
                int sx = (w - total) / 2;
                int cy = h / 2;
                Color c = hover ? TEXT : new Color(0xB4BFC8);
                g.setColor(c);
                g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(new Ellipse2D.Double(sx + 4, cy - 8, 8, 8));
                g.draw(new Arc2D.Double(sx - 1, cy + 2, 18, 14, 0, 180, Arc2D.OPEN));
                g.drawLine(sx + 21, cy - 4, sx + 21, cy + 4);
                g.drawLine(sx + 17, cy, sx + 25, cy);
                g.drawString(t, sx + 38, mid(g, cy));
                g.dispose();
            }
        };
        addBtn.setPreferredSize(new Dimension(W, 76));

        JPanel form = new JPanel(new BorderLayout(0, 4));
        form.setOpaque(false);
        form.setBorder(new EmptyBorder(8, 14, 8, 14));
        JTextField nickField = field("");
        FlatButton add = new FlatButton("Dodaj", true);
        add.setBorder(new EmptyBorder(9, 16, 9, 16));
        JLabel err = new JLabel(" ");
        err.setForeground(new Color(0xFF6B5A));
        err.setFont(body(Font.PLAIN, 12));
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.add(nickField, BorderLayout.CENTER);
        row.add(add, BorderLayout.EAST);
        form.add(row, BorderLayout.NORTH);
        form.add(err, BorderLayout.SOUTH);

        bottom.add(addBtn, "btn");
        bottom.add(form, "form");
        addBtn.onClick = () -> {
            bottomCards.show(bottom, "form");
            nickField.requestFocusInWindow();
        };

        Runnable[] refresh = new Runnable[1];
        refresh[0] = () -> {
            list.removeAll();
            final boolean srv = socialEnabled();
            Social.Snapshot sn = socialSnap;
            if (srv && !socialStatus.isEmpty()) {
                list.add(noteRow(socialStatus, new Color(0xFF6B5A)));
            }
            if (!friendsNote.isEmpty()) {
                list.add(noteRow(friendsNote, new Color(0xFF6B5A)));
            }
            if (validNick(nick)) {
                list.add(new SelfRow());
            }
            List<String> fs = friends();
            boolean hasIncoming = srv && sn != null && !sn.incoming().isEmpty();
            if (hasIncoming) {
                list.add(new SectionLabel("ZAPROSZENIA · " + sn.incoming().size()));
                for (Social.Request rq : sn.incoming()) {
                    list.add(new IncomingRow(rq.name(),
                            () -> socialDo(api -> api.accept(rq.uuid()), null),
                            () -> socialDo(api -> api.decline(rq.uuid()), null)));
                }
                if (!fs.isEmpty()) {
                    list.add(new SectionLabel("ZNAJOMI"));
                }
            }
            for (String f : fs) {
                list.add(new FriendRow(f, () -> {
                    String id = friendUuids.get(f);
                    if (srv && id != null) {
                        socialDo(api -> api.remove(id), null);
                    } else {
                        List<String> cur = friends();
                        cur.remove(f);
                        saveFriends(cur);
                        refresh[0].run();
                    }
                }, () -> {
                    close.run();
                    onPlay();
                }));
            }
            if (fs.isEmpty() && !hasIncoming) {
                JLabel none = new JLabel("  Nie masz jeszcze znajomych.");
                none.setForeground(MUTED);
                none.setFont(body(Font.PLAIN, 13));
                none.setBorder(new EmptyBorder(14, 14, 14, 14));
                list.add(none);
            }
            if (srv && sn != null && !sn.outgoing().isEmpty()) {
                list.add(new SectionLabel("WYSŁANE · " + sn.outgoing().size()));
                for (String o : sn.outgoing()) {
                    list.add(new OutgoingRow(o, () -> socialDo(api -> api.cancel(o), null)));
                }
            }
            scroll.setPreferredSize(new Dimension(W, Math.max(52, Math.min(372, list.getPreferredSize().height))));
            list.revalidate();
            list.repaint();
            head.repaint();
            resize.run();
        };
        add.addActionListener(e -> {
            String n = nickField.getText().trim();
            List<String> cur = friends();
            if (!validNick(n)) {
                err.setForeground(new Color(0xFF6B5A));
                err.setText("Nick: 3-16 znaków (litery, cyfry, _).");
            } else if (socialEnabled()) {
                err.setForeground(MUTED);
                err.setText("Wysyłanie...");
                final boolean[] accepted = new boolean[1];
                socialDo(api -> accepted[0] = api.request(n), er -> {
                    if (er != null) {
                        err.setForeground(new Color(0xFF6B5A));
                        err.setText(er);
                    } else {
                        err.setForeground(ACCENT_HI);
                        err.setText(accepted[0] ? n + " jest już Twoim znajomym."
                                : "Wysłano - " + n + " musi potwierdzić w launcherze.");
                        nickField.setText("");
                    }
                });
            } else if (cur.stream().anyMatch(x -> x.equalsIgnoreCase(n))) {
                err.setText("Ten gracz już jest na liście.");
            } else if (cur.size() >= 30) {
                err.setText("Maksymalnie 30 znajomych.");
            } else {
                cur.add(n);
                err.setText(" ");
                nickField.setText("");
                saveFriends(cur);
                bottomCards.show(bottom, "btn");
                refresh[0].run();
            }
        });
        nickField.addActionListener(e -> add.doClick());
        friendsRefresh = refresh[0];
        refresh[0].run();

        root.add(scroll, BorderLayout.CENTER);
        root.add(bottom, BorderLayout.SOUTH);
        return root;
    }

    /** Nagłówek sekcji na liście znajomych. */
    private static final class SectionLabel extends JComponent {
        private final String text;

        SectionLabel(String text) {
            this.text = text;
            setPreferredSize(new Dimension(306, 30));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
            setAlignmentX(Component.LEFT_ALIGNMENT);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            aa(g);
            g.setFont(tracked(mono(Font.PLAIN, 11), 0.14f));
            g.setColor(MUTED);
            g.drawString(text, 18, getHeight() - 8);
            g.dispose();
        }
    }

    private static JComponent noteRow(String text, Color color) {
        JLabel l = new JLabel("<html><body style='width:262px'>" + escape(text) + "</body></html>");
        l.setForeground(color);
        l.setFont(body(Font.PLAIN, 12));
        l.setBorder(new EmptyBorder(8, 16, 6, 16));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    /** Własny wiersz na górze listy znajomych: ten sam awatar z kropką co u reszty. */
    private final class SelfRow extends JComponent {
        SelfRow() {
            setPreferredSize(new Dimension(306, 58));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 58));
            setAlignmentX(Component.LEFT_ALIGNMENT);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            aa(g);
            int w = getWidth();
            int cy = getHeight() / 2;
            String state = selfState();
            drawAvatar(g, skin, 16, cy - 17, 34, state, new Color(0x0D1318));
            int tx = 66;
            g.setFont(body(Font.BOLD, 16));
            g.setColor(TEXT);
            g.drawString(fit(g, nick, w - tx - 14), tx, cy - 3);
            g.setFont(body(Font.PLAIN, 12));
            g.setColor(MUTED);
            String desc = "game".equals(state) ? "Ty · grasz teraz" : "online".equals(state) ? "Ty · online" : "Ty · offline";
            g.drawString(desc, tx, cy + 15);
            g.dispose();
        }
    }

    /** Wiersz znajomego: głowa ze skina + kropka statusu, nick, opis i przycisk Dołącz. Usunięcie wymaga potwierdzenia. */
    private final class FriendRow extends Clickable {
        private final String nick;
        private final Runnable onRemove;
        private final Runnable onJoin;
        private final Rectangle joinRect = new Rectangle();
        private final Rectangle removeRect = new Rectangle();
        private final Rectangle yesRect = new Rectangle();
        private final Rectangle noRect = new Rectangle();
        private boolean confirming;

        FriendRow(String nick, Runnable onRemove, Runnable onJoin) {
            super(null);
            this.nick = nick;
            this.onRemove = onRemove;
            this.onJoin = onJoin;
            setCursor(Cursor.getDefaultCursor());
            setPreferredSize(new Dimension(306, 58));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 58));
            setAlignmentX(Component.LEFT_ALIGNMENT);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseReleased(MouseEvent e) {
                    Point p = e.getPoint();
                    if (confirming) {
                        boolean yes = yesRect.contains(p);
                        confirming = false;
                        repaint();
                        if (yes) {
                            FriendRow.this.onRemove.run();
                        }
                    } else if (javax.swing.SwingUtilities.isRightMouseButton(e) || removeRect.contains(p)) {
                        confirming = true;
                        repaint();
                    } else if (joinRect.contains(p)) {
                        FriendRow.this.onJoin.run();
                    }
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            aa(g);
            String[] st = presenceOf(nick);
            boolean off = "offline".equals(st[0]);
            boolean game = "game".equals(st[0]) && !confirming;
            int w = getWidth();
            int cy = getHeight() / 2;
            if (hover || confirming) {
                g.setColor(alpha(Color.WHITE, 7));
                g.fill(new RoundRectangle2D.Double(6, 2, w - 12, getHeight() - 4, 14, 14));
            }
            // głowa + kropka statusu
            BufferedImage sk = friendSkins.get(nick);
            drawAvatar(g, sk == NO_SKIN ? null : sk, 16, cy - 17, 34, st[0], new Color(0x0D1318));
            // nick i opis
            int tx = 66;
            int right = confirming ? 126 : game ? 84 : 14;
            g.setFont(body(Font.BOLD, 16));
            g.setColor(off ? new Color(0x7B8794) : TEXT);
            g.drawString(fit(g, nick, w - tx - right), tx, cy - 3);
            g.setFont(body(Font.PLAIN, 12));
            g.setColor(confirming ? new Color(0xFF8A7A) : off ? new Color(0x5E6A76) : MUTED);
            g.drawString(fit(g, confirming ? "Usunąć?" : st[1], w - tx - right), tx, cy + 15);
            // przycisk Dołącz
            joinRect.setBounds(0, 0, 0, 0);
            if (game) {
                int bw = 62;
                int bh = 28;
                joinRect.setBounds(w - 14 - bw, cy - bh / 2, bw, bh);
                g.setPaint(new GradientPaint(0, joinRect.y, ACCENT_HI, 0, joinRect.y + bh, ACCENT));
                g.fill(new RoundRectangle2D.Double(joinRect.x, joinRect.y, bw, bh, 14, 14));
                g.setFont(body(Font.BOLD, 13));
                g.setColor(ON_ACCENT);
                String t = "Dołącz";
                g.drawString(t, joinRect.x + (bw - g.getFontMetrics().stringWidth(t)) / 2, mid(g, cy));
            }
            // potwierdzenie usunięcia: Tak / Nie
            yesRect.setBounds(0, 0, 0, 0);
            noRect.setBounds(0, 0, 0, 0);
            removeRect.setBounds(0, 0, 0, 0);
            if (confirming) {
                noRect.setBounds(w - 14 - 48, cy - 14, 48, 28);
                yesRect.setBounds(noRect.x - 8 - 48, cy - 14, 48, 28);
                g.setColor(new Color(0xC9473A));
                g.fill(new RoundRectangle2D.Double(yesRect.x, yesRect.y, 48, 28, 14, 14));
                g.setColor(alpha(Color.WHITE, 22));
                g.fill(new RoundRectangle2D.Double(noRect.x, noRect.y, 48, 28, 14, 14));
                g.setFont(body(Font.BOLD, 13));
                g.setColor(Color.WHITE);
                g.drawString("Tak", yesRect.x + (48 - g.getFontMetrics().stringWidth("Tak")) / 2, mid(g, cy));
                g.setColor(TEXT);
                g.drawString("Nie", noRect.x + (48 - g.getFontMetrics().stringWidth("Nie")) / 2, mid(g, cy));
            } else if (hover && !game) {
                // usuwanie: krzyżyk po najechaniu (dla wierszy bez przycisku Dołącz) / prawy przycisk myszy
                removeRect.setBounds(w - 40, cy - 14, 28, 28);
                g.setColor(MUTED);
                g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int cx = w - 26;
                g.drawLine(cx - 4, cy - 4, cx + 4, cy + 4);
                g.drawLine(cx - 4, cy + 4, cx + 4, cy - 4);
                setToolTipText("Usuń znajomego (lub prawy przycisk myszy)");
            }
            g.dispose();
        }
    }

    /** Przychodzące zaproszenie: Akceptuj / Odrzuć. */
    private final class IncomingRow extends Clickable {
        private final String nick;
        private final Rectangle acceptRect = new Rectangle();
        private final Rectangle declineRect = new Rectangle();

        IncomingRow(String nick, Runnable onAccept, Runnable onDecline) {
            super(null);
            this.nick = nick;
            setCursor(Cursor.getDefaultCursor());
            setPreferredSize(new Dimension(306, 58));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 58));
            setAlignmentX(Component.LEFT_ALIGNMENT);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseReleased(MouseEvent e) {
                    if (acceptRect.contains(e.getPoint())) {
                        onAccept.run();
                    } else if (declineRect.contains(e.getPoint())) {
                        onDecline.run();
                    }
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            aa(g);
            int w = getWidth();
            int cy = getHeight() / 2;
            if (hover) {
                g.setColor(alpha(Color.WHITE, 7));
                g.fill(new RoundRectangle2D.Double(6, 2, w - 12, getHeight() - 4, 14, 14));
            }
            BufferedImage sk = friendSkins.get(nick);
            drawAvatar(g, sk == NO_SKIN ? null : sk, 16, cy - 17, 34, null, new Color(0x0D1318));
            int tx = 66;
            int right = 14 + 28 + 6 + 74 + 6;
            g.setFont(body(Font.BOLD, 16));
            g.setColor(TEXT);
            g.drawString(fit(g, nick, w - tx - right), tx, cy - 3);
            g.setFont(body(Font.PLAIN, 12));
            g.setColor(ACCENT_HI);
            g.drawString("Zaproszenie", tx, cy + 15);

            declineRect.setBounds(w - 14 - 28, cy - 14, 28, 28);
            acceptRect.setBounds(declineRect.x - 6 - 74, cy - 14, 74, 28);
            g.setPaint(new GradientPaint(0, acceptRect.y, ACCENT_HI, 0, acceptRect.y + 28, ACCENT));
            g.fill(new RoundRectangle2D.Double(acceptRect.x, acceptRect.y, 74, 28, 14, 14));
            g.setFont(body(Font.BOLD, 13));
            g.setColor(ON_ACCENT);
            g.drawString("Akceptuj", acceptRect.x + (74 - g.getFontMetrics().stringWidth("Akceptuj")) / 2, mid(g, cy));
            g.setColor(alpha(Color.WHITE, 16));
            g.fill(new Ellipse2D.Double(declineRect.x, declineRect.y, 28, 28));
            g.setColor(MUTED);
            g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int cx = declineRect.x + 14;
            g.drawLine(cx - 4, cy - 4, cx + 4, cy + 4);
            g.drawLine(cx - 4, cy + 4, cx + 4, cy - 4);
            setToolTipText("Odrzuć");
            g.dispose();
        }
    }

    /** Wysłane zaproszenie, na które druga osoba jeszcze nie odpowiedziała. */
    private final class OutgoingRow extends Clickable {
        private final String nick;
        private final Rectangle cancelRect = new Rectangle();

        OutgoingRow(String nick, Runnable onCancel) {
            super(null);
            this.nick = nick;
            setCursor(Cursor.getDefaultCursor());
            setPreferredSize(new Dimension(306, 58));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 58));
            setAlignmentX(Component.LEFT_ALIGNMENT);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseReleased(MouseEvent e) {
                    if (cancelRect.contains(e.getPoint())) {
                        onCancel.run();
                    }
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            aa(g);
            int w = getWidth();
            int cy = getHeight() / 2;
            if (hover) {
                g.setColor(alpha(Color.WHITE, 7));
                g.fill(new RoundRectangle2D.Double(6, 2, w - 12, getHeight() - 4, 14, 14));
            }
            BufferedImage sk = friendSkins.get(nick);
            drawAvatar(g, sk == NO_SKIN ? null : sk, 16, cy - 17, 34, "offline", new Color(0x0D1318));
            int tx = 66;
            int right = 14 + 62 + 8;
            g.setFont(body(Font.BOLD, 16));
            g.setColor(new Color(0x9AA6B2));
            g.drawString(fit(g, nick, w - tx - right), tx, cy - 3);
            g.setFont(body(Font.PLAIN, 12));
            g.setColor(MUTED);
            g.drawString(fit(g, "Czeka na potwierdzenie", w - tx - right), tx, cy + 15);
            cancelRect.setBounds(w - 14 - 62, cy - 14, 62, 28);
            g.setColor(alpha(Color.WHITE, 16));
            g.fill(new RoundRectangle2D.Double(cancelRect.x, cancelRect.y, 62, 28, 14, 14));
            g.setFont(body(Font.BOLD, 13));
            g.setColor(TEXT);
            g.drawString("Anuluj", cancelRect.x + (62 - g.getFontMetrics().stringWidth("Anuluj")) / 2, mid(g, cy));
            g.dispose();
        }
    }

    private static void part(Graphics2D g, BufferedImage s, int sx, int sy, int sw, int sh,
                             double dx, double dy, double dw, double dh, boolean mirror) {
        if (sx + sw > s.getWidth() || sy + sh > s.getHeight()) {
            return;
        }
        int x1 = (int) Math.round(dx);
        int y1 = (int) Math.round(dy);
        int x2 = (int) Math.round(dx + dw);
        int y2 = (int) Math.round(dy + dh);
        if (mirror) {
            g.drawImage(s, x2, y1, x1, y2, sx, sy, sx + sw, sy + sh, null);
        } else {
            g.drawImage(s, x1, y1, x2, y2, sx, sy, sx + sw, sy + sh, null);
        }
    }

    static void drawHead(Graphics2D g0, BufferedImage s, int x, int y, int size) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        if (s == null) {
            g.setColor(new Color(0xB98B63));
            g.fillRect(x, y, size, size);
            g.setColor(new Color(0x3B2A1E));
            g.fillRect(x, y, size, size / 4);
            g.setColor(new Color(0x2B2B4A));
            g.fillRect(x + size / 5, y + size * 2 / 5, size / 5, size / 5);
            g.fillRect(x + size * 3 / 5, y + size * 2 / 5, size / 5, size / 5);
        } else {
            part(g, s, 8, 8, 8, 8, x, y, size, size, false);
            part(g, s, 40, 8, 8, 8, x - size * 0.04, y - size * 0.04, size * 1.08, size * 1.08, false);
        }
        g.dispose();
    }

    /** Kolor kropki statusu: game = zielony, online = niebieski, offline = szary. */
    static Color statusColor(String state) {
        if ("game".equals(state)) {
            return ACCENT;
        }
        if ("offline".equals(state)) {
            return new Color(0x4A5560);
        }
        return new Color(0x3B9BFF);
    }

    /**
     * Wspólny awatar gracza: głowa ze skina w zaokrąglonym kwadracie + kropka statusu w prawym dolnym rogu.
     * state: game | online | offline (null = bez kropki), ring = kolor tła pod awatarem (obwódka kropki).
     */
    static void drawAvatar(Graphics2D g0, BufferedImage s, int x, int y, int size, String state, Color ring) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        double arc = size * 0.26;
        Graphics2D gh = (Graphics2D) g.create();
        gh.clip(new RoundRectangle2D.Double(x, y, size, size, arc, arc));
        drawHead(gh, s, x, y, size);
        if ("offline".equals(state)) {
            gh.setColor(alpha(new Color(0x0D1318), 150));
            gh.fillRect(x, y, size, size);
        }
        gh.dispose();
        if (state != null) {
            double outer = Math.max(10, Math.round(size * 0.47));
            double rw = Math.max(2, Math.round(outer * 0.125));
            double cx = x + size * 0.87;
            double cy = y + size * 0.87;
            g.setColor(ring);
            g.fill(new Ellipse2D.Double(cx - outer / 2, cy - outer / 2, outer, outer));
            double in = outer - 2 * rw;
            g.setColor(statusColor(state));
            g.fill(new Ellipse2D.Double(cx - in / 2, cy - in / 2, in, in));
        }
        g.dispose();
    }

    static void drawBlock(Graphics2D g, int x, int y, int size, long seed) {
        Random r = new Random(seed);
        int cell = size / 4;
        for (int cy = 0; cy < 4; cy++) {
            for (int cx = 0; cx < 4; cx++) {
                int c;
                if (cy == 0) {
                    c = r.nextBoolean() ? 0x4C9A2E : 0x5CAE38;
                } else {
                    c = r.nextBoolean() ? 0x7A5634 : 0x6B4A2C;
                }
                g.setColor(new Color(c));
                g.fillRect(x + cx * cell, y + cy * cell, cell, cell);
            }
        }
    }

    // ------------------------------------------------------------------ profile (zainstalowane wersje)

    private static String typeLabel(String type) {
        if ("release".equals(type)) {
            return "Wydanie";
        }
        if ("snapshot".equals(type)) {
            return "Snapshot";
        }
        return type == null || type.isEmpty() ? "Wersja" : type;
    }

    private final class ProfileCard extends Clickable {
        private final Profile p;
        private final boolean editable;
        private final Rectangle editRect = new Rectangle();
        private boolean hoverEdit;
        private Point pressed;

        ProfileCard(Profile p, boolean editable) {
            super(null);
            this.p = p;
            this.editable = editable;
            this.onClick = () -> {
                if (this.editable && pressed != null && editRect.contains(pressed)) {
                    openProfileEditor(this.p);
                } else {
                    selectProfile(this.p);
                    showPage("home");
                }
            };
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    pressed = e.getPoint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    if (hoverEdit) {
                        hoverEdit = false;
                        repaint();
                    }
                }
            });
            addMouseMotionListener(new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    boolean h = ProfileCard.this.editable && editRect.contains(e.getPoint());
                    if (h != hoverEdit) {
                        hoverEdit = h;
                        repaint();
                    }
                }
            });
            setPreferredSize(new Dimension(220, 76));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            aa(g);
            boolean sel = p.id.equals(activeProfileId);
            card(g, 0, 0, getWidth(), getHeight(), 18, sel || hover);
            if (sel) {
                g.setColor(ACCENT);
                g.setStroke(new BasicStroke(1.6f));
                g.draw(cut(0.8, 0.8, getWidth() - 1.6, getHeight() - 1.6, 18));
                g.setFont(body(Font.BOLD, 10));
                g.setColor(ACCENT_HI);
                String tag = "AKTYWNY";
                g.drawString(tag, getWidth() - 14 - g.getFontMetrics().stringWidth(tag), getHeight() - 12);
            }
            String ver = p.version == null || p.version.isBlank() ? "-" : p.version;
            drawBlock(g, 14, 16, 44, ver.hashCode());

            int nameMax = getWidth() - 72 - (editable ? 46 : 14);
            g.setColor(TEXT);
            g.setFont(head(Font.BOLD, 20));
            g.drawString(fit(g, p.name, nameMax), 72, 38);

            VersionEntry v = p.version == null || p.version.isBlank() ? null : resolveVersion(p.version);
            String sub = ver + (v != null && v.type() != null ? "  \u00b7  " + typeLabel(v.type()) : "");
            g.setColor(MUTED);
            g.setFont(body(Font.PLAIN, 13));
            g.drawString(fit(g, sub, getWidth() - 72 - (sel ? 82 : 14)), 72, 60);

            if (editable) {
                editRect.setBounds(getWidth() - 42, 8, 32, 30);
                if (hoverEdit) {
                    g.setColor(alpha(ACCENT, 60));
                    g.fill(new RoundRectangle2D.Double(editRect.x, editRect.y, editRect.width, editRect.height, 10, 10));
                }
                icon(g, 7, editRect.x + 7, editRect.y + 6, 18, hoverEdit ? TEXT : MUTED);
            }
            g.dispose();
        }
    }

    // ------------------------------------------------------------------ profile (dane)

    private Profile activeProfile() {
        for (Profile p : profiles) {
            if (p.id.equals(activeProfileId)) {
                return p;
            }
        }
        return null;
    }

    private VersionEntry resolveVersion(String id) {
        VersionEntry v = versionIndex.get(id);
        return v != null ? v : new VersionEntry(id, null, null);
    }

    private String newProfileId() {
        Random rnd = new Random();
        while (true) {
            String id = Long.toString(System.currentTimeMillis(), 36) + Integer.toString(rnd.nextInt(1296), 36);
            boolean taken = false;
            for (Profile p : profiles) {
                if (p.id.equals(id)) {
                    taken = true;
                    break;
                }
            }
            if (!taken) {
                return id;
            }
        }
    }

    private void loadProfiles() {
        profiles.clear();
        for (String raw : config.getProperty("profiles", "").split(",")) {
            String id = raw.trim();
            if (id.isEmpty()) {
                continue;
            }
            String name = config.getProperty("profile." + id + ".name", "").trim();
            if (name.isEmpty()) {
                continue;
            }
            Profile p = new Profile(id, name, config.getProperty("profile." + id + ".version", ""));
            try {
                p.used = Long.parseLong(config.getProperty("profile." + id + ".used", "0"));
            } catch (NumberFormatException ignored) {
                p.used = 0;
            }
            profiles.add(p);
        }
        if (profiles.isEmpty()) {
            // pierwsze uruchomienie z profilami: przenosimy dotychczas wybraną wersję do profilu "Domyślny"
            profiles.add(new Profile(newProfileId(), "Domyślny", config.getProperty("version", "")));
        }
        activeProfileId = config.getProperty("profile", "");
        if (activeProfile() == null) {
            activeProfileId = profiles.get(0).id;
        }
        saveProfiles();
    }

    private void saveProfiles() {
        for (String k : new ArrayList<>(config.stringPropertyNames())) {
            if (k.startsWith("profile.")) {
                config.remove(k);
            }
        }
        StringBuilder ids = new StringBuilder();
        for (Profile p : profiles) {
            if (ids.length() > 0) {
                ids.append(',');
            }
            ids.append(p.id);
            config.setProperty("profile." + p.id + ".name", p.name);
            config.setProperty("profile." + p.id + ".version", p.version == null ? "" : p.version);
            config.setProperty("profile." + p.id + ".used", String.valueOf(p.used));
        }
        config.setProperty("profiles", ids.toString());
        config.setProperty("profile", activeProfileId == null ? "" : activeProfileId);
        saveConfig();
    }

    /** Przelicza wybraną wersję z aktywnego profilu i odświeża wszystkie widoki. */
    private void applyActive() {
        Profile p = activeProfile();
        selected = p == null || p.version == null || p.version.isBlank() ? null : resolveVersion(p.version);
        refreshRecent();
        refreshProfiles();
        if (hero != null) {
            hero.repaint();
        }
    }

    private void selectProfile(Profile p) {
        activeProfileId = p.id;
        saveProfiles();
        applyActive();
    }

    private void setProfileVersion(Profile p, VersionEntry v) {
        p.version = v.id();
        saveProfiles();
        applyActive();
    }

    private void deleteProfile(Profile p) {
        if (profiles.size() <= 1) {
            return;
        }
        profiles.remove(p);
        if (p.id.equals(activeProfileId)) {
            activeProfileId = profiles.get(0).id;
        }
        saveProfiles();
        applyActive();
    }

    private List<Profile> recentProfiles() {
        List<Profile> list = new ArrayList<>(profiles);
        list.sort((a, b) -> Long.compare(b.used, a.used));
        Profile ap = activeProfile();
        if (ap != null) {
            list.remove(ap);
            list.add(0, ap);
        }
        return list;
    }

    // ------------------------------------------------------------------ profile (widoki)

    private void refreshRecent() {
        if (recentCards == null) {
            return;
        }
        recentCards.removeAll();
        List<Profile> list = recentProfiles();
        for (int i = 0; i < Math.min(3, list.size()); i++) {
            recentCards.add(new RecentRow(list.get(i)));
        }
        recentCards.revalidate();
        recentCards.repaint();
    }

    private JComponent buildProfilesPage() {
        JPanel p = new JPanel(new BorderLayout(0, 18));
        p.setBackground(BG);
        p.setBorder(new EmptyBorder(26, 30, 26, 30));
        p.add(pageTitle("Wersje",
                "Każdy profil ma własną wersję gry. Kliknij kartę, żeby go wybrać, a ikonę suwaków, żeby edytować."),
                BorderLayout.NORTH);

        profilesGrid = new JPanel(new GridLayout(0, 3, 16, 16));
        profilesGrid.setOpaque(false);
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.add(profilesGrid, BorderLayout.NORTH);
        JScrollPane scroll = darkScroll(wrap);
        scroll.setBorder(new EmptyBorder(0, 0, 0, 0));
        scroll.getViewport().setOpaque(false);

        FlatButton add = new FlatButton("+ Nowy profil", true);
        add.addActionListener(e -> openProfileEditor(null));
        FlatButton pick = new FlatButton("Zmień wersję aktywnego profilu", false);
        pick.addActionListener(e -> openVersionPicker());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        south.setOpaque(false);
        south.add(add);
        south.add(javax.swing.Box.createHorizontalStrut(12));
        south.add(pick);

        p.add(scroll, BorderLayout.CENTER);
        p.add(south, BorderLayout.SOUTH);
        return p;
    }

    private void refreshProfiles() {
        if (profilesGrid == null) {
            return;
        }
        profilesGrid.removeAll();
        for (Profile p : profiles) {
            profilesGrid.add(new ProfileCard(p, true));
        }
        // puste komórki, żeby karty nie rozciągały się na całą szerokość przy 1-2 profilach
        for (int i = profiles.size() % 3; i != 0 && i < 3; i++) {
            profilesGrid.add(new JLabel());
        }
        profilesGrid.revalidate();
        profilesGrid.repaint();
    }

    /** Okno tworzenia (existing == null) albo edycji profilu: nazwa i wersja gry. */
    private void openProfileEditor(Profile existing) {
        final String[] ver = {existing != null ? existing.version
                : (selected != null ? selected.id() : null)};
        JPanel body = new JPanel(new BorderLayout(0, 10));
        JDialog d = makeDialog(existing == null ? "Nowy profil" : "Edytuj profil", body, 440, 330);

        JLabel nameHint = new JLabel("Nazwa profilu");
        nameHint.setForeground(MUTED);
        nameHint.setFont(body(Font.PLAIN, 12));
        JTextField name = field(existing != null ? existing.name : "Nowy profil");
        name.setMaximumSize(new Dimension(Integer.MAX_VALUE, name.getPreferredSize().height));

        JLabel verHint = new JLabel("Wersja gry");
        verHint.setForeground(MUTED);
        verHint.setFont(body(Font.PLAIN, 12));

        Clickable verBox = new Clickable(null) {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                aa(g);
                card(g, 0, 0, getWidth(), getHeight(), 14, hover);
                if (ver[0] == null) {
                    g.setColor(MUTED);
                    g.setFont(body(Font.PLAIN, 15));
                    g.drawString("Kliknij, aby wybrać wersję", 16, getHeight() / 2 + 5);
                } else {
                    VersionEntry v = resolveVersion(ver[0]);
                    drawBlock(g, 12, 12, 40, ver[0].hashCode());
                    g.setFont(head(Font.BOLD, 20));
                    g.setColor(TEXT);
                    g.drawString(ver[0], 62, 32);
                    g.setFont(body(Font.PLAIN, 13));
                    g.setColor(MUTED);
                    g.drawString(typeLabel(v.type()), 62, 50);
                }
                g.setColor(MUTED);
                g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int cx = getWidth() - 24;
                int cy = getHeight() / 2;
                g.drawLine(cx - 5, cy - 3, cx, cy + 2);
                g.drawLine(cx, cy + 2, cx + 5, cy - 3);
                g.dispose();
            }
        };
        verBox.setPreferredSize(new Dimension(10, 64));
        verBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));
        verBox.onClick = () -> openVersionPicker(ver[0] == null ? null : resolveVersion(ver[0]), v -> {
            ver[0] = v.id();
            verBox.repaint();
        });

        JLabel err = new JLabel(" ");
        err.setForeground(new Color(0xFF6B5A));
        err.setFont(body(Font.PLAIN, 12));

        JPanel mid = new JPanel();
        mid.setOpaque(false);
        mid.setLayout(new BoxLayout(mid, BoxLayout.Y_AXIS));
        for (JComponent c : new JComponent[]{nameHint, name, verHint, verBox, err}) {
            c.setAlignmentX(Component.LEFT_ALIGNMENT);
        }
        mid.add(nameHint);
        mid.add(javax.swing.Box.createVerticalStrut(4));
        mid.add(name);
        mid.add(javax.swing.Box.createVerticalStrut(14));
        mid.add(verHint);
        mid.add(javax.swing.Box.createVerticalStrut(4));
        mid.add(verBox);
        mid.add(javax.swing.Box.createVerticalStrut(8));
        mid.add(err);

        FlatButton save = new FlatButton("Zapisz", true);
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.setOpaque(false);
        if (existing != null) {
            FlatButton dup = new FlatButton("Duplikuj", false);
            dup.addActionListener(e -> {
                String base = existing.name.length() > 16 ? existing.name.substring(0, 16).trim() : existing.name;
                Profile copy = new Profile(newProfileId(), base + " (kopia)", existing.version);
                profiles.add(profiles.indexOf(existing) + 1, copy);
                saveProfiles();
                applyActive();
                d.dispose();
            });
            left.add(dup);
            if (profiles.size() > 1) {
                FlatButton del = new FlatButton("Usuń", false);
                final boolean[] armed = {false};
                del.addActionListener(e -> {
                    if (!armed[0]) {
                        armed[0] = true;
                        del.setText("Na pewno?");
                        left.revalidate();
                        left.repaint();
                        return;
                    }
                    deleteProfile(existing);
                    d.dispose();
                });
                left.add(javax.swing.Box.createHorizontalStrut(8));
                left.add(del);
            }
        }
        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        south.add(left, BorderLayout.WEST);
        south.add(save, BorderLayout.EAST);

        body.add(mid, BorderLayout.NORTH);
        body.add(south, BorderLayout.SOUTH);

        Runnable submit = () -> {
            String n = name.getText().trim();
            if (n.isEmpty()) {
                err.setText("Podaj nazwę profilu");
                return;
            }
            if (n.length() > 24) {
                err.setText("Nazwa może mieć maksymalnie 24 znaki");
                return;
            }
            if (ver[0] == null) {
                err.setText("Wybierz wersję gry");
                return;
            }
            if (existing != null) {
                existing.name = n;
                existing.version = ver[0];
            } else {
                Profile created = new Profile(newProfileId(), n, ver[0]);
                profiles.add(created);
                activeProfileId = created.id;
            }
            saveProfiles();
            applyActive();
            d.dispose();
        };
        save.addActionListener(e -> submit.run());
        name.addActionListener(e -> submit.run());
        SwingUtilities.invokeLater(() -> {
            name.requestFocusInWindow();
            name.selectAll();
        });
        d.setVisible(true);
    }


    // ------------------------------------------------------------------ mody

    private JComponent buildModsPage() {
        JPanel p = new JPanel(new BorderLayout(0, 18));
        p.setBackground(BG);
        p.setBorder(new EmptyBorder(26, 30, 26, 30));
        p.add(pageTitle("Mody", "Pliki .jar z folderu mods w katalogu gry."), BorderLayout.NORTH);

        modsList = new JPanel();
        modsList.setOpaque(false);
        modsList.setLayout(new BoxLayout(modsList, BoxLayout.Y_AXIS));
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.add(modsList, BorderLayout.NORTH);
        JScrollPane scroll = darkScroll(wrap);
        scroll.setBorder(new EmptyBorder(0, 0, 0, 0));
        scroll.getViewport().setOpaque(false);
        p.add(scroll, BorderLayout.CENTER);

        modsInfo = new JLabel(" ");
        modsInfo.setForeground(MUTED);
        modsInfo.setFont(body(Font.PLAIN, 13));
        FlatButton open = new FlatButton("Otwórz folder mods", true);
        open.addActionListener(e -> openFolder(installer.gameDir().resolve("mods")));
        JPanel south = new JPanel(new BorderLayout(14, 0));
        south.setOpaque(false);
        south.add(open, BorderLayout.WEST);
        south.add(modsInfo, BorderLayout.CENTER);
        p.add(south, BorderLayout.SOUTH);
        return p;
    }

    private void refreshMods() {
        if (modsList == null) {
            return;
        }
        modsList.removeAll();
        List<String> names = new ArrayList<>();
        Path dir = installer.gameDir().resolve("mods");
        if (Files.isDirectory(dir)) {
            try (Stream<Path> s = Files.list(dir)) {
                s.filter(f -> f.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                        .forEach(f -> names.add(f.getFileName().toString()));
            } catch (IOException ignored) {
                // pusty folder
            }
        }
        java.util.Collections.sort(names);
        for (String n : names) {
            ModRow row = new ModRow(n, dir.resolve(n));
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            modsList.add(row);
            modsList.add(javax.swing.Box.createVerticalStrut(8));
        }
        if (names.isEmpty()) {
            JComponent empty = new JComponent() {
                @Override
                protected void paintComponent(Graphics g0) {
                    Graphics2D g = (Graphics2D) g0.create();
                    aa(g);
                    card(g, 0, 0, getWidth(), getHeight(), 20, false);
                    icon(g, 2, getWidth() / 2 - 17, 38, 34, alpha(ACCENT_HI, 200));
                    g.setFont(head(Font.BOLD, 20));
                    g.setColor(TEXT);
                    String t = "Brak modów";
                    g.drawString(t, (getWidth() - g.getFontMetrics().stringWidth(t)) / 2, 112);
                    g.setFont(body(Font.PLAIN, 14));
                    g.setColor(MUTED);
                    String u = "Wrzuć pliki .jar do folderu mods - pojawią się tutaj.";
                    g.drawString(u, (getWidth() - g.getFontMetrics().stringWidth(u)) / 2, 138);
                    g.dispose();
                }
            };
            empty.setPreferredSize(new Dimension(10, 170));
            empty.setMaximumSize(new Dimension(Integer.MAX_VALUE, 170));
            empty.setAlignmentX(Component.LEFT_ALIGNMENT);
            modsList.add(empty);
        }
        modsInfo.setText("Launcher uruchamia na razie czysty Minecraft. Obsługa Fabric/Forge - wkrótce.");
        modsList.revalidate();
        modsList.repaint();
    }

    /** Wiersz moda: ikona, nazwa pliku, rozmiar. */
    private static final class ModRow extends JComponent {
        private final String name;
        private final String size;

        ModRow(String name, Path file) {
            this.name = name;
            String sz = "";
            try {
                long b = Files.size(file);
                sz = b >= 1_048_576 ? String.format(Locale.ROOT, "%.1f MB", b / 1_048_576.0)
                        : Math.max(1, b / 1024) + " KB";
            } catch (IOException ignored) {
                // bez rozmiaru
            }
            this.size = sz;
            setPreferredSize(new Dimension(10, 54));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 54));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            aa(g);
            card(g, 0, 0, getWidth(), getHeight(), 14, false);
            g.setColor(alpha(ACCENT, 38));
            g.fill(new RoundRectangle2D.Double(10, 9, 36, 36, 10, 10));
            icon(g, 2, 17, 16, 22, ACCENT_HI);
            g.setColor(TEXT);
            g.setFont(body(Font.PLAIN, 15));
            g.drawString(name, 60, 32);
            g.setColor(MUTED);
            g.setFont(body(Font.PLAIN, 13));
            g.drawString(size, getWidth() - 18 - g.getFontMetrics().stringWidth(size), 32);
            g.dispose();
        }
    }

    private void refreshStats() {
        int count = 0;
        try {
            Path dir = installer == null ? baseDir.resolve("game").resolve("mods") : installer.gameDir().resolve("mods");
            if (Files.isDirectory(dir)) {
                try (Stream<Path> s = Files.list(dir)) {
                    count = (int) s.filter(f -> f.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")).count();
                }
            }
        } catch (IOException ignored) {
            // zostaje 0
        }
        modsCount = count;
        if (hero != null) {
            hero.repaint();
        }
    }

    private JComponent pageTitle(String title, String sub) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        JLabel t = new JLabel(title);
        t.setForeground(TEXT);
        t.setFont(head(Font.BOLD, 34));
        t.setAlignmentX(Component.LEFT_ALIGNMENT);
        JComponent bar = new JComponent() {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                g.setPaint(new GradientPaint(0, 0, ACCENT_HI, 120, 0, alpha(ACCENT, 0)));
                g.fillRect(0, 0, 120, 3);
                g.dispose();
            }
        };
        bar.setPreferredSize(new Dimension(120, 3));
        bar.setMaximumSize(new Dimension(120, 3));
        bar.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel s = new JLabel(sub);
        s.setForeground(MUTED);
        s.setFont(body(Font.PLAIN, 14));
        s.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(t);
        p.add(javax.swing.Box.createVerticalStrut(6));
        p.add(bar);
        p.add(javax.swing.Box.createVerticalStrut(8));
        p.add(s);
        return p;
    }

    // ------------------------------------------------------------------ konsola

    private JComponent buildConsolePage() {
        JPanel p = new JPanel(new BorderLayout(0, 16));
        p.setBackground(BG);
        p.setBorder(new EmptyBorder(26, 30, 26, 30));
        p.add(pageTitle("Konsola", "Log launchera i gry."), BorderLayout.NORTH);

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setBackground(PANEL);
        logArea.setForeground(MUTED);
        logArea.setCaretColor(TEXT);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        logArea.setBorder(new EmptyBorder(10, 12, 10, 12));
        p.add(darkScroll(logArea), BorderLayout.CENTER);
        return p;
    }

    private static JScrollPane darkScroll(JComponent c) {
        JScrollPane sp = new JScrollPane(c);
        sp.setBorder(BorderFactory.createLineBorder(BORDER));
        sp.getViewport().setBackground(c instanceof JTextArea ? PANEL : BG);
        sp.setOpaque(false);
        JScrollBar vb = sp.getVerticalScrollBar();
        vb.setUI(new DarkScrollBarUI());
        vb.setPreferredSize(new Dimension(10, 10));
        vb.setUnitIncrement(18);
        return sp;
    }

    private static final class DarkScrollBarUI extends javax.swing.plaf.basic.BasicScrollBarUI {
        @Override
        protected void configureScrollBarColors() {
            thumbColor = new Color(0x2B3A46);
            trackColor = BG;
        }

        @Override
        protected JButton createDecreaseButton(int orientation) {
            return zero();
        }

        @Override
        protected JButton createIncreaseButton(int orientation) {
            return zero();
        }

        private static JButton zero() {
            JButton b = new JButton();
            b.setPreferredSize(new Dimension(0, 0));
            b.setMinimumSize(new Dimension(0, 0));
            b.setMaximumSize(new Dimension(0, 0));
            return b;
        }

        @Override
        protected void paintTrack(Graphics g, JComponent c, Rectangle r) {
            g.setColor(BG);
            g.fillRect(r.x, r.y, r.width, r.height);
        }

        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
            Graphics2D g2 = (Graphics2D) g.create();
            aa(g2);
            g2.setColor(thumbColor);
            g2.fillRoundRect(r.x + 2, r.y + 2, r.width - 4, r.height - 4, 6, 6);
            g2.dispose();
        }
    }

    // ------------------------------------------------------------------ dialogi

    private JDialog makeDialog(String title, JPanel body, int w, int h) {
        JDialog d = new JDialog(frame, true);
        d.setUndecorated(true);
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(PANEL);
        root.setBorder(BorderFactory.createLineBorder(ACCENT_DIM));

        JPanel headBar = new JPanel(new BorderLayout());
        headBar.setBackground(new Color(0x080B0F));
        JLabel t = new JLabel(title);
        t.setForeground(TEXT);
        t.setFont(head(Font.BOLD, 19));
        t.setBorder(new EmptyBorder(0, 18, 0, 0));
        headBar.add(t, BorderLayout.CENTER);
        headBar.add(new WinButton(2, d::dispose), BorderLayout.EAST);
        headBar.setPreferredSize(new Dimension(10, 40));

        final int[] press = new int[2];
        MouseAdapter drag = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                press[0] = e.getX();
                press[1] = e.getY();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                d.setLocation(e.getXOnScreen() - press[0], e.getYOnScreen() - press[1]);
            }
        };
        headBar.addMouseListener(drag);
        headBar.addMouseMotionListener(drag);

        body.setOpaque(false);
        body.setBorder(new EmptyBorder(16, 18, 18, 18));
        root.add(headBar, BorderLayout.NORTH);
        root.add(body, BorderLayout.CENTER);
        d.setContentPane(root);
        d.setSize(w, h);
        d.setLocationRelativeTo(frame);
        return d;
    }

    private static JTextField field(String text) {
        JTextField f = new JTextField(text);
        f.setBackground(BG);
        f.setForeground(TEXT);
        f.setCaretColor(TEXT);
        f.setFont(body(Font.PLAIN, 15));
        f.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER), new EmptyBorder(8, 10, 8, 10)));
        return f;
    }

    private static boolean validNick(String n) {
        return n != null && n.matches("[A-Za-z0-9_]{3,16}");
    }

    // ------------------------------------------------------------------ konto (tylko Microsoft)

    private boolean isMicrosoft() {
        return msAccount != null;
    }

    /** Client ID: najpierw ukryte pole msClientId w config.properties (jeśli ustawione), potem wbudowane. */
    private String msClientId() {
        String custom = config.getProperty("msClientId", "").trim();
        return custom.isEmpty() ? MicrosoftAuth.bundledClientId() : custom;
    }

    private void loadAccount() {
        msAccount = MicrosoftAuth.loadAccount(baseDir);
        nick = msAccount != null ? msAccount.name() : "";
    }

    /** Status własnego awatara: game = gra uruchomiona, online = zalogowany i połączony, offline = reszta. */
    private String selfState() {
        if (!isMicrosoft() || !online) {
            return "offline";
        }
        return gameRunning ? "game" : "online";
    }

    private void repaintSelf() {
        if (sidebarUser != null) {
            sidebarUser.repaint();
        }
        if (titleAvatar != null) {
            titleAvatar.repaint();
        }
        if (hero != null) {
            hero.repaint();
        }
    }

    private void applyAccount() {
        nick = msAccount != null ? msAccount.name() : "";
        skin = null;
        loadSkinAsync();
        startSocial();
        repaintSelf();
        if (frame != null) {
            frame.repaint();
        }
    }

    private static JLabel htmlLabel(String text, Color color, int size, int style) {
        JLabel l = new JLabel("<html><body style='width:400px'>" + escape(text) + "</body></html>");
        l.setForeground(color);
        l.setFont(body(style, size));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private FlatButton wideButton(String text, boolean primary, Runnable action) {
        FlatButton b = new FlatButton(text, primary);
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, b.getPreferredSize().height));
        b.addActionListener(e -> action.run());
        return b;
    }

    /** Okno konta: logowanie przez Microsoft (przeglądarka, zapasowo kod). */
    private void openAccountDialog() {
        openAccountDialog(null);
    }

    /** afterLogin: co zrobić po udanym zalogowaniu (np. dokończyć kliknięcie GRAJ). */
    private void openAccountDialog(Runnable afterLogin) {
        if (busy) {
            return;
        }
        CardLayout cl = new CardLayout();
        JPanel body = new JPanel(cl);
        JDialog d = makeDialog("Konto", body, 460, 400);

        final boolean[] closed = {false};
        final int[] gen = {0};
        d.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                closed[0] = true;
            }
        });

        JPanel account = new JPanel();
        account.setOpaque(false);
        account.setLayout(new BoxLayout(account, BoxLayout.Y_AXIS));

        // ---- widok logowania (kod do wpisania)
        JPanel login = new JPanel();
        login.setOpaque(false);
        login.setLayout(new BoxLayout(login, BoxLayout.Y_AXIS));
        JLabel loginTitle = new JLabel("Logowanie kontem Microsoft");
        loginTitle.setForeground(TEXT);
        loginTitle.setFont(head(Font.BOLD, 20));
        loginTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel codeLabel = new JLabel("...");
        codeLabel.setForeground(ACCENT_HI);
        codeLabel.setFont(head(Font.BOLD, 44));
        codeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel uriLabel = new JLabel(" ");
        uriLabel.setForeground(MUTED);
        uriLabel.setFont(body(Font.PLAIN, 13));
        uriLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel status = new JLabel(" ");
        status.setForeground(MUTED);
        status.setFont(body(Font.PLAIN, 13));
        status.setAlignmentX(Component.LEFT_ALIGNMENT);
        final String[] currentCode = {null};
        final String[] currentUri = {null};
        FlatButton openPage = wideButton("Otwórz stronę logowania", true, () -> {
            if (currentUri[0] != null) {
                openUrl(currentUri[0]);
            }
        });
        final JLabel intro = htmlLabel("Otworzyłem przeglądarkę.", MUTED, 13, Font.PLAIN);
        final Runnable[] viaCodeAction = {null};
        FlatButton viaCode = wideButton("Nie działa? Zaloguj kodem", false, () -> {
            if (viaCodeAction[0] != null) {
                viaCodeAction[0].run();
            }
        });
        FlatButton copyCode = wideButton("Kopiuj kod", false, () -> {
            if (currentCode[0] != null) {
                java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                        .setContents(new java.awt.datatransfer.StringSelection(currentCode[0]), null);
            }
        });
        FlatButton back = new FlatButton("Anuluj", false);
        back.setAlignmentX(Component.LEFT_ALIGNMENT);
        back.setMaximumSize(new Dimension(Integer.MAX_VALUE, back.getPreferredSize().height));
        login.add(loginTitle);
        login.add(javax.swing.Box.createVerticalStrut(8));
        login.add(intro);
        login.add(javax.swing.Box.createVerticalStrut(10));
        login.add(codeLabel);
        login.add(uriLabel);
        login.add(javax.swing.Box.createVerticalStrut(12));
        login.add(openPage);
        login.add(javax.swing.Box.createVerticalStrut(8));
        login.add(copyCode);
        login.add(viaCode);
        login.add(javax.swing.Box.createVerticalStrut(12));
        login.add(status);
        login.add(javax.swing.Box.createVerticalGlue());
        login.add(back);

        body.add(account, "account");
        body.add(login, "login");

        JLabel err = new JLabel(" ");
        err.setForeground(new Color(0xFF6B5A));
        err.setFont(body(Font.PLAIN, 12));
        err.setAlignmentX(Component.LEFT_ALIGNMENT);

        final Runnable[] build = new Runnable[1];

        final java.util.function.Consumer<Boolean>[] flowRef = new java.util.function.Consumer[1];

        // device = false: logowanie w przeglądarce (wybór konta Microsoft, powrót na localhost)
        // device = true: zapasowo kod do wpisania na stronie Microsoftu
        java.util.function.Consumer<Boolean> flow = device -> {
            String cid = msClientId();
            if (cid == null || cid.isBlank()) {
                err.setText("<html><body style='width:400px'>Logowanie Microsoft nie jest jeszcze skonfigurowane przez właściciela BushLaunch. Spróbuj ponownie później.</body></html>");
                appendLog("Brak wbudowanego Client ID. Właściciel musi ustawić MS_CLIENT_ID podczas budowania launchera.");
                return;
            }
            final String clientId = cid;
            err.setText(" ");
            final int my = ++gen[0];
            codeLabel.setVisible(device);
            uriLabel.setVisible(device);
            copyCode.setVisible(device);
            viaCode.setVisible(!device);
            openPage.setText(device ? "Otwórz stronę logowania" : "Otwórz przeglądarkę ponownie");
            intro.setText("<html><body style='width:400px'>" + (device
                    ? "Wejdź na stronę poniżej i wpisz ten kod. Okno zamknie się samo po zalogowaniu."
                    : "Otworzyłem przeglądarkę - wybierz konto Microsoft i zatwierdź logowanie. "
                    + "Okno zamknie się samo po zalogowaniu.") + "</body></html>");
            codeLabel.setText("...");
            uriLabel.setText(" ");
            status.setForeground(MUTED);
            status.setText("Łączenie z Microsoft...");
            back.setText("Anuluj");
            currentCode[0] = null;
            currentUri[0] = null;
            cl.show(body, "login");
            java.util.function.BooleanSupplier cancelled = () -> closed[0] || gen[0] != my;
            java.util.function.Consumer<String> statusSink = s2 -> SwingUtilities.invokeLater(() -> {
                if (!cancelled.getAsBoolean()) {
                    status.setText(s2);
                }
            });
            Thread t = new Thread(() -> {
                try {
                    MicrosoftAuth.Login l;
                    if (device) {
                        MicrosoftAuth.DeviceCode dc = MicrosoftAuth.startDeviceLogin(clientId);
                        SwingUtilities.invokeLater(() -> {
                            if (cancelled.getAsBoolean()) {
                                return;
                            }
                            currentCode[0] = dc.userCode();
                            String loginUrl = dc.verificationUriComplete() != null
                                    && !dc.verificationUriComplete().isBlank()
                                    ? dc.verificationUriComplete() : dc.verificationUri();
                            currentUri[0] = loginUrl;
                            codeLabel.setText(dc.userCode());
                            uriLabel.setText(loginUrl);
                            status.setText("Otwieram stronę logowania Microsoft...");
                            openUrl(loginUrl);
                        });
                        l = MicrosoftAuth.pollLogin(clientId, dc, cancelled, statusSink);
                    } else {
                        MicrosoftAuth.BrowserLogin bl;
                        try {
                            bl = MicrosoftAuth.startBrowserLogin(clientId);
                        } catch (IOException ex) {
                            // nie da się uruchomić lokalnego serwera (np. zapora) - przechodzimy na kod
                            appendLog("Logowanie w przeglądarce niedostępne (" + ex.getMessage() + "), używam kodu.");
                            SwingUtilities.invokeLater(() -> flowRef[0].accept(true));
                            return;
                        }
                        try (bl) {
                            SwingUtilities.invokeLater(() -> {
                                if (cancelled.getAsBoolean()) {
                                    return;
                                }
                                currentUri[0] = bl.authUrl();
                                status.setText("Czekam na zalogowanie w przeglądarce...");
                                openUrl(bl.authUrl());
                            });
                            l = bl.await(cancelled, statusSink);
                        }
                    }
                    if (cancelled.getAsBoolean()) {
                        return;
                    }
                    MicrosoftAuth.Account acc = new MicrosoftAuth.Account(l.name(), l.uuid(), l.refreshToken());
                    MicrosoftAuth.saveAccount(baseDir, acc);
                    SwingUtilities.invokeLater(() -> {
                        msAccount = acc;
                        applyAccount();
                        appendLog("Zalogowano kontem Microsoft: " + acc.name());
                        d.dispose();
                        if (afterLogin != null) {
                            afterLogin.run();
                        }
                    });
                } catch (MicrosoftAuth.CancelledException ignored) {
                    // gracz anulował
                } catch (Exception e) {
                    String msg = e.getMessage() == null ? e.toString() : e.getMessage();
                    appendLog("Logowanie Microsoft nie powiodło się: " + msg);
                    SwingUtilities.invokeLater(() -> {
                        if (cancelled.getAsBoolean()) {
                            return;
                        }
                        status.setForeground(new Color(0xFF6B5A));
                        status.setText("<html><body style='width:400px'>" + escape(msg) + "</body></html>");
                        back.setText("Wróć");
                    });
                }
            }, "ms-login");
            t.setDaemon(true);
            t.start();
        };
        flowRef[0] = flow;
        viaCodeAction[0] = () -> flow.accept(true);
        Runnable startLogin = () -> flow.accept(false);
        back.addActionListener(e -> {
            gen[0]++;
            cl.show(body, "account");
        });

        build[0] = () -> {
            account.removeAll();
            account.setBorder(javax.swing.BorderFactory.createEmptyBorder(2, 0, 2, 0));

            JLabel heading = new JLabel("Konto");
            heading.setForeground(TEXT);
            heading.setFont(head(Font.BOLD, 23));
            heading.setAlignmentX(Component.LEFT_ALIGNMENT);
            JLabel subtitle = htmlLabel("Logowanie przez oficjalne konto Microsoft", MUTED, 13, Font.PLAIN);
            account.add(heading);
            account.add(javax.swing.Box.createVerticalStrut(3));
            account.add(subtitle);
            account.add(javax.swing.Box.createVerticalStrut(18));

            JPanel profile = new JPanel(new BorderLayout(12, 0));
            profile.setOpaque(true);
            profile.setBackground(new Color(0x101D1C));
            profile.setBorder(javax.swing.BorderFactory.createCompoundBorder(
                    javax.swing.BorderFactory.createLineBorder(ACCENT_DIM, 1),
                    javax.swing.BorderFactory.createEmptyBorder(13, 13, 12, 13)));
            JComponent avatar = new JComponent() {
                @Override protected void paintComponent(Graphics g0) {
                    Graphics2D g = (Graphics2D) g0.create(); aa(g);
                    drawAvatar(g, skin, 0, 0, 56, selfState(), new Color(0x10171E));
                    g.dispose();
                }
            };
            avatar.setPreferredSize(new Dimension(56, 56));
            profile.add(avatar, BorderLayout.WEST);
            JPanel identity = new JPanel();
            identity.setOpaque(false);
            identity.setLayout(new BoxLayout(identity, BoxLayout.Y_AXIS));
            JLabel username = new JLabel(nick == null || nick.isBlank() ? "Gość" : nick);
            username.setForeground(TEXT); username.setFont(body(Font.BOLD, 17));
            JLabel provider = new JLabel("▦  Konto Microsoft  ·  Minecraft Java");
            provider.setForeground(MUTED); provider.setFont(body(Font.PLAIN, 12));
            identity.add(username); identity.add(javax.swing.Box.createVerticalStrut(4)); identity.add(provider);
            profile.add(identity, BorderLayout.CENTER);
            JLabel active = new JLabel(isMicrosoft() ? "✓ Aktywne" : "Nie zalogowano");
            active.setOpaque(true);
            active.setBackground(isMicrosoft() ? ACCENT : PANEL_HI);
            active.setForeground(isMicrosoft() ? ON_ACCENT : MUTED);
            active.setFont(body(Font.BOLD, 11));
            active.setBorder(javax.swing.BorderFactory.createEmptyBorder(6, 10, 6, 10));
            profile.add(active, BorderLayout.EAST);
            profile.setAlignmentX(Component.LEFT_ALIGNMENT);
            profile.setMaximumSize(new Dimension(Integer.MAX_VALUE, 86));
            account.add(profile);
            account.add(javax.swing.Box.createVerticalStrut(10));

            JPanel details = new JPanel(new GridLayout(1, 3, 8, 0));
            details.setOpaque(false);
            String[][] values = isMicrosoft()
                    ? new String[][]{{"Sesja", "zapamiętana"}, {"Licencja", "Java Edition"}, {"Status", "gotowy do gry"}}
                    : new String[][]{{"Sesja", "brak"}, {"Licencja", "nieznana"}, {"Status", "wymaga logowania"}};
            for (String[] value : values) {
                JPanel tile = new JPanel(); tile.setOpaque(true); tile.setBackground(PANEL);
                tile.setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 9, 8, 7));
                tile.setLayout(new BoxLayout(tile, BoxLayout.Y_AXIS));
                JLabel cap = new JLabel(value[0]); cap.setForeground(MUTED); cap.setFont(body(Font.PLAIN, 11));
                JLabel val = new JLabel(value[1]); val.setForeground(isMicrosoft() ? ACCENT_HI : TEXT); val.setFont(head(Font.BOLD, 12));
                tile.add(cap); tile.add(javax.swing.Box.createVerticalStrut(4)); tile.add(val); details.add(tile);
            }
            details.setAlignmentX(Component.LEFT_ALIGNMENT);
            details.setMaximumSize(new Dimension(Integer.MAX_VALUE, 58));
            account.add(details);
            account.add(javax.swing.Box.createVerticalStrut(10));

            if (isMicrosoft()) {
                JLabel saved = new JLabel("✓  Sesja jest zapisana na tym komputerze");
                saved.setForeground(TEXT); saved.setFont(body(Font.PLAIN, 13));
                saved.setBorder(javax.swing.BorderFactory.createCompoundBorder(
                        javax.swing.BorderFactory.createLineBorder(BORDER),
                        javax.swing.BorderFactory.createEmptyBorder(10, 12, 10, 12)));
                saved.setAlignmentX(Component.LEFT_ALIGNMENT);
                saved.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
                account.add(saved);
                account.add(javax.swing.Box.createVerticalStrut(9));
                account.add(wideButton("Zmień konto Microsoft", true, startLogin));
                account.add(javax.swing.Box.createVerticalStrut(7));
                account.add(wideButton("Wyloguj z tego komputera", false, () -> {
                    MicrosoftAuth.deleteAccount(baseDir);
                    msAccount = null;
                    applyAccount();
                    build[0].run();
                }));
            } else {
                account.add(wideButton("Zaloguj przez Microsoft", true, startLogin));
            }
            account.add(javax.swing.Box.createVerticalStrut(10));
            account.add(htmlLabel("Hasło wpisujesz wyłącznie na oficjalnej stronie Microsoft.", MUTED, 12, Font.PLAIN));
            account.add(javax.swing.Box.createVerticalStrut(5));
            account.add(err);
            account.revalidate();
            account.repaint();
        };
        build[0].run();
        d.setVisible(true);
    }

    /** Zmiana wersji aktywnego profilu. */
    private void openVersionPicker() {
        Profile ap = activeProfile();
        if (ap == null) {
            return;
        }
        openVersionPicker(selected, v -> setProfileVersion(ap, v));
    }

    private void openVersionPicker(VersionEntry current, Consumer<VersionEntry> onChoose) {
        JPanel body = new JPanel(new BorderLayout(0, 10));
        JDialog d = makeDialog("Wybierz wersję", body, 440, 580);
        final boolean startAll = showAll
                || (current != null && current.type() != null && !"release".equals(current.type()));

        JTextField search = field("");
        JCheckBox all = new JCheckBox("Snapshoty i bardzo stare wersje", startAll);
        all.setOpaque(false);
        all.setForeground(MUTED);
        all.setFocusPainted(false);
        all.setFont(body(Font.PLAIN, 13));

        JPanel top = new JPanel(new BorderLayout(0, 8));
        top.setOpaque(false);
        JLabel hint = new JLabel("Szukaj wersji");
        hint.setForeground(MUTED);
        hint.setFont(body(Font.PLAIN, 12));
        JPanel searchBox = new JPanel(new BorderLayout(0, 4));
        searchBox.setOpaque(false);
        searchBox.add(hint, BorderLayout.NORTH);
        searchBox.add(search, BorderLayout.CENTER);
        top.add(searchBox, BorderLayout.NORTH);
        top.add(all, BorderLayout.SOUTH);

        DefaultListModel<VersionEntry> model = new DefaultListModel<>();
        JList<VersionEntry> list = new JList<>(model);
        list.setBackground(BG);
        list.setForeground(TEXT);
        list.setFixedCellHeight(38);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object value, int index,
                                                          boolean sel, boolean focus) {
                JLabel lab = (JLabel) super.getListCellRendererComponent(l, value, index, sel, focus);
                VersionEntry v = (VersionEntry) value;
                lab.setText(v.id() + (v.type() != null && !"release".equals(v.type()) ? "   (" + typeLabel(v.type()) + ")" : ""));
                lab.setFont(body(Font.PLAIN, 15));
                lab.setBackground(sel ? ACCENT : BG);
                lab.setForeground(sel ? Color.WHITE : TEXT);
                lab.setBorder(new EmptyBorder(0, 14, 0, 14));
                return lab;
            }
        });

        Runnable fill = () -> {
            String q = search.getText().trim().toLowerCase(Locale.ROOT);
            model.clear();
            for (VersionEntry v : allVersions) {
                if (!all.isSelected() && !"release".equals(v.type())) {
                    continue;
                }
                if (!q.isEmpty() && !v.id().toLowerCase(Locale.ROOT).contains(q)) {
                    continue;
                }
                model.addElement(v);
            }
            if (current != null) {
                for (int i = 0; i < model.size(); i++) {
                    if (model.get(i).id().equals(current.id())) {
                        list.setSelectedIndex(i);
                        list.ensureIndexIsVisible(i);
                        break;
                    }
                }
            }
        };
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                fill.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                fill.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                fill.run();
            }
        });
        all.addActionListener(e -> fill.run());

        FlatButton ok = new FlatButton("Wybierz", true);
        Runnable choose = () -> {
            VersionEntry v = list.getSelectedValue();
            if (all.isSelected() != startAll) {
                showAll = all.isSelected();
                config.setProperty("showAll", String.valueOf(showAll));
                saveConfig();
            }
            if (v != null) {
                onChoose.accept(v);
            }
            d.dispose();
        };
        ok.addActionListener(e -> choose.run());
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && list.getSelectedValue() != null) {
                    choose.run();
                }
            }
        });

        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        south.add(ok, BorderLayout.EAST);

        body.add(top, BorderLayout.NORTH);
        body.add(darkScroll(list), BorderLayout.CENTER);
        body.add(south, BorderLayout.SOUTH);

        fill.run();
        d.setVisible(true);
    }

    private void openSettings() {
        JPanel body = new JPanel(new BorderLayout(0, 12));
        JDialog d = makeDialog("Ustawienia", body, 440, 490);

        int maxRam = 8192;
        try {
            long total = ((com.sun.management.OperatingSystemMXBean)
                    java.lang.management.ManagementFactory.getOperatingSystemMXBean()).getTotalMemorySize() / (1024 * 1024);
            maxRam = (int) Math.max(2048, Math.min(32768, (total * 3 / 4) / 256 * 256));
        } catch (Throwable ignored) {
            // zostaje 8 GB
        }
        int start = Math.max(512, Math.min(ramMb, maxRam));

        JLabel ramLabel = new JLabel("RAM: " + formatGb(start));
        ramLabel.setForeground(TEXT);
        ramLabel.setFont(head(Font.BOLD, 20));
        JSlider slider = new JSlider(512, maxRam, start);
        slider.setOpaque(false);
        slider.setUI(new RamSliderUI(slider));
        slider.addChangeListener(e -> ramLabel.setText("RAM: " + formatGb(snap(slider.getValue()))));

        JPanel ramBox = new JPanel(new BorderLayout(0, 8));
        ramBox.setOpaque(false);
        ramBox.add(ramLabel, BorderLayout.NORTH);
        ramBox.add(slider, BorderLayout.CENTER);

        JCheckBox autoUpd = new JCheckBox("Automatycznie sprawdzaj aktualizacje",
                Boolean.parseBoolean(config.getProperty("autoUpdate", "true")));
        autoUpd.setOpaque(false);
        autoUpd.setForeground(TEXT);
        autoUpd.setFocusPainted(false);
        autoUpd.setFont(body(Font.PLAIN, 14));
        JPanel top = new JPanel(new BorderLayout(0, 10));
        top.setOpaque(false);
        top.add(ramBox, BorderLayout.CENTER);
        top.add(autoUpd, BorderLayout.SOUTH);

        FlatButton openGame = new FlatButton("Otwórz folder gry", false);
        openGame.addActionListener(e -> openFolder(installer.gameDir()));
        FlatButton openBase = new FlatButton("Otwórz folder launchera", false);
        openBase.addActionListener(e -> openFolder(baseDir));
        FlatButton checkUpd = new FlatButton("Sprawdź aktualizacje (v" + LAUNCHER_VERSION + ")", false);
        checkUpd.addActionListener(e -> {
            d.dispose();
            checkForUpdates(true);
        });
        FlatButton console = new FlatButton("Konsola (log launchera i gry)", false);
        console.addActionListener(e -> {
            d.dispose();
            showPage("console");
        });
        JPanel links = new JPanel(new GridLayout(4, 1, 0, 8));
        links.setOpaque(false);
        links.add(openGame);
        links.add(openBase);
        links.add(checkUpd);
        links.add(console);

        FlatButton save = new FlatButton("Zapisz", true);
        save.addActionListener(e -> {
            ramMb = snap(slider.getValue());
            config.setProperty("ram", String.valueOf(ramMb));
            config.setProperty("autoUpdate", String.valueOf(autoUpd.isSelected()));
            saveConfig();
            hero.repaint();
            d.dispose();
        });
        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        south.add(save, BorderLayout.EAST);

        body.add(top, BorderLayout.NORTH);
        body.add(links, BorderLayout.CENTER);
        body.add(south, BorderLayout.SOUTH);
        d.setVisible(true);
    }

    private static int snap(int v) {
        return Math.max(512, Math.round(v / 256f) * 256);
    }

    private static final class RamSliderUI extends javax.swing.plaf.basic.BasicSliderUI {
        RamSliderUI(JSlider s) {
            super(s);
        }

        @Override
        protected Dimension getThumbSize() {
            return new Dimension(18, 18);
        }

        @Override
        public void paintTrack(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            aa(g2);
            int cy = trackRect.y + trackRect.height / 2;
            g2.setColor(BORDER);
            g2.fillRoundRect(trackRect.x, cy - 3, trackRect.width, 6, 6, 6);
            g2.setColor(ACCENT);
            g2.fillRoundRect(trackRect.x, cy - 3, Math.max(0, thumbRect.x + thumbRect.width / 2 - trackRect.x), 6, 6, 6);
            g2.dispose();
        }

        @Override
        public void paintThumb(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            aa(g2);
            g2.setColor(ACCENT_HI);
            g2.fillOval(thumbRect.x, thumbRect.y, 18, 18);
            g2.dispose();
        }

        @Override
        public void paintFocus(Graphics g) {
            // bez ramki fokusu
        }
    }

    // ------------------------------------------------------------------ aktualizacje

    /** Sprawdza w tle, czy jest nowa wersja launchera. manual = true: pokaż też wynik "masz najnowszą". */
    private void checkForUpdates(boolean manual) {
        if (!Updater.enabled()) {
            if (manual) {
                notice("Aktualizacje", "Ta wersja launchera nie ma włączonych aktualizacji (build lokalny).");
            }
            return;
        }
        Thread t = new Thread(() -> {
            try {
                Updater.Info info = Updater.check(LAUNCHER_VERSION);
                if (info == null) {
                    appendLog("Aktualizacje: masz najnowszą wersję (" + LAUNCHER_VERSION + ").");
                    if (manual) {
                        SwingUtilities.invokeLater(() -> notice("Aktualizacje",
                                "Masz najnowszą wersję launchera (v" + LAUNCHER_VERSION + ")."));
                    }
                    return;
                }
                appendLog("Aktualizacje: dostępna wersja " + info.version() + ".");
                if (!manual && info.version().equals(config.getProperty("skipVersion"))) {
                    return;
                }
                SwingUtilities.invokeLater(() -> openUpdateDialog(info));
            } catch (Exception e) {
                appendLog("Aktualizacje: nie udało się sprawdzić (" + e.getMessage() + ").");
                if (manual) {
                    SwingUtilities.invokeLater(() -> notice("Aktualizacje",
                            "Nie udało się sprawdzić aktualizacji. Sprawdź połączenie z internetem."));
                }
            }
        }, "check-update");
        t.setDaemon(true);
        t.start();
    }

    private void notice(String title, String text) {
        JPanel body = new JPanel(new BorderLayout(0, 16));
        JDialog d = makeDialog(title, body, 420, 190);
        JLabel l = new JLabel("<html><body style='width:340px'>" + escape(text) + "</body></html>");
        l.setForeground(TEXT);
        l.setFont(body(Font.PLAIN, 14));
        FlatButton ok = new FlatButton("OK", true);
        ok.addActionListener(e -> d.dispose());
        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        south.add(ok, BorderLayout.EAST);
        body.add(l, BorderLayout.CENTER);
        body.add(south, BorderLayout.SOUTH);
        d.setVisible(true);
    }

    private void openUpdateDialog(Updater.Info info) {
        JPanel body = new JPanel(new BorderLayout(0, 12));
        JDialog d = makeDialog("Dostępna aktualizacja", body, 480, 330);

        JLabel head = new JLabel("BushLaunch " + info.version());
        head.setForeground(TEXT);
        head.setFont(head(Font.BOLD, 24));
        String notes = info.notes().isBlank() ? "Nowa wersja launchera jest gotowa do instalacji." : info.notes();
        JLabel sub = new JLabel("<html><body style='width:400px'>Masz wersję " + LAUNCHER_VERSION + ".<br><br>"
                + escape(notes).replace("\n", "<br>") + "</body></html>");
        sub.setForeground(MUTED);
        sub.setFont(body(Font.PLAIN, 14));
        sub.setVerticalAlignment(JLabel.TOP);

        final double[] frac = {0};
        JComponent bar = new JComponent() {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                aa(g);
                g.setColor(BORDER);
                g.fill(new RoundRectangle2D.Double(0, 5, getWidth(), 8, 8, 8));
                g.setPaint(new GradientPaint(0, 0, ACCENT, getWidth(), 0, ACCENT_HI));
                g.fill(new RoundRectangle2D.Double(0, 5, Math.max(0, getWidth() * frac[0]), 8, 8, 8));
                g.dispose();
            }
        };
        bar.setPreferredSize(new Dimension(10, 18));
        bar.setVisible(false);
        JLabel status = new JLabel(" ");
        status.setForeground(MUTED);
        status.setFont(body(Font.PLAIN, 12));

        FlatButton go = new FlatButton("Aktualizuj teraz", true);
        FlatButton later = new FlatButton("Później", false);
        FlatButton skip = new FlatButton("Pomiń tę wersję", false);
        later.addActionListener(e -> d.dispose());
        skip.addActionListener(e -> {
            config.setProperty("skipVersion", info.version());
            saveConfig();
            d.dispose();
        });
        go.addActionListener(e -> {
            if (busy || gameRunning) {
                status.setForeground(new Color(0xFF6B5A));
                status.setText("Poczekaj, aż skończy się pobieranie lub gra.");
                return;
            }
            if (!Updater.isInstalled()) {
                openUrl(Updater.releasePage());
                d.dispose();
                return;
            }
            go.setEnabled(false);
            later.setEnabled(false);
            skip.setEnabled(false);
            bar.setVisible(true);
            status.setForeground(MUTED);
            status.setText("Pobieranie...");
            Thread t = new Thread(() -> {
                try {
                    java.nio.file.Path setup = Updater.download(info, (done, total) -> SwingUtilities.invokeLater(() -> {
                        frac[0] = total > 0 ? Math.min(1.0, done / (double) total) : 0;
                        status.setText(total > 0 ? "Pobieranie... " + (int) (frac[0] * 100) + "%" : "Pobieranie...");
                        bar.repaint();
                    }));
                    SwingUtilities.invokeLater(() -> status.setText("Instalowanie - launcher uruchomi się ponownie..."));
                    appendLog("Aktualizacje: uruchamiam instalator " + info.version());
                    Updater.installAndExit(setup);
                } catch (Exception ex) {
                    appendLog("Aktualizacje: błąd - " + ex.getMessage());
                    SwingUtilities.invokeLater(() -> {
                        status.setForeground(new Color(0xFF6B5A));
                        status.setText("Błąd: " + ex.getMessage());
                        go.setEnabled(true);
                        later.setEnabled(true);
                        skip.setEnabled(true);
                    });
                }
            }, "download-update");
            t.setDaemon(true);
            t.start();
        });

        JPanel center = new JPanel(new BorderLayout(0, 10));
        center.setOpaque(false);
        JPanel headBox = new JPanel(new BorderLayout());
        headBox.setOpaque(false);
        headBox.add(head, BorderLayout.NORTH);
        center.add(headBox, BorderLayout.NORTH);
        center.add(sub, BorderLayout.CENTER);
        JPanel prog = new JPanel(new BorderLayout(0, 4));
        prog.setOpaque(false);
        prog.add(bar, BorderLayout.NORTH);
        prog.add(status, BorderLayout.SOUTH);
        center.add(prog, BorderLayout.SOUTH);

        JPanel buttons = new JPanel(new BorderLayout());
        buttons.setOpaque(false);
        buttons.add(skip, BorderLayout.WEST);
        JPanel right = new JPanel(new GridLayout(1, 2, 8, 0));
        right.setOpaque(false);
        right.add(later);
        right.add(go);
        buttons.add(right, BorderLayout.EAST);

        body.add(center, BorderLayout.CENTER);
        body.add(buttons, BorderLayout.SOUTH);
        d.setVisible(true);
    }

    // ------------------------------------------------------------------ wersje i uruchamianie

    private void loadVersions() {
        appendLog("Pobieranie listy wersji z serwerów Mojang...");
        Thread t = new Thread(() -> {
            List<VersionEntry> list;
            try {
                list = installer.fetchVersions();
                appendLog("Znaleziono wersji: " + list.size());
            } catch (Exception e) {
                appendLog("Brak połączenia z serwerami Mojang (" + e.getMessage()
                        + "). Pokazuję tylko wersje pobrane wcześniej.");
                list = installer.installedVersions();
                online = false;
            }
            List<VersionEntry> result = list;
            SwingUtilities.invokeLater(() -> {
                allVersions = result;
                versionIndex.clear();
                for (VersionEntry v : allVersions) {
                    versionIndex.put(v.id(), v);
                }
                for (VersionEntry v : installer.installedVersions()) {
                    versionIndex.putIfAbsent(v.id(), v);
                }
                loadingVersions = false;
                // profil bez wersji (np. po pierwszej migracji) dostaje najnowsze wydanie
                boolean changed = false;
                VersionEntry latest = null;
                for (VersionEntry v : allVersions) {
                    if ("release".equals(v.type())) {
                        latest = v;
                        break;
                    }
                }
                if (latest == null && !allVersions.isEmpty()) {
                    latest = allVersions.get(0);
                }
                if (latest != null) {
                    for (Profile p : profiles) {
                        if (p.version == null || p.version.isBlank()) {
                            p.version = latest.id();
                            changed = true;
                        }
                    }
                }
                if (changed) {
                    saveProfiles();
                }
                applyActive();
            });
        }, "load-versions");
        t.setDaemon(true);
        t.start();
    }

    private void onPlay() {
        if (busy || loadingVersions) {
            return;
        }
        if (!isMicrosoft()) {
            openAccountDialog(this::onPlay);
            return;
        }
        if (selected == null) {
            openVersionPicker();
            return;
        }
        VersionEntry version = selected;
        String name = nick;
        int ram = ramMb;
        MicrosoftAuth.Account acc = isMicrosoft() ? msAccount : null;

        Profile ap = activeProfile();
        if (ap != null) {
            ap.used = System.currentTimeMillis();
        }
        config.setProperty("version", version.id());
        config.setProperty("ram", String.valueOf(ram));
        config.setProperty("showAll", String.valueOf(showAll));
        saveProfiles();

        setBusy(true);
        Thread t = new Thread(() -> runGame(version, name, ram, acc), "game");
        t.setDaemon(true);
        t.start();
    }

    private void runGame(VersionEntry version, String name, int ram, MicrosoftAuth.Account acc) {
        try {
            GameInstaller.Session session;
            if (acc == null) {
                throw new IOException("Zaloguj się kontem Microsoft, żeby zagrać.");
            }
            {
                String cid = msClientId();
                if (cid == null || cid.isBlank()) {
                    throw new IOException("Brak konfiguracji logowania Microsoft. Właściciel musi wbudować Client ID podczas budowania launchera.");
                }
                appendLog("Odświeżanie sesji Microsoft...");
                MicrosoftAuth.Login l;
                synchronized (authLock) {
                    MicrosoftAuth.Account cur = msAccount != null ? msAccount : acc;
                    l = MicrosoftAuth.refresh(cid, cur, this::appendLog);
                    MicrosoftAuth.Account fresh = new MicrosoftAuth.Account(l.name(), l.uuid(), l.refreshToken());
                    MicrosoftAuth.saveAccount(baseDir, fresh);
                    msAccount = fresh;
                    socialCredsCache = new Social.Creds(l.name(), l.uuid(), l.mcToken());
                    socialCredsAt = System.currentTimeMillis();
                }
                session = new GameInstaller.Session(l.name(), l.uuid(), l.mcToken(), "msa");
            }

            GameInstaller.Prepared prepared = installer.install(version);

            JavaFinder.Found java = JavaFinder.find(prepared.javaMajor(), baseDir);
            if (java.bundled() && java.major() < prepared.javaMajor()) {
                throw new IOException("Ta wersja wymaga Javy " + prepared.javaMajor()
                        + ", a dostępna jest tylko " + java.major()
                        + ". Zainstaluj Javę " + prepared.javaMajor() + " (np. Temurin).");
            }
            if (java.bundled() && prepared.javaMajor() <= 8 && java.major() > 8) {
                appendLog("UWAGA: ta wersja najlepiej działa na Javie 8, a użyto Javy " + java.major()
                        + ". Jeśli gra się nie uruchomi, zainstaluj Javę 8.");
            }
            appendLog("Java " + java.major() + ": " + java.exe());

            List<String> cmd = installer.buildCommand(prepared, java.exe(), session, ram);
            appendLog("Uruchamianie Minecrafta " + version.id() + " jako " + session.name() + "...");
            SwingUtilities.invokeLater(() -> {
                gameRunning = true;
                gameInfo = "Minecraft " + version.id();
                hero.repaint();
                repaintSelf();
                socialPingNow();
            });

            long started = System.currentTimeMillis();
            Process proc = new ProcessBuilder(cmd)
                    .directory(installer.gameDir().toFile())
                    .redirectErrorStream(true)
                    .start();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    appendLog(line);
                }
            }
            int code = proc.waitFor();
            appendLog("Gra zakończona (kod " + code + ")");
            addPlaytime((System.currentTimeMillis() - started) / 1000);
        } catch (Exception e) {
            appendLog("BŁĄD: " + e.getMessage());
            SwingUtilities.invokeLater(() -> showError(e.getMessage()));
        } finally {
            SwingUtilities.invokeLater(() -> {
                gameRunning = false;
                gameInfo = "";
                repaintSelf();
                socialPingNow();
                setBusy(false);
                refreshStats();
                refreshRecent();
            });
        }
    }

    private void showError(String message) {
        JPanel body = new JPanel(new BorderLayout(0, 14));
        JDialog d = makeDialog("BushLaunch - błąd", body, 520, 230);
        JLabel l = new JLabel("<html><body style='width:440px'>" + escape(message) + "</body></html>");
        l.setForeground(TEXT);
        l.setFont(body(Font.PLAIN, 14));
        FlatButton ok = new FlatButton("OK", true);
        ok.addActionListener(e -> d.dispose());
        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        south.add(ok, BorderLayout.EAST);
        body.add(l, BorderLayout.CENTER);
        body.add(south, BorderLayout.SOUTH);
        d.setVisible(true);
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private void setBusy(boolean b) {
        busy = b;
        indeterminate = b;
        progDone = 0;
        progTotal = 0;
        progWhat = "";
        if (hero != null) {
            hero.repaint();
        }
        if (sidebarUser != null) {
            sidebarUser.repaint();
        }
    }

    // ------------------------------------------------------------------ statystyki

    private void addPlaytime(long seconds) {
        long total = playtimeSec() + Math.max(0, seconds);
        config.setProperty("playtimeSec", String.valueOf(total));
        saveConfig();
    }

    private long playtimeSec() {
        try {
            return Long.parseLong(config.getProperty("playtimeSec", "0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String formatPlaytime() {
        long s = playtimeSec();
        if (s < 3600) {
            return (s / 60) + " min";
        }
        return (s / 3600) + " h";
    }

    private static String formatGb(int mb) {
        if (mb % 1024 == 0) {
            return (mb / 1024) + " GB";
        }
        return String.format(Locale.ROOT, "%.1f GB", mb / 1024.0).replace('.', ',');
    }

    // ------------------------------------------------------------------ pomocnicze

    private void openUrl(String url) {
        if (url == null || url.isBlank()) return;
        try {
            URI uri = new URI(url);
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(uri);
                return;
            }
            // Awaryjne otwieranie domyślnej przeglądarki, także gdy AWT Desktop
            // nie jest dostępny w danym środowisku.
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            if (os.contains("win")) {
                new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start();
            } else if (os.contains("mac")) {
                new ProcessBuilder("open", url).start();
            } else {
                new ProcessBuilder("xdg-open", url).start();
            }
        } catch (Exception e) {
            appendLog("Nie udało się otworzyć strony Microsoft. Otwórz ręcznie: " + url
                    + " (" + e.getMessage() + ")");
        }
    }

    private void openFolder(Path dir) {
        try {
            Files.createDirectories(dir);
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(dir.toFile());
            }
        } catch (Exception e) {
            appendLog("Nie udało się otworzyć folderu: " + e.getMessage());
        }
    }

    private void appendLog(String line) {
        SwingUtilities.invokeLater(() -> {
            if (logArea == null) {
                return;
            }
            logArea.append(line + "\n");
            if (logArea.getDocument().getLength() > 200_000) {
                try {
                    logArea.getDocument().remove(0, 100_000);
                } catch (BadLocationException ignored) {
                    // nic nie robimy
                }
            }
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private void loadConfig() {
        if (!Files.exists(configFile)) {
            return;
        }
        try (InputStream in = Files.newInputStream(configFile)) {
            config.load(in);
        } catch (IOException ignored) {
            // uszkodzony config - użyjemy domyślnych wartości
        }
    }

    private void saveConfig() {
        try {
            Files.createDirectories(baseDir);
            try (OutputStream out = Files.newOutputStream(configFile)) {
                config.store(out, "BushLaunch " + LAUNCHER_VERSION);
            }
        } catch (IOException e) {
            appendLog("Nie udało się zapisać konfiguracji: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ małe komponenty

    static class Clickable extends JComponent {
        boolean hover;
        Runnable onClick;

        Clickable(Runnable onClick) {
            this.onClick = onClick;
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (contains(e.getPoint()) && Clickable.this.onClick != null) {
                        Clickable.this.onClick.run();
                    }
                }
            });
        }
    }

    /** Przyciski okna: 0 = minimalizuj, 1 = maksymalizuj, 2 = zamknij. */
    static final class WinButton extends Clickable {
        private final int kind;

        WinButton(int kind, Runnable action) {
            super(action);
            this.kind = kind;
            setPreferredSize(new Dimension(50, 46));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            aa(g);
            if (hover) {
                g.setColor(kind == 2 ? new Color(0xC42B1C) : alpha(Color.WHITE, 24));
                g.fillRect(0, 0, getWidth(), getHeight());
            }
            g.setColor(hover ? TEXT : MUTED);
            g.setStroke(new BasicStroke(1.3f));
            int cx = getWidth() / 2;
            int cy = getHeight() / 2;
            if (kind == 0) {
                g.drawLine(cx - 5, cy, cx + 5, cy);
            } else if (kind == 1) {
                g.drawRect(cx - 5, cy - 5, 10, 10);
            } else {
                g.drawLine(cx - 5, cy - 5, cx + 5, cy + 5);
                g.drawLine(cx - 5, cy + 5, cx + 5, cy - 5);
            }
            g.dispose();
        }
    }

    /** Płaski przycisk w stylu launchera. */
    static final class FlatButton extends JButton {
        private final boolean primary;
        private boolean hover;

        FlatButton(String text, boolean primary) {
            super(text);
            this.primary = primary;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setForeground(primary ? ON_ACCENT : TEXT);
            setFont(body(Font.BOLD, 14));
            setBorder(new EmptyBorder(11, 24, 11, 24));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            aa(g);
            Color top = primary ? (hover ? new Color(0x5BFFB4) : ACCENT_HI) : (hover ? new Color(0x243340) : new Color(0x1A2530));
            Color bot = primary ? (hover ? ACCENT_HI : ACCENT) : (hover ? new Color(0x1A2530) : new Color(0x131C25));
            g.setPaint(new GradientPaint(0, 0, top, 0, getHeight(), bot));
            g.fill(cut(0, 0, getWidth(), getHeight(), 10));
            g.setPaint(new GradientPaint(0, 0, alpha(Color.WHITE, primary ? 60 : 24), 0, getHeight() / 2f, alpha(Color.WHITE, 0)));
            g.fill(cut(0, 0, getWidth(), getHeight() / 2.0, 10));
            g.dispose();
            super.paintComponent(g0);
        }
    }

    // ------------------------------------------------------------------ ikony (rysowane, bez plików)

    /**
     * Ikony: 0 gamepad, 1 warstwy, 2 kostka, 3 koszulka, 4 torba, 5 prezent, 6 konsola,
     * 7 suwaki, 8 przekreślone oko, 9 folder, 10 dokument, 12 dom, 13 puzzle (mody), 14 koło zębate, 15 układ scalony (RAM).
     */
    static void icon(Graphics2D g0, int kind, int x, int y, int s, Color color) {
        Graphics2D g = (Graphics2D) g0.create();
        aa(g);
        g.setColor(color);
        g.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        switch (kind) {
            case 0 -> {
                g.draw(new RoundRectangle2D.Double(x, y + s * 0.22, s, s * 0.58, 9, 9));
                g.drawLine((int) (x + s * 0.28), (int) (y + s * 0.38), (int) (x + s * 0.28), (int) (y + s * 0.62));
                g.drawLine((int) (x + s * 0.16), (int) (y + s * 0.50), (int) (x + s * 0.40), (int) (y + s * 0.50));
                g.fillOval((int) (x + s * 0.62), (int) (y + s * 0.40), 3, 3);
                g.fillOval((int) (x + s * 0.74), (int) (y + s * 0.52), 3, 3);
            }
            case 1 -> {
                g.draw(poly(true, x + s * 0.5, y + s * 0.08, x + s * 0.95, y + s * 0.30,
                        x + s * 0.5, y + s * 0.52, x + s * 0.05, y + s * 0.30));
                g.draw(poly(false, x + s * 0.05, y + s * 0.50, x + s * 0.5, y + s * 0.72, x + s * 0.95, y + s * 0.50));
                g.draw(poly(false, x + s * 0.05, y + s * 0.70, x + s * 0.5, y + s * 0.92, x + s * 0.95, y + s * 0.70));
            }
            case 2 -> {
                g.draw(poly(true, x + s * 0.5, y + s * 0.04, x + s * 0.93, y + s * 0.27, x + s * 0.93, y + s * 0.73,
                        x + s * 0.5, y + s * 0.96, x + s * 0.07, y + s * 0.73, x + s * 0.07, y + s * 0.27));
                g.draw(poly(false, x + s * 0.07, y + s * 0.27, x + s * 0.5, y + s * 0.5, x + s * 0.93, y + s * 0.27));
                g.drawLine((int) (x + s * 0.5), (int) (y + s * 0.5), (int) (x + s * 0.5), (int) (y + s * 0.96));
            }
            case 3 -> g.draw(poly(true, x + s * 0.30, y + s * 0.08, x + s * 0.42, y + s * 0.08, x + s * 0.50, y + s * 0.20,
                    x + s * 0.58, y + s * 0.08, x + s * 0.70, y + s * 0.08, x + s * 0.96, y + s * 0.30,
                    x + s * 0.82, y + s * 0.48, x + s * 0.72, y + s * 0.40, x + s * 0.72, y + s * 0.92,
                    x + s * 0.28, y + s * 0.92, x + s * 0.28, y + s * 0.40, x + s * 0.18, y + s * 0.48,
                    x + s * 0.04, y + s * 0.30));
            case 4 -> {
                g.draw(new RoundRectangle2D.Double(x + s * 0.1, y + s * 0.32, s * 0.8, s * 0.64, 5, 5));
                g.draw(new Arc2D.Double(x + s * 0.3, y + s * 0.06, s * 0.4, s * 0.5, 0, 180, Arc2D.OPEN));
            }
            case 5 -> {
                g.draw(new RoundRectangle2D.Double(x + s * 0.1, y + s * 0.42, s * 0.8, s * 0.52, 3, 3));
                g.draw(new RoundRectangle2D.Double(x + s * 0.04, y + s * 0.28, s * 0.92, s * 0.15, 3, 3));
                g.drawLine((int) (x + s * 0.5), (int) (y + s * 0.28), (int) (x + s * 0.5), (int) (y + s * 0.94));
                g.draw(new Ellipse2D.Double(x + s * 0.24, y + s * 0.06, s * 0.26, s * 0.22));
                g.draw(new Ellipse2D.Double(x + s * 0.50, y + s * 0.06, s * 0.26, s * 0.22));
            }
            case 6 -> {
                g.draw(new RoundRectangle2D.Double(x + s * 0.04, y + s * 0.14, s * 0.92, s * 0.72, 6, 6));
                g.draw(poly(false, x + s * 0.22, y + s * 0.36, x + s * 0.38, y + s * 0.50, x + s * 0.22, y + s * 0.64));
                g.drawLine((int) (x + s * 0.48), (int) (y + s * 0.66), (int) (x + s * 0.70), (int) (y + s * 0.66));
            }
            case 7 -> {
                g.drawLine(x, (int) (y + s * 0.3), x + s, (int) (y + s * 0.3));
                g.drawLine(x, (int) (y + s * 0.7), x + s, (int) (y + s * 0.7));
                g.setColor(PANEL_HI);
                g.fillOval((int) (x + s * 0.2), (int) (y + s * 0.3 - 4), 8, 8);
                g.fillOval((int) (x + s * 0.62), (int) (y + s * 0.7 - 4), 8, 8);
                g.setColor(color);
                g.drawOval((int) (x + s * 0.2), (int) (y + s * 0.3 - 4), 8, 8);
                g.drawOval((int) (x + s * 0.62), (int) (y + s * 0.7 - 4), 8, 8);
            }
            case 12 -> {
                g.draw(poly(false, x + s * 0.05, y + s * 0.50, x + s * 0.5, y + s * 0.10, x + s * 0.95, y + s * 0.50));
                g.draw(poly(false, x + s * 0.18, y + s * 0.42, x + s * 0.18, y + s * 0.92,
                        x + s * 0.82, y + s * 0.92, x + s * 0.82, y + s * 0.42));
                g.draw(poly(false, x + s * 0.40, y + s * 0.92, x + s * 0.40, y + s * 0.64,
                        x + s * 0.60, y + s * 0.64, x + s * 0.60, y + s * 0.92));
            }
            case 13 -> {
                java.awt.geom.Area a = new java.awt.geom.Area(
                        new java.awt.geom.Rectangle2D.Double(x + s * 0.10, y + s * 0.28, s * 0.70, s * 0.64));
                a.add(new java.awt.geom.Area(new Ellipse2D.Double(x + s * 0.34, y + s * 0.06, s * 0.26, s * 0.26)));
                a.add(new java.awt.geom.Area(new Ellipse2D.Double(x + s * 0.72, y + s * 0.46, s * 0.26, s * 0.26)));
                g.draw(a);
            }
            case 14 -> {
                double cx = x + s / 2.0;
                double cy = y + s / 2.0;
                g.draw(new Ellipse2D.Double(cx - s * 0.28, cy - s * 0.28, s * 0.56, s * 0.56));
                g.draw(new Ellipse2D.Double(cx - s * 0.11, cy - s * 0.11, s * 0.22, s * 0.22));
                g.setStroke(new BasicStroke(Math.max(2f, s * 0.13f), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
                for (int i = 0; i < 8; i++) {
                    double ang = i * Math.PI / 4;
                    g.draw(new java.awt.geom.Line2D.Double(cx + Math.cos(ang) * s * 0.30, cy + Math.sin(ang) * s * 0.30,
                            cx + Math.cos(ang) * s * 0.45, cy + Math.sin(ang) * s * 0.45));
                }
            }
            case 15 -> {
                g.draw(new RoundRectangle2D.Double(x + s * 0.22, y + s * 0.22, s * 0.56, s * 0.56, 4, 4));
                g.draw(new java.awt.geom.Rectangle2D.Double(x + s * 0.38, y + s * 0.38, s * 0.24, s * 0.24));
                for (int i = 0; i < 3; i++) {
                    double o = s * (0.34 + i * 0.16);
                    g.draw(new java.awt.geom.Line2D.Double(x + o, y + s * 0.06, x + o, y + s * 0.22));
                    g.draw(new java.awt.geom.Line2D.Double(x + o, y + s * 0.78, x + o, y + s * 0.94));
                    g.draw(new java.awt.geom.Line2D.Double(x + s * 0.06, y + o, x + s * 0.22, y + o));
                    g.draw(new java.awt.geom.Line2D.Double(x + s * 0.78, y + o, x + s * 0.94, y + o));
                }
            }
            case 9 -> {
                Path2D f = new Path2D.Double();
                f.moveTo(x, y + s * 0.78);
                f.lineTo(x, y + s * 0.22);
                f.lineTo(x + s * 0.38, y + s * 0.22);
                f.lineTo(x + s * 0.48, y + s * 0.34);
                f.lineTo(x + s, y + s * 0.34);
                f.lineTo(x + s, y + s * 0.78);
                f.closePath();
                g.draw(f);
            }
            case 10 -> {
                g.draw(new RoundRectangle2D.Double(x + s * 0.1, y + s * 0.1, s * 0.8, s * 0.8, 6, 6));
                g.drawLine((int) (x + s * 0.28), (int) (y + s * 0.34), (int) (x + s * 0.72), (int) (y + s * 0.34));
                g.drawLine((int) (x + s * 0.28), (int) (y + s * 0.52), (int) (x + s * 0.72), (int) (y + s * 0.52));
                g.drawLine((int) (x + s * 0.28), (int) (y + s * 0.70), (int) (x + s * 0.56), (int) (y + s * 0.70));
            }
            default -> {
                g.draw(new Ellipse2D.Double(x + s * 0.05, y + s * 0.25, s * 0.9, s * 0.5));
                g.draw(new Ellipse2D.Double(x + s * 0.38, y + s * 0.38, s * 0.24, s * 0.24));
                g.drawLine((int) (x + s * 0.12), (int) (y + s * 0.88), (int) (x + s * 0.88), (int) (y + s * 0.12));
            }
        }
        g.dispose();
    }

    private static Path2D poly(boolean close, double... pts) {
        Path2D p = new Path2D.Double();
        p.moveTo(pts[0], pts[1]);
        for (int i = 2; i + 1 < pts.length; i += 2) {
            p.lineTo(pts[i], pts[i + 1]);
        }
        if (close) {
            p.closePath();
        }
        return p;
    }
}
