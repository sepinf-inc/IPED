package dpf.sp.gpinf.indexer.search;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;

import javax.swing.JFileChooser;

import org.apache.lucene.index.AtomicReader;
import org.apache.lucene.index.Fields;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.SlowCompositeReaderWrapper;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;

import dpf.sp.gpinf.indexer.process.IndexItem;
import dpf.sp.gpinf.indexer.util.CancelableWorker;
import dpf.sp.gpinf.indexer.util.ProgressDialog;

public class ExportIndexedTerms extends CancelableWorker<Boolean, Integer> implements PropertyChangeListener {

  ProgressDialog progressMonitor;
  IndexReader reader;
  File file;
  volatile long total = 0;

  public ExportIndexedTerms(IndexReader reader) {
    this.reader = reader;
    this.addPropertyChangeListener(this);
  }

  public void export() {
    JFileChooser fileChooser = new JFileChooser();
    fileChooser.setFileFilter(null);
    fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
    fileChooser.setCurrentDirectory(new File(App.get().codePath).getParentFile().getParentFile());

    if (fileChooser.showSaveDialog(App.get()) == JFileChooser.APPROVE_OPTION) {
      file = fileChooser.getSelectedFile();
      progressMonitor = new ProgressDialog(App.get(), this, true);
      this.execute();
    }
  }

  @Override
  protected Boolean doInBackground() {

    try {
      BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"));

      String[] field = {IndexItem.NAME, IndexItem.CONTENT};
      long progress = 0;

			//Exibe progresso, porém exporta termos por subíndice, logo exporta mtos termos duplicados
			/*for(AtomicReaderContext atomicContext: reader.leaves()){
       AtomicReader atomicReader = atomicContext.reader();
       Fields fields = atomicReader.fields();
       for (String f : field) {
       Terms terms = fields.terms(f);
       total += (int)terms.size();
       }
       }
       this.firePropertyChange("total", 0, total);

       for(AtomicReaderContext atomicContext: reader.leaves()){
       AtomicReader atomicReader = atomicContext.reader();
       Fields fields = atomicReader.fields();
				
       for (String f : field) {
       Terms terms = fields.terms(f);
       TermsEnum termsEnum = terms.iterator(null);	            
       while (termsEnum.next() != null) {
       writer.write(termsEnum.term().utf8ToString());
       writer.write("\r\n");
       if(progress++ % (total / 100) == 0)
       this.firePropertyChange("progress", 0, progress);
       }
       if (this.isCancelled())
       break;
       }
       if (this.isCancelled())
       break;
       }*/
      //Não exibe progresso pois SlowCompositeReaderWrapper não fornece o número total de termos
      AtomicReader atomicReader = SlowCompositeReaderWrapper.wrap(reader);
      Fields fields = atomicReader.fields();
      for (String f : field) {
        Terms terms = fields.terms(f);
        TermsEnum termsEnum = terms.iterator(null);

        while (termsEnum.next() != null) {
          writer.write(termsEnum.term().utf8ToString());
          writer.write("\r\n");
          if (++progress % (1000000) == 0) {
            this.firePropertyChange("progress", 0, progress);
            if (this.isCancelled()) {
              break;
            }
          }
        }

        if (this.isCancelled()) {
          break;
        }
      }

      writer.close();

    } catch (Exception e) {
      e.printStackTrace();
    }

    this.firePropertyChange("close", 0, 1);

    return null;
  }

  @Override
  public void propertyChange(PropertyChangeEvent evt) {

    if (progressMonitor.isCanceled()) {
      this.cancel(true);
    } else if ("progress" == evt.getPropertyName()) {
      long progress = (Long) evt.getNewValue();
      //progressMonitor.setProgress((int)(progress/1000));
      progressMonitor.setNote("Exportados " + progress + " de termos.");// + " de " + total);

    } else if ("total" == evt.getPropertyName()) {
      progressMonitor.setMaximum((int) (total / 1000));

    } else if ("close" == evt.getPropertyName()) {
      progressMonitor.close();
    }

  }
}
