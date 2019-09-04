package macee;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.SeekableByteChannel;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

/**
 * Defines an item in a case.
 *
 * COMENTÁRIO (Werneck): a instanciação de CaseItem deve ser leve e não incluir
 * dependências. Por isso, alguns métodos como getExtractedText() tem que ser
 * lazy (rodar apenas quando forem chamados) ou serem fornecidos por um serviço
 * externo com apoio de um cache (ex.:
 * caseManager.getExtractedTextForItem(ItemRef ref))
 * 
 * @author werneck.bwph
 */
public interface CaseItem {

    int getId();

    /**
     * Gets the ID of the associated evidence.
     *
     * @return the ID of the associated evidence.
     */
    String getDataSourceId();

    String getCaseId();

    String getName();

    String getBasePath();

    String getPath();

    ItemType getType();

    String getContentType();

    /**
     * Gets the category.
     *
     * @return the category.
     */
    List<String> getCategories();

    /**
     * Gets the extension.
     *
     * @return the extension.
     */
    String getExtension();

    /**
     * Gets the access date.
     *
     * @return the access date.
     */
    ZonedDateTime getAccessDateTime();

    /**
     * Gets the creation date.
     *
     * @return the creation date.
     */
    ZonedDateTime getCreationDateTime();

    /**
     * Gets the modification date.
     *
     * @return the modification date.
     */
    ZonedDateTime getModificationDateTime();

    /**
     * Gets the logical size.
     *
     * @return the logical size.
     */
    long getSize();

    Map<String, String> getHashes();

    String getHash(String algorithm);

    boolean isCarved();

    boolean isDeleted();

    boolean isIgnored();

    boolean isSubItem();

    boolean isDuplicate();

    boolean hasExtractedText();

    boolean isEncrypted();

    boolean hasChildren();

    String getExtractedText();

    InputStream getInputStream() throws IOException;

    SeekableByteChannel getChannel() throws IOException;

    File getTempFile() throws IOException;

    File getViewFile() throws IOException;

    int getParent();

    int[] getParents();

    int[] getChildren();

    int getChildCount();

    Map<String, Object> getProperties();

    Object getProperty(String name);
}
