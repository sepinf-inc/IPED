package gpinf.dev.filetypes;

import gpinf.dev.data.EvidenceFile;

import java.io.File;
import java.util.List;

public class OriginalEmailFileType extends EvidenceFileType {

  /**
   *
   */
  private static final long serialVersionUID = 1L;

  @Override
  public String getLongDescr() {
    return "E-mail de Origem";
  }

  @Override
  public void processFiles(File baseDir, List<EvidenceFile> evidenceFiles) {
    // TODO Auto-generated method stub

  }

}
