package iped.app.home.configurables.autocompletion;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.SortedSet;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MediaTypeRegistry;
import org.eclipse.collections.impl.set.sorted.mutable.TreeSortedSet;
import org.fife.ui.autocomplete.BasicCompletion;
import org.fife.ui.autocomplete.DefaultCompletionProvider;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import iped.engine.config.ConfigurationManager;
import iped.engine.config.SignatureConfig;

public class MimetypeAutoCompletionProvider extends DefaultCompletionProvider {

    private ArrayList<String> keywords;

    public MimetypeAutoCompletionProvider() {
        super();

        TreeSortedSet<String> mimes = null;
        try {
            mimes = getCustomMimetypes();
            keywords = new ArrayList<String>();
            if (mimes != null) {
                keywords.addAll(mimes);
            }
            SortedSet<MediaType> mts = MediaTypeRegistry.getDefaultRegistry().getTypes();
            for (Iterator iterator = mts.iterator(); iterator.hasNext();) {
                MediaType mediaType = (MediaType) iterator.next();
                keywords.add(mediaType.toString());
            }
            Collections.sort(keywords);

            for (Iterator iterator = keywords.iterator(); iterator.hasNext();) {
                String mediaType = (String) iterator.next();
                this.addCompletion(new BasicCompletion(this, mediaType));
            }
        } catch (ParserConfigurationException | SAXException | IOException e) {
            e.printStackTrace();
        }

    }

    private TreeSortedSet<String> getCustomMimetypes() throws ParserConfigurationException, SAXException, IOException {
        SignatureConfig sc = ConfigurationManager.get().findObject(SignatureConfig.class);

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DocumentBuilder docBuilder = dbf.newDocumentBuilder();
        String xml = sc.getConfiguration();
        ByteArrayInputStream bis = new ByteArrayInputStream(xml.getBytes(Charset.forName("UTF-8")));
        Document doc = docBuilder.parse(bis);

        TreeSortedSet<String> mimes = new TreeSortedSet<String>();
        NodeList nl = doc.getElementsByTagName("mime-type");
        for (int i = 0; i < nl.getLength(); i++) {
            String mime = nl.item(i).getAttributes().getNamedItem("type").getNodeValue();
            mimes.add(mime);
        }
        return mimes;
    }

    @Override
    protected boolean isValidChar(char ch) {
        return super.isValidChar(ch) || ch == '/' || ch == '-' || ch == '.' || ch == '-' || ch == '=' || ch == '+' || ch == ';';
    }

    public boolean containsKeyword(String string) {
        return keywords.contains(string);
    }

}
