package com.netvault.ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.plaf.basic.BasicProgressBarUI;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * NetVault Design System: Theme Constants and Swing Component Stylers.
 * Dark Navy, Charcoal, Champagne-Gold aesthetic with modern typography and spacing.
 */
public final class Theme {

    // Color Palette
    public static final Color BG_DARK = new Color(15, 23, 42);             // #0F172A (Deep Dark Navy)
    public static final Color PANEL_BG = new Color(30, 41, 59);           // #1E293B (Slate Charcoal)
    public static final Color CARD_BG = new Color(39, 51, 73);            // #273349 (Raised Card Navy)
    public static final Color INPUT_BG = new Color(18, 25, 38);           // #121926 (Input Field Navy)
    public static final Color ACCENT_GOLD = new Color(212, 175, 55);      // #D4AF37 (Champagne Gold)
    public static final Color ACCENT_GOLD_HOVER = new Color(230, 195, 80);
    public static final Color ACCENT_GOLD_DARK = new Color(175, 140, 35);
    public static final Color TEXT_PRIMARY = new Color(248, 250, 252);     // #F8FAFC (White/Bright Ice)
    public static final Color TEXT_SECONDARY = new Color(148, 163, 184);   // #94A3B8 (Muted Slate)
    public static final Color TEXT_MUTED = new Color(100, 116, 139);       // #64748B
    public static final Color SUCCESS_GREEN = new Color(34, 197, 94);     // #22C55E (Vibrant Emerald)
    public static final Color ERROR_RED = new Color(239, 68, 68);         // #EF4444 (Vibrant Rose Red)
    public static final Color WARNING_AMBER = new Color(245, 158, 11);    // #F59E0B (Warm Amber)
    public static final Color BORDER_COLOR = new Color(51, 65, 85);       // #334155 (Subtle Divider)
    public static final Color TABLE_ROW_ALT = new Color(25, 34, 49);

