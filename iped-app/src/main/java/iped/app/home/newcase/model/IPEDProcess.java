package iped.app.home.newcase.model;/*
 * @created 27/09/2022
 * @project IPED
 * @author Thiago S. Figueiredo
 */

import java.nio.file.Path;
import java.util.ArrayList;

public class IPEDProcess {

    private CaseInfo caseInfo;
    private ArrayList<Evidence> evidenceList;
    private Path caseOutputPath;
    private ArrayList<String> options;

    public IPEDProcess() {
        caseInfo = new CaseInfo();
        evidenceList = new ArrayList<>();
    }

    public CaseInfo getCaseInfo() {
        return caseInfo;
    }

    public void setCaseInfo(CaseInfo caseInfo) {
        this.caseInfo = caseInfo;
    }

    public ArrayList<Evidence> getEvidenceList() {
        return evidenceList;
    }

    public void setEvidenceList(ArrayList<Evidence> evidenceList) {
        this.evidenceList = evidenceList;
    }

    public Path getCaseOutputPath() {
        return caseOutputPath;
    }

    public void setCaseOutputPath(Path caseOutputPath) {
        this.caseOutputPath = caseOutputPath;
    }

    /**
     * A list to of iped command options like:
     * 1: --append
     * 2: --continue
     * 3: --restart
     * @return ArrayList<String> - A lista containing the options
     */
    public ArrayList<String> getOptions() {
        if (options == null)
            options = new ArrayList<String>();
        return options;
    }

    public void setOptions(ArrayList<String> options) {
        this.options = options;
    }
}
