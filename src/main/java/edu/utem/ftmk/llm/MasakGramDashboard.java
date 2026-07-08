package edu.utem.ftmk.llm;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class MasakGramDashboard extends JFrame {

    private static final Color BG_MAIN      = new Color(245, 246, 250);
    private static final Color BG_PANEL     = new Color(255, 255, 255);
    private static final Color BG_CARD      = new Color(235, 237, 243);
    private static final Color ACCENT_GREEN = new Color(39, 174, 96);
    private static final Color ACCENT_BLUE  = new Color(52, 152, 219);
    private static final Color ACCENT_RED   = new Color(231, 76, 60);
    private static final Color ACCENT_AMBER = new Color(243, 156, 18);
    private static final Color TEXT_PRIMARY = new Color(30, 30, 40);
    private static final Color TEXT_MUTED   = new Color(110, 110, 125);
    private static final Color SIDEBAR_BG   = new Color(42, 52, 70);
    private static final Color BORDER_COLOR = new Color(210, 213, 220);

    private static final String[] MODEL_TAGS  = {
        "llama3.2:3b", "phi4-mini", "qwen2.5:3b",
        "aisingapore/Gemma-SEA-LION-v4-4B-VL", "medgemma:4b"
    };
    private static final String[] MODEL_SHORT = {
        "Llama 3.2", "Phi-4-mini", "Qwen 2.5", "Gemma-SEA", "MedGemma"
    };
    private static final String[] TECHNIQUES = {
        "zero_shot", "few_shot", "chain_of_thought", "structured_output"
    };
    private static final int TOTAL_TRANSCRIPTS = 50;

    private JLabel lblTotalTranscripts, lblExperimentsRun, lblSuccessRate, lblFailureRate;
    private JComboBox<String> techniqueDropdown;
    private JTable statusMatrix;
    private DefaultTableModel tableModel;
    private JButton btnRunExperiment;
    private JComboBox<String> modelSelector;
    private JComboBox<String> runTechSelector;
    private JLabel statusBarLabel;
    private JProgressBar progressBar;
    private JPanel contentPanel;
    private CardLayout cardLayout;

    private JLabel lblReelId, lblInfluencer, lblDuration, lblLangTag, lblTransStatus, lblGroundTruth;
    private JTextPane transcriptPane;
    private DefaultTableModel reelStatusModel;
    private int selectedTranscriptId = -1;

    private JTable gtTable, llmTable;
    private DefaultTableModel gtModel, llmModel;
    private JLabel lblGtTotalCal, lblLlmTotalCal;
    private JComboBox<String> fsReelIdSelector;
    private JComboBox<String> fsModelSelector, fsTechSelector;

    // Auto-refresh timer for live matrix updates during batch run
    private javax.swing.Timer autoRefreshTimer;

    public MasakGramDashboard() {
        setTitle("MasakGramPrompt — Experiment Execution Engine");
        setSize(1350, 820);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG_MAIN);
        setLayout(new BorderLayout());
        add(buildSidebar(),   BorderLayout.WEST);
        add(buildMainArea(),  BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);
        refreshMatrix();
    }

    private JPanel buildSidebar() {
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBackground(SIDEBAR_BG);
        sidebar.setPreferredSize(new Dimension(210, 0));
        sidebar.setBorder(BorderFactory.createEmptyBorder(24, 0, 24, 0));
        JLabel logo = new JLabel("MasakGram", SwingConstants.CENTER);
        logo.setFont(new Font("SansSerif", Font.BOLD, 20));
        logo.setForeground(new Color(100, 220, 140));
        logo.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel sub = new JLabel("Prompt Engine", SwingConstants.CENTER);
        sub.setFont(new Font("SansSerif", Font.PLAIN, 11));
        sub.setForeground(new Color(150, 165, 185));
        sub.setAlignmentX(Component.CENTER_ALIGNMENT);
        sidebar.add(logo);
        sidebar.add(Box.createVerticalStrut(4));
        sidebar.add(sub);
        sidebar.add(Box.createVerticalStrut(24));
        JSeparator sep = new JSeparator();
        sep.setForeground(new Color(70, 85, 108));
        sep.setMaximumSize(new Dimension(170, 1));
        sep.setAlignmentX(Component.CENTER_ALIGNMENT);
        sidebar.add(sep);
        sidebar.add(Box.createVerticalStrut(16));
        sidebar.add(sidebarBtn("[>] Run Experiment", "RUN"));
        sidebar.add(Box.createVerticalStrut(8));
        sidebar.add(sidebarBtn("[=] Status Matrix",  "MATRIX"));
        sidebar.add(Box.createVerticalStrut(8));
        sidebar.add(sidebarBtn("[i] Reel Analysis",  "REEL"));
        sidebar.add(Box.createVerticalStrut(8));
        sidebar.add(sidebarBtn("[#] Fact Sheet",     "FACTSHEET"));
        sidebar.add(Box.createVerticalStrut(8));
        sidebar.add(sidebarBtn("[^] Data Export",    "EXPORT"));
        sidebar.add(Box.createVerticalGlue());
        return sidebar;
    }

    private JButton sidebarBtn(String label, String card) {
        JButton btn = new JButton(label);
        btn.setMaximumSize(new Dimension(185, 40));
        btn.setAlignmentX(Component.CENTER_ALIGNMENT);
        btn.setBackground(Color.WHITE);
        btn.setForeground(Color.BLACK);
        btn.setFont(new Font("Monospaced", Font.PLAIN, 12));
        btn.setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 16));
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { btn.setBackground(new Color(220, 225, 235)); }
            public void mouseExited(MouseEvent e)  { btn.setBackground(Color.WHITE); }
        });
        btn.addActionListener(e -> {
            cardLayout.show(contentPanel, card);
            if ("MATRIX".equals(card)) refreshMatrix();
        });
        return btn;
    }

    private JPanel buildMainArea() {
        cardLayout   = new CardLayout();
        contentPanel = new JPanel(cardLayout);
        contentPanel.setBackground(BG_MAIN);
        contentPanel.add(buildRunPanel(),        "RUN");
        contentPanel.add(buildMatrixPanel(),     "MATRIX");
        contentPanel.add(buildReelPanel(),       "REEL");
        contentPanel.add(buildFactSheetPanel(),  "FACTSHEET");
        contentPanel.add(buildExportPanel(),     "EXPORT");
        cardLayout.show(contentPanel, "MATRIX");
        return contentPanel;
    }

    private JPanel buildRunPanel() {
        JPanel panel = new JPanel(new BorderLayout(16, 16));
        panel.setBackground(BG_MAIN);
        panel.setBorder(BorderFactory.createEmptyBorder(28, 28, 28, 28));
        JLabel title = new JLabel("Run Experiment");
        title.setFont(new Font("SansSerif", Font.BOLD, 22));
        title.setForeground(TEXT_PRIMARY);
        title.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        panel.add(title, BorderLayout.NORTH);
        JPanel config = new JPanel(new GridBagLayout());
        config.setBackground(BG_PANEL);
        config.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_COLOR, 1),
            BorderFactory.createEmptyBorder(20, 24, 20, 24)));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(8, 8, 8, 8);
        g.anchor = GridBagConstraints.WEST;
        g.gridx = 0; g.gridy = 0; g.gridwidth = 5;
        JLabel allInfo = new JLabel("All 50 transcripts will be processed in sequence");
        allInfo.setForeground(ACCENT_BLUE);
        allInfo.setFont(new Font("SansSerif", Font.BOLD, 12));
        config.add(allInfo, g);
        g.gridwidth = 1;
        g.gridx = 0; g.gridy = 1;
        config.add(styledLabel("LLM Model:"), g);
        g.gridx = 1;
        modelSelector = new JComboBox<>(MODEL_SHORT);
        styleCombo(modelSelector);
        config.add(modelSelector, g);
        g.gridx = 0; g.gridy = 2;
        config.add(styledLabel("Technique:"), g);
        g.gridx = 1;
        runTechSelector = new JComboBox<>(TECHNIQUES);
        styleCombo(runTechSelector);
        config.add(runTechSelector, g);
        g.gridwidth = 1;
        g.gridx = 4; g.gridy = 1;
        btnRunExperiment = new JButton("  Run Batch Pipeline");
        btnRunExperiment.setBackground(ACCENT_GREEN);
        btnRunExperiment.setForeground(Color.WHITE);
        btnRunExperiment.setFont(new Font("SansSerif", Font.BOLD, 13));
        btnRunExperiment.setBorder(BorderFactory.createEmptyBorder(10, 18, 10, 18));
        btnRunExperiment.setFocusPainted(false);
        btnRunExperiment.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnRunExperiment.addActionListener(e -> executeBatch());
        config.add(btnRunExperiment, g);
        g.gridx = 0; g.gridy = 3; g.gridwidth = 4; g.fill = GridBagConstraints.HORIZONTAL;
        progressBar = new JProgressBar(0, 1);
        progressBar.setStringPainted(true);
        progressBar.setString("Idle");
        progressBar.setBackground(BG_CARD);
        progressBar.setForeground(ACCENT_GREEN);
        progressBar.setPreferredSize(new Dimension(500, 22));
        config.add(progressBar, g);
        g.fill = GridBagConstraints.NONE; g.gridwidth = 1;
        panel.add(config, BorderLayout.CENTER);
        JTextArea logArea = new JTextArea();
        logArea.setBackground(new Color(248, 249, 252));
        logArea.setForeground(new Color(30, 100, 60));
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        logArea.setEditable(false);
        logArea.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(BORDER_COLOR), "Console Output",
            0, 0, new Font("SansSerif", Font.BOLD, 11), TEXT_MUTED));
        logScroll.setPreferredSize(new Dimension(0, 240));
        panel.add(logScroll, BorderLayout.SOUTH);
        PrintStream ps = new PrintStream(new OutputStream() {
            @Override public void write(int b) {
                SwingUtilities.invokeLater(() -> {
                    logArea.append(String.valueOf((char) b));
                    logArea.setCaretPosition(logArea.getDocument().getLength());
                });
            }
        });
        System.setOut(ps);
        return panel;
    }

    private JPanel buildMatrixPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 16));
        panel.setBackground(BG_MAIN);
        panel.setBorder(BorderFactory.createEmptyBorder(28, 28, 28, 28));
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(BG_MAIN);
        JPanel titleBlock = new JPanel();
        titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));
        titleBlock.setBackground(BG_MAIN);
        JLabel title = new JLabel("Transcript Execution Status Matrix");
        title.setFont(new Font("SansSerif", Font.BOLD, 22));
        title.setForeground(TEXT_PRIMARY);
        JLabel sub = new JLabel("Cross-tabulated view mapping transcripts against models. Click a row to view Reel Analysis.");
        sub.setFont(new Font("SansSerif", Font.PLAIN, 12));
        sub.setForeground(TEXT_MUTED);
        titleBlock.add(title);
        titleBlock.add(Box.createVerticalStrut(4));
        titleBlock.add(sub);
        header.add(titleBlock, BorderLayout.WEST);
        JPanel dropPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        dropPanel.setBackground(BG_MAIN);
        JLabel dropLabel = new JLabel("Active Prompt Matrix:");
        dropLabel.setForeground(TEXT_MUTED);
        dropLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        techniqueDropdown = new JComboBox<>(TECHNIQUES);
        styleCombo(techniqueDropdown);
        techniqueDropdown.addActionListener(e -> refreshMatrix());
        dropPanel.add(dropLabel);
        dropPanel.add(techniqueDropdown);
        header.add(dropPanel, BorderLayout.EAST);
        panel.add(header, BorderLayout.NORTH);
        JPanel cards = new JPanel(new GridLayout(1, 4, 12, 0));
        cards.setBackground(BG_MAIN);
        cards.setPreferredSize(new Dimension(0, 95));
        lblTotalTranscripts = new JLabel("-");
        lblExperimentsRun   = new JLabel("-");
        lblSuccessRate      = new JLabel("-");
        lblFailureRate      = new JLabel("-");
        cards.add(statCard("TOTAL TRANSCRIPTS", lblTotalTranscripts, ACCENT_BLUE));
        cards.add(statCard("EXPERIMENTS RUN",   lblExperimentsRun,   ACCENT_BLUE));
        cards.add(statCard("SUCCESS RATE (%)",  lblSuccessRate,      ACCENT_GREEN));
        cards.add(statCard("FAILURE RATE (%)",  lblFailureRate,      ACCENT_RED));
        String[] cols = new String[MODEL_SHORT.length + 2];
        cols[0] = "ID"; cols[1] = "Verified By";
        for (int i = 0; i < MODEL_SHORT.length; i++) cols[i + 2] = MODEL_SHORT[i];
        tableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        statusMatrix = new JTable(tableModel);
        statusMatrix.setBackground(BG_PANEL);
        statusMatrix.setForeground(TEXT_PRIMARY);
        statusMatrix.setFont(new Font("SansSerif", Font.PLAIN, 12));
        statusMatrix.setRowHeight(32);
        statusMatrix.setGridColor(BORDER_COLOR);
        statusMatrix.setSelectionBackground(new Color(220, 235, 255));
        statusMatrix.setSelectionForeground(TEXT_PRIMARY);
        statusMatrix.setShowGrid(true);
        statusMatrix.setIntercellSpacing(new Dimension(1, 1));
        JTableHeader th = statusMatrix.getTableHeader();
        th.setBackground(BG_CARD);
        th.setForeground(TEXT_MUTED);
        th.setFont(new Font("SansSerif", Font.BOLD, 12));
        th.setPreferredSize(new Dimension(0, 36));
        statusMatrix.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int col) {
                if (col >= 2 && value != null && !value.toString().equals("-")) {
                    JPanel bp = new JPanel(new GridBagLayout());
                    bp.setBackground(isSelected ? new Color(220, 235, 255)
                        : (row % 2 == 0 ? BG_PANEL : new Color(250, 251, 253)));
                    String val = value.toString();
                    Color bc; String bt;
                    if (val.startsWith("COMPLETED")) {
                        bc = ACCENT_GREEN;
                        bt = "Completed #" + val.replaceAll(".*#(\\d+).*", "$1");
                    } else if (val.startsWith("RUNNING")) {
                        bc = ACCENT_AMBER;
                        bt = "~ Running #" + val.replaceAll(".*#(\\d+).*", "$1");
                    } else if (val.startsWith("PENDING")) {
                        bc = new Color(100, 120, 150);
                        bt = "~ Pending";
                    } else if (val.startsWith("FAILED")) {
                        bc = ACCENT_RED;
                        bt = "X Failed #" + val.replaceAll(".*#(\\d+).*", "$1");
                    } else {
                        bc = ACCENT_RED;
                        bt = "X Failed";
                    }
                    JLabel badge = new JLabel(bt);
                    badge.setFont(new Font("SansSerif", Font.BOLD, 11));
                    badge.setForeground(Color.WHITE);
                    badge.setBackground(bc);
                    badge.setOpaque(true);
                    badge.setBorder(BorderFactory.createEmptyBorder(3, 10, 3, 10));
                    bp.add(badge);
                    bp.setToolTipText(val);
                    return bp;
                }
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
                setBackground(isSelected ? new Color(220, 235, 255)
                    : (row % 2 == 0 ? BG_PANEL : new Color(250, 251, 253)));
                if (value != null && value.toString().equals("-")) {
                    setText("- Unexecuted");
                    setForeground(TEXT_MUTED);
                } else {
                    setForeground(TEXT_PRIMARY);
                }
                setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
                setFont(new Font("SansSerif", Font.PLAIN, 12));
                setHorizontalAlignment(SwingConstants.CENTER);
                setToolTipText(value != null ? value.toString() : "");
                return this;
            }
        });
        statusMatrix.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                int row = statusMatrix.rowAtPoint(e.getPoint());
                if (row >= 0) {
                    Object idVal = tableModel.getValueAt(row, 0);
                    if (idVal != null) {
                        selectedTranscriptId = Integer.parseInt(idVal.toString());
                        loadReelAnalysis(selectedTranscriptId);
                        cardLayout.show(contentPanel, "REEL");
                    }
                }
            }
        });
        ToolTipManager.sharedInstance().registerComponent(statusMatrix);
        statusMatrix.getColumnModel().getColumn(0).setPreferredWidth(45);
        statusMatrix.getColumnModel().getColumn(1).setPreferredWidth(200);
        for (int i = 2; i < cols.length; i++)
            statusMatrix.getColumnModel().getColumn(i).setPreferredWidth(140);
        JScrollPane scroll = new JScrollPane(statusMatrix);
        scroll.setBackground(BG_PANEL);
        scroll.getViewport().setBackground(BG_PANEL);
        scroll.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));
        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.setBackground(BG_CARD);
        refreshBtn.setForeground(TEXT_PRIMARY);
        refreshBtn.setFont(new Font("SansSerif", Font.PLAIN, 12));
        refreshBtn.setFocusPainted(false);
        refreshBtn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_COLOR),
            BorderFactory.createEmptyBorder(5, 14, 5, 14)));
        refreshBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        refreshBtn.addActionListener(e -> refreshMatrix());
        JPanel bottomRow = new JPanel(new BorderLayout());
        bottomRow.setBackground(BG_MAIN);
        JLabel clickHint = new JLabel("  Click any row to view Reel Analysis");
        clickHint.setForeground(TEXT_MUTED);
        clickHint.setFont(new Font("SansSerif", Font.ITALIC, 11));
        bottomRow.add(clickHint,  BorderLayout.WEST);
        bottomRow.add(refreshBtn, BorderLayout.EAST);
        JPanel center = new JPanel(new BorderLayout(0, 12));
        center.setBackground(BG_MAIN);
        center.add(cards,     BorderLayout.NORTH);
        center.add(scroll,    BorderLayout.CENTER);
        center.add(bottomRow, BorderLayout.SOUTH);
        panel.add(center, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildReelPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        panel.setBackground(BG_MAIN);
        panel.setBorder(BorderFactory.createEmptyBorder(28, 28, 28, 28));
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(BG_MAIN);
        JLabel title = new JLabel("Reel Analysis Dashboard");
        title.setFont(new Font("SansSerif", Font.BOLD, 22));
        title.setForeground(TEXT_PRIMARY);
        JButton backBtn = new JButton("Back to Matrix");
        backBtn.setBackground(BG_CARD);
        backBtn.setForeground(TEXT_PRIMARY);
        backBtn.setFocusPainted(false);
        backBtn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_COLOR),
            BorderFactory.createEmptyBorder(6, 14, 6, 14)));
        backBtn.addActionListener(e -> cardLayout.show(contentPanel, "MATRIX"));
        header.add(title,   BorderLayout.WEST);
        header.add(backBtn, BorderLayout.EAST);
        panel.add(header, BorderLayout.NORTH);
        JPanel detailCard = new JPanel(new GridLayout(2, 3, 12, 8));
        detailCard.setBackground(BG_PANEL);
        detailCard.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder(BorderFactory.createLineBorder(BORDER_COLOR),
                "Reel Details", 0, 0, new Font("SansSerif", Font.BOLD, 11), TEXT_MUTED),
            BorderFactory.createEmptyBorder(8, 12, 8, 12)));
        lblReelId      = new JLabel("-"); lblInfluencer  = new JLabel("-");
        lblDuration    = new JLabel("-"); lblLangTag     = new JLabel("-");
        lblTransStatus = new JLabel("-"); lblGroundTruth = new JLabel("-");
        detailCard.add(detailItem("Reel ID",            lblReelId));
        detailCard.add(detailItem("Influencer Handle",  lblInfluencer));
        detailCard.add(detailItem("Video Duration",     lblDuration));
        detailCard.add(detailItem("Language Tag",       lblLangTag));
        detailCard.add(detailItem("Transcript Status",  lblTransStatus));
        detailCard.add(detailItem("Ground Truth",       lblGroundTruth));
        transcriptPane = new JTextPane();
        transcriptPane.setContentType("text/html");
        transcriptPane.setEditable(false);
        transcriptPane.setBackground(BG_PANEL);
        JScrollPane transcriptScroll = new JScrollPane(transcriptPane);
        transcriptScroll.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(BORDER_COLOR),
            "Transcript Preview (Malay terms highlighted)",
            0, 0, new Font("SansSerif", Font.BOLD, 11), TEXT_MUTED));
        transcriptScroll.setPreferredSize(new Dimension(0, 180));
        String[] reelCols = new String[MODEL_SHORT.length + 1];
        reelCols[0] = "Technique";
        for (int i = 0; i < MODEL_SHORT.length; i++) reelCols[i + 1] = MODEL_SHORT[i];
        reelStatusModel = new DefaultTableModel(reelCols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable reelStatusTable = new JTable(reelStatusModel);
        reelStatusTable.setBackground(BG_PANEL);
        reelStatusTable.setForeground(TEXT_PRIMARY);
        reelStatusTable.setFont(new Font("SansSerif", Font.PLAIN, 12));
        reelStatusTable.setRowHeight(28);
        reelStatusTable.setGridColor(BORDER_COLOR);
        reelStatusTable.getTableHeader().setBackground(BG_CARD);
        reelStatusTable.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 12));
        reelStatusTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v,
                    boolean sel, boolean foc, int row, int col) {
                super.getTableCellRendererComponent(t, v, sel, foc, row, col);
                setBackground(sel ? new Color(220,235,255) : (row%2==0 ? BG_PANEL : new Color(250,251,253)));
                setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
                setHorizontalAlignment(col == 0 ? SwingConstants.LEFT : SwingConstants.CENTER);
                if (v != null) {
                    String s = v.toString();
                    if (s.startsWith("Completed")) { setForeground(ACCENT_GREEN); setFont(getFont().deriveFont(Font.BOLD)); }
                    else if (s.startsWith("Running")) { setForeground(ACCENT_AMBER); setFont(getFont().deriveFont(Font.BOLD)); }
                    else if (s.startsWith("Failed"))  { setForeground(ACCENT_RED);   setFont(getFont().deriveFont(Font.BOLD)); }
                    else { setForeground(TEXT_MUTED); setFont(new Font("SansSerif", Font.PLAIN, 12)); }
                }
                return this;
            }
        });
        JScrollPane reelStatusScroll = new JScrollPane(reelStatusTable);
        reelStatusScroll.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(BORDER_COLOR),
            "Analysis Status per Model x Technique (5.3c)",
            0, 0, new Font("SansSerif", Font.BOLD, 11), TEXT_MUTED));
        reelStatusScroll.setPreferredSize(new Dimension(0, 160));
        JButton viewFactSheet = new JButton("View Nutritional Fact Sheet for this Reel ->");
        viewFactSheet.setBackground(ACCENT_BLUE);
        viewFactSheet.setForeground(Color.WHITE);
        viewFactSheet.setFont(new Font("SansSerif", Font.BOLD, 12));
        viewFactSheet.setFocusPainted(false);
        viewFactSheet.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        viewFactSheet.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        viewFactSheet.addActionListener(e -> {
            if (selectedTranscriptId > 0) {
                fsReelIdSelector.setSelectedItem(String.valueOf(selectedTranscriptId));
                cardLayout.show(contentPanel, "FACTSHEET");
            }
        });
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnRow.setBackground(BG_MAIN);
        btnRow.add(viewFactSheet);
        JPanel center = new JPanel(new BorderLayout(0, 10));
        center.setBackground(BG_MAIN);
        center.add(detailCard,       BorderLayout.NORTH);
        center.add(transcriptScroll, BorderLayout.CENTER);
        JPanel south = new JPanel(new BorderLayout(0, 8));
        south.setBackground(BG_MAIN);
        south.add(reelStatusScroll, BorderLayout.CENTER);
        south.add(btnRow,           BorderLayout.SOUTH);
        panel.add(center, BorderLayout.CENTER);
        panel.add(south,  BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildFactSheetPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBackground(BG_MAIN);
        panel.setBorder(BorderFactory.createEmptyBorder(28, 28, 28, 28));
        JLabel title = new JLabel("Nutritional Fact Sheet");
        title.setFont(new Font("SansSerif", Font.BOLD, 22));
        title.setForeground(TEXT_PRIMARY);
        title.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        JPanel selBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        selBar.setBackground(BG_PANEL);
        selBar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_COLOR),
            BorderFactory.createEmptyBorder(10, 14, 10, 14)));

        // Reel ID dropdown: 1 to TOTAL_TRANSCRIPTS
        String[] reelIds = new String[TOTAL_TRANSCRIPTS];
        for (int i = 0; i < TOTAL_TRANSCRIPTS; i++) reelIds[i] = String.valueOf(i + 1);
        fsReelIdSelector = new JComboBox<>(reelIds);
        styleCombo(fsReelIdSelector);

        fsModelSelector = new JComboBox<>(MODEL_SHORT);
        styleCombo(fsModelSelector);
        fsTechSelector = new JComboBox<>(TECHNIQUES);
        styleCombo(fsTechSelector);
        JButton loadBtn = new JButton("Load Fact Sheet");
        loadBtn.setBackground(ACCENT_BLUE); loadBtn.setForeground(Color.WHITE);
        loadBtn.setFont(new Font("SansSerif", Font.BOLD, 12));
        loadBtn.setFocusPainted(false);
        loadBtn.setBorder(BorderFactory.createEmptyBorder(7, 14, 7, 14));
        loadBtn.addActionListener(e -> loadFactSheet());
        JButton exportBtn = new JButton("Download as CSV");
        exportBtn.setBackground(ACCENT_GREEN); exportBtn.setForeground(Color.WHITE);
        exportBtn.setFont(new Font("SansSerif", Font.BOLD, 12));
        exportBtn.setFocusPainted(false);
        exportBtn.setBorder(BorderFactory.createEmptyBorder(7, 14, 7, 14));
        exportBtn.addActionListener(e -> exportFactSheetCsv());
        selBar.add(new JLabel("Reel ID:") {{ setForeground(TEXT_PRIMARY); }});
        selBar.add(fsReelIdSelector);
        selBar.add(new JLabel("Model:") {{ setForeground(TEXT_PRIMARY); }});
        selBar.add(fsModelSelector);
        selBar.add(new JLabel("Technique:") {{ setForeground(TEXT_PRIMARY); }});
        selBar.add(fsTechSelector);
        selBar.add(loadBtn);
        selBar.add(exportBtn);
        lblGtTotalCal  = new JLabel("-");
        lblLlmTotalCal = new JLabel("-");
        lblGtTotalCal.setFont(new Font("SansSerif", Font.BOLD, 16));
        lblGtTotalCal.setForeground(ACCENT_GREEN);
        lblLlmTotalCal.setFont(new Font("SansSerif", Font.BOLD, 16));
        lblLlmTotalCal.setForeground(ACCENT_BLUE);
        JPanel gtCard = new JPanel(new BorderLayout(0, 4));
        gtCard.setBackground(BG_PANEL);
        gtCard.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_COLOR),
            BorderFactory.createEmptyBorder(10, 16, 10, 16)));
        JLabel gtCardTitle = new JLabel("HUMAN GROUND TRUTH — Total Calories");
        gtCardTitle.setFont(new Font("SansSerif", Font.BOLD, 10));
        gtCardTitle.setForeground(TEXT_MUTED);
        gtCard.add(gtCardTitle,   BorderLayout.NORTH);
        gtCard.add(lblGtTotalCal, BorderLayout.CENTER);
        JPanel llmCard = new JPanel(new BorderLayout(0, 4));
        llmCard.setBackground(BG_PANEL);
        llmCard.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_COLOR),
            BorderFactory.createEmptyBorder(10, 16, 10, 16)));
        JLabel llmCardTitle = new JLabel("LLM PREDICTION — Total Calories");
        llmCardTitle.setFont(new Font("SansSerif", Font.BOLD, 10));
        llmCardTitle.setForeground(TEXT_MUTED);
        llmCard.add(llmCardTitle,   BorderLayout.NORTH);
        llmCard.add(lblLlmTotalCal, BorderLayout.CENTER);
        JPanel summaryBar = new JPanel(new GridLayout(1, 2, 12, 0));
        summaryBar.setBackground(BG_MAIN);
        summaryBar.setPreferredSize(new Dimension(0, 65));
        summaryBar.add(gtCard);
        summaryBar.add(llmCard);
        String[] gtCols = {"Raw Name", "English Name", "Qty Expression",
            "Unit", "Weight(g)", "Calories", "Protein(g)", "Fat(g)", "Carbs(g)", "Sodium(mg)", "Lang"};
        gtModel = new DefaultTableModel(gtCols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        gtTable = new JTable(gtModel);
        styleFactTable(gtTable);
        String[] llmCols = {"Raw Name", "English Name", "Weight(g)",
            "Calories", "Protein(g)", "Fat(g)", "Carbs(g)", "Sodium(mg)", "Hallucination"};
        llmModel = new DefaultTableModel(llmCols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        llmTable = new JTable(llmModel);
        styleFactTable(llmTable);
        JScrollPane gtScroll = new JScrollPane(gtTable);
        gtScroll.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(BORDER_COLOR), "Ground Truth Ingredients (5.4a)",
            0, 0, new Font("SansSerif", Font.BOLD, 11), TEXT_MUTED));
        JScrollPane llmScroll = new JScrollPane(llmTable);
        llmScroll.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(BORDER_COLOR), "LLM Predicted Ingredients (5.4b, 5.4c)",
            0, 0, new Font("SansSerif", Font.BOLD, 11), TEXT_MUTED));
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, gtScroll, llmScroll);
        split.setResizeWeight(0.5);
        split.setBorder(BorderFactory.createEmptyBorder());
        JPanel north = new JPanel(new BorderLayout(0, 8));
        north.setBackground(BG_MAIN);
        north.add(title,      BorderLayout.NORTH);
        north.add(selBar,     BorderLayout.CENTER);
        north.add(summaryBar, BorderLayout.SOUTH);
        panel.add(north, BorderLayout.NORTH);
        panel.add(split, BorderLayout.CENTER);
        return panel;
    }

    private void styleFactTable(JTable table) {
        table.setBackground(BG_PANEL);
        table.setForeground(TEXT_PRIMARY);
        table.setFont(new Font("SansSerif", Font.PLAIN, 11));
        table.setRowHeight(24);
        table.setGridColor(BORDER_COLOR);
        table.setSelectionBackground(new Color(220, 235, 255));
        table.setShowGrid(true);
        table.getTableHeader().setBackground(BG_CARD);
        table.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 11));
        table.getTableHeader().setForeground(TEXT_MUTED);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
    }

    private JPanel buildExportPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 16));
        panel.setBackground(BG_MAIN);
        panel.setBorder(BorderFactory.createEmptyBorder(28, 28, 28, 28));
        JLabel title = new JLabel("Data Export");
        title.setFont(new Font("SansSerif", Font.BOLD, 22));
        title.setForeground(TEXT_PRIMARY);
        JLabel sub = new JLabel("Select an evaluation layer to export as CSV");
        sub.setForeground(TEXT_MUTED);
        sub.setFont(new Font("SansSerif", Font.PLAIN, 12));
        JPanel titleBlock = new JPanel();
        titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));
        titleBlock.setBackground(BG_MAIN);
        titleBlock.add(title);
        titleBlock.add(Box.createVerticalStrut(4));
        titleBlock.add(sub);
        panel.add(titleBlock, BorderLayout.NORTH);
        String[][] layers = {
            {"LAYER_1A", "Exact Match",          "layer1a_exact_match.csv"},
            {"LAYER_1B", "Text Similarity",      "layer1b_text_similarity.csv"},
            {"LAYER_2A", "Numeric Quantity",     "layer2a_numeric_quantity.csv"},
            {"LAYER_2B", "Numeric Nutrition",    "layer2b_numeric_nutrition.csv"},
            {"LAYER_2C", "Nutrition Totals",     "layer2c_nutrition_totals.csv"},
            {"LAYER_3A", "JSON Validity",        "layer3a_json_validity.csv"},
            {"LAYER_3B", "Hallucination",        "layer3b_hallucination.csv"},
            {"LAYER_3C", "Ingredient Detection", "layer3c_ingredient_detection.csv"},
            {"LAYER_4",  "Human Evaluation",     "layer4_human_evaluation.csv"},
            {"LAYER_5",  "Condition Scores",     "layer5_condition_scores.csv"}
        };
        JPanel grid = new JPanel(new GridLayout(5, 2, 16, 16));
        grid.setBackground(BG_MAIN);
        for (String[] layer : layers) {
            JPanel card = new JPanel(new BorderLayout(8, 4));
            card.setBackground(BG_PANEL);
            card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR),
                BorderFactory.createEmptyBorder(14, 16, 14, 16)));
            JLabel layerLabel = new JLabel(layer[0]);
            layerLabel.setFont(new Font("Monospaced", Font.BOLD, 11));
            layerLabel.setForeground(ACCENT_BLUE);
            JLabel nameLabel = new JLabel(layer[1]);
            nameLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
            nameLabel.setForeground(TEXT_PRIMARY);
            JLabel fileLabel = new JLabel(layer[2]);
            fileLabel.setFont(new Font("SansSerif", Font.PLAIN, 10));
            fileLabel.setForeground(TEXT_MUTED);
            JButton exportBtn = new JButton("Export CSV");
            exportBtn.setBackground(ACCENT_BLUE);
            exportBtn.setForeground(Color.WHITE);
            exportBtn.setFont(new Font("SansSerif", Font.BOLD, 11));
            exportBtn.setFocusPainted(false);
            exportBtn.setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
            exportBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            final String layerCode = layer[0];
            final String fileName  = layer[2];
            exportBtn.addActionListener(e -> triggerExport(layerCode, fileName));
            JPanel textBlock = new JPanel();
            textBlock.setLayout(new BoxLayout(textBlock, BoxLayout.Y_AXIS));
            textBlock.setBackground(BG_PANEL);
            textBlock.add(layerLabel);
            textBlock.add(Box.createVerticalStrut(2));
            textBlock.add(nameLabel);
            textBlock.add(Box.createVerticalStrut(2));
            textBlock.add(fileLabel);
            card.add(textBlock, BorderLayout.CENTER);
            card.add(exportBtn, BorderLayout.EAST);
            grid.add(card);
        }
        JScrollPane scroll = new JScrollPane(grid);
        scroll.setBackground(BG_MAIN);
        scroll.getViewport().setBackground(BG_MAIN);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 4));
        bar.setBackground(BG_CARD);
        bar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_COLOR));
        statusBarLabel = new JLabel("Ready — MasakGramPrompt v1.0");
        statusBarLabel.setForeground(TEXT_MUTED);
        statusBarLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        bar.add(statusBarLabel);
        return bar;
    }

    private void loadReelAnalysis(int transcriptId) {
        new Thread(() -> {
            try {
                ReelRequest req = new ReelRequest(transcriptId, "", "");
                req.action = "REEL_DETAIL";
                ReelResponse resp = sendPacket(req);
                SwingUtilities.invokeLater(() -> {
                    lblReelId.setText(String.valueOf(transcriptId));
                    lblInfluencer.setText(resp.influencerHandle != null ? "@" + resp.influencerHandle : "-");
                    lblDuration.setText(resp.videoDuration > 0 ? resp.videoDuration + " seconds" : "-");
                    lblLangTag.setText(resp.languageTag != null ? resp.languageTag : "-");
                    lblTransStatus.setText(resp.transcriptStatus != null ? resp.transcriptStatus : "-");
                    lblGroundTruth.setText(resp.groundTruthAvailable ? "Available" : "Not Available");
                    String[] malayWords = {"telur", "goreng", "minyak", "bawang", "cili", "gula",
                        "garam", "masak", "ayam", "santan", "serai", "kunyit", "ketumbar",
                        "tepung", "sudu", "cawan", "biji", "ulas", "kentang", "ikan"};
                    String html = resp.transcriptPreview != null ? resp.transcriptPreview : "";
                    html = html.replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>");
                    for (String word : malayWords) {
                        html = html.replaceAll("(?i)\\b" + word + "\\b",
                            "<b style='color:#c0392b; background:#fdedec; padding:1px 3px;'>" + word + "</b>");
                    }
                    transcriptPane.setText("<html><body style='font-family:Arial,sans-serif;font-size:12px;padding:8px;'>" + html + "</body></html>");
                    reelStatusModel.setRowCount(0);
                    if (resp.reelStatusRows != null) {
                        for (Object[] row : resp.reelStatusRows) reelStatusModel.addRow(row);
                    }
                    statusBarLabel.setText("Reel Analysis loaded for Transcript #" + transcriptId);
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() ->
                    statusBarLabel.setText("Error loading reel: " + ex.getMessage()));
            }
        }).start();
    }

    private void loadFactSheet() {
        try {
            int tid = Integer.parseInt((String) fsReelIdSelector.getSelectedItem());
            String model = MODEL_TAGS[fsModelSelector.getSelectedIndex()];
            String tech  = TECHNIQUES[fsTechSelector.getSelectedIndex()];
            ReelRequest req = new ReelRequest(tid, model, tech);
            req.action = "FACT_SHEET_DATA";
            ReelResponse resp = sendPacket(req);
            gtModel.setRowCount(0);
            llmModel.setRowCount(0);
            if (resp.gtRows != null)  for (Object[] row : resp.gtRows)  gtModel.addRow(row);
            if (resp.llmRows != null) for (Object[] row : resp.llmRows) llmModel.addRow(row);
            lblGtTotalCal.setText(resp.gtTotalCalories > 0
                ? String.format("%.1f kcal  (%d ingredients)", resp.gtTotalCalories,
                    resp.gtRows != null ? resp.gtRows.size() : 0)
                : "No ground truth data");
            lblLlmTotalCal.setText(resp.llmTotalCalories > 0
                ? String.format("%.1f kcal  (per serving: %.1f kcal x%d)",
                    resp.llmTotalCalories, resp.llmServingCalories, resp.llmServings)
                : "No LLM data");
            statusBarLabel.setText("Fact Sheet loaded for Reel #" + tid + " | " + model + " | " + tech);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage());
        }
    }

    private void exportFactSheetCsv() {
        try {
            int tid = Integer.parseInt((String) fsReelIdSelector.getSelectedItem());
            String model = MODEL_TAGS[fsModelSelector.getSelectedIndex()];
            String tech  = TECHNIQUES[fsTechSelector.getSelectedIndex()];
            ReelRequest req = new ReelRequest(tid, model, tech);
            req.action = "EXPORT_FACTSHEET";
            ReelResponse resp = sendPacket(req);
            if (resp.csvPayload != null && !resp.csvPayload.startsWith("Error")) {
                JFileChooser fc = new JFileChooser();
                fc.setSelectedFile(new java.io.File("factsheet_" + tid + ".csv"));
                if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                    try (FileWriter fw = new FileWriter(fc.getSelectedFile())) {
                        fw.write(resp.csvPayload);
                        JOptionPane.showMessageDialog(this, "Saved: " + fc.getSelectedFile().getAbsolutePath());
                    }
                }
            } else {
                JOptionPane.showMessageDialog(this, "No data to export.");
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Export error: " + ex.getMessage());
        }
    }

    private void triggerExport(String layerCode, String fileName) {
        new Thread(() -> {
            try {
                SwingUtilities.invokeLater(() -> statusBarLabel.setText("Exporting " + layerCode + "..."));
                ReelRequest req = new ReelRequest(0, "", "");
                req.action = "EXPORT";
                req.exportLayer = layerCode;
                ReelResponse resp = sendPacket(req);
                if (resp.csvPayload != null && !resp.csvPayload.startsWith("Error")) {
                    SwingUtilities.invokeLater(() -> {
                        JFileChooser fc = new JFileChooser();
                        fc.setSelectedFile(new java.io.File(fileName));
                        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                            try (FileWriter fw = new FileWriter(fc.getSelectedFile())) {
                                fw.write(resp.csvPayload);
                                statusBarLabel.setText("Exported: " + fc.getSelectedFile().getName());
                                JOptionPane.showMessageDialog(this,
                                    "Saved to: " + fc.getSelectedFile().getAbsolutePath(),
                                    "Export Complete", JOptionPane.INFORMATION_MESSAGE);
                            } catch (Exception ex) {
                                statusBarLabel.setText("Save error: " + ex.getMessage());
                            }
                        }
                    });
                } else {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                        resp.csvPayload, "Export Error", JOptionPane.ERROR_MESSAGE));
                }
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> statusBarLabel.setText("Export error: " + ex.getMessage()));
            }
        }).start();
    }

    private void refreshMatrix() {
        String technique = TECHNIQUES[techniqueDropdown.getSelectedIndex()];
        new Thread(() -> {
            try {
                ReelRequest req = new ReelRequest(0, "", technique);
                req.action = "STATUS_MATRIX";
                ReelResponse resp = sendPacket(req);
                SwingUtilities.invokeLater(() -> {
                    tableModel.setRowCount(0);
                    if (resp == null || resp.matrixRows == null) return;
                    int totalRun = 0, completed = 0, failed = 0;
                    for (Object[] row : resp.matrixRows) {
                        tableModel.addRow(row);
                        for (int c = 2; c < row.length; c++) {
                            String v = row[c] == null ? "-" : row[c].toString();
                            if (!v.equals("-")) totalRun++;
                            if (v.startsWith("COMPLETED")) completed++;
                            if (v.startsWith("FAILED"))    failed++;
                        }
                    }
                    lblTotalTranscripts.setText(String.valueOf(TOTAL_TRANSCRIPTS));
                    lblExperimentsRun.setText(String.valueOf(totalRun));
                    double sp = totalRun > 0 ? (completed * 100.0 / totalRun) : 0;
                    double fp = totalRun > 0 ? (failed    * 100.0 / totalRun) : 0;
                    lblSuccessRate.setText(String.format("%.1f%%", sp));
                    lblFailureRate.setText(String.format("%.1f%%", fp));
                    statusBarLabel.setText("Matrix refreshed — technique: " + technique);
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() ->
                    statusBarLabel.setText("Could not reach server: " + ex.getMessage()));
            }
        }).start();
    }

    // =========================================================
    // EXECUTE BATCH — all 50 transcripts, pending -> running -> completed
    // =========================================================
    private void executeBatch() {
        List<String> selectedTechs = new ArrayList<>();
        selectedTechs.add(TECHNIQUES[runTechSelector.getSelectedIndex()]);

        String modelTag = MODEL_TAGS[modelSelector.getSelectedIndex()];
        int total = TOTAL_TRANSCRIPTS * selectedTechs.size();

        btnRunExperiment.setEnabled(false);
        btnRunExperiment.setText("Running...");
        progressBar.setForeground(ACCENT_GREEN);
        progressBar.setMaximum(total);
        progressBar.setValue(0);
        progressBar.setString("0 / " + total);

        // Switch to matrix immediately
        cardLayout.show(contentPanel, "MATRIX");

        // Auto-refresh matrix every 3 seconds during batch
        autoRefreshTimer = new javax.swing.Timer(3000, e -> refreshMatrix());
        autoRefreshTimer.start();

        new Thread(() -> {
            int done = 0, fail = 0;
            for (String tech : selectedTechs) {
                final String currentTech = tech;

                // Step 1: Insert all 50 as PENDING in DB for this technique
                System.out.println("Inserting pending experiments for " + modelTag + " / " + currentTech);
                try {
                    ReelRequest pendingReq = new ReelRequest(0, modelTag, currentTech);
                    pendingReq.action = "PENDING_BATCH";
                    sendPacket(pendingReq);
                } catch (Exception ex) {
                    System.out.println("PENDING_BATCH error: " + ex.getMessage());
                }

                // Sync technique dropdown
                SwingUtilities.invokeLater(() -> {
                    for (int i = 0; i < TECHNIQUES.length; i++) {
                        if (TECHNIQUES[i].equals(currentTech)) {
                            techniqueDropdown.setSelectedIndex(i);
                            break;
                        }
                    }
                });

                // Step 2: Process each transcript 1-50
                for (int id = 1; id <= TOTAL_TRANSCRIPTS; id++) {
                    final int cur = id;
                    final int run = done + fail + 1;
                    SwingUtilities.invokeLater(() ->
                        statusBarLabel.setText("Running ID " + cur + " [" + run + "/" + total + "] | " + currentTech + " | " + modelTag));
                    try {
                        SwingUtilities.invokeLater(() -> refreshMatrix());
                        ReelRequest req = new ReelRequest(id, modelTag, currentTech);
                        req.action = "ANALYZE";
                        ReelResponse resp = sendPacket(req);
                        if ("Failed".equals(resp.status)) {
                            fail++;
                            final String log = "[ID " + id + "] FAILED: " + resp.jsonOutput;
                            SwingUtilities.invokeLater(() -> System.out.println(log));
                        } else {
                            done++;
                            final String log = resp.logMessage.isEmpty() ? "[ID " + id + "] OK" : resp.logMessage;
                            SwingUtilities.invokeLater(() -> System.out.println(log));
                        }
                    } catch (Exception ex) {
                        fail++;
                        System.out.println("[ID " + id + "] ERROR: " + ex.getMessage());
                    }
                    final int prog = done + fail, d = done, f = fail;
                    SwingUtilities.invokeLater(() -> {
                        progressBar.setValue(prog);
                        progressBar.setString(prog + "/" + total + "  OK:" + d + "  FAIL:" + f);
                    });
                }
            }
            final int fd = done, ff = fail;
            SwingUtilities.invokeLater(() -> {
                if (autoRefreshTimer != null) autoRefreshTimer.stop();
                btnRunExperiment.setEnabled(true);
                btnRunExperiment.setText("  Run Batch Pipeline");
                progressBar.setValue(total);
                progressBar.setString("Done: " + fd + "/" + total + (ff > 0 ? "  (" + ff + " failed)" : "  All complete!"));
                progressBar.setForeground(ff > 0 ? ACCENT_AMBER : ACCENT_GREEN);
                statusBarLabel.setText("Done — " + fd + " succeeded, " + ff + " failed");
                refreshMatrix();
                JOptionPane.showMessageDialog(MasakGramDashboard.this,
                    "Batch complete!\nSucceeded: " + fd + "\nFailed: " + ff,
                    "Result", JOptionPane.INFORMATION_MESSAGE);
            });
        }).start();
    }

    private ReelResponse sendPacket(ReelRequest req) throws Exception {
        try (Socket s = new Socket("localhost", 5000);
             ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream());
             ObjectInputStream  in  = new ObjectInputStream(s.getInputStream())) {
            out.writeObject(req); out.flush();
            return (ReelResponse) in.readObject();
        }
    }

    private JPanel statCard(String labelText, JLabel valueLabel, Color valueColor) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(BG_PANEL);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_COLOR, 1),
            BorderFactory.createEmptyBorder(16, 20, 16, 20)));
        JLabel lbl = new JLabel(labelText);
        lbl.setForeground(TEXT_MUTED);
        lbl.setFont(new Font("SansSerif", Font.BOLD, 10));
        valueLabel.setFont(new Font("SansSerif", Font.BOLD, 30));
        valueLabel.setForeground(valueColor);
        card.add(lbl, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        return card;
    }

    private JPanel detailItem(String label, JLabel valueLabel) {
        JPanel p = new JPanel(new BorderLayout(4, 0));
        p.setBackground(BG_PANEL);
        JLabel lbl = new JLabel(label + ":");
        lbl.setForeground(TEXT_MUTED);
        lbl.setFont(new Font("SansSerif", Font.PLAIN, 11));
        valueLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        valueLabel.setForeground(TEXT_PRIMARY);
        p.add(lbl,        BorderLayout.NORTH);
        p.add(valueLabel, BorderLayout.CENTER);
        return p;
    }

    private JLabel styledLabel(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(TEXT_PRIMARY);
        l.setFont(new Font("SansSerif", Font.PLAIN, 13));
        return l;
    }

    private void styleCombo(JComboBox<?> c) {
        c.setBackground(BG_PANEL);
        c.setForeground(TEXT_PRIMARY);
        c.setFont(new Font("SansSerif", Font.PLAIN, 12));
    }

    private void styleTextField(JTextField f) {
        f.setBackground(BG_CARD);
        f.setForeground(TEXT_PRIMARY);
        f.setCaretColor(TEXT_PRIMARY);
        f.setFont(new Font("Monospaced", Font.PLAIN, 13));
        f.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_COLOR),
            BorderFactory.createEmptyBorder(5, 8, 5, 8)));
    }

    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); }
        catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> new MasakGramDashboard().setVisible(true));
    }
}