package iped.parsers.bittorrent;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped.parsers.util.IgnoreCorruptedCarved;
import iped.parsers.util.Messages;
import iped.parsers.util.Util;
import iped.utils.TimeConverter;

public class BitTorrentGenericDatParser extends AbstractParser {
    private static final long serialVersionUID = -5053923300569187504L;
    private static final String SETTINGS_DAT_MIME_TYPE = "application/x-bittorrent-settings-dat";
    private static final Set<MediaType> SUPPORTED_TYPES = Collections
            .singleton(MediaType.parse(SETTINGS_DAT_MIME_TYPE));

    private static final Set<String> secondsKeys = new HashSet<String>();
    private static final Set<String> filetimeKeys = new HashSet<String>();
    static {
        secondsKeys.add("isp.peer_policy_expy");
        secondsKeys.add("lit");
        secondsKeys.add("next_market_share_report");
        secondsKeys.add("offers.cookiepage_last_showtime");
        secondsKeys.add("offers.graphicscard_check_timestamp");
        secondsKeys.add("settings_saved_systime");

        filetimeKeys.add("bin_change");
        filetimeKeys.add("born_on");
    }

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        metadata.set(HttpHeaders.CONTENT_TYPE, SETTINGS_DAT_MIME_TYPE);
        metadata.remove(TikaCoreProperties.RESOURCE_NAME_KEY);

        DateFormat df = new SimpleDateFormat(Messages.getString("BitTorrentResumeDatParser.DateFormat"));
        df.setTimeZone(TimeZone.getTimeZone("GMT+0"));

        BencodedDict dict = new BencodedDict(stream, df, (context.get(IgnoreCorruptedCarved.class) == null));

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();
        xhtml.newline();
        try {
            xhtml.startElement("pre");
            xhtml.characters(toFormattedString(dict.getDict(), df));
            xhtml.endElement("pre");
        } finally {
            xhtml.endDocument();
        }
    }

    private static String toFormattedString(Map<String, Object> dict, DateFormat df) {
        StringBuilder sb = new StringBuilder();
        format(sb, dict, df, 0);
        return sb.toString();
    }

    private static void format(StringBuilder sb, Map<String, Object> dict, DateFormat df, int level) {
        if (dict.isEmpty()) {
            sb.append("{}");
            return;
        }
        char[] aux = new char[level * 4];
        Arrays.fill(aux, ' ');
        String start = new String(aux);
        if (level > 0) {
            sb.append("{\n");
        }
        for (String key : dict.keySet()) {
            if (key.equals(".fileguard")) {
                continue;
            }
            sb.append(start).append(key).append(" = ");
            Object val = dict.get(key);
            format(sb, key, val, df, level);
            sb.append("\n");
        }
        if (level > 0) {
            sb.append(start.substring(4)).append("}");
        }
    }

    @SuppressWarnings("unchecked")
    private static void format(StringBuilder sb, String key, Object val, DateFormat df, int level) {
        if (val == null) {
            sb.append("<null>");
        } else {
            if (val instanceof Long) {
                long v = (Long) val;
                if (secondsKeys.contains(key)) {
                    sb.append(df.format(v * 1000));
                } else if (filetimeKeys.contains(key)) {
                    sb.append(df.format(TimeConverter.filetimeToMillis(v * 10000000)));
                } else {
                    sb.append(v);
                }
            } else if (val instanceof Map) {
                format(sb, (Map<String, Object>) val, df, level + 1);
            } else if (val instanceof ByteBuffer) {
                byte[] bytes = ((ByteBuffer) val).array();
                int cnt0 = 0;
                int cnt1 = 0;
                for (int i = 0; i < bytes.length; i++) {
                    byte b = bytes[i];
                    if (b < 32 || b > 127) {
                        if ((i & 1) == 0) {
                            cnt0++;
                        } else {
                            cnt1++;
                        }
                    }
                }
                if (cnt0 > bytes.length / 4 || cnt1 > bytes.length / 4) {
                    for (int i = 0; i < bytes.length; i++) {
                        if (i > 0) {
                            sb.append(' ');
                        }
                        sb.append(String.format("%02X", bytes[i] & 0xFF));
                    }
                } else {
                    try {
                        String s = Util.decodeUnknownCharset(bytes);
                        sb.append(s);
                    } catch (Exception e) {
                    }
                }
            } else if (val instanceof List) {
                List<Object> l = (List<Object>) val;
                sb.append('[');
                for (int i = 0; i < l.size(); i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    format(sb, key, l.get(i), df, level);
                }
                sb.append(']');
            } else {
                sb.append(val.getClass() + " : " + val.toString());
            }
        }
    }
}
