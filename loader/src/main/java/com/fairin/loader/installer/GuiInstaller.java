package com.fairin.loader.installer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Font;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Small Swing GUI: client profile install or dedicated server folder install. */
final class GuiInstaller {
    static final String SEP = "────────── installed locally ──────────";
    static final int MAX_RELEASES = 30;   // newest N stable releases in the dropdown
    static final int MAX_SNAPSHOTS = 15;  // newest N snapshots (checkbox)
    private JFrame frame;
    private JComboBox<String> mode;
    private javax.swing.JTextField gameDirField;
    private JComboBox<String> versionBox;
    private javax.swing.JCheckBox snapshots;
    private JButton installBtn;
    private JTextArea log;
    private List<String> releaseIds = new ArrayList<String>();
    private List<String> snapshotIds = new ArrayList<String>();
    private boolean manifestLoaded;

    static void run() {
        EventQueue.invokeLater(new Runnable() {
            public void run() {
                try {
                    new GuiInstaller().show();
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        });
    }

    private void show() {
        frame = new JFrame("Fairin Installer");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

        JPanel head = new JPanel();
        head.setLayout(new BoxLayout(head, BoxLayout.X_AXIS));
        JLabel logo = new JLabel();
        java.net.URL logoUrl = GuiInstaller.class.getResource("/fairin/logo.png");
        if (logoUrl != null) {
            ImageIcon icon = new ImageIcon(logoUrl);
            java.awt.Image scaled = icon.getImage().getScaledInstance(44, 44, java.awt.Image.SCALE_SMOOTH);
            logo.setIcon(new ImageIcon(scaled));
        }
        JLabel title = new JLabel("  Fairin Loader");
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        head.add(logo);
        head.add(title);
        head.add(Box.createHorizontalGlue());
        root.add(head);

        mode = new JComboBox<String>(new String[]{"Client (launcher profile)", "Server (dedicated folder)"});
        mode.setMaximumSize(new Dimension(10000, 30));
        mode.setAlignmentX(0.0f);
        root.add(mode);
        root.add(Box.createVerticalStrut(8));

        JPanel dirPanel = new JPanel();
        dirPanel.setLayout(new BoxLayout(dirPanel, BoxLayout.X_AXIS));
        dirPanel.setAlignmentX(0.0f);
        dirPanel.add(new JLabel("Folder: "));
        gameDirField = new javax.swing.JTextField(defaultDir());
        gameDirField.setMaximumSize(new Dimension(10000, 28));
        dirPanel.add(gameDirField);
        JButton browse = new JButton("...");
        browse.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                JFileChooser fc = new JFileChooser(gameDirField.getText());
                fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                if (fc.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                    gameDirField.setText(fc.getSelectedFile().getAbsolutePath());
                    reloadVersions();
                    fetchManifestVersions();
                }
            }
        });
        dirPanel.add(browse);
        root.add(dirPanel);

