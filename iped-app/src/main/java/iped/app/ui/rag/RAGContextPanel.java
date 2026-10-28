package iped.app.ui.rag;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.UIManager;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;

import iped.app.ui.Messages;
import iped.engine.rag.LLMProvider.PromptMessage;

/** Read-only inspection of the source bodies and the prompt sent in a single model request. */
class RAGContextPanel extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = LoggerFactory.getLogger(RAGContextPanel.class);
    private final RAGContextSnapshot snapshot;
    private final JTextArea content = new JTextArea();
    private final JTextField queryField = new JTextField(28);
    private final JTextArea searchStatus = new JTextArea(2, 0);
    private final JButton previousButton = new JButton(Messages.getString("RAGAssistant.PassagePrevious"));
    private final JButton nextButton = new JButton(Messages.getString("RAGAssistant.PassageNext"));
    private final JButton cancelSearchButton = new JButton(Messages.getString("Cancel"));
    private final JTable fragments;
    private final JTabbedPane tabs = new JTabbedPane();
    private SwingWorker<RAGPassageSearch.Result, Void> searchWorker;
    private RAGPassageSearch.Result searchResult;
    private int currentHit = -1;
    private boolean closed;

    RAGContextPanel(RAGContextSnapshot snapshot) {
        super(new BorderLayout(4, 4));
        this.snapshot = snapshot;
        setPreferredSize(new Dimension(820, 540));

        JTextArea notice = new JTextArea(Messages.getString("RAGAssistant.ContextNotice",
                snapshot.fragments.size(), snapshot.omissions.size()));
        notice.setEditable(false);
        notice.setLineWrap(true);
        notice.setWrapStyleWord(true);
        notice.setFont(UIManager.getFont("Label.font"));
        notice.setBackground(getBackground());
        add(notice, BorderLayout.NORTH);

        content.setEditable(false);
        content.setLineWrap(true);
        content.setWrapStyleWord(true);
        content.setFont(UIManager.getFont("TextArea.font"));

        String[] columns = { "#", Messages.getString("RAGAssistant.Header.Id"),
                Messages.getString("RAGAssistant.Header.Name"), "contenttrackID" };
        fragments = new JTable(new AbstractTableModel() {
            private static final long serialVersionUID = 1L;

            @Override
            public int getRowCount() {
                return snapshot.fragments.size();
            }

            @Override
            public int getColumnCount() {
                return columns.length;
            }

            @Override
            public String getColumnName(int column) {
                return columns[column];
            }

            @Override
            public Object getValueAt(int row, int column) {
                RAGContextSnapshot.Fragment fragment = snapshot.fragments.get(row);
                switch (column) {
                    case 0:
                        return fragment.includedOrdinal + 1;
                    case 1:
                        return fragment.itemId;
                    case 2:
                        return fragment.name;
                    default:
                        return fragment.contenttrackID;
                }
            }
        });
        // Evidence names and identifiers are plain text, including HTML-like names.
        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer();
        renderer.putClientProperty("html.disable", Boolean.TRUE);
        renderer.setHorizontalAlignment(JLabel.LEFT);
        fragments.setDefaultRenderer(Object.class, renderer);
        fragments.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        fragments.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        fragments.getColumnModel().getColumn(0).setPreferredWidth(35);
        fragments.getColumnModel().getColumn(2).setPreferredWidth(220);
        fragments.getColumnModel().getColumn(3).setPreferredWidth(260);
        fragments.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = fragments.getSelectedRow();
                content.getHighlighter().removeAllHighlights();
                content.setText(row < 0 ? "" : snapshot.fragments.get(row).content);
                content.setCaretPosition(0);
            }
        });

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(fragments), new JScrollPane(content));
        split.setResizeWeight(0.3);
        split.setDividerLocation(135);
        tabs.addTab(Messages.getString("RAGAssistant.ContextFragmentsTab"), split);
        tabs.addTab(Messages.getString("RAGAssistant.ContextPromptTab"), createPromptTab());
        add(tabs, BorderLayout.CENTER);
        if (!snapshot.fragments.isEmpty()) {
            fragments.setRowSelectionInterval(0, 0);
        }
        addSearchControls();
    }

    private JPanel createPromptTab() {
        JTextArea summary = new JTextArea(snapshot.prompt == null
                ? Messages.getString("RAGAssistant.ContextPromptUnavailable")
                : Messages.getString("RAGAssistant.ContextPromptSummary", snapshot.prompt.size(),
                        snapshot.promptChars));
        summary.setEditable(false);
        summary.setLineWrap(true);
        summary.setWrapStyleWord(true);
        summary.setFont(UIManager.getFont("Label.font"));
        summary.setBackground(getBackground());
        JTextArea promptText = new JTextArea(snapshot.prompt == null ? "" : renderPrompt(snapshot.prompt));
        promptText.setEditable(false);
        promptText.setLineWrap(true);
        promptText.setWrapStyleWord(true);
        promptText.setFont(UIManager.getFont("TextArea.font"));
        promptText.setCaretPosition(0);
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.add(summary, BorderLayout.NORTH);
        panel.add(new JScrollPane(promptText), BorderLayout.CENTER);
        return panel;
    }

    /** Plain-text view of the messages, in sending order, with their roles. */
    static String renderPrompt(List<PromptMessage> messages) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < messages.size(); i++) {
            PromptMessage message = messages.get(i);
            if (i > 0) {
                text.append("\n\n");
            }
            text.append("===== ").append(i + 1).append(". ").append(message.role).append(" =====\n")
                    .append(message.content);
        }
        return text.toString();
    }

    private void addSearchControls() {
        JButton findButton = new JButton(Messages.getString("RAGAssistant.PassageFind"));
        JLabel label = new JLabel(Messages.getString("RAGAssistant.PassageQuery"));
        label.setLabelFor(queryField);
        queryField.setToolTipText(Messages.getString("RAGAssistant.PassageQueryHint",
                RAGPassageSearch.MAX_QUERY_CHARS));
        searchStatus.setEditable(false);
        searchStatus.setLineWrap(true);
        searchStatus.setWrapStyleWord(true);
        searchStatus.setFont(UIManager.getFont("Label.font"));
        searchStatus.setBackground(getBackground());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        buttons.add(findButton);
        buttons.add(cancelSearchButton);
        buttons.add(previousButton);
        buttons.add(nextButton);
        JPanel controls = new JPanel(new BorderLayout(4, 0));
        controls.add(label, BorderLayout.WEST);
        controls.add(queryField, BorderLayout.CENTER);
        controls.add(buttons, BorderLayout.EAST);
        JPanel searchPanel = new JPanel(new BorderLayout(4, 4));
        searchPanel.add(controls, BorderLayout.NORTH);
        searchPanel.add(searchStatus, BorderLayout.SOUTH);
        add(searchPanel, BorderLayout.SOUTH);
        resetSearch();
        findButton.addActionListener(e -> startSearch());
        queryField.addActionListener(e -> startSearch());
        previousButton.addActionListener(e -> moveHit(-1));
        nextButton.addActionListener(e -> moveHit(1));
        cancelSearchButton.addActionListener(e -> {
            resetSearch();
            searchStatus.setText(Messages.getString("RAGAssistant.PassageCancelled"));
        });
        queryField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                resetSearch();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                resetSearch();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                resetSearch();
            }
        });
    }

    private void resetSearch() {
        SwingWorker<RAGPassageSearch.Result, Void> previous = searchWorker;
        searchWorker = null;
        if (previous != null) {
            previous.cancel(true);
        }
        searchResult = null;
        currentHit = -1;
        previousButton.setEnabled(false);
        nextButton.setEnabled(false);
        cancelSearchButton.setEnabled(false);
        content.getHighlighter().removeAllHighlights();
        searchStatus.setText(Messages.getString("RAGAssistant.PassageReady"));
    }

    void startSearch() {
        if (closed) {
            return;
        }
        resetSearch();
        final String query = queryField.getText();
        searchStatus.setText(Messages.getString("RAGAssistant.PassageSearching"));
        cancelSearchButton.setEnabled(true);
        searchWorker = new SwingWorker<RAGPassageSearch.Result, Void>() {
            @Override
            protected RAGPassageSearch.Result doInBackground() {
                return findPassages(query, this::isCancelled);
            }

            @Override
            protected void done() {
                if (closed || searchWorker != this || isCancelled()) {
                    return;
                }
                try {
                    searchResult = get();
                    currentHit = searchResult.hits.isEmpty() ? -1 : 0;
                    previousButton.setEnabled(searchResult.hits.size() > 1);
                    nextButton.setEnabled(searchResult.hits.size() > 1);
                    showHit();
                } catch (CancellationException e) {
                    // A cancelled lookup does not own the view anymore.
                } catch (Exception e) {
                    LOGGER.error("Error locating passages in RAG context", e);
                    searchStatus.setText(Messages.getString("RAGAssistant.PassageError"));
                } finally {
                    if (searchWorker == this) {
                        searchWorker = null;
                        cancelSearchButton.setEnabled(false);
                    }
                }
            }
        };
        searchWorker.execute();
    }

    RAGPassageSearch.Result findPassages(String query, BooleanSupplier cancelled) {
        return RAGPassageSearch.find(snapshot, query, cancelled);
    }

    private void moveHit(int direction) {
        if (searchResult != null && !searchResult.hits.isEmpty()) {
            currentHit = Math.floorMod(currentHit + direction, searchResult.hits.size());
            showHit();
        }
    }

    private void showHit() {
        String status;
        switch (searchResult.state) {
            case COMPLETE:
                status = Messages.getString(searchResult.hits.isEmpty()
                        ? "RAGAssistant.PassageNoMatch" : "RAGAssistant.PassageComplete");
                break;
            case EMPTY_QUERY:
                status = Messages.getString("RAGAssistant.PassageEmpty");
                break;
            case INVALID_QUERY:
                status = Messages.getString("RAGAssistant.PassageInvalid", RAGPassageSearch.MAX_QUERY_CHARS);
                break;
            case MATCH_LIMIT:
                status = Messages.getString("RAGAssistant.PassageLimited");
                break;
            case TIME_LIMIT:
                status = Messages.getString("RAGAssistant.PassageTimeout");
                break;
            case CANCELLED:
                status = Messages.getString("RAGAssistant.PassageCancelled");
                break;
            default:
                status = Messages.getString("RAGAssistant.ContextUnavailable");
                break;
        }
        if (currentHit >= 0) {
            RAGPassageSearch.Hit hit = searchResult.hits.get(currentHit);
            tabs.setSelectedIndex(0);
            fragments.setRowSelectionInterval(hit.fragmentOrdinal, hit.fragmentOrdinal);
            fragments.scrollRectToVisible(fragments.getCellRect(hit.fragmentOrdinal, 0, true));
            content.getHighlighter().removeAllHighlights();
            try {
                content.getHighlighter().addHighlight(hit.start, hit.end,
                        new DefaultHighlighter.DefaultHighlightPainter(
                                UIManager.getColor("TextArea.selectionBackground")));
                content.setCaretPosition(hit.start);
            } catch (BadLocationException e) {
                LOGGER.error("Invalid RAG passage offsets", e);
            }
            status = Messages.getString("RAGAssistant.PassagePosition", currentHit + 1,
                    searchResult.hits.size()) + " " + status;
        }
        searchStatus.setText(status);
    }

    /** Called when the inspection dialog or its owning case/session closes. */
    void close() {
        closed = true;
        resetSearch();
    }
}
