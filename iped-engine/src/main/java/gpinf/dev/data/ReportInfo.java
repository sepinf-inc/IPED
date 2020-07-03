package gpinf.dev.data;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import com.github.openjson.JSONArray;
import com.github.openjson.JSONObject;

import dpf.sp.gpinf.indexer.Messages;

public class ReportInfo implements Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    
    private static final String EVIDENCE_DELIMITER = "<br>\n";
    private static final String ID_DESC_DELIMITER = ": ";
    private static final String EVIDENCE_PREFIX = Messages.getString("ReportInfo.EvidencePrefix"); //$NON-NLS-1$;
    
    public String reportNumber;
    public String reportDate;
    public String reportHeader;
    public String reportTitle;
    public String caseNumber;
    public String requestForm;
    public String requestDate;
    public String requester;
    public String labCaseNumber;
    public String labCaseDate;
    public List<String> examiners = new ArrayList<>();
    public List<String> examinersID = new ArrayList<>();
    public List<EvidenceDesc> evidences = new ArrayList<>();
    public String finalEvidenceDesc;
    
    class EvidenceDesc implements Serializable{
        
        private static final long serialVersionUID = 1L;

        String id, desc;
    }
    
    /**
     * Lê arquivo com informações do caso (para inclusão em página informativa do
     * relatório).
     */
    public void readAsapInfoFile(File asap) throws IOException {
        BufferedReader in = new BufferedReader(
                new InputStreamReader(new FileInputStream(asap), Charset.forName("cp1252"))); //$NON-NLS-1$
        String str = null;
        String subTit = ""; //$NON-NLS-1$
        String numero = ""; //$NON-NLS-1$
        String unidade = ""; //$NON-NLS-1$
        String matDesc = ""; //$NON-NLS-1$
        String matNum = ""; //$NON-NLS-1$
        examiners.clear();
        examinersID.clear();
        finalEvidenceDesc = null;
        // classe.clear();
        while ((str = in.readLine()) != null) {
            String[] s = str.split("=", 2); //$NON-NLS-1$
            if (s.length < 2) {
                continue;
            }
            String chave = s[0];
            String valor = s[1];
            if (chave.equalsIgnoreCase("Titulo")) { //$NON-NLS-1$
                reportTitle = valor;
            } else if (chave.equalsIgnoreCase("Subtitulo")) { //$NON-NLS-1$
                subTit = valor;
            } else if (chave.equalsIgnoreCase("Unidade")) { //$NON-NLS-1$
                unidade = valor;
            } else if (chave.equalsIgnoreCase("Numero")) { //$NON-NLS-1$
                numero = valor;
            } else if (chave.equalsIgnoreCase("Data")) { //$NON-NLS-1$
                reportDate = valor;
            } else if (chave.toUpperCase().startsWith("PCF")) { //$NON-NLS-1$
                String[] v = valor.split("\\|"); //$NON-NLS-1$
                if (v.length >= 1 && v[0].length() > 0) {
                    examiners.add(v[0]);
                    if (v.length >= 2) {
                        examinersID.add(v[1]);
                    }
                    if (v.length >= 3) {
                        // classe.add(v[2]);
                    }
                }
            } else if (chave.equalsIgnoreCase("MATERIAL_DESCR")) { //$NON-NLS-1$
                matDesc = valor;
            } else if (chave.equalsIgnoreCase("MATERIAL_NUMERO")) { //$NON-NLS-1$
                matNum = valor;
            } else if (chave.equalsIgnoreCase("NUMERO_IPL")) { //$NON-NLS-1$
                caseNumber = valor;
            } else if (chave.equalsIgnoreCase("AUTORIDADE")) { //$NON-NLS-1$
                requester = valor;
            } else if (chave.equalsIgnoreCase("DOCUMENTO")) { //$NON-NLS-1$
                requestForm = valor;
            } else if (chave.equalsIgnoreCase("DATA_DOCUMENTO")) { //$NON-NLS-1$
                requestDate = valor;
            } else if (chave.equalsIgnoreCase("NUMERO_CRIMINALISTICA")) { //$NON-NLS-1$
                labCaseNumber = valor;
            } else if (chave.equalsIgnoreCase("DATA_CRIMINALISTICA")) { //$NON-NLS-1$
                labCaseDate = valor;
            }
        }
        in.close();

        reportTitle += " (" + subTit + ")"; //$NON-NLS-1$ //$NON-NLS-2$
        reportNumber = numero + "-" + unidade; //$NON-NLS-1$

        String[] evidenceDesc = matDesc.split("\\|"); //$NON-NLS-1$
        String[] evidenceNumbers = matNum.split("\\|"); //$NON-NLS-1$
        if (evidenceDesc.length != evidenceNumbers.length) {
            evidenceDesc = new String[] { matDesc };
            evidenceNumbers = new String[] { matNum };
        }
        for(int i = 0; i < evidenceDesc.length; i++) {
            EvidenceDesc e = new EvidenceDesc();
            e.id = evidenceNumbers[i];
            e.desc = evidenceDesc[i];
            evidences.add(e);
        }
    }
    
    public String getEvidenceDescHtml() {
        if(finalEvidenceDesc != null) {
            return finalEvidenceDesc;
        }
        StringBuilder mat = new StringBuilder();
        for (int i = 0; i < evidences.size(); i++) {
            if (i > 0) {
                mat.append(EVIDENCE_DELIMITER); //$NON-NLS-1$
            }
            mat.append(EVIDENCE_PREFIX).append(evidences.get(i).id).append(ID_DESC_DELIMITER).append(evidences.get(i).desc); //$NON-NLS-1$ //$NON-NLS-2$
        }
        return mat.toString();
    }
    
    public String getExaminersText() {
        if(examiners.size() == 1)
            return examiners.get(0);
        String result = "";
        for(String examiner : examiners)
            result += examiner + "; ";
        return result;
    }
    
    public void fillExaminersFromText(String text) {
        examiners.clear();
        String[] es = text.split(";");
        for(String e : es)
            examiners.add(e.trim());
    }
    
    public void fillEvidenceFromText(String text) {
        finalEvidenceDesc = text;
    }
    
    public void readJsonInfoFile(File file) throws IOException {
        finalEvidenceDesc = null;
        String str = new String(Files.readAllBytes(file.toPath()), "UTF-8");
        JSONObject json = new JSONObject(str);
        reportNumber = json.getString("reportNumber");
        reportDate = json.getString("reportDate");
        reportTitle = json.getString("reportTitle");
        JSONArray array = json.getJSONArray("examiners");
        for(int i = 0; i < array.length(); i++)
            examiners.add(array.getString(i));
        caseNumber = json.getString("caseNumber");
        requestForm = json.getString("requestForm");
        requestDate = json.getString("requestDate");
        requester = json.getString("requester");
        labCaseNumber = json.getString("labCaseNumber");
        labCaseDate = json.getString("labCaseDate");
        array = json.getJSONArray("evidences");
        for(int i = 0; i < array.length(); i++) {
            JSONObject evidence = array.getJSONObject(i);
            EvidenceDesc e = new EvidenceDesc();
            e.id = evidence.getString("id");
            e.desc = evidence.getString("desc");
            evidences.add(e);
        }
    }
    
    public File writeReportInfoFile() throws IOException {
        File tmp = File.createTempFile("iped-", ".report");
        FileOutputStream fos = new FileOutputStream(tmp);
        ObjectOutputStream oos = new ObjectOutputStream(fos);
        oos.writeObject(this);
        oos.close();
        return tmp;
    }
    
    public static ReportInfo readReportInfoFile(File file) throws IOException, ClassNotFoundException {
        FileInputStream fis = new FileInputStream(file);
        ObjectInputStream ois = new ObjectInputStream(fis);
        ReportInfo reportInfo = (ReportInfo) ois.readObject();
        ois.close();
        fis.close();
        return reportInfo;
    }
    
}
