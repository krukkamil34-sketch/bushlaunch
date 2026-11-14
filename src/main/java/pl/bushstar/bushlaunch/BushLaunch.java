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
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
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
import java.awt.GridLayout;
import java.awt.Polygon;
import java.awt.Rectangle;
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
import java.util.stream.Stream;

/**
 * BushLaunch - launcher Minecraft by BushStar.
 * Pobiera wybraną wersję gry z serwerów Mojang i ją uruchamia (tryb offline, sam nick).
 * Wygląd: ciemny launcher w stylu Fire Client (pasek boczny, scena ze skinem, duży przycisk GRAJ).
 */
public class BushLaunch {

    public static final String LAUNCHER_VERSION = "0.2.0";

    /** Link do Discorda BushStar - podmień na swój zaproszeniowy. */
    private static final String DISCORD_URL = "https://discord.gg/";

    // ------------------------------------------------------------------ kolory i fonty

    static final Color BG = new Color(0x0F0A08);
    static final Color SIDEBAR = new Color(0x130C0A);
    static final Color PANEL = new Color(0x1A110E);
    static final Color PANEL_HI = new Color(0x26180F);
    static final Color ACCENT = new Color(0xE8590C);
    static final Color ACCENT_HI = new Color(0xFF7A1F);
    static final Color ACCENT_DIM = new Color(0x7A330E);
    static final Color TEXT = new Color(0xF4EEEA);
    static final Color MUTED = new Color(0x9C8F89);
    static final Color BORDER = new Color(0x2E211B);
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

    static Color alpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
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
        nick = config.getProperty("username", "");
        showAll = Boolean.parseBoolean(config.getProperty("showAll", "false"));
        try {
            ramMb = Math.max(512, Math.min(Integer.parseInt(config.getProperty("ram", "2048")), 65536));
        } catch (NumberFormatException ignored) {
            ramMb = 2048;
        }

        frame = new JFrame("BushLaunch");
        frame.setUndecorated(true);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setIconImage(makeIcon());
        frame.setMinimumSize(new Dimension(1180, 700));
        frame.setSize(1360, 800);
        frame.setLocationRelativeTo(null);
        frame.setMaximizedBounds(GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds());

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
        pages.add(buildSoon("Sklep", "Sklep BushStar pojawi się tutaj."), "shop");
        pages.add(buildSoon("Nagroda", "Codzienne nagrody - wkrótce."), "reward");
        pages.add(buildConsolePage(), "console");

        JPanel main = new JPanel(new BorderLayout());
        main.setBackground(BG);
        main.add(buildSidebar(), BorderLayout.WEST);
        main.add(pages, BorderLayout.CENTER);

        root.add(buildTitleBar(), BorderLayout.NORTH);
        root.add(main, BorderLayout.CENTER);

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
        JPanel side = new JPanel(new BorderLayout());
        side.setBackground(SIDEBAR);
        side.setPreferredSize(new Dimension(220, 10));

        JComponent logo = new JComponent() {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                aa(g);
                g.setColor(ACCENT);
                g.fill(flame(24, 24, 34));
                g.setColor(new Color(0xFFB347));
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
        bottom.setBorder(new EmptyBorder(0, 0, 14, 0));

        Clickable user = new Clickable(() -> askNick(null)) {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                aa(g);
                if (hover) {
                    g.setColor(alpha(Color.WHITE, 10));
                    g.fillRect(0, 0, getWidth(), getHeight());
                }
                drawHead(g, skin, 20, 12, 44);
                g.setFont(body(Font.PLAIN, 16));
                g.setColor(TEXT);
                g.drawString(nick.isEmpty() ? "Ustaw nick" : nick, 76, 32);
                g.setFont(body(Font.PLAIN, 12));
                g.setColor(MUTED);
                g.drawString(busy ? "Przygotowanie..." : "Offline", 76, 50);
                g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int cx = getWidth() - 22;
                g.drawLine(cx - 3, 26, cx + 2, 31);
                g.drawLine(cx + 2, 31, cx - 3, 36);
                g.dispose();
            }
        };
        user.setPreferredSize(new Dimension(220, 68));
        user.setMaximumSize(new Dimension(Integer.MAX_VALUE, 68));
        user.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebarUser = user;

