package dpf.sp.gpinf.indexer.desktop;

import java.awt.Desktop;
import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gpinf.dev.data.EvidenceFile;

public class ExternalFileOpen {
	
	private static Logger LOGGER = LoggerFactory.getLogger(ExternalFileOpen.class);
	
	public static void open(final int luceneId){
		new Thread() {
	        public void run() {
	          File file = null;
	          try {
	        	  EvidenceFile item = App.get().appCase.getItemByLuceneID(luceneId);
	              file = item.getTempFile();
	              file.setReadOnly();
	              
	              LOGGER.info("Externally Opening file " + item.getPath());

	            if (file != null) {
	              Desktop.getDesktop().open(file.getCanonicalFile());
	            }

	          } catch (Exception e) {
	            // e.printStackTrace();
	            try {
	              if (System.getProperty("os.name").startsWith("Windows")) { //$NON-NLS-1$ //$NON-NLS-2$
	                Runtime.getRuntime().exec(new String[]{"rundll32", "SHELL32.DLL,ShellExec_RunDLL", "\"" + file.getCanonicalFile() + "\""}); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
	              } else {
	                Runtime.getRuntime().exec(new String[]{"xdg-open", file.toURI().toURL().toString()}); //$NON-NLS-1$
	              }

	            } catch (Exception e2) {
	              e2.printStackTrace();
	              CopiarArquivos.salvarArquivo(luceneId);
	            }
	          }
	        }
	      }.start();
	}

}
