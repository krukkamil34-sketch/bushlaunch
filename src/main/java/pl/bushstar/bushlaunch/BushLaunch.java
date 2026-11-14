package pl.bushstar.bushlaunch;

import pl.bushstar.bushlaunch.GameInstaller.VersionEntry;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.text.BadLocationException;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * BushLaunch - launcher Minecraft by BushStar.
 * Pobiera wybraną wersję gry z serwerów Mojang i ją uruchamia (tryb offline, sam nick).
 */
public class BushLaunch {

    public static final String LAUNCHER_VERSION = "0.2.0";

    private static final Color BG = new Color(0x14, 0x1A, 0x16);
    private static final Color PANEL = new Color(0x1E, 0x27, 0x21);
    private static final Color ACCENT = new Color(0x3F, 0xB9, 0x50);
    private static final Color TEXT = new Color(0xE6, 0xED, 0xE8);
    private static final Color MUTED = new Color(0x8A, 0x9A, 0x8F);
    private static final Color BORDER = new Color(0x2C, 0x3A, 0x31);

    private final Path baseDir = defaultBaseDir();
    private final Path configFile = baseDir.resolve("config.properties");
    private final Properties config = new Properties();

    private JFrame frame;
    private JTextField userField;
    private JComboBox<VersionEntry> versionBox;
    private JCheckBox allBox;
    private JSpinner ramSpin;
    private JButton playBtn;
    private JProgressBar bar;
    private JTextArea logArea;

    private GameInstaller installer;
    private List<VersionEntry> allVersions = new ArrayList<>();

