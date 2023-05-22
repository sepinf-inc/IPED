package iped.app.ui;

import java.io.File;
import java.io.IOException;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import iped.engine.data.Bookmarks;
import iped.utils.LocalizedFormat;

public class BookmarksController {

    public static final String HISTORY_DIV = Messages.getString("BookmarksController.HistoryDelimiter"); //$NON-NLS-1$

    private static BookmarksController instance;

    private static JFileChooser fileChooser;
    private static SearchStateFilter filter;

    private boolean multiSetting = false;

    private boolean updatingHistory = false;

    private BookmarksController() {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                fileChooser = new JFileChooser();
                fileChooser.setCurrentDirectory(App.get().appCase.getCaseDir());
                filter = new SearchStateFilter();
            }
        });
    }

    public boolean isUpdatingHistory() {
        return updatingHistory;
    }

    public static BookmarksController get() {
        if (instance == null)
            instance = new BookmarksController();
        return instance;
    }

    public void setMultiSetting(boolean value) {
        this.multiSetting = value;
    }

    public boolean isMultiSetting() {
        return this.multiSetting;
    }

    public void addToRecentSearches(String texto) {

        if (!texto.equals(HISTORY_DIV) && !texto.trim().isEmpty()
                && !App.get().appCase.getMultiBookmarks().getTypedWords().contains(texto)
                && !App.get().appCase.getKeywords().contains(texto)) {

            if (App.get().appCase.getMultiBookmarks().getTypedWords().size() == 0)
                App.get().queryComboBox.addItem(HISTORY_DIV);

            App.get().queryComboBox.addItem(texto);
            App.get().appCase.getMultiBookmarks().addToTypedWords(texto);
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
                App.get().checkBox.setText(
                        LocalizedFormat.format(App.get().appCase.getMultiBookmarks().getTotalChecked()) + " / " //$NON-NLS-1$
                                + LocalizedFormat.format(App.get().appCase.getTotalItems()));
                App.get().checkBox.setSelected(App.get().appCase.getMultiBookmarks().getTotalChecked() > 0);
                App.get().bookmarksListener.updateModelAndSelection();
                App.get().repaintAllTableViews();
                BookmarksManager.updateCounters();
            }
        });

    }

    public void askAndLoadState() {
        fileChooser.setFileFilter(filter);
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
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

        for (String text : App.get().appCase.getMultiBookmarks().getTypedWords()) {
            App.get().queryComboBox.addItem(text);
        }
        App.get().queryComboBox.setSelectedItem(prevText);
        updatingHistory = false;
    }

    public void askAndSaveState() {
        fileChooser.setFileFilter(filter);
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
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
