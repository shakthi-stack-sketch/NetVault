package com.netvault.ui;

import com.netvault.client.FileTransferClient;
import com.netvault.model.FileInfo;
import com.netvault.model.TransferRecord;
import com.netvault.service.ChecksumService;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
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
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Modern Swing graphical user interface for the NetVault Client.
 * Manages TCP network connections, file uploads, file downloads,
 * live progress indicators, session history, and cryptographic integrity verification.
 */
public class ClientFrame extends JFrame {

    private final FileTransferClient client;

    // Connection Controls
    private JTextField hostField;
    private JTextField portField;
    private JButton connectBtn;
    private JButton disconnectBtn;
    private JLabel connectionBadge;

    // Upload Controls
    private File selectedUploadFile;
    private JLabel uploadFileNameLabel;
    private JLabel uploadFileSizeLabel;
    private JLabel uploadShaLabel;
    private JButton browseBtn;
    private JButton uploadBtn;

    // Download Controls
    private JComboBox<FileInfo> serverFilesCombo;
    private DefaultComboBoxModel<FileInfo> comboModel;
    private JButton refreshFilesBtn;
    private JButton downloadBtn;

    // Progress Controls
    private JLabel transferStatusLabel;
    private JProgressBar progressBar;
    private JLabel transferMetricsLabel;

    // History and Logs
    private DefaultTableModel historyTableModel;
    private JTable historyTable;
    private JTextArea logArea;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    public ClientFrame() {
        this(new FileTransferClient());
    }

    public ClientFrame(FileTransferClient client) {
        this.client = client;
        initUI();
        updateConnectionStateUI(false);
    }

