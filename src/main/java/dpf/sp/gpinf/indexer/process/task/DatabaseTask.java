package dpf.sp.gpinf.indexer.process.task;

import gpinf.dev.data.EvidenceFile;
import gpinf.dev.data.SleuthEvidenceFile;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Properties;

import dpf.sp.gpinf.indexer.process.Worker;

public class DatabaseTask extends AbstractTask{
    
    public DatabaseTask(Worker worker) {
		super(worker);
		// TODO Auto-generated constructor stub
	}

	private static String databaseName = "iped.db";
    private static int MAX_LIST_LEN = 200;
    private static boolean schemaDone = false;
    private Connection con;
    
    private ArrayList<EvidenceFile> itemList = new ArrayList<EvidenceFile>();

    @Override
    public void init(Properties confParams, File confDir) throws Exception {
                    
        Class.forName("org.sqlite.JDBC");
        con = DriverManager.getConnection("jdbc:sqlite:" + this.output.getCanonicalPath() + "/" + databaseName);
        //con.setAutoCommit(false);
        
        if(schemaDone)
            return;
        
        Statement stmt = con.createStatement();
        
        String sql = "CREATE TABLE ITEMS (" +
                "ID            INT PRIMARY KEY     NOT NULL," +
                "PARENTID      INT, " +
                "SLEUTHID      INT, " +
                "NAME          TEXT    NOT NULL, " +
                "TYPE          TEXT    NOT NULL, " + 
                "CATEGORY      TEXT    NOT NULL, " + 
                "PATH          TEXT    NOT NULL, " +
                "EXPORT        TEXT, " +
                "HASH          TEXT," +
                "MIMETYPE      TEXT," +
                "LENGTH        LONG, " +
                "CARVEDOFFSET  LONG," +
                "ISCARVED      BOOLEAN," +
                "ISSUBITEM     BOOLEAN," +
                "HASCHILD      BOOLEAN," +
                "ISROOT        BOOLEAN," +
                "ISDIR         BOOLEAN," +
                "ISDELETED     BOOLEAN," +
                "ISDUPLICATE   BOOLEAN," +
                "TIMEOUT       BOOLEAN," +
                "MODIFIED      TEXT, " +
                "CREATED       TEXT, " +
                "ACCESSED      TEXT)";
        
        stmt.executeUpdate(sql);
        stmt.close();
        
        schemaDone = true;
    }

    @Override
    public void finish() throws Exception {
        if(!con.isClosed())
            con.close();
    }

    @Override
    protected void process(EvidenceFile evidence) throws Exception {
        
        if(!evidence.isQueueEnd()){
            itemList.add(evidence);
            if(itemList.size() < MAX_LIST_LEN)
                return;
        }
        
        Statement stmt = con.createStatement();
        StringBuilder sql = new StringBuilder();
        sql.append("INSERT INTO ITEMS VALUES ");
        
        for(int i = 0; i < itemList.size(); i++){
            EvidenceFile e = itemList.get(i);
            sql.append("(" + 
                    e.getId() +","+
                    e.getParentId() +","+
                    getSleuthId(e) +",\'"+ 
                    e.getName() + "\',\'" + 
                    e.getType().getLongDescr() + "\',\'" + 
                    e.getCategories() + "\',\'" +
                    e.getPath() + "\',\'" +
                    e.getExportedFile() + "\',\'" +
                    e.getHash() + "\',\'" +
                    e.getMediaType().getBaseType() + "\'," +
                    e.getLength() + "," +
                    e.getFileOffset() + "," +
                    (e.isCarved() ? 1 : 0) + "," +
                    (e.isSubItem() ? 1 : 0) + "," +
                    (e.hasChildren() ? 1 : 0) + "," +
                    (e.isRoot() ? 1 : 0) + "," +
                    (e.isDir() ? 1 : 0) + "," +
                    (e.isDeleted() ? 1 : 0) + "," +
                    (e.isDuplicate() ? 1 : 0) + "," +
                    (e.isTimedOut() ? 1 : 0) + ",\'" +
                    e.getModDate() + "\',\'" +
                    e.getCreationDate() + "\',\'" +
                    e.getAccessDate() + "\'" +
                    ")");
            
            if(i < itemList.size() - 1)
                sql.append(",");
            else
                sql.append(";");
        }
        
        stmt.executeUpdate(sql.toString());
        stmt.close();
        //con.commit();
        itemList.clear();
    }
    
    private String getSleuthId(EvidenceFile evidenceFile) {
    	if (evidenceFile instanceof SleuthEvidenceFile) {
    		return ((SleuthEvidenceFile) evidenceFile).getSleuthId();
    	}
    	return null;
    }

}
