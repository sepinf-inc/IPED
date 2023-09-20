package iped.app.home.processmanager;/*
 * @created 29/09/2022
 * @project IPED
 * @author Thiago S. Figueiredo
 */

import iped.app.home.newcase.model.Evidence;
import iped.app.home.newcase.model.IPEDProcess;
import iped.app.home.utils.CasePathManager;
import iped.configuration.IConfigurationDirectory;
import iped.engine.util.Util;
import org.apache.commons.lang3.StringUtils;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

public class ProcessManager {

    private Process process;
    private final ArrayList<ProcessListener> processListener = new ArrayList<>();

    public ArrayList<String> getEvidencesCommandList(ArrayList<Evidence> evidenceList){
        ArrayList<String> commandArgs = new ArrayList<>();
        for(Evidence currentEvidence : evidenceList ){
            commandArgs.add("-d");
            commandArgs.add("\"" + currentEvidence.getPath() + "\"");

            if( (currentEvidence.getAlias() != null) && (! currentEvidence.getAlias().trim().isEmpty()) ) {
                commandArgs.add("-dname");
                commandArgs.add("\"" + currentEvidence.getAlias().trim() + "\"");
            }
            if( (currentEvidence.getTimezone() != null) && (! currentEvidence.getTimezone().trim().isEmpty()) ) {
                commandArgs.add("-tz");
                commandArgs.add(currentEvidence.getTimezone());
            }
            if( (currentEvidence.getPassword() != null) && (! currentEvidence.getPassword().trim().isEmpty()) ){
                commandArgs.add("-p");
                commandArgs.add("\"" + currentEvidence.getPassword() + "\"");
            }
            if( (currentEvidence.getBlocksize() != null) && (currentEvidence.getBlocksize() > 0 ) ){
                commandArgs.add("-b");
                commandArgs.add(currentEvidence.getBlocksize().toString());
            }
        }
        return commandArgs;
    }

    public ArrayList<String> getCaseOutputCommand(Path caseOutput){
        ArrayList<String> commandArgs = new ArrayList<>();
        commandArgs.add("-o");
        commandArgs.add( "\"" + caseOutput.toString() + "\"" );
        return commandArgs;
    }

    public ArrayList<String> getProfileCommand(String profileName){
        ArrayList<String> commandArgs = new ArrayList<>();
        if( StringUtils.isEmpty(profileName) )
            return commandArgs;
        commandArgs.add("-profile");
        commandArgs.add( profileName );
        return commandArgs;
    }

    public ArrayList<String> getIpedSearchAppJarCommand(Path caseOutput){
        ArrayList<String> cmds = new ArrayList<>();
        cmds.add("-jar");
        cmds.add(Paths.get(caseOutput.toString(), "iped", "lib", "iped-search-app.jar").toString() );
        return cmds;
    }

    public ArrayList<String> getIpedJarCommand(){
        ArrayList<String> cmds = new ArrayList<>();
        cmds.add("-jar");
        cmds.add(Paths.get(System.getProperty(IConfigurationDirectory.IPED_APP_ROOT), "iped.jar").toString() );
        return cmds;
    }

    public String getJarBinCommand(){
        String javaBin = "java";
        File embeddedJRE = Paths.get(getRootPath(), "jre").toFile();
        File javaHome = new File(System.getProperty("java.home"));
        if (!javaHome.equals(embeddedJRE)) {
            String warn = Util.getJavaVersionWarn();
            if (warn != null) {
                System.err.println(warn);
            }
        }
        if (org.apache.tika.utils.SystemUtils.IS_OS_WINDOWS) {
            javaBin = Paths.get(javaHome.getPath(), "bin", "java.exe").toString();
        }
        return javaBin;
    }

