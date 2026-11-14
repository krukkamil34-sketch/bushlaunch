package pl.bushstar.bushlaunch;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * BushLaunch - launcher Minecraft by BushStar.
 * Wersja 0.1.0: tylko wybór wersji Minecrafta (zapisywany do pliku konfiguracyjnego).
 */
public class BushLaunch {

    public static final String LAUNCHER_VERSION = "0.1.0";

    private static final Color BG = new Color(0x14, 0x1A, 0x16);
    private static final Color PANEL = new Color(0x1E, 0x27, 0x21);
    private static final Color ACCENT = new Color(0x3F, 0xB9, 0x50);
    private static final Color TEXT = new Color(0xE6, 0xED, 0xE8);
    private static final Color MUTED = new Color(0x8A, 0x9A, 0x8F);

    private static final String[] VERSIONS = {
            "1.21.4", "1.21.3", "1.21.1", "1.21",
            "1.20.6", "1.20.4", "1.20.2", "1.20.1",
            "1.19.4", "1.19.2",
            "1.18.2",
            "1.17.1",
            "1.16.5",
            "1.12.2",
            "1.8.9"
    };

    private static final Path CONFIG_DIR = Path.of(System.getProperty("user.home"), ".bushlaunch");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("config.properties");

    private final Properties config = new Properties();
    private JList<String> versionList;
    private JLabel selectedLabel;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new BushLaunch().show());
    }

    private void show() {
        loadConfig();

        JFrame frame = new JFrame("BushLaunch " + LAUNCHER_VERSION);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(460, 520);
        frame.setLocationRelativeTo(null);
        frame.setResizable(false);

        JPanel root = new JPanel(new BorderLayout(0, 14));
        root.setBackground(BG);
        root.setBorder(new EmptyBorder(18, 18, 18, 18));

        root.add(buildHeader(), BorderLayout.NORTH);
        root.add(buildVersionPanel(), BorderLayout.CENTER);
        root.add(buildFooter(), BorderLayout.SOUTH);

        frame.setContentPane(root);
        frame.setVisible(true);
    }

    private JComponent buildHeader() {
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
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
        header.add(Box.createVerticalStrut(2));
        header.add(sub);
        return header;
    }

    private JComponent buildVersionPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBackground(PANEL);
        panel.setBorder(new EmptyBorder(12, 12, 12, 12));

        JLabel label = new JLabel("Wybierz wersję Minecrafta");
        label.setForeground(TEXT);
        label.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        panel.add(label, BorderLayout.NORTH);

        versionList = new JList<>(VERSIONS);
        versionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        versionList.setBackground(BG);
        versionList.setForeground(TEXT);
        versionList.setSelectionBackground(ACCENT);
        versionList.setSelectionForeground(Color.BLACK);
        versionList.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 15));
        versionList.setFixedCellHeight(28);

        String saved = config.getProperty("version", VERSIONS[0]);
        versionList.setSelectedValue(saved, true);
        if (versionList.getSelectedIndex() < 0) {
            versionList.setSelectedIndex(0);
        }

        JScrollPane scroll = new JScrollPane(versionList);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(0x2C, 0x3A, 0x31)));
        panel.add(scroll, BorderLayout.CENTER);

        return panel;
    }

    private JComponent buildFooter() {
        JPanel footer = new JPanel(new BorderLayout(10, 0));
        footer.setOpaque(false);

        selectedLabel = new JLabel();
        selectedLabel.setForeground(MUTED);
        selectedLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        updateSelectedLabel(config.getProperty("version"));

        JButton select = new JButton("Wybierz wersję");
        select.setBackground(ACCENT);
        select.setForeground(Color.BLACK);
        select.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        select.setFocusPainted(false);
        select.setBorder(new EmptyBorder(10, 18, 10, 18));
        select.addActionListener(e -> {
            String version = versionList.getSelectedValue();
            if (version == null) {
                return;
            }
            config.setProperty("version", version);
            saveConfig();
            updateSelectedLabel(version);
        });

        footer.add(selectedLabel, BorderLayout.CENTER);
        footer.add(select, BorderLayout.EAST);
        return footer;
    }

    private void updateSelectedLabel(String version) {
        selectedLabel.setText(version == null
                ? "Nie wybrano wersji"
                : "Wybrana wersja: " + version);
    }

    private void loadConfig() {
        if (!Files.exists(CONFIG_FILE)) {
            return;
        }
        try (InputStream in = Files.newInputStream(CONFIG_FILE)) {
            config.load(in);
        } catch (IOException ignored) {
            // brak lub uszkodzony config - użyjemy domyślnych wartości
        }
    }

    private void saveConfig() {
        try {
            Files.createDirectories(CONFIG_DIR);
            try (OutputStream out = Files.newOutputStream(CONFIG_FILE)) {
                config.store(out, "BushLaunch " + LAUNCHER_VERSION);
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null,
                    "Nie udało się zapisać konfiguracji:\n" + e.getMessage(),
                    "BushLaunch", JOptionPane.ERROR_MESSAGE);
        }
    }
}
