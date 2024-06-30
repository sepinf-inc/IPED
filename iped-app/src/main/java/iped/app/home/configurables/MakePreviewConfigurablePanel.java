package iped.app.home.configurables;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.TooManyListenersException;

import javax.swing.DropMode;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.ScrollPaneConstants;

import iped.app.home.MainFrame;
import iped.app.home.configurables.api.ConfigurableValidationException;
import iped.app.home.configurables.uicomponents.IPEDMimeSearchList;
import iped.app.home.configurables.uicomponents.MimeListTransferHandler;
import iped.app.ui.controls.textarea.RegexTextPane;
import iped.engine.config.MakePreviewConfig;
import iped.engine.localization.Messages;

public class MakePreviewConfigurablePanel extends ConfigurablePanel {
    MakePreviewConfig previewConfig;
    protected RegexTextPane textAreaSupportedMimes;
    protected RegexTextPane textAreaSupportedMimesWithLinks;
    private JSplitPane splitPane;
    private DropTarget dtMime;
    private JList<String> listSupportedMimes;
    private JList<String> listSupportedMimesWithLinks;
    private DropTargetAdapter dtlistener;
    private DropTarget dtSupportedMimesWithLinks;
    private DropTarget dtSupportedMimes;
    private JList<String> dragSourceList;
    private MouseAdapter listMA;

    List<String> supportedMimes = new ArrayList<String>();
    List<String> supportedMimesWithLinks = new ArrayList<String>();

    protected MakePreviewConfigurablePanel(MakePreviewConfig configurable, MainFrame mainFrame) {
        super(configurable, mainFrame);
        this.previewConfig = configurable;
        supportedMimes.addAll(this.previewConfig.getSupportedMimes());
        supportedMimesWithLinks.addAll(this.previewConfig.getSupportedMimesWithLinks());
    }