    public static void main(String[] args) {
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

        frame = new JFrame("BushLaunch " + LAUNCHER_VERSION);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(640, 520));
        frame.setSize(760, 620);
        frame.setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(0, 14));
        root.setBackground(BG);
        root.setBorder(new EmptyBorder(18, 18, 18, 18));

        JPanel center = new JPanel(new BorderLayout(0, 12));
        center.setOpaque(false);
        center.add(buildForm(), BorderLayout.NORTH);
        center.add(buildLog(), BorderLayout.CENTER);

        root.add(buildHeader(), BorderLayout.NORTH);
        root.add(center, BorderLayout.CENTER);
        root.add(buildBottom(), BorderLayout.SOUTH);

        frame.setContentPane(root);
        frame.setVisible(true);

        installer = new GameInstaller(baseDir, new GameInstaller.Listener() {
            @Override
            public void log(String message) {
                appendLog(message);
            }

            @Override
            public void progress(int done, int total, String what) {
                SwingUtilities.invokeLater(() -> {
                    bar.setIndeterminate(false);
                    bar.setMaximum(Math.max(total, 1));
                    bar.setValue(done);
                    bar.setString(what + " " + done + "/" + total);
                });
            }
        });

        loadVersions();
    }

    private JComponent buildHeader() {
        JPanel header = new JPanel();
        header.setLayout(new javax.swing.BoxLayout(header, javax.swing.BoxLayout.Y_AXIS));
        header.setOpaque(false);

        JLabel title = new JLabel("BushLaunch");
        title.setForeground(ACCENT);
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel sub = new JLabel("Launcher Minecraft by BushStar  •  v" + LAUNCHER_VERSION);
        sub.setForeground(MUTED);
        sub.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        sub.setAlignmentX(Component.LEFT_ALIGNMENT);

        header.add(title);
        header.add(sub);
        return header;
    }

    private JComponent buildForm() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(PANEL);
        form.setBorder(new EmptyBorder(14, 14, 14, 14));

        userField = new JTextField(config.getProperty("username", ""), 16);
        styleField(userField);

        versionBox = new JComboBox<>();
        versionBox.setBackground(BG);
        versionBox.setForeground(TEXT);
        versionBox.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean selected, boolean focus) {
                JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focus);
                l.setBackground(selected ? ACCENT : BG);
                l.setForeground(selected ? Color.BLACK : TEXT);
                l.setBorder(new EmptyBorder(3, 6, 3, 6));
                return l;
            }
        });

        allBox = new JCheckBox("Pokaż snapshoty i bardzo stare wersje",
                Boolean.parseBoolean(config.getProperty("showAll", "false")));
        allBox.setOpaque(false);
        allBox.setForeground(MUTED);
        allBox.setFocusPainted(false);
        allBox.addActionListener(e -> refreshVersions());

        int ram = 2048;
        try {
            ram = Integer.parseInt(config.getProperty("ram", "2048"));
        } catch (NumberFormatException ignored) {
            // zostaje domyślne 2048
        }
        ramSpin = new JSpinner(new SpinnerNumberModel(Math.max(512, Math.min(ram, 32768)), 512, 32768, 256));
        JTextField ramText = ((JSpinner.DefaultEditor) ramSpin.getEditor()).getTextField();
        styleField(ramText);
        ramSpin.setBorder(BorderFactory.createLineBorder(BORDER));

        addRow(form, 0, "Nick", userField);
        addRow(form, 1, "Wersja", versionBox);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 1;
        c.gridy = 2;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(0, 8, 6, 0);
        form.add(allBox, c);
        addRow(form, 3, "RAM (MB)", ramSpin);
        return form;
    }

    private void addRow(JPanel form, int row, String label, JComponent comp) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(4, 0, 4, 8);
        JLabel l = new JLabel(label);
        l.setForeground(TEXT);
        form.add(l, c);

        c = new GridBagConstraints();
        c.gridx = 1;
        c.gridy = row;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(4, 8, 4, 0);
        form.add(comp, c);
    }

    private void styleField(JTextField f) {
        f.setBackground(BG);
        f.setForeground(TEXT);
        f.setCaretColor(TEXT);
        f.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER), new EmptyBorder(5, 7, 5, 7)));
    }

    private JComponent buildLog() {
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setBackground(PANEL);
        logArea.setForeground(MUTED);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        logArea.setBorder(new EmptyBorder(8, 8, 8, 8));
        JScrollPane scroll = new JScrollPane(logArea);
        scroll.setBorder(BorderFactory.createLineBorder(BORDER));
        return scroll;
    }

    private JComponent buildBottom() {
        JPanel bottom = new JPanel(new BorderLayout(0, 10));
        bottom.setOpaque(false);

        bar = new JProgressBar();
        bar.setStringPainted(true);
        bar.setString(" ");
        bar.setForeground(ACCENT);
        bar.setBackground(PANEL);
        bar.setBorder(BorderFactory.createLineBorder(BORDER));

        playBtn = new JButton("GRAJ");
        playBtn.setEnabled(false);
        playBtn.setBackground(ACCENT);
        playBtn.setForeground(Color.BLACK);
        playBtn.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        playBtn.setFocusPainted(false);
        playBtn.setBorder(new EmptyBorder(12, 18, 12, 18));
        playBtn.addActionListener(e -> onPlay());

        bottom.add(bar, BorderLayout.NORTH);
        bottom.add(playBtn, BorderLayout.CENTER);
        return bottom;
    }

    // ------------------------------------------------------------------ wersje

    private void loadVersions() {
        appendLog("Pobieranie listy wersji z serwerów Mojang...");
        bar.setIndeterminate(true);
        bar.setString("Pobieranie listy wersji");
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
                bar.setIndeterminate(false);
                bar.setString(" ");
                refreshVersions();
            });
        }, "load-versions");
        t.setDaemon(true);
        t.start();
    }

    private void refreshVersions() {
        VersionEntry current = (VersionEntry) versionBox.getSelectedItem();
        String wanted = current != null ? current.id() : config.getProperty("version");

        DefaultComboBoxModel<VersionEntry> model = new DefaultComboBoxModel<>();
        VersionEntry select = null;
        for (VersionEntry v : allVersions) {
            boolean release = "release".equals(v.type());
            if (!release && !allBox.isSelected()) {
                continue;
            }
            model.addElement(v);
            if (v.id().equals(wanted)) {
                select = v;
            }
        }
        versionBox.setModel(model);
        if (select != null) {
            versionBox.setSelectedItem(select);
        } else if (model.getSize() > 0) {
            versionBox.setSelectedIndex(0);
        }
        playBtn.setEnabled(model.getSize() > 0);
    }

    // ------------------------------------------------------------------ graj

    private void onPlay() {
        String name = userField.getText().trim();
        if (!name.matches("[A-Za-z0-9_]{3,16}")) {
            JOptionPane.showMessageDialog(frame,
                    "Nick musi mieć 3-16 znaków: litery, cyfry lub _",
                    "BushLaunch", JOptionPane.WARNING_MESSAGE);
            return;
        }
        VersionEntry version = (VersionEntry) versionBox.getSelectedItem();
        if (version == null) {
            return;
        }
        int ram = (Integer) ramSpin.getValue();

        config.setProperty("username", name);
        config.setProperty("version", version.id());
        config.setProperty("ram", String.valueOf(ram));
        config.setProperty("showAll", String.valueOf(allBox.isSelected()));
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
                bar.setIndeterminate(false);
                bar.setValue(bar.getMaximum());
                bar.setString("Gra uruchomiona");
            });

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
        } catch (Exception e) {
            appendLog("BŁĄD: " + e.getMessage());
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(frame,
                    e.getMessage(), "BushLaunch - błąd", JOptionPane.ERROR_MESSAGE));
        } finally {
            SwingUtilities.invokeLater(() -> {
                setBusy(false);
                bar.setString(" ");
                bar.setValue(0);
            });
        }
    }

    private void setBusy(boolean busy) {
        playBtn.setEnabled(!busy);
        playBtn.setText(busy ? "TRWA..." : "GRAJ");
        userField.setEnabled(!busy);
        versionBox.setEnabled(!busy);
        allBox.setEnabled(!busy);
        ramSpin.setEnabled(!busy);
        if (busy) {
            bar.setIndeterminate(true);
            bar.setString("Przygotowanie gry");
        }
    }

    // ------------------------------------------------------------------ log i konfiguracja

    private void appendLog(String line) {
        SwingUtilities.invokeLater(() -> {
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
}
