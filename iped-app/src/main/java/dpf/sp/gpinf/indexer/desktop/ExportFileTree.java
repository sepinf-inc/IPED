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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.lucene.document.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.hash.Hashing;
import com.google.common.hash.HashingOutputStream;

import dpf.sp.gpinf.indexer.desktop.TreeViewModel.Node;
import dpf.sp.gpinf.indexer.process.IndexItem;
import dpf.sp.gpinf.indexer.search.MultiSearchResultImpl;
import dpf.sp.gpinf.indexer.search.IPEDSearcherImpl;
import dpf.sp.gpinf.indexer.process.task.BaseCarveTask;
import dpf.sp.gpinf.indexer.util.Util;
import iped3.IPEDSource;
import iped3.Item;
import iped3.desktop.CancelableWorker;
import iped3.desktop.ProgressDialog;
import iped3.search.IPEDSearcher;
import iped3.search.LuceneSearchResult;
import iped3.search.MultiSearchResult;

public class ExportFileTree extends CancelableWorker {

    private static Logger LOGGER = LoggerFactory.getLogger(ExportFileTree.class);

    int baseDocId;
    boolean onlyChecked, toZip;
    File baseDir;
    private volatile boolean error = false;

    int total, progress = 0;
    ProgressDialog progressDialog;

    HashMap<Integer, Object> parentCache = new HashMap<Integer, Object>();

    ZipArchiveOutputStream zaos;
    HashingOutputStream hos;
    byte[] buf = new byte[8 * 1024 * 1024];

    static Node root = (Node) App.get().tree.getModel().getRoot();

    public ExportFileTree(File baseDir, int baseDocId, boolean onlyChecked) {
        this(baseDir, baseDocId, onlyChecked, false);
    }

    public ExportFileTree(File baseDir, int baseDocId, boolean onlyChecked, boolean toZip) {
        this.baseDir = baseDir;
        this.baseDocId = baseDocId;
        this.onlyChecked = onlyChecked;
        this.toZip = toZip;
    }

    private int[] getItemsToExport(boolean allocated) {

        try {
            String textQuery = "*:*"; //$NON-NLS-1$
            if (baseDocId != root.docId) {
                Document doc = App.get().appCase.getReader().document(baseDocId);

                String id = doc.get(IndexItem.FTKID);
                if (id == null)
                    id = doc.get(IndexItem.ID);

                textQuery = IndexItem.PARENTIDs + ":" + id + " " + IndexItem.ID + ":" + id; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                String sourceUUID = doc.get(IndexItem.EVIDENCE_UUID);
                textQuery = IndexItem.EVIDENCE_UUID + ":" + sourceUUID + " && (" + textQuery + ")"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            }

            String activeStr = IndexItem.SUBITEM + ":false && " + IndexItem.CARVED + ":false && " + IndexItem.DELETED
                    + ":false -" + BaseCarveTask.FILE_FRAGMENT + ":true";
            if (allocated)
                textQuery = "(" + textQuery + ") && (" + activeStr + ")";
            else
                textQuery = "(" + textQuery + ") AND NOT (" + activeStr + ")";

            IPEDSearcher task = new IPEDSearcherImpl(App.get().appCase, textQuery);
            LuceneSearchResult result = task.luceneSearch();

            if (onlyChecked) {
                MultiSearchResultImpl ir = MultiSearchResultImpl.get(App.get().appCase, result);
                ir = (MultiSearchResultImpl) App.get().appCase.getMultiMarcadores().filtrarSelecionados(ir);
                result = MultiSearchResultImpl.get(ir, App.get().appCase);
            }

            return result.getLuceneIds();

        } catch (Exception e) {
            return new int[0];
        }
    }

    private void exportItem(int docId) {
        exportItem(docId, false);
    }

