package iped.parsers.emule.data;

import java.util.ArrayList;
import java.util.List;

public class ED2KURLCollection implements ECollection {
    String name;
    ArrayList<ECollectionFile> files = new ArrayList<ECollectionFile>();
    
    public ED2KURLCollection(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public List<ECollectionFile> getFiles() {
        return files;
    }

}
