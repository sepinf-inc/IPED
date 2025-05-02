package iped.app.ui.auxview;

import java.awt.Dimension;
import java.awt.GridLayout;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;

import org.apache.tika.Tika;
import org.apache.tika.mime.MediaType;

import iped.app.ui.App;
import iped.app.ui.FileProcessor;
import iped.data.IItem;
import iped.engine.data.Item;
import iped.engine.task.index.IndexItem;
import iped.utils.FileInputStreamFactory;
import iped.viewers.HtmlViewer;
import iped.viewers.IcePDFViewer;
import iped.viewers.ImageViewer;
import iped.viewers.MetadataViewer;
import iped.viewers.MultiViewer;
import iped.viewers.TiffViewer;
import iped.viewers.components.HitsTable;

public class AuxViewPanel extends JPanel {
    private static final long serialVersionUID = 5931045947355161692L;

    private final HitsTable auxViewTable;
    private final AuxViewTableModel auxViewModel;
    private final JScrollPane auxViewScroll;
    private final MetadataViewer metadataViewer;
    private final MultiViewer multiViewer;
    private Tika tika;
    private final List<IItem> loadedItems = new ArrayList<IItem>();

    public AuxViewPanel() {
        auxViewModel = new AuxViewTableModel();
        auxViewTable = new HitsTable(auxViewModel);
        auxViewScroll = new JScrollPane(auxViewTable);
        App.setupItemTable(auxViewTable);
        auxViewTable.addMouseListener(auxViewModel);
        auxViewTable.getSelectionModel().addListSelectionListener(auxViewModel);

        metadataViewer = new MetadataViewer(true) {
            @Override
            public boolean isNumeric(String field) {
                return IndexItem.isNumeric(field);
            }
        };

        multiViewer = new MultiViewer();
        multiViewer.addViewer(new ImageViewer(0, true));
        multiViewer.addViewer(new HtmlViewer());
        multiViewer.addViewer(new IcePDFViewer());
        multiViewer.addViewer(new TiffViewer());

        JSplitPane spVert = new JSplitPane(JSplitPane.VERTICAL_SPLIT, auxViewScroll, metadataViewer.getPanel());
        JSplitPane spHoriz = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, spVert, multiViewer.getPanel());

        spVert.setDividerSize(2);
        spHoriz.setDividerSize(2);
        spVert.setResizeWeight(0.3);
        spHoriz.setResizeWeight(0.5);

        Dimension minimumSize = new Dimension(50, 50);
        spVert.setMinimumSize(minimumSize);
        auxViewScroll.setMinimumSize(minimumSize);

        setLayout(new GridLayout());
        add(spHoriz);
    }

    public HitsTable getTable() {
        return auxViewTable;
    }

    public void listItems(String luceneQuery) {
        auxViewModel.listItems(luceneQuery);
        if (auxViewModel.getRowCount() > 0) {
            App.get().selectDockableTab(App.get().auxViewDock);
            auxViewTable.setRowSelectionInterval(0, 0);
        }
    }

    public void clear() {
        auxViewModel.clear();
        setItem(null);
    }

    public void setItem(IItem item) {
        synchronized (loadedItems) {
            for (IItem prev : loadedItems) {
                prev.dispose();
            }
            if (item != null) {
                loadedItems.add(item);
            }
        }

        metadataViewer.loadFile(item);

        if (item != null) {
            // View
            IItem viewItem = getViewItem(item);
            if (viewItem != null) {
                synchronized (loadedItems) {
                    loadedItems.add(viewItem);
                }
                MediaType mediaType = viewItem.getMediaType();
                if (mediaType != null && multiViewer.isSupportedType(mediaType.toString())) {
                    multiViewer.loadFile(viewItem, mediaType.toString(), Collections.emptySet());
                    return;
                }
            }

            // Actual file
            MediaType mediaType = item.getMediaType();
            if (mediaType != null && multiViewer.isSupportedType(mediaType.toString())) {
                try {
                    FileProcessor.waitEvidenceOpening(item);
                } catch (InterruptedException e) {
                }
                multiViewer.loadFile(item, mediaType.toString(), Collections.emptySet());
                return;
            }

            // Thumb
            byte[] thumb = item.getThumb();
            if (thumb != null && thumb.length > 0) {
                IItem thumbItem = getThumbItem(thumb);
                if (thumbItem != null) {
                    synchronized (loadedItems) {
                        loadedItems.add(thumbItem);
                    }
                    multiViewer.loadFile(thumbItem, thumbItem.getMediaType().toString(), Collections.emptySet());
                    return;
                }
            }
        }

        multiViewer.loadFile(null);
    }

    private IItem getViewItem(IItem item) {
        File viewFile = item.getViewFile();
        if (viewFile != null) {
            Item viewItem = new Item();
            viewItem.setViewFile(viewFile);
            viewItem.setIdInDataSource("");
            viewItem.setInputStreamFactory(new FileInputStreamFactory(viewFile.toPath()));
            viewItem.setTempFile(viewFile);
            try {
                if (tika == null) {
                    tika = new Tika();
                }
                viewItem.setMediaTypeStr(tika.detect(viewFile));
                return viewItem;
            } catch (IOException e) {
                e.printStackTrace();
                viewItem.dispose();
            }
        }
        return null;
    }

    private IItem getThumbItem(byte[] thumb) {
        Item thumbItem = new Item();
        try {
            File tmpFile = File.createTempFile("auxview", ".jpg");
            tmpFile.deleteOnExit();
            Files.write(tmpFile.toPath(), thumb, StandardOpenOption.TRUNCATE_EXISTING);

            thumbItem.setViewFile(tmpFile);
            thumbItem.setIdInDataSource("");
            thumbItem.setInputStreamFactory(new FileInputStreamFactory(tmpFile.toPath()));
            thumbItem.setTempFile(tmpFile);
            thumbItem.setMediaType(MediaType.image("jpeg"));

            return thumbItem;
        } catch (Exception e) {
            e.printStackTrace();
            thumbItem.dispose();
        }
        return null;
    }
}
