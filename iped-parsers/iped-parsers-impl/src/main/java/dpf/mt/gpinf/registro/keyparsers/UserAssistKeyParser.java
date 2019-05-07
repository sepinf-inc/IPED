package dpf.mt.gpinf.registro.keyparsers;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.nio.file.attribute.FileTime;
import java.text.DateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.ArrayUtils;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import dpf.mt.gpinf.registro.model.KeyNode;
import dpf.mt.gpinf.registro.model.KeyValue;
import dpf.sp.gpinf.indexer.parsers.util.EmbeddedItem;
import dpf.sp.gpinf.indexer.parsers.util.EmbeddedParent;

public class UserAssistKeyParser extends HtmlKeyParser {
    static HashMap<String, String> knownFolders = new HashMap<String, String>();
    static {
        knownFolders.put("7C5A40EF-A0FB-4BFC-874A-C0F2E0B9FA8E", "ProgramFilesX86");
        knownFolders.put("6D809377-6AF0-444b-8957-A3773F02200E", "ProgramFilesX64");
        knownFolders.put("905e63b6-c1bf-494e-b29c-65b732d3d21a", "ProgramFiles");
        knownFolders.put("008ca0b1-55b4-4c56-b8a8-4de4b299d3be", "Account Pictures");
        knownFolders.put("de61d971-5ebc-4f02-a3a9-6c82895e5c04", "Get Programs");
        knownFolders.put("724EF170-A42D-4FEF-9F26-B60E846FBA4F", "Administrative Tools");
        knownFolders.put("B2C5E279-7ADD-439F-B28C-C41FE1BBF672", "AppDataDesktop");
        knownFolders.put("7BE16610-1F7F-44AC-BFF0-83E15F2FFCA1", "AppDataDocuments");
        knownFolders.put("7CFBEFBC-DE1F-45AA-B843-A542AC536CC9", "AppDataFavorites");
        knownFolders.put("559D40A3-A036-40FA-AF61-84CB430A4D34", "AppDataProgramData");
        knownFolders.put("A3918781-E5F2-4890-B3D9-A7E54332328C", "Application Shortcuts");
        knownFolders.put("1e87508d-89c2-42f0-8a7e-645a0f50ca58", "Applications");
        knownFolders.put("D0384E7D-BAC3-4797-8F14-CBA229B392B5", "Administrative Tools");
        knownFolders.put("A4115719-D62E-491D-AA7C-E74B8BE3B067", "Start Menu");
        knownFolders.put("82A5EA35-D9CD-47C5-9629-E15D2F714E6E", "Startup");
        knownFolders.put("82A74AEB-AEB4-465C-A014-D097EE346D63", "Control Panel");
        knownFolders.put("B4BFCC3A-DB2C-424C-B029-7FE99A87C641", "Desktop");
        knownFolders.put("FDD39AD0-238F-46AF-ADB4-6C85480369C7", "Documents");
        knownFolders.put("374DE290-123F-4565-9164-39C4925E467B", "Downloads");
        knownFolders.put("1777F761-68AD-4D8A-87BD-30B759FA33DD", "Favorites");
        knownFolders.put("D20BEEC4-5CA8-4905-AE3B-BF251EA09B53", "Network");
        knownFolders.put("33E28130-4E1E-4676-835A-98395C3BC3BB", "Pictures");
        knownFolders.put("5E6C858F-0E22-4760-9AFE-EA3317B67173", "Profile");
        knownFolders.put("A77F5D77-2E2B-44C3-A6A2-ABA601054A51", "Programs");
        knownFolders.put("52a4f021-7b75-48a9-9f6b-4b87a210bc8f", "Quick Launch");
        knownFolders.put("AE50C081-EBD2-438A-8655-8A092E34987A", "Recent");
        knownFolders.put("B7534046-3ECB-4C18-BE4E-64CD4CB7D6AC", "Recycle Bin");
    }

