package macee.descriptor;

import com.google.common.collect.Multimap;
import java.util.Collection;
import java.util.Map;
import macee.collection.CaseItemCollection;
import macee.components.Reporter;

public interface ReportDescriptor extends Reporter {

  void addFragmentToSection(String reportPath, ReportFragment value);

  Multimap<String, ReportFragment> getFragments();

  Collection<ReportFragment> getFragments(String reportPath);

  Map<String, CaseItemCollection> getReportStructure();

  CaseItemCollection getSectionContent(String reportPath);

  Collection<CaseItemCollection> getSectionContents();

    
}
