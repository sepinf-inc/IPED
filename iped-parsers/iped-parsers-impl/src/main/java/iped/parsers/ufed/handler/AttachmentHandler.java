package iped.parsers.ufed.handler;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.data.IItemReader;
import iped.io.ISeekableInputStreamFactory;
import iped.parsers.ufed.model.Attachment;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;
import iped.search.IItemSearcher;

public class AttachmentHandler extends BaseModelHandler<Attachment> {

    private static final Logger logger = LoggerFactory.getLogger(AttachmentHandler.class);

    public AttachmentHandler(Attachment model, IItemReader parentItem) {
        super(model, parentItem);
    }

    @Override
    public void doLoadReferences(IItemSearcher searcher) {

        if (model.getFileId() == null && model.getAttachmentExtractedPath() == null) {
            return;
        }

        // lookup for file using "ufed:file_id"
        if (model.getFileId() != null) {
            String query = searcher.escapeQuery(ExtraProperties.UFED_ID) + ":\"" + model.getFileId() + "\"";
            List<IItemReader> fileItems = searcher.search(query);
            if (!fileItems.isEmpty()) {
                if (fileItems.size() > 1) {
                    logger.warn("Found more than 1 file for attachment [0]: {}", fileItems);
                }
                model.setReferencedFile(fileItems.get(0));
                return;
            }
        }

        // lookup for file using "ufed:attachment_extracted_path"
        if (model.getAttachmentExtractedPath() != null) {
            {
                String query = BasicProps.EVIDENCE_UUID + ":\"" + item.getDataSource().getUUID() + "\"" //
                        + " && " + searcher.escapeQuery(ExtraProperties.UFED_META_PREFIX + "local_path") + ":\"" + model.getAttachmentExtractedPath() + "\"";
                List<IItemReader> fileItems = searcher.search(query);
                if (!fileItems.isEmpty()) {
                    if (fileItems.size() > 1) {
                        logger.warn("Found more than 1 file for attachment [1]: {}", fileItems);
                    }
                    model.setReferencedFile(fileItems.get(0));
                    return;
                }
            }

            // if referenced file was not found, read the "ufed:attachment_extracted_path" content to handle it
            // PS: this is a rare state, that was seen in UFDR files generated in PA 8.
            // In such situations, the file with file_id was not present and attachment_extracted_path pointed to unreferenced files
            ISeekableInputStreamFactory isf = item.getInputStreamFactory();
            if (isf != null) {
                try (InputStream is = isf.getSeekableInputStream(model.getAttachmentExtractedPath())) {
                    byte[] content = IOUtils.toByteArray(is);

                    // first lookup for file with same hash
                    String md5 = DigestUtils.md5Hex(content);
                    String query = BasicProps.EVIDENCE_UUID + ":\"" + item.getDataSource().getUUID() + "\"" //
                            + " && " + BasicProps.HASH + ":" + md5;
                    List<IItemReader> fileItems = searcher.search(query);
                    if (!fileItems.isEmpty()) {
                        if (fileItems.size() > 1) {
                            logger.warn("Found more than 1 file for attachment [2]: {}", fileItems);
                        }
                        model.setReferencedFile(fileItems.get(0));

                    } else {
                        // if file was not indexed, set it as unreferenced content
                        model.setUnreferencedContent(content);
                    }
                    return;
                } catch (IOException e) {
                    logger.error("Error reading attachment unreferencedContent {}", model);
                }
            }
        }

        if (!StringUtils.endsWith(model.getFilename(), ".enc")                  // don´t log ".enc" files not found - e.g. WhatsApp
                && !StringUtils.equalsIgnoreCase(model.getContentType(), "URL") // don't log URL attachments
        ) {
            logger.warn("Attachment file reference was not found: {}", model);
        }
    }

    @Override
    public String getTitle() {
        return new StringBuilder()
                .append("Attachment") //
                .append("-[") //
                .append(StringUtils.firstNonBlank(model.getFilename(), model.getId())) //
                .append("]") //
                .toString();
    }
}
