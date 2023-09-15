package iped.app.home.newcase.model;/*
 * @created 27/09/2022
 * @project IPED
 * @author Thiago S. Figueiredo
 */

import iped.engine.data.ReportInfo;

import java.nio.file.Path;
import java.util.ArrayList;

public class IPEDProcess {

    private ReportInfo reportInfo;
    private ArrayList<Evidence> evidenceList;
    private Path caseOutputPath;
    private ArrayList<String> options;
    private ExistentCaseOptions existentCaseOption;
    private String profile;

    public IPEDProcess() {
        //caseInfo = new CaseInfo();
        evidenceList = new ArrayList<>();
    }

    public ReportInfo getReportInfo() {
        return reportInfo;
    }

    public void setReportInfo(ReportInfo reportInfo) {
        this.reportInfo = reportInfo;
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
     * A list to of iped command options
     * @return ArrayList<String> - A list containing the options
     */
    public ArrayList<String> getOptions() {
        if (options == null)
            options = new ArrayList<>();
        return options;
    }

    public void setOptions(ArrayList<String> options) {
        this.options = options;
    }

    public String getProfile() {
        return profile;
    }

    public void setProfile(String profile) {
        this.profile = profile;
    }

    public ExistentCaseOptions getExistentCaseOption() {
        return existentCaseOption;
    }

    public void setExistentCaseOption(ExistentCaseOptions existentCaseOption) {
        this.existentCaseOption = existentCaseOption;
    }
}