    private void initUI() {
        setTitle("NetVault — Client Workstation");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(1020, 720);
        setMinimumSize(new Dimension(880, 580));
        setLocationRelativeTo(null);
        getContentPane().setBackground(Theme.BG_DARK);
        setLayout(new BorderLayout(0, 8));

        // 1. Header & Connection Bar
        JPanel topContainer = new JPanel();
        topContainer.setLayout(new BoxLayout(topContainer, BoxLayout.Y_AXIS));
        topContainer.setBackground(Theme.BG_DARK);
        topContainer.setBorder(new EmptyBorder(12, 16, 6, 16));

        // Banner
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(Theme.BG_DARK);

        JPanel titlesPanel = new JPanel();
        titlesPanel.setLayout(new BoxLayout(titlesPanel, BoxLayout.Y_AXIS));
        titlesPanel.setBackground(Theme.BG_DARK);

        JLabel titleLabel = new JLabel("NETVAULT  //  CLIENT WORKSTATION");
        titleLabel.setFont(Theme.FONT_TITLE);
        titleLabel.setForeground(Theme.ACCENT_GOLD);

        JLabel subtitleLabel = new JLabel("Secure Network File Transfer with SHA-256 Cryptographic Verification");
        subtitleLabel.setFont(Theme.FONT_REGULAR);
        subtitleLabel.setForeground(Theme.TEXT_SECONDARY);

        titlesPanel.add(titleLabel);
        titlesPanel.add(Box.createVerticalStrut(2));
        titlesPanel.add(subtitleLabel);

        connectionBadge = new JLabel();
        Theme.updateStatusBadge(connectionBadge, false, "CONNECTED", "DISCONNECTED");

        headerPanel.add(titlesPanel, BorderLayout.WEST);
        headerPanel.add(connectionBadge, BorderLayout.EAST);
        topContainer.add(headerPanel);
        topContainer.add(Box.createVerticalStrut(10));

        // Connection Card
        JPanel connectionCard = Theme.createCardPanel();
        connectionCard.setLayout(new FlowLayout(FlowLayout.LEFT, 10, 2));

        JLabel ipPrompt = new JLabel("Server IP:");
        ipPrompt.setFont(Theme.FONT_BOLD);
        ipPrompt.setForeground(Theme.TEXT_PRIMARY);

        hostField = new JTextField("127.0.0.1", 12);
        Theme.styleTextField(hostField);

        JLabel portPrompt = new JLabel("Port:");
        portPrompt.setFont(Theme.FONT_BOLD);
        portPrompt.setForeground(Theme.TEXT_PRIMARY);

        portField = new JTextField("5000", 6);
        Theme.styleTextField(portField);

        connectBtn = new JButton("Connect");
        Theme.stylePrimaryButton(connectBtn);
        connectBtn.addActionListener(e -> connectAction());

        disconnectBtn = new JButton("Disconnect");
        Theme.styleDangerButton(disconnectBtn);
        disconnectBtn.addActionListener(e -> disconnectAction());

        connectionCard.add(ipPrompt);
        connectionCard.add(hostField);
        connectionCard.add(portPrompt);
        connectionCard.add(portField);
        connectionCard.add(connectBtn);
        connectionCard.add(disconnectBtn);

        topContainer.add(connectionCard);
        add(topContainer, BorderLayout.NORTH);

        // 2. Central Content: Transfer Panels & Tabs
        JPanel centerContainer = new JPanel(new BorderLayout(0, 10));
        centerContainer.setBackground(Theme.BG_DARK);
        centerContainer.setBorder(new EmptyBorder(0, 16, 0, 16));

        // Transfer Panel (Upload Card + Download Card)
        JPanel operationsPanel = new JPanel(new GridLayout(1, 2, 12, 0));
        operationsPanel.setOpaque(false);

        // Upload Card
        JPanel uploadCard = Theme.createCardPanel();
        uploadCard.setLayout(new BorderLayout(0, 8));

        JLabel uploadTitle = new JLabel("Upload File to Vault");
        uploadTitle.setFont(Theme.FONT_HEADER);
        uploadTitle.setForeground(Theme.ACCENT_GOLD);

        JPanel uploadDetails = new JPanel();
        uploadDetails.setLayout(new BoxLayout(uploadDetails, BoxLayout.Y_AXIS));
        uploadDetails.setOpaque(false);

        uploadFileNameLabel = new JLabel("Selected File: None");
        uploadFileNameLabel.setFont(Theme.FONT_REGULAR);
        uploadFileNameLabel.setForeground(Theme.TEXT_PRIMARY);

        uploadFileSizeLabel = new JLabel("Size: —");
        uploadFileSizeLabel.setFont(Theme.FONT_SMALL);
        uploadFileSizeLabel.setForeground(Theme.TEXT_SECONDARY);

        uploadShaLabel = new JLabel("SHA-256: —");
        uploadShaLabel.setFont(Theme.FONT_SMALL);
        uploadShaLabel.setForeground(Theme.TEXT_MUTED);

        uploadDetails.add(uploadFileNameLabel);
        uploadDetails.add(Box.createVerticalStrut(4));
        uploadDetails.add(uploadFileSizeLabel);
        uploadDetails.add(Box.createVerticalStrut(4));
        uploadDetails.add(uploadShaLabel);

        JPanel uploadButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        uploadButtons.setOpaque(false);

        browseBtn = new JButton("Browse File...");
        Theme.styleSecondaryButton(browseBtn);
        browseBtn.addActionListener(e -> browseFileAction());

        uploadBtn = new JButton("Upload File");
        Theme.stylePrimaryButton(uploadBtn);
        uploadBtn.setEnabled(false);
        uploadBtn.addActionListener(e -> uploadFileAction());

        uploadButtons.add(browseBtn);
        uploadButtons.add(uploadBtn);

        uploadCard.add(uploadTitle, BorderLayout.NORTH);
        uploadCard.add(uploadDetails, BorderLayout.CENTER);
        uploadCard.add(uploadButtons, BorderLayout.SOUTH);

        // Download Card
        JPanel downloadCard = Theme.createCardPanel();
        downloadCard.setLayout(new BorderLayout(0, 8));

        JLabel downloadTitle = new JLabel("Download File from Vault");
        downloadTitle.setFont(Theme.FONT_HEADER);
        downloadTitle.setForeground(Theme.ACCENT_GOLD);

        JPanel downloadDetails = new JPanel();
        downloadDetails.setLayout(new BoxLayout(downloadDetails, BoxLayout.Y_AXIS));
        downloadDetails.setOpaque(false);

        JLabel selectLabel = new JLabel("Available Files on Server:");
        selectLabel.setFont(Theme.FONT_REGULAR);
        selectLabel.setForeground(Theme.TEXT_PRIMARY);

        comboModel = new DefaultComboBoxModel<>();
        serverFilesCombo = new JComboBox<>(comboModel);
        serverFilesCombo.setFont(Theme.FONT_REGULAR);
        serverFilesCombo.setBackground(Theme.INPUT_BG);
        serverFilesCombo.setForeground(Theme.TEXT_PRIMARY);

        downloadDetails.add(selectLabel);
        downloadDetails.add(Box.createVerticalStrut(6));
        downloadDetails.add(serverFilesCombo);

        JPanel downloadButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        downloadButtons.setOpaque(false);

        refreshFilesBtn = new JButton("Refresh Catalog");
        Theme.styleSecondaryButton(refreshFilesBtn);
        refreshFilesBtn.addActionListener(e -> refreshServerFilesAction());

        downloadBtn = new JButton("Download Selected");
        Theme.stylePrimaryButton(downloadBtn);
        downloadBtn.setEnabled(false);
        downloadBtn.addActionListener(e -> downloadFileAction());

        downloadButtons.add(refreshFilesBtn);
        downloadButtons.add(downloadBtn);

        downloadCard.add(downloadTitle, BorderLayout.NORTH);
        downloadCard.add(downloadDetails, BorderLayout.CENTER);
        downloadCard.add(downloadButtons, BorderLayout.SOUTH);

        operationsPanel.add(uploadCard);
        operationsPanel.add(downloadCard);

        // Progress Panel
        JPanel progressCard = Theme.createCardPanel();
        progressCard.setLayout(new BorderLayout(0, 6));

        JPanel progressHeader = new JPanel(new BorderLayout());
        progressHeader.setOpaque(false);

        transferStatusLabel = new JLabel("Ready for transfer operations");
        transferStatusLabel.setFont(Theme.FONT_BOLD);
        transferStatusLabel.setForeground(Theme.TEXT_PRIMARY);

        transferMetricsLabel = new JLabel("0 / 0 B (0%)");
        transferMetricsLabel.setFont(Theme.FONT_SMALL);
        transferMetricsLabel.setForeground(Theme.TEXT_SECONDARY);

        progressHeader.add(transferStatusLabel, BorderLayout.WEST);
        progressHeader.add(transferMetricsLabel, BorderLayout.EAST);

        progressBar = new JProgressBar(0, 100);
        Theme.styleProgressBar(progressBar);
        progressBar.setPreferredSize(new Dimension(progressBar.getPreferredSize().width, 22));

        progressCard.add(progressHeader, BorderLayout.NORTH);
        progressCard.add(progressBar, BorderLayout.CENTER);

        JPanel transferSection = new JPanel(new BorderLayout(0, 8));
        transferSection.setOpaque(false);
        transferSection.add(operationsPanel, BorderLayout.CENTER);
        transferSection.add(progressCard, BorderLayout.SOUTH);

        centerContainer.add(transferSection, BorderLayout.NORTH);

        // Lower Tabs: Session Transfer History & Live Log
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(Theme.FONT_BOLD);
        tabbedPane.setBackground(Theme.PANEL_BG);
        tabbedPane.setForeground(Theme.TEXT_PRIMARY);

        // History Tab
        JPanel historyPanel = new JPanel(new BorderLayout());
        historyPanel.setBackground(Theme.PANEL_BG);
        historyPanel.setBorder(new EmptyBorder(8, 8, 8, 8));

        String[] historyColumns = {"Filename", "Operation", "File Size", "Start Time", "Status", "Integrity Result", "SHA-256 Hash"};
        historyTableModel = new DefaultTableModel(historyColumns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        historyTable = new JTable(historyTableModel);
        Theme.styleTable(historyTable);
        historyTable.getColumnModel().getColumn(5).setPreferredWidth(160);
        historyTable.getColumnModel().getColumn(6).setPreferredWidth(280);

        JScrollPane historyScroll = new JScrollPane(historyTable);
        Theme.styleScrollPane(historyScroll);
        historyPanel.add(historyScroll, BorderLayout.CENTER);

        tabbedPane.addTab("Transfer History (Current Session)", historyPanel);

        // Console Tab
        JPanel logPanel = new JPanel(new BorderLayout());
        logPanel.setBackground(Theme.PANEL_BG);
        logPanel.setBorder(new EmptyBorder(8, 8, 8, 8));

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(Theme.FONT_MONO);
        logArea.setBackground(Theme.INPUT_BG);
        logArea.setForeground(Theme.TEXT_PRIMARY);
        logArea.setCaretColor(Theme.ACCENT_GOLD);
        logArea.setBorder(new EmptyBorder(6, 6, 6, 6));

        JScrollPane logScroll = new JScrollPane(logArea);
        Theme.styleScrollPane(logScroll);
        logPanel.add(logScroll, BorderLayout.CENTER);

        tabbedPane.addTab("Client Network Console", logPanel);

        centerContainer.add(tabbedPane, BorderLayout.CENTER);
        add(centerContainer, BorderLayout.CENTER);

        // 3. Footer Bar
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBackground(Theme.CARD_BG);
        footer.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER_COLOR),
                new EmptyBorder(6, 16, 6, 16)
        ));

        JLabel info = new JLabel("NetVault Secure TCP Socket Protocol — Active Hash: SHA-256");
        info.setFont(Theme.FONT_SMALL);
        info.setForeground(Theme.TEXT_SECONDARY);

        footer.add(info, BorderLayout.WEST);
        add(footer, BorderLayout.SOUTH);

        // Window closing: disconnect cleanly
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                if (client.isConnected()) {
                    client.disconnect();
                }
            }
        });
    }

    private void updateConnectionStateUI(boolean connected) {
        hostField.setEnabled(!connected);
        portField.setEnabled(!connected);
        connectBtn.setEnabled(!connected);
        disconnectBtn.setEnabled(connected);

        browseBtn.setEnabled(connected);
        uploadBtn.setEnabled(connected && selectedUploadFile != null);
        refreshFilesBtn.setEnabled(connected);
        downloadBtn.setEnabled(connected && comboModel.getSize() > 0);

        Theme.updateStatusBadge(connectionBadge, connected, "CONNECTED", "DISCONNECTED");

        if (!connected) {
            comboModel.removeAllElements();
            transferStatusLabel.setText("Disconnected from server.");
            transferStatusLabel.setForeground(Theme.TEXT_MUTED);
            progressBar.setValue(0);
            progressBar.setString("0%");
            transferMetricsLabel.setText("0 / 0 B (0%)");
        }
    }

    private void connectAction() {
        String host = hostField.getText().trim();
        String portStr = portField.getText().trim();

        if (host.isEmpty()) {
            showError("Server IP address cannot be empty.");
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portStr);
            if (port < 1 || port > 65535) {
                showError("Port must be between 1 and 65535.");
                return;
            }
        } catch (NumberFormatException e) {
            showError("Invalid port number format.");
            return;
        }

        connectBtn.setEnabled(false);
        logInfo("Connecting to NetVault server at " + host + ":" + port + "...");

        new Thread(() -> {
            try {
                client.connect(host, port, 5000);
                SwingUtilities.invokeLater(() -> {
                    updateConnectionStateUI(true);
                    logInfo("Successfully connected to " + host + ":" + port + " (Handshake verified).");
                    refreshServerFilesAction();
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    updateConnectionStateUI(false);
                    showError("Connection Failed: " + ex.getMessage() + "\n" +
                            "Verify that the NetVault server is running and firewall allows TCP port " + port);
                    logError("Connection failed: " + ex.getMessage());
                });
            }
        }).start();
    }

    private void disconnectAction() {
        new Thread(() -> {
            client.disconnect();
            SwingUtilities.invokeLater(() -> {
                updateConnectionStateUI(false);
                logInfo("Disconnected from server.");
            });
        }).start();
    }

    private void browseFileAction() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select File to Upload");
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            selectedUploadFile = chooser.getSelectedFile();
            uploadFileNameLabel.setText("Selected: " + selectedUploadFile.getName());
            uploadFileSizeLabel.setText("Size: " + ChecksumService.formatFileSize(selectedUploadFile.length()));
            uploadShaLabel.setText("SHA-256: Computing...");
            uploadBtn.setEnabled(client.isConnected());

            // Compute local checksum preview in background
            new Thread(() -> {
                try {
                    String hash = ChecksumService.calculateChecksum(selectedUploadFile);
                    SwingUtilities.invokeLater(() -> {
                        uploadShaLabel.setText("SHA-256: " + hash);
                        logInfo(String.format("Calculated local checksum for '%s': %s",
                                selectedUploadFile.getName(), hash));
                    });
                } catch (IOException e) {
                    SwingUtilities.invokeLater(() -> uploadShaLabel.setText("SHA-256: Error computing checksum"));
                }
            }).start();
        }
    }

    private void uploadFileAction() {
        if (selectedUploadFile == null || !selectedUploadFile.exists()) {
            showError("Please select a valid file to upload.");
            return;
        }

        setTransferControlsEnabled(false);
        transferStatusLabel.setText("Uploading: " + selectedUploadFile.getName());
        transferStatusLabel.setForeground(Theme.WARNING_AMBER);
        progressBar.setValue(0);

        long totalSize = selectedUploadFile.length();
        long startTime = System.currentTimeMillis();

        new Thread(() -> {
            try {
                logInfo(String.format("Initiating upload of '%s' (%s)...",
                        selectedUploadFile.getName(), ChecksumService.formatFileSize(totalSize)));

                TransferRecord record = client.uploadFile(selectedUploadFile, (transferred, total) -> {
                    int percent = total > 0 ? (int) ((transferred * 100) / total) : 0;
                    long elapsed = Math.max(1, System.currentTimeMillis() - startTime);
                    double speedKb = (transferred / 1024.0) / (elapsed / 1000.0);

                    SwingUtilities.invokeLater(() -> {
                        progressBar.setValue(percent);
                        progressBar.setString(percent + "%");
                        transferMetricsLabel.setText(String.format("%s / %s (%.1f KB/s)",
                                ChecksumService.formatFileSize(transferred),
                                ChecksumService.formatFileSize(total),
                                speedKb));
                    });
                });

                SwingUtilities.invokeLater(() -> {
                    transferStatusLabel.setText("Upload Completed: Integrity Verification Passed! (SHA-256 Match)");
                    transferStatusLabel.setForeground(Theme.SUCCESS_GREEN);
                    addHistoryRow(record);
                    logInfo(String.format("Upload SUCCESS for '%s' in %d ms. Checksum: %s",
                            record.getFilename(), record.getDurationMs(), record.getSha256Hash()));
                    JOptionPane.showMessageDialog(this,
                            String.format("Transfer completed successfully!\n\n" +
                                            "File: %s\nOriginal Size: %s\nReceived Size: %s\n" +
                                            "SHA-256 Verification: PASSED\nChecksum: %s",
                                    record.getFilename(),
                                    ChecksumService.formatFileSize(record.getFileSize()),
                                    ChecksumService.formatFileSize(record.getFileSize()),
                                    record.getSha256Hash()),
                            "NetVault Upload Success",
                            JOptionPane.INFORMATION_MESSAGE);
                    refreshServerFilesAction();
                });

            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    transferStatusLabel.setText("Upload Failed: " + ex.getMessage());
                    transferStatusLabel.setForeground(Theme.ERROR_RED);
                    logError("Upload failed: " + ex.getMessage());
                    TransferRecord failedRecord = new TransferRecord(selectedUploadFile.getName(), "Upload", totalSize);
                    failedRecord.setCompletionStatus("Failed");
                    failedRecord.setIntegrityResult("Failed");
                    failedRecord.setErrorMessage(ex.getMessage());
                    addHistoryRow(failedRecord);
                    showError("Upload Failed: " + ex.getMessage());
                });
            } finally {
                SwingUtilities.invokeLater(() -> setTransferControlsEnabled(true));
            }
        }).start();
    }

    private void refreshServerFilesAction() {
        if (!client.isConnected()) return;

        new Thread(() -> {
            try {
                List<FileInfo> files = client.listFiles();
                SwingUtilities.invokeLater(() -> {
                    comboModel.removeAllElements();
                    for (FileInfo f : files) {
                        comboModel.addElement(f);
                    }
                    downloadBtn.setEnabled(comboModel.getSize() > 0);
                    logInfo("Retrieved server catalog (" + files.size() + " files available).");
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    logError("Failed to fetch server catalog: " + e.getMessage());
                });
            }
        }).start();
    }

    private void downloadFileAction() {
        FileInfo selected = (FileInfo) serverFilesCombo.getSelectedItem();
        if (selected == null) {
            showError("Please select a file to download.");
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save Downloaded File As...");
        chooser.setSelectedFile(new File(selected.getFilename()));

        int result = chooser.showSaveDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File destination = chooser.getSelectedFile();
        if (destination.exists()) {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "File '" + destination.getName() + "' already exists. Overwrite?",
                    "Confirm Overwrite", JOptionPane.YES_NO_OPTION);
            if (confirm != JOptionPane.YES_OPTION) {
                return;
            }
        }

        setTransferControlsEnabled(false);
        transferStatusLabel.setText("Downloading: " + selected.getFilename());
        transferStatusLabel.setForeground(Theme.WARNING_AMBER);
        progressBar.setValue(0);

        long startTime = System.currentTimeMillis();

        new Thread(() -> {
            try {
                logInfo(String.format("Starting download of '%s' (%s)...",
                        selected.getFilename(), ChecksumService.formatFileSize(selected.getSize())));

                TransferRecord record = client.downloadFile(selected.getFilename(), destination, (transferred, total) -> {
                    int percent = total > 0 ? (int) ((transferred * 100) / total) : 0;
                    long elapsed = Math.max(1, System.currentTimeMillis() - startTime);
                    double speedKb = (transferred / 1024.0) / (elapsed / 1000.0);

                    SwingUtilities.invokeLater(() -> {
                        progressBar.setValue(percent);
                        progressBar.setString(percent + "%");
                        transferMetricsLabel.setText(String.format("%s / %s (%.1f KB/s)",
                                ChecksumService.formatFileSize(transferred),
                                ChecksumService.formatFileSize(total),
                                speedKb));
                    });
                });

                SwingUtilities.invokeLater(() -> {
                    transferStatusLabel.setText("Download Completed: Integrity Verification Passed! (SHA-256 Match)");
                    transferStatusLabel.setForeground(Theme.SUCCESS_GREEN);
                    addHistoryRow(record);
                    logInfo(String.format("Download SUCCESS for '%s' in %d ms. Checksum: %s",
                            record.getFilename(), record.getDurationMs(), record.getSha256Hash()));
                    JOptionPane.showMessageDialog(this,
                            String.format("Download completed successfully!\n\n" +
                                            "File: %s\nOriginal Server Size: %s\nReceived Size: %s\n" +
                                            "SHA-256 Verification: PASSED\nChecksum: %s\nSaved to: %s",
                                    record.getFilename(),
                                    ChecksumService.formatFileSize(selected.getSize()),
                                    ChecksumService.formatFileSize(destination.length()),
                                    record.getSha256Hash(),
                                    destination.getAbsolutePath()),
                            "NetVault Download Success",
                            JOptionPane.INFORMATION_MESSAGE);
                });

            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    transferStatusLabel.setText("Download Failed: " + ex.getMessage());
                    transferStatusLabel.setForeground(Theme.ERROR_RED);
                    logError("Download failed: " + ex.getMessage());
                    TransferRecord failedRecord = new TransferRecord(selected.getFilename(), "Download", selected.getSize());
                    failedRecord.setCompletionStatus("Failed");
                    failedRecord.setIntegrityResult("Failed");
                    failedRecord.setErrorMessage(ex.getMessage());
                    addHistoryRow(failedRecord);
                    showError("Download Failed: " + ex.getMessage());
                });
            } finally {
                SwingUtilities.invokeLater(() -> setTransferControlsEnabled(true));
            }
        }).start();
    }

    private void setTransferControlsEnabled(boolean enabled) {
        browseBtn.setEnabled(enabled);
        uploadBtn.setEnabled(enabled && selectedUploadFile != null);
        refreshFilesBtn.setEnabled(enabled);
        downloadBtn.setEnabled(enabled && comboModel.getSize() > 0);
        disconnectBtn.setEnabled(enabled);
    }

    private void addHistoryRow(TransferRecord record) {
        historyTableModel.addRow(new Object[]{
                record.getFilename(),
                record.getOperation(),
                ChecksumService.formatFileSize(record.getFileSize()),
                record.getStartTime(),
                record.getCompletionStatus(),
                record.getIntegrityResult(),
                record.getSha256Hash()
        });
    }

    private void logInfo(String msg) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(String.format("%s [INFO]  %s%n",
                    LocalDateTime.now().format(TIME_FORMATTER), msg));
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private void logError(String msg) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(String.format("%s [ERROR] %s%n",
                    LocalDateTime.now().format(TIME_FORMATTER), msg));
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, "NetVault Client Error", JOptionPane.ERROR_MESSAGE);
    }
}
