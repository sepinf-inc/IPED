package iped.parsers.mail;

import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped.parsers.util.AbstractPkgTest;

public class OutlookDBXParserTest extends AbstractPkgTest {

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    }

    @Test
    public void testOutlookDBXParser() throws IOException, SAXException, TikaException {

        OutlookDBXParser parser = new OutlookDBXParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        parser.getSupportedTypes(trackingContext);

        try (InputStream stream = getStream("test-files/test_OutlookDBX.dbx")) {
            parser.parse(stream, handler, metadata, trackingContext);
            assertEquals(28, tracker.subitemCount);
            assertEquals(28, tracker.itensmd5.size());
            assertEquals(0, tracker.modifieddate.size());
            assertEquals(0, tracker.folderCount);
            assertEquals(0, tracker.filenames.size());

            assertEquals("6852BF85343E903FDF6F83C33F582B2E", tracker.itensmd5.get(0));
            assertEquals("25CC4C447656C7961F69E0018717672F", tracker.itensmd5.get(1));
            assertEquals("584BD9FDEAF57819153C7B9E01AF440B", tracker.itensmd5.get(2));
            assertEquals("C5CE683823FF491D2F944FBC64FE8B18", tracker.itensmd5.get(3));
            assertEquals("0846BD4DF6258CBA0B892946AD1F0DF3", tracker.itensmd5.get(4));
            assertEquals("9C8A8AE7D00ABFAC8BB4BF2E60433A15", tracker.itensmd5.get(5));
            assertEquals("0A3C8FB6FA4F843E220C575938DB1141", tracker.itensmd5.get(6));
            assertEquals("75A0EADE18F1467022D4188D0A6F56AD", tracker.itensmd5.get(7));
            assertEquals("F2135B4C558744DC91F29BFC060ADB64", tracker.itensmd5.get(8));
            assertEquals("7A539664E4D5B130E99E066DBBCEC60E", tracker.itensmd5.get(9));
            assertEquals("BCD8CA051ED61F2797366D76285DE71C", tracker.itensmd5.get(10));
            assertEquals("F19B84D4AC955AA2EB795AF5D774CCFF", tracker.itensmd5.get(11));
            assertEquals("86260A093D50B11C781690CFCE4D7A92", tracker.itensmd5.get(12));
            assertEquals("AA242F8AC1D2EE29E228F45B4AD47A6A", tracker.itensmd5.get(13));
            assertEquals("54F2A1BBE225F029ED0BA1B3105AFB41", tracker.itensmd5.get(14));
            assertEquals("27BF46CDD514E04B398752D0A1E86D3B", tracker.itensmd5.get(15));
            assertEquals("905BA8015060B9AED5E2336F2A70DFF5", tracker.itensmd5.get(16));
            assertEquals("58E67362CDA2A19A0BA14C5082CD10E3", tracker.itensmd5.get(17));
            assertEquals("3844BF0F91E04203BA9A4BAEC9AD1C29", tracker.itensmd5.get(18));
            assertEquals("A122B72898F0E330B1F880050D1797DF", tracker.itensmd5.get(19));
            assertEquals("C6797BCFAAABC9D429626E357AE7EF15", tracker.itensmd5.get(20));
            assertEquals("45986C53AF20AF7899117823DAB09283", tracker.itensmd5.get(21));
            assertEquals("90EC5C5A40D552A7CA5A6BD669903C42", tracker.itensmd5.get(22));
            assertEquals("9EF09431216BA3CD65C688F2ABA89101", tracker.itensmd5.get(23));
            assertEquals("B86F747AA859EBF68A63B037DE117D5B", tracker.itensmd5.get(24));
            assertEquals("DF01055F040B2D1D05586D0110D3AC25", tracker.itensmd5.get(25));
            assertEquals("A77AADBD2365CAF89AF9AF00610BD02F", tracker.itensmd5.get(26));
            assertEquals("58F528FF2A6C7BF215AE07C0AFB8FB53", tracker.itensmd5.get(27));

        }
    }

}