    private Object exportItem(int docId, boolean isParent) {

        Object exportedItem = null;
        if (docId == baseDocId) {
            exportedItem = exportItem(docId, baseDir, isParent);
            parentCache.put(docId, exportedItem);
        } else {
            try {
                Document doc = App.get().appCase.getReader().document(docId);

                int parentDocId = root.docId;
                String parentIdStr = doc.get(IndexItem.PARENTID);
                if (parentIdStr != null) {
                    int parentId = Integer.parseInt(parentIdStr);
                    IPEDSource source = App.get().appCase.getAtomicSource(docId);
                    int baseLuceneId = App.get().appCase.getBaseLuceneId(source);
                    parentDocId = source.getLuceneId(parentId) + baseLuceneId;
                }
                Object exportedParent = parentCache.get(parentDocId);
                if (exportedParent == null) {
                    exportedParent = exportItem(parentDocId, true);
                    parentCache.put(parentDocId, exportedParent);
                }

                exportedItem = exportItem(docId, exportedParent, isParent);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return exportedItem;

    }

    private File getNonExistingFile(File dst) {
        int num = 1;
        String name = dst.getName();
        while (dst.exists()) {
            dst = new File(dst.getParentFile(), Util.concat(name, num++));
        }
        return dst;
    }

    private File getExistingOrNewDir(File dst) {
        int num = 1;
        String name = dst.getName();
        while (dst.exists() && !dst.isDirectory()) {
            dst = new File(dst.getParentFile(), Util.concat(name, num++));
        }
        return dst;
    }

    private Object exportItem(int docId, Object subdir, boolean isParent) {

        if (docId == root.docId || error)
            return null;

        if (subdir == null)
            subdir = baseDir;

        if (toZip)
            return exportItemToZip(docId, subdir, isParent);

        File dst = null;
        Item item = null;
        try {
            item = App.get().appCase.getItemByLuceneID(docId);

            String dstName = Util.getValidFilename(item.getName());
            dst = new File((File) subdir, dstName);

            if (item.isDir() || isParent) {
                if (!dst.isDirectory()) {
                    dst = getExistingOrNewDir(dst);
                    Files.createDirectories(dst.toPath());
                }
            } else {
                LOGGER.info("Exporting file " + item.getPath()); //$NON-NLS-1$

                try (InputStream in = item.getBufferedStream()) {
                    dst = getNonExistingFile(dst);
                    Files.copy(in, dst.toPath());
                }
            }

            if (item.getModDate() != null)
                dst.setLastModified(item.getModDate().getTime());

        } catch (Exception e1) {
            e1.printStackTrace();

        } finally {
            item.dispose();
        }

        if (!isParent) {
            progressDialog.setProgress(++progress);
            progressDialog.setNote(Messages.getString("ExportFileTree.Copied") + progress //$NON-NLS-1$
                    + Messages.getString("ExportFileTree.from") + total); //$NON-NLS-1$
        }

        return dst;

    }

    private String exportItemToZip(int docId, Object subdir, boolean isParent) {

        String dst = null;
        Item item = null;
        try {
            if (zaos == null) {
                BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(baseDir));
                hos = new HashingOutputStream(Hashing.md5(), bos);
                zaos = new ZipArchiveOutputStream(hos);
            }

            item = App.get().appCase.getItemByLuceneID(docId);
            String dstName = item.getName().replace("/", "-").trim(); //$NON-NLS-1$ //$NON-NLS-2$
            if (dstName.isEmpty())
                dstName = "-"; //$NON-NLS-1$

            dst = dstName;
            if (subdir != baseDir)
                dst = subdir + dstName;

            if (item.isDir() || isParent)
                dst += "/"; //$NON-NLS-1$

            ZipArchiveEntry entry = new ZipArchiveEntry(dst);

            if (item.getLength() != null)
                entry.setSize(item.getLength());

            ExportFilesToZip.fillZipDates(entry, item);

            zaos.putArchiveEntry(entry);

            if (!item.isDir() && !isParent) {
                LOGGER.info("Exporting file " + item.getPath()); //$NON-NLS-1$
                try (InputStream in = item.getBufferedStream()) {
                    int len = 0;
                    while ((len = in.read(buf)) != -1 && !this.isCancelled())
                        try {
                            zaos.write(buf, 0, len);
                        } catch (IOException e) {
                            showErrorMessage(e);
                            e.printStackTrace();
                            error = true;
                            return null;
                        }

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            zaos.closeArchiveEntry();

        } catch (IOException e1) {
            showErrorMessage(e1);
            error = true;
            e1.printStackTrace();

        } finally {
            if (item != null)
                item.dispose();
        }

        if (!isParent) {
            progressDialog.setProgress(++progress);
            progressDialog.setNote(Messages.getString("ExportFileTree.Copied") + progress //$NON-NLS-1$
                    + Messages.getString("ExportFileTree.from") + total); //$NON-NLS-1$
        }

        return dst;

    }

    public static void showErrorMessage(final Exception e) {
        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    JOptionPane.showMessageDialog(App.get(),
                            Messages.getString("ExportFileTree.ExportError") + e.getMessage(), "Error", //$NON-NLS-2$
                            JOptionPane.ERROR_MESSAGE);
                }
            });
        } catch (InvocationTargetException | InterruptedException e2) {
            e2.printStackTrace();
        }
    }

    @Override
    public boolean doCancel(boolean mayInterrupt) {
        return cancel(false);
    }

    @Override
    protected Boolean doInBackground() throws Exception {

        progressDialog = new ProgressDialog(App.get(), this);

        ArrayList<Integer> docIds = new ArrayList<Integer>();
        for (int docId : getItemsToExport(true))
            docIds.add(docId);
        for (int docId : getItemsToExport(false))
            docIds.add(docId);

        total = docIds.size();
        progressDialog.setMaximum(total);

        try {
            for (int docId : docIds) {
                exportItem(docId);
                if (progressDialog.isCanceled() || error) {
                    break;
                }
            }

        } finally {
            progressDialog.close();
            if (zaos != null)
                zaos.close();
        }

        return null;
    }

    @Override
    protected void done() {
        if (hos != null && !error) {
            String hash = hos.hash().toString().toUpperCase();
            LOGGER.info("MD5 of " + baseDir.getAbsolutePath() + ": " + hash); //$NON-NLS-1$ //$NON-NLS-2$
            HashDialog dialog = new HashDialog(hash, baseDir.getAbsolutePath());
            dialog.setVisible(true);
        }
    }

    public static void salvarArquivo(int baseDocId, boolean onlyChecked, boolean toZip) {
        try {
            // JFileChooser fileChooser = new JFileChooser();
            // [Triage] Patch para o caso de o arquivo selecionado já existir. Na versão
            // original, ele era sobrescrito silenciosamente.
            JFileChooser fileChooser = new JFileChooser() {
                @Override
                public void approveSelection() {
                    File f = getSelectedFile();
                    if (f.exists() && getDialogType() == SAVE_DIALOG) {
                        int result = JOptionPane.showConfirmDialog(this,
                                Messages.getString("ExportToZIP.FileAlreadyExistsMessageText"),
                                Messages.getString("ExportToZIP.FileAlreadyExistsMessageTitle"),
                                JOptionPane.YES_NO_CANCEL_OPTION);
                        switch (result) {
                            case JOptionPane.YES_OPTION:
                                super.approveSelection();
                                return;
                            case JOptionPane.NO_OPTION:
                                return;
                            case JOptionPane.CLOSED_OPTION:
                                return;
                            case JOptionPane.CANCEL_OPTION:
                                cancelSelection();
                                return;
                        }
                    }
                    super.approveSelection();
                }
            };

            File moduleDir = App.get().appCase.getAtomicSourceBySourceId(0).getModuleDir();
            fileChooser.setCurrentDirectory(moduleDir.getParentFile());

            /*
             * [Triage] Se existe o diretório padrão de dados exportados, como o
             * /home/caine/DADOS_EXPORTADOS, abre como padrão nesse diretório
             */
            File dirDadosExportados = new File(Messages.getString("ExportToZIP.DefaultPath"));
            if (dirDadosExportados.exists()) {
                fileChooser.setCurrentDirectory(dirDadosExportados);
            }

            fileChooser.setFileFilter(null);
            if (toZip) {
                fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                // int randInt = (int)(Math.random() * ((1000 - 0) + 1));
                // fileChooser.setSelectedFile(new
                // File(Messages.getString("ExportToZIP.DefaultName").substring(0,
                // Messages.getString("ExportToZIP.DefaultName").length() - 4)+"_"+ randInt +
                // ".zip"));
                fileChooser.setSelectedFile(new File(Messages.getString("ExportToZIP.DefaultName")));
            } else
                fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

            if (fileChooser.showSaveDialog(App.get()) == JFileChooser.APPROVE_OPTION) {
                File baseDir = fileChooser.getSelectedFile();
                if (toZip && !baseDir.getName().toLowerCase().endsWith(".zip")) //$NON-NLS-1$
                    baseDir = new File(baseDir.getAbsolutePath() + ".zip"); //$NON-NLS-1$

                LOGGER.info("Exporting files to " + baseDir.getAbsolutePath()); //$NON-NLS-1$
                (new ExportFileTree(baseDir, baseDocId, onlyChecked, toZip)).execute();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
