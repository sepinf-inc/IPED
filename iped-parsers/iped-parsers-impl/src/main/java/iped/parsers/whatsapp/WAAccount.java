package iped.parsers.whatsapp;

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import com.dd.plist.NSDictionary;
import com.dd.plist.NSObject;
import com.dd.plist.PropertyListFormatException;
import com.dd.plist.PropertyListParser;

public class WAAccount extends WAContact {

    private boolean isUnknown = false;

    public WAAccount(String id) {
        super(id);
    }

    public String getTitle() {
        return "WhatsApp Account: " + getName();
    }

    public static WAAccount getFromAndroidXml(InputStream is) throws SAXException, IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(is);

            String id = null, name = null, status = null;

            XPath xpath = XPathFactory.newInstance().newXPath();

            XPathExpression expr = xpath
                    .compile("/map/string[@name=\"com.whatsapp.registration.RegisterPhone.phone_number\"]");
            String value = (String) expr.evaluate(doc, XPathConstants.STRING);
            if (StringUtils.isNotBlank(value)) {
                String phoneNumber = value;
                expr = xpath.compile("/map/string[@name=\"com.whatsapp.registration.RegisterPhone.country_code\"]");
                value = (String) expr.evaluate(doc, XPathConstants.STRING);
                if (StringUtils.isNotBlank(value)) {
                    String countryCode = value;
                    id = countryCode + phoneNumber + waSuffix;
                }
            } else {
                expr = xpath.compile("/map/string[@name=\"registration_jid\"]");
                value = (String) expr.evaluate(doc, XPathConstants.STRING);
                if (StringUtils.isBlank(value)) {
                    expr = xpath.compile("/map/string[@name=\"ph\"]");
                    value = (String) expr.evaluate(doc, XPathConstants.STRING);
                }
                if (StringUtils.isNotBlank(value)) {
                    if (!value.endsWith(waSuffix)) {
                        value += waSuffix;
                    }
                    id = value;
                }
            }

            expr = xpath.compile("/map/string[@name=\"push_name\"]");
            value = (String) expr.evaluate(doc, XPathConstants.STRING);
            if (StringUtils.isNotBlank(value)) {
                name = value;
            }

            expr = xpath.compile("/map/string[@name=\"my_current_status\"]");
            value = (String) expr.evaluate(doc, XPathConstants.STRING);
            if (StringUtils.isNotBlank(value)) {
                status = value;
            }

            if (StringUtils.isAllBlank(id, name, status)) {
                return null;
            }

            WAAccount account = new WAAccount(id);
            account.setWaName(name);
            account.setStatus(status);

            return account;
        } catch (ParserConfigurationException | XPathExpressionException e) {
            throw new RuntimeException(e);
        }
    }

    public static WAAccount getFromIOSPlist(InputStream is) throws SAXException, IOException {
        try {
            NSDictionary rootDict = (NSDictionary) PropertyListParser.parse(is);
            NSObject value = rootDict.get("OwnJabberID");
            if (value == null) {
                value = rootDict.get("LastOwnJabberID");
                if (value == null)
                    return null;
            }
            String strVal = value.toString();
            if (!strVal.endsWith(waSuffix))
                strVal += waSuffix;

            WAAccount account = new WAAccount(strVal);

            value = rootDict.get("FullUserName");
            if (value != null)
                account.setWaName(value.toString());

            value = rootDict.get("CurrentStatusText");
            if (value != null)
                account.setStatus(value.toString());

            return account;
        } catch (PropertyListFormatException | ParseException | ParserConfigurationException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean isUnknown() {
        return isUnknown;
    }

    public void setUnknown(boolean isUnknown) {
        this.isUnknown = isUnknown;
    }
}
