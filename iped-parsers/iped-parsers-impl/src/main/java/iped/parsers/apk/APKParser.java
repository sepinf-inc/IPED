package iped.parsers.apk;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

import javax.imageio.ImageIO;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped.parsers.security.CertificateParser;
import iped.parsers.whatsapp.Util;
import iped.utils.IOUtil;
import iped.utils.ImageUtil;
import net.dongliu.apk.parser.bean.ApkMeta;
import net.dongliu.apk.parser.bean.ApkSigner;
import net.dongliu.apk.parser.bean.ApkV2Signer;
import net.dongliu.apk.parser.bean.CertificateMeta;
import net.dongliu.apk.parser.bean.Icon;
import net.dongliu.apk.parser.bean.IconFace;
import net.dongliu.apk.parser.bean.UseFeature;

public class APKParser extends AbstractParser {
    private static final long serialVersionUID = 8308661247390527209L;
    private static final MediaType apkMimeType = MediaType.application("vnd.android.package-archive");
    private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(apkMimeType);
    
    // TODO: Use another property or reuse this one from CertificateParser?
    private static final Property certStartDate = CertificateParser.NOTBEFORE;

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        TemporaryResources tmp = new TemporaryResources();
        CustomApkFile apkFile = null;
        XHTMLContentHandler xhtml = null;
        File tmpFile = null;
        try {
            TikaInputStream tis = TikaInputStream.get(stream, tmp);
            tmpFile = tis.getFile();

            apkFile = new CustomApkFile(tmpFile);
            ApkMeta apkMeta = apkFile.getApkMeta();

            metadata.set(HttpHeaders.CONTENT_TYPE, apkMimeType.toString());
            metadata.remove(TikaCoreProperties.RESOURCE_NAME_KEY);

            xhtml = new XHTMLContentHandler(handler, metadata);
            xhtml.startDocument();

            xhtml.startElement("style");
            xhtml.characters(
                    ".tab {border-collapse: collapse; font-family: Arial, sans-serif; margin-right: 32px; margin-bottom: 32px; } "
                            + "img { width: 64px; } "
                            + ".prop { border: solid; border-width: thin; padding: 3px; text-align: left; background-color:#EEEEEE; vertical-align: middle; white-space: nowrap; } "
                            + ".ico { border: solid; border-width: thin; padding: 2px; text-align: center; background-color:#BBBBBB; vertical-align: middle; } "
                            + ".val { border: solid; border-width: thin; padding: 3px; text-align: left; vertical-align: middle; } ");
            xhtml.endElement("style");
            xhtml.newline();
            xhtml.startElement("table", "class", "tab");

            String name = apkMeta.getName();
            if (name == null || name.isBlank()) {
                name = apkMeta.getLabel();
            }

            Icon icon = null;
            List<IconFace> iconFaces = apkFile.getAllIcons();
            for (IconFace f : iconFaces) {
                if (f instanceof Icon && f.isFile() && f.getData() != null) {
                    if (icon == null || icon.getData().length < f.getData().length) {
                        icon = (Icon) f;
                    }
                }
            }

            add(xhtml, icon, name);
            add(xhtml, "Package", apkMeta.getPackageName(), false);
            add(xhtml, "Version", apkMeta.getVersionName(), false);
            add(xhtml, "SDK Version", apkMeta.getCompileSdkVersion(), false);

            StringBuilder sb = new StringBuilder();
            Set<String> seenCertificates = new HashSet<String>();
            List<ApkSigner> signers = null;
            try {
                signers = apkFile.getApkSingers();
            } catch (Exception e) {
            }
            if (signers != null && !signers.isEmpty()) {
                for (ApkSigner s : signers) {
                    sb.append("Path: ").append(s.getPath()).append("\n");
                    for (CertificateMeta m : s.getCertificateMetas()) {
                        if (seenCertificates.add(m.toString())) {
                            sb.append(formatCertificate(m));
                            // TODO: Keep just one date or use a multivalued property? 
                            metadata.set(certStartDate, m.getStartDate());
                        }
                    }
                }
                add(xhtml, "Signers", sb.toString(), true);
            }

            List<ApkV2Signer> signers2 = null;
            try {
                signers2 = apkFile.getApkV2Singers();
            } catch (Exception e) {
            }
            if (signers2 != null && !signers2.isEmpty()) {
                List<CertificateMeta> certificates = new ArrayList<CertificateMeta>();
                for (ApkV2Signer s : signers2) {
                    for (CertificateMeta m : s.getCertificateMetas()) {
                        if (seenCertificates.add(m.toString())) {
                            certificates.add(m);
                            metadata.set(certStartDate, m.getStartDate());
                        }
                    }
                }
                if (!certificates.isEmpty()) {
                    sb.delete(0, sb.length());
                    for (CertificateMeta m : certificates) {
                        sb.append(formatCertificate(m));
                    }
                    add(xhtml, "Signers V2", sb.toString(), true);
                }
            }

            sb.delete(0, sb.length());
            List<UseFeature> features = apkMeta.getUsesFeatures();
            Collections.sort(features, new Comparator<UseFeature>() {
                public int compare(UseFeature o1, UseFeature o2) {
                    return o1.getName().compareToIgnoreCase(o2.getName());
                }
            });
            for (UseFeature feature : features) {
                sb.append(feature.getName()).append("\n");
            }
            add(xhtml, "Features", sb.toString(), true);

            sb.delete(0, sb.length());
            List<String> permissions = apkMeta.getUsesPermissions();
            Collections.sort(permissions);
            for (String permission : permissions) {
                sb.append(permission).append("\n");
            }
            add(xhtml, "Permissions", sb.toString(), true);

            String manifestXml = apkFile.getManifestXml();
            add(xhtml, "Manifest XML", manifestXml, true);
        } finally {
            IOUtil.closeQuietly(apkFile);
            tmp.close();
            if (xhtml != null) {
                xhtml.endElement("table");
                xhtml.endDocument();
            }
        }
    }

    private String iconToBase64(Icon icon) throws Exception {
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(icon.getData()));
        int size = 128;
        img = ImageUtil.resizeImage(img, size, size);
        ByteArrayOutputStream out = new ByteArrayOutputStream(4096);
        ImageIO.write(img, "png", out);
        return Util.encodeBase64(out.toByteArray());
    }

    private void add(XHTMLContentHandler xhtml, Icon icon, String val) throws SAXException {
        xhtml.startElement("tr");

        xhtml.startElement("td", "class", "ico");
        String imgBase64 = null;
        if (icon != null) {
            try {
                imgBase64 = iconToBase64(icon);
            } catch (Exception e) {
            }
        }
        if (imgBase64 != null) {
            xhtml.startElement("img", "src", "data:image/png;base64," + imgBase64);
            xhtml.endElement("img");
        }
        xhtml.endElement("td");

        xhtml.startElement("td", "class", "val");
        if (val != null) {
            val = val.trim();
        }
        if (val == null || val.isBlank()) {
            val = "-";
        }
        xhtml.startElement("b");
        xhtml.characters(val);
        xhtml.endElement("b");
        xhtml.endElement("td");

        xhtml.endElement("tr");
        xhtml.newline();
    }

    private void add(XHTMLContentHandler xhtml, String prop, String val, boolean isPre) throws SAXException {
        xhtml.startElement("tr");
        xhtml.startElement("td", "class", "prop");
        xhtml.characters(prop);
        xhtml.endElement("td");
        xhtml.startElement("td", "class", "val");
        if (val != null) {
            val = val.trim();
        }
        if (val == null || val.isBlank()) {
            val = "-";
        }
        if (isPre) {
            xhtml.startElement("pre");
            xhtml.characters(val);
            xhtml.endElement("pre");
        } else {
            xhtml.characters(val);
        }
        xhtml.endElement("td");
        xhtml.endElement("tr");
        xhtml.newline();
    }

    private String formatCertificate(CertificateMeta m) {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss'Z'");
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
        StringBuilder sb = new StringBuilder();
        sb.append("  Certificate").append("\n");
        sb.append("    Algorithm  : ").append(m.getSignAlgorithm()).append("\n");
        sb.append("    MD5        : ").append(m.getCertMd5().toUpperCase()).append("\n");
        sb.append("    OID        : ").append(m.getSignAlgorithmOID()).append("\n");
        sb.append("    Start Date : ").append(df.format(m.getStartDate())).append("\n");
        sb.append("    End Date   : ").append(df.format(m.getEndDate())).append("\n");
        return sb.toString();
    }
}
