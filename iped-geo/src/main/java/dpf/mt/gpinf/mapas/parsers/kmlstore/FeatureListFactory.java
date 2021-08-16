package dpf.mt.gpinf.mapas.parsers.kmlstore;

import java.io.File;
import java.io.IOException;
import java.util.List;

public interface FeatureListFactory {

    public boolean canParse(String mimeType);

    public List<Object> parseFeatureList(File file) throws IOException;

}