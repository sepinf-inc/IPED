package iped.app.home.newcase.model;

/*
 * @created 27/09/2022
 * @project IPED
 * @author Thiago S. Figueiredo
 */

import java.time.LocalDate;
import java.util.ArrayList;

public class CaseInfo {

    private String caseNumber;
    private String caseName;
    private String investigatedNames;
    private String requestDate;
    private String demandant;
    private String organization;
    private String examinerNames;
    private String contact;
    private String caseNotes;

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

    public String getInvestigatedNames() {
        return investigatedNames;
    }

    public void setInvestigatedNames(String investigatedNames) {
        this.investigatedNames = investigatedNames;
    }

    public String getRequestDate() {
        return requestDate;
    }

    public void setRequestDate(String requestDate) {
        this.requestDate = requestDate;
    }

    public String getDemandant() {
        return demandant;
    }

    public void setDemandant(String demandant) {
        this.demandant = demandant;
    }

    public String getOrganization() {
        return organization;
    }

    public void setOrganization(String organization) {
        this.organization = organization;
    }

    public String getExaminerNames() {
        return examinerNames;
    }

    public void setExaminerNames(String examinerNames) {
        this.examinerNames = examinerNames;
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
}