    // Typography
    public static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 18);
    public static final Font FONT_HEADER = new Font("Segoe UI", Font.BOLD, 15);
    public static final Font FONT_SUBTITLE = new Font("Segoe UI", Font.BOLD, 13);
    public static final Font FONT_REGULAR = new Font("Segoe UI", Font.PLAIN, 12);
    public static final Font FONT_BOLD = new Font("Segoe UI", Font.BOLD, 12);
    public static final Font FONT_SMALL = new Font("Segoe UI", Font.PLAIN, 11);
    public static final Font FONT_MONO = new Font("Consolas", Font.PLAIN, 12);

    private Theme() {}

    /**
     * Styles standard panels with dark charcoal background.
     */
    public static JPanel createPanel() {
        JPanel p = new JPanel();
        p.setBackground(PANEL_BG);
        return p;
    }

    /**
     * Styles card containers with distinct background and subtle borders.
     */
    public static JPanel createCardPanel() {
        JPanel p = new JPanel();
        p.setBackground(CARD_BG);
        p.setBorder(new CompoundBorder(
                new LineBorder(BORDER_COLOR, 1, true),
                new EmptyBorder(10, 12, 10, 12)
        ));
        return p;
    }

    /**
     * Styles a primary champagne-gold action button.
     */
    public static void stylePrimaryButton(JButton btn) {
        btn.setFont(FONT_BOLD);
        btn.setBackground(ACCENT_GOLD);
        btn.setForeground(BG_DARK);
        btn.setFocusPainted(false);
        btn.setBorder(new CompoundBorder(
                new LineBorder(ACCENT_GOLD_DARK, 1),
                new EmptyBorder(7, 16, 7, 16)
        ));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (btn.isEnabled()) btn.setBackground(ACCENT_GOLD_HOVER);
            }
            @Override
            public void mouseExited(MouseEvent e) {
                if (btn.isEnabled()) btn.setBackground(ACCENT_GOLD);
            }
        });
    }

    /**
     * Styles a secondary dark button.
     */
    public static void styleSecondaryButton(JButton btn) {
        btn.setFont(FONT_BOLD);
        btn.setBackground(PANEL_BG);
        btn.setForeground(TEXT_PRIMARY);
        btn.setFocusPainted(false);
        btn.setBorder(new CompoundBorder(
                new LineBorder(BORDER_COLOR, 1),
                new EmptyBorder(7, 14, 7, 14)
        ));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (btn.isEnabled()) btn.setBackground(CARD_BG);
            }
            @Override
            public void mouseExited(MouseEvent e) {
                if (btn.isEnabled()) btn.setBackground(PANEL_BG);
            }
        });
    }

    /**
     * Styles a danger action button (e.g., Stop Server, Disconnect).
     */
    public static void styleDangerButton(JButton btn) {
        btn.setFont(FONT_BOLD);
        btn.setBackground(new Color(153, 27, 27)); // Darker red base
        btn.setForeground(TEXT_PRIMARY);
        btn.setFocusPainted(false);
        btn.setBorder(new CompoundBorder(
                new LineBorder(ERROR_RED, 1),
                new EmptyBorder(7, 14, 7, 14)
        ));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (btn.isEnabled()) btn.setBackground(ERROR_RED);
            }
            @Override
            public void mouseExited(MouseEvent e) {
                if (btn.isEnabled()) btn.setBackground(new Color(153, 27, 27));
            }
        });
    }

    /**
     * Styles text input fields.
     */
    public static void styleTextField(JTextField tf) {
        tf.setFont(FONT_REGULAR);
        tf.setBackground(INPUT_BG);
        tf.setForeground(TEXT_PRIMARY);
        tf.setCaretColor(ACCENT_GOLD);
        tf.setBorder(new CompoundBorder(
                new LineBorder(BORDER_COLOR, 1),
                new EmptyBorder(6, 8, 6, 8)
        ));
    }

    /**
     * Applies styling to tables, including header, alternating row colors,
     * cell borders, and selection colors.
     */
    public static void styleTable(JTable table) {
        table.setFont(FONT_REGULAR);
        table.setBackground(PANEL_BG);
        table.setForeground(TEXT_PRIMARY);
        table.setGridColor(BORDER_COLOR);
        table.setRowHeight(28);
        table.setShowGrid(true);
        table.setShowHorizontalLines(true);
        table.setShowVerticalLines(false);
        table.setSelectionBackground(new Color(45, 62, 88));
        table.setSelectionForeground(TEXT_PRIMARY);

        JTableHeader header = table.getTableHeader();
        header.setFont(FONT_BOLD);
        header.setBackground(CARD_BG);
        header.setForeground(ACCENT_GOLD);
        header.setPreferredSize(new Dimension(header.getWidth(), 32));
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COLOR));

        // Alternate row color renderer
        DefaultTableCellRenderer defaultRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object val, boolean isSelected,
                                                           boolean hasFocus, int row, int col) {
                Component c = super.getTableCellRendererComponent(t, val, isSelected, hasFocus, row, col);
                if (!isSelected) {
                    c.setBackground(row % 2 == 0 ? PANEL_BG : TABLE_ROW_ALT);
                    c.setForeground(TEXT_PRIMARY);
                }
                if (val != null) {
                    String str = val.toString();
                    if (str.startsWith("Passed")) {
                        c.setForeground(SUCCESS_GREEN);
                    } else if (str.startsWith("Failed")) {
                        c.setForeground(ERROR_RED);
                    } else if (str.equals("In Progress") || str.equals("Pending")) {
                        c.setForeground(WARNING_AMBER);
                    }
                }
                setBorder(new EmptyBorder(0, 8, 0, 8));
                return c;
            }
        };

        for (int i = 0; i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(defaultRenderer);
        }
    }

    /**
     * Styles scroll panes.
     */
    public static void styleScrollPane(JScrollPane sp) {
        sp.getViewport().setBackground(PANEL_BG);
        sp.setBorder(new LineBorder(BORDER_COLOR, 1));
        sp.setBackground(PANEL_BG);
    }

    /**
     * Custom progress bar UI with smooth champagne-gold fill and dark track.
     */
    public static void styleProgressBar(JProgressBar pb) {
        pb.setBackground(INPUT_BG);
        pb.setForeground(ACCENT_GOLD);
        pb.setBorder(new LineBorder(BORDER_COLOR, 1));
        pb.setFont(FONT_BOLD);
        pb.setStringPainted(true);
        pb.setUI(new BasicProgressBarUI() {
            @Override
            protected Color getSelectionBackground() {
                return TEXT_PRIMARY;
            }

            @Override
            protected Color getSelectionForeground() {
                return BG_DARK;
            }

            @Override
            protected void paintDeterminate(Graphics g, javax.swing.JComponent c) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int width = pb.getWidth();
                int height = pb.getHeight();
                double percent = pb.getPercentComplete();
                int fillWidth = (int) (width * percent);

                // Background track
                g2.setColor(INPUT_BG);
                g2.fillRect(0, 0, width, height);

                // Progress fill (Champagne Gold gradient or solid)
                g2.setColor(ACCENT_GOLD);
                g2.fillRect(0, 0, fillWidth, height);

                // Draw percentage string
                if (pb.isStringPainted()) {
                    paintString(g2, 0, 0, width, height, fillWidth, pb.getInsets());
                }
                g2.dispose();
            }
        });
    }

    /**
     * Formats status label badge.
     */
    public static void updateStatusBadge(JLabel label, boolean running, String activeText, String inactiveText) {
        if (running) {
            label.setText("  ●  " + activeText + "  ");
            label.setForeground(SUCCESS_GREEN);
            label.setBackground(new Color(20, 83, 45, 120)); // Soft dark green tint
        } else {
            label.setText("  ●  " + inactiveText + "  ");
            label.setForeground(ERROR_RED);
            label.setBackground(new Color(127, 29, 29, 120)); // Soft dark red tint
        }
        label.setOpaque(true);
        label.setFont(FONT_BOLD);
        label.setBorder(new LineBorder(running ? SUCCESS_GREEN : ERROR_RED, 1, true));
    }
}
