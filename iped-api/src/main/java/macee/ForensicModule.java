package macee;

import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import macee.core.App;
import macee.core.Module;
import macee.core.util.Identity;
import org.apache.commons.configuration.PropertiesConfiguration;

/**
 * A forensic module owns one or more forensic components.
 * 
 * COMENTÁRIO (Werneck): O módulo serve para agrupar componentes que "vivem"
 * juntos. Por exemplo, um módulo pode propor parsers, scripts, filtros e outros
 * que estão correlacionados. Esse módulo tem seus próprios dados,
 * configurações, arquivo de log e diretório de saída (necessário?). No entanto,
 * creio que ExaminationProcedures não deixa claro que esses componentes podem
 * ser de outros tipos. Não deveria ser ForensicModuleComponent?
 *
 * @param <A>
 *            The type of application the module is associated with.
 * @author werneck.bwph
 */
public interface ForensicModule<A extends App> extends Identity<UUID>, Module<A>, Serializable {

    String getNamespace();

    Map getModuleData();

    PropertiesConfiguration getConfiguration();

    File getLogFile();

    void setLogFile(File file);

    File getOutputFolder();

    void setOutputFolder(File file);

    ForensicModuleDescriptor getModuleDescriptor();

    void setModuleDescriptor(ForensicModuleDescriptor description);

    Collection<Class<? extends ExaminationProcedure>> getExaminationProcedures();

    <T extends ExaminationProcedure> T getExaminationProcedureInstance(Class<T> examinationProcedure) throws Exception;
}
