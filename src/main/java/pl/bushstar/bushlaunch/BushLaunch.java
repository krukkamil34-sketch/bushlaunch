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
 * Wygląd: ciemny launcher w stylu Fire Client (pasek boczny, scena ze skinem, duży przycisk GRAJ).
 */
public class BushLaunch {

    public static final String LAUNCHER_VERSION = "0.7.0";

    /** Link do Discorda BushStar - podmień na swój zaproszeniowy. */
    private static final String DISCORD_URL = "https://discord.gg/";

    // ------------------------------------------------------------------ kolory i fonty

    static final Color BG = new Color(0x0C0807);
    static final Color SIDEBAR = new Color(0x130C0A);
    static final Color PANEL = new Color(0x1B120E);
    static final Color PANEL_HI = new Color(0x26180F);
    static final Color ACCENT = new Color(0xE8590C);
    static final Color ACCENT_HI = new Color(0xFF7A1F);
    static final Color ACCENT_DIM = new Color(0x7A330E);
    static final Color TEXT = new Color(0xF4EEEA);
    static final Color MUTED = new Color(0x9C8F89);
    static final Color BORDER = new Color(0x34251D);
    static final Color GLOW = new Color(0xFF8A3D);
    static final Color DISCORD = new Color(0x5865F2);

    private static final String HEAD_FONT = pickFont("Bahnschrift", "Segoe UI", "Arial");
    private static final String BODY_FONT = pickFont("Segoe UI", "Arial");

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

    static Font head(int style, float size) {
        return new Font(HEAD_FONT, Font.PLAIN, 12).deriveFont(style, size);
    }

    static Font body(int style, float size) {
        return new Font(BODY_FONT, Font.PLAIN, 12).deriveFont(style, size);
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
        while (t.length() > 1 && fm.stringWidth(t + "\u2026") > maxW) {
            t = t.substring(0, t.length() - 1);
        }
        return t.trim() + "\u2026";
    }