    @Override
    public void createConfigurableGUI() {
        this.setLayout(new BorderLayout());

        splitPane = new JSplitPane();
        splitPane.setDividerSize(4);

        this.add(splitPane, BorderLayout.CENTER);

        IPEDMimeSearchList slmime = new IPEDMimeSearchList();
        slmime.setBackground(this.getBackground());
        splitPane.setLeftComponent(slmime);
        slmime.setTransferHandler(new MimeListTransferHandler(slmime.getListComponent()));
        slmime.setDragEnabled(true);
        slmime.setDropMode(DropMode.ON);
        dtlistener = new DropTargetAdapter() {
            @Override
            public void drop(DropTargetDropEvent dtde) {
                if (dtde.getDropTargetContext().getComponent() == slmime.getListComponent()) {
                    if (dragSourceList == listSupportedMimes) {
                        try {
                            Object[] o = (Object[]) dtde.getTransferable().getTransferData(DataFlavor.stringFlavor);
                            for (int i = 0; i < o.length; i++) {
                                supportedMimes.remove((String) o[i]);
                            }
                            JList supportList = new JList<String>(supportedMimes.toArray(new String[0]));
                            listSupportedMimes.setModel(supportList.getModel());
                            changed = true;
                        } catch (UnsupportedFlavorException | IOException e) {
                            e.printStackTrace();
                        }
                    }
                    if (dragSourceList == listSupportedMimesWithLinks) {
                        try {
                            Object[] o = (Object[]) dtde.getTransferable().getTransferData(DataFlavor.stringFlavor);
                            for (int i = 0; i < o.length; i++) {
                                supportedMimesWithLinks.remove((String) o[i]);
                            }
                            JList supportList = new JList<String>(supportedMimesWithLinks.toArray(new String[0]));
                            listSupportedMimesWithLinks.setModel(supportList.getModel());
                            changed = true;
                        } catch (UnsupportedFlavorException | IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
                if (dtde.getDropTargetContext().getComponent() == listSupportedMimes) {
                    try {
                        Object[] o = (Object[]) dtde.getTransferable().getTransferData(DataFlavor.stringFlavor);
                        for (int i = 0; i < o.length; i++) {
                            supportedMimes.add((String) o[i]);
                        }
                        JList supportList = new JList<String>(supportedMimes.toArray(new String[0]));
                        listSupportedMimes.setModel(supportList.getModel());
                        changed = true;
                    } catch (UnsupportedFlavorException | IOException e) {
                        e.printStackTrace();
                    }
                }
                if (dtde.getDropTargetContext().getComponent() == listSupportedMimesWithLinks) {
                    try {
                        Object[] o = (Object[]) dtde.getTransferable().getTransferData(DataFlavor.stringFlavor);
                        for (int i = 0; i < o.length; i++) {
                            supportedMimesWithLinks.add((String) o[i]);
                        }
                        JList supportList = new JList<String>(supportedMimesWithLinks.toArray(new String[0]));
                        listSupportedMimesWithLinks.setModel(supportList.getModel());
                        changed = true;
                    } catch (UnsupportedFlavorException | IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void dragEnter(DropTargetDragEvent dtde) {
                Component srcComponent = ((DropTarget) dtde.getSource()).getComponent();
                if (srcComponent == listSupportedMimes || srcComponent == listSupportedMimesWithLinks || srcComponent == slmime.getListComponent()) {
                    dtde.acceptDrag(DnDConstants.ACTION_MOVE);
                } else {
                    dtde.rejectDrag();
                }
            }
        };
        dtMime = new DropTarget();
        try {
            dtMime.addDropTargetListener(dtlistener);
        } catch (TooManyListenersException e) {
            e.printStackTrace();
        }
        slmime.setDropTarget(dtMime);

        JSplitPane rightPanel = new JSplitPane();
        rightPanel.setDividerSize(4);
        rightPanel.setResizeWeight(.8);
        rightPanel.setOrientation(JSplitPane.VERTICAL_SPLIT);
        splitPane.setRightComponent(rightPanel);

        listMA = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                dragSourceList = (JList<String>) e.getSource();
            }
        };

        listSupportedMimes = new JList<String>(supportedMimes.toArray(new String[0]));
        JPanel textPanel = new JPanel(new BorderLayout());
        textPanel.setBackground(this.getBackground());
        textPanel.add(new JLabel(Messages.getString(configurable.getClass().getName() + ".supportedMimeTypes")), BorderLayout.NORTH);
        JScrollPane sp = new JScrollPane(listSupportedMimes);
        sp.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        textPanel.add(sp, BorderLayout.CENTER);
        rightPanel.setTopComponent(textPanel);
        listSupportedMimes.setDragEnabled(true);
        listSupportedMimes.setTransferHandler(new MimeListTransferHandler(listSupportedMimes));
        dtSupportedMimes = new DropTarget();
        try {
            dtSupportedMimes.addDropTargetListener(dtlistener);
        } catch (TooManyListenersException e) {
            e.printStackTrace();
        }
        listSupportedMimes.setDropTarget(dtSupportedMimes);
        listSupportedMimes.addMouseListener(listMA);

        listSupportedMimesWithLinks = new JList<String>(supportedMimesWithLinks.toArray(new String[0]));
        listSupportedMimesWithLinks.setAutoscrolls(true);
        textPanel = new JPanel(new BorderLayout());
        textPanel.setBackground(this.getBackground());
        textPanel.add(new JLabel(Messages.getString(configurable.getClass().getName() + ".supportedMimeTypesWithLinks")), BorderLayout.NORTH);
        sp = new JScrollPane(listSupportedMimesWithLinks);
        sp.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        textPanel.add(sp, BorderLayout.CENTER);
        rightPanel.setBottomComponent(textPanel);
        listSupportedMimesWithLinks.setDragEnabled(true);
        listSupportedMimesWithLinks.setTransferHandler(new MimeListTransferHandler(listSupportedMimesWithLinks));
        dtSupportedMimesWithLinks = new DropTarget();
        try {
            dtSupportedMimesWithLinks.addDropTargetListener(dtlistener);
        } catch (TooManyListenersException e) {
            e.printStackTrace();
        }
        listSupportedMimesWithLinks.setDropTarget(dtSupportedMimesWithLinks);
        listSupportedMimesWithLinks.addMouseListener(listMA);
    }

    @Override
    public void applyChanges() throws ConfigurableValidationException {
        if (changed) {
            previewConfig.getSupportedMimes().clear();
            previewConfig.getSupportedMimes().addAll(supportedMimes);
            previewConfig.getSupportedMimesWithLinks().clear();
            previewConfig.getSupportedMimesWithLinks().addAll(supportedMimesWithLinks);
        }
    }
}
