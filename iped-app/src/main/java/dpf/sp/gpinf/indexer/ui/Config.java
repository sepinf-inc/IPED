/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dpf.sp.gpinf.indexer.ui;

import java.util.ArrayList;

/**
 *
 * @author blinddog
 */
public class Config {
    private String path_ledDie;
    private String path_kffDb;
    private String path_ledWkffPath;
    private String path_tskJarPath;
    private String numThreads;

    private ArrayList<String> hashes;
    private String[] hash;
    private String indexTemp;
    private Boolean enableKnownMetCarving;
    private Boolean indexFileContents;
    private Boolean enableOCR;
    private Boolean exportFileProps;
    private Boolean processFileSignatures;
    private Boolean enableVideoThumbs;
    private Boolean addUnallocated;
    private Boolean indexTempOnSSD;
    private Boolean ignoreDuplicates;
    private Boolean enableImageThumbs;
    private Boolean enableCarving;
    private Boolean enableFileParsing;
    private Boolean excludeKffIgnorable;
    private Boolean indexUnallocated;
    private Boolean enableKFFCarving;
    private Boolean expandContainers;
    private Boolean outputOnSSD;
    private Boolean indexUnknownFiles;
    private Boolean enableHTMLReport;

    private void setHashTypes() {
        hashes = new ArrayList<>();
        hashes.add("md5");
        hashes.add("sha-1");
        hashes.add("sha-256");
        hashes.add("sha-512");
        hashes.add("edonkey");
    }

    public Config() {
        this.indexTemp = "default";
        String[] hashes = { "md5", "sha-1", "sha-256", "sha-512", "edonkey" };
        setHashTypes();
        this.hash = hashes;
        this.path_ledDie = "E:/LED/V1.21.00/pedo/die/rfdie.dat";
        this.path_kffDb = "E:/kff/kff.db";
        this.path_ledWkffPath = "E:/LED/V1.21.00/pedo/wkff";
        this.path_tskJarPath = "/home/user/tsk-4.3-src-folder/bindings/java/dist/Tsk_DataModel.jar";

        this.indexTempOnSSD = false;
        this.outputOnSSD = false;
        this.numThreads = "default";
        this.excludeKffIgnorable = false;
        this.ignoreDuplicates = false;
        this.exportFileProps = true;
        this.processFileSignatures = true;
        this.enableFileParsing = true;
        this.expandContainers = true;
        this.indexFileContents = true;
        this.indexUnknownFiles = true;
        this.enableOCR = false;
        this.addUnallocated = false;
        this.indexUnallocated = false;
        this.enableCarving = false;
        this.enableKFFCarving = false;
        this.enableKnownMetCarving = false;
        this.enableImageThumbs = true;
        this.enableVideoThumbs = true;
        this.enableHTMLReport = true;
    }

    /**
     * @return the hash
     */
    public ArrayList<String> getHashes() {
        return hashes;
    }

    /**
     * @param hash
     *            the hash to set
     */
    public void setHashes(ArrayList<String> hash) {
        this.hashes = hash;
    }

    /**
     * @return the ledDie
     */
    public String getPathLedDie() {
        return path_ledDie;
    }

    /**
     * @param ledDie
     *            the ledDie to set
     */
    public void setLedDie(String ledDie) {
        this.path_ledDie = ledDie;
    }

    /**
     * @return the kffDb
     */
    public String getPathKffDb() {
        return path_kffDb;
    }

    /**
     * @param kffDb
     *            the kffDb to set
     */
    public void setKffDb(String kffDb) {
        this.path_kffDb = kffDb;
    }

    /**
     * @return the ledWkffPath
     */
    public String getPathLedWkff() {
        return path_ledWkffPath;
    }

    /**
     * @param ledWkffPath
     *            the ledWkffPath to set
     */
    public void setLedWkffPath(String ledWkffPath) {
        this.path_ledWkffPath = ledWkffPath;
    }

    /**
     * @return the indexTemp
     */
    public String getIndexTemp() {
        return indexTemp;
    }

    /**
     * @param indexTemp
     *            the indexTemp to set
     */
    public void setIndexTemp(String indexTemp) {
        this.indexTemp = indexTemp;
    }

    /**
     * @return the enableKnownMetCarving
     */
    public Boolean getEnableKnownMetCarving() {
        return enableKnownMetCarving;
    }

    /**
     * @param enableKnownMetCarving
     *            the enableKnownMetCarving to set
     */
    public void setEnableKnownMetCarving(Boolean enableKnownMetCarving) {
        this.enableKnownMetCarving = enableKnownMetCarving;
    }

    /**
     * @return the indexFileContents
     */
    public Boolean getIndexFileContents() {
        return indexFileContents;
    }

    /**
     * @param indexFileContents
     *            the indexFileContents to set
     */
    public void setIndexFileContents(Boolean indexFileContents) {
        this.indexFileContents = indexFileContents;
    }

    /**
     * @return the enableOCR
     */
    public Boolean getEnableOCR() {
        return enableOCR;
    }

    /**
     * @param enableOCR
     *            the enableOCR to set
     */
    public void setEnableOCR(Boolean enableOCR) {
        this.enableOCR = enableOCR;
    }

    /**
     * @return the exportFileProps
     */
    public Boolean getExportFileProps() {
        return exportFileProps;
    }

    /**
     * @param exportFileProps
     *            the exportFileProps to set
     */
    public void setExportFileProps(Boolean exportFileProps) {
        this.exportFileProps = exportFileProps;
    }

