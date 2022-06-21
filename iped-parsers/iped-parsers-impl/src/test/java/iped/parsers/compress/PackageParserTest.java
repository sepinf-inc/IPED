package iped.parsers.compress;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped.parsers.util.AbstractPkgTest;

public class PackageParserTest extends AbstractPkgTest {

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    }


    @Test
    public void testPackageParserZipParsing() throws IOException, SAXException, TikaException {

        PackageParser parser = new PackageParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        parser.getSupportedTypes(recursingContext);
        try (InputStream stream = getStream("test-files/test_mockZip.zip")) {
            parser.parse(stream, handler, metadata, recursingContext);
            String hts = handler.toString();

            assertTrue(hts.contains("mockdoc1.docx\n" + "· Lorem ipsum dolor sit amet, consectetur adipiscing elit."));
            assertTrue(hts.contains("mockdoc2.docx\n"
                    + "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Aenean nunc augue, tincidunt eget mi a, euismod ultricies augue."));
            assertTrue(hts.contains("mocksheets1.xlsx\nPlan1"));
            assertTrue(hts.contains("mocksheets2.xlsx\nPlan1"));

        }
    }

    @Test
    public void testPackageParserZipEmbedded() throws IOException, SAXException, TikaException, ParseException {

        PackageParser parser = new PackageParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        parser.getSupportedTypes(trackingContext);
        try (InputStream stream = getStream("test-files/test_mockZip.zip")) {
            parser.parse(stream, handler, metadata, trackingContext);


            DateFormat df = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");


            assertEquals(10, tracker.filenames.size());
            assertEquals(10, tracker.modifieddate.size());
            assertEquals(10, tracker.itensmd5.size());

            assertEquals("mockdoc1.docx", tracker.filenames.get(0));
            Date date = parseFromDefaultDateFormat(tracker.modifieddate.get(0));
            assertEquals(df.parse("08/04/2021 14:03:32"), date);
            assertEquals("DF8000908EE52C996D5D151AC8DB730C", tracker.itensmd5.get(0));
            assertEquals("false", tracker.isfolder.get(0));

            assertEquals("mockdoc2.docx", tracker.filenames.get(1));
            date = parseFromDefaultDateFormat(tracker.modifieddate.get(1));
            assertEquals(df.parse("08/04/2021 14:03:54"), date);
            assertEquals("9BB76549BF676C633BE94B9608FA5DAE", tracker.itensmd5.get(1));
            assertEquals("false", tracker.isfolder.get(1));

            assertEquals("mocksheets1.xlsx", tracker.filenames.get(2));
            date = parseFromDefaultDateFormat(tracker.modifieddate.get(2));
            assertEquals(df.parse("08/04/2021 14:06:14"), date);
            assertEquals("5C1ADD743E6E1389EC8CB1AF78E79202", tracker.itensmd5.get(2));
            assertEquals("false", tracker.isfolder.get(2));

            assertEquals("mocksheets2.xlsx", tracker.filenames.get(3));
            date = parseFromDefaultDateFormat(tracker.modifieddate.get(3));
            assertEquals(df.parse("08/04/2021 14:07:16"), date);
            assertEquals("4375CBF2CF4920CFE0AAA23C4B1A1744", tracker.itensmd5.get(3));
            assertEquals("false", tracker.isfolder.get(3));

            assertEquals("mocktext1.txt", tracker.filenames.get(4));
            date = parseFromDefaultDateFormat(tracker.modifieddate.get(4));
            assertEquals(df.parse("08/04/2021 14:02:00"), date);
            assertEquals("761D9CBE8BE4BDB94A8962074053CA85", tracker.itensmd5.get(4));
            assertEquals("false", tracker.isfolder.get(4));

            assertEquals("mocktext2.txt", tracker.filenames.get(5));
            date = parseFromDefaultDateFormat(tracker.modifieddate.get(5));
            assertEquals(df.parse("08/04/2021 14:02:14"), date);
            assertEquals("17F87B5AF0D45F02FFCAF36093BD1ED1", tracker.itensmd5.get(5));
            assertEquals("false", tracker.isfolder.get(5));

            assertEquals("mockfolder", tracker.filenames.get(6));
            date = parseFromDefaultDateFormat(tracker.modifieddate.get(6));
            assertEquals(df.parse("09/04/2021 10:19:20"), date);
            assertEquals("D41D8CD98F00B204E9800998ECF8427E", tracker.itensmd5.get(6));
            assertEquals("true", tracker.isfolder.get(6));

            assertEquals("mockfolder/mockdoc5.docx", tracker.filenames.get(7));
            date = parseFromDefaultDateFormat(tracker.modifieddate.get(7));
            assertEquals(df.parse("09/04/2021 09:26:28"), date);
            assertEquals("6E2B84163C6352908AC4B612B0FDDF1E", tracker.itensmd5.get(7));
            assertEquals("false", tracker.isfolder.get(7));

            assertEquals("mockfolder/mocksheets5.xlsx", tracker.filenames.get(8));
            date = parseFromDefaultDateFormat(tracker.modifieddate.get(8));
            assertEquals(df.parse("09/04/2021 09:26:16"), date);
            assertEquals("0E3FD8870A4F85F0975DEBD0C8E24ECF", tracker.itensmd5.get(8));
            assertEquals("false", tracker.isfolder.get(8));

            assertEquals("mockfolder/mocktext5.txt", tracker.filenames.get(9));
            date = parseFromDefaultDateFormat(tracker.modifieddate.get(9));
            assertEquals(df.parse("09/04/2021 09:26:00"), date);
            assertEquals("6212E7FBA5A8FE2FFF5EEA267D4009BE", tracker.itensmd5.get(9));
            assertEquals("false", tracker.isfolder.get(9));

        }

    }

    @Test
    public void testPackageParserTarParsing() throws IOException, SAXException, TikaException {

        PackageParser parser = new PackageParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        parser.getSupportedTypes(recursingContext);
        try (InputStream stream = getStream("test-files/test_mockTar.tar")) {
            parser.parse(stream, handler, metadata, recursingContext);

            String hts = handler.toString();
            assertTrue(hts.contains("mockdoc1.docx\nMockdoc1:"));
            assertTrue(hts.contains("mockdoc2.docx\nMockdoc2:"));
            assertTrue(hts.contains("mockfolder\n\nmockfolder/mockdoc5.docx"));
            assertTrue(hts.contains("mockfolder/mocksheets5.xlsx\nPlan1\n"));

        }
    }

    @Test
    public void testPackageParserTarEmbedded() throws IOException, SAXException, TikaException, ParseException {

        PackageParser parser = new PackageParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        parser.getSupportedTypes(trackingContext);
        try (InputStream stream = getStream("test-files/test_mockTar.tar")) {
            parser.parse(stream, handler, metadata, trackingContext);

            DateFormat df = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
            df.setTimeZone(TimeZone.getTimeZone("GMT-3"));

            assertEquals(10, tracker.filenames.size());
            assertEquals(10, tracker.modifieddate.size());
            assertEquals(10, tracker.itensmd5.size());

            assertEquals("mockdoc1.docx", tracker.filenames.get(0));
            Date date = parseFromDefaultDateFormat(tracker.modifieddate.get(0));
            assertEquals(df.parse("09/04/2021 09:25:42"), date);
            assertEquals("A8D50FC70640100E628B73138FB8F9BB", tracker.itensmd5.get(0));
            assertEquals("false", tracker.isfolder.get(0));

            assertEquals("mockdoc2.docx", tracker.filenames.get(1));
            date = parseFromDefaultDateFormat(tracker.modifieddate.get(1));
            assertEquals(df.parse("09/04/2021 09:25:30"), date);
            assertEquals("62032D28BB6587132BA4ABE786636429", tracker.itensmd5.get(1));
            assertEquals("false", tracker.isfolder.get(1));

            assertEquals("mockfolder", tracker.filenames.get(2));
            date = parseFromDefaultDateFormat(tracker.modifieddate.get(2));
            assertEquals(df.parse("09/04/2021 10:19:21"), date);
            assertEquals("D41D8CD98F00B204E9800998ECF8427E", tracker.itensmd5.get(2));
            assertEquals("true", tracker.isfolder.get(2));

            assertEquals("mockfolder/mockdoc5.docx", tracker.filenames.get(3));
            date = parseFromDefaultDateFormat(tracker.modifieddate.get(3));
            assertEquals(df.parse("09/04/2021 09:26:28"), date);
            assertEquals("6E2B84163C6352908AC4B612B0FDDF1E", tracker.itensmd5.get(3));
            assertEquals("false", tracker.isfolder.get(3));

            assertEquals("mockfolder/mocksheets5.xlsx", tracker.filenames.get(4));
            date = parseFromDefaultDateFormat(tracker.modifieddate.get(4));
            assertEquals(df.parse("09/04/2021 09:26:17"), date);
            assertEquals("0E3FD8870A4F85F0975DEBD0C8E24ECF", tracker.itensmd5.get(4));
            assertEquals("false", tracker.isfolder.get(4));

            assertEquals("mockfolder/mocktext5.txt", tracker.filenames.get(5));
            date = parseFromDefaultDateFormat(tracker.modifieddate.get(5));
            assertEquals(df.parse("09/04/2021 09:26:01"), date);
            assertEquals("6212E7FBA5A8FE2FFF5EEA267D4009BE", tracker.itensmd5.get(5));
            assertEquals("false", tracker.isfolder.get(5));

            assertEquals("mocksheets1.xlsx", tracker.filenames.get(6));
            date = parseFromDefaultDateFormat(tracker.modifieddate.get(6));
            assertEquals(df.parse("09/04/2021 09:24:22"), date);
            assertEquals("03C14E0C639186329CAB1B65054E0AA8", tracker.itensmd5.get(6));
            assertEquals("false", tracker.isfolder.get(6));

            assertEquals("mocksheets2.xlsx", tracker.filenames.get(7));
            date = parseFromDefaultDateFormat(tracker.modifieddate.get(7));
            assertEquals(df.parse("09/04/2021 09:24:12"), date);
            assertEquals("0D6FC269C63F593B1F232D20F6E30462", tracker.itensmd5.get(7));
            assertEquals("false", tracker.isfolder.get(7));

            assertEquals("mocktext1.txt", tracker.filenames.get(8));
            date = parseFromDefaultDateFormat(tracker.modifieddate.get(8));
            assertEquals(df.parse("09/04/2021 09:23:02"), date);
            assertEquals("F45BC2E86A3CC307776E851072003559", tracker.itensmd5.get(8));
            assertEquals("false", tracker.isfolder.get(8));

            assertEquals("mocktext2.txt", tracker.filenames.get(9));
            date = parseFromDefaultDateFormat(tracker.modifieddate.get(9));
            assertEquals(df.parse("09/04/2021 09:22:54"), date);
            assertEquals("C922B74878BA73C51904E75A79B3DF5B", tracker.itensmd5.get(9));
            assertEquals("false", tracker.isfolder.get(9));

        }

    }

    @Test
    public void testPackageParser7zParsing() throws IOException, SAXException, TikaException {

        PackageParser parser = new PackageParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        parser.getSupportedTypes(recursingContext);
        try (InputStream stream = getStream("test-files/test_mock7z_2.7z")) {
            parser.parse(stream, handler, metadata, recursingContext);

            String hts = handler.toString();
            assertTrue(hts.contains("mockdoc1.docx\nMockdoc1:"));
            assertTrue(hts.contains("mockdoc2.docx\nMockdoc2:"));
            assertTrue(hts.contains("mockfolder/mockdoc5.docx\nMockdoc5/folder"));
            assertTrue(hts.contains("mockfolder/mocksheets5.xlsx\nPlan1\n"));

        }
    }

    @Test
    public void testPackageParser7zEmbedded() throws IOException, SAXException, TikaException, ParseException {

        PackageParser parser = new PackageParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        parser.getSupportedTypes(trackingContext);
        try (InputStream stream = getStream("test-files/test_mock7z_2.7z")) {
            parser.parse(stream, handler, metadata, trackingContext);
            
            DateFormat df = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
            df.setTimeZone(TimeZone.getTimeZone("GMT-3"));

            assertEquals(11, tracker.filenames.size());
            assertEquals(11, tracker.modifieddate.size());
            assertEquals(11, tracker.itensmd5.size());

            assertEquals("mockfolder", tracker.filenames.get(0));
            Date date = parseFromDefaultDateFormat(tracker.modifieddate.get(0));
            assertEquals(df.parse("09/04/2021 10:19:21"), date);
            assertEquals("D41D8CD98F00B204E9800998ECF8427E", tracker.itensmd5.get(0));
            assertEquals("true", tracker.isfolder.get(0));

            assertEquals("mockdoc1.docx", tracker.filenames.get(1));
            date = parseFromDefaultDateFormat(tracker.modifieddate.get(1));
            assertEquals(df.parse("09/04/2021 09:25:42"), date);
            assertEquals("A8D50FC70640100E628B73138FB8F9BB", tracker.itensmd5.get(1));
            assertEquals("false", tracker.isfolder.get(1));

            assertEquals("mockdoc2.docx", tracker.filenames.get(2));
            date = parseFromDefaultDateFormat(tracker.modifieddate.get(2));
            assertEquals(df.parse("09/04/2021 09:25:30"), date);
            assertEquals("62032D28BB6587132BA4ABE786636429", tracker.itensmd5.get(2));
            assertEquals("false", tracker.isfolder.get(2));

            assertEquals("mockfolder/mockdoc5.docx", tracker.filenames.get(3));
            date = parseFromDefaultDateFormat(tracker.modifieddate.get(3));
            assertEquals(df.parse("09/04/2021 09:26:28"), date);
            assertEquals("6E2B84163C6352908AC4B612B0FDDF1E", tracker.itensmd5.get(3));
            assertEquals("false", tracker.isfolder.get(3));

            assertEquals("mockfolder/mocksheets5.xlsx", tracker.filenames.get(4));
            date = parseFromDefaultDateFormat(tracker.modifieddate.get(4));
            assertEquals(df.parse("09/04/2021 09:26:17"), date);
            assertEquals("0E3FD8870A4F85F0975DEBD0C8E24ECF", tracker.itensmd5.get(4));
            assertEquals("false", tracker.isfolder.get(4));

            assertEquals("mockfolder/mocktext5.txt", tracker.filenames.get(5));
            date = parseFromDefaultDateFormat(tracker.modifieddate.get(5));
            assertEquals(df.parse("09/04/2021 09:26:01"), date);
            assertEquals("6212E7FBA5A8FE2FFF5EEA267D4009BE", tracker.itensmd5.get(5));
            assertEquals("false", tracker.isfolder.get(5));

            assertEquals("mockrar.zip", tracker.filenames.get(6));
            date = parseFromDefaultDateFormat(tracker.modifieddate.get(6));
            assertEquals(df.parse("08/04/2021 15:13:51"), date);
            assertEquals("0A6931F7799E5A2D28AB5A22E6C84088", tracker.itensmd5.get(6));
            assertEquals("false", tracker.isfolder.get(6));

            assertEquals("mocksheets1.xlsx", tracker.filenames.get(7));
            date = parseFromDefaultDateFormat(tracker.modifieddate.get(7));
            assertEquals(df.parse("09/04/2021 09:24:22"), date);
            assertEquals("03C14E0C639186329CAB1B65054E0AA8", tracker.itensmd5.get(7));
            assertEquals("false", tracker.isfolder.get(7));

            assertEquals("mocksheets2.xlsx", tracker.filenames.get(8));
            date = parseFromDefaultDateFormat(tracker.modifieddate.get(8));
            assertEquals(df.parse("09/04/2021 09:24:12"), date);
            assertEquals("0D6FC269C63F593B1F232D20F6E30462", tracker.itensmd5.get(8));
            assertEquals("false", tracker.isfolder.get(8));

            assertEquals("mocktext1.txt", tracker.filenames.get(9));
            date = parseFromDefaultDateFormat(tracker.modifieddate.get(9));
            assertEquals(df.parse("09/04/2021 09:23:02"), date);
            assertEquals("F45BC2E86A3CC307776E851072003559", tracker.itensmd5.get(9));
            assertEquals("false", tracker.isfolder.get(9));

            assertEquals("mocktext2.txt", tracker.filenames.get(10));
            date = parseFromDefaultDateFormat(tracker.modifieddate.get(10));
            assertEquals(df.parse("09/04/2021 09:22:54"), date);
            assertEquals("C922B74878BA73C51904E75A79B3DF5B", tracker.itensmd5.get(10));
            assertEquals("false", tracker.isfolder.get(10));

        }

    }

    @Test
    public void testPackageParserJarParsing() throws IOException, SAXException, TikaException {

        PackageParser parser = new PackageParser();
        Metadata metadata = spy(new Metadata());
        ContentHandler handler = new BodyContentHandler();
        parser.getSupportedTypes(recursingContext);
        when(metadata.get(Metadata.CONTENT_TYPE)).thenReturn("application/java");
        try (InputStream stream = getStream("test-files/test_jar.jar")) {
            parser.parse(stream, handler, metadata, recursingContext);

            String hts = handler.toString();
            assertTrue(hts.contains("META-INF"));
            assertTrue(hts.contains("Manifest-Version: 1.0"));
            assertTrue(hts.contains("Created-By: 1.8.0_281 (Oracle Corporation)"));
            assertTrue(hts.contains("testJar.class"));
            assertTrue(hts.contains("public void testJar();"));
            assertTrue(hts.contains("static void insereMoeda(char, int[]);"));
            assertTrue(hts.contains("static void trocaAB(int[]);"));

        }

    }

    @Test
    public void testPackageParserJarEmbedded() throws IOException, SAXException, TikaException, ParseException {

        PackageParser parser = new PackageParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        parser.getSupportedTypes(trackingContext);
        try (InputStream stream = getStream("test-files/test_jar.jar")) {
            parser.parse(stream, handler, metadata, trackingContext);

            DateFormat df = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

            assertEquals(3, tracker.filenames.size());
            assertEquals(3, tracker.modifieddate.size());
            assertEquals(3, tracker.itensmd5.size());

            assertEquals("META-INF", tracker.filenames.get(0)); 
            Date date = parseFromDefaultDateFormat(tracker.modifieddate.get(0));
            assertEquals(df.parse("30/04/2021 15:26:20"), date);
            assertEquals("D41D8CD98F00B204E9800998ECF8427E", tracker.itensmd5.get(0));
            assertEquals("true", tracker.isfolder.get(0));

            assertEquals("META-INF/MANIFEST.MF", tracker.filenames.get(1));
            date = parseFromDefaultDateFormat(tracker.modifieddate.get(1));
            assertEquals(df.parse("30/04/2021 15:26:20"), date);
            assertEquals("F32E26890EACA8D3D39153D2E2B3EC2F", tracker.itensmd5.get(1));
            assertEquals("false", tracker.isfolder.get(1));

            assertEquals("testJar.class", tracker.filenames.get(2));
            date = parseFromDefaultDateFormat(tracker.modifieddate.get(2));
            assertEquals(df.parse("30/04/2021 15:24:42"), date);
            assertEquals("B402684EEDD7BAA0E6D61CCF51498BE4", tracker.itensmd5.get(2));
            assertEquals("false", tracker.isfolder.get(2));

        }

    }

    @Test
    public void testPackageParserArjParsing() throws IOException, SAXException, TikaException {

        PackageParser parser = new PackageParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        parser.getSupportedTypes(recursingContext);
        try (InputStream stream = getStream("test-files/test_mockArc.arj")) {
            parser.parse(stream, handler, metadata, recursingContext);

            String hts = handler.toString();
            assertTrue(hts.contains("mockdoc1.docx"));
            assertTrue(hts.contains("mockdoc2.docx"));
            assertTrue(hts.contains("mocksheets1.xlsx"));
            assertTrue(hts.contains("mocksheets2.xlsx"));
            assertTrue(hts.contains("mocktext1.txt"));
            assertTrue(hts.contains("mocktext2.txt"));

        }

    }

    @Test
    public void testPackageParserArjEmbedded() throws IOException, SAXException, TikaException, ParseException {

        PackageParser parser = new PackageParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        parser.getSupportedTypes(trackingContext);
        try (InputStream stream = getStream("test-files/test_mockArc.arj")) {
            parser.parse(stream, handler, metadata, trackingContext);
            assertEquals(6, tracker.filenames.size());
            assertEquals(6, tracker.modifieddate.size());
            assertEquals(6, tracker.itensmd5.size());

            DateFormat df = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
            df.setTimeZone(TimeZone.getTimeZone("GMT-3"));

            assertEquals("mockdoc1.docx", tracker.filenames.get(0));
            Date date = parseFromDefaultDateFormat(tracker.modifieddate.get(0));
            assertEquals(df.parse("09/04/2021 09:25:42"), date);
            assertEquals("A8D50FC70640100E628B73138FB8F9BB", tracker.itensmd5.get(0));

            assertEquals("mockdoc2.docx", tracker.filenames.get(1));
            date = parseFromDefaultDateFormat(tracker.modifieddate.get(1));
            assertEquals(df.parse("09/04/2021 09:25:30"), date);
            assertEquals("62032D28BB6587132BA4ABE786636429", tracker.itensmd5.get(1));

            assertEquals("mocksheets1.xlsx", tracker.filenames.get(2));
            date = parseFromDefaultDateFormat(tracker.modifieddate.get(2));
            assertEquals(df.parse("09/04/2021 09:24:22"), date);
            assertEquals("03C14E0C639186329CAB1B65054E0AA8", tracker.itensmd5.get(2));

            assertEquals("mocksheets2.xlsx", tracker.filenames.get(3));
            date = parseFromDefaultDateFormat(tracker.modifieddate.get(3));
            assertEquals(df.parse("09/04/2021 09:24:12"), date);
            assertEquals("0D6FC269C63F593B1F232D20F6E30462", tracker.itensmd5.get(3));

            assertEquals("mocktext1.txt", tracker.filenames.get(4));
            date = parseFromDefaultDateFormat(tracker.modifieddate.get(4));
            assertEquals(df.parse("09/04/2021 09:23:02"), date);
            assertEquals("F45BC2E86A3CC307776E851072003559", tracker.itensmd5.get(4));

            assertEquals("mocktext2.txt", tracker.filenames.get(5));
            date = parseFromDefaultDateFormat(tracker.modifieddate.get(5));
            assertEquals(df.parse("09/04/2021 09:22:54"), date);
            assertEquals("C922B74878BA73C51904E75A79B3DF5B", tracker.itensmd5.get(5));

        }

    }

    @Test
    public void testPackageParserArParsing() throws IOException, SAXException, TikaException {

        PackageParser parser = new PackageParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        parser.getSupportedTypes(recursingContext);
        try (InputStream stream = getStream("test-files/test_mockArc.ar")) {
            parser.parse(stream, handler, metadata, recursingContext);
            String hts = handler.toString();
            assertTrue(hts.contains("mockdoc1.docx"));
            assertTrue(hts.contains("mockdoc2.docx"));
            assertTrue(hts.contains("mocksheets1.xlsx"));
            assertTrue(hts.contains("mocksheets2.xlsx"));
            assertTrue(hts.contains("mocktext1.txt"));
            assertTrue(hts.contains("mocktext2.txt"));

        }

    }

    @Test
    public void testPackageParserArEmbedded() throws IOException, SAXException, TikaException, ParseException {

        PackageParser parser = new PackageParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        parser.getSupportedTypes(trackingContext);
        try (InputStream stream = getStream("test-files/test_mockArc.ar")) {
            parser.parse(stream, handler, metadata, trackingContext);
            assertEquals(6, tracker.filenames.size());
            assertEquals(6, tracker.modifieddate.size());
            assertEquals(6, tracker.itensmd5.size());

            DateFormat df = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
            df.setTimeZone(TimeZone.getTimeZone("GMT-3"));

            assertEquals("mockdoc1.docx", tracker.filenames.get(0));
            Date date = parseFromDefaultDateFormat(tracker.modifieddate.get(0));
            assertEquals(df.parse("09/04/2021 09:25:42"), date);
            assertEquals("A8D50FC70640100E628B73138FB8F9BB", tracker.itensmd5.get(0));

            assertEquals("mockdoc2.docx", tracker.filenames.get(1));
            date = parseFromDefaultDateFormat(tracker.modifieddate.get(1));
            assertEquals(df.parse("09/04/2021 09:25:30"), date);
            assertEquals("62032D28BB6587132BA4ABE786636429", tracker.itensmd5.get(1));

            assertEquals("mocksheets1.xlsx", tracker.filenames.get(2));
            date = parseFromDefaultDateFormat(tracker.modifieddate.get(2));
            assertEquals(df.parse("09/04/2021 09:24:22"), date);
            assertEquals("03C14E0C639186329CAB1B65054E0AA8", tracker.itensmd5.get(2));

            assertEquals("mocksheets2.xlsx", tracker.filenames.get(3));
            date = parseFromDefaultDateFormat(tracker.modifieddate.get(3));
            assertEquals(df.parse("09/04/2021 09:24:12"), date);
            assertEquals("0D6FC269C63F593B1F232D20F6E30462", tracker.itensmd5.get(3));

            assertEquals("mocktext1.txt", tracker.filenames.get(4));
            date = parseFromDefaultDateFormat(tracker.modifieddate.get(4));
            assertEquals(df.parse("09/04/2021 09:23:02"), date);
            assertEquals("F45BC2E86A3CC307776E851072003559", tracker.itensmd5.get(4));

            assertEquals("mocktext2.txt", tracker.filenames.get(5));
            date = parseFromDefaultDateFormat(tracker.modifieddate.get(5));
            assertEquals(df.parse("09/04/2021 09:22:54"), date);
            assertEquals("C922B74878BA73C51904E75A79B3DF5B", tracker.itensmd5.get(5));

        }

    }

    @Test
    public void testPackageParserCpioParsing() throws IOException, SAXException, TikaException {

        PackageParser parser = new PackageParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        parser.getSupportedTypes(recursingContext);
        try (InputStream stream = getStream("test-files/test_cpio.cpio")) {
            parser.parse(stream, handler, metadata, recursingContext);
            String hts = handler.toString();

            assertTrue(hts.contains("mockdoc1.docx"));
            assertTrue(hts.contains("mockdoc2.docx"));
            assertTrue(hts.contains("mocksheets1.xlsx"));
            assertTrue(hts.contains("mocksheets2.xlsx"));
            assertTrue(hts.contains("mocktext1.txt"));
            assertTrue(hts.contains("mocktext2.txt"));

        }

    }

    @Test
    public void testPackageParserCpioEmbedded() throws IOException, SAXException, TikaException, ParseException {

        PackageParser parser = new PackageParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        parser.getSupportedTypes(trackingContext);
        try (InputStream stream = getStream("test-files/test_cpio.cpio")) {
            parser.parse(stream, handler, metadata, trackingContext);
            assertEquals(8, tracker.filenames.size());
            assertEquals(8, tracker.modifieddate.size());
            assertEquals(8, tracker.itensmd5.size());

            DateFormat df = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
            df.setTimeZone(TimeZone.getTimeZone("GMT-3"));

            assertEquals("mockdoc1.docx", tracker.filenames.get(0));
            Date date = parseFromDefaultDateFormat(tracker.modifieddate.get(0));
            assertEquals(df.parse("04/05/2021 14:07:20"), date);
            assertEquals("A8D50FC70640100E628B73138FB8F9BB", tracker.itensmd5.get(0));

            assertEquals("mockdoc2.docx", tracker.filenames.get(1));
            date = parseFromDefaultDateFormat(tracker.modifieddate.get(1));
            assertEquals(df.parse("09/04/2021 09:25:30"), date);
            assertEquals("62032D28BB6587132BA4ABE786636429", tracker.itensmd5.get(1));

            assertEquals("mockfolder", tracker.filenames.get(2));
            date = parseFromDefaultDateFormat(tracker.modifieddate.get(2));
            assertEquals(df.parse("04/05/2021 14:08:07"), date);
            assertEquals("D41D8CD98F00B204E9800998ECF8427E", tracker.itensmd5.get(2));
            assertEquals("true", tracker.isfolder.get(2));

            assertEquals("mocksheets1.xlsx", tracker.filenames.get(3));
            date = parseFromDefaultDateFormat(tracker.modifieddate.get(3));
            assertEquals(df.parse("09/04/2021 09:24:22"), date);
            assertEquals("03C14E0C639186329CAB1B65054E0AA8", tracker.itensmd5.get(3));

            assertEquals("mocksheets2.xlsx", tracker.filenames.get(4));
            date = parseFromDefaultDateFormat(tracker.modifieddate.get(4));
            assertEquals(df.parse("09/04/2021 09:24:12"), date);
            assertEquals("0D6FC269C63F593B1F232D20F6E30462", tracker.itensmd5.get(4));

            assertEquals("mocktext1.txt", tracker.filenames.get(5));
            date = parseFromDefaultDateFormat(tracker.modifieddate.get(5));
            assertEquals(df.parse("09/04/2021 09:23:02"), date);
            assertEquals("F45BC2E86A3CC307776E851072003559", tracker.itensmd5.get(5));

            assertEquals("mocktext2.txt", tracker.filenames.get(6));
            date = parseFromDefaultDateFormat(tracker.modifieddate.get(6));
            assertEquals(df.parse("09/04/2021 09:22:54"), date);
            assertEquals("C922B74878BA73C51904E75A79B3DF5B", tracker.itensmd5.get(6));

            assertEquals("test.cpio", tracker.filenames.get(7));
            date = parseFromDefaultDateFormat(tracker.modifieddate.get(7));
            assertEquals(df.parse("04/05/2021 14:08:20"), date);
            assertEquals("BA61B70D7C45946AC670D39080C1DD42", tracker.itensmd5.get(7));

        }

    }

    @Test
    public void testPackageParserOoxmlParsing() throws IOException, SAXException, TikaException {

        PackageParser parser = new PackageParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        parser.getSupportedTypes(recursingContext);
        try (InputStream stream = getStream("test-files/test_mockDoc1.docx")) {
            parser.parse(stream, handler, metadata, recursingContext);
            String hts = handler.toString();
            assertTrue(hts.contains("Mockdoc1:"));

        }

    }

    @Test
    public void testPackageParserOoxmlEmbedded() throws IOException, SAXException, TikaException, ParseException {

        PackageParser parser = new PackageParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        parser.getSupportedTypes(trackingContext);
        try (InputStream stream = getStream("test-files/test_mockDoc1.docx")) {
            parser.parse(stream, handler, metadata, trackingContext);
            assertEquals(17, tracker.filenames.size());
            assertEquals(12, tracker.modifieddate.size());
            assertEquals(17, tracker.itensmd5.size());
            assertEquals(5, tracker.folderCount);

            DateFormat df = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

            assertEquals("[Content_Types].xml", tracker.filenames.get(0));
            Date date = parseFromDefaultDateFormat(tracker.modifieddate.get(0));
            assertEquals(df.parse("01/01/1980 00:00:00"), date);
            assertEquals("A7CBC749C317FA432BA2DB5A47131123", tracker.itensmd5.get(0));
            assertEquals("false", tracker.isfolder.get(0));

            assertEquals("_rels", tracker.filenames.get(1));
            date = parseFromDefaultDateFormat(tracker.modifieddate.get(1));
            assertEquals(df.parse("01/01/1980 00:00:00"), date);
            assertEquals("D41D8CD98F00B204E9800998ECF8427E", tracker.itensmd5.get(1));
            assertEquals("true", tracker.isfolder.get(1));

        }

    }

}
