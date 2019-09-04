package macee.core;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * This interface is used to indicate a configurable object that holds
 * configuration for both the application and the user.
 * 
 * COMENTÁRIO (Werneck): era usado em App só pra separar a configuração global
 * da configuração de usuário. Não define carregamento ou salvamento de
 * configurações. Poderia ser usado nos módulos, sendo AppConfig uma
 * configuração global do aplicative e UserConfig, uma configuração própria do
 * módulo.
 * 
 * TO DO: mudar nomes dos métodos para atender o comentário acima.
 *
 * @param <AppConfig>
 *            the type used for the application configuration
 * @param <UserConfig>
 *            the type used for the user configuration
 * @author Bruno W. P. Hoelz
 */
public interface Configurable<AppConfig, UserConfig> {

    /*
     * Returns a filter to be used for resource lookup on the configuration
     * directory system
     * 
     * @return the filter to be used
     */
    public DirectoryStream.Filter<Path> getResourceLookupFilter();

    /*
     * Returns a filter to be used for resource lookup on the configuration
     * directory system
     * 
     * @return the filter to be used
     */
    public void processConfigs(List<Path> resources) throws IOException;

    /**
     * Gets the application configuration object.
     *
     * @return the application configuration object.
     */
    AppConfig getApplicationConfiguration();

    void setApplicationConfiguration(AppConfig config);

    Set<String> getApplicationPropertyNames();

    /**
     * Gets the user configuration object.
     *
     * @return the user configuration object.
     */
    UserConfig getUserConfiguration();

    void setUserConfiguration(UserConfig config);

}
