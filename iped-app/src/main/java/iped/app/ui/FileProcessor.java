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
package iped.app.ui;

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

import iped.app.ui.ViewerController.ChatContext;
import iped.data.IItem;
import iped.engine.data.DataSource;
import iped.engine.data.IPEDSource;
import iped.engine.data.ItemId;
import iped.engine.datasource.AD1DataSourceReader.AD1InputStreamFactory;
import iped.engine.io.ZIPInputStreamFactory;
import iped.engine.search.SimilarFacesSearch;
import iped.engine.sleuthkit.SleuthkitInputStreamFactory;
import iped.engine.task.index.IndexItem;
import iped.engine.task.similarity.ImageSimilarityTask;
import iped.io.ISeekableInputStreamFactory;
import iped.localization.LocaleResolver;
import iped.properties.ExtraProperties;
import iped.properties.MediaTypes;
import iped.utils.FileInputStreamFactory;
import iped.viewers.HtmlLinkViewer;
import iped.viewers.ImageViewer;
import iped.viewers.api.CancelableWorker;
import iped.viewers.api.IFileProcessor;

public class FileProcessor extends CancelableWorker<Void, Void> implements IFileProcessor {
    private static Logger LOGGER = LoggerFactory.getLogger(FileProcessor.class);

    private static int STATUS_LENGTH = 200;
    private volatile static FileProcessor parsingTask;
    private static Object lock = new Object(), openEvidenceLock = new Object();
    private static HashSet<String> dataSourceOpened = new HashSet<String>();

    private Document doc;
    private int docId;
    private boolean listRelated;
    private int referencingChatLuceneId = -1;
    private String referencingElementName;
    private static volatile IItem lastItem;

    // last chat rendered for a message or attachment, reused while messages of the same
    // chat are selected, so the viewer can just scroll instead of reloading it
    private static int lastChatLuceneId = -1;
    private static IItem lastChatItem;

    public static final void disposeLastItem() {
        if (lastItem != null) {
            lastItem.dispose();
        }
    }

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

            String locale = LocaleResolver.getLocaleString();
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

        disposeItem(lastItem);
        lastItem = item;
        String contentType = null;
        if (item.getMediaType() != null) {
            contentType = item.getMediaType().toString();
        }

        boolean imgSimEnabled = item.getExtraAttribute(ImageSimilarityTask.IMAGE_FEATURES) != null;
        App.get().setEnableGallerySimSearchButton(imgSimEnabled);

        boolean faceEnabled = item.getExtraAttribute(ExtraProperties.FACE_ENCODINGS) != null;
        App.get().setEnableGalleryFaceSearchButton(faceEnabled);

        IItem viewItem = item;

        if (item.getViewFile() != null || item.hasPreview()) {
            viewItem = IndexItem.getItem(doc, iCase, true);
        }

        // messages and attachments can be rendered in the context of their chat
        ChatContext chatContext = getChatContext(item, iCase);

        waitEvidenceOpening(item);

        Set<String> highlights = new HashSet<>();
        highlights.addAll(App.get().getHighlightTerms());

        if (App.get().similarFacesRefItem != null) {
            List<String> locations = SimilarFacesSearch.getMatchLocations(App.get().similarFacesRefItem, item);
            for (String location : locations) {
                highlights.add(ImageViewer.HIGHLIGHT_LOCATION + location);
            }
        }

        App.get().getViewerController().loadFile(item, viewItem, contentType, highlights, chatContext);

        if (listRelated) {
            App.get().subItemModel.listItems(doc);
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
            App.get().parentItemModel.listItems(doc);

            App.get().duplicatesModel.listItems(doc);

            App.get().referencedByModel.listItems(doc);

            App.get().referencesModel.listItems(doc);
        }
    }

    /**
     * Sets the chat that references the item, so the item can be rendered in the
     * context of that chat by the preview viewer.
     * 
     * @param chatLuceneId
     *            the lucene id of the referencing chat
     * @param elementName
     *            the name of the element of the item in the chat
     */
    public void setReferencingChat(int chatLuceneId, String elementName) {
        this.referencingChatLuceneId = chatLuceneId;
        this.referencingElementName = elementName;
    }

    /**
     * Returns the chat where the item can be rendered in context by the preview
     * viewer: the referencing chat, if it was set, or the parent chat of a message
     * with a known position. Returns null if there is no such chat.
     */
    private ChatContext getChatContext(IItem item, IPEDSource iCase) {
        try {
            if (referencingChatLuceneId >= 0) {
                return getChatContext(referencingChatLuceneId, null, referencingElementName);
            }
            String position = item.getMetadata().get(ExtraProperties.PARENT_VIEW_POSITION);
            if (position == null || item.getParentId() == null) {
                return null;
            }
            if (!MediaTypes.isInstanceOf(item.getMediaType(), MediaTypes.CHAT_MESSAGE_MIME) && !MediaTypes.UFED_MESSAGE_MIME.equals(item.getMediaType())) {
                return null;
            }
            int chatLuceneId = App.get().appCase.getLuceneId(new ItemId(iCase.getSourceId(), item.getParentId()));
            return getChatContext(chatLuceneId, position, null);

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private ChatContext getChatContext(int chatLuceneId, String elementId, String elementName) throws IOException {
        if (chatLuceneId < 0) {
            return null;
        }
        if (chatLuceneId != lastChatLuceneId) {
            Document chatDoc = App.get().appCase.getSearcher().doc(chatLuceneId);
            IItem chat = IndexItem.getItem(chatDoc, (IPEDSource) App.get().appCase.getAtomicSource(chatLuceneId), false);
            HtmlLinkViewer linkViewer = App.get().getViewerController().getHtmlLinkViewer();
            if (chat == null || chat.getMediaType() == null || !linkViewer.isSupportedType(chat.getMediaType().toString(), true)) {
                chat = null;
            }
            lastChatLuceneId = chatLuceneId;
            lastChatItem = chat;
        }
        if (lastChatItem == null) {
            return null;
        }
        return new ChatContext(lastChatItem, lastChatItem.getMediaType().toString(), chatLuceneId, elementId, elementName);
    }

    private void waitEvidenceOpening(final IItem item) throws InterruptedException {
        ISeekableInputStreamFactory factory = item.getInputStreamFactory();
        if (!(factory instanceof SleuthkitInputStreamFactory) && !(factory instanceof ZIPInputStreamFactory) && !(factory instanceof AD1InputStreamFactory)) {
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
                    if (visible) {
                        String prevMsg = App.get().progressBar.getString();
                        App.get().progressBar.setString(Messages.getString("FileProcessor.OpeningEvidence")); //$NON-NLS-1$
                        App.get().dialogBar.setVisible(true);
                        App.get().progressBar.setString(prevMsg);
                    } else {
                        // Use dispose() instead of setVisible(false) here as a workaround for #1595, as
                        // sometimes the area covered by the dialog was not cleared after
                        // setVisible(false), in some environments/situations.
                        App.get().dialogBar.dispose();
                    }
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
