package macee.components;

import macee.collection.CaseItemCollection;

public interface Reporter {

    void addToSection(CaseItemCollection collection, String reportPath, int order);

    void addToSection(CaseItemCollection collection, String reportPath);

    void removeFromSection(CaseItemCollection collection, String reportPath);

    void addSection(String reportPath, int order);

    void addSection(String reportPath);

    void removeSection(String reportPath);
}
