package dpf.sp.gpinf.indexer.process.task;

import java.io.File;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import dpf.sp.gpinf.indexer.config.ConfigurationManager;
import dpf.sp.gpinf.indexer.config.IPEDConfig;
import dpf.sp.gpinf.indexer.util.Util;
import iped3.IItem;

public abstract class ThumbTask extends AbstractTask {

    public static final String thumbsFolder = "thumbs"; //$NON-NLS-1$
    public static final String HAS_THUMB = "hasThumb"; //$NON-NLS-1$

    private static final String SELECT_THUMB = "SELECT thumb FROM t1 WHERE id=?;"; //$NON-NLS-1$
    private static final String INSERT_THUMB = "INSERT INTO t1(id, thumb) VALUES(?,?) ON CONFLICT(id) DO UPDATE SET thumb=? WHERE thumb IS NULL;"; //$NON-NLS-1$

    protected File getThumbFile(IItem evidence) throws Exception {
        File thumbFile = null;

        IPEDConfig ipedConfig = (IPEDConfig) ConfigurationManager.getInstance().findObjects(IPEDConfig.class).iterator()
                .next();
        boolean storeThumbsInDb = !caseData.containsReport() || !ipedConfig.isHtmlReportEnabled();
        if (storeThumbsInDb) {
            return null;
        }
        thumbFile = Util.getFileFromHash(new File(output, thumbsFolder), evidence.getHash(), "jpg"); //$NON-NLS-1$
        return thumbFile;
    }

    protected boolean hasThumb(IItem evidence, File thumbFile) throws Exception {
        if (thumbFile == null) {
            Connection con = ExportFileTask.getSQLiteStorageCon(output, evidence.getHashValue().getBytes());
            try (PreparedStatement ps = con.prepareStatement(SELECT_THUMB)) {
                ps.setString(1, evidence.getHash());
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    byte[] thumb = rs.getBytes(1);
                    if (thumb != null) {
                        evidence.setThumb(thumb);
                        if (thumb.length > 0) {
                            evidence.setExtraAttribute(HAS_THUMB, true);
                        } else {
                            evidence.setExtraAttribute(HAS_THUMB, false);
                        }
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
                if (thumbFile.length() != 0) {
                    evidence.setExtraAttribute(HAS_THUMB, true);
                } else {
                    evidence.setExtraAttribute(HAS_THUMB, false);
                }
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
                tmp = File.createTempFile("iped", ".tmp", new File(output, thumbsFolder)); //$NON-NLS-1$ //$NON-NLS-2$
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

            if (evidence.getThumb() != null && evidence.getThumb().length > 0) {
                evidence.setExtraAttribute(HAS_THUMB, true);
            } else {
                evidence.setExtraAttribute(HAS_THUMB, false);
            }
        }
    }

}
