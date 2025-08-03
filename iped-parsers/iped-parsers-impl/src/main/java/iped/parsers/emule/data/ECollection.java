package iped.parsers.emule.data;

import java.util.List;

public interface ECollection {
    String getName();

    List<ECollectionFile> getFiles();
}
