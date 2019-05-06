package dpf.sp.gpinf.indexer.ui.fileViewer.util;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;

import javax.swing.SwingUtilities;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

import dpf.sp.gpinf.indexer.ui.fileViewer.Messages;
import dpf.sp.gpinf.indexer.util.IOUtil;
import iped3.desktop.ProgressDialog;
import iped3.desktop.CancelableWorker;

/**
 *
 * Classe para descompactar o aplicativo LibreOffice
 *
 */
public class LOExtractor extends CancelableWorker {

  private File output, input;
  private ProgressDialog progressMonitor;
  private int progress = 0, numSubitens = 2876;// TODO obter número de itens automaticamente
  private boolean completed = false;

  public LOExtractor(File input, File output) {
    this.output = output;
    this.input = input;
  }

  public boolean decompressLO() {

    try {
      if (output.exists()) {
        if (IOUtil.countSubFiles(output) >= numSubitens) {
          return true;
        } else {
          IOUtil.deletarDiretorio(output);
        }
      }

      if (input.exists()) {
        SwingUtilities.invokeLater(new Runnable() {
          @Override
          public void run() {
            progressMonitor = new ProgressDialog(null, LOExtractor.this);
            progressMonitor.setMaximum(numSubitens);
            progressMonitor.setNote(Messages.getString("LOExtractor.DecompressingLO")); //$NON-NLS-1$
          }
        });
        
        try(ZipFile zip = new ZipFile(input)){
            Enumeration<ZipArchiveEntry> e = zip.getEntries();
            while(e.hasMoreElements()) {
                ZipArchiveEntry ze = e.nextElement();
                if(ze.isDirectory()) continue;
                try(InputStream in = zip.getInputStream(ze)){
                    Path target = new File(output, ze.getName().replace("\\", "/")).toPath();
                    if(!Files.exists(target.getParent()))
                        Files.createDirectories(target.getParent());
                    
                    Files.copy(in, target);
                    
                    if (++progress == numSubitens)
                        completed = true;
                    
                    progressMonitor.setProgress(progress);
                    
                    if(progressMonitor.isCanceled())
                        break;
                }
            }
        }

        if (!progressMonitor.isCanceled()) {
          progressMonitor.close();
        }

        if (completed) {
          return true;
        } else {
          IOUtil.deletarDiretorio(output);
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }

    return false;

  }

  @Override
  protected Void doInBackground() throws Exception {
    // TODO Auto-generated method stub
    return null;
  }

}
