package dpf.mt.gpinf.mapas.parsers.kmlstore;

import java.io.File;
import java.io.IOError;
import java.io.IOException;
import java.util.List;

public class KMLFeatureListFactory implements FeatureListFactory {

    @Override
    public List<Object> parseFeatureList(File file) throws IOException {
        try {
            return KMLParser.parse(file);
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    @Override
    public boolean canParse(String mimeType) {
        return mimeType.equals("application/vnd.google-earth.kml+xml");
    }

}
