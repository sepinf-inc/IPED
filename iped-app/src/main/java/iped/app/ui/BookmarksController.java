package iped.app.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;

import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.data.IMultiBookmarks;
import iped.engine.data.Bookmarks;
import iped.engine.data.BookmarksImporter;
import iped.engine.data.BookmarksImporter.ImportedBookmark;
import iped.engine.data.BookmarksImporter.SourceCaseNotFoundException;
import iped.engine.data.BookmarksImporter.UnsupportedStateFileException;
import iped.utils.DateUtil;
import iped.utils.IOUtil;
import iped.utils.LocalizedFormat;
import iped.viewers.bookmarks.IBookmarksController;
import iped.viewers.util.ProgressDialog;

public class BookmarksController implements IBookmarksController {

    private static final Logger LOGGER = LoggerFactory.getLogger(BookmarksController.class);

    public static final String HISTORY_DIV = Messages.getString("BookmarksController.HistoryDelimiter"); //$NON-NLS-1$

    private static JFileChooser fileChooser;

    private boolean multiSetting = false;

    private boolean updatingHistory = false;

    private BookmarksController() {
    }

    private static void setupFileChooser() {
        if (fileChooser == null) {
            fileChooser = new JFileChooser();
            fileChooser.setCurrentDirectory(App.get().appCase.getCaseDir());
            fileChooser.setFileFilter(new SearchStateFilter());
            fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        }
    }

    public boolean isUpdatingHistory() {
        return updatingHistory;
    }

    public static BookmarksController get() {
        IBookmarksController ibc = IBookmarksController.get();
        if ((ibc == null) || !(ibc instanceof BookmarksController)) {
            ibc = new BookmarksController();
            IBookmarksController.registerBookmarksController(ibc);
        }

        return (BookmarksController) ibc;
    }

    public void setMultiSetting(boolean value) {
        this.multiSetting = value;
    }

    public boolean isMultiSetting() {
        return this.multiSetting;
    }

    public void addToRecentSearches(String text) {

        if (!text.equals(HISTORY_DIV) && !text.trim().isEmpty() && !App.get().appCase.getKeywords().contains(text)) {
            JComboBox<String> queryComboBox = App.get().queryComboBox;
            IMultiBookmarks multiBookmarks = App.get().appCase.getMultiBookmarks();
            
            if (multiBookmarks.getTypedWords().isEmpty()) {
                queryComboBox.addItem(HISTORY_DIV);
            }
            multiBookmarks.addToTypedWords(text);
            
            // Remove if already present
            queryComboBox.removeItem(text);

            // Insert at the top, right after HISTORY_DIV
            for (int i = 0; i < queryComboBox.getItemCount(); i++) {
                if (queryComboBox.getItemAt(i).equals(HISTORY_DIV)) {
                    queryComboBox.insertItemAt(text, i + 1);
                    break;
                }
            }
        }
    }

    public void updateUIandHistory() {
        updateUIHistory();
        updateUI();
    }

    public void updateUISelection() {
        if (!multiSetting) {
            updateUI();
        }
    }

