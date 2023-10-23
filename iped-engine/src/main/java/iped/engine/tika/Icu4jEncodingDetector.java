package iped.engine.tika;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.txt.CharsetDetector;
import org.apache.tika.parser.txt.CharsetMatch;
import org.apache.tika.utils.CharsetUtils;

/**
 * Copy and paste from Tika-2.4.0 with a fix for TIKA-3774.
 * 
 * @author Nassif
 *
 */
public class Icu4jEncodingDetector extends org.apache.tika.parser.txt.Icu4jEncodingDetector {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    static final int markLimit = 12000; // This is a Tika modification; ICU's is 8000

    private List<String> ignoreCharsets = Arrays.asList("IBM420", "IBM424");

    public Charset detect(InputStream input, Metadata metadata) throws IOException {
        if (input == null) {
            return null;
        }

        CharsetDetector detector = new CharsetDetector(markLimit);

        String incomingCharset = metadata.get(Metadata.CONTENT_ENCODING);
        String incomingType = metadata.get(Metadata.CONTENT_TYPE);
        if (incomingCharset == null && incomingType != null) {
            // TIKA-341: Use charset in content-type
            MediaType mt = MediaType.parse(incomingType);
            if (mt != null) {
                incomingCharset = mt.getParameters().get("charset");
            }
        }

        if (incomingCharset != null) {
            String cleaned = CharsetUtils.clean(incomingCharset);
            if (cleaned != null) {
                detector.setDeclaredEncoding(cleaned);
            } else {
                // TODO: log a warning?
            }
        }

        // TIKA-341 without enabling input filtering (stripping of tags)
        // short HTML tests don't work well
        detector.enableInputFilter(true);

        detector.setText(input);

        for (CharsetMatch match : detector.detectAll()) {
            try {
                String n = match.getNormalizedName();
                if (ignoreCharsets.contains(n)) {
                    continue;
                }
                return CharsetUtils.forName(match.getNormalizedName());
            } catch (IllegalArgumentException e) {
                // ignore
            }
        }

        return null;
    }

}
