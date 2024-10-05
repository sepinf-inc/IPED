package iped.parsers.emule.data;

import java.util.List;

public interface Collection {
    String getName();

    List<CollectionFile> getFiles();
}
