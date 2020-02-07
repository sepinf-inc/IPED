package macee;

import java.util.List;
import java.util.Map;
import macee.descriptor.Descriptor;

/**
 * Um descritor para o módulo. Pode ser armazenado e carregado em JSON/XML. Se
 * associa ao registro de módulos e ferramentas do portal da MACEE que deve ser
 * usado para verificar atualizações bem como fornecer documentação e suporte.
 * 
 */
public interface ForensicModuleDescriptor extends Descriptor {

    String getVersionTag();

    String getNamespace();

    String getModuleClass();

    String getAuthor();

    String getWikiUrl();

    String getDownloadUrl();

    String getSupportEmail();

    String getReleaseNotes();

    List<String> getDependencies();

    List<Map<String, Object>> getComponents();

    // default configuration and parameters
    Map<String, Object> getConfig();

}
