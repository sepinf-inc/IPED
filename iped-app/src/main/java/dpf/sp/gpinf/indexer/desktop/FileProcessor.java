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

import java.awt.Dialog.ModalityType;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.SwingUtilities;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.StoredField;
import org.apache.tika.mime.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.IFileProcessor;
import dpf.sp.gpinf.indexer.datasource.AD1DataSourceReader.AD1InputStreamFactory;
import dpf.sp.gpinf.indexer.io.ZIPInputStreamFactory;
import dpf.sp.gpinf.indexer.process.IndexItem;
import dpf.sp.gpinf.indexer.process.task.ImageSimilarityTask;
import dpf.sp.gpinf.indexer.search.IPEDSource;
import dpf.sp.gpinf.indexer.search.SimilarFacesSearch;
import dpf.sp.gpinf.indexer.ui.fileViewer.frames.ImageViewer;
import dpf.sp.gpinf.indexer.util.FileInputStreamFactory;
import gpinf.dev.data.DataSource;
import dpf.sp.gpinf.indexer.sleuthkit.SleuthkitInputStreamFactory;
import iped3.IItem;
import iped3.desktop.CancelableWorker;
import iped3.io.ISeekableInputStreamFactory;

public class FileProcessor extends CancelableWorker<Void, Void> implements IFileProcessor {
    private static Logger LOGGER = LoggerFactory.getLogger(FileProcessor.class);

    private static int STATUS_LENGTH = 200;
    private volatile static FileProcessor parsingTask;
    private static Object lock = new Object(), openEvidenceLock = new Object();
    private static HashSet<String> dataSourceOpened = new HashSet<String>();

    private Document doc;
    private int docId;
    private boolean listRelated;
    private static volatile IItem lastItem;

    public FileProcessor(int docId, boolean listRelated) {
        this.listRelated = listRelated;
        this.docId = docId;

        App.get().setLastSelectedDoc(docId);

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
            File caseDir = App.get().appCase.getAtomicSourceBySourceId(0).getCaseDir();
            doc = new Document();
            doc.add(new StoredField(IndexItem.ID, 0));
            doc.add(new StoredField(IndexItem.NAME, "Help")); //$NON-NLS-1$
            doc.add(new StoredField(IndexItem.CONTENTTYPE, MediaType.TEXT_HTML.toString()));

            String locale = System.getProperty(iped3.util.Messages.LOCALE_SYS_PROP);
            String helpPath = IPEDSource.MODULE_DIR + "/help/Help_" + locale + ".htm"; // $NON-NLS-1$ // $NON-NLS-2$
            if (!new File(caseDir, helpPath).exists()) {
                helpPath = IPEDSource.MODULE_DIR + "/help/Help.htm"; // $NON-NLS-1$
            }
            doc.add(new StoredField(IndexItem.ID_IN_SOURCE, helpPath));
            doc.add(new StoredField(IndexItem.SOURCE_DECODER, FileInputStreamFactory.class.getName()));
            doc.add(new StoredField(IndexItem.SOURCE_PATH, caseDir.getAbsolutePath()));
            doc.add(new StoredField(IndexItem.PATH, helpPath));
            doc.add(new StoredField(IndexItem.EVIDENCE_UUID, new DataSource(null).toString()));
        }
    }

    @Override
    protected Void doInBackground() {

        if (parsingTask != null) {
            // see https://github.com/sepinf-inc/IPED/issues/1099 why this lock is needed
            synchronized (openEvidenceLock) {
                parsingTask.cancel(true);
            }
        }
        parsingTask = this;

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

    private void process() throws InterruptedException {

        LOGGER.info("Opening " + doc.get(IndexItem.PATH)); //$NON-NLS-1$

        // TODO usar nova API e contornar exibição da Ajuda
        IPEDSource iCase = (IPEDSource) App.get().appCase.getAtomicSource(docId);
        App.get().setLastSelectedSource(iCase);
        IItem item = IndexItem.getItem(doc, iCase, false);

        long textSize = iCase.getTextSize(item.getId());
        item.setExtraAttribute(TextParser.TEXT_SIZE, textSize);

        disposeItem(lastItem);
        lastItem = item;
        String contentType = null;
        if (item.getMediaType() != null) {
            contentType = item.getMediaType().toString();
        }
        
        boolean enabled = item.getExtraAttribute(ImageSimilarityTask.SIMILARITY_FEATURES) != null;
        App.get().setEnableGallerySimSearchButton(enabled);

        IItem viewItem = item;

        if (item.getViewFile() != null) {
            viewItem = IndexItem.getItem(doc, iCase, true);
        }

        waitEvidenceOpening(item);

        Set<String> highlights = new HashSet<>();
        highlights.addAll(App.get().getHighlightTerms());

        if (App.get().similarFacesRefItem != null) {
            List<String> locations = SimilarFacesSearch.getMatchLocations(App.get().similarFacesRefItem, item);
            for (String location : locations) {
                highlights.add(ImageViewer.HIGHLIGHT_LOCATION + location);
            }
        }

        App.get().getViewerController().loadFile(item, viewItem, contentType, highlights);

        if (listRelated) {
            App.get().subItemModel.listSubItems(doc);
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
            App.get().parentItemModel.listParents(doc);

            App.get().duplicatesModel.listDuplicates(doc);

            App.get().referencesModel.listReferencingItems(doc);
        }
    }

    private void waitEvidenceOpening(final IItem item) throws InterruptedException {
        ISeekableInputStreamFactory factory = item.getInputStreamFactory();
        if (!(factory instanceof SleuthkitInputStreamFactory) && !(factory instanceof ZIPInputStreamFactory)
                && !(factory instanceof AD1InputStreamFactory)) {
            return;
        }
        if (!dataSourceOpened.contains(item.getDataSource().getUUID())) {
            synchronized (openEvidenceLock) {
                if (Thread.interrupted()) {
                    throw new InterruptedException();
                }
                setWaitVisible(true);
                try (InputStream is = item.getSeekableInputStream()) {
                    is.read();
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    setWaitVisible(false);
                }
            }
            dataSourceOpened.add(item.getDataSource().getUUID());
        }
    }

    private void setWaitVisible(final boolean visible) {
        try {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    ModalityType previous = App.get().dialogBar.getModalityType();
                    String prevMsg = App.get().progressBar.getString();
                    App.get().progressBar.setString(Messages.getString("FileProcessor.OpeningEvidence")); //$NON-NLS-1$
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

}
