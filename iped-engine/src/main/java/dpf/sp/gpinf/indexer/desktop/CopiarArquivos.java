/*
 * Copyright 2012-2014, Luis Filipe da Cruz Nassif
 * 
 * This file is part of Indexador e Processador de Evidências Digitais (IPED).
 *
 * IPED is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * IPED is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with IPED.  If not, see <http://www.gnu.org/licenses/>.
 */
package dpf.sp.gpinf.indexer.desktop;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import javax.swing.JFileChooser;
import javax.swing.ProgressMonitor;
import javax.swing.SwingWorker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.util.IOUtil;
import dpf.sp.gpinf.indexer.util.Util;
import iped3.Item;
import iped3.ItemId;

public class CopiarArquivos extends SwingWorker<Boolean, Integer> implements PropertyChangeListener {

    private static Logger LOGGER = LoggerFactory.getLogger(CopiarArquivos.class);

    ArrayList<ItemId> uniqueIds;
    File dir, subdir;
    ProgressMonitor progressMonitor;

    public CopiarArquivos(File dir, ArrayList<ItemId> uniqueIds) {
        this.dir = dir;
        this.subdir = dir;
        this.uniqueIds = uniqueIds;

        progressMonitor = new ProgressMonitor(App.get(), "", "", 0, uniqueIds.size()); //$NON-NLS-1$ //$NON-NLS-2$
        this.addPropertyChangeListener(this);
    }

    private String addExtension(String srcName, String dstName) {
        int srcExtIndex;
        if ((srcExtIndex = srcName.lastIndexOf('.')) > -1) {
            String srcExt = srcName.substring(srcExtIndex);
            if (!dstName.endsWith(srcExt)) {
                if (srcName.endsWith(".[AD]" + srcExt)) { //$NON-NLS-1$
                    srcExt = ".[AD]" + srcExt; //$NON-NLS-1$
                }
                dstName += srcExt;
            }
        }
        return dstName;
    }

    @Override
    protected Boolean doInBackground() throws Exception {

        LOGGER.info("Exporting files to " + dir.getAbsolutePath()); //$NON-NLS-1$
        dir.mkdirs();

        int progress = 0, subdirCount = 1;
        for (ItemId item : uniqueIds) {
            try {
                if (progress % 1000 == 0 && progress > 0) {
                    do {
                        subdir = new File(dir, Integer.toString(subdirCount++));
                    } while (!subdir.mkdir());
                }

                Item e = App.get().appCase.getItemByItemId(item);
                String dstName = Util.getValidFilename(e.getName());
                InputStream in = e.getBufferedStream();

                File dst = new File(subdir, dstName);
                int num = 1;
                while (dst.exists()) {
                    dst = new File(subdir, Util.concat(dstName, num++));
                }

                BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(dst));

                LOGGER.info("Exporting file " + e.getPath()); //$NON-NLS-1$

                IOUtil.copiaArquivo(in, out);

                in.close();
                out.close();

                if (e.getModDate() != null)
                    dst.setLastModified(e.getModDate().getTime());

            } catch (IOException e1) {
                e1.printStackTrace();
                ExportFileTree.showErrorMessage(e1);
                break;
            }

            this.firePropertyChange("progress", progress, ++progress); //$NON-NLS-1$

            if (this.isCancelled()) {
                break;
            }
        }

        return null;
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if ("progress" == evt.getPropertyName()) { //$NON-NLS-1$
            int progress = (Integer) evt.getNewValue();
            progressMonitor.setProgress(progress);
            progressMonitor.setNote(Messages.getString("ExportFiles.Copying") + progress //$NON-NLS-1$
                    + Messages.getString("ExportFiles.of") + uniqueIds.size()); //$NON-NLS-1$
        }
        if (progressMonitor.isCanceled()) {
            this.cancel(true);
        }

    }

    public static void salvarArquivo(int docId) {
        try {
            ArrayList<ItemId> uniqueDoc = new ArrayList<ItemId>();
            uniqueDoc.add(App.get().appCase.getItemId(docId));

            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setFileFilter(null);
            fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (fileChooser.showSaveDialog(App.get()) == JFileChooser.APPROVE_OPTION) {
                File dir = fileChooser.getSelectedFile();
                (new CopiarArquivos(dir, uniqueDoc)).execute();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
