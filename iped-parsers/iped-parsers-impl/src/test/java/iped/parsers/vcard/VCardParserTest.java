package iped.parsers.vcard;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped.properties.ExtraProperties;
import junit.framework.TestCase;

public class VCardParserTest extends TestCase {

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    }

    private static int getVersion() {
        String version = System.getProperty("java.version");
        if (version.startsWith("1.")) {
            version = version.substring(2, 3);
        } else {
            int dot = version.indexOf(".");
            if (dot != -1) {
                version = version.substring(0, dot);
            }
        }
        return Integer.parseInt(version);
    }

    @Test
    public void testVCardSimpleParser() throws IOException, SAXException, TikaException {

        VCardParser parser = new VCardParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        try (InputStream stream = getStream("test-files/test_contacts.vcf")) {
            parser.parse(stream, handler, metadata, context);

            String hts = handler.toString();

            assertTrue(hts.contains("Internet"));
            assertTrue(hts.contains("guilhermeandreuce@gmail.com"));

            assertEquals("guilhermeandreuce@gmail.com", metadata.get(ExtraProperties.USER_EMAIL));

        }
    }

    @Test
    public void testVCardMultipleParser() throws IOException, SAXException, TikaException {

        VCardParser parser = new VCardParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        try (InputStream stream = getStream("test-files/test_contactsMultiple.vcf")) {
            parser.parse(stream, handler, metadata, context);

            String hts = handler.toString();
            String[] phonenumbers;
            String[] usernames;
            phonenumbers = metadata.getValues(ExtraProperties.USER_PHONE);
            usernames = metadata.getValues(ExtraProperties.USER_NAME);

            assertTrue(hts.contains("Family: Flamingo"));
            assertTrue(hts.contains("Given: Sushi"));
            assertTrue(hts.contains("Additional"));
            assertTrue(hts.contains("myContacts"));
            assertTrue(hts.contains("021 (61)3468-2000"));
            assertTrue(hts.contains("99592-3794"));

            assertEquals("021 (61)3468-2000", phonenumbers[0]);
            assertEquals("021 (61)3011-7666", phonenumbers[1]);
            assertEquals("99592-3794", phonenumbers[2]);
            assertEquals("Taxi Lago Norte", usernames[0]);
            assertEquals("Sushi Loko Flamingo", usernames[1]);
            assertEquals("Smart Assistência Técnica", usernames[2]);

        }
    }

    @Test
    public void testVCardCompleteInfoParser() throws IOException, SAXException, TikaException, ParseException {

        VCardParser parser = new VCardParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        if (getVersion() < 9) {
            try (InputStream stream = getStream("test-files/test_contactsCompleteInfo.vcf")) {
                parser.parse(stream, handler, metadata, context);

                String hts = handler.toString();
                String[] phonenumbers;
                String[] useraddresses;
                String[] useremails;
                String[] userurls;
                String[] userorganizations;
                Date userBirth;

                phonenumbers = metadata.getValues(ExtraProperties.USER_PHONE);
                useraddresses = metadata.getValues(ExtraProperties.USER_ADDRESS);
                useremails = metadata.getValues(ExtraProperties.USER_EMAIL);
                userurls = metadata.getValues(ExtraProperties.USER_URLS);
                userorganizations = metadata.getValues(ExtraProperties.USER_ORGANIZATION);
                userBirth = metadata.getDate(ExtraProperties.USER_BIRTH);

                assertTrue(hts.contains("ratuxo"));
                assertTrue(hts.contains("nicolecity,"));
                assertTrue(hts.contains("Test@paraparaparapara"));
                assertTrue(hts.contains("www.rato.com.br"));
                assertTrue(hts.contains("gosta muito de dançar para-pararato rato rato: esse cara é o rato"));

                assertEquals("R. da Casa do Prof. Elder isso mesmo Bela Vista MS BR ZIP 79260-000", useraddresses[0]);
                assertEquals("www.rato.com.br", userurls[0]);
                assertEquals("insuportavel@test.paraparapara", useremails[0]);
                assertEquals("021 (61)3468-2000", phonenumbers[0]);
                assertEquals("021 (61)3011-7666", phonenumbers[1]);
                assertEquals("[nicolecity, dança]", userorganizations[0]);

                SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd");
                assertEquals(df.parse("19961024"), userBirth);

            }
        }
    }

    @Test
    public void testVCardCompleteWithoutThumbParser() throws IOException, SAXException, TikaException {

        VCardParser parser = new VCardParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        try (InputStream stream = getStream("test-files/test_contactsCompleteWithoutThumb.vcf")) {
            parser.parse(stream, handler, metadata, context);

            String hts = handler.toString();
            String[] phonenumbers;
            String[] useraddresses;
            String[] useremails;
            String[] userurls;
            String[] userorganizations;
            String[] usernotes;
            String[] usernames;
            phonenumbers = metadata.getValues(ExtraProperties.USER_PHONE);
            useraddresses = metadata.getValues(ExtraProperties.USER_ADDRESS);
            useremails = metadata.getValues(ExtraProperties.USER_EMAIL);
            userurls = metadata.getValues(ExtraProperties.USER_URLS);
            userorganizations = metadata.getValues(ExtraProperties.USER_ORGANIZATION);
            usernotes = metadata.getValues(ExtraProperties.USER_NOTES);
            usernames = metadata.getValues(ExtraProperties.USER_NAME);

            assertTrue(hts.contains("Magnanimo Test@paraparaparapara el raton para el"));
            assertTrue(hts.contains("ratuxo"));
            assertTrue(hts.contains("myContacts"));
            assertTrue(hts.contains("R. da Casa do Prof. Elder"));
            assertTrue(hts.contains("insuportavel@test.paraparapara"));
            assertTrue(hts.contains("Magnanimo"));
            assertTrue(hts.contains("nicolecity"));
            assertTrue(hts.contains("CEO do para para"));
            assertTrue(hts.contains("gosta muito de dançar para-pararato rato rato: esse cara é o rato"));

            assertEquals("R. da Casa do Prof. Elder isso mesmo Bela Vista MS BR ZIP 79260-000", useraddresses[0]);
            assertEquals("www.rato.com.br", userurls[0]);
            assertEquals("insuportavel@test.paraparapara", useremails[0]);
            assertEquals("99 99998-6219", phonenumbers[0]);
            assertEquals("[nicolecity, dança]", userorganizations[0]);
            assertEquals("gosta muito de dançar para-para", usernotes[0].substring(0, 31));
            assertEquals("Magnanimo Test@paraparaparapara el raton para el", usernames[0]);

        }

    }

    @Test
    public void testVCardHtmlFromString() throws IOException, SAXException, TikaException, URISyntaxException {
        StringWriter out = new StringWriter();
        PrintWriter writer = new PrintWriter(out);
        URI uri = this.getClass().getResource("/test-files/test_contactsCompleteInfo.vcf").toURI();
        String vcardText = Files.readString(Paths.get(uri).toAbsolutePath());

        VCardParser.printHtmlFromString(writer, vcardText);
        writer.flush();
        String vcardHTML = out.toString();

        assertTrue(vcardHTML.contains("Taxi Lago Norte"));
        assertTrue(vcardHTML.contains("Sushi Loko Flamingo"));
        assertTrue(vcardHTML.contains("(61)3468-2000"));
        assertTrue(vcardHTML.contains("myContacts"));
        assertTrue(vcardHTML.contains("Smart Assistência Técnica"));
        assertTrue(vcardHTML.contains("Magnanimo Test@paraparaparapara el raton para el"));
        assertTrue(vcardHTML.contains("casado"));

    }

}
