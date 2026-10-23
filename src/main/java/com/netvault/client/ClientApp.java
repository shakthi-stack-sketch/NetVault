package com.netvault.client;

import com.netvault.ui.ClientFrame;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Dedicated entry point for launching the NetVault Client standalone workstation.
 */
public class ClientApp {

    public static void main(String[] args) {
        // Attempt to configure system look and feel
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        SwingUtilities.invokeLater(() -> {
            ClientFrame frame = new ClientFrame();
            frame.setVisible(true);
        });
    }
}
