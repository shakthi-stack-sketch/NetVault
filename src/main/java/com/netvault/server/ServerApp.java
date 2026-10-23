package com.netvault.server;

import com.netvault.ui.ServerFrame;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Dedicated entry point for launching the NetVault Server standalone application.
 */
public class ServerApp {

    public static void main(String[] args) {
        // Attempt to configure system look and feel
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        SwingUtilities.invokeLater(() -> {
            ServerFrame frame = new ServerFrame();
            frame.setVisible(true);
        });
    }
}
