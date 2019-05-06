package macee.descriptor;

public interface DataSourceDescriptor extends Descriptor {

  String getCaseId();

  String[] getEvidenceItems();

  String getPath();

  boolean isEnabled();

  void setCaseId(String caseId);

  void setEvidenceItems(String[] evidenceId);

  void setPath(String path);

}
