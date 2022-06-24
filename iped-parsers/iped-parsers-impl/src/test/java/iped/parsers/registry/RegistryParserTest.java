package iped.parsers.registry;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.ArrayList;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.ToTextContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped.parsers.registry.model.HiveCell;
import iped.parsers.registry.model.KeyNode;
import iped.parsers.registry.model.RegistryFile;
import iped.parsers.util.ItemInfo;
import junit.framework.TestCase;

public class RegistryParserTest extends TestCase {

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    }

    @Test
    public void testRegistroParserSecurity() throws IOException, SAXException, TikaException, URISyntaxException {

        RegistryParser parser = new RegistryParser();
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, "SECURITY");
        ContentHandler handler = new ToTextContentHandler();
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("test-files/test_security").toURI());
        ParseContext context = new ParseContext();
        ItemInfo itemInfo = new ItemInfo(0, getName(), null, null, "system32/config", false);
        context.set(ItemInfo.class, itemInfo);
        parser.getSupportedTypes(context);
        try (InputStream stream = getStream("test-files/test_security")) {
            parser.parse(stream, handler, metadata, context);

            RegistryFile rf = new RegistryFile(file);
            rf.load();
            HiveCell c = rf.getRootCell();
            KeyNode k = (KeyNode) c.getCellContent();
            assertEquals(k.getKeyName(), "ROOT");

        }
    }

    @Test
    public void testRegistroParserSAM() throws IOException, SAXException, TikaException, URISyntaxException {

        RegistryParser parser = new RegistryParser();
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, "SAM");
        ContentHandler handler = new ToTextContentHandler();
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("test-files/test_sam").toURI());
        ParseContext context = new ParseContext();
        ItemInfo itemInfo = new ItemInfo(0, getName(), null, null, "system32/config", false);
        context.set(ItemInfo.class, itemInfo);
        parser.getSupportedTypes(context);
        try (InputStream stream = getStream("test-files/test_sam")) {
            parser.parse(stream, handler, metadata, context);

            RegistryFile rf = new RegistryFile(file);
            rf.load();
            HiveCell c = rf.getRootCell();
            KeyNode k = (KeyNode) c.getCellContent();
            assertEquals(k.getKeyName(), "ROOT");
        }
    }

    @Test
    public void testRegistroParserSYSTEM() throws IOException, SAXException, TikaException, URISyntaxException {
        RegistryParser parser = new RegistryParser();
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, "SYSTEM");
        ContentHandler handler = new ToTextContentHandler();
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("test-files/test_system").toURI());
        ParseContext context = new ParseContext();
        ItemInfo itemInfo = new ItemInfo(0, getName(), null, null, "system32/config", false);
        context.set(ItemInfo.class, itemInfo);
        parser.getSupportedTypes(context);
        try (InputStream stream = getStream("test-files/test_system")) {
            parser.parse(stream, handler, metadata, context);

            RegistryFile rf = new RegistryFile(file);
            rf.load();
            HiveCell c = rf.getRootCell();
            KeyNode k = (KeyNode) c.getCellContent();
            assertEquals(k.getKeyName(), "CMI-CreateHive{F10156BE-0E87-4EFB-969E-5DA29D131144}");
            KeyNode kf = rf.findKeyNode("ControlSet001/Control/ComputerName/ComputerName");
            assertEquals("Computer Name: " + kf.getValue("ComputerName").getValueDataAsString(),
                    "Computer Name: WKS-WIN732BITA");
            kf = rf.findKeyNode("Setup");
            assertEquals("Working directory: " + kf.getValue("WorkingDirectory").getValueDataAsString(),
                    "Working directory: C:\\Windows\\Panther");

        }
    }

    @Test
    public void testRegistroParserSOFTWARE() throws IOException, SAXException, TikaException, URISyntaxException {

        RegistryParser parser = new RegistryParser();
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, "SOFTWARE");
        ContentHandler handler = new ToTextContentHandler();
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("test-files/test_software").toURI());
        ParseContext context = new ParseContext();
        ItemInfo itemInfo = new ItemInfo(0, getName(), null, null, "system32/config", false);
        context.set(ItemInfo.class, itemInfo);
        parser.getSupportedTypes(context);
        try (InputStream stream = getStream("test-files/test_software")) {
            parser.parse(stream, handler, metadata, context);

            RegistryFile rf = new RegistryFile(file);
            rf.load();
            HiveCell c = rf.getRootCell();
            KeyNode k = (KeyNode) c.getCellContent();
            assertEquals(k.getKeyName(), "CMI-CreateHive{3D971F19-49AB-4000-8D39-A6D9C673D809}");
            KeyNode kf = rf.findKeyNode("Adobe/Acrobat Reader/10.0/Installer");
            assertEquals("Install Date: " + kf.getValue("InstallDate").getValueDataAsString(),
                    "Install Date: 8/28/2011");
            kf = rf.findKeyNode("Microsoft/Windows Defender/");
            assertEquals("Disable Anti Spyware: " + kf.getValue("DisableAntiSpyware").getValueDataAsString(),
                    "Disable Anti Spyware: 1");

        }
    }

    @Test
    public void testRegistroParserNTUSER() throws IOException, SAXException, TikaException, URISyntaxException {

        RegistryParser parser = new RegistryParser();
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, "NTUSER.DAT");
        ContentHandler handler = new ToTextContentHandler();
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("test-files/test_ntuser.dat").toURI());
        ParseContext context = new ParseContext();
        ItemInfo itemInfo = new ItemInfo(0, getName(), null, null, "system32/config", false);
        context.set(ItemInfo.class, itemInfo);
        parser.getSupportedTypes(context);
        try (InputStream stream = getStream("test-files/test_ntuser.dat")) {
            parser.parse(stream, handler, metadata, context);

            RegistryFile rf = new RegistryFile(file);
            rf.load();
            HiveCell c = rf.getRootCell();
            KeyNode k = (KeyNode) c.getCellContent();
            assertEquals(k.getKeyName(), "CMI-CreateHive{6A1C4018-979D-4291-A7DC-7AED1C75B67C}");
            KeyNode kf = rf.findKeyNode("/Control Panel/Desktop");
            assertEquals("Wallpaper: " + kf.getValue("Wallpaper").getValueDataAsString(),
                    "Wallpaper: C:\\Users\\vibranium\\AppData\\Roaming\\Microsoft\\Windows\\Themes\\TranscodedWallpaper.jpg");
            kf = rf.findKeyNode("/Identities");
            ArrayList<KeyNode> identities = kf.getSubKeys();
            for (int i = 0; i < identities.size(); i++) {
                assertEquals("Username: " + identities.get(i).getValue("Username").getValueDataAsString(),
                        "Username: Main Identity");
                assertEquals("Username ID: " + identities.get(i).getValue("User ID").getValueDataAsString(),
                        "Username ID: {EF208C86-65AC-4012-84A1-F1B2647B21FD}");
            }

        }
    }
}
