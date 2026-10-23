package com.netvault.ui;

import com.netvault.model.FileInfo;
import com.netvault.model.TransferRecord;
import com.netvault.server.ClientHandler;
import com.netvault.server.FileTransferServer;
import com.netvault.service.ChecksumService;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.io.File;
import java.io.IOException;
import java.net.BindException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Modern Swing graphical user interface for the NetVault Server.
 * Supports concurrent client monitoring, live logging, and file vault inspection.
 */
public class ServerFrame extends JFrame implements com.netvault.server.ServerEventListener {

    private final FileTransferServer server;

    // Controls
    private JTextField portField;
    private JButton startButton;
    private JButton stopButton;
    private JButton openFolderButton;
    private JLabel statusBadge;
    private JLabel serverIpLabel;
    private JLabel boundPortLabel;
    private JLabel activeClientsLabel;
    private JLabel storageCountLabel;

    // Tables & Logs
    private DefaultTableModel clientTableModel;
    private JTable clientTable;
    private DefaultTableModel vaultTableModel;
    private JTable vaultTable;
    private JTextArea logArea;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    public ServerFrame() {
        this(new FileTransferServer());
    }

    public ServerFrame(FileTransferServer server) {
        this.server = server;
        this.server.addListener(this);

        initUI();
        refreshVaultCatalog();
        updateServerStateUI(false);
    }

    private void initUI() {
        setTitle("NetVault — Server Management Console");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(980, 680);
        setMinimumSize(new Dimension(850, 550));
        setLocationRelativeTo(null);
        getContentPane().setBackground(Theme.BG_DARK);
        setLayout(new BorderLayout(0, 8));

        // 1. Top Header & Control Panel
        JPanel topContainer = new JPanel();
        topContainer.setLayout(new BoxLayout(topContainer, BoxLayout.Y_AXIS));
        topContainer.setBackground(Theme.BG_DARK);
        topContainer.setBorder(new EmptyBorder(12, 16, 8, 16));

        // Header Banner
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(Theme.BG_DARK);

        JPanel titlesPanel = new JPanel();
        titlesPanel.setLayout(new BoxLayout(titlesPanel, BoxLayout.Y_AXIS));
        titlesPanel.setBackground(Theme.BG_DARK);

        JLabel titleLabel = new JLabel("NETVAULT  //  SERVER CONSOLE");
        titleLabel.setFont(Theme.FONT_TITLE);
        titleLabel.setForeground(Theme.ACCENT_GOLD);

        JLabel subtitleLabel = new JLabel("High-Performance Concurrent TCP File Transfer Daemon & Integrity Vault");
        subtitleLabel.setFont(Theme.FONT_REGULAR);
        subtitleLabel.setForeground(Theme.TEXT_SECONDARY);

        titlesPanel.add(titleLabel);
        titlesPanel.add(Box.createVerticalStrut(2));
        titlesPanel.add(subtitleLabel);

        statusBadge = new JLabel();
        Theme.updateStatusBadge(statusBadge, false, "ONLINE", "STOPPED");

        headerPanel.add(titlesPanel, BorderLayout.WEST);
        headerPanel.add(statusBadge, BorderLayout.EAST);
        topContainer.add(headerPanel);
        topContainer.add(Box.createVerticalStrut(12));

        // Server Control Toolbar
        JPanel controlCard = Theme.createCardPanel();
        controlCard.setLayout(new BorderLayout(15, 0));

        JPanel leftControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        leftControls.setOpaque(false);

        JLabel portPrompt = new JLabel("Listening Port:");
        portPrompt.setFont(Theme.FONT_BOLD);
        portPrompt.setForeground(Theme.TEXT_PRIMARY);

        portField = new JTextField("5000", 6);
        Theme.styleTextField(portField);

        startButton = new JButton("Start Server");
        Theme.stylePrimaryButton(startButton);
        startButton.addActionListener(e -> startServerAction());

        stopButton = new JButton("Stop Server");
        Theme.styleDangerButton(stopButton);
        stopButton.setEnabled(false);
        stopButton.addActionListener(e -> stopServerAction());

        openFolderButton = new JButton("Open Storage Folder");
        Theme.styleSecondaryButton(openFolderButton);
        openFolderButton.addActionListener(e -> openStorageFolderAction());

        leftControls.add(portPrompt);
        leftControls.add(portField);
        leftControls.add(startButton);
        leftControls.add(stopButton);
        leftControls.add(openFolderButton);

        // Stats Badges
        JPanel metricsPanel = new JPanel(new GridLayout(1, 4, 10, 0));
        metricsPanel.setOpaque(false);

        serverIpLabel = createMetricBadge("IP Address", FileTransferServer.getLocalHostAddress());
        boundPortLabel = createMetricBadge("Port", "—");
        activeClientsLabel = createMetricBadge("Active Clients", "0");
        storageCountLabel = createMetricBadge("Vault Files", "0");

        metricsPanel.add(serverIpLabel);
        metricsPanel.add(boundPortLabel);
        metricsPanel.add(activeClientsLabel);
        metricsPanel.add(storageCountLabel);

        controlCard.add(leftControls, BorderLayout.WEST);
        controlCard.add(metricsPanel, BorderLayout.EAST);
        topContainer.add(controlCard);

        add(topContainer, BorderLayout.NORTH);

        // 2. Central Tabs: Clients, Live Log, Vault Files
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(Theme.FONT_BOLD);
        tabbedPane.setBackground(Theme.PANEL_BG);
        tabbedPane.setForeground(Theme.TEXT_PRIMARY);
        tabbedPane.setBorder(new EmptyBorder(0, 16, 0, 16));

        // Tab A: Connected Clients Table
        JPanel clientsPanel = new JPanel(new BorderLayout(0, 8));
        clientsPanel.setBackground(Theme.PANEL_BG);
        clientsPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        String[] clientColumns = {"Client Identifier", "Remote IP", "Remote Port", "Connected At", "Status"};
        clientTableModel = new DefaultTableModel(clientColumns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        clientTable = new JTable(clientTableModel);
        Theme.styleTable(clientTable);
        JScrollPane clientScroll = new JScrollPane(clientTable);
        Theme.styleScrollPane(clientScroll);
        clientsPanel.add(clientScroll, BorderLayout.CENTER);

        tabbedPane.addTab("Connected Clients", clientsPanel);

        // Tab B: Activity Log
        JPanel logPanel = new JPanel(new BorderLayout(0, 8));
        logPanel.setBackground(Theme.PANEL_BG);
        logPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(Theme.FONT_MONO);
        logArea.setBackground(Theme.INPUT_BG);
        logArea.setForeground(Theme.TEXT_PRIMARY);
        logArea.setCaretColor(Theme.ACCENT_GOLD);
        logArea.setBorder(new EmptyBorder(8, 8, 8, 8));
        JScrollPane logScroll = new JScrollPane(logArea);
        Theme.styleScrollPane(logScroll);

        JPanel logActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        logActions.setOpaque(false);
        JButton clearLogBtn = new JButton("Clear Log");
        Theme.styleSecondaryButton(clearLogBtn);
        clearLogBtn.addActionListener(e -> logArea.setText(""));
        logActions.add(clearLogBtn);

        logPanel.add(logScroll, BorderLayout.CENTER);
        logPanel.add(logActions, BorderLayout.SOUTH);

        tabbedPane.addTab("Live Activity Log", logPanel);

        // Tab C: Vault Files
        JPanel vaultPanel = new JPanel(new BorderLayout(0, 8));
        vaultPanel.setBackground(Theme.PANEL_BG);
        vaultPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        String[] vaultColumns = {"File Name", "Size", "Last Modified", "SHA-256 Checksum"};
        vaultTableModel = new DefaultTableModel(vaultColumns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        vaultTable = new JTable(vaultTableModel);
        Theme.styleTable(vaultTable);
        // Let SHA-256 column have wider width
        vaultTable.getColumnModel().getColumn(3).setPreferredWidth(320);
        JScrollPane vaultScroll = new JScrollPane(vaultTable);
        Theme.styleScrollPane(vaultScroll);

        JPanel vaultActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        vaultActions.setOpaque(false);
        JButton refreshBtn = new JButton("Refresh Vault");
        Theme.styleSecondaryButton(refreshBtn);
        refreshBtn.addActionListener(e -> refreshVaultCatalog());
        vaultActions.add(refreshBtn);

        vaultPanel.add(vaultScroll, BorderLayout.CENTER);
        vaultPanel.add(vaultActions, BorderLayout.SOUTH);

        tabbedPane.addTab("Received Files Vault", vaultPanel);

        add(tabbedPane, BorderLayout.CENTER);

        // 3. Footer Bar
        JPanel footerPanel = new JPanel(new BorderLayout());
        footerPanel.setBackground(Theme.CARD_BG);
        footerPanel.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER_COLOR),
                new EmptyBorder(6, 16, 6, 16)
        ));

