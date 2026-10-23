package com.netvault;

import com.netvault.client.ClientApp;
import com.netvault.server.FileTransferServer;
import com.netvault.server.ServerApp;
import com.netvault.ui.ClientFrame;
import com.netvault.ui.ServerFrame;
import com.netvault.ui.Theme;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;

/**
 * NetVault Application Entry Point.
 *
 * Supports launching via command-line arguments:
 *   java -jar netvault.jar server
 *   java -jar netvault.jar client
 *   java -jar netvault.jar --headless-server [port]
 *
 * If no arguments are provided, displays the NetVault Interactive Desktop Launcher.
 */
public class Main {

    public static void main(String[] args) {
        if (args.length > 0) {
            String command = args[0].trim().toLowerCase();
            if (command.equals("server")) {
                ServerApp.main(args);
                return;
            } else if (command.equals("client")) {
                ClientApp.main(args);
                return;
            } else if (command.equals("--headless-server")) {
                int port = FileTransferServer.DEFAULT_PORT;
                if (args.length > 1) {
                    try {
                        port = Integer.parseInt(args[1]);
                    } catch (NumberFormatException ignored) {}
                }
                startHeadlessServer(port);
                return;
            }
        }

        // Default: Interactive Launcher GUI
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(Main::showLauncher);
    }

    private static void showLauncher() {
        JFrame launcher = new JFrame("NetVault — System Launcher");
        launcher.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        launcher.setSize(520, 360);
        launcher.setResizable(false);
        launcher.setLocationRelativeTo(null);
        launcher.getContentPane().setBackground(Theme.BG_DARK);
        launcher.setLayout(new BorderLayout(0, 16));

        // Header Panel
        JPanel headerPanel = new JPanel();
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
        headerPanel.setBackground(Theme.BG_DARK);
        headerPanel.setBorder(new EmptyBorder(24, 24, 0, 24));

        JLabel titleLabel = new JLabel("NETVAULT");
        titleLabel.setFont(Theme.FONT_TITLE);
        titleLabel.setForeground(Theme.ACCENT_GOLD);
        titleLabel.setAlignmentX(JLabel.CENTER_ALIGNMENT);

        JLabel subtitleLabel = new JLabel("Secure Network File Transfer System (TCP/IP)");
        subtitleLabel.setFont(Theme.FONT_SUBTITLE);
        subtitleLabel.setForeground(Theme.TEXT_PRIMARY);
        subtitleLabel.setAlignmentX(JLabel.CENTER_ALIGNMENT);

        JLabel descLabel = new JLabel("Select a component to launch or run both for local testing.");
        descLabel.setFont(Theme.FONT_REGULAR);
        descLabel.setForeground(Theme.TEXT_SECONDARY);
        descLabel.setAlignmentX(JLabel.CENTER_ALIGNMENT);

        headerPanel.add(titleLabel);
        headerPanel.add(Box.createVerticalStrut(4));
        headerPanel.add(subtitleLabel);
        headerPanel.add(Box.createVerticalStrut(6));
        headerPanel.add(descLabel);

        launcher.add(headerPanel, BorderLayout.NORTH);

        // Buttons Panel
        JPanel buttonsPanel = new JPanel();
        buttonsPanel.setLayout(new BoxLayout(buttonsPanel, BoxLayout.Y_AXIS));
        buttonsPanel.setBackground(Theme.PANEL_BG);
        buttonsPanel.setBorder(new EmptyBorder(20, 40, 20, 40));

        JButton serverBtn = new JButton("Launch Server Application");
        Theme.stylePrimaryButton(serverBtn);
        serverBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        serverBtn.setAlignmentX(JButton.CENTER_ALIGNMENT);
        serverBtn.addActionListener(e -> {
            new ServerFrame().setVisible(true);
            launcher.dispose();
        });

        JButton clientBtn = new JButton("Launch Client Workstation");
        Theme.stylePrimaryButton(clientBtn);
        clientBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        clientBtn.setAlignmentX(JButton.CENTER_ALIGNMENT);
        clientBtn.addActionListener(e -> {
            new ClientFrame().setVisible(true);
            launcher.dispose();
        });

        JButton bothBtn = new JButton("Launch Both (Side-by-Side Test)");
        Theme.styleSecondaryButton(bothBtn);
        bothBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        bothBtn.setAlignmentX(JButton.CENTER_ALIGNMENT);
        bothBtn.addActionListener(e -> {
            launchBothSideBySide();
            launcher.dispose();
        });

        buttonsPanel.add(serverBtn);
        buttonsPanel.add(Box.createVerticalStrut(10));
        buttonsPanel.add(clientBtn);
        buttonsPanel.add(Box.createVerticalStrut(10));
        buttonsPanel.add(bothBtn);

        launcher.add(buttonsPanel, BorderLayout.CENTER);

        // Footer
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.CENTER));
        footer.setBackground(Theme.BG_DARK);
        footer.setBorder(new EmptyBorder(0, 0, 12, 0));

        JLabel verLabel = new JLabel("Computer Networks Laboratory Project • Java TCP Sockets • SHA-256");
        verLabel.setFont(Theme.FONT_SMALL);
        verLabel.setForeground(Theme.TEXT_MUTED);
        footer.add(verLabel);

        launcher.add(footer, BorderLayout.SOUTH);

        launcher.setVisible(true);
    }

    private static void launchBothSideBySide() {
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int halfWidth = screen.width / 2;
        int height = screen.height;

        ServerFrame serverFrame = new ServerFrame();
        serverFrame.setBounds(screen.x, screen.y, halfWidth - 5, height);
        serverFrame.setVisible(true);

        ClientFrame clientFrame = new ClientFrame();
        clientFrame.setBounds(screen.x + halfWidth + 5, screen.y, halfWidth - 5, height);
        clientFrame.setVisible(true);
    }

    private static void startHeadlessServer(int port) {
        System.out.println("Starting NetVault Headless Server on port " + port + "...");
        FileTransferServer server = new FileTransferServer();
        server.addListener(new com.netvault.server.ServerEventListener() {
            @Override
            public void onServerStarted(String hostAddress, int p) {
                System.out.println(">>> Server ONLINE at " + hostAddress + ":" + p);
            }
            @Override
            public void onServerStopped() {
                System.out.println(">>> Server STOPPED.");
            }
            @Override
            public void onClientConnected(String clientId, String ip, int clientPort) {
                System.out.println(">>> Client connected: " + clientId);
            }
            @Override
            public void onClientDisconnected(String clientId) {
                System.out.println(">>> Client disconnected: " + clientId);
            }
            @Override
            public void onTransferProgress(String clientId, String fn, String op, long cur, long total) {}
            @Override
            public void onTransferCompleted(com.netvault.model.TransferRecord rec) {
                System.out.println(String.format(">>> Transfer complete: %s %s [%s] Checksum: %s",
                        rec.getOperation(), rec.getFilename(), rec.getIntegrityResult(), rec.getSha256Hash()));
            }
            @Override
            public void onLogActivity(String msg) {
                System.out.println("[INFO]  " + msg);
            }
            @Override
            public void onLogError(String msg, Throwable t) {
                System.err.println("[ERROR] " + msg);
            }
        });

        try {
            server.start(port);
            System.out.println("Press Ctrl+C to stop the server.");
            Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
            Thread.currentThread().join();
        } catch (Exception e) {
            System.err.println("Fatal server error: " + e.getMessage());
        }
    }
}
