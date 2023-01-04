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

    public void saveCaseInfo(CaseInfo caseInfo, File destinationFile){
        try {
            JSONObject caseInfoJson = new JSONObject();
            caseInfoJson.put("caseNumber", caseInfo.getCaseNumber() );
            caseInfoJson.put( "caseName", caseInfo.getCaseName() );
            caseInfoJson.put( "investigatedNames", caseInfo.getInvestigatedNames() );
            caseInfoJson.put( "requestDate", caseInfo.getRequestDate() );
            caseInfoJson.put("requester", caseInfo.getRequester());
            caseInfoJson.put("organizationName", caseInfo.getOrganizationName() );
            caseInfoJson.put( "examiners", caseInfo.getExaminers() );
            caseInfoJson.put("contact", caseInfo.getContact());
            caseInfoJson.put("caseNotes", caseInfo.getCaseNotes());
            caseInfoJson.put("materials", caseInfo.getMaterials());
            FileWriter writer = new FileWriter(destinationFile, StandardCharsets.UTF_8);
            writer.write(caseInfoJson.toString());
            writer.flush();
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void validateCasePath(Path casePath) throws CaseException {
        if( casePath == null)
            throw new CaseException("Invalid case path");
        if( ! Paths.get(casePath.toString(), "IPED-SearchApp.exe").toFile().exists() )
            throw new CaseException("Invalid case path");
        if( ! Paths.get(casePath.toString(), "iped", "data", "processing_finished").toFile().exists() )
            throw new CaseException("The case process is not finished.");
    }

    public void castEvidenceListToMaterialsList(CaseInfo caseInfo, ArrayList<Evidence> evidenceList){
        if( (evidenceList == null || evidenceList.isEmpty()) || (caseInfo == null) )
            return;
        ArrayList<String> materialList = new ArrayList<>();
        for( Evidence currentEvidence : evidenceList ){
            String materialDescription = currentEvidence.getMaterial() != null ? currentEvidence.getMaterial() : currentEvidence.getAlias();
            if(materialDescription == null || materialDescription.isEmpty())
                materialDescription = (currentEvidence.getFileName() == null || currentEvidence.getFileName().isEmpty())? "no information.." : currentEvidence.getFileName();
            materialList.add(materialDescription);
        }
        caseInfo.setMaterials(materialList);
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
    }

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
