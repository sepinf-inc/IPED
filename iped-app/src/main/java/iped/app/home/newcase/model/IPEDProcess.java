package iped.app.home.newcase.model;/*
                                    * @created 27/09/2022
                                    * @project IPED
                                    * @author Thiago S. Figueiredo
                                    */

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import iped.engine.data.ReportInfo;

public class IPEDProcess {

    private ReportInfo reportInfo;
    private ArrayList<Evidence> evidenceList;
    private Path caseOutputPath;
    private Map<String, String> options;
    private ExistentCaseOptions existentCaseOption;
    private String profile;
    private Path asapFile;

    public IPEDProcess() {
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
     * 
     * @return ArrayList<String> - A list containing the options
     */
    private Map<String, String> getOptions() {
        if (options == null)
            options = new HashMap<String, String>() {
            };
        return options;
    }

    public void setOptions(Map<String, String> options) {
        this.options = options;
    }

    public void addOptionValue(String key, String value) {
        String currentKey = getOptions().get(key);
        if (currentKey == null) {
            getOptions().put(key, value);
        } else {
            getOptions().putIfAbsent(key, value);
        }

    }

    public List getOptionsAsList() {
        ArrayList optionsList = new ArrayList();
        for (Map.Entry<String, String> set : getOptions().entrySet()) {
            optionsList.add(set.getKey());
            optionsList.add(set.getValue());
        }
        return optionsList;
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

    public Path getAsapFile() {
        return asapFile;
    }

    public void setAsapFile(Path asapFile) {
        this.asapFile = asapFile;
    }
}
