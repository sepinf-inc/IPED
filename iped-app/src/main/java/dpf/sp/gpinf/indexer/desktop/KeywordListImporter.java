package dpf.sp.gpinf.indexer.desktop;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import dpf.sp.gpinf.indexer.search.IPEDSearcher;
import dpf.sp.gpinf.indexer.util.Util;
import iped3.desktop.CancelableWorker;
import iped3.desktop.ProgressDialog;

public class KeywordListImporter extends CancelableWorker {

    ProgressDialog progress;
    ArrayList<String> keywords, result = new ArrayList<String>(), errors = new ArrayList<String>();

    public KeywordListImporter(File file) {

        try {
            keywords = Util.loadKeywords(file.getAbsolutePath(), Charset.defaultCharset().displayName());

            progress = new ProgressDialog(App.get(), this, false);
            progress.setMaximum(keywords.size());

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected Object doInBackground() {

        int i = 0;
        for (String keyword : keywords) {
            if (this.isCancelled()) {
                break;
            }

            try {
                IPEDSearcher task = new IPEDSearcher(App.get().appCase, keyword);
                if (task.multiSearch().getLength() > 0) {
                    result.add(keyword);
                }

                final int j = ++i;
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        progress.setProgress(j);
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
                errors.add(keyword);
            }

        }

        return null;
    }

    @Override
    public void done() {

        progress.close();

        for (String word : result)
            App.get().appCase.getMultiBookmarks().addToTypedWords(word);

        App.get().appCase.getMultiBookmarks().saveState();

        BookmarksController.get().updateUIHistory();

        if (errors.size() > 0) {
            StringBuilder errorTerms = new StringBuilder();
            for (String s : errors) {
                errorTerms.append("\n" + s); //$NON-NLS-1$
            }
            JOptionPane.showMessageDialog(null, Messages.getString("KeywordListImporter.SyntaxError") + errorTerms); //$NON-NLS-1$
        }

        JOptionPane.showMessageDialog(null, Messages.getString("KeywordListImporter.Msg.1") + result.size() //$NON-NLS-1$
                + Messages.getString("KeywordListImporter.Msg.2") + keywords.size()); //$NON-NLS-1$
    }

}
