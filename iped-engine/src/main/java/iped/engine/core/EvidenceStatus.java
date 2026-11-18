package iped.engine.core;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import iped.engine.CmdLineArgs;

public class EvidenceStatus {

    private static String OLD_STATUS_FILE = "iped/data/processing_finished";
    private static String STATUS_FILE = "iped/data/evidences_processing_status";

    private HashMap<String, Boolean> statusMap = new HashMap<>();
    private File caseDir;

    @SuppressWarnings("unchecked")
    public EvidenceStatus(File caseDir) {
        this.caseDir = caseDir;
        File file = new File(caseDir, STATUS_FILE);
        if (!file.exists()) {
            return;
        }
        try (ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(Files.newInputStream(file.toPath())))) {
            this.statusMap = (HashMap<String, Boolean>) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public void save() throws IOException {
        File file = new File(caseDir, STATUS_FILE);
        try (ObjectOutputStream oos = new ObjectOutputStream(Files.newOutputStream(file.toPath()))) {
            oos.writeObject(statusMap);
        }
    }

    public List<String> getFailedEvidences() {
        if (!new File(caseDir, STATUS_FILE).exists()) {
            if (new File(caseDir, OLD_STATUS_FILE).exists()) {
                return Collections.emptyList();
            }
            return null;
        }
        ArrayList<String> failed = new ArrayList<>();
        for (Entry<String, Boolean> entry : statusMap.entrySet()) {
            if (!entry.getValue()) {
                failed.add(entry.getKey());
            }
        }
        return failed;
    }

    public void addProcessingEvidences(CmdLineArgs args) {
        for (File evidence : args.getDatasources()) {
            statusMap.putIfAbsent(args.getDataSourceName(evidence), false);
        }
    }

    public boolean removeEvidence(String evidence) {
        return statusMap.remove(evidence) != null;
    }

    public void addSuccessfulEvidences(CmdLineArgs args) {
        for (File evidence : args.getDatasources()) {
            statusMap.put(args.getDataSourceName(evidence), true);
        }
    }

}
