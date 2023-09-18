package iped.engine.config;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.Files;
import java.nio.file.Path;

import iped.configuration.Configurable;

public class ParsersConfig implements Configurable<String> {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    private static final String PARSER_CONFIG = "ParserConfig.xml"; //$NON-NLS-1$

    public static final String PARSER_DISABLED_ATTR = "iped:disabled";

    private String parserConfigXml;
    private transient Path tmp;

    @Override
    public Filter<Path> getResourceLookupFilter() {
        return new Filter<Path>() {
            @Override
            public boolean accept(Path entry) throws IOException {
                return entry.endsWith(PARSER_CONFIG);
            }
        };
    }

    @Override
    public void processConfig(Path resource) throws IOException {
        parserConfigXml = new String(Files.readAllBytes(resource), StandardCharsets.UTF_8);
    }

    @Override
    public String getConfiguration() {
        return parserConfigXml;
    }

    @Override
    public void setConfiguration(String config) {
        parserConfigXml = config;
    }
    
    public String removeDisabledParsers(String parserConfigXml) {
        String[] slices = parserConfigXml.split(PARSER_DISABLED_ATTR+"=\"true\"");
        StringBuffer result=new StringBuffer();
        for (int i = 0; i < slices.length; i++) {
            String part = slices[i];
            if(i>0) {
                int disabledParserEndIndex = part.indexOf(">");
                if(disabledParserEndIndex==0 || part.charAt(disabledParserEndIndex-1)!='/') {
                    disabledParserEndIndex = part.indexOf("</parser>");
                }
                part=part.substring(disabledParserEndIndex+1);
            }
            if(i<slices.length-1) {
                int disabledParserIndex = part.lastIndexOf("<parser");
                result.append(part.substring(0, disabledParserIndex));
            }else {
                result.append(part);
            }
        }
        return result.toString();
    }

    public synchronized File getTmpConfigFile() {
        if (tmp == null) {
            try {
                tmp = Files.createTempFile("parser-config", ".xml");
                
                Files.write(tmp, removeDisabledParsers(parserConfigXml).getBytes(StandardCharsets.UTF_8));
                tmp.toFile().deleteOnExit();

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return tmp.toFile();
    }

    @Override
    public void save(Path resource) {
        try {
            File confDir = new File(resource.toFile(), Configuration.CONF_DIR);
            confDir.mkdirs();
            File confFile = new File(confDir, PARSER_CONFIG);            

            Files.write(confFile.toPath(), parserConfigXml.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void reset() {
        // TODO Auto-generated method stub        
    }
}
