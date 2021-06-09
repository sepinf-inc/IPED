package dpf.sp.gpinf.indexer.process.task;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import iped3.IItem;
import iped3.sleuthkit.ISleuthKitItem;
import macee.core.Configurable;

public class DatabaseTask extends AbstractTask {

    private static String databaseName = "iped.db"; //$NON-NLS-1$
    private static int MAX_LIST_LEN = 200;
    private static boolean schemaDone = false;
    private Connection con;

    private ArrayList<IItem> itemList = new ArrayList<IItem>();

    @Override
    public List<Configurable<?>> getConfigurables() {
        return Collections.emptyList();
    }

    @Override
    public void init(Properties confParams, File confDir) throws Exception {

        Class.forName("org.sqlite.JDBC"); //$NON-NLS-1$
        con = DriverManager.getConnection("jdbc:sqlite:" + this.output.getCanonicalPath() + "/" + databaseName); //$NON-NLS-1$ //$NON-NLS-2$
        // con.setAutoCommit(false);

        if (schemaDone) {
            return;
        }

        Statement stmt = con.createStatement();

        String sql = "CREATE TABLE ITEMS (" //$NON-NLS-1$
                + "ID            INT PRIMARY KEY     NOT NULL," //$NON-NLS-1$
                + "PARENTID      INT, " //$NON-NLS-1$
                + "SLEUTHID      INT, " //$NON-NLS-1$
                + "NAME          TEXT    NOT NULL, " //$NON-NLS-1$
                + "TYPE          TEXT    NOT NULL, " //$NON-NLS-1$
                + "CATEGORY      TEXT    NOT NULL, " //$NON-NLS-1$
                + "PATH          TEXT    NOT NULL, " //$NON-NLS-1$
                + "EXPORT        TEXT, " //$NON-NLS-1$
                + "HASH          TEXT," //$NON-NLS-1$
                + "MIMETYPE      TEXT," //$NON-NLS-1$
                + "LENGTH        LONG, " //$NON-NLS-1$
                + "CARVEDOFFSET  LONG," //$NON-NLS-1$
                + "ISCARVED      BOOLEAN," //$NON-NLS-1$
                + "ISSUBITEM     BOOLEAN," //$NON-NLS-1$
                + "HASCHILD      BOOLEAN," //$NON-NLS-1$
                + "ISROOT        BOOLEAN," //$NON-NLS-1$
                + "ISDIR         BOOLEAN," //$NON-NLS-1$
                + "ISDELETED     BOOLEAN," //$NON-NLS-1$
                + "ISDUPLICATE   BOOLEAN," //$NON-NLS-1$
                + "TIMEOUT       BOOLEAN," //$NON-NLS-1$
                + "MODIFIED      TEXT, " //$NON-NLS-1$
                + "CREATED       TEXT, " //$NON-NLS-1$
                + "ACCESSED      TEXT)"; //$NON-NLS-1$

        stmt.executeUpdate(sql);
        stmt.close();

        schemaDone = true;
    }

    @Override
    public void finish() throws Exception {
        if (!con.isClosed()) {
            con.close();
        }
    }

    @Override
    protected void process(IItem evidence) throws Exception {

        if (!evidence.isQueueEnd()) {
            itemList.add(evidence);
            if (itemList.size() < MAX_LIST_LEN) {
                return;
            }
        }

        Statement stmt = con.createStatement();
        StringBuilder sql = new StringBuilder();
        sql.append("INSERT INTO ITEMS VALUES "); //$NON-NLS-1$

        for (int i = 0; i < itemList.size(); i++) {
            IItem e = itemList.get(i);
            sql.append("(" //$NON-NLS-1$
                    + e.getId() + "," //$NON-NLS-1$
                    + e.getParentId() + "," //$NON-NLS-1$
                    + ((e instanceof ISleuthKitItem) ? ((ISleuthKitItem) e).getSleuthId() : null) + ",\'" //$NON-NLS-1$
                    + e.getName() + "\',\'" //$NON-NLS-1$
                    + e.getType().getLongDescr() + "\',\'" //$NON-NLS-1$
                    + e.getCategories() + "\',\'" //$NON-NLS-1$
                    + e.getPath() + "\',\'" //$NON-NLS-1$
                    + e.getExportedFile() + "\',\'" //$NON-NLS-1$
                    + e.getHash() + "\',\'" //$NON-NLS-1$
                    + e.getMediaType().getBaseType() + "\'," //$NON-NLS-1$
                    + e.getLength() + "," //$NON-NLS-1$
                    + e.getFileOffset() + "," //$NON-NLS-1$
                    + (e.isCarved() ? 1 : 0) + "," //$NON-NLS-1$
                    + (e.isSubItem() ? 1 : 0) + "," //$NON-NLS-1$
                    + (e.hasChildren() ? 1 : 0) + "," //$NON-NLS-1$
                    + (e.isRoot() ? 1 : 0) + "," //$NON-NLS-1$
                    + (e.isDir() ? 1 : 0) + "," //$NON-NLS-1$
                    + (e.isDeleted() ? 1 : 0) + "," //$NON-NLS-1$
                    + (e.isDuplicate() ? 1 : 0) + "," //$NON-NLS-1$
                    + (e.isTimedOut() ? 1 : 0) + ",\'" //$NON-NLS-1$
                    + e.getModDate() + "\',\'" //$NON-NLS-1$
                    + e.getCreationDate() + "\',\'" //$NON-NLS-1$
                    + e.getAccessDate() + "\'" //$NON-NLS-1$
                    + ")"); //$NON-NLS-1$

            if (i < itemList.size() - 1) {
                sql.append(","); //$NON-NLS-1$
            } else {
                sql.append(";"); //$NON-NLS-1$
            }
        }

        stmt.executeUpdate(sql.toString());
        stmt.close();
        // con.commit();
        itemList.clear();
    }

}
