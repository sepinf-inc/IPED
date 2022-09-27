package iped.app.home.newcase.model;/*
 * @created 27/09/2022
 * @project IPED
 * @author Thiago S. Figueiredo
 */

import java.util.ArrayList;

public class IPEDProcess {

    private CaseInfo caseInfo;
    private ArrayList<Evidence> evidenceList;

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
}