    /**
     * @return the processFileSignatures
     */
    public Boolean getProcessFileSignatures() {
        return processFileSignatures;
    }

    /**
     * @param processFileSignatures
     *            the processFileSignatures to set
     */
    public void setProcessFileSignatures(Boolean processFileSignatures) {
        this.processFileSignatures = processFileSignatures;
    }

    /**
     * @return the enableVideoThumbs
     */
    public Boolean getEnableVideoThumbs() {
        return enableVideoThumbs;
    }

    /**
     * @param enableVideoThumbs
     *            the enableVideoThumbs to set
     */
    public void setEnableVideoThumbs(Boolean enableVideoThumbs) {
        this.enableVideoThumbs = enableVideoThumbs;
    }

    /**
     * @return the addUnallocated
     */
    public Boolean getAddUnallocated() {
        return addUnallocated;
    }

    /**
     * @param addUnallocated
     *            the addUnallocated to set
     */
    public void setAddUnallocated(Boolean addUnallocated) {
        this.addUnallocated = addUnallocated;
    }

    /**
     * @return the indexTempOnSSD
     */
    public Boolean getIndexTempOnSSD() {
        return indexTempOnSSD;
    }

    /**
     * @param indexTempOnSSD
     *            the indexTempOnSSD to set
     */
    public void setIndexTempOnSSD(Boolean indexTempOnSSD) {
        this.indexTempOnSSD = indexTempOnSSD;
    }

    /**
     * @return the ignoreDuplicates
     */
    public Boolean getIgnoreDuplicates() {
        return ignoreDuplicates;
    }

    /**
     * @param ignoreDuplicates
     *            the ignoreDuplicates to set
     */
    public void setIgnoreDuplicates(Boolean ignoreDuplicates) {
        this.ignoreDuplicates = ignoreDuplicates;
    }

    /**
     * @return the enableImageThumbs
     */
    public Boolean getEnableImageThumbs() {
        return enableImageThumbs;
    }

    /**
     * @param enableImageThumbs
     *            the enableImageThumbs to set
     */
    public void setEnableImageThumbs(Boolean enableImageThumbs) {
        this.enableImageThumbs = enableImageThumbs;
    }

    /**
     * @return the enableCarving
     */
    public Boolean getEnableCarving() {
        return enableCarving;
    }

    /**
     * @param enableCarving
     *            the enableCarving to set
     */
    public void setEnableCarving(Boolean enableCarving) {
        this.enableCarving = enableCarving;
    }

    /**
     * @return the enableFileParsing
     */
    public Boolean getEnableFileParsing() {
        return enableFileParsing;
    }

    /**
     * @param enableFileParsing
     *            the enableFileParsing to set
     */
    public void setEnableFileParsing(Boolean enableFileParsing) {
        this.enableFileParsing = enableFileParsing;
    }

    /**
     * @return the excludeKffIgnorable
     */
    public Boolean getExcludeKffIgnorable() {
        return excludeKffIgnorable;
    }

    /**
     * @param excludeKffIgnorable
     *            the excludeKffIgnorable to set
     */
    public void setExcludeKffIgnorable(Boolean excludeKffIgnorable) {
        this.excludeKffIgnorable = excludeKffIgnorable;
    }

    /**
     * @return the indexUnallocated
     */
    public Boolean getIndexUnallocated() {
        return indexUnallocated;
    }

    /**
     * @param indexUnallocated
     *            the indexUnallocated to set
     */
    public void setIndexUnallocated(Boolean indexUnallocated) {
        this.indexUnallocated = indexUnallocated;
    }

    /**
     * @return the enableKFFCarving
     */
    public Boolean getEnableKFFCarving() {
        return enableKFFCarving;
    }

    /**
     * @param enableKFFCarving
     *            the enableKFFCarving to set
     */
    public void setEnableKFFCarving(Boolean enableKFFCarving) {
        this.enableKFFCarving = enableKFFCarving;
    }

    /**
     * @return the expandContainers
     */
    public Boolean getExpandContainers() {
        return expandContainers;
    }

    /**
     * @param expandContainers
     *            the expandContainers to set
     */
    public void setExpandContainers(Boolean expandContainers) {
        this.expandContainers = expandContainers;
    }

    /**
     * @return the outputOnSSD
     */
    public Boolean getOutputOnSSD() {
        return outputOnSSD;
    }

    /**
     * @param outputOnSSD
     *            the outputOnSSD to set
     */
    public void setOutputOnSSD(Boolean outputOnSSD) {
        this.outputOnSSD = outputOnSSD;
    }

    /**
     * @return the indexUnknownFiles
     */
    public Boolean getIndexUnknownFiles() {
        return indexUnknownFiles;
    }

    /**
     * @param indexUnknownFiles
     *            the indexUnknownFiles to set
     */
    public void setIndexUnknownFiles(Boolean indexUnknownFiles) {
        this.indexUnknownFiles = indexUnknownFiles;
    }

    /**
     * @return the enableHTMLReport
     */
    public Boolean getEnableHTMLReport() {
        return enableHTMLReport;
    }

    /**
     * @param enableHTMLReport
     *            the enableHTMLReport to set
     */
    public void setEnableHTMLReport(Boolean enableHTMLReport) {
        this.enableHTMLReport = enableHTMLReport;
    }

    /**
     * @return the hash
     */
    public String[] getHash() {
        return hash;
    }

    /**
     * @param hash
     *            the hash to set
     */
    public void setHash(String[] hash) {
        this.hash = hash;
    }
}
