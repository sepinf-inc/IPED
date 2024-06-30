package iped.app.home.configurables.uicomponents;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;
import java.util.function.Predicate;

import javax.swing.JCheckBox;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MediaTypeRegistry;
import org.eclipse.collections.impl.set.sorted.mutable.TreeSortedSet;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import iped.app.ui.Messages;
import iped.app.ui.controls.IPEDConfigSearchList;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.SignatureConfig;

public class IPEDMimeSearchList extends IPEDConfigSearchList {
    private JCheckBox ckShowTika;

    public IPEDMimeSearchList() {
        this(null);
    }

    public IPEDMimeSearchList(Predicate<String> availablePredicate) {
        super(availablePredicate);
        try {
            this.availableItems = getAvailableMimetypes();
        } catch (ParserConfigurationException | SAXException | IOException e) {
            e.printStackTrace();
        }
        createGUI();
    }

    private List<String> getAvailableMimetypes() throws ParserConfigurationException, SAXException, IOException {
        SignatureConfig sc = ConfigurationManager.get().findObject(SignatureConfig.class);

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DocumentBuilder docBuilder = dbf.newDocumentBuilder();
        String xml = sc.getConfiguration();
        ByteArrayInputStream bis = new ByteArrayInputStream(xml.getBytes(Charset.forName("UTF-8")));
        Document doc = docBuilder.parse(bis);

        SortedSet<String> mimes = new TreeSortedSet<String>();
        NodeList nl = doc.getElementsByTagName("mime-type");
        for (int i = 0; i < nl.getLength(); i++) {
            String mime = nl.item(i).getAttributes().getNamedItem("type").getNodeValue();
            if (availablePredicate == null || availablePredicate.test(mime)) {
                mimes.add(mime);
            }
        }

        if (ckShowTika != null && ckShowTika.isSelected()) {
            SortedSet<MediaType> mts = MediaTypeRegistry.getDefaultRegistry().getTypes();
            for (Iterator iterator = mts.iterator(); iterator.hasNext();) {
                MediaType mediaType = (MediaType) iterator.next();
                if (availablePredicate == null || availablePredicate.test(mediaType.toString())) {
                    mimes.add(mediaType.toString());
                }
            }
        }

        this.availableItems = new ArrayList<String>();
        this.availableItems.addAll(mimes);

        return this.availableItems;
    }

    public void createGUI() {
        super.createGUI(new MimeListModel(availableItems, checkTypedContent));

        ckShowTika = new JCheckBox();
        ckShowTika.setText(Messages.get("Home.IPEDMimeSeachList.showTikaMimeTypes"));
        ckShowTika.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    getAvailableMimetypes();
                } catch (ParserConfigurationException | SAXException | IOException e1) {
                    e1.printStackTrace();
                }
                list.setModel(new MimeListModel(availableItems, checkTypedContent));
            }
        });
        this.add(ckShowTika, BorderLayout.SOUTH);
    }

}
