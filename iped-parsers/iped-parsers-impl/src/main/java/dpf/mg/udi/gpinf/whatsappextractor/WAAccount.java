package dpf.mg.udi.gpinf.whatsappextractor;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;

import com.dd.plist.NSDictionary;
import com.dd.plist.NSObject;
import com.dd.plist.PropertyListParser;

public class WAAccount extends WAContact {

    private static final String idSuffix = "@s.whatsapp.net"; //$NON-NLS-1$

    private boolean isUnknown = false;

    public WAAccount(String id) {
        super(id);
    }

    public String getTitle() {
        return "WhatsApp Account: " + getName(); //$NON-NLS-1$
    }

    public static WAAccount getFromAndroidXml(InputStream is) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(is);

            XPath xpath = XPathFactory.newInstance().newXPath();
            XPathExpression expr = xpath.compile("/map/string[@name=\"registration_jid\"]");
            String value = (String) expr.evaluate(doc, XPathConstants.STRING);
            if (value == null) {
                expr = xpath.compile("/map/string[@name=\"ph\"]");
                value = (String) expr.evaluate(doc, XPathConstants.STRING);
                if (value == null)
                    return null;
            }
            if (!value.endsWith(idSuffix))
                value += idSuffix;

            WAAccount account = new WAAccount(value);

            expr = xpath.compile("/map/string[@name=\"push_name\"]");
            value = (String) expr.evaluate(doc, XPathConstants.STRING);
            if (value != null)
                account.setWaName(value);

            expr = xpath.compile("/map/string[@name=\"my_current_status\"]");
            value = (String) expr.evaluate(doc, XPathConstants.STRING);
            if (value != null)
                account.setStatus(value);

            return account;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static WAAccount getFromIOSPlist(InputStream is) {
        try {
            NSDictionary rootDict = (NSDictionary) PropertyListParser.parse(is);
            NSObject value = rootDict.get("OwnJabberID");
            if (value == null) {
                value = rootDict.get("LastOwnJabberID");
                if (value == null)
                    return null;
            }
            String strVal = value.toString();
            if (!strVal.endsWith(idSuffix))
                strVal += idSuffix;

            WAAccount account = new WAAccount(strVal);

            value = rootDict.get("FullUserName");
            if (value != null)
                account.setWaName(value.toString());

            value = rootDict.get("CurrentStatusText");
            if (value != null)
                account.setStatus(value.toString());

            return account;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void main(String[] args) {
        try (FileInputStream fis = new FileInputStream(
                "c:/users/nassif/downloads/group.net.whatsapp.WhatsApp.shared.plist")) {
            WAAccount a = getFromIOSPlist(fis);
            System.out.println(a.getId());
            System.out.println(a.getWaName());
            System.out.println(a.getStatus());
        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public boolean isUnknown() {
        return isUnknown;
    }

    public void setUnknown(boolean isUnknown) {
        this.isUnknown = isUnknown;
    }

}
