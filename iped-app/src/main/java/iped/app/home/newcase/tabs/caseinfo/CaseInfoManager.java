package iped.app.home.newcase.tabs.caseinfo;/*
 * @created 06/12/2022
 * @project IPED
 * @author Thiago S. Figueiredo
 */

import com.github.openjson.JSONArray;
import com.github.openjson.JSONException;
import com.github.openjson.JSONObject;
import iped.app.home.newcase.model.CaseInfo;
import iped.app.home.newcase.model.Evidence;
import iped.engine.data.ReportInfo;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

public class CaseInfoManager {

    /*public void saveCaseInfo(ReportInfo reportInfo, File destinationFile){
        try {
            JSONObject reportInfoJson = new JSONObject();
            reportInfoJson.put("reportNumber", reportInfo.reportNumber );
            reportInfoJson.put("reportDate", reportInfo.reportDate);
            reportInfoJson.put( "reportTitle", reportInfo.reportTitle );
            reportInfoJson.put( "examiners", reportInfo.examiners );
            reportInfoJson.put( "investigatedNames", reportInfo.investigatedName );
            reportInfoJson.put("organizationName", reportInfo.organizationName );
            reportInfoJson.put("contact", reportInfo.contact);
            reportInfoJson.put("caseNotes", reportInfo.caseNotes);
            reportInfoJson.put("caseNumber", reportInfo.caseNumber);
            reportInfoJson.put("requestForm", reportInfo.requestForm);
            reportInfoJson.put( "requestDate", reportInfo.requestDate );
            reportInfoJson.put("requester", reportInfo.requester);
            reportInfoJson.put("labCaseNumber", reportInfo.labCaseNumber);
            reportInfoJson.put("labCaseDate", reportInfo.labCaseDate);
            reportInfoJson.put("evidences", reportInfo.evidences);
            FileWriter writer = new FileWriter(destinationFile, StandardCharsets.UTF_8);
            writer.write(reportInfoJson.toString());
            writer.flush();
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }*/

    public void validateCasePath(Path casePath) throws CaseException {
        if( casePath == null)
            throw new CaseException("Invalid case path");
        if( ! Paths.get(casePath.toString(), "IPED-SearchApp.exe").toFile().exists() )
            throw new CaseException("Invalid case path");
        if( ! Paths.get(casePath.toString(), "iped", "data", "processing_finished").toFile().exists() )
            throw new CaseException("The case process is not finished.");
    }

    public void castEvidenceListToMaterialsList(ReportInfo reportInfo, ArrayList<Evidence> evidenceList){
        if( (evidenceList == null || evidenceList.isEmpty()) || (reportInfo == null) )
            return;
        ArrayList<String> materialList = new ArrayList<>();
        for( Evidence currentEvidence : evidenceList ){
            String materialDescription = currentEvidence.getMaterial() != null ? currentEvidence.getMaterial() : currentEvidence.getAlias();
            if(materialDescription == null || materialDescription.isEmpty())
                materialDescription = (currentEvidence.getFileName() == null || currentEvidence.getFileName().isEmpty())? "no information.." : currentEvidence.getFileName();
            materialList.add(materialDescription);
        }
        reportInfo.evidences = 
        //reportInfo.setMaterials(materialList);
    }

   /* public CaseInfo readCaseInfoFile(File fileToLoad){
        ReportInfo ri = new ReportInfo();
        CaseInfo caseInfo = new CaseInfo();
        try {
            ri.readJsonInfoFile(fileToLoad);
            caseInfo.setCaseNumber( ri.caseNumber );
            caseInfo.setCaseName( ri.reportTitle );
            caseInfo.setInvestigatedNames( new ArrayList<>(ri.investigatedName));
            caseInfo.setRequestDate( ri.requestDate );
            caseInfo.setRequester(ri.requester);
            caseInfo.setOrganizationName( ri.organizationName );
            caseInfo.setExaminers(new ArrayList<>( ri.examiners ));
            caseInfo.setContact(ri.contact);
            caseInfo.setCaseNotes(ri.caseNotes);
            caseInfo.setMaterials(new ArrayList<>());
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            return caseInfo;
        }
    }

    public CaseInfo loadCaseInfo(File fileToLoad) throws IOException {
        if(fileToLoad == null)
            return null;
        String str = Files.readString(fileToLoad.toPath(), StandardCharsets.UTF_8);
        CaseInfo caseInfo = new CaseInfo();
        JSONObject json = new JSONObject(str);
        caseInfo.setCaseNumber( json.getString("caseNumber") );
        caseInfo.setCaseName( json.getString("caseName") );
        caseInfo.setInvestigatedNames(new ArrayList<>());
        getJsonArray(json,"investigatedNames").forEach(value -> {
            if(! StringUtils.isBlank((String) value) )
                caseInfo.getInvestigatedNames().add((String) value);
        });
        caseInfo.setRequestDate( json.getString("requestDate") );
        caseInfo.setRequester(json.getString("requester"));
        caseInfo.setOrganizationName(json.getString("organizationName") );
        caseInfo.setExaminers(new ArrayList<>());
        getJsonArray(json,"examiners").forEach(value -> {
            if(! StringUtils.isBlank((String) value) )
                caseInfo.getExaminers().add((String) value);
        });
        caseInfo.setContact(json.getString("contact"));
        caseInfo.setCaseNotes(json.getString("caseNotes"));
        caseInfo.setMaterials(new ArrayList<>());
        getJsonArray(json,"materials").forEach(value -> {
            if(! StringUtils.isBlank((String) value) )
                caseInfo.getMaterials().add((String) value);
        });
        return caseInfo;
    }*/

    private JSONArray getJsonArray(JSONObject json, String name ){
        JSONArray array = new JSONArray();
        try{
            array = json.getJSONArray(name);
        }catch(JSONException e){
            return array;
        }
        return array;
    }

}
