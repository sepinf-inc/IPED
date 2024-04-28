package iped.parsers.util;

import java.io.File;
import java.util.List;

import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import iped.data.IItemReader;
import iped.properties.BasicProps;
import iped.search.IItemSearcher;

public class P2PUtil {
    public static IItemReader searchItemInCase(IItemSearcher searcher, String hashAlgo, String hash) {
        if (searcher == null || hash == null || hash.isBlank()) {
            return null;
        }
        List<IItemReader> items = searcher.search(searcher.escapeQuery(hashAlgo) + ":" + hash);
        if (items == null || items.isEmpty()) {
            return null;
        }
        return items.get(0);
    }

    public static void printNameWithLink(XHTMLContentHandler xhtml, IItemReader item, String name) throws SAXException {
        String hashPath = getPathFromHash(new File("../../../../", ExportFolder.getExportPath()), item.getHash(),
                item.getExt());
        AttributesImpl attributes = new AttributesImpl();
        attributes.addAttribute("", "onclick", "onclick", "CDATA",
                "app.open(\"" + BasicProps.HASH + ":" + item.getHash() + "\")");
        attributes.addAttribute("", "href", "href", "CDATA", hashPath);
        xhtml.startElement("a", attributes);
        xhtml.characters(name);
        xhtml.endElement("a");
    }

    private static String getPathFromHash(File baseDir, String hash, String ext) {
        if (hash == null || hash.length() < 2) {
            return "";
        }
        StringBuilder path = new StringBuilder();
        hash = hash.toUpperCase();
        path.append(hash.charAt(0)).append('/');
        path.append(hash.charAt(1)).append('/');
        path.append(hash).append('.').append(ext);
        File result = new File(baseDir, path.toString());
        return result.getPath();
    }
}