        JLabel pathLabel = new JLabel("Storage Directory: " + server.getFileService().getStorageDirectory().toAbsolutePath());
        pathLabel.setFont(Theme.FONT_SMALL);
        pathLabel.setForeground(Theme.TEXT_SECONDARY);

        JLabel tipLabel = new JLabel("Ready for concurrent client connections");
        tipLabel.setFont(Theme.FONT_SMALL);
        tipLabel.setForeground(Theme.ACCENT_GOLD);

        footerPanel.add(pathLabel, BorderLayout.WEST);
        footerPanel.add(tipLabel, BorderLayout.EAST);

        add(footerPanel, BorderLayout.SOUTH);

        // Handle window closing: stop server cleanly
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                if (server.isRunning()) {
                    server.stop();
                }
            }
        });
    }

    private JLabel createMetricBadge(String title, String val) {
        JLabel lbl = new JLabel("<html><center><font color='#94A3B8' size='2'>" + title +
                "</font><br><font color='#F8FAFC' size='3'><b>" + val + "</b></font></center></html>");
        lbl.setHorizontalAlignment(JLabel.CENTER);
        lbl.setBorder(new CompoundBorder(
                new LineBorder(Theme.BORDER_COLOR, 1),
                new EmptyBorder(2, 6, 2, 6)
        ));
        lbl.setOpaque(true);
        lbl.setBackground(Theme.PANEL_BG);
        return lbl;
    }

    private void updateMetricBadge(JLabel label, String title, String val) {
        label.setText("<html><center><font color='#94A3B8' size='2'>" + title +
                "</font><br><font color='#F8FAFC' size='3'><b>" + val + "</b></font></center></html>");
    }

    private void startServerAction() {
        String portStr = portField.getText().trim();
        int port;
        try {
            port = Integer.parseInt(portStr);
            if (port < 1024 || port > 65535) {
                showError("Port must be between 1024 and 65535.");
                return;
            }
        } catch (NumberFormatException e) {
            showError("Invalid port number format.");
            return;
        }

        // Run server start in background thread to avoid freezing GUI
        new Thread(() -> {
            try {
                server.start(port);
            } catch (BindException be) {
                SwingUtilities.invokeLater(() -> showError(
                        "Port Conflict Error: Port " + port + " is already in use by another application.\n" +
                        "Please select a different port (e.g. 5001, 5555, 8080)."));
            } catch (IOException ex) {
                SwingUtilities.invokeLater(() -> showError("Failed to start server: " + ex.getMessage()));
            }
        }).start();
    }

    private void stopServerAction() {
        new Thread(server::stop).start();
    }

    private void openStorageFolderAction() {
        try {
            File dir = server.getFileService().getStorageDirectory().toFile();
            if (!dir.exists()) {
                dir.mkdirs();
            }
            Desktop.getDesktop().open(dir);
        } catch (Exception ex) {
            showError("Could not open file manager: " + ex.getMessage());
        }
    }

    private void updateServerStateUI(boolean running) {
        portField.setEnabled(!running);
        startButton.setEnabled(!running);
        stopButton.setEnabled(running);
        Theme.updateStatusBadge(statusBadge, running, "ONLINE", "STOPPED");

        if (running) {
            updateMetricBadge(serverIpLabel, "IP Address", FileTransferServer.getLocalHostAddress());
            updateMetricBadge(boundPortLabel, "Port", String.valueOf(server.getBoundPort()));
        } else {
            updateMetricBadge(boundPortLabel, "Port", "—");
            updateMetricBadge(activeClientsLabel, "Active Clients", "0");
            clientTableModel.setRowCount(0);
        }
    }

    private void refreshVaultCatalog() {
        new Thread(() -> {
            List<FileInfo> files = server.getFileService().listStoredFiles();
            long totalBytes = server.getFileService().getTotalStorageUsed();

            SwingUtilities.invokeLater(() -> {
                vaultTableModel.setRowCount(0);
                for (FileInfo f : files) {
                    vaultTableModel.addRow(new Object[]{
                            f.getFilename(),
                            ChecksumService.formatFileSize(f.getSize()),
                            Instant.ofEpochMilli(f.getLastModified()).atZone(ZoneId.systemDefault()).toLocalDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                            f.getSha256()
                    });
                }
                updateMetricBadge(storageCountLabel, "Vault Files",
                        files.size() + " (" + ChecksumService.formatFileSize(totalBytes) + ")");
            });
        }).start();
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "NetVault Server Error", JOptionPane.ERROR_MESSAGE);
    }

    // ==========================================
    // ServerEventListener Callbacks
    // ==========================================

    @Override
    public void onServerStarted(String hostAddress, int port) {
        SwingUtilities.invokeLater(() -> {
            updateServerStateUI(true);
            refreshVaultCatalog();
        });
    }

    @Override
    public void onServerStopped() {
        SwingUtilities.invokeLater(() -> updateServerStateUI(false));
    }

    @Override
    public void onClientConnected(String clientId, String remoteAddress, int remotePort) {
        SwingUtilities.invokeLater(() -> {
            clientTableModel.addRow(new Object[]{
                    clientId,
                    remoteAddress,
                    remotePort,
                    LocalDateTime.now().format(TIME_FORMATTER),
                    "Active"
            });
            updateMetricBadge(activeClientsLabel, "Active Clients", String.valueOf(server.getActiveClients().size()));
        });
    }

    @Override
    public void onClientDisconnected(String clientId) {
        SwingUtilities.invokeLater(() -> {
            for (int i = 0; i < clientTableModel.getRowCount(); i++) {
                if (clientId.equals(clientTableModel.getValueAt(i, 0))) {
                    clientTableModel.removeRow(i);
                    break;
                }
            }
            updateMetricBadge(activeClientsLabel, "Active Clients", String.valueOf(server.getActiveClients().size()));
        });
    }

    @Override
    public void onTransferProgress(String clientId, String filename, String operation, long transferred, long total) {
        // Handled silently or in status bar to avoid flooding table
    }

    @Override
    public void onTransferCompleted(TransferRecord record) {
        SwingUtilities.invokeLater(() -> {
            refreshVaultCatalog();
            String prefix = record.getIntegrityResult().startsWith("Passed") ? "[SUCCESS]" : "[FAILED]";
            logArea.append(String.format("%s %s %s '%s' (%s) - Integrity: %s%n",
                    LocalDateTime.now().format(TIME_FORMATTER),
                    prefix,
                    record.getOperation(),
                    record.getFilename(),
                    ChecksumService.formatFileSize(record.getFileSize()),
                    record.getIntegrityResult()
            ));
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    @Override
    public void onLogActivity(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(String.format("%s [INFO]  %s%n",
                    LocalDateTime.now().format(TIME_FORMATTER), message));
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    @Override
    public void onLogError(String message, Throwable throwable) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(String.format("%s [ERROR] %s%n",
                    LocalDateTime.now().format(TIME_FORMATTER), message));
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }
}
