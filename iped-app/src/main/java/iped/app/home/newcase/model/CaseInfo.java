package iped.app.home.newcase.model;

/*
 * @created 27/09/2022
 * @project IPED
 * @author Thiago S. Figueiredo
 */

import java.util.ArrayList;

public class CaseInfo {

    private String caseNumber;
    private String caseName;
    private ArrayList<String> investigatedNames;
    private String requestDate;
    private String requester;
    private String organizationName;
    private ArrayList<String> examiners;
    private String contact;
    private String caseNotes;
    private ArrayList<String> materials;

    public String getCaseNumber() {
        return caseNumber;
    }

    public void setCaseNumber(String caseNumber) {
        this.caseNumber = caseNumber;
    }

    public String getCaseName() {
        return caseName;
    }

    public void setCaseName(String caseName) {
        this.caseName = caseName;
    }

    public ArrayList<String> getInvestigatedNames() {
        return investigatedNames;
    }

    public void setInvestigatedNames(ArrayList<String> investigatedNames) {
        this.investigatedNames = investigatedNames;
    }

    public String getRequestDate() {
        return requestDate;
    }

    public void setRequestDate(String requestDate) {
        this.requestDate = requestDate;
    }

    public String getRequester() {
        return requester;
    }

    public void setRequester(String requester) {
        this.requester = requester;
    }

    public String getOrganizationName() {
        return organizationName;
    }

    public void setOrganizationName(String organizationName) {
        this.organizationName = organizationName;
    }

    public ArrayList<String> getExaminers() {
        return examiners;
    }

    public void setExaminers(ArrayList<String> examiners) {
        this.examiners = examiners;
    }

    public String getContact() {
        return contact;
    }

    public void setContact(String contact) {
        this.contact = contact;
    }

    public String getCaseNotes() {
        return caseNotes;
    }

    public void setCaseNotes(String caseNotes) {
        this.caseNotes = caseNotes;
    }

    public ArrayList<String> getMaterials() {
        return materials;
    }

    public void setMaterials(ArrayList<String> materials) {
        this.materials = materials;
    }
}