    static Color alpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
    }

    /** Karta w stylu launchera: gradient, jasna krawędź u góry, ścięty róg. */
    static void card(Graphics2D g, int x, int y, int w, int h, int c, boolean hot) {
        Shape sh = cut(x, y, w, h, c);
        g.setPaint(new GradientPaint(0, y, hot ? new Color(0x2C1A11) : new Color(0x1F1511),
                0, y + h, hot ? new Color(0x1F120C) : new Color(0x150E0B)));
        g.fill(sh);
        g.setStroke(new BasicStroke(1f));
        g.setColor(hot ? alpha(ACCENT, 170) : BORDER);
        g.draw(cut(x + 0.5, y + 0.5, w - 1, h - 1, c));
        g.setPaint(new GradientPaint(x, y, alpha(Color.WHITE, 26), x + w, y, alpha(Color.WHITE, 0)));
        g.fillRect(x + 1, y + 1, Math.max(0, w - c - 2), 1);
    }

    /** Prostokąt z ściętym prawym górnym rogiem (jak w Fire Client). */
    static Shape cut(double x, double y, double w, double h, double c) {
        Path2D p = new Path2D.Double();
        p.moveTo(x, y);
        p.lineTo(x + w - c, y);
        p.lineTo(x + w, y + c);
        p.lineTo(x + w, y + h);
        p.lineTo(x, y + h);
        p.closePath();
        return p;
    }

    /** Hero: ścięty prawy górny i lewy dolny róg. */
    static Shape cut2(double x, double y, double w, double h, double c) {
        Path2D p = new Path2D.Double();
        p.moveTo(x, y);
        p.lineTo(x + w - c, y);
        p.lineTo(x + w, y + c);
        p.lineTo(x + w, y + h);
        p.lineTo(x + c, y + h);
        p.lineTo(x, y + h - c);
        p.closePath();
        return p;
    }

    static Path2D flame(double x, double y, double s) {
        Path2D p = new Path2D.Double();
        p.moveTo(0.50, 0.00);
        p.curveTo(0.58, 0.25, 0.97, 0.38, 0.92, 0.70);
        p.curveTo(0.88, 0.95, 0.14, 0.97, 0.09, 0.70);
        p.curveTo(0.06, 0.50, 0.24, 0.40, 0.30, 0.24);
        p.curveTo(0.40, 0.36, 0.46, 0.18, 0.50, 0.00);
        p.closePath();
        AffineTransform at = new AffineTransform();
        at.translate(x, y);
        at.scale(s, s);
        p.transform(at);
        return p;
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
    private JLabel lockerName;

    private boolean loadingVersions = true;
    private boolean busy;
    private boolean gameRunning;
    private boolean indeterminate;
    private int progDone;
    private int progTotal;
    private String progWhat = "";
    private int modsCount;

    private BufferedImage skin;

    private JTextArea logArea;
    private CardLayout cards;
    private JPanel pages;
    private final Map<String, NavButton> navs = new LinkedHashMap<>();
    private Hero hero;
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
        frame.setMinimumSize(new Dimension(1180, 700));
        frame.setSize(1360, 800);
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
        pages.add(buildLockerPage(), "locker");
        pages.add(buildSoon("Sklep", "Sklep BushStar pojawi się tutaj.", 4), "shop");
        pages.add(buildSoon("Nagroda", "Codzienne nagrody dla graczy BushStar.", 5), "reward");
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
        aa(g);
        g.setColor(ACCENT);
        g.fill(flame(8, 4, 48));
        g.setColor(new Color(0xFFB347));
        g.fill(flame(22, 26, 20));
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
                g.setColor(new Color(0x0B0706));
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(ACCENT);
                g.fill(flame(12, 9, 16));
                g.setFont(body(Font.PLAIN, 13));
                g.setColor(TEXT);
                g.drawString("BushLaunch", 38, 21);
                g.dispose();
            }
        };
        bar.setPreferredSize(new Dimension(10, 34));

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
                g.setPaint(new GradientPaint(0, 0, new Color(0x160D0A), 0, getHeight(), new Color(0x0F0907)));
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(BORDER);
                g.fillRect(getWidth() - 1, 0, 1, getHeight());
                g.dispose();
            }
        };
        side.setOpaque(false);
        side.setPreferredSize(new Dimension(236, 10));

        JComponent logo = new JComponent() {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                aa(g);
                g.setPaint(new RadialGradientPaint(41f, 48f, 34f,
                        new float[]{0f, 1f}, new Color[]{alpha(GLOW, 90), alpha(GLOW, 0)}));
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setPaint(new GradientPaint(0, 24, ACCENT_HI, 0, 58, new Color(0xC2410C)));
                g.fill(flame(24, 24, 34));
                g.setColor(new Color(0xFFD08A));
                g.fill(flame(33, 43, 16));
                g.setFont(head(Font.BOLD, 23));
                g.setColor(ACCENT_HI);
                g.drawString("Bush", 66, 51);
                int w = g.getFontMetrics().stringWidth("Bush");
                g.setColor(TEXT);
                g.setFont(head(Font.PLAIN, 23));
                g.drawString("Launch", 66 + w + 3, 51);
                g.dispose();
            }
        };
        logo.setPreferredSize(new Dimension(10, 92));

        JPanel nav = new JPanel();
        nav.setOpaque(false);
        nav.setLayout(new BoxLayout(nav, BoxLayout.Y_AXIS));
        addNav(nav, "home", "Graj", 0);
        addNav(nav, "profiles", "Moje profile", 1);
        addNav(nav, "mods", "Mody", 2);
        addNav(nav, "locker", "Szafka", 3);
        addNav(nav, "shop", "Sklep", 4);
        addNav(nav, "reward", "Nagroda", 5);
        addNav(nav, "console", "Konsola", 6);

        JPanel bottom = new JPanel();
        bottom.setOpaque(false);
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
        bottom.setBorder(new EmptyBorder(0, 0, 6, 0));

        Clickable user = new Clickable(this::openAccountDialog) {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                aa(g);
                int w = getWidth() - 28;
                g.translate(14, 4);
                card(g, 0, 0, w, getHeight() - 8, 14, hover);
                drawHead(g, skin, 12, 11, 42);
                g.setFont(body(Font.BOLD, 15));
                g.setColor(TEXT);
                g.drawString(nick.isEmpty() ? "Zaloguj się" : nick, 64, 29);
                g.setFont(body(Font.PLAIN, 12));
                g.setColor(busy ? ACCENT_HI : MUTED);
                g.drawString(busy ? "Przygotowanie..." : (isMicrosoft() ? "Konto Microsoft" : "Nie zalogowano"), 64, 47);
                g.setColor(MUTED);
                g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int cx = w - 18;
                g.drawLine(cx - 3, 26, cx + 2, 31);
                g.drawLine(cx + 2, 31, cx - 3, 36);
                g.dispose();
            }
        };
        user.setPreferredSize(new Dimension(220, 72));
        user.setMaximumSize(new Dimension(Integer.MAX_VALUE, 72));
        user.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebarUser = user;

        Clickable discord = new Clickable(() -> openUrl(DISCORD_URL)) {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                aa(g);
                int w = getWidth() - 28;
                g.translate(14, 2);
                g.setPaint(new GradientPaint(0, 0, hover ? new Color(0x7480FF) : new Color(0x5865F2),
                        0, 44, hover ? new Color(0x5865F2) : new Color(0x4752C4)));
                g.fill(new RoundRectangle2D.Double(0, 0, w, 44, 14, 14));
                g.setColor(Color.WHITE);
                g.fill(new RoundRectangle2D.Double(16, 13, 22, 17, 8, 8));
                g.setColor(new Color(0x5865F2));
                g.fillOval(21, 18, 4, 5);
                g.fillOval(29, 18, 4, 5);
                g.setColor(Color.WHITE);
                g.setFont(body(Font.BOLD, 15));
                g.drawString("Discord", 50, 28);
                g.dispose();
            }
        };
        discord.setPreferredSize(new Dimension(220, 50));
        discord.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
        discord.setAlignmentX(Component.LEFT_ALIGNMENT);

        JComponent version = new JComponent() {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                aa(g);
                g.setFont(body(Font.PLAIN, 11));
                g.setColor(alpha(MUTED, 170));
                g.drawString("BushLaunch v" + LAUNCHER_VERSION + "  ·  by BushStar", 18, 16);
                g.dispose();
            }
        };
        version.setPreferredSize(new Dimension(220, 24));
        version.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        version.setAlignmentX(Component.LEFT_ALIGNMENT);

        bottom.add(user);
        bottom.add(discord);
        bottom.add(version);

        side.add(logo, BorderLayout.NORTH);
        side.add(nav, BorderLayout.CENTER);
        side.add(bottom, BorderLayout.SOUTH);
        return side;
    }

    private void addNav(JPanel nav, String key, String label, int icon) {
        NavButton b = new NavButton(key, label, icon);
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

        NavButton(String key, String label, int icon) {
            super(null);
            this.label = label;
            this.icon = icon;
            this.onClick = () -> showPage(key);
            setPreferredSize(new Dimension(220, 54));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 54));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            aa(g);
            int hh = getHeight() - 8;
            if (active) {
                g.setPaint(new GradientPaint(10, 0, alpha(ACCENT, 70), getWidth() - 10, 0, alpha(ACCENT, 8)));
                g.fill(new RoundRectangle2D.Double(10, 4, getWidth() - 20, hh, 14, 14));
                g.setColor(ACCENT_HI);
                g.fill(new RoundRectangle2D.Double(10, 12, 4, hh - 16, 4, 4));
            } else if (hover) {
                g.setColor(alpha(Color.WHITE, 12));
                g.fill(new RoundRectangle2D.Double(10, 4, getWidth() - 20, hh, 14, 14));
            }
            Color c = active ? ACCENT_HI : hover ? TEXT : MUTED;
            icon(g, icon, 30, 17, 22, c);
            g.setColor(active || hover ? TEXT : MUTED);
            g.setFont(body(active ? Font.BOLD : Font.PLAIN, 16));
            g.drawString(label, 66, 34);
            g.dispose();
        }
    }

    // ------------------------------------------------------------------ strona główna

    private JComponent buildHome() {
        JPanel home = new JPanel(new BorderLayout(18, 0));
        home.setBackground(BG);
        home.setBorder(new EmptyBorder(20, 22, 20, 22));

        hero = new Hero();

        JPanel recent = new JPanel(new BorderLayout(0, 10));
        recent.setOpaque(false);
        recent.setPreferredSize(new Dimension(10, 132));
        recent.setBorder(new EmptyBorder(14, 0, 0, 0));

        JPanel recentHead = new JPanel(new BorderLayout());
        recentHead.setOpaque(false);
        JLabel t = new JLabel("Ostatnie profile");
        t.setForeground(TEXT);
        t.setFont(head(Font.BOLD, 22));
        Clickable all = new Clickable(() -> showPage("profiles")) {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                aa(g);
                g.setColor(hover ? ACCENT_HI : TEXT);
                g.setFont(body(Font.PLAIN, 14));
                String s = "Wszystkie profile";
                g.drawString(s, 0, 18);
                int x = g.getFontMetrics().stringWidth(s) + 10;
                g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawLine(x, 13, x + 10, 13);
                g.drawLine(x + 6, 9, x + 10, 13);
                g.drawLine(x + 6, 17, x + 10, 13);
                g.dispose();
            }
        };
        all.setPreferredSize(new Dimension(170, 26));
        recentHead.add(t, BorderLayout.WEST);
        recentHead.add(all, BorderLayout.EAST);

        recentCards = new JPanel(new GridLayout(1, 3, 14, 0));
        recentCards.setOpaque(false);

        recent.add(recentHead, BorderLayout.NORTH);
        recent.add(recentCards, BorderLayout.CENTER);

        JPanel left = new JPanel(new BorderLayout());
        left.setOpaque(false);
        left.add(hero, BorderLayout.CENTER);
        left.add(recent, BorderLayout.SOUTH);

        JPanel col = new JPanel(new BorderLayout(0, 16));
        col.setOpaque(false);
        col.setPreferredSize(new Dimension(300, 10));

        InfoCard news = new InfoCard("Nowości", 10, new String[][]{
                {"v" + LAUNCHER_VERSION, "Logowanie kontem Microsoft"},
                {"Profile", "Własne profile i zmiana ich wersji gry"},
                {"Wersje", "Wszystkie wydania i snapshoty Mojang"},
                {"Premium", "Wymagane konto Microsoft"},
                {"Wkrótce", "Fabric, Forge i skiny"}});

        JPanel actions = new JPanel(new GridLayout(3, 1, 0, 10));
        actions.setOpaque(false);
        actions.setPreferredSize(new Dimension(10, 214));
        actions.setBorder(new EmptyBorder(0, 0, 0, 0));
        actions.add(new ActionRow("Folder gry", "Otwórz katalog z grą", 9,
                () -> openFolder(installer.gameDir())));
        actions.add(new ActionRow("Ustawienia", "RAM i foldery launchera", 7, this::openSettings));
        actions.add(new ActionRow("Konsola", "Log launchera i gry", 6, () -> showPage("console")));

        col.add(news, BorderLayout.CENTER);
        col.add(actions, BorderLayout.SOUTH);

        home.add(left, BorderLayout.CENTER);
        home.add(col, BorderLayout.EAST);
        return home;
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
            int step = Math.max(70, Math.min(96, (getHeight() - 90) / Math.max(1, rows.length)));
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
                g.drawString(r[1], 38, y + 24);
                y += step;
            }
            g.dispose();
        }
    }

    /** Wiersz szybkiej akcji w prawej kolumnie. */
    private static final class ActionRow extends Clickable {
        private final String title;
        private final String sub;
        private final int icon;

        ActionRow(String title, String sub, int icon, Runnable onClick) {
            super(onClick);
            this.title = title;
            this.sub = sub;
            this.icon = icon;
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            aa(g);
            card(g, 0, 0, getWidth(), getHeight(), 16, hover);
            g.setColor(alpha(ACCENT, hover ? 70 : 38));
            g.fill(new RoundRectangle2D.Double(14, (getHeight() - 38) / 2.0, 38, 38, 12, 12));
            icon(g, icon, 22, (getHeight() - 22) / 2, 22, hover ? Color.WHITE : ACCENT_HI);
            g.setColor(TEXT);
            g.setFont(body(Font.BOLD, 15));
            g.drawString(title, 66, getHeight() / 2 - 2);
            g.setColor(MUTED);
            g.setFont(body(Font.PLAIN, 12));
            g.drawString(sub, 66, getHeight() / 2 + 16);
            g.setColor(hover ? ACCENT_HI : MUTED);
            g.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int cx = getWidth() - 24;
            int cy = getHeight() / 2;
            g.drawLine(cx - 3, cy - 5, cx + 2, cy);
            g.drawLine(cx + 2, cy, cx - 3, cy + 5);
            g.dispose();
        }
    }

    // ------------------------------------------------------------------ hero (scena + przycisk GRAJ)

    private final class Hero extends JComponent {
        private BufferedImage bg;
        private int bgW;
        private int bgH;
        private final Rectangle gearRect = new Rectangle();
        private final Rectangle verRect = new Rectangle();
        private Polygon playPoly = new Polygon();
        private boolean hoverPlay;
        private boolean hoverGear;
        private boolean hoverVer;
        private final long t0 = System.nanoTime();
        /** Iskry: x (0-1), prędkość, rozmiar, faza, kołysanie. */
        private final double[][] embers = new double[46][5];

        Hero() {
            Random er = new Random(7);
            for (double[] e : embers) {
                e[0] = er.nextDouble();
                e[1] = 0.025 + er.nextDouble() * 0.05;
                e[2] = 1.4 + er.nextDouble() * 2.6;
                e[3] = er.nextDouble();
                e[4] = er.nextDouble() * Math.PI * 2;
            }
            Timer anim = new Timer(40, e -> {
                if (isShowing()) {
                    repaint();
                }
            });
            anim.start();

            MouseAdapter m = new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    boolean p = playPoly.contains(e.getPoint());
                    boolean gr = gearRect.contains(e.getPoint());
                    boolean v = verRect.contains(e.getPoint());
                    if (p != hoverPlay || gr != hoverGear || v != hoverVer) {
                        hoverPlay = p;
                        hoverGear = gr;
                        hoverVer = v;
                        setCursor(Cursor.getPredefinedCursor(p || gr || v ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                        repaint();
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hoverPlay = false;
                    hoverGear = false;
                    hoverVer = false;
                    setCursor(Cursor.getDefaultCursor());
                    repaint();
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (playPoly.contains(e.getPoint())) {
                        onPlay();
                    } else if (gearRect.contains(e.getPoint())) {
                        openSettings();
                    } else if (verRect.contains(e.getPoint())) {
                        openVersionPicker();
                    }
                }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            aa(g);
            int w = getWidth();
            int h = getHeight();
            int barH = 110;
            int barY = h - barH;

            g.setClip(cut2(0, 0, w, h, 26));

            if (bg == null || bgW != w || bgH != h) {
                bg = landscape(w, h);
                bgW = w;
                bgH = h;
            }
            g.drawImage(bg, 0, 0, w, h, null);

            // przyciemnienie u góry i płynne przejście do dolnego paska
            g.setPaint(new GradientPaint(0, 0, new Color(0, 0, 0, 60), 0, 90, new Color(0, 0, 0, 0)));
            g.fillRect(0, 0, w, 90);
            g.setPaint(new GradientPaint(0, barY - 130, alpha(PANEL, 0), 0, barY, PANEL));
            g.fillRect(0, barY - 130, w, 130);
            g.setColor(PANEL);
            g.fillRect(0, barY, w, barH);
            g.setPaint(new GradientPaint(0, 0, alpha(ACCENT, 0), w / 2f, 0, alpha(ACCENT, 200)));
            g.fillRect(0, barY, w / 2, 2);
            g.setPaint(new GradientPaint(w / 2f, 0, alpha(ACCENT, 200), w, 0, alpha(ACCENT, 0)));
            g.fillRect(w / 2, barY, w - w / 2, 2);

            double time = (System.nanoTime() - t0) / 1e9;

            // postać
            double availH = barY;
            double unit = Math.max(4, availH * 0.66 / 32.0);
            double top = (availH - 32 * unit) / 2 + 30;

            // ciepła poświata za postacią
            float gr = (float) (availH * 0.62);
            float breathe = (float) (1 + 0.04 * Math.sin(time * 1.6));
            g.setPaint(new RadialGradientPaint(w / 2f, (float) (top + 16 * unit), gr * breathe,
                    new float[]{0f, 1f}, new Color[]{alpha(GLOW, 80), alpha(GLOW, 0)}));
            g.fillRect(0, 0, w, barY);

            // delikatny cień pod postacią
            g.setColor(alpha(Color.BLACK, 70));
            g.fill(new Ellipse2D.Double(w / 2.0 - unit * 7, top + 32 * unit - unit * 0.6, unit * 14, unit * 1.6));

            drawFigure(g, skin, w / 2.0, top, unit);

            // unoszące się iskry
            for (double[] e : embers) {
                double ph = (time * e[1] + e[3]) % 1.0;
                double ex = e[0] * w + Math.sin(time * 0.9 + e[4]) * 14;
                double ey = barY - ph * (barY + 20);
                int a = (int) (170 * Math.sin(Math.PI * ph));
                double sz = e[2];
                g.setColor(alpha(GLOW, Math.max(0, a / 4)));
                g.fill(new Ellipse2D.Double(ex - sz * 2, ey - sz * 2, sz * 4, sz * 4));
                g.setColor(alpha(new Color(0xFFD9A0), Math.max(0, a)));
                g.fill(new Ellipse2D.Double(ex - sz / 2, ey - sz / 2, sz, sz));
            }

            // winieta
            g.setPaint(new RadialGradientPaint(w / 2f, barY * 0.5f, Math.max(w, barY) * 0.75f,
                    new float[]{0.55f, 1f}, new Color[]{alpha(Color.BLACK, 0), alpha(Color.BLACK, 120)}));
            g.fillRect(0, 0, w, barY);

            // tabliczka z nickiem
            String tag = nick.isEmpty() ? "Zaloguj się" : nick;
            g.setFont(head(Font.BOLD, 26));
            FontMetrics fm = g.getFontMetrics();
            int tw = fm.stringWidth(tag) + 44;
            int tx = (int) (w / 2.0 - tw / 2.0);
            int ty = (int) (top - 58);
            g.setColor(new Color(0x0C, 0x08, 0x07, 200));
            g.fill(cut(tx, ty, tw, 44, 12));
            g.setColor(ACCENT);
            g.fillRect(tx, ty + 40, tw, 3);
            g.setColor(TEXT);
            g.drawString(tag, tx + 22, ty + 31);

            // dolny pasek: ustawienia, wersja
            int by = barY + (barH - 64) / 2;
            gearRect.setBounds(28, by, 50, 64);
            g.setPaint(new GradientPaint(0, gearRect.y, hoverGear ? new Color(0x3A2518) : new Color(0x2C1B12),
                    0, gearRect.y + gearRect.height, new Color(0x201410)));
            g.fill(cut(gearRect.x, gearRect.y, gearRect.width, gearRect.height, 10));
            icon(g, 7, gearRect.x + 14, gearRect.y + 21, 22, hoverGear ? TEXT : MUTED);

            verRect.setBounds(88, by, 212, 64);
            g.setPaint(new GradientPaint(0, verRect.y, hoverVer ? new Color(0x3A2518) : new Color(0x2C1B12),
                    0, verRect.y + verRect.height, new Color(0x201410)));
            g.fill(cut(verRect.x, verRect.y, verRect.width, verRect.height, 10));
            Profile ap = activeProfile();
            String vid = ap != null ? ap.name : "Brak profilu";
            String vsub = selected == null ? (loadingVersions ? "Wczytywanie..." : "Wybierz wersję")
                    : selected.id() + "  \u00b7  " + typeLabel(selected.type());
            if (selected != null) {
                drawBlock(g, verRect.x + 10, verRect.y + 12, 40, selected.id().hashCode());
            }
            int textX = verRect.x + (selected != null ? 60 : 14);
            int textMax = verRect.x + verRect.width - 34 - textX;
            g.setFont(head(Font.BOLD, 19));
            g.setColor(TEXT);
            g.drawString(fit(g, vid, textMax), textX, verRect.y + 28);
            g.setFont(body(Font.PLAIN, 13));
            g.setColor(MUTED);
            g.drawString(fit(g, vsub, textMax), textX, verRect.y + 48);
            g.setColor(MUTED);
            g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int cx = verRect.x + verRect.width - 20;
            g.drawLine(cx - 5, verRect.y + 28, cx, verRect.y + 33);
            g.drawLine(cx, verRect.y + 33, cx + 5, verRect.y + 28);

            // statystyki
            boolean stats = w >= 720;
            int statsW = 160;
            int statsX = w - 28 - statsW;
            if (stats) {
                String[][] rows = {
                        {"RAM", formatGb(ramMb)},
                        {"Mody", String.valueOf(modsCount)},
                        {"Czas gry", formatPlaytime()}};
                int sy = barY + (barH - 3 * 34) / 2;
                for (int i = 0; i < rows.length; i++) {
                    int yy = sy + i * 34;
                    g.setFont(body(Font.PLAIN, 14));
                    g.setColor(MUTED);
                    g.drawString(rows[i][0], statsX, yy + 22);
                    g.setFont(head(Font.BOLD, 20));
                    g.setColor(TEXT);
                    g.drawString(rows[i][1], statsX + statsW - g.getFontMetrics().stringWidth(rows[i][1]), yy + 23);
                    g.setColor(BORDER);
                    g.drawLine(statsX, yy + 30, statsX + statsW, yy + 30);
                }
            }

            // przycisk GRAJ
            int availX = verRect.x + verRect.width + 24;
            int availEnd = (stats ? statsX : w - 28) - 24;
            int pw = Math.max(200, Math.min(310, availEnd - availX));
            int px = availX + Math.max(0, (availEnd - availX - pw) / 2);
            int ph = 88;
            int py = barY + (barH - ph) / 2 - 4;
            int skew = 24;
            playPoly = new Polygon(
                    new int[]{px + skew, px + pw, px + pw - skew, px},
                    new int[]{py, py, py + ph, py + ph}, 4);

            boolean disabled = busy || loadingVersions;
            if (!disabled || (busy && !gameRunning)) {
                double pulse = 0.5 + 0.5 * Math.sin(time * 2.4);
                int gl = disabled ? 10 : (int) (14 + 8 * pulse + (hoverPlay ? 8 : 0));
                for (int i = gl; i >= 2; i -= 2) {
                    g.setColor(alpha(GLOW, (int) ((disabled ? 6 : 12) * (1 - i / (double) (gl + 2)) + 2)));
                    g.setStroke(new BasicStroke(i * 2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g.draw(playPoly);
                }
                g.setStroke(new BasicStroke(1f));
            }
            Shape oldClip = g.getClip();
            g.clip(playPoly);
            if (busy && !gameRunning && !indeterminate && progTotal > 0) {
                g.setColor(ACCENT_DIM);
                g.fillRect(px, py, pw, ph);
                g.setPaint(new GradientPaint(px, py, ACCENT_HI, px, py + ph, ACCENT));
                g.fillRect(px, py, (int) (pw * (progDone / (double) progTotal)), ph);
            } else if (disabled) {
                g.setColor(ACCENT_DIM);
                g.fillRect(px, py, pw, ph);
            } else {
                g.setPaint(new GradientPaint(px, py, hoverPlay ? ACCENT_HI : ACCENT.brighter(),
                        px, py + ph, hoverPlay ? ACCENT : ACCENT.darker()));
                g.fillRect(px, py, pw, ph);
                // połysk przesuwający się po przycisku
                double sh = ((time * 0.35) % 2.2) - 0.6;
                int sx = (int) (px + sh * pw);
                g.setPaint(new GradientPaint(sx, 0, alpha(Color.WHITE, 0), sx + 40, 0, alpha(Color.WHITE, 60)));
                g.fillRect(sx, py, 40, ph);
                g.setPaint(new GradientPaint(sx + 40, 0, alpha(Color.WHITE, 60), sx + 80, 0, alpha(Color.WHITE, 0)));
                g.fillRect(sx + 40, py, 40, ph);
                g.setPaint(new GradientPaint(0, py, alpha(Color.WHITE, 50), 0, py + ph / 2f, alpha(Color.WHITE, 0)));
                g.fillRect(px, py, pw, ph / 2);
            }
            g.setClip(oldClip);

            String label;
            String caption = null;
            if (gameRunning) {
                label = "GRA URUCHOMIONA";
            } else if (busy) {
                label = "PRZYGOTOWANIE...";
                if (!indeterminate && progTotal > 0) {
                    caption = progWhat + " " + progDone + "/" + progTotal;
                }
            } else if (loadingVersions) {
                label = "WCZYTYWANIE...";
            } else if (selected == null) {
                label = "WYBIERZ WERSJĘ";
            } else {
                label = "GRAJ";
            }
            boolean big = label.length() <= 5;
            g.setFont(head(Font.BOLD | Font.ITALIC, big ? 40 : 22));
            fm = g.getFontMetrics();
            int lx = px + (pw - fm.stringWidth(label)) / 2;
            int ly = py + ph / 2 + fm.getAscent() / 2 - 4 - (caption != null ? 8 : 0);
            g.setColor(disabled ? new Color(0xF0D2BF) : Color.WHITE);
            g.drawString(label, lx, ly);
            if (caption != null) {
                g.setFont(body(Font.PLAIN, 12));
                g.setColor(new Color(0xFFE2D0));
                FontMetrics cf = g.getFontMetrics();
                g.drawString(caption, px + (pw - cf.stringWidth(caption)) / 2, ly + 20);
            }
            g.dispose();
        }
    }

    // ------------------------------------------------------------------ scena (generowane tło w stylu Minecrafta)

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

    private static BufferedImage landscape(int w, int h) {
        final int s = 5;
        int lw = Math.max(8, w / s + 1);
        int lh = Math.max(8, h / s + 1);
        BufferedImage im = new BufferedImage(lw, lh, BufferedImage.TYPE_INT_RGB);
        Random rnd = new Random(42);
        int horizon = (int) (lh * 0.50);

        // niebo + słońce
        double sunX = lw * 0.17;
        double sunY = horizon * 0.80;
        for (int y = 0; y < lh; y++) {
            double t = Math.min(1.0, y / (double) horizon);
            int base = mix(0x3F7BC0, 0xF6BE7A, Math.pow(t, 1.4));
            for (int x = 0; x < lw; x++) {
                double d = Math.hypot(x - sunX, (y - sunY) * 1.2) / (lw * 0.38);
                int c = base;
                if (d < 1) {
                    c = mix(base, 0xFFE9A8, Math.pow(1 - d, 2) * 0.85);
                }
                im.setRGB(x, y, c);
            }
        }
        for (int dy = -3; dy <= 3; dy++) {
            for (int dx = -3; dx <= 3; dx++) {
                px(im, (int) sunX + dx, (int) sunY + dy, 0xFFF6CF);
            }
        }
        // chmury
        for (int i = 0; i < 7; i++) {
            int cx = rnd.nextInt(lw);
            int cy = 4 + rnd.nextInt(Math.max(1, (int) (horizon * 0.42)));
            int cw = 14 + rnd.nextInt(22);
            for (int r = 0; r < 3; r++) {
                int off = r == 1 ? 0 : 3;
                for (int x = off; x < cw - off; x++) {
                    int col = r == 2 ? 0xE9C99C : 0xFBE7C4;
                    px(im, cx + x, cy + r, col);
                }
            }
        }

        // góry
        mountains(im, rnd, horizon, lh * 0.34, 0x76899C, true, 0.9);
        mountains(im, rnd, horizon + 2, lh * 0.20, 0x4E6F5E, false, 2.1);

        // wzgórza z lasem
        for (int x = 0; x < lw; x++) {
            int top = horizon + 2 + (int) (4 * Math.sin(x * 0.06 + 1) + 3 * Math.sin(x * 0.17));
            for (int y = top; y < lh; y++) {
                int c = (rnd.nextInt(4) == 0) ? 0x2F6B24 : 0x3C7F2B;
                px(im, x, y, mix(c, 0x1E4A18, (y - top) / (double) lh * 0.5));
            }
        }
        for (int i = 0; i < lw / 3; i++) {
            int x = rnd.nextInt(lw);
            int top = horizon + 2 + (int) (4 * Math.sin(x * 0.06 + 1) + 3 * Math.sin(x * 0.17));
            tree(im, rnd, x, top + 3, 2 + rnd.nextInt(3), 3 + rnd.nextInt(2));
        }

        // pierwszy plan - trawa
        int groundTop = (int) (lh * 0.66);
        for (int x = 0; x < lw; x++) {
            int gy = groundTop + (int) (3 * Math.sin(x * 0.05) + 2 * Math.sin(x * 0.19 + 2));
            for (int y = gy; y < lh; y++) {
                int n = rnd.nextInt(5);
                int c = n == 0 ? 0x5CA033 : n == 1 ? 0x356E22 : n == 2 ? 0x4C8F2B : 0x417C27;
                px(im, x, y, mix(c, 0x1D3F14, (y - gy) / (double) Math.max(1, lh - gy) * 0.55));
            }
        }
        // bloki ziemi po bokach
        for (int x = 0; x < lw; x++) {
            double edge = Math.min(x, lw - 1 - x) / (double) lw;
            if (edge < 0.18) {
                int depth = (int) ((0.18 - edge) * lh * 1.1);
                int gy = groundTop + 6;
                for (int y = gy; y < gy + depth && y < lh; y++) {
                    int n = rnd.nextInt(4);
                    px(im, x, y, n == 0 ? 0x6B4A2C : n == 1 ? 0x7A5634 : 0x5E3F25);
                }
                for (int y = gy - 1; y <= gy; y++) {
                    px(im, x, y, 0x5CA033);
                }
            }
        }
        // duże drzewa po bokach
        bigTree(im, rnd, (int) (lw * 0.05), groundTop + 4, (int) (lh * 0.62));
        bigTree(im, rnd, (int) (lw * 0.94), groundTop + 2, (int) (lh * 0.5));
        return im;
    }

    private static void mountains(BufferedImage im, Random rnd, int horizon, double amp, int color,
                                  boolean snow, double freq) {
        double p1 = rnd.nextDouble() * 6;
        double p2 = rnd.nextDouble() * 6;
        double p3 = rnd.nextDouble() * 6;
        int lw = im.getWidth();
        for (int x = 0; x < lw; x++) {
            double u = x / (double) lw * freq * 6;
            double hgt = amp * (0.5 + 0.28 * Math.sin(u + p1) + 0.16 * Math.sin(u * 2.3 + p2)
                    + 0.08 * Math.sin(u * 5.1 + p3));
            int top = horizon - (int) hgt;
            for (int y = top; y < horizon + 4 && y < im.getHeight(); y++) {
                int c = color;
                int n = rnd.nextInt(5);
                if (n == 0) {
                    c = mix(color, 0x000000, 0.12);
                } else if (n == 1) {
                    c = mix(color, 0xFFFFFF, 0.08);
                }
                if (snow && hgt > amp * 0.62 && y < top + (int) ((hgt - amp * 0.62) * 0.55) + 1) {
                    c = rnd.nextInt(4) == 0 ? 0xD6DEE6 : 0xF1F5F8;
                }
                px(im, x, y, mix(c, 0xF1B880, Math.max(0, (y - (horizon - amp)) / (amp + 4)) * 0.25));
            }
        }
    }

    private static void tree(BufferedImage im, Random rnd, int x, int baseY, int trunk, int r) {
        for (int y = 0; y < trunk; y++) {
            px(im, x, baseY - y, 0x5A3F22);
        }
        for (int dy = -r; dy <= r; dy++) {
            for (int dx = -r - 1; dx <= r + 1; dx++) {
                if (dx * dx / 1.6 + dy * dy <= r * r + 1) {
                    int n = rnd.nextInt(3);
                    px(im, x + dx, baseY - trunk - r + dy + r / 2, n == 0 ? 0x2E7A22 : n == 1 ? 0x3F9130 : 0x266B1C);
                }
            }
        }
    }

    private static void bigTree(BufferedImage im, Random rnd, int x, int baseY, int height) {
        int tw = Math.max(5, im.getWidth() / 28);
        for (int y = 0; y < height; y++) {
            for (int dx = 0; dx < tw; dx++) {
                int n = rnd.nextInt(4);
                px(im, x + dx - tw / 2, baseY - y, n == 0 ? 0x4B3319 : n == 1 ? 0x6B4A27 : 0x5A3F22);
            }
        }
        int r = Math.max(10, im.getWidth() / 9);
        int cy = baseY - height;
        for (int dy = -r; dy <= r * 0.7; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                if (dx * dx + dy * dy * 1.3 <= r * r) {
                    int n = rnd.nextInt(4);
                    px(im, x + dx, cy + dy, n == 0 ? 0x2E7A22 : n == 1 ? 0x4AA03A : n == 2 ? 0x3A8A2A : 0x276B1C);
                }
            }
        }
    }

    // ------------------------------------------------------------------ skin

    private void loadSkinAsync() {
        final String n = nick;
        if (!validNick(n)) {
            skin = null;
            frame.repaint();
            return;
        }
        Thread t = new Thread(() -> {
            BufferedImage img = null;
            Path cache = baseDir.resolve("skins").resolve(n + ".png");
            try {
                URLConnection c = new URL("https://minotar.net/skin/" + n).openConnection();
                c.setConnectTimeout(6000);
                c.setReadTimeout(8000);
                c.setRequestProperty("User-Agent", "BushLaunch/" + LAUNCHER_VERSION);
                try (InputStream in = c.getInputStream()) {
                    img = ImageIO.read(in);
                }
                if (img != null) {
                    Files.createDirectories(cache.getParent());
                    ImageIO.write(img, "png", cache.toFile());
                }
            } catch (Exception e) {
                try {
                    if (Files.exists(cache)) {
                        img = ImageIO.read(cache.toFile());
                    }
                } catch (IOException ignored) {
                    // brak skina - użyjemy domyślnej postaci
                }
            }
            BufferedImage result = img;
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
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
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

    static void drawFigure(Graphics2D g0, BufferedImage s, double cx, double top, double u) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

        g.setColor(new Color(0, 0, 0, 80));
        g.fill(new Ellipse2D.Double(cx - 6 * u, top + 31.3 * u, 12 * u, 1.8 * u));

        double bodyX = cx - 4 * u;
        if (s == null) {
            g.setColor(new Color(0x3B3B8C));
            g.fill(new Rectangle2DHelper(cx - 4 * u, top + 20 * u, 8 * u, 12 * u).r());
            g.setColor(new Color(0x00A8A8));
            g.fill(new Rectangle2DHelper(bodyX, top + 8 * u, 8 * u, 12 * u).r());
            g.setColor(new Color(0xB98B63));
            g.fill(new Rectangle2DHelper(bodyX - 4 * u, top + 8 * u, 4 * u, 12 * u).r());
            g.fill(new Rectangle2DHelper(bodyX + 8 * u, top + 8 * u, 4 * u, 12 * u).r());
            g.fill(new Rectangle2DHelper(cx - 4 * u, top, 8 * u, 8 * u).r());
            g.setColor(new Color(0x3B2A1E));
            g.fill(new Rectangle2DHelper(cx - 4 * u, top, 8 * u, 2 * u).r());
            g.dispose();
            return;
        }

        boolean modern = s.getHeight() >= 64;
        boolean slim = modern && ((s.getRGB(47, 24) >>> 24) == 0);
        int aw = slim ? 3 : 4;

        // nogi
        part(g, s, 4, 20, 4, 12, cx - 4 * u, top + 20 * u, 4 * u, 12 * u, false);
        if (modern) {
            part(g, s, 20, 52, 4, 12, cx, top + 20 * u, 4 * u, 12 * u, false);
            part(g, s, 4, 36, 4, 12, cx - 4 * u, top + 20 * u, 4 * u, 12 * u, false);
            part(g, s, 4, 52, 4, 12, cx, top + 20 * u, 4 * u, 12 * u, false);
        } else {
            part(g, s, 4, 20, 4, 12, cx, top + 20 * u, 4 * u, 12 * u, true);
        }
        // tułów
        part(g, s, 20, 20, 8, 12, bodyX, top + 8 * u, 8 * u, 12 * u, false);
        if (modern) {
            part(g, s, 20, 36, 8, 12, bodyX, top + 8 * u, 8 * u, 12 * u, false);
        }
        // ręce
        part(g, s, 44, 20, aw, 12, bodyX - aw * u, top + 8 * u, aw * u, 12 * u, false);
        if (modern) {
            part(g, s, 44, 36, aw, 12, bodyX - aw * u, top + 8 * u, aw * u, 12 * u, false);
            part(g, s, 36, 52, aw, 12, bodyX + 8 * u, top + 8 * u, aw * u, 12 * u, false);
            part(g, s, 52, 52, aw, 12, bodyX + 8 * u, top + 8 * u, aw * u, 12 * u, false);
        } else {
            part(g, s, 44, 20, aw, 12, bodyX + 8 * u, top + 8 * u, aw * u, 12 * u, true);
        }
        // głowa
        part(g, s, 8, 8, 8, 8, cx - 4 * u, top, 8 * u, 8 * u, false);
        part(g, s, 40, 8, 8, 8, cx - 4.35 * u, top - 0.35 * u, 8.7 * u, 8.7 * u, false);
        g.dispose();
    }

    /** Mała pomoc, żeby nie importować Rectangle2D w każdym miejscu. */
    private record Rectangle2DHelper(double x, double y, double w, double h) {
        java.awt.geom.Rectangle2D r() {
            return new java.awt.geom.Rectangle2D.Double(x, y, w, h);
        }
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
        for (int i = 0; i < 3; i++) {
            if (i < list.size()) {
                recentCards.add(new ProfileCard(list.get(i), false));
            } else {
                recentCards.add(new JLabel());
            }
        }
        recentCards.revalidate();
        recentCards.repaint();
    }

    private JComponent buildProfilesPage() {
        JPanel p = new JPanel(new BorderLayout(0, 18));
        p.setBackground(BG);
        p.setBorder(new EmptyBorder(26, 30, 26, 30));
        p.add(pageTitle("Moje profile",
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

    // ------------------------------------------------------------------ szafka

    private JComponent buildLockerPage() {
        JPanel p = new JPanel(new BorderLayout(24, 0));
        p.setBackground(BG);
        p.setBorder(new EmptyBorder(26, 30, 26, 30));

        JComponent preview = new JComponent() {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                aa(g);
                g.setColor(PANEL);
                g.fill(cut(0, 0, getWidth(), getHeight(), 26));
                double u = Math.max(4, getHeight() * 0.66 / 32.0);
                drawFigure(g, skin, getWidth() / 2.0, (getHeight() - 32 * u) / 2, u);
                g.dispose();
            }
        };

        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setPreferredSize(new Dimension(340, 10));

        JLabel title = new JLabel("Szafka");
        title.setForeground(TEXT);
        title.setFont(head(Font.BOLD, 34));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel sub = new JLabel("<html>Skin jest pobierany z Twojego konta Microsoft.<br>Zmienisz go na minecraft.net.</html>");
        sub.setForeground(MUTED);
        sub.setFont(body(Font.PLAIN, 13));
        sub.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel lab = new JLabel("Konto");
        lab.setForeground(TEXT);
        lab.setFont(body(Font.PLAIN, 14));
        lab.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel who = new JLabel(nick.isEmpty() ? "Nie zalogowano" : nick);
        who.setForeground(ACCENT_HI);
        who.setFont(head(Font.BOLD, 22));
        who.setAlignmentX(Component.LEFT_ALIGNMENT);
        lockerName = who;
        FlatButton account = new FlatButton("Konto Microsoft", true);
        account.setAlignmentX(Component.LEFT_ALIGNMENT);
        account.addActionListener(e -> openAccountDialog());

        form.add(title);
        form.add(javax.swing.Box.createVerticalStrut(8));
        form.add(sub);
        form.add(javax.swing.Box.createVerticalStrut(26));
        form.add(lab);
        form.add(javax.swing.Box.createVerticalStrut(6));
        form.add(who);
        form.add(javax.swing.Box.createVerticalStrut(16));
        form.add(account);

        p.add(preview, BorderLayout.CENTER);
        p.add(form, BorderLayout.EAST);
        return p;
    }

    private JComponent buildSoon(String title, String text, int iconKind) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(BG);
        p.setBorder(new EmptyBorder(26, 30, 26, 30));
        p.add(pageTitle(title, text), BorderLayout.NORTH);
        JComponent soon = new JComponent() {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                aa(g);
                card(g, 0, 0, getWidth(), getHeight(), 26, false);
                int cx = getWidth() / 2;
                int cy = getHeight() / 2;
                g.setPaint(new RadialGradientPaint(cx, cy - 20, 150f,
                        new float[]{0f, 1f}, new Color[]{alpha(GLOW, 60), alpha(GLOW, 0)}));
                g.fillRect(0, 0, getWidth(), getHeight());
                icon(g, iconKind, cx - 28, cy - 78, 56, ACCENT_HI);
                g.setFont(head(Font.BOLD, 40));
                g.setColor(TEXT);
                String s = "WKRÓTCE";
                g.drawString(s, cx - g.getFontMetrics().stringWidth(s) / 2, cy + 22);
                g.setFont(body(Font.PLAIN, 14));
                g.setColor(MUTED);
                String u2 = "Pracujemy nad tym - zajrzyj w następnej wersji launchera.";
                g.drawString(u2, cx - g.getFontMetrics().stringWidth(u2) / 2, cy + 52);
                g.dispose();
            }
        };
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.setBorder(new EmptyBorder(18, 0, 0, 0));
        wrap.add(soon, BorderLayout.CENTER);
        p.add(wrap, BorderLayout.CENTER);
        return p;
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
            thumbColor = new Color(0x4A3A32);
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
        headBar.setBackground(new Color(0x0B0706));
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
        if (lockerName != null) {
            lockerName.setText(nick.isEmpty() ? "Nie zalogowano" : nick);
        }
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
        JDialog d = makeDialog("Ustawienia", body, 440, 430);

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
        JPanel links = new JPanel(new GridLayout(3, 1, 0, 8));
        links.setOpaque(false);
        links.add(openGame);
        links.add(openBase);
        links.add(checkUpd);

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
            setPreferredSize(new Dimension(46, 34));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            aa(g);
            if (hover) {
                g.setColor(kind == 2 ? new Color(0xC42B1C) : alpha(Color.WHITE, 24));
                g.fillRect(0, 0, getWidth(), getHeight());
            }
            g.setColor(TEXT);
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
            setForeground(Color.WHITE);
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
            Color top = primary ? (hover ? new Color(0xFF8A3D) : new Color(0xF2691A)) : (hover ? new Color(0x40291C) : new Color(0x2E1D14));
            Color bot = primary ? (hover ? new Color(0xE8590C) : new Color(0xCF4A08)) : (hover ? new Color(0x2E1D14) : new Color(0x22150F));
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
     * 7 suwaki (ustawienia), 8 przekreślone oko.
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
