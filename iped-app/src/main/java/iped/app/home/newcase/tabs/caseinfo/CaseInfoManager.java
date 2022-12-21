package iped.app.home.newcase.tabs.caseinfo;/*
 * @created 06/12/2022
 * @project IPED
 * @author Thiago S. Figueiredo
 */

import com.github.openjson.JSONArray;
import com.github.openjson.JSONObject;
import iped.app.home.newcase.model.CaseInfo;
import iped.app.home.newcase.model.Evidence;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;

public class CaseInfoManager {

    Charset UTF8 = Charset.forName("UTF-8");

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
            FileWriter writer = new FileWriter(destinationFile, UTF8);
            writer.write(caseInfoJson.toString());
            writer.flush();
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
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
        String str = new String(Files.readAllBytes(fileToLoad.toPath()), UTF8);
        CaseInfo caseInfo = new CaseInfo();
        JSONObject json = new JSONObject(str);
        caseInfo.setCaseNumber( json.getString("caseNumber") );
        caseInfo.setCaseName( json.getString("caseName") );
        caseInfo.setInvestigatedNames(new ArrayList<>());
        JSONArray array = json.getJSONArray("investigatedNames");
        for (int i = 0; i < array.length(); i++)
            caseInfo.getInvestigatedNames().add(array.getString(i));
        caseInfo.setRequestDate( json.getString("requestDate") );
        caseInfo.setRequester(json.getString("requester"));
        caseInfo.setOrganizationName(json.getString("organizationName") );
        caseInfo.setExaminers(new ArrayList<>());
        array = json.getJSONArray("examiners");
        for (int i = 0; i < array.length(); i++)
            caseInfo.getExaminers().add(array.getString(i));
        caseInfo.setContact(json.getString("contact"));
        caseInfo.setCaseNotes(json.getString("caseNotes"));
        return caseInfo;
    }

}