    private String getRootPath(){
        String rootPath = null;
        try {
            URL url = ProcessManager.class.getProtectionDomain().getCodeSource().getLocation();
            if ("true".equals(System.getProperty("Debugging"))) {
                rootPath = System.getProperty("user.dir");
            } else {
                rootPath = new File(url.toURI()).getParent();
                // test for report generation from case folder
                if (rootPath.endsWith("iped" + File.separator + "lib")) { //$NON-NLS-1$ //$NON-NLS-2$
                    rootPath = new File(url.toURI()).getParentFile().getParent();
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        return rootPath;
    }

    public void openMulticase(Path casePath,  File multiCaseFile){
        ArrayList<String> commandList =  new ArrayList<>();
        commandList.add(getJarBinCommand());
        commandList.addAll(getIpedSearchAppJarCommand(CasePathManager.getInstance().getCasePath().toPath()));
        commandList.add("-multicases");
        commandList.add(multiCaseFile.getPath());
        StringBuffer output = new StringBuffer();
        System.out.println(commandList);
        startIpedSearchAppProcess(commandList, output);
    }

    public void openSingleCase(Path casePath){
        ArrayList<String> commandList =  new ArrayList<>();
        commandList.add(getJarBinCommand());
        commandList.addAll(getIpedSearchAppJarCommand(casePath));
        StringBuffer output = new StringBuffer();
        startIpedSearchAppProcess(commandList, output);
    }

    private void startIpedSearchAppProcess(ArrayList<String> commandList, StringBuffer output){
        try{
            System.out.println("IPED Search command: " + String.join(" ", commandList.toArray(new String[0])));
            process = Runtime.getRuntime().exec(commandList.toArray(new String[0]));
            fireCaseIsOpening();
            readProcessOutput(process, output);
            process.waitFor();
            if (process.exitValue() != 0) {
                throw new Exception(output.toString());
            }
        }catch (Exception e) {
            e.printStackTrace();
        }finally {
            fireCaseWasClosed();
        }
    }

    public void startIpedProcess(IPEDProcess ipedProcess, JTextArea logTextArea) throws IpedStartException {
        try {
            ArrayList<String> commandList =  new ArrayList<>();
            commandList.add(getJarBinCommand());
            commandList.addAll(getIpedJarCommand());
            commandList.addAll(getEvidencesCommandList(ipedProcess.getEvidenceList()) );
            commandList.addAll(getCaseOutputCommand(ipedProcess.getCaseOutputPath()));
            commandList.addAll(getProfileCommand(ipedProcess.getProfile()));
            commandList.addAll(ipedProcess.getOptions());
            if(ipedProcess.getExistentCaseOption() != null)
                commandList.add(ipedProcess.getExistentCaseOption().getCommand());
            System.out.println("IPED command: " + String.join(" ", commandList.toArray(new String[0])));
            process = Runtime.getRuntime().exec(commandList.toArray(new String[0]));
            fireProcessStartListener();
            readProcessOutput(process, logTextArea);
            //wait process finish
            process.waitFor();
            int exitVal = process.exitValue();
            if (exitVal != 0) {
                throw new IpedStartException(logTextArea.getText());
            }
        } catch (final IOException | InterruptedException e) {
            throw new IpedStartException("Exception on Iped start", e);
        }finally {
            fireProcessFinishedListener();
        }
    }

    public void readProcessOutput(Process process, JTextArea logTextArea) throws IOException {
        Runnable routput = new Runnable() {
            @Override
            public void run() {
                try {
                    BufferedReader inputReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    String line;
                    while ((line = inputReader.readLine()) != null) {
                        String finalLine = line;
                        SwingUtilities.invokeLater(new Runnable() {
                            public void run() {
                                logTextArea.append(finalLine + "\n");
                            }
                        });
                    }
                } catch (Exception e) {
                    // TODO: handle exception
                }
            }
        };
        Thread toutput = new Thread(routput);
        toutput.start();
        Runnable rerror = new Runnable() {
            @Override
            public void run() {
                try {
                    String line;
                    BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                    while ((line = errorReader.readLine()) != null) {
                        String finalLine = line;
                        SwingUtilities.invokeLater(new Runnable() {
                            public void run() {
                                logTextArea.append(finalLine + "\n");
                            }
                        });
                    }
                } catch (Exception e) {
                    // TODO: handle exception
                }
            }
        };
        Thread terror = new Thread(rerror);
        terror.start();
    }

    public void readProcessOutput(Process process, StringBuffer outputText) throws IOException {
        BufferedReader inputReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        while ((line = inputReader.readLine()) != null)
            outputText.append(line).append("\n");
        BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
        while ((line = errorReader.readLine()) != null)
            outputText.append(line).append("\n");
    }

    private void fireProcessStartListener(){
        for( ProcessListener listener : processListener )
            listener.processStarted();
    }

    private void fireProcessFinishedListener(){
        for( ProcessListener listener : processListener )
            listener.processFinished();
    }

    public void fireCaseIsOpening(){
        for( ProcessListener listener : processListener )
            listener.caseIsOpening();
    }
    public void fireCaseWasClosed(){
        for( ProcessListener listener : processListener )
            listener.caseWasClosed();
    }

    public void addProcessListener(ProcessListener listener) {
        processListener.add(listener);
    }

}