    @Override
    public void parse(KeyNode kn, String title, boolean hasChildren, String keyPath, EmbeddedParent parent,
            ContentHandler handler, Metadata metadata, ParseContext context) throws TikaException {
        EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class,
                new ParsingEmbeddedDocumentExtractor(context));
        if (extractor.shouldParseEmbedded(metadata)) {
            try {
                super.parse(kn, title, hasChildren, keyPath, parent, handler, metadata, context);
                EmbeddedItem item = context.get(EmbeddedItem.class);

                KeyValue[] kvs = kn.getValues();
                if (kvs != null) {
                    for (int j = 0; j < kvs.length; j++) {
                        KeyValue keyValue = kvs[j];

                        if (keyValue.getValueName().equals("MRUListEx")) {

                        } else {
                            Metadata kmeta = new Metadata();

                            ByteArrayInputStream keyValueStream = new ByteArrayInputStream(
                                    generateUserAssistHtml(keyValue, 0, kmeta));

                            context.set(EmbeddedParent.class, item);
                            extractor.parseEmbedded(keyValueStream, handler, kmeta, false);
                        }
                    }
                }
            } catch (IOException | SAXException e) {
                e.printStackTrace();
            }

        }

    }

    private byte[] generateUserAssistHtml(KeyValue keyValue, int startOffset, Metadata kmeta)
            throws UnsupportedEncodingException {
        try {
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            PrintWriter out = new PrintWriter(new OutputStreamWriter(bout, "UTF-8"));

            startDocument(out);

            out.print("<table>");

            byte[] data = keyValue.getValueData();

            String rot13Name = decodeUserAssist(keyValue.getValueName());

            Pattern p = Pattern.compile("\b[A-F0-9]{8}(?:-[A-F0-9]{4}){3}-[A-F0-9]{12}\b");
            Matcher m = p.matcher(rot13Name);
            String folderGuid = null;
            if (m.find()) {
                folderGuid = m.group();
            }

            BigInteger sum = BigInteger.valueOf(0l);
            for (int i = 67; i >= 60; i--) {
                int pow = i - 60;
                sum = sum.add(BigInteger.valueOf(data[i] & 0xFF).multiply(BigInteger.valueOf(256).pow(pow)));
            }
            sum = sum.divide(BigInteger.valueOf(10000)).subtract(BigInteger.valueOf(11644473600000l));

            long runCount = (data[4] & 0xFF) + (data[5] & 0xFF) * 256 + (data[6] & 0xFF) * 256 * 256
                    + (data[7] & 0xFF) * 256 * 256 * 256;

            Date dataUltimaModificacao = new Date(sum.longValue());

            kmeta.add(TikaCoreProperties.TITLE, rot13Name);
            kmeta.set(TikaCoreProperties.MODIFIED, dataUltimaModificacao);

            out.print("<tr>");
            out.print("<td>Valor:</td>");
            out.print("<td>");
            out.print(keyValue.getValueName());
            out.print("</td>");
            out.print("</tr>");

            out.print("<tr>");
            out.print("<td>Caminho do programa:</td>");
            out.print("<td>");
            out.print(rot13Name);
            out.print("</td>");
            out.print("</tr>");

            if ((folderGuid != null) && (knownFolders.containsKey(folderGuid))) {
                out.print("<tr>");
                out.print("<td>Descrição da pasta:</td>");
                out.print("<td>");
                out.print("{" + folderGuid + "}---" + knownFolders.get(folderGuid));
                out.print("</td>");
                out.print("</tr>");
            }

            out.print("<tr>");
            out.print("<td>Quantidade de chamadas:</td>");
            out.print("<td>");
            out.print(runCount);
            out.print("</td>");
            out.print("</tr>");

            if (dataUltimaModificacao != null) {
                out.print("<tr>");
                out.print("<td>Data:</td>");
                out.print("<td>");
                DateFormat df = DateFormat.getDateTimeInstance();
                out.print(df.format(dataUltimaModificacao));
                out.print("</td>");
                out.print("</tr>");
            }

            endDocument(out);
            out.flush();

            return bout.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    static private String decodeUserAssist(String keyValueName) {
        StringBuffer result = new StringBuffer("");

        for (int i = 0; i < keyValueName.length(); i++) {
            if ((keyValueName.charAt(i) >= 'a') && (keyValueName.charAt(i) <= 'z')) {
                if (keyValueName.charAt(i) > 'm') {
                    result.append((char) (keyValueName.charAt(i) - 13));
                } else {
                    result.append((char) (keyValueName.charAt(i) + 13));
                }
            } else if ((keyValueName.charAt(i) >= 'A') && (keyValueName.charAt(i) <= 'Z')) {
                if (keyValueName.charAt(i) > 'M') {
                    result.append((char) (keyValueName.charAt(i) - 13));
                } else {
                    result.append((char) (keyValueName.charAt(i) + 13));
                }
            } else {
                result.append((char) (keyValueName.charAt(i)));
            }
        }

        return result.toString();
    }
}
