package iped.parsers.emule.data;

import java.util.ArrayList;
import java.util.List;

public class ED2KURLCollection implements Collection {
    String name;
    ArrayList<CollectionFile> files = new ArrayList<CollectionFile>();
    
    public ED2KURLCollection(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public List<CollectionFile> getFiles() {
        return files;
    }

}
