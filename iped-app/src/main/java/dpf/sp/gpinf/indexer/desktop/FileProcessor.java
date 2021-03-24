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

import dpf.sp.gpinf.indexer.IFileProcessor;
import dpf.sp.gpinf.indexer.parsers.util.OCROutputFolder;

import java.awt.Dialog.ModalityType;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;

import javax.swing.SwingUtilities;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.IntField;
import org.apache.tika.mime.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.process.IndexItem;
import dpf.sp.gpinf.indexer.search.IPEDSource;
import iped3.IItem;
import iped3.desktop.CancelableWorker;
import iped3.sleuthkit.ISleuthKitItem;

public class FileProcessor extends CancelableWorker<Void, Void> implements IFileProcessor {
    private static Logger LOGGER = LoggerFactory.getLogger(FileProcessor.class);

    private static int STATUS_LENGTH = 200;
    private volatile static FileProcessor parsingTask;
    private static Object lock = new Object(), lock2 = new Object();
    private static HashSet<String> tskDataSourceInited = new HashSet<String>();

    private Document doc;
    private int docId;
    private boolean listRelated;
    private static volatile IItem lastItem;

    public FileProcessor(int docId, boolean listRelated) {
        this.listRelated = listRelated;
        this.docId = docId;

        App.get().getSearchParams().lastSelectedDoc = docId;

        if (parsingTask != null) {
            parsingTask.cancel(true);
        }
        parsingTask = this;

        if (docId >= 0) {
            try {
                doc = App.get().appCase.getSearcher().doc(docId);

                String path = doc.get(IndexItem.PATH);
                if (path.length() > STATUS_LENGTH) {
                    path = "..." + path.substring(path.length() - STATUS_LENGTH); //$NON-NLS-1$
                }
                String status = path;
                if (App.get().appCase.getAtomicSources().size() > 1) {
                    String casePath = App.get().appCase.getAtomicSource(docId).getCaseDir().getCanonicalPath();
                    String separator = !path.startsWith("/") && !path.startsWith("\\") ? "/" : "";
                    status = (casePath + separator + path).replace("\\", "/");
                }
                App.get().status.setText(status);

            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            doc = new Document();
            doc.add(new IntField(IndexItem.ID, 0, Field.Store.YES));
            doc.add(new Field(IndexItem.NAME, "Ajuda.htm", Field.Store.YES, Field.Index.NO)); //$NON-NLS-1$
            String moduleDir = App.get().appCase.getAtomicSourceBySourceId(0).getModuleDir().getAbsolutePath();
            doc.add(new Field(IndexItem.EXPORT, moduleDir + Messages.getString("FileProcessor.HelpPath"), //$NON-NLS-1$
                    Field.Store.YES, Field.Index.NO));
            doc.add(new Field(IndexItem.CONTENTTYPE, MediaType.TEXT_HTML.toString(), Field.Store.YES, Field.Index.NO));
            doc.add(new Field(IndexItem.PATH, moduleDir + Messages.getString("FileProcessor.HelpPath"), Field.Store.YES, //$NON-NLS-1$
                    Field.Index.NO));
        }
    }

    @Override
    protected Void doInBackground() {

        synchronized (lock) {

            if (this.isCancelled()) {
                return null;
            }

            try {
                process();

            } catch (Exception e) {
                e.printStackTrace();
            }

        }
        return null;
    }

    private void process() {

        LOGGER.info("Opening " + doc.get(IndexItem.PATH)); //$NON-NLS-1$

        // TODO usar nova API e contornar exibição da Ajuda
        IPEDSource iCase = (IPEDSource) App.get().appCase.getAtomicSource(docId);
        App.get().getSearchParams().lastSelectedSource = iCase;
        IItem item = IndexItem.getItem(doc, iCase.getModuleDir(), iCase.getSleuthCase(), false);

        long textSize = iCase.getTextSize(item.getId());
        item.setExtraAttribute(TextParser.TEXT_SIZE, textSize);

        disposeItem(lastItem);
        lastItem = item;
        String contentType = null;
        if (item.getMediaType() != null) {
            contentType = item.getMediaType().toString();
        }

        IItem viewItem = item;

        if (item.getViewFile() != null) {
            viewItem = IndexItem.getItem(doc, iCase.getModuleDir(), iCase.getSleuthCase(), true);
        }

        waitSleuthkitInit(item);

        App.get().getViewerController().loadFile(item, viewItem, contentType, App.get().getParams().highlightTerms);

        if (listRelated) {
            // listRelatedItens();
            App.get().subItemModel.listSubItens(doc);
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
            App.get().parentItemModel.listParents(doc);

            App.get().duplicatesModel.listDuplicates(doc);
        }
    }

    private void waitSleuthkitInit(final IItem item) {
        if (item instanceof ISleuthKitItem) {
            ISleuthKitItem sitem = (ISleuthKitItem) item;
            if (sitem.getSleuthFile() == null)
                return;
        }
        if (!tskDataSourceInited.contains(item.getDataSource().getUUID())) {
            tskDataSourceInited.add(item.getDataSource().getUUID());
            setWaitVisible(true);
            try (InputStream is = item.getStream()) {
                is.read();
            } catch (IOException e) {
                e.printStackTrace();
            }
            setWaitVisible(false);
        }
    }

    private void setWaitVisible(final boolean visible) {
        try {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    ModalityType previous = App.get().dialogBar.getModalityType();
                    String prevMsg = App.get().progressBar.getString();
                    App.get().progressBar.setString(Messages.getString("FileProcessor.WaitingTSK")); //$NON-NLS-1$
                    App.get().dialogBar.setModalityType(ModalityType.APPLICATION_MODAL);
                    App.get().dialogBar.setVisible(visible);
                    App.get().dialogBar.setModalityType(previous);
                    App.get().progressBar.setString(prevMsg);
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void disposeItem(final IItem itemToDispose) {
        if (itemToDispose != null) {
            new Thread() {
                public void run() {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                    }
                    itemToDispose.dispose();
                }
            }.start();
        }
    }

    private Thread listTask;

    private void listRelatedItens() {
        if (listTask != null) {
            listTask.interrupt();
        }

        listTask = new Thread(new Runnable() {
            @Override
            public void run() {
                synchronized (lock2) {
                    App.get().subItemModel.listSubItens(doc);
                    if (Thread.currentThread().isInterrupted()) {
                        return;
                    }
                    App.get().parentItemModel.listParents(doc);
                    App.get().duplicatesModel.listDuplicates(doc);
                }

            }
        });
        synchronized (lock2) {
            listTask.start();
        }

    }

}
