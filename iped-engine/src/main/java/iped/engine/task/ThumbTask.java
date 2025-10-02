package iped.engine.task;

import java.io.File;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import iped.data.IItem;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.HtmlReportTaskConfig;
import iped.engine.util.Util;

public abstract class ThumbTask extends AbstractTask {

    public static final String THUMBS_FOLDER_NAME = "thumbs";
    public static final String HAS_THUMB = "hasThumb";

    public static final String THUMB_EXT = "jpg";

    private static final String SELECT_THUMB = "SELECT thumb FROM thumbs WHERE id=?;"; //$NON-NLS-1$
    private static final String INSERT_THUMB = "INSERT INTO thumbs(id, thumb) VALUES(?,?) ON CONFLICT(id) DO UPDATE SET thumb=? WHERE thumb IS NULL;"; //$NON-NLS-1$

    protected File getThumbFile(IItem evidence) throws Exception {
        HtmlReportTaskConfig htmlReportConfig = ConfigurationManager.get().findObject(HtmlReportTaskConfig.class);
        boolean storeThumbsInDisk = caseData.containsReport() && htmlReportConfig.isEnabled();
        if (storeThumbsInDisk) {
            File reportSubFolder = HTMLReportTask.getReportSubFolder();
            return Util.getFileFromHash(new File(reportSubFolder, THUMBS_FOLDER_NAME), evidence.getHash(), THUMB_EXT);
        }
        return null; // it will be stored in DB
    }

    protected boolean hasThumb(IItem evidence, File thumbFile) throws Exception {
        if (evidence.getThumb() != null) {
            return true;
        }
        if (thumbFile == null) {
            Connection con = ExportFileTask.getSQLiteStorageCon(output, evidence.getHashValue().getBytes());
            try (PreparedStatement ps = con.prepareStatement(SELECT_THUMB)) {
                ps.setString(1, evidence.getHash());
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    byte[] thumb = rs.getBytes(1);
                    if (thumb != null) {
                        evidence.setThumb(thumb);
                        evidence.setExtraAttribute(HAS_THUMB, thumb.length > 0);
                        rs.close();
                        ps.close();
                        return true;
                    }
                }
                rs.close();
                ps.close();
            }
        } else {
            // if exists, do not need to compute again
            if (thumbFile.exists()) {
                evidence.setThumb(Files.readAllBytes(thumbFile.toPath()));
                evidence.setExtraAttribute(HAS_THUMB, thumbFile.length() != 0);
                return true;
            }
        }
        return false;
    }

    protected void saveThumb(IItem evidence, File thumbFile) throws Throwable {
        File tmp = null;
        try {
            if (evidence.getThumb() == null) {
                evidence.setThumb(new byte[0]); // zero size thumb means thumb error
            }
            if (thumbFile == null) {
                Connection con = ExportFileTask.getSQLiteStorageCon(output, evidence.getHashValue().getBytes());
                try (PreparedStatement ps = con.prepareStatement(INSERT_THUMB)) {
                    ps.setString(1, evidence.getHash());
                    ps.setBytes(2, evidence.getThumb());
                    ps.setBytes(3, evidence.getThumb());
                    ps.executeUpdate();
                    ps.close();
                }
            } else {
                if (!thumbFile.getParentFile().exists()) {
                    thumbFile.getParentFile().mkdirs();
                }
                tmp = File.createTempFile("thumb", ".tmp", thumbFile.getParentFile());
                Files.write(tmp.toPath(), evidence.getThumb());
            }

        } catch (Throwable e) {
            throw e;

        } finally {
            try {
                if (tmp != null && !tmp.renameTo(thumbFile)) {
                    tmp.delete();
                }
            } catch (Exception e) {
            }
        }
    }

    protected boolean updateHasThumb(IItem evidence) {
        boolean hasThumb = evidence.getThumb() != null && evidence.getThumb().length > 0;
        evidence.setExtraAttribute(HAS_THUMB, hasThumb);
        return hasThumb;
    }
}
