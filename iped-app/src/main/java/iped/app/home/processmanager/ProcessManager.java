package iped.app.home.processmanager;/*
 * @created 29/09/2022
 * @project IPED
 * @author Thiago S. Figueiredo
 */

import iped.app.home.newcase.model.Evidence;
import iped.configuration.IConfigurationDirectory;
import iped.engine.util.Util;
import org.apache.commons.lang3.SystemUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class ProcessManager {

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
            if( (currentEvidence.getAditionalComands() != null) && (! currentEvidence.getAditionalComands().trim().isEmpty()) ){
                commandArgs.add(currentEvidence.getAditionalComands());
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

    public String getIpedJarCommand(){
        String command = " -jar ".concat( Paths.get(System.getProperty(IConfigurationDirectory.IPED_APP_ROOT), "iped.jar").toString() );
        return command;
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

    public void startIpedProcess(ArrayList<String> commandList) throws IpedStartException {
        String output = "";
        try {
            final Process process = Runtime.getRuntime().exec(commandList.toArray(new String[0]));
            // aguarda terminar a aplicação
            process.waitFor();
            int exitVal = process.exitValue();
            output = readProcessOutput(process);
            if (exitVal != 0) {
                throw new IpedStartException(output);
            }
        } catch (final IOException | InterruptedException e) {
            throw new IpedStartException("Exception on Iped start", e);
        }
    }



    public String readProcessOutput(Process process) throws IOException {
        BufferedReader inputReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        StringBuffer output = new StringBuffer();
        String line = "";
        while ((line = inputReader.readLine()) != null) {
            output.append(line).append("\n");
        }
        BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
        while ((line = errorReader.readLine()) != null) {
            output.append(line).append("\n");
        }
        return output.toString();
    }

}
