package gpinf.dev.filetypes;

import gpinf.dev.data.EvidenceFile;

import java.io.File;
import java.util.List;

import dpf.sp.gpinf.indexer.Messages;

public class OriginalEmailFileType extends EvidenceFileType {

  /**
   *
   */
  private static final long serialVersionUID = 1L;

  @Override
  public String getLongDescr() {
    return Messages.getString("OriginalEmailFileType.ParentEmail"); //$NON-NLS-1$
  }

  @Override
  public void processFiles(File baseDir, List<EvidenceFile> evidenceFiles) {
    // TODO Auto-generated method stub

  }

}
