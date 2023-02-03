package iped.parsers.registry;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Set;
import java.util.TimeZone;

import org.apache.tika.config.Field;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped.data.ICaseData;
import iped.parsers.registry.keys.RegistryKeyParser;
import iped.parsers.registry.keys.RegistryKeyParserManager;
import iped.parsers.registry.model.KeyNode;
import iped.parsers.registry.model.KeyValue;
import iped.parsers.registry.model.RegistryFile;
import iped.parsers.registry.model.RegistryFileException;
import iped.parsers.util.EmbeddedItem;
import iped.parsers.util.EmbeddedParent;
import iped.parsers.util.ItemInfo;

public class RegistryParser extends AbstractParser {

    private static final long serialVersionUID = 1L;

    public static final MediaType REGISTRY_MIME = MediaType.application("x-windows-registry");

    private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(REGISTRY_MIME);

    RegistryKeyParser defaultRegistryKeyParser = null;

    private boolean extractItems = false;
    private boolean keepInCaseData = false;

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext arg0) {
        return SUPPORTED_TYPES;
    }

    @Field
    public void setExtractItems(boolean extractItems) {
        this.extractItems = extractItems;
    }

    @Field
    public void setKeepInCaseData(boolean keepInCaseData) {
        this.keepInCaseData = keepInCaseData;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context) throws IOException, SAXException, TikaException {
        /* filtra os itens a serem parseados */
        String nome = metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY).toUpperCase();
        try {
            if (defaultRegistryKeyParser == null) {
                synchronized (RegistryParser.class) {
                    defaultRegistryKeyParser = RegistryKeyParserManager.getRegistryKeyParserManager().getDefaultRegistryKeyParser();
                }
            }
            if (!(nome.equals("SYSTEM") || nome.equals("SOFTWARE") || nome.equals("SAM") || nome.equals("SECURITY") || nome.equals("NTUSER.DAT")))
                return;
            ItemInfo itemInfo = context.get(ItemInfo.class);
            String caminho = itemInfo.getPath().toLowerCase().replace("\\", "/");
            if (!(caminho.contains("system32/config") || caminho.contains("users") || caminho.contains("settings")))
                return;

            File dbFile = TikaInputStream.get(stream).getFile();

            RegistryFile rf = new RegistryFile(dbFile);
            rf.load(RegistryKeyParserManager.getRegistryKeyParserManager());

            extractCaseData(nome, caminho, rf, context);

            if (extractItems) {
                KeyNode kf = rf.findKeyNode("/");
                recursiveKeyParser(kf, "ROOT", "", handler, metadata, context, new HashMap<String, EmbeddedParent>());
            }
        } catch (Exception e) {
            throw new TikaException("Erro ao decodificar arquivo de registro: " + nome, e);
        } finally {
        }
    }

    public void extractCaseData(String nome, String caminho, RegistryFile rf, ParseContext context) throws RegistryFileException {
        TimeZone tz = null;
        if (nome.equals("SYSTEM")) {
            KeyNode kn = rf.findKeyNode("/Select");
            if (kn != null) {
                KeyValue v = kn.getValue("LastKnownGood");
                if (v != null) {
                    v = kn.getValue("Current");
                }
                if (v != null) {
                    int controlSetIndex = v.getValueDataAsInt();
                    String controlSet = Integer.toString(controlSetIndex);
                    if (controlSet.length() < 3) {
                        controlSet = "/ControlSet" + "0".repeat(3 - controlSet.length()) + controlSet;
                    }
                    kn = rf.findKeyNode(controlSet + "/Control/TimeZoneInformation");
                    if (kn != null) {
                        v = kn.getValue("ActiveTimeBias");
                        if (v != null) {
                            int timeBias = v.getValueDataAsInt();
                            String[] tzs = TimeZone.getAvailableIDs(timeBias * 60 * 1000);
                            if (tzs != null && tzs.length > 0) {
                                tz = TimeZone.getTimeZone(tzs[0]);
                            }
                        }
                    }
                }
            }
        }

        ICaseData caseData = context.get(ICaseData.class);
        if (caseData != null) {
            synchronized (caseData) {
                if (keepInCaseData) {
                    SystemRegistries sr = (SystemRegistries) caseData.getCaseObject("SystemRegistries");
                    if (sr == null) {
                        sr = new SystemRegistries();
                        caseData.addCaseObject("SystemRegistries", sr);
                    }
                    if (nome.equals("SYSTEM")) {
                        sr.addSystem(caminho, rf);
                    }
                    if (nome.equals("SOFTWARE")) {
                        sr.addSoftware(caminho, rf);
                    }
                    if (nome.equals("SAM")) {
                        sr.addSam(caminho, rf);
                    }
                    if (nome.equals("SECURITY")) {
                        sr.addSecurity(caminho, rf);
                    }
                    if (nome.equals("NTUSER.DAT")) {
                        sr.addNtUserDat(caminho, rf);
                    }
                }

                if (nome.equals("SYSTEM")) {
                    if (tz != null) {
                        HashMap<String, TimeZone> tzs = (HashMap<String, TimeZone>) caseData.getCaseObject(ICaseData.TIMEZONE_INFO_KEY);
                        if (tzs == null) {
                            tzs = new HashMap<String, TimeZone>();
                            caseData.addCaseObject(ICaseData.TIMEZONE_INFO_KEY, tzs);
                        }
                        tzs.put(caminho, tz);
                    }
                }
            }
        }
    }
    
    private void keyParser(KeyNode kn, boolean hasChildren, String keyPath, String parentPath, ContentHandler handler, Metadata metadata, ParseContext context, HashMap<String, EmbeddedParent> parentMap) throws TikaException {
        RegistryKeyParser parser = RegistryKeyParserManager.getRegistryKeyParserManager().getRegistryKeyParser(keyPath);
        if (parser != null) {
            String title = keyPath.substring(parentPath.length() + 1);
            EmbeddedParent parent = parentMap.get(parentPath);
            parser.parse(kn, title, hasChildren, keyPath, parent, handler, metadata, context);
            parentMap.put(keyPath, context.get(EmbeddedItem.class));
        } else {
            if (RegistryKeyParserManager.getRegistryKeyParserManager().hasChildRegistered(keyPath)) {
                String title = keyPath.substring(parentPath.length() + 1);
                EmbeddedParent parent = parentMap.get(parentPath);
                defaultRegistryKeyParser.parse(kn, title, hasChildren, keyPath, parent, handler, metadata, context);
                parentMap.put(keyPath, context.get(EmbeddedItem.class));
            }
        }
    }

    private void recursiveKeyParser(KeyNode kn, String keyPath, String parentPath, ContentHandler handler, Metadata metadata, ParseContext context, HashMap<String, EmbeddedParent> parentMap) throws TikaException {
        ArrayList<KeyNode> kns = kn.getSubKeys();

        keyParser(kn, ((kns != null) && (kns.size() > 0)), keyPath, parentPath, handler, metadata, context, parentMap);

        EmbeddedItem item = context.get(EmbeddedItem.class);

        if (RegistryKeyParserManager.getRegistryKeyParserManager().hasChildRegistered(keyPath)) {
            if (kns != null) {
                for (int i = 0; i < kns.size(); i++) {
                    recursiveKeyParser(kns.get(i), keyPath + "/" + kns.get(i).getKeyName(), keyPath, handler, metadata, context, parentMap);
                }
            }
        }
    }

}