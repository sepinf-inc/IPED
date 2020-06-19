package br.gov.pf.labld.cases;

import java.util.ArrayList;
import java.util.List;

import dpf.sp.gpinf.indexer.IndexFiles;

public class IpedProcessHelper {

  private IpedCase ipedCase;
  private IpedCase oldCase;
  private String profile;

  public IpedProcessHelper(IpedCase ipedCase, IpedCase oldCase, String profile) {
    super();
    this.ipedCase = ipedCase;
    this.oldCase = oldCase;
    this.profile = profile;
  }

  public void process() {

    String[] args = buildArgs();
    IndexFiles.main(args);

  }

  private String[] buildArgs() {
    List<String> args = new ArrayList<>();

//    Set<String> inputs = null;
//    if (ipedCase.isProcessed()) {
//      args.add("--append");
//      inputs = ipedCase.getUnprocessedInputs(oldCase);
//    } else {
//      inputs = ipedCase.getInputs();
//    }

    args.add("-o");
    args.add(ipedCase.getOutput());

    // for (String input : inputs) {
    args.add("-d");
    args.add(ipedCase.getCaseFile().getAbsolutePath());
    // }

    if (profile != null) {
      args.add("-profile");
      args.add(profile);
    }

    return args.toArray(new String[args.size()]);
  }

}
