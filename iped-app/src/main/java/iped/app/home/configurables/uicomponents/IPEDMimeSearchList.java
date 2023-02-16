package iped.app.home.configurables.uicomponents;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.dnd.DropTarget;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.beans.VetoableChangeListener;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;
import java.util.function.Predicate;

import javax.swing.DropMode;
import javax.swing.JCheckBox;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.TransferHandler;
import javax.swing.event.ListSelectionListener;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MediaTypeRegistry;
import org.eclipse.collections.impl.set.sorted.mutable.TreeSortedSet;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import iped.app.home.configurables.autocompletion.MimetypeAutoCompletionProvider;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.SignatureConfig;

public class IPEDMimeSearchList extends JPanel {
        private RSyntaxTextArea txMimeFilter;
        Predicate<String> availablePredicate;
        private ArrayList<String> availableMimes;
        private JScrollPane mimeListScrollPanel;
        private JList<String> mimeList;
        private MimetypeAutoCompletionProvider cp;
        private JCheckBox ckShowTika;
        private Predicate<String> checkTypedContent;

        public IPEDMimeSearchList() {
            this(null);
        }
        
        public IPEDMimeSearchList(Predicate<String> availablePredicate) {
            super(new BorderLayout());
            this.availablePredicate=availablePredicate;
            
            checkTypedContent = new Predicate<String>() {
                @Override
                public boolean test(String t) {
                    return !txMimeFilter.getText().trim().equals("") && !t.contains(txMimeFilter.getText());
                }
            };

            try {
                getAvailableMimetypes();
                mimeList = new JList<String>(new MimeListModel(availableMimes, checkTypedContent));
            } catch (ParserConfigurationException | SAXException | IOException e) {
                e.printStackTrace();
            }
            
            txMimeFilter = new RSyntaxTextArea(1,20);
            txMimeFilter.setHighlightCurrentLine(false);
            txMimeFilter.addKeyListener(new KeyListener() {
                @Override
                public void keyTyped(KeyEvent e) {
                    mimeList.setModel(new MimeListModel(availableMimes, checkTypedContent));
                }
                @Override public void keyReleased(KeyEvent e) {
                }
                @Override public void keyPressed(KeyEvent e) {}
            });
            
            //mimelistPanel.add(mimesLabel,BorderLayout.BEFORE_FIRST_LINE);
            RTextScrollPane tsMimeFilter = new RTextScrollPane(txMimeFilter);
            tsMimeFilter.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
            tsMimeFilter.setLineNumbersEnabled(false);
            this.add(tsMimeFilter,BorderLayout.NORTH);
            mimeListScrollPanel = new JScrollPane();
            mimeListScrollPanel.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            mimeListScrollPanel.setViewportView(mimeList);
            mimeListScrollPanel.setAutoscrolls(true);
            mimeList.setDropMode(DropMode.ON);
            this.add(mimeListScrollPanel, BorderLayout.CENTER);
            ckShowTika = new JCheckBox();
            ckShowTika.setText("Show Tika mime-types");
            ckShowTika.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    try {
                        getAvailableMimetypes();
                    } catch (ParserConfigurationException | SAXException | IOException e1) {
                        e1.printStackTrace();
                    }
                    mimeList.setModel(new MimeListModel(availableMimes, checkTypedContent));
                }
            });
            this.add(ckShowTika,BorderLayout.SOUTH);
        }
        

        private List<String> getAvailableMimetypes() throws ParserConfigurationException, SAXException, IOException {
            SignatureConfig sc = ConfigurationManager.get().findObject(SignatureConfig.class);

            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            DocumentBuilder docBuilder = dbf.newDocumentBuilder();
            String xml = sc.getConfiguration();
            ByteArrayInputStream bis = new ByteArrayInputStream(xml.getBytes());
            Document doc = docBuilder.parse(bis);
            
            SortedSet<String> mimes = new TreeSortedSet<String>();
            NodeList nl = doc.getElementsByTagName("mime-type");
            for(int i =0; i<nl.getLength(); i++) {
                String mime = nl.item(i).getAttributes().getNamedItem("type").getNodeValue();
                if(availablePredicate==null || availablePredicate.test(mime)) {
                    mimes.add(mime);
                }
            }
            
            if(ckShowTika!=null && ckShowTika.isSelected()) {
                SortedSet<MediaType> mts = MediaTypeRegistry.getDefaultRegistry().getTypes();
                for (Iterator iterator = mts.iterator(); iterator.hasNext();) {
                    MediaType mediaType = (MediaType) iterator.next();
                    if(availablePredicate==null || availablePredicate.test(mediaType.toString())) {
                        mimes.add(mediaType.toString());
                    }
                }
            }
            
            this.availableMimes = new ArrayList<String>();
            this.availableMimes.addAll(mimes);

            return this.availableMimes;
        }


        @Override
        public void setTransferHandler(TransferHandler newHandler) {
            mimeList.setTransferHandler(newHandler);
        }


        public Color getSelectionForeground() {
            return mimeList.getSelectionForeground();
        }


        public void setSelectionForeground(Color selectionForeground) {
            mimeList.setSelectionForeground(selectionForeground);
        }


        public Color getSelectionBackground() {
            return mimeList.getSelectionBackground();
        }


        public void setSelectionBackground(Color selectionBackground) {
            mimeList.setSelectionBackground(selectionBackground);
        }


        public int getVisibleRowCount() {
            return mimeList.getVisibleRowCount();
        }


        public void setDropTarget(DropTarget dt) {
            mimeList.setDropTarget(dt);
        }


        public void setDragEnabled(boolean b) {
            mimeList.setDragEnabled(b);
        }


        public final void setDropMode(DropMode dropMode) {
            mimeList.setDropMode(dropMode);
        }


        public ListSelectionModel getSelectionModel() {
            return mimeList.getSelectionModel();
        }


        public ListSelectionListener[] getListSelectionListeners() {
            return mimeList.getListSelectionListeners();
        }


        public void setSelectionMode(int selectionMode) {
            mimeList.setSelectionMode(selectionMode);
        }


        public int getSelectionMode() {
            return mimeList.getSelectionMode();
        }


        public void clearSelection() {
            mimeList.clearSelection();
        }


        public int[] getSelectedIndices() {
            return mimeList.getSelectedIndices();
        }


        public void setSelectedIndex(int index) {
            mimeList.setSelectedIndex(index);
        }


        public void setSelectedIndices(int[] indices) {
            mimeList.setSelectedIndices(indices);
        }


        public Object[] getSelectedValues() {
            return mimeList.getSelectedValues();
        }


        public List<String> getSelectedValuesList() {
            return mimeList.getSelectedValuesList();
        }


        public int getSelectedIndex() {
            return mimeList.getSelectedIndex();
        }


        public String getSelectedValue() {
            return mimeList.getSelectedValue();
        }


        public void setSelectedValue(Object anObject, boolean shouldScroll) {
            mimeList.setSelectedValue(anObject, shouldScroll);
        }


        public void addVetoableChangeListener(VetoableChangeListener listener) {
            mimeList.addVetoableChangeListener(listener);
        }


        public void refreshModel() {
            mimeList.setModel(new MimeListModel(availableMimes, checkTypedContent));
        }


        public JList<String> getListComponent() {
            return mimeList;
        }
}
