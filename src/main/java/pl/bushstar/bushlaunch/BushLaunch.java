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

    /** Logo: zielona litera B w ciemnym kwadracie z zieloną ramką. */
    static void logoMark(Graphics2D g0, int x, int y, int s) {
        Graphics2D g = (Graphics2D) g0.create();
        aa(g);
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
    private boolean gameRunning;
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
        bar.add(buttons, BorderLayout.EAST);

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
                Graphics2D gh = (Graphics2D) g.create();
                gh.clip(new RoundRectangle2D.Double(14, (h - 40) / 2.0, 40, 40, 8, 8));
                drawHead(gh, skin, 14, (h - 40) / 2, 40);
                gh.dispose();
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
            this.thumb = thumb(kind, 40, 32);
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
        private int sceneW;
        private int sceneH;
        private final long t0 = System.nanoTime();
        /** Świetliki: x (0-1), y (0-1), faza, prędkość. */
        private final double[][] flies = new double[16][4];
        /** Gwiazdy: x (0-1), y (0-0.46), rozmiar, faza migotania. */
        private final double[][] stars = new double[110][4];
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
            for (double[] st : stars) {
                st[0] = r.nextDouble();
                st[1] = Math.pow(r.nextDouble(), 1.3) * 0.46;
                st[2] = 0.8 + r.nextDouble() * 1.2;
                st[3] = r.nextDouble() * Math.PI * 2;
            }
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
            if (scene == null || sceneW != w || sceneH != h) {
                scene = nightScene(w, h);
                sceneW = w;
                sceneH = h;
            }
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(scene, 0, 0, w, h, null);

            // gwiazdy (lekko migoczą)
            for (double[] st : stars) {
                double tw = 0.5 + 0.5 * Math.sin(time * (0.6 + st[2] * 0.4) + st[3]);
                int a = (int) (70 + 150 * tw * (1 - st[1] / 0.6));
                g.setColor(alpha(new Color(0xE6F4EE), Math.max(20, Math.min(255, a))));
                g.fill(new Ellipse2D.Double(st[0] * w, st[1] * h, st[2], st[2]));
            }

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
            int headsW = shown == 0 ? 0 : 28 + (shown - 1) * 18 + 10;
            int pw = 18 + 10 + 10 + stW + 14 + 1 + 14 + headsW + lbW + 18 + 20;
            int px = w - 22 - pw;
            int py = 18;
            int cy = py + ph / 2;
            g.setColor(alpha(new Color(0x070B0F), 205));
            g.fill(new RoundRectangle2D.Double(px, py, pw, ph, ph, ph));
            g.setColor(hoverFriends ? alpha(ACCENT, 160) : alpha(Color.WHITE, 30));
            g.setStroke(new BasicStroke(1f));
            g.draw(new RoundRectangle2D.Double(px + 0.5, py + 0.5, pw - 1, ph - 1, ph, ph));

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
                int hx = x + i * 18;
                g.setColor(new Color(0x070B0F));
                g.fill(new Ellipse2D.Double(hx - 2, cy - 16, 32, 32));
                Graphics2D gh = (Graphics2D) g.create();
                gh.clip(new Ellipse2D.Double(hx, cy - 14, 28, 28));
                BufferedImage sk = friendSkins.get(fs.get(i));
                drawHead(gh, sk == NO_SKIN ? null : sk, hx, cy - 14, 28);
                gh.dispose();
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

    // ------------------------------------------------------------------ grafika sceny (generowana, w stylu Minecrafta)

    private static final double LANTERN_X = 0.786;
    private static final double LANTERN_Y = 0.585;

    private static int mix(int a, int b, double t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private static void px(BufferedImage im, int x, int y, int rgb) {
        if (x >= 0 && y >= 0 && x < im.getWidth() && y < im.getHeight()) {
            im.setRGB(x, y, rgb);
        }
    }

    private static BufferedImage nightScene(int w, int h) {
        final int s = 4;
        int lw = Math.max(8, w / s + 1);
        int lh = Math.max(8, h / s + 1);
        BufferedImage im = new BufferedImage(lw, lh, BufferedImage.TYPE_INT_RGB);
        Random rnd = new Random(1337);
        int horizon = (int) (lh * 0.50);

        // niebo
        for (int y = 0; y < lh; y++) {
            double t = Math.min(1.0, y / (double) horizon);
            int c = mix(0x04080B, 0x3B5456, Math.pow(t, 1.6));
            for (int x = 0; x < lw; x++) {
                im.setRGB(x, y, c);
            }
        }
        nightMountains(im, rnd, horizon, lh * 0.30, 0x34495A, 0.8, 0x88A2AF);
        nightMountains(im, rnd, horizon + 2, lh * 0.19, 0x1B2D34, 1.9, 0x4B6671);

        // mgła przy horyzoncie
        for (int y = Math.max(0, horizon - 9); y < Math.min(lh, horizon + 13); y++) {
            double a = Math.max(0, 1 - Math.abs(y - horizon - 1) / 11.0) * 0.38;
            for (int x = 0; x < lw; x++) {
                im.setRGB(x, y, mix(im.getRGB(x, y) & 0xFFFFFF, 0x4A6461, a));
            }
        }

        // wzgórza z lasem
        int[] hillTop = new int[lw];
        for (int x = 0; x < lw; x++) {
            int top = horizon + 4 + (int) (3 * Math.sin(x * 0.07 + 1) + 2 * Math.sin(x * 0.19));
            hillTop[x] = top;
            for (int y = top; y < lh; y++) {
                int n = rnd.nextInt(6);
                int c = n == 0 ? 0x10201A : 0x0C1914;
                px(im, x, y, mix(c, 0x070F0C, (y - top) / (lh * 0.45)));
            }
        }
        for (int i = 0; i < lw / 3; i++) {
            int x = rnd.nextInt(lw);
            pine(im, rnd, x, hillTop[x] + 2, 5 + rnd.nextInt(6), 2 + rnd.nextInt(2), 0x0A1813);
        }

        // pierwszy plan
        int groundTop = (int) (lh * 0.68);
        for (int x = 0; x < lw; x++) {
            int gy = groundTop + (int) (2 * Math.sin(x * 0.05) + 1.5 * Math.sin(x * 0.21 + 2));
            for (int y = gy; y < lh; y++) {
                int n = rnd.nextInt(6);
                int c = n == 0 ? 0x16291F : n == 1 ? 0x0A130F : 0x0D1914;
                if (y == gy) {
                    c = 0x1C3427;
                }
                px(im, x, y, mix(c, 0x050A08, (y - gy) / (double) Math.max(1, lh - gy) * 0.6));
            }
        }

        // duże sosny po lewej
        bigPine(im, rnd, (int) (lw * 0.06), groundTop + 4, (int) (lh * 0.84), (int) (lw * 0.085));
        bigPine(im, rnd, (int) (lw * 0.17), groundTop + 2, (int) (lh * 0.58), (int) (lw * 0.06));
        bigPine(im, rnd, (int) (lw * 0.30), groundTop, (int) (lh * 0.36), (int) (lw * 0.04));

        // dom z latarnią po prawej
        house(im, rnd, (int) (lw * 0.74), groundTop + 2, lw - (int) (lw * 0.74) + 4, (int) (lh * 0.27), lw, lh);
        return im;
    }

    private static void nightMountains(BufferedImage im, Random rnd, int horizon, double amp, int color,
                                       double freq, int rim) {
        double p1 = rnd.nextDouble() * 6;
        double p2 = rnd.nextDouble() * 6;
        double p3 = rnd.nextDouble() * 6;
        int lw = im.getWidth();
        for (int x = 0; x < lw; x++) {
            double u = x / (double) lw * freq * 6;
            double hgt = amp * (0.5 + 0.28 * Math.sin(u + p1) + 0.16 * Math.sin(u * 2.3 + p2)
                    + 0.08 * Math.sin(u * 5.1 + p3));
            int top = horizon - (int) hgt;
            for (int y = top; y < horizon + 5 && y < im.getHeight(); y++) {
                int c = color;
                int n = rnd.nextInt(5);
                if (n == 0) {
                    c = mix(color, 0x000000, 0.14);
                } else if (n == 1) {
                    c = mix(color, 0xFFFFFF, 0.05);
                }
                if (y - top < 2) {
                    c = mix(c, rim, 0.5);
                }
                px(im, x, y, mix(c, 0x0A1216, Math.max(0, (y - top) / (amp + 5)) * 0.5));
            }
        }
    }

    private static void pine(BufferedImage im, Random rnd, int cx, int baseY, int height, int halfW, int color) {
        int trunk = Math.max(1, height / 6);
        for (int y = 0; y < trunk; y++) {
            px(im, cx, baseY - y, mix(color, 0x000000, 0.3));
        }
        int tiers = Math.max(3, height / 4);
        int crown = Math.max(1, height - trunk);
        for (int y = 0; y < crown; y++) {
            double f = y / (double) crown;
            double tierPos = (f * tiers) % 1.0;
            int wy = (int) Math.round(halfW * (1 - f) * (0.55 + 0.45 * (1 - tierPos)));
            for (int dx = -wy; dx <= wy; dx++) {
                int n = rnd.nextInt(5);
                int c = n == 0 ? mix(color, 0xFFFFFF, 0.06) : n == 1 ? mix(color, 0x000000, 0.25) : color;
                px(im, cx + dx, baseY - trunk - y, c);
            }
        }
    }

    private static void bigPine(BufferedImage im, Random rnd, int cx, int baseY, int height, int halfW) {
        int color = 0x060E0B;
        int trunkW = Math.max(2, halfW / 6);
        int trunk = height / 7;
        for (int y = 0; y < trunk; y++) {
            for (int dx = -trunkW / 2; dx <= trunkW / 2; dx++) {
                px(im, cx + dx, baseY - y, rnd.nextInt(3) == 0 ? 0x120C08 : 0x0B0806);
            }
        }
        int tiers = Math.max(5, height / 9);
        int crown = height - trunk;
        for (int y = 0; y < crown; y++) {
            double f = y / (double) crown;
            double tierPos = (f * tiers) % 1.0;
            double edge = (1 - f) * (0.45 + 0.55 * (1 - tierPos));
            int wy = (int) Math.round(halfW * edge);
            for (int dx = -wy; dx <= wy; dx++) {
                int n = rnd.nextInt(6);
                int c = n == 0 ? 0x0C1A14 : n == 1 ? 0x040A08 : color;
                // jaśniejsza krawędź od strony latarni (prawa)
                if (dx > wy - 2 && n < 3) {
                    c = 0x12281F;
                }
                px(im, cx + dx, baseY - trunk - y, c);
            }
        }
    }

    private static void house(BufferedImage im, Random rnd, int x0, int groundY, int w, int h, int lw, int lh) {
        int wallTop = groundY - h;
        int baseH = Math.max(3, h / 4);
        for (int y = wallTop; y <= groundY; y++) {
            for (int x = x0; x < x0 + w; x++) {
                boolean base = y > groundY - baseH;
                int n = rnd.nextInt(4);
                int c = base ? (n == 0 ? 0x2E3438 : n == 1 ? 0x22282C : 0x292F33)
                        : (n == 0 ? 0x3B2A1C : n == 1 ? 0x2D2015 : 0x35261A);
                if (!base && (y - wallTop) % 3 == 0) {
                    c = mix(c, 0x000000, 0.32);
                }
                if (x == x0 || x == x0 + 1) {
                    c = mix(c, 0x000000, 0.35);
                }
                px(im, x, y, c);
            }
        }
        // dach
        int roofH = Math.max(4, h / 3);
        for (int r = 0; r < roofH; r++) {
            int inset = (int) (r * 1.15);
            for (int x = x0 - 3 + inset; x < x0 + w + 3 - inset; x++) {
                int n = rnd.nextInt(4);
                int c = mix(n == 0 ? 0x1F1814 : 0x17110E, 0x0A0705, r / (double) roofH * 0.6);
                if (r == 0) {
                    c = mix(c, 0x4A3A2E, 0.35);
                }
                px(im, x, wallTop - 1 - r, c);
            }
        }
        // okno
        int wx = x0 + (int) (w * 0.58);
        int wy = wallTop + (int) (h * 0.22);
        int ww = Math.max(4, (int) (w * 0.14));
        int wh = Math.max(5, (int) (h * 0.30));
        for (int y = wy - 1; y <= wy + wh; y++) {
            for (int x = wx - 1; x <= wx + ww; x++) {
                boolean frame = y == wy - 1 || y == wy + wh || x == wx - 1 || x == wx + ww;
                px(im, x, y, frame ? 0x4A3626 : 0x0A1311);
            }
        }
        // latarnia
        int lx = (int) (LANTERN_X * lw);
        int ly = (int) (LANTERN_Y * lh);
        px(im, lx, ly - 4, 0x1A1410);
        px(im, lx, ly - 3, 0x1A1410);
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                px(im, lx + dx, ly + dy, dy == 0 ? 0xE6FFF1 : 0x5CF2A6);
            }
        }
    }

    /** Miniatury kart wiadomości: 0 = zielone kryształy w skale, 1 = wioska nocą. */
    private static BufferedImage thumb(int kind, int w, int h) {
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Random rnd = new Random(kind * 77L + 5);
        if (kind == 0) {
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int c = mix(0x0B1512, 0x1B2B26, rnd.nextDouble() * 0.7);
                    if ((x / 5 + y / 5) % 2 == 0) {
                        c = mix(c, 0x000000, 0.18);
                    }
                    im.setRGB(x, y, c);
                }
            }
            double[][] cs = {{0.30, 0.62, 9}, {0.58, 0.78, 7}, {0.72, 0.50, 11}, {0.14, 0.80, 6}, {0.46, 0.38, 6}};
            for (double[] c : cs) {
                int cx = (int) (c[0] * w);
                int cy = (int) (c[1] * h);
                int len = (int) c[2];
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        double d = Math.hypot(x - cx, y - cy) / (len * 1.8);
                        if (d < 1) {
                            im.setRGB(x, y, mix(im.getRGB(x, y) & 0xFFFFFF, 0x19E68C, Math.pow(1 - d, 2) * 0.55));
                        }
                    }
                }
                for (int i = -1; i <= 1; i++) {
                    int l = len - Math.abs(i) * 3;
                    for (int k = 0; k < l; k++) {
                        int half = Math.max(0, (int) ((1 - k / (double) l) * 1.6));
                        for (int dx = -half; dx <= half; dx++) {
                            px(im, cx + i * 3 + dx, cy - k + Math.abs(i) * 2, k > l / 2 ? 0xB8FFDD : (dx < 0 ? 0x3DF0A0 : 0x17B872));
                        }
                    }
                }
            }
        } else {
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    im.setRGB(x, y, mix(0x0E1D24, 0x213A40, y / (double) h * 0.8 + rnd.nextDouble() * 0.12));
                }
            }
            for (int i = 0; i < 11; i++) {
                int bx = 2 + rnd.nextInt(w - 8);
                int by = 6 + rnd.nextInt(h - 12);
                int bw = 4 + rnd.nextInt(4);
                int bhh = 3 + rnd.nextInt(3);
                for (int y = by; y < by + bhh; y++) {
                    for (int x = bx; x < bx + bw; x++) {
                        px(im, x, y, rnd.nextInt(3) == 0 ? 0x6A4A31 : 0x4D3524);
                    }
                }
                for (int x = bx - 1; x <= bx + bw; x++) {
                    px(im, x, by - 1, 0x1D1410);
                }
                px(im, bx + 1, by + 1, 0xFFB35A);
                if (bw > 5) {
                    px(im, bx + bw - 2, by + 1, 0xFFC878);
                }
            }
            for (int i = 0; i < 6; i++) {
                int x = rnd.nextInt(w);
                int y = rnd.nextInt(h);
                px(im, x, y, 0xFFB35A);
            }
        }
        return im;
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

    private void loadFriendSkins() {
        for (String f : friends()) {
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

    private void openFriendsDialog() {
        JPanel body = new JPanel(new BorderLayout(0, 12));
        JDialog d = makeDialog("Znajomi", body, 440, 440);
        JTextField nickField = field("");
        JLabel err = new JLabel(" ");
        err.setForeground(new Color(0xFF6B5A));
        err.setFont(body(Font.PLAIN, 12));
        FlatButton add = new FlatButton("Dodaj", true);
        JPanel addRow = new JPanel(new BorderLayout(10, 0));
        addRow.setOpaque(false);
        addRow.add(nickField, BorderLayout.CENTER);
        addRow.add(add, BorderLayout.EAST);
        JPanel head = new JPanel(new BorderLayout(0, 4));
        head.setOpaque(false);
        head.add(addRow, BorderLayout.NORTH);
        head.add(err, BorderLayout.SOUTH);

        JPanel list = new JPanel();
        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.add(list, BorderLayout.NORTH);
        JScrollPane scroll = darkScroll(wrap);
        scroll.getViewport().setOpaque(false);

        Runnable[] refresh = new Runnable[1];
        refresh[0] = () -> {
            list.removeAll();
            List<String> fs = friends();
            for (String f : fs) {
                JPanel row = new JPanel(new BorderLayout(10, 0)) {
                    @Override
                    protected void paintComponent(Graphics g0) {
                        Graphics2D g = (Graphics2D) g0.create();
                        aa(g);
                        card(g, 0, 0, getWidth(), getHeight(), 10, false);
                        Graphics2D gh = (Graphics2D) g.create();
                        gh.clip(new RoundRectangle2D.Double(10, 8, 30, 30, 8, 8));
                        BufferedImage sk = friendSkins.get(f);
                        drawHead(gh, sk == NO_SKIN ? null : sk, 10, 8, 30);
                        gh.dispose();
                        g.dispose();
                    }
                };
                row.setOpaque(false);
                row.setBorder(new EmptyBorder(0, 52, 0, 8));
                row.setPreferredSize(new Dimension(10, 46));
                row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
                JLabel l = new JLabel(f);
                l.setForeground(TEXT);
                l.setFont(body(Font.BOLD, 15));
                FlatButton rm = new FlatButton("Usuń", false);
                rm.setBorder(new EmptyBorder(6, 14, 6, 14));
                rm.addActionListener(e -> {
                    List<String> cur = friends();
                    cur.remove(f);
                    saveFriends(cur);
                    refresh[0].run();
                });
                row.add(l, BorderLayout.CENTER);
                row.add(rm, BorderLayout.EAST);
                list.add(row);
                list.add(javax.swing.Box.createVerticalStrut(6));
            }
            if (fs.isEmpty()) {
                JLabel none = new JLabel("Nie masz jeszcze znajomych. Wpisz nick gracza i kliknij Dodaj.");
                none.setForeground(MUTED);
                none.setFont(body(Font.PLAIN, 13));
                list.add(none);
            }
            list.revalidate();
            list.repaint();
        };
        add.addActionListener(e -> {
            String n = nickField.getText().trim();
            List<String> cur = friends();
            if (!validNick(n)) {
                err.setText("Nick: 3-16 znaków (litery, cyfry, _).");
            } else if (cur.stream().anyMatch(x -> x.equalsIgnoreCase(n))) {
                err.setText("Ten gracz już jest na liście.");
            } else if (cur.size() >= 30) {
                err.setText("Maksymalnie 30 znajomych.");
            } else {
                cur.add(n);
                err.setText(" ");
                nickField.setText("");
                saveFriends(cur);
                refresh[0].run();
            }
        });
        nickField.addActionListener(e -> add.doClick());
        refresh[0].run();

        FlatButton close = new FlatButton("Zamknij", false);
        close.addActionListener(e -> d.dispose());
        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        south.add(close, BorderLayout.EAST);
        body.add(head, BorderLayout.NORTH);
        body.add(scroll, BorderLayout.CENTER);
        body.add(south, BorderLayout.SOUTH);
        d.setVisible(true);
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

    private void applyAccount() {
        nick = msAccount != null ? msAccount.name() : "";
        skin = null;
        loadSkinAsync();
        if (sidebarUser != null) {
            sidebarUser.repaint();
        }
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
            JComponent info = new JComponent() {
                @Override
                protected void paintComponent(Graphics g0) {
                    Graphics2D g = (Graphics2D) g0.create();
                    aa(g);
                    card(g, 0, 0, getWidth(), getHeight(), 14, false);
                    drawHead(g, skin, 12, 14, 44);
                    g.setFont(body(Font.BOLD, 17));
                    g.setColor(TEXT);
                    g.drawString(fit(g, nick.isEmpty() ? "Gość" : nick, getWidth() - 90), 68, 34);
                    g.setFont(body(Font.PLAIN, 13));
                    g.setColor(MUTED);
                    g.drawString(isMicrosoft() ? "Konto Microsoft (premium)" : "Nie zalogowano", 68, 54);
                    g.dispose();
                }
            };
            info.setPreferredSize(new Dimension(10, 72));
            info.setMaximumSize(new Dimension(Integer.MAX_VALUE, 72));
            info.setAlignmentX(Component.LEFT_ALIGNMENT);
            account.add(info);
            account.add(javax.swing.Box.createVerticalStrut(16));

            if (isMicrosoft()) {
                account.add(wideButton("Zmień konto Microsoft", true, startLogin));
                account.add(javax.swing.Box.createVerticalStrut(8));
                account.add(wideButton("Wyloguj", false, () -> {
                    MicrosoftAuth.deleteAccount(baseDir);
                    msAccount = null;
                    applyAccount();
                    build[0].run();
                }));
            } else {
                account.add(htmlLabel("Do gry potrzebne jest konto Microsoft z Minecraftem Java Edition.",
                        MUTED, 13, Font.PLAIN));
                account.add(javax.swing.Box.createVerticalStrut(12));
                account.add(wideButton("Zaloguj przez Microsoft", true, startLogin));
            }
            account.add(javax.swing.Box.createVerticalStrut(10));
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
                MicrosoftAuth.Login l = MicrosoftAuth.refresh(cid, acc, this::appendLog);
                MicrosoftAuth.Account fresh = new MicrosoftAuth.Account(l.name(), l.uuid(), l.refreshToken());
                MicrosoftAuth.saveAccount(baseDir, fresh);
                msAccount = fresh;
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
                hero.repaint();
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
