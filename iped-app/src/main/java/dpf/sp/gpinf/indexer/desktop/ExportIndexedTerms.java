package dpf.sp.gpinf.indexer.desktop;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;

import javax.swing.JFileChooser;

import org.apache.lucene.index.Fields;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.MultiFields;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;

import dpf.sp.gpinf.indexer.process.IndexItem;
import iped3.desktop.CancelableWorker;
import iped3.desktop.ProgressDialog;

public class ExportIndexedTerms extends CancelableWorker<Boolean, Integer> implements PropertyChangeListener {

    ProgressDialog progressMonitor;
    LeafReader atomicReader;
    File file;
    volatile long total = 0;

    public ExportIndexedTerms(LeafReader atomicReader) {
        this.atomicReader = atomicReader;
        this.addPropertyChangeListener(this);
    }

    public void export() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(null);
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        File moduleDir = App.get().appCase.getAtomicSourceBySourceId(0).getModuleDir();
        fileChooser.setCurrentDirectory(moduleDir.getParentFile());

        if (fileChooser.showSaveDialog(App.get()) == JFileChooser.APPROVE_OPTION) {
            file = fileChooser.getSelectedFile();
            progressMonitor = new ProgressDialog(App.get(), this, true);
            this.execute();
        }
    }

    @Override
    protected Boolean doInBackground() {

        try {
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8")); //$NON-NLS-1$

            String[] field = { IndexItem.NAME, IndexItem.CONTENT };
            long progress = 0;

            // Exibe progresso, porém exporta termos por subíndice, logo exporta mtos termos
            // duplicados
            /*
             * for(AtomicReaderContext atomicContext: reader.leaves()){ AtomicReader
             * atomicReader = atomicContext.reader(); Fields fields = atomicReader.fields();
             * for (String f : field) { Terms terms = fields.terms(f); total +=
             * (int)terms.size(); } } this.firePropertyChange("total", 0, total);
             * 
             * for(AtomicReaderContext atomicContext: reader.leaves()){ AtomicReader
             * atomicReader = atomicContext.reader(); Fields fields = atomicReader.fields();
             * 
             * for (String f : field) { Terms terms = fields.terms(f); TermsEnum termsEnum =
             * terms.iterator(null); while (termsEnum.next() != null) {
             * writer.write(termsEnum.term().utf8ToString()); writer.write("\r\n");
             * if(progress++ % (total / 100) == 0) this.firePropertyChange("progress", 0,
             * progress); } if (this.isCancelled()) break; } if (this.isCancelled()) break;
             * }
             */

            // Não exibe progresso pois SlowCompositeReaderWrapper não fornece o número
            // total de termos
            for (String f : field) {
                Terms terms = atomicReader.terms(f);
                if (terms == null) {
                    continue;
                }
                TermsEnum termsEnum = terms.iterator();

                while (termsEnum.next() != null) {
                    writer.write(termsEnum.term().utf8ToString());
                    writer.write("\r\n"); //$NON-NLS-1$
                    if (++progress % (1000000) == 0) {
                        this.firePropertyChange("progress", 0, progress); //$NON-NLS-1$
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

        this.firePropertyChange("close", 0, 1); //$NON-NLS-1$

        return null;
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {

        if (progressMonitor.isCanceled()) {
            this.cancel(true);
        } else if ("progress" == evt.getPropertyName()) { //$NON-NLS-1$
            long progress = (Long) evt.getNewValue();
            // progressMonitor.setProgress((int)(progress/1000));
            progressMonitor.setNote(Messages.getString("ExportIndexedTerms.Exported") + progress //$NON-NLS-1$
                    + Messages.getString("ExportIndexedTerms.ofWords"));// + " de " + total); //$NON-NLS-1$

        } else if ("total" == evt.getPropertyName()) { //$NON-NLS-1$
            progressMonitor.setMaximum((int) (total / 1000));

        } else if ("close" == evt.getPropertyName()) { //$NON-NLS-1$
            progressMonitor.close();
        }

    }
}
