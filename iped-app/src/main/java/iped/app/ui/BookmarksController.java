package iped.app.ui;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.TermInSetQuery;
import org.apache.lucene.util.BytesRef;

import iped.data.IItemId;
import iped.data.IMultiBookmarks;
import iped.engine.data.Bookmarks;
import iped.engine.data.IPEDMultiSource;
import iped.engine.data.IPEDSource;
import iped.engine.search.IPEDSearcher;
import iped.engine.search.MultiSearchResult;
import iped.engine.util.SaveStateThread;
import iped.properties.BasicProps;
import iped.search.IMultiSearchResult;
import iped.utils.DateUtil;
import iped.utils.LocalizedFormat;
import iped.viewers.bookmarks.IBookmarksController;
import iped.viewers.util.ProgressDialog;

public class BookmarksController implements IBookmarksController {

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

    private File lookupCaseFolderFromBookmark(File bookmarkFile) {
        if (bookmarkFile == null || bookmarkFile.getParentFile() == null) {
            return null;
        }

        File immediateParent = bookmarkFile.getParentFile();

        // 1. Check if the file is in the standard module directory
        // Expected structure: [Case Folder] / MODULE_DIR / bookmarkFile
        if (IPEDSource.MODULE_DIR.equals(immediateParent.getName())) {
            File caseFolder = immediateParent.getParentFile();

            if (caseFolder != null && IPEDSource.checkIfIsCaseFolder(caseFolder)) {
                return caseFolder;
            }
        }

        // 2. Check if the file is in a backup directory
        // Expected structure: [Case Folder] / MODULE_DIR / BKP_DIR / bookmarkFile
        if (SaveStateThread.BKP_DIR.equals(immediateParent.getName())) {
            File moduleDir = immediateParent.getParentFile();

            if (moduleDir != null && moduleDir.getParentFile() != null) {
                File caseFolder = moduleDir.getParentFile();

                if (IPEDSource.checkIfIsCaseFolder(caseFolder)) {
                    return caseFolder;
                }
            }
        }

        return null;
    }