        Clickable discord = new Clickable(() -> openUrl(DISCORD_URL)) {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                aa(g);
                g.setColor(hover ? DISCORD.brighter() : DISCORD);
                g.fill(cut(0, 0, getWidth(), getHeight(), 14));
                g.setColor(Color.WHITE);
                g.fill(new RoundRectangle2D.Double(24, 22, 24, 18, 8, 8));
                g.setColor(DISCORD);
                g.fillOval(30, 27, 4, 5);
                g.fillOval(38, 27, 4, 5);
                g.setColor(Color.WHITE);
                g.setFont(body(Font.BOLD, 16));
                g.drawString("Discord", 60, 37);
                g.dispose();
            }
        };
        discord.setPreferredSize(new Dimension(190, 62));
        discord.setMaximumSize(new Dimension(190, 62));
        discord.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel discordWrap = new JPanel(new BorderLayout());
        discordWrap.setOpaque(false);
        discordWrap.setBorder(new EmptyBorder(8, 0, 0, 0));
        discordWrap.setMaximumSize(new Dimension(Integer.MAX_VALUE, 72));
        discordWrap.add(discord, BorderLayout.WEST);
        discordWrap.setAlignmentX(Component.LEFT_ALIGNMENT);

        bottom.add(user);
        bottom.add(discordWrap);

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
            setPreferredSize(new Dimension(220, 58));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 58));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            aa(g);
            if (active) {
                g.setPaint(new GradientPaint(0, 0, alpha(ACCENT, 46), getWidth(), 0, alpha(ACCENT, 0)));
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(ACCENT);
                g.fillRect(0, 8, 4, getHeight() - 16);
            } else if (hover) {
                g.setColor(alpha(Color.WHITE, 10));
                g.fillRect(0, 0, getWidth(), getHeight());
            }
            Color c = active || hover ? TEXT : MUTED;
            icon(g, icon, 26, 18, 22, c);
            g.setColor(c);
            g.setFont(body(Font.PLAIN, 17));
            g.drawString(label, 64, 35);
            g.dispose();
        }
    }

    // ------------------------------------------------------------------ strona główna

    private JComponent buildHome() {
        JPanel home = new JPanel(new BorderLayout(18, 0));
        home.setBackground(BG);
        home.setBorder(new EmptyBorder(22, 22, 22, 22));

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
        col.setPreferredSize(new Dimension(290, 10));

        Clickable account = new Clickable(() -> askNick(null)) {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                aa(g);
                g.setColor(hover ? new Color(0x4A3A32) : new Color(0x3B2E27));
                g.fill(cut(0, 0, getWidth(), getHeight(), 24));
                drawHead(g, skin, 16, 17, 56);
                g.setColor(TEXT);
                g.setFont(head(Font.BOLD, 28));
                g.drawString(nick.isEmpty() ? "Ustaw nick" : nick, 86, 47);
                g.setColor(MUTED);
                g.setFont(body(Font.PLAIN, 13));
                g.drawString("Zmień konto", 86, 68);
                g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.setColor(TEXT);
                int cx = getWidth() - 26;
                g.drawLine(cx - 5, 40, cx, 46);
                g.drawLine(cx, 46, cx + 5, 40);
                g.dispose();
            }
        };
        account.setPreferredSize(new Dimension(10, 90));

        InfoCard news = new InfoCard("Nowości", "Brak nowości.", false);
        news.setPreferredSize(new Dimension(10, 150));
        InfoCard friends = new InfoCard("Znajomi", "Tryb offline - znajomi wkrótce.", true);

        JPanel top = new JPanel(new BorderLayout(0, 16));
        top.setOpaque(false);
        top.add(account, BorderLayout.NORTH);
        top.add(news, BorderLayout.CENTER);
        col.add(top, BorderLayout.NORTH);
        col.add(friends, BorderLayout.CENTER);

        home.add(left, BorderLayout.CENTER);
        home.add(col, BorderLayout.EAST);
        return home;
    }

    private static final class InfoCard extends JComponent {
        private final String title;
        private final String text;
        private final boolean eye;

        InfoCard(String title, String text, boolean eye) {
            this.title = title;
            this.text = text;
            this.eye = eye;
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            aa(g);
            g.setColor(new Color(0x150E0B));
            g.fill(cut(0, 0, getWidth(), getHeight(), 24));
            g.setColor(BORDER);
            g.setStroke(new BasicStroke(1f));
            g.draw(cut(0.5, 0.5, getWidth() - 1, getHeight() - 1, 24));
            g.setColor(TEXT);
            g.setFont(head(Font.BOLD, 26));
            g.drawString(title, 22, 44);
            g.setColor(MUTED);
            g.setFont(body(Font.PLAIN, 14));
            g.drawString(text, 22, 82);
            if (eye) {
                icon(g, 8, getWidth() - 56, 26, 22, MUTED);
            }
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

        Hero() {
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

            // postać
            double availH = barY;
            double unit = Math.max(4, availH * 0.66 / 32.0);
            double top = (availH - 32 * unit) / 2 + 30;
            drawFigure(g, skin, w / 2.0, top, unit);

            // tabliczka z nickiem
            String tag = nick.isEmpty() ? "Ustaw nick" : nick;
            g.setFont(head(Font.BOLD, 26));
            FontMetrics fm = g.getFontMetrics();
            int tw = fm.stringWidth(tag) + 44;
            int tx = (int) (w / 2.0 - tw / 2.0);
            int ty = (int) (top - 58);
            g.setColor(new Color(0x2B, 0x30, 0x48, 220));
            g.fillRect(tx, ty, tw, 44);
            g.setColor(TEXT);
            g.drawString(tag, tx + 22, ty + 32);

            // dolny pasek: ustawienia, wersja
            int by = barY + (barH - 64) / 2;
            gearRect.setBounds(28, by, 50, 64);
            g.setColor(hoverGear ? PANEL_HI.brighter() : PANEL_HI);
            g.fillRect(gearRect.x, gearRect.y, gearRect.width, gearRect.height);
            icon(g, 7, gearRect.x + 14, gearRect.y + 21, 22, hoverGear ? TEXT : MUTED);

            verRect.setBounds(88, by, 212, 64);
            g.setColor(hoverVer ? PANEL_HI.brighter() : PANEL_HI);
            g.fillRect(verRect.x, verRect.y, verRect.width, verRect.height);
            String vid = selected == null ? "Wybierz wersję" : selected.id();
            String vsub = selected == null ? (loadingVersions ? "Wczytywanie..." : "Brak wersji")
                    : typeLabel(selected.type());
            if (selected != null) {
                drawBlock(g, verRect.x + 10, verRect.y + 12, 40, selected.id().hashCode());
            }
            int textX = verRect.x + (selected != null ? 60 : 14);
            g.setFont(head(Font.BOLD, 19));
            g.setColor(MUTED.brighter());
            g.drawString(vid, textX, verRect.y + 28);
            g.setFont(body(Font.PLAIN, 13));
            g.setColor(MUTED);
            g.drawString(vsub, textX, verRect.y + 48);
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
        private final VersionEntry v;

        ProfileCard(VersionEntry v) {
            super(null);
            this.v = v;
            this.onClick = () -> {
                selectVersion(v);
                showPage("home");
            };
            setPreferredSize(new Dimension(220, 76));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            aa(g);
            boolean sel = selected != null && selected.id().equals(v.id());
            g.setColor(sel ? new Color(0x2A1810) : hover ? PANEL_HI : PANEL);
            g.fill(cut(0, 0, getWidth(), getHeight(), 18));
            g.setColor(sel ? ACCENT : BORDER);
            g.setStroke(new BasicStroke(sel ? 1.6f : 1f));
            g.draw(cut(0.5, 0.5, getWidth() - 1, getHeight() - 1, 18));
            drawBlock(g, 14, 16, 44, v.id().hashCode());
            g.setColor(TEXT);
            g.setFont(head(Font.BOLD, 22));
            g.drawString(v.id(), 72, 40);
            g.setColor(MUTED);
            g.setFont(body(Font.PLAIN, 13));
            g.drawString(typeLabel(v.type()), 72, 60);
            g.dispose();
        }
    }

    private void selectVersion(VersionEntry v) {
        selected = v;
        config.setProperty("version", v.id());
        config.setProperty("showAll", String.valueOf(showAll));
        saveConfig();
        refreshRecent();
        if (hero != null) {
            hero.repaint();
        }
    }

    private List<VersionEntry> installedList() {
        List<VersionEntry> list = new ArrayList<>();
        try {
            if (installer != null) {
                list.addAll(installer.installedVersions());
            }
        } catch (Exception ignored) {
            // brak zainstalowanych wersji
        }
        if (selected != null) {
            list.removeIf(v -> v.id().equals(selected.id()));
            list.add(0, selected);
        }
        return list;
    }

    private void refreshRecent() {
        if (recentCards == null) {
            return;
        }
        recentCards.removeAll();
        List<VersionEntry> list = installedList();
        for (int i = 0; i < 3; i++) {
            if (i < list.size()) {
                recentCards.add(new ProfileCard(list.get(i)));
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
        p.add(pageTitle("Moje profile", "Zainstalowane i wybrane wersje gry. Kliknij, żeby wybrać."), BorderLayout.NORTH);

        profilesGrid = new JPanel(new GridLayout(0, 3, 16, 16));
        profilesGrid.setOpaque(false);
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.add(profilesGrid, BorderLayout.NORTH);

        FlatButton pick = new FlatButton("Wybierz inną wersję", true);
        pick.addActionListener(e -> openVersionPicker());
        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        south.add(pick, BorderLayout.WEST);

        p.add(wrap, BorderLayout.CENTER);
        p.add(south, BorderLayout.SOUTH);
        return p;
    }

    private void refreshProfiles() {
        if (profilesGrid == null) {
            return;
        }
        profilesGrid.removeAll();
        for (VersionEntry v : installedList()) {
            profilesGrid.add(new ProfileCard(v));
        }
        profilesGrid.revalidate();
        profilesGrid.repaint();
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
            JLabel l = new JLabel(n);
            l.setForeground(TEXT);
            l.setFont(body(Font.PLAIN, 15));
            l.setBorder(new EmptyBorder(10, 14, 10, 14));
            l.setOpaque(true);
            l.setBackground(PANEL);
            l.setAlignmentX(Component.LEFT_ALIGNMENT);
            l.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
            modsList.add(l);
            modsList.add(javax.swing.Box.createVerticalStrut(6));
        }
        if (names.isEmpty()) {
            JLabel l = new JLabel("Brak modów w folderze mods.");
            l.setForeground(MUTED);
            l.setFont(body(Font.PLAIN, 15));
            modsList.add(l);
        }
        modsInfo.setText("Launcher uruchamia na razie czysty Minecraft. Obsługa Fabric/Forge - wkrótce.");
        modsList.revalidate();
        modsList.repaint();
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
        JLabel sub = new JLabel("<html>Skin jest pobierany z Mojang po nicku.<br>Skiny premium są widoczne tylko tutaj.</html>");
        sub.setForeground(MUTED);
        sub.setFont(body(Font.PLAIN, 13));
        sub.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel lab = new JLabel("Nick");
        lab.setForeground(TEXT);
        lab.setFont(body(Font.PLAIN, 14));
        lab.setAlignmentX(Component.LEFT_ALIGNMENT);
        JTextField f = field(nick);
        f.setAlignmentX(Component.LEFT_ALIGNMENT);
        f.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        FlatButton save = new FlatButton("Zapisz nick", true);
        save.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel err = new JLabel(" ");
        err.setForeground(new Color(0xFF6B5A));
        err.setFont(body(Font.PLAIN, 12));
        err.setAlignmentX(Component.LEFT_ALIGNMENT);
        save.addActionListener(e -> {
            String n = f.getText().trim();
            if (!validNick(n)) {
                err.setText("Nick musi mieć 3-16 znaków: litery, cyfry lub _");
                return;
            }
            err.setText(" ");
            setNick(n);
            preview.repaint();
        });

        form.add(title);
        form.add(javax.swing.Box.createVerticalStrut(8));
        form.add(sub);
        form.add(javax.swing.Box.createVerticalStrut(26));
        form.add(lab);
        form.add(javax.swing.Box.createVerticalStrut(6));
        form.add(f);
        form.add(javax.swing.Box.createVerticalStrut(6));
        form.add(err);
        form.add(javax.swing.Box.createVerticalStrut(10));
        form.add(save);

        p.add(preview, BorderLayout.CENTER);
        p.add(form, BorderLayout.EAST);
        return p;
    }

    private JComponent buildSoon(String title, String text) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(BG);
        p.setBorder(new EmptyBorder(26, 30, 26, 30));
        p.add(pageTitle(title, text), BorderLayout.NORTH);
        JComponent soon = new JComponent() {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                aa(g);
                g.setColor(PANEL);
                g.fill(cut(0, 0, getWidth(), getHeight(), 26));
                g.setFont(head(Font.BOLD, 44));
                g.setColor(alpha(ACCENT, 190));
                String s = "WKRÓTCE";
                g.drawString(s, (getWidth() - g.getFontMetrics().stringWidth(s)) / 2, getHeight() / 2 + 14);
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
        JLabel s = new JLabel(sub);
        s.setForeground(MUTED);
        s.setFont(body(Font.PLAIN, 14));
        s.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(t);
        p.add(javax.swing.Box.createVerticalStrut(4));
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

    private void askNick(Runnable after) {
        JPanel body = new JPanel(new BorderLayout(0, 8));
        JDialog d = makeDialog("Zmień konto", body, 420, 250);

        JLabel info = new JLabel("Nick (3-16 znaków: litery, cyfry lub _)");
        info.setForeground(MUTED);
        info.setFont(body(Font.PLAIN, 13));
        JTextField f = field(nick);
        JLabel err = new JLabel(" ");
        err.setForeground(new Color(0xFF6B5A));
        err.setFont(body(Font.PLAIN, 12));
        FlatButton ok = new FlatButton("Zapisz", true);

        JPanel mid = new JPanel(new BorderLayout(0, 8));
        mid.setOpaque(false);
        mid.add(info, BorderLayout.NORTH);
        mid.add(f, BorderLayout.CENTER);
        mid.add(err, BorderLayout.SOUTH);
        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        south.add(ok, BorderLayout.EAST);
        body.add(mid, BorderLayout.NORTH);
        body.add(south, BorderLayout.SOUTH);

        final boolean[] saved = {false};
        Runnable submit = () -> {
            String n = f.getText().trim();
            if (!validNick(n)) {
                err.setText("Nick musi mieć 3-16 znaków: litery, cyfry lub _");
                return;
            }
            setNick(n);
            saved[0] = true;
            d.dispose();
        };
        ok.addActionListener(e -> submit.run());
        f.addActionListener(e -> submit.run());
        SwingUtilities.invokeLater(f::requestFocusInWindow);
        d.setVisible(true);
        if (saved[0] && after != null) {
            after.run();
        }
    }

    private void setNick(String n) {
        nick = n;
        config.setProperty("username", n);
        saveConfig();
        skin = null;
        loadSkinAsync();
        frame.repaint();
    }

    private void openVersionPicker() {
        JPanel body = new JPanel(new BorderLayout(0, 10));
        JDialog d = makeDialog("Wybierz wersję", body, 440, 580);

        JTextField search = field("");
        JCheckBox all = new JCheckBox("Snapshoty i bardzo stare wersje", showAll);
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
            if (selected != null) {
                list.setSelectedValue(selected, true);
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
            showAll = all.isSelected();
            if (v != null) {
                selectVersion(v);
            } else {
                config.setProperty("showAll", String.valueOf(showAll));
                saveConfig();
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
        JDialog d = makeDialog("Ustawienia", body, 440, 340);

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

        FlatButton openGame = new FlatButton("Otwórz folder gry", false);
        openGame.addActionListener(e -> openFolder(installer.gameDir()));
        FlatButton openBase = new FlatButton("Otwórz folder launchera", false);
        openBase.addActionListener(e -> openFolder(baseDir));
        JPanel links = new JPanel(new GridLayout(2, 1, 0, 8));
        links.setOpaque(false);
        links.add(openGame);
        links.add(openBase);

        FlatButton save = new FlatButton("Zapisz", true);
        save.addActionListener(e -> {
            ramMb = snap(slider.getValue());
            config.setProperty("ram", String.valueOf(ramMb));
            saveConfig();
            hero.repaint();
            d.dispose();
        });
        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        south.add(save, BorderLayout.EAST);

        body.add(ramBox, BorderLayout.NORTH);
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
                loadingVersions = false;
                String wanted = config.getProperty("version");
                VersionEntry pick = null;
                for (VersionEntry v : allVersions) {
                    if (v.id().equals(wanted)) {
                        pick = v;
                        break;
                    }
                }
                if (pick == null) {
                    for (VersionEntry v : allVersions) {
                        if ("release".equals(v.type())) {
                            pick = v;
                            break;
                        }
                    }
                }
                if (pick == null && !allVersions.isEmpty()) {
                    pick = allVersions.get(0);
                }
                selected = pick;
                refreshRecent();
                hero.repaint();
            });
        }, "load-versions");
        t.setDaemon(true);
        t.start();
    }

    private void onPlay() {
        if (busy || loadingVersions) {
            return;
        }
        if (!validNick(nick)) {
            askNick(this::onPlay);
            return;
        }
        if (selected == null) {
            openVersionPicker();
            return;
        }
        VersionEntry version = selected;
        String name = nick;
        int ram = ramMb;

        config.setProperty("username", name);
        config.setProperty("version", version.id());
        config.setProperty("ram", String.valueOf(ram));
        config.setProperty("showAll", String.valueOf(showAll));
        saveConfig();

        setBusy(true);
        Thread t = new Thread(() -> runGame(version, name, ram), "game");
        t.setDaemon(true);
        t.start();
    }

    private void runGame(VersionEntry version, String name, int ram) {
        try {
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

            List<String> cmd = installer.buildCommand(prepared, java.exe(), name, ram);
            appendLog("Uruchamianie Minecrafta " + version.id() + " jako " + name + "...");
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
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(new URI(url));
            }
        } catch (Exception e) {
            appendLog("Nie udało się otworzyć linku: " + e.getMessage());
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
            Color c = primary ? (hover ? ACCENT_HI : ACCENT) : (hover ? new Color(0x3A2A20) : PANEL_HI);
            g.setColor(c);
            g.fill(cut(0, 0, getWidth(), getHeight(), 10));
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
