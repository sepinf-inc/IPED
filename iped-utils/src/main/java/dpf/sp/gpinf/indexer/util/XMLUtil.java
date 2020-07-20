package dpf.sp.gpinf.indexer.util;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class XMLUtil {

    static public Element getFirstElement(Element el, String tagName) {
        NodeList snl = el.getElementsByTagName(tagName);
        if (snl != null) {
            return (Element) snl.item(0);
        } else {
            return null;
        }
    }

}