        JPanel verPanel = new JPanel();
        verPanel.setLayout(new BoxLayout(verPanel, BoxLayout.X_AXIS));
        verPanel.setAlignmentX(0.0f);
        verPanel.add(new JLabel("Minecraft: "));
        versionBox = new JComboBox<String>();
        versionBox.setMaximumSize(new Dimension(10000, 28));
        verPanel.add(versionBox);
        snapshots = new javax.swing.JCheckBox("Snapshots");
        snapshots.setToolTipText("Include development snapshots from Mojang");
        snapshots.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                rebuildVersions();
            }
        });
        verPanel.add(snapshots);
        root.add(verPanel);

        installBtn = new JButton("Install");
        installBtn.setAlignmentX(0.0f);
        installBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                doInstall();
            }
        });
        root.add(installBtn);

        log = new JTextArea(12, 50);
        log.setEditable(false);
        log.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        root.add(new JScrollPane(log));

        frame.setContentPane(root);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        say("Fairin Installer ready. Pick a folder, a Minecraft version, then Install.");
        reloadVersions();
        fetchManifestVersions();
    }

    private String defaultDir() {
        String appdata = System.getenv("APPDATA");
        return new File(appdata != null ? appdata : ".", ".minecraft").getAbsolutePath();
    }

    private void say(final String s) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                log.append(s + "\n");
                log.setCaretPosition(log.getDocument().getLength());
            }
        });
    }

    private void reloadVersions() {
        File dir = new File(gameDirField.getText().trim());
        int locals = 0;
        File[] list = new File(dir, "versions").listFiles();
        if (list != null) for (File f : list) {
            if (new File(f, f.getName() + ".json").isFile()) locals++;
        }
        say("Local versions: " + locals);
        rebuildVersions();
    }

    /** Releases first (newest), snapshots optional, local profiles under a separator. */
    private void rebuildVersions() {
        String selected = (String) versionBox.getSelectedItem();
        File dir = new File(gameDirField.getText().trim());
        versionBox.removeAllItems();

        java.util.Set<String> seen = new java.util.HashSet<String>();
        for (String r : releaseIds) {
            if (ManifestHelper.isSupported(r) && seen.add(r)) versionBox.addItem(r);
        }
        if (snapshots.isSelected()) {
            for (String s : snapshotIds) {
                if (ManifestHelper.isSupported(s) && seen.add(s)) versionBox.addItem(s);
            }
        }

        List<String> locals = new ArrayList<String>();
        File[] list = new File(dir, "versions").listFiles();
        if (list != null) for (File f : list) {
            if (new File(f, f.getName() + ".json").isFile() && !seen.contains(f.getName())) locals.add(f.getName());
        }
        if (!locals.isEmpty()) {
            versionBox.addItem(SEP);
            for (String l : locals) versionBox.addItem(l);
        }

        // keep previous choice if still present, else default to the newest release
        boolean found = false;
        for (int i = 0; i < versionBox.getItemCount(); i++) {
            if (versionBox.getItemAt(i).equals(selected)) { versionBox.setSelectedIndex(i); found = true; break; }
        }
        if (!found) {
            for (int i = 0; i < versionBox.getItemCount(); i++) {
                String it = versionBox.getItemAt(i);
                if (!it.equals(SEP)) { versionBox.setSelectedIndex(i); break; }
            }
        }
    }

    /** Fetches the Mojang manifest in background, then rebuilds the version list. */
    private void fetchManifestVersions() {
        if (manifestLoaded) return;
        final File dir = new File(gameDirField.getText().trim());
        new Thread(new Runnable() {
            public void run() {
                try {
                    releaseIds = ManifestHelper.releaseVersions(dir);
                    snapshotIds = ManifestHelper.snapshotVersions(dir);
                    // keep the list short and useful: newest 30 releases / 15 snapshots
                    if (releaseIds.size() > MAX_RELEASES) releaseIds = releaseIds.subList(0, MAX_RELEASES);
                    if (snapshotIds.size() > MAX_SNAPSHOTS) snapshotIds = snapshotIds.subList(0, MAX_SNAPSHOTS);
                    manifestLoaded = true;
                    say("Mojang manifest: " + releaseIds.size() + " свежих релизов"
                            + (snapshots.isSelected() ? ", " + snapshotIds.size() + " снапшотов" : "")
                            + " (старые версии — через консоль: --mc <version>)");
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            rebuildVersions();
                        }
                    });
                } catch (Throwable t) {
                    say("Manifest fetch failed (offline?): " + t);
                }
            }
        }, "fairin-manifest").start();
    }

    private void doInstall() {
        final boolean serverMode = mode.getSelectedIndex() == 1;
        final String ver = (String) versionBox.getSelectedItem();
        final File dir = new File(gameDirField.getText().trim());
        if (ver == null || ver.isEmpty() || ver.equals(SEP)) { say("Select a Minecraft version first."); return; }
        installBtn.setEnabled(false);
        say("Installing " + ver + (serverMode ? " (server)" : " (client)") + " ...");
        new Thread(new Runnable() {
            public void run() {
                try {
                    if (serverMode) {
                        Installer.installServer(ver, new File(dir, "fairin-server-" + ver));
                    } else {
                        Installer.install(ver, dir);
                        File self = Installer.findSelfJarPublic();
                        if (self.isFile()) {
                            Installer.copyPublic(self, new File(dir,
                                    "libraries/com/fairin/fairin-loader/0.1.0/fairin-loader-0.1.0.jar".replace('/', File.separatorChar)));
                        }
                    }
                    say("DONE.");
                } catch (Throwable t) {
                    say("FAILED: " + t);
                } finally {
                    installBtn.setEnabled(true);
                }
            }
        }, "fairin-install").start();
    }
}
