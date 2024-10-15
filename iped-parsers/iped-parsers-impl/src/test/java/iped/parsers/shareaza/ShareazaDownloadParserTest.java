package iped.parsers.shareaza;

import java.io.IOException;
import java.io.InputStream;

import iped.parsers.image.TiffPageParserTest;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.ToTextContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

public class ShareazaDownloadParserTest extends TiffPageParserTest {
    @Test
    public void testShareazaDownloadParser() throws IOException, SAXException, TikaException {

        String file = "test-files/test_shareazaDownload.sd";
        ParseContext context = getContext(file);
        ShareazaDownloadParser parser = new ShareazaDownloadParser();
        ContentHandler handler = new ToTextContentHandler();
        Metadata metadata = new Metadata();

        try (InputStream stream = getStream(file)) {
            metadata.set(Metadata.CONTENT_TYPE, "application/x-shareaza-download");
            parser.parse(stream, handler, metadata, context);

            String hts = handler.toString();

            assertTrue(hts.contains("Magic:                   SDL"));
            assertTrue(hts.contains("Version:                 42"));
            assertTrue(hts.contains("File Name:               Komodor - Electrize.mp3"));
            assertTrue(hts.contains("Search Terms:            <none present>"));
            assertTrue(hts.contains("SHA1:                    6FC0E7F66C3B8059B2C3B485710FA4B09BAA4F86"));
            assertTrue(hts.contains("TIGER:                   CF9F77A8B2DB58844EE3CB6E3C68D832A333E22980D11EAC"));
            assertTrue(hts.contains("MD5:                     2A092D1BC6EC61272B2AF858B67FEAA0"));
            assertTrue(hts.contains("EDONKEY:                 46A856AC472CC0B238258AFCB9612A83"));
            assertTrue(hts.contains("Expanded:                FALSE"));
            assertTrue(hts.contains("Paused:                  FALSE"));
            assertTrue(hts.contains("Boosted:                 FALSE"));
            assertTrue(hts.contains("Shared:                  TRUE"));
            assertTrue(hts.contains("Serial ID:               F599F476"));
            assertTrue(hts.contains("File: "));
            assertTrue(hts.contains("    Total Size:          5613696"));
            assertTrue(hts.contains("    Total Remaining:     5613696"));
            assertTrue(hts.contains("    Total Downloaded:    0"));
            assertTrue(hts.contains("    Number of Fragments: 1"));
            assertTrue(hts.contains("        Fragment 1"));
            assertTrue(hts.contains("            Range Begin: 0"));
            assertTrue(hts.contains("            Range End:   5613696"));
            assertTrue(hts.contains("    Number of Files:     1"));
            assertTrue(hts.contains("        File 1"));
            assertTrue(hts.contains("            File Path:   C:\\Program Files\\Shareaza\\Incomplete\\ttr_Z6PXPKFS3NMIITXDZNXDY2GYGKRTHYRJQDIR5LA.partial"));
            assertTrue(hts.contains("            Offset:      0"));
            assertTrue(hts.contains("            Size:        5613696"));
            assertTrue(hts.contains("            Write:       1"));
            assertTrue(hts.contains("            File Name:   Komodor - Electrize.mp3"));
            assertTrue(hts.contains("            Priority:    2"));
            assertTrue(hts.contains("Number of Previews:      0"));
            assertTrue(hts.contains("Number of Reviews:       0"));
            assertTrue(hts.contains("XML File: "));
            assertTrue(hts.contains("    <audios xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"  xsi:nonamespaceschemalocation=\"http://www.limewire.com/schemas/audio.xsd\" >"));
            assertTrue(hts.contains("        <audio title=\"Komodor - electrize\"  soundtype=\"Joint Stereo\"  samplerate=\"44100\"  channels=\"2\"  track=\"5\"  seconds=\"233\"  artist=\"Artist\"  description=\"MusicMatch_TrackArtist\"  album=\"Planeta DJ 2006\"  bitrate=\"192\"  genre=\"Genre\" >"));
            assertTrue(hts.contains("        </audio>"));
            assertTrue(hts.contains("    </audios>"));
            assertTrue(hts.contains("Torrent Version:         11"));
            assertTrue(hts.contains("    Valid:               0"));
            assertTrue(hts.contains("Sources Count:           8"));
            assertTrue(hts.contains("     Source 1: "));
            assertTrue(hts.contains("          Source Address:                 http://189.60.225.131:6346/uri-res/N2R?urn:sha1:N7AOP5TMHOAFTMWDWSCXCD5EWCN2UT4G"));
            assertTrue(hts.contains("          Protocol ID:                    PROTOCOL_HTTP"));
            assertTrue(hts.contains("          GUID Valid:                     1"));
            assertTrue(hts.contains("          GUID:                           7E16C9361D6FDC44A19FF0210551A78C"));
            assertTrue(hts.contains("          Port:                           6346"));
            assertTrue(hts.contains("          IP Adress:                      189.60.225.131"));
            assertTrue(hts.contains("          Server Port:                    0"));
            assertTrue(hts.contains("          Server Name:                    04 - Artist - Komodor - electrize.mp3"));
            assertTrue(hts.contains("          Index:                          0"));
            assertTrue(hts.contains("          Hash Auth:                      FALSE"));
            assertTrue(hts.contains("          Use SHA1:                       TRUE"));
            assertTrue(hts.contains("          Use TIGER:                      TRUE"));
            assertTrue(hts.contains("          Use EDONKEY:                    TRUE"));
            assertTrue(hts.contains("          Use BTH:                        FALSE"));
            assertTrue(hts.contains("          Use MD5:                        TRUE"));
            assertTrue(hts.contains("          Server Type:                    Shareaza"));
            assertTrue(hts.contains("          Nick Name:                      Randraf"));
            assertTrue(hts.contains("          Country Code:                   BR"));
            assertTrue(hts.contains("          Country Name:                   Brazil"));
            assertTrue(hts.contains("          Speed:                          30336"));
            assertTrue(hts.contains("          Push Only:                      TRUE"));
            assertTrue(hts.contains("          Close Connection:               FALSE"));
            assertTrue(hts.contains("          Read Content                    FALSE"));
            assertTrue(hts.contains("          Last Seen UTC:                  Tue Jul 06 21:55:13 BRT 2010"));
            assertTrue(hts.contains("          Number of past fragments:       0"));
            assertTrue(hts.contains("          Client Extended:                TRUE"));
            assertTrue(hts.contains("          Meta Ignore:                    FALSE"));
            assertTrue(hts.contains("     Source 2: "));
            assertTrue(hts.contains("          Source Address:                 http://189.107.203.209:6346/uri-res/N2R?urn:sha1:N7AOP5TMHOAFTMWDWSCXCD5EWCN2UT4G"));
            assertTrue(hts.contains("          Protocol ID:                    PROTOCOL_HTTP"));
            assertTrue(hts.contains("          GUID Valid:                     1"));
            assertTrue(hts.contains("          GUID:                           67181526D0D66B45A1B5719A5EA4134D"));
            assertTrue(hts.contains("          Port:                           6346"));
            assertTrue(hts.contains("          IP Adress:                      189.107.203.209"));
            assertTrue(hts.contains("          Server Port:                    0"));
            assertTrue(hts.contains("          Server Name:                    <none>"));
            assertTrue(hts.contains("          Index:                          0"));
            assertTrue(hts.contains("          Hash Auth:                      FALSE"));
            assertTrue(hts.contains("          Use SHA1:                       TRUE"));
            assertTrue(hts.contains("          Use TIGER:                      TRUE"));
            assertTrue(hts.contains("          Use EDONKEY:                    TRUE"));
            assertTrue(hts.contains("          Use BTH:                        FALSE"));
            assertTrue(hts.contains("          Use MD5:                        TRUE"));
            assertTrue(hts.contains("          Server Type:                    Shareaza"));
            assertTrue(hts.contains("          Nick Name:                      <none>"));
            assertTrue(hts.contains("          Country Code:                   BR"));
            assertTrue(hts.contains("          Country Name:                   Brazil"));
            assertTrue(hts.contains("          Speed:                          17920"));
            assertTrue(hts.contains("          Push Only:                      TRUE"));
            assertTrue(hts.contains("          Close Connection:               FALSE"));
            assertTrue(hts.contains("          Read Content                    FALSE"));
            assertTrue(hts.contains("          Last Seen UTC:                  Sun Jul 18 00:12:00 BRT 2010"));
            assertTrue(hts.contains("          Number of past fragments:       0"));
            assertTrue(hts.contains("          Client Extended:                TRUE"));
            assertTrue(hts.contains("          Meta Ignore:                    FALSE"));
            assertTrue(hts.contains("     Source 3: "));
            assertTrue(hts.contains("          Source Address:                 ed2kftp://187.7.47.188:10500/46a856ac472cc0b238258afcb9612a83/5613696/"));
            assertTrue(hts.contains("          Protocol ID:                    PROTOCOL_ED2K"));
            assertTrue(hts.contains("          GUID Valid:                     1"));
            assertTrue(hts.contains("          GUID:                           904F2859500E7C848EB05018EC8C6FD9"));
            assertTrue(hts.contains("          Port:                           10500"));
            assertTrue(hts.contains("          IP Adress:                      187.7.47.188"));
            assertTrue(hts.contains("          Server Port:                    0"));
            assertTrue(hts.contains("          Server Name:                    <none>"));
            assertTrue(hts.contains("          Index:                          0"));
            assertTrue(hts.contains("          Hash Auth:                      FALSE"));
            assertTrue(hts.contains("          Use SHA1:                       FALSE"));
            assertTrue(hts.contains("          Use TIGER:                      FALSE"));
            assertTrue(hts.contains("          Use EDONKEY:                    TRUE"));
            assertTrue(hts.contains("          Use BTH:                        FALSE"));
            assertTrue(hts.contains("          Use MD5:                        FALSE"));
            assertTrue(hts.contains("          Server Type:                    Shareaza"));

        }
    }
}
