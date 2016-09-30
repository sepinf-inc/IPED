package dpf.sp.gpinf.indexer.desktop;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import dpf.sp.gpinf.indexer.search.IPEDSearcher;
import dpf.sp.gpinf.indexer.util.CancelableWorker;
import dpf.sp.gpinf.indexer.util.ProgressDialog;
import dpf.sp.gpinf.indexer.util.Util;

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
        if (task.pesquisarTodos().getLength() > 0) {
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

    if (App.get().appCase.getMarcadores().typedWords.size() == 0) {
      App.get().termo.addItem(Marcadores.HISTORY_DIV);
    }

    for (String word : result) {
      App.get().termo.addItem(word);
      App.get().appCase.getMarcadores().typedWords.add(word);
    }

    App.get().appCase.getMarcadores().saveState();

    if (errors.size() > 0) {
      StringBuilder errorTerms = new StringBuilder();
      for (String s : errors) {
        errorTerms.append("\n" + s);
      }
      JOptionPane.showMessageDialog(null, "Erro de sintaxe nas seguintes expressões: " + errorTerms);
    }

    JOptionPane.showMessageDialog(null, "Importada(s) " + result.size() + " expressão(ões) com ocorrência(s) do total de " + keywords.size());
  }

}