    public void askAndImportFromAnotherCase() {
        setupFileChooser();

        if (fileChooser.showOpenDialog(App.get()) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File selectedFile = fileChooser.getSelectedFile();
        File sourceCaseFolder = lookupCaseFolderFromBookmark(selectedFile);

        if (sourceCaseFolder == null) {
            JOptionPane.showMessageDialog(App.get(), Messages.getString("BookmarksController.ImportFromAnotherCase.NotValidCase"),
                            Messages.getString("BookmarksController.ImportFromAnotherCase.NotValidCase.Title"), JOptionPane.ERROR_MESSAGE);
            return;
        }

        new Thread(() -> {
            importFromAnotherCase(sourceCaseFolder);
        }).start();

    }

    private void importFromAnotherCase(File sourceCaseFolder) {

        IMultiBookmarks currentBookmarks = App.get().appCase.getMultiBookmarks();

        ProgressDialog progress = new ProgressDialog(App.get(), null);
        progress.setNote(Messages.getString("BookmarksController.ImportFromAnotherCase.Progress.Text"));

        // Open the source case to map its bookmarks to the current case
        try (IPEDMultiSource sourceCase = new IPEDMultiSource(new IPEDSource(sourceCaseFolder))) {

            IMultiBookmarks sourceBookmarks = sourceCase.getMultiBookmarks();

            Set<String> fieldsToLoad = Collections.singleton(BasicProps.TRACK_ID);
            String timestamp = DateUtil.dateToString(new Date());
            String importPrefix = String.format("[%s_%s] - ", Messages.getString("BookmarksController.ImportFromAnotherCase.Prefix"), timestamp);

            // Fetch all items from the source case to filter by bookmark later
            IPEDSearcher sourceSearcher = new IPEDSearcher(sourceCase, "");
            MultiSearchResult sourceItems = sourceSearcher.multiSearch();

            long totalItems = sourceBookmarks.getBookmarkSet().stream().mapToLong(name -> sourceBookmarks.getBookmarkCount(name)).sum();
            progress.setMaximum(totalItems);

            AtomicLong count = new AtomicLong(0);
            for (String sourceBookmarkName : sourceBookmarks.getBookmarkSet()) {

                String newBookmarkName = importPrefix + sourceBookmarkName;

                // 1. Recreate bookmark metadata in the current case
                currentBookmarks.newBookmark(newBookmarkName);
                currentBookmarks.setBookmarkComment(newBookmarkName, sourceBookmarks.getBookmarkComment(sourceBookmarkName));
                currentBookmarks.setBookmarkColor(newBookmarkName, sourceBookmarks.getBookmarkColor(sourceBookmarkName));

                KeyStroke stroke = sourceBookmarks.getBookmarkKeyStroke(sourceBookmarkName);
                if (stroke != null && !BookmarksManager.get().isKeyStrokeAlreadyUsed(stroke)) {
                    currentBookmarks.setBookmarkKeyStroke(newBookmarkName, stroke);
                }

                // 2. Extract trackIds from the source case's bookmarked items
                IMultiSearchResult bookmarkedSourceItems = sourceBookmarks.filterBookmarks(sourceItems, Collections.singleton(sourceBookmarkName));
                List<BytesRef> trackIds = StreamSupport
                        .stream(bookmarkedSourceItems.getIterator().spliterator(), true)
                        .map(sourceCase::getLuceneId)
                        .map(luceneId -> {
                            if (progress.isCanceled()) {
                                throw new CancellationException();
                            }
                            try {
                                return sourceCase.getReader().document(luceneId, fieldsToLoad);
                            } catch (IOException e) {
                                e.printStackTrace();
                                return null;
                            } finally {
                                progress.setProgress(count.incrementAndGet());
                            }
                        })
                        .filter(Objects::nonNull)
                        .map(doc -> doc.get(BasicProps.TRACK_ID))
                        .filter(StringUtils::isNotBlank)
                        .map(BytesRef::new)
                        .collect(Collectors.toList());

                // Skip querying if the bookmark is empty or has no trackable items
                if (trackIds.isEmpty()) {
                    continue;
                }

                // 3. Locate corresponding items in the current case and link them
                BooleanQuery.Builder queryBuilder = new BooleanQuery.Builder();
                queryBuilder.add(new TermInSetQuery(BasicProps.TRACK_ID, trackIds), Occur.SHOULD);

                IPEDSearcher currentSearcher = new IPEDSearcher(App.get().appCase, queryBuilder.build());
                currentSearcher.setRewritequery(false);

                Set<IItemId> currentCaseItemIds = StreamSupport
                        .stream(currentSearcher.multiSearch().getIterator().spliterator(), true)
                        .collect(Collectors.toSet());

                if (!currentCaseItemIds.isEmpty()) {
                    currentBookmarks.addBookmark(currentCaseItemIds, newBookmarkName);
                }

                if (progress.isCanceled()) {
                    return;
                }
            }

            currentBookmarks.saveState();
            BookmarksManager.get().updateList();
            BookmarksController.get().updateUI();

            JOptionPane.showMessageDialog(App.get(), Messages.getString("BookmarksController.ImportFromAnotherCase.Success"),
                            Messages.getString("BookmarksController.ImportFromAnotherCase.Success.Title"), JOptionPane.INFORMATION_MESSAGE);

        } catch (Exception e) {

            // restore state
            currentBookmarks.loadState();

            if (!progress.isCanceled()) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(App.get(), Messages.getString("BookmarksController.ImportFromAnotherCase.Error"),
                                Messages.getString("BookmarksController.ImportFromAnotherCase.Error.Title"), JOptionPane.ERROR_MESSAGE);
            }
        } finally {
            progress.close();
        }
    }
}
