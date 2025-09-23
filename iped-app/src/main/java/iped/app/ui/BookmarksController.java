package iped.app.ui;

import java.io.File;
import java.io.IOException;

import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import iped.data.IMultiBookmarks;
import iped.engine.data.Bookmarks;
import iped.utils.LocalizedFormat;
import iped.viewers.bookmarks.IBookmarksController;

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
}