    public void updateUI() {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                App.get().checkBox.setText(LocalizedFormat.format(App.get().appCase.getMultiBookmarks().getTotalChecked()) + " / " //$NON-NLS-1$
                        + LocalizedFormat.format(App.get().appCase.getTotalItems()));
                App.get().checkBox.setSelected(App.get().appCase.getMultiBookmarks().getTotalChecked() > 0);
                App.get().bookmarksListener.updateModelAndSelection();
                App.get().repaintAllTableViews();
                BookmarksManager.updateCounters();
            }
        });

    }

    public void askAndLoadState() {
        setupFileChooser();
        if (fileChooser.showOpenDialog(App.get()) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            try {
                App.get().appCase.getMultiBookmarks().loadState(file);
                updateUIandHistory();
                BookmarksManager.get().updateList();
                JOptionPane.showMessageDialog(App.get(), Messages.getString("BookmarksController.LoadSuccess"), //$NON-NLS-1$
                        Messages.getString("BookmarksController.LoadSuccess.Title"), JOptionPane.INFORMATION_MESSAGE); //$NON-NLS-1$

            } catch (Exception e1) {
                e1.printStackTrace();
                JOptionPane.showMessageDialog(App.get(), Messages.getString("BookmarksController.LoadError"), //$NON-NLS-1$
                        Messages.getString("BookmarksController.LoadError.Title"), JOptionPane.ERROR_MESSAGE); //$NON-NLS-1$
            }
        }
    }

    public void updateUIHistory() {
        updatingHistory = true;
        Object prevText = App.get().queryComboBox.getSelectedItem();
        App.get().queryComboBox.removeAllItems();
        for (String word : App.get().appCase.getKeywords())
            App.get().queryComboBox.addItem(word);

        if (App.get().appCase.getMultiBookmarks().getTypedWords().size() != 0)
            App.get().queryComboBox.addItem(HISTORY_DIV);

        int insPos = App.get().queryComboBox.getItemCount(); 
        for (String text : App.get().appCase.getMultiBookmarks().getTypedWords()) {
            App.get().queryComboBox.insertItemAt(text, insPos);
        }
        App.get().queryComboBox.setSelectedItem(prevText);
        updatingHistory = false;
    }

    public void askAndSaveState() {
        setupFileChooser();
        if (fileChooser.showSaveDialog(App.get()) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            if (!file.getName().endsWith(Bookmarks.EXT))
                file = new File(file.getPath() + Bookmarks.EXT);

            try {
                App.get().appCase.getMultiBookmarks().saveState(file);
                JOptionPane.showMessageDialog(App.get(), Messages.getString("BookmarksController.SaveSuccess"), //$NON-NLS-1$
                        Messages.getString("BookmarksController.SaveSuccess.Title"), JOptionPane.INFORMATION_MESSAGE); //$NON-NLS-1$

            } catch (IOException e1) {
                e1.printStackTrace();
                JOptionPane.showMessageDialog(App.get(), Messages.getString("BookmarksController.SaveError"), //$NON-NLS-1$
                        Messages.getString("BookmarksController.SaveError.Title"), JOptionPane.ERROR_MESSAGE); //$NON-NLS-1$
            }

        }
    }

    public void askAndImportFromAnotherCase() {
        setupFileChooser();

        if (fileChooser.showOpenDialog(App.get()) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File stateFile = fileChooser.getSelectedFile();
        new Thread(() -> importFromAnotherCase(stateFile)).start();
    }

    /**
     * Computes the import in a background thread, without changing the current
     * case. The result is applied in the EDT just if the user does not cancel.
     */
    private void importFromAnotherCase(File stateFile) {
        ProgressDialog progress = new ProgressDialog(App.get(), null, true);
        progress.setNote(Messages.getString("BookmarksController.ImportFromAnotherCase.Progress.Text"));
        try {
            BookmarksImporter importer = new BookmarksImporter(stateFile, App.get().appCase, progress::isCanceled);
            List<ImportedBookmark> imported = importer.computeImport();
            File notFoundReport = writeNotFoundReport(imported, stateFile);
            progress.close();
            if (!progress.isCanceled()) {
                SwingUtilities.invokeLater(() -> applyImport(imported, notFoundReport));
            }

        } catch (CancellationException e) {
            // canceled by the user, nothing was changed

        } catch (UnsupportedStateFileException e) {
            LOGGER.error(e.getMessage(), e);
            showImportError(Messages.getString("BookmarksController.ImportFromAnotherCase.UnsupportedFile"));

        } catch (SourceCaseNotFoundException e) {
            LOGGER.error(e.getMessage());
            showImportError(MessageFormat.format(Messages.getString("BookmarksController.ImportFromAnotherCase.NotValidCase"),
                    e.getCaseFolder() != null ? e.getCaseFolder().getAbsolutePath() : stateFile.getAbsolutePath()));

        } catch (Exception e) {
            LOGGER.error("Error importing bookmarks from " + stateFile.getAbsolutePath(), e);
            showImportError(Messages.getString("BookmarksController.ImportFromAnotherCase.Error"));

        } finally {
            progress.close();
        }
    }

    private void showImportError(String message) {
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(App.get(), message,
                Messages.getString("BookmarksController.ImportFromAnotherCase.Error.Title"), JOptionPane.ERROR_MESSAGE));
    }

    /**
     * Writes the bookmarked items not found in the current case to a text file in
     * the case root folder (the first case of a multicase), or in the temp folder
     * if not writable.
     *
     * @return the report file, or null if all items were found
     */
    private File writeNotFoundReport(List<ImportedBookmark> imported, File stateFile) {
        if (imported.stream().allMatch(b -> b.getNotFoundItems().isEmpty())) {
            return null;
        }
        File dir = App.get().appCase.getCaseDir();
        if (dir == null && !App.get().appCase.getAtomicSources().isEmpty()) {
            dir = App.get().appCase.getAtomicSources().get(0).getCaseDir();
        }
        if (dir == null || !IOUtil.canCreateFile(dir)) {
            dir = new File(System.getProperty("java.io.tmpdir"));
        }
        String date = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(new Date());
        File report = new File(dir, "IPED-BookmarksImport-" + date + ".txt");
        try {
            int count = BookmarksImporter.writeNotFoundReport(imported, stateFile, report);
            LOGGER.info("{} bookmarked items not found in the current case were written to {}", count,
                    report.getAbsolutePath());
            return report;
        } catch (IOException e) {
            LOGGER.error("Error writing report of not found items to " + report.getAbsolutePath(), e);
            return null;
        }
    }

    private void applyImport(List<ImportedBookmark> imported, File notFoundReport) {
        IMultiBookmarks currentBookmarks = App.get().appCase.getMultiBookmarks();
        String timestamp = DateUtil.dateToString(new Date());
        String importPrefix = String.format("[%s_%s] - ", Messages.getString("BookmarksController.ImportFromAnotherCase.Prefix"), timestamp);

        Set<KeyStroke> usedKeyStrokes = new HashSet<>();
        int created = 0;
        for (ImportedBookmark bookmark : imported) {
            // bookmarks without items found are just listed in the report
            if (bookmark.getTargetIds().isEmpty()) {
                continue;
            }
            String newBookmarkName = importPrefix + bookmark.getName();
            currentBookmarks.newBookmark(newBookmarkName);
            currentBookmarks.setBookmarkComment(newBookmarkName, bookmark.getComment());
            if (bookmark.getColor() != null) {
                currentBookmarks.setBookmarkColor(newBookmarkName, bookmark.getColor());
            }
            currentBookmarks.setInReport(newBookmarkName, bookmark.isInReport());

            KeyStroke stroke = bookmark.getKeyStroke();
            if (stroke != null && !BookmarksManager.get().isKeyStrokeAlreadyUsed(stroke) && usedKeyStrokes.add(stroke)) {
                currentBookmarks.setBookmarkKeyStroke(newBookmarkName, stroke);
            }

            currentBookmarks.addBookmark(bookmark.getTargetIds(), newBookmarkName);
            created++;
        }

        if (created > 0) {
            currentBookmarks.saveState();
            BookmarksManager.get().updateList();
            updateUI();
        }

        showImportReport(imported, created, notFoundReport);
    }

    private void showImportReport(List<ImportedBookmark> imported, int created, File notFoundReport) {
        String[] columns = { Messages.getString("BookmarksController.ImportFromAnotherCase.Report.Bookmark"),
                Messages.getString("BookmarksController.ImportFromAnotherCase.Report.SourceItems"),
                Messages.getString("BookmarksController.ImportFromAnotherCase.Report.Found"),
                Messages.getString("BookmarksController.ImportFromAnotherCase.Report.NotFound"),
                Messages.getString("BookmarksController.ImportFromAnotherCase.Report.WithoutTrackId") };

        Object[][] rows = new Object[imported.size()][];
        long totalSource = 0, totalFound = 0;
        for (int i = 0; i < imported.size(); i++) {
            ImportedBookmark b = imported.get(i);
            rows[i] = new Object[] { b.getName(), b.getSourceItems(), b.getFoundSourceItems(), b.getNotFoundSourceItems(),
                    b.getSourceItemsWithoutTrackId() };
            totalSource += b.getSourceItems();
            totalFound += b.getFoundSourceItems();
            LOGGER.info("Imported bookmark '{}': {} source items, {} found, {} not found, {} without trackId", b.getName(),
                    b.getSourceItems(), b.getFoundSourceItems(), b.getNotFoundSourceItems(), b.getSourceItemsWithoutTrackId());
        }

        JTable table = new JTable(new DefaultTableModel(rows, columns) {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }

            @Override
            public Class<?> getColumnClass(int column) {
                return column == 0 ? String.class : Integer.class;
            }
        });
        table.setAutoCreateRowSorter(true);
        table.getColumnModel().getColumn(0).setPreferredWidth(300);
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setPreferredSize(new Dimension(700, Math.min(400, 60 + 20 * rows.length)));

        String summary = MessageFormat.format(Messages.getString("BookmarksController.ImportFromAnotherCase.Report.Summary"),
                LocalizedFormat.format(totalFound), LocalizedFormat.format(totalSource), created);

        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.add(new JLabel(summary), BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);

        long totalNotFound = totalSource - totalFound;
        String title = Messages.getString("BookmarksController.ImportFromAnotherCase.Report.Title");
        if (totalNotFound == 0) {
            JOptionPane.showMessageDialog(App.get(), panel, title, JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        String notFoundMsg = notFoundReport != null
                ? MessageFormat.format(Messages.getString("BookmarksController.ImportFromAnotherCase.Report.NotFoundDetails"),
                        LocalizedFormat.format(totalNotFound), notFoundReport.getAbsolutePath())
                : MessageFormat.format(Messages.getString("BookmarksController.ImportFromAnotherCase.Report.NotFoundNoDetails"),
                        LocalizedFormat.format(totalNotFound));
        JLabel notFoundLabel = new JLabel("<html>" + escapeHtml(notFoundMsg).replace("\n", "<br>") + "</html>");
        panel.add(notFoundLabel, BorderLayout.SOUTH);

        if (notFoundReport == null) {
            JOptionPane.showMessageDialog(App.get(), panel, title, JOptionPane.WARNING_MESSAGE);
            return;
        }
        String openLog = Messages.getString("BookmarksController.ImportFromAnotherCase.Report.OpenLog");
        String close = Messages.getString("BookmarksController.ImportFromAnotherCase.Report.Close");
        int option = JOptionPane.showOptionDialog(App.get(), panel, title, JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE,
                null, new Object[] { openLog, close }, close);
        if (option == 0) {
            ExternalFileOpen.open(notFoundReport);
        }
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
