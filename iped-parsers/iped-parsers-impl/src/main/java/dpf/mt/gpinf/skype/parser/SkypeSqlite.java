package dpf.mt.gpinf.skype.parser;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Hashtable;
import java.util.List;

import org.apache.commons.lang.ArrayUtils;

import iped3.io.IItemBase;
import iped3.search.IItemSearcher;
import iped3.util.BasicProps;

/**
 * Classe de persistência (apenas leitura) para acesso aos dados no banco de
 * dados SQLLite main.db.
 *
 * @author Patrick Dalla Bernardina patrick.pdb@dpf.gov.br
 */

public class SkypeSqlite implements SkypeStorage {

    private static final String STORAGE_DB_PATH = "/media_messaging/storage_db/asyncdb/storage_db.db"; //$NON-NLS-1$
    private static final String CACHE_DB_PATH = "/media_messaging/media_cache/asyncdb/cache_db.db"; //$NON-NLS-1$
    private static final String CACHE_DIR_PATH = "/media_messaging/media_cache"; //$NON-NLS-1$

    private File mainDb;
    private String baseSkypeFolder;

    protected Connection conn = null;
    protected Connection connStorageDb = null;
    protected Connection connMediaCacheDb = null;

    protected File storageDbPath;
    protected File cacheMediaDbPath;

    private String skypeName;
    private Hashtable<Integer, SkypeConversation> conversations = null;

    List<IItemBase> cachedMediaList = null;

    SkypeAccount account;

    public SkypeSqlite(File file, String mainDbPath) {
        this.mainDb = file;
        this.baseSkypeFolder = new File(mainDbPath).getParent();
        carregaSkypeName();
    }

    @Override
	public void searchMediaCache(IItemSearcher searcher) {
        if (baseSkypeFolder == null)
            return;
        baseSkypeFolder = baseSkypeFolder.replace('\\', '/');
        baseSkypeFolder = searcher.escapeQuery(baseSkypeFolder);

        String query = BasicProps.PATH + ":\"" + baseSkypeFolder + STORAGE_DB_PATH + "\""; //$NON-NLS-1$//$NON-NLS-2$
        List<IItemBase> items = searcher.search(query);
        for (IItemBase item : items) {
            if (item.getName().equalsIgnoreCase("storage_db.db")) { //$NON-NLS-1$
                storageDbPath = getTempFile(item);
                break;
            }
        }
        query = BasicProps.PATH + ":\"" + baseSkypeFolder + CACHE_DB_PATH + "\"~1"; // ~1 //$NON-NLS-1$//$NON-NLS-2$
                                                                                    // searchers for media_cache_v2,
                                                                                    // media_cache_v3, etc
        items = searcher.search(query);
        for (IItemBase item : items)
            if (item.getName().equalsIgnoreCase("cache_db.db")) { //$NON-NLS-1$
                cacheMediaDbPath = getTempFile(item);
                break;
            }

        query = BasicProps.PATH + ":\"" + baseSkypeFolder + CACHE_DIR_PATH + "\" -" + BasicProps.TYPE + ":slack"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        cachedMediaList = searcher.search(query);
    }

    private File getTempFile(IItemBase item) {
        try (InputStream is = item.getBufferedStream()) {
            Path temp = Files.createTempFile("sqlite-parser", null); //$NON-NLS-1$
            Files.copy(is, temp, StandardCopyOption.REPLACE_EXISTING);
            return temp.toFile();

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public void close() {
        try {
            if (conn != null)
                conn.close();
            if (connStorageDb != null)
                connStorageDb.close();
            if (connMediaCacheDb != null)
                connMediaCacheDb.close();
            if (storageDbPath != null)
                storageDbPath.delete();
            if (cacheMediaDbPath != null)
                cacheMediaDbPath.delete();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Connection getConnection() throws SkypeParserException {
        if (conn != null) {
            return conn;
        } else {
            try {
                this.conn = DriverManager.getConnection("jdbc:sqlite:" + mainDb.getAbsolutePath()); //$NON-NLS-1$
                return conn;

            } catch (SQLException ex) {
                throw new SkypeParserException(ex);
            }

        }
    }

    public Connection getStorageDbConnection() throws SkypeParserException {
        if (storageDbPath == null) {
            return null;
        }

        if (connStorageDb != null) {
            return connStorageDb;
        } else {
            try {
                this.connStorageDb = DriverManager.getConnection("jdbc:sqlite:" + storageDbPath.getAbsolutePath()); //$NON-NLS-1$
                return connStorageDb;

            } catch (SQLException ex) {
                throw new SkypeParserException(ex);
            }

        }
    }

    public Connection getMediaCacheDbConnection() throws SkypeParserException {
        if (cacheMediaDbPath == null) {
            return null;
        }

        if (connMediaCacheDb != null) {
            return connMediaCacheDb;
        } else {
            try {
                this.connMediaCacheDb = DriverManager
                        .getConnection("jdbc:sqlite:" + cacheMediaDbPath.getAbsolutePath()); //$NON-NLS-1$
                return connMediaCacheDb;

            } catch (SQLException ex) {
                throw new SkypeParserException(ex);
            }

        }
    }

    public static final String SELECT_SKYPENAME = "select skypename, " //$NON-NLS-1$
            + "id," //$NON-NLS-1$
            + "avatar_timestamp*1000 as avatar_timestamp," //$NON-NLS-1$
            + "avatar_image," //$NON-NLS-1$
            + "mood_timestamp*1000 as mood_timestamp," //$NON-NLS-1$
            + "mood_text," //$NON-NLS-1$
            + "profile_timestamp*1000 as profile_timestamp," //$NON-NLS-1$
            + "about," //$NON-NLS-1$
            + "phone_home," //$NON-NLS-1$
            + "phone_office," //$NON-NLS-1$
            + "emails," //$NON-NLS-1$
            + "phone_mobile," //$NON-NLS-1$
            + "fullname," //$NON-NLS-1$
            + "skypename," //$NON-NLS-1$
            + "birthday," //$NON-NLS-1$
            + "country," //$NON-NLS-1$
            + "province," //$NON-NLS-1$
            + "city," //$NON-NLS-1$
            + "registration_timestamp*1000 as registration_timestamp" //$NON-NLS-1$
            + " from accounts"; //$NON-NLS-1$

    public void carregaSkypeName() {

        try (Statement stmt = getConnection().createStatement()) {
            ResultSet rs = stmt.executeQuery(SkypeSqlite.SELECT_SKYPENAME);

            rs.next();

            skypeName = rs.getString("skypename"); //$NON-NLS-1$

            account = new SkypeAccount();
            account.setSkypeName(skypeName);
            account.setId(Short.toString(rs.getShort("id"))); //$NON-NLS-1$
            account.setAbout(rs.getString("about")); //$NON-NLS-1$
            account.setMood(rs.getString("mood_text")); //$NON-NLS-1$
            account.setFullname(rs.getString("fullname")); //$NON-NLS-1$
            account.setPhoneHome(rs.getString("phone_home")); //$NON-NLS-1$
            account.setPhoneMobile(rs.getString("phone_mobile")); //$NON-NLS-1$
            account.setPhoneOffice(rs.getString("phone_office")); //$NON-NLS-1$
            account.setCity(rs.getString("city")); //$NON-NLS-1$
            account.setProvince(rs.getString("province")); //$NON-NLS-1$
            account.setCountry(rs.getString("country")); //$NON-NLS-1$
            int birthday = rs.getInt("birthday"); //$NON-NLS-1$
            if (!rs.wasNull()) {
                int ano = birthday / 10000;
                int mes = (birthday - ano * 10000) / 100;
                int dia = birthday % 100;
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(0);
                cal.set(ano, mes - 1, dia);
                account.setBirthday(cal.getTime());
            }
            byte[] b = rs.getBytes("avatar_image"); //$NON-NLS-1$
            if (b != null) {
                account.setAvatar(getThumb(b));
            } else {
                account.setAvatar(null);
            }
            account.setEmail(rs.getString("emails")); //$NON-NLS-1$

            if (rs.getDate("profile_timestamp") != null) { //$NON-NLS-1$
                account.setProfileTimestamp(rs.getDate("profile_timestamp")); //$NON-NLS-1$
            }
            if (rs.getDate("avatar_timestamp") != null) { //$NON-NLS-1$
                account.setAvatarTimestamp(rs.getDate("avatar_timestamp")); //$NON-NLS-1$
            }
            if (rs.getDate("mood_timestamp") != null) { //$NON-NLS-1$
                account.setMoodTimestamp(rs.getDate("mood_timestamp")); //$NON-NLS-1$
            }
            if (rs.getDate("registration_timestamp") != null) { //$NON-NLS-1$
                account.setRegistrationTimestamp(rs.getDate("registration_timestamp")); //$NON-NLS-1$
            }

        } catch (SQLException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (SkypeParserException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private byte[] getThumb(byte[] bytes) {
        int start = 0, end = bytes.length - 1;
        while (bytes[start] == 0)
            start++;
        while (bytes[end] == 0)
            end--;
        return ArrayUtils.subarray(bytes, start, end + 1);
    }

    private static final String SELECT_CACHE_DB = "select id, key, serialized_data, actual_size from assets where key like '%URI' order by actual_size desc"; //$NON-NLS-1$
    
    private class NameSize{
        String name;
        long size;
    }

    private NameSize getFileNameFromCacheDb(String uri) {

        if (cacheMediaDbPath == null) {
            return null;
        }

        try (Statement stmt = this.getMediaCacheDbConnection().createStatement()) {

            String select = SELECT_CACHE_DB.replaceAll("URI", uri); //$NON-NLS-1$
            ResultSet rs = stmt.executeQuery(select);

            if (!rs.next())
                return null;

            byte[] bytes = rs.getBytes("serialized_data"); //$NON-NLS-1$
            String str = new String(bytes, "windows-1252"); //$NON-NLS-1$
            String prefix = "$CACHE/\\\\"; //$NON-NLS-1$
            int start = str.indexOf(prefix) + prefix.length();
            int end = start;
            for (; end < bytes.length - 1; end++)
                if (bytes[end] == 0)
                    break;

            NameSize result = new NameSize();
            result.name = str.substring(start, end - 1);
            result.size = rs.getLong("actual_size"); //$NON-NLS-1$
            
            return result;

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static final String SELECT_URI = "select * from  documents d " //$NON-NLS-1$
            + " left join contents c ON c.document_id = d.id "; //$NON-NLS-1$

    public SkypeMessageUrlFile getLocalUri(String uri) {

        /* Se existe o banco de cache */
        if (storageDbPath != null) {
            SkypeMessageUrlFile urlFile = new SkypeMessageUrlFile();
            urlFile.setUri(uri);

            try (Statement stmt = getStorageDbConnection().createStatement()) {

                String select = SkypeSqlite.SELECT_URI + " where d.uri = '" + uri + "'"; //$NON-NLS-1$ //$NON-NLS-2$
                ResultSet rs = stmt.executeQuery(select);

                if (!rs.next()) {
                    return getCacheFileFromCacheDb(urlFile);
                }

                urlFile.setId(rs.getInt("id")); //$NON-NLS-1$
                urlFile.setLocalFile(rs.getString("local_path")); //$NON-NLS-1$
                urlFile.setSize(rs.getInt("content_size")); //$NON-NLS-1$
                try {
                    // may not exist
                    urlFile.setFilename(rs.getString("file_name")); //$NON-NLS-1$
                } catch (SQLException e) {
                }

                // search thumb
                for (IItemBase item : cachedMediaList) {
                    String nome = item.getName();
                    if (nome.startsWith("i" + urlFile.getId() + "^") && nome.contains("thumb")) { //$NON-NLS-1$ //$NON-NLS-2$
                        urlFile.setThumbFile(item);
                        break;
                    }
                }

                boolean originalFound = false;
                for (IItemBase item : cachedMediaList) {
                    String nome = item.getName();
                    if (nome.startsWith("i" + urlFile.getId() + "^") && item.getLength() == urlFile.getSize()) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        urlFile.setCacheFile(item);
                        originalFound = true;
                        break;
                    }
                }
                if (!originalFound)
                    return getCacheFileFromCacheDb(urlFile);

                return urlFile;

            } catch (SQLException e) {
                e.printStackTrace();

            } catch (SkypeParserException e) {
                e.printStackTrace();
            }
            return null;
        } else {
            return null;
        }
    }

    private SkypeMessageUrlFile getCacheFileFromCacheDb(SkypeMessageUrlFile urlFile) {
        NameSize nameSize = getFileNameFromCacheDb(urlFile.getUri());
        if (nameSize != null) {
            String name = nameSize.name;
            if(name.startsWith("^") && name.indexOf("^", 1) == 51) {
                name = name.split("\\^")[1];
            }
            for (IItemBase item : cachedMediaList) {
                if (item.getName().contains(name) && item.getLength() == nameSize.size) {
                    urlFile.setCacheFile(item);
                    return urlFile;
                }
            }
        }
        return null;
    }

    public static final String SELECT_MESSAGES = " select c.id as convo_id, " //$NON-NLS-1$
            + " c.identity," //$NON-NLS-1$
            + "    c.displayname," //$NON-NLS-1$
            + "    c.last_activity_timestamp*1000 as last_activity_timestamp," //$NON-NLS-1$
            + "    c.creation_timestamp*1000 as creation_timestamp," //$NON-NLS-1$
            + "   m.id as messageId," //$NON-NLS-1$
            + "   m.author," //$NON-NLS-1$
            + "    m.from_dispname," //$NON-NLS-1$
            + "    m.dialog_partner," //$NON-NLS-1$
            + "    m.timestamp*1000 as timestamp," //$NON-NLS-1$
            + "    m.body_xml," //$NON-NLS-1$
            + "    m.chatmsg_type," //$NON-NLS-1$
            + "    m.sending_status," //$NON-NLS-1$
            + "    m.type," //$NON-NLS-1$
            + "    m.edited_by," //$NON-NLS-1$
            + "    m.edited_timestamp*1000 as edited_timestamp," //$NON-NLS-1$
            + "    m.reason," //$NON-NLS-1$
            + "	   m.chatmsg_status, " //$NON-NLS-1$
            + "    m.remote_id, " //$NON-NLS-1$
            + "    chats.participants, " //$NON-NLS-1$
            + "    chats.activemembers, " //$NON-NLS-1$
            + "    chats.type as chattype," //$NON-NLS-1$
            + "	   chats.name as chatname," //$NON-NLS-1$
            + "    c.identity," //$NON-NLS-1$
            + "    c.displayname " //$NON-NLS-1$
            + "    from conversations c LEFT JOIN Messages m " //$NON-NLS-1$
            + "            ON c.id = m.convo_id and m.type in (61,63,201,68,50,51) " //$NON-NLS-1$
            + "            LEFT JOIN chats ON chats.name = m.chatname " //$NON-NLS-1$
            + " order by c.id, m.timestamp*1000 "; //$NON-NLS-1$

    public static final String SELECT_PARTICIPANTS = " select p.identity from Participants p" //$NON-NLS-1$
            + " where p.convo_id = ? " //$NON-NLS-1$
            + " and p.identity <> ( select skypename from Accounts )"; //$NON-NLS-1$

    private SkypeConversation criaConvResultSet(ResultSet rs) throws SkypeParserException {
        /* Prepara consulta dos participantes de conversas - usado adiante no método */
        try (PreparedStatement partsstmt = getConnection().prepareStatement(SELECT_PARTICIPANTS)) {
            SkypeConversation c = new SkypeConversation();

            c.setChatName(rs.getString("chatname")); //$NON-NLS-1$
            c.setDisplayName(rs.getString("displayname")); //$NON-NLS-1$
            c.setCreationDate(rs.getDate("creation_timestamp")); //$NON-NLS-1$
            c.setLastActivity(rs.getDate("last_activity_timestamp")); //$NON-NLS-1$
            c.setId(rs.getString("convo_id")); //$NON-NLS-1$

            /* busca e preenche os participantes da conversa */
            partsstmt.setInt(1, Integer.parseInt(c.getId()));
            ResultSet parts = partsstmt.executeQuery();
            ArrayList<String> participantes = new ArrayList<String>();
            while (parts.next()) {
                participantes.add(parts.getString("identity")); //$NON-NLS-1$
            }
            c.setParticipantes(participantes);

            return c;
        } catch (SQLException e) {
            throw new SkypeParserException(e);
        }
    }

    @Override
	public List<SkypeConversation> extraiMensagens() throws SkypeParserException {
        conversations = new Hashtable<Integer, SkypeConversation>();

        try (Statement stmt = getConnection().createStatement();) {
            ResultSet rs = stmt.executeQuery(SkypeSqlite.SELECT_MESSAGES);

            List<SkypeConversation> resultado = new ArrayList<SkypeConversation>();

            SkypeConversation c = null;
            List<SkypeMessage> mensagens = new ArrayList<SkypeMessage>();

            while (rs.next()) {
                /* caso seja o primeiro registro sendo processado */
                if (c == null) {
                    c = criaConvResultSet(rs);
                    resultado.add(c);
                    conversations.put(new Integer(c.getId()), c);
                }

                /*
                 * se a mensagem for de uma conversa diferente da mensagem imediatamente
                 * anterior
                 */
                if (!rs.getString("convo_id").contentEquals(c.getId())) { //$NON-NLS-1$
                    /* atribui a lista de mensagens à conversação anterior e limpa a lista */
                    c.setMessages(mensagens);
                    mensagens = new ArrayList<SkypeMessage>();

                    /* cria uma nova conversação */
                    c = criaConvResultSet(rs);
                    resultado.add(c);
                    conversations.put(new Integer(c.getId()), c);
                }

                SkypeMessage sm = null;

                if (!rs.wasNull()) {
                    int messageType = rs.getInt("type"); //$NON-NLS-1$

                    sm = new SkypeMessage();
                    sm.setId(Long.toString(rs.getLong("messageId")) ); //$NON-NLS-1$
                    sm.setAutor(rs.getString("author")); //$NON-NLS-1$
                    sm.setFromMe(skypeName.equals(sm.getAutor()));
                    if ((sm.getAutor()!=null) && !sm.getAutor().equals(skypeName)) {
                        sm.setDestino(skypeName);
                    } else {
                        sm.setDestino(rs.getString("identity")); //$NON-NLS-1$
                    }
                    sm.setConteudo(rs.getString("body_xml")); //$NON-NLS-1$
                    sm.setData(rs.getDate("timestamp")); //$NON-NLS-1$
                    sm.setEditor(rs.getString("edited_by")); //$NON-NLS-1$
                    if (rs.getDate("edited_timestamp") != null) { //$NON-NLS-1$
                        sm.setDataEdicao(rs.getDate("edited_timestamp")); //$NON-NLS-1$
                    }
                    sm.setTipo(rs.getInt("type")); //$NON-NLS-1$
                    sm.setChatMessageStatus(rs.getInt("chatmsg_status")); //$NON-NLS-1$
                    sm.setSendingStatus(rs.getInt("sending_status")); //$NON-NLS-1$
                    sm.setIdRemoto(rs.getInt("remote_id")); //$NON-NLS-1$
                    sm.setConversation(c);

                    if (messageType == 201) {
                        String prefix = "uri=\""; //$NON-NLS-1$
                        int idx = sm.getConteudo().indexOf(prefix);
                        if (idx != -1) {
                            idx += prefix.length();
                            int end = sm.getConteudo().indexOf("\"", idx + 1); //$NON-NLS-1$
                            if (end != -1) {
                                String uri = sm.getConteudo().substring(idx, end);
                                sm.setAnexoUri(getLocalUri(uri));
                            }
                        }
                    }
                }

                if (sm != null) {
                    mensagens.add(sm);
                }
            }

            /* atribui a última lista de mensagens à conversação */
            if (c != null)
                c.setMessages(mensagens);

            return resultado;

        } catch (SQLException ex) {
            throw new SkypeParserException(ex);
        }
    }

    public static final String SELECT_CONTACTS = "SELECT id, fullname, skypename, city, displayname, assigned_phone1, assigned_phone2, assigned_phone3," //$NON-NLS-1$
            + " pstnnumber, about, avatar_image, birthday, emails, " //$NON-NLS-1$
            + " profile_timestamp*1000 as profile_timestamp," //$NON-NLS-1$
            + " avatar_timestamp*1000 as avatar_timestamp," //$NON-NLS-1$
            + " lastonline_timestamp*1000 as lastonline_timestamp," //$NON-NLS-1$
            + " lastused_timestamp*1000 as lastused_timestamp " //$NON-NLS-1$
            + "from contacts"; //$NON-NLS-1$

    @Override
	public List<SkypeContact> extraiContatos() throws SkypeParserException {
        try (Statement stmt = getConnection().createStatement();) {

            ResultSet rs = stmt.executeQuery(SkypeSqlite.SELECT_CONTACTS);

            List<SkypeContact> resultado = new ArrayList<SkypeContact>();

            while (rs.next()) {
                SkypeContact c = new SkypeContact();

                c.setId(rs.getString("id")); //$NON-NLS-1$
                c.setFullName(rs.getString("fullname")); //$NON-NLS-1$
                c.setSkypeName(rs.getString("skypename")); //$NON-NLS-1$
                c.setCity(rs.getString("city")); //$NON-NLS-1$
                c.setDisplayName(rs.getString("displayname")); //$NON-NLS-1$

                String phones = rs.getString("assigned_phone1"); //$NON-NLS-1$
                if (phones == null)
                    phones = ""; //$NON-NLS-1$
                if (rs.getString("assigned_phone2") != null) { //$NON-NLS-1$
                    if (!phones.isEmpty())
                        phones += " / "; //$NON-NLS-1$
                    phones += rs.getString("assigned_phone2"); //$NON-NLS-1$
                }
                if (rs.getString("assigned_phone3") != null) { //$NON-NLS-1$
                    if (!phones.isEmpty())
                        phones += " / "; //$NON-NLS-1$
                    phones += rs.getString("assigned_phone3"); //$NON-NLS-1$
                }
                c.setAssignedPhone(phones);

                c.setPstnNumber(rs.getString("pstnnumber")); //$NON-NLS-1$
                c.setSobre(rs.getString("about")); //$NON-NLS-1$
                byte[] b = rs.getBytes("avatar_image"); //$NON-NLS-1$
                if (b != null) {
                    c.setAvatar(getThumb(b));
                } else {
                    c.setAvatar(null);
                }
                int birthday = rs.getInt("birthday"); //$NON-NLS-1$
                if (!rs.wasNull()) {
                    int ano = birthday / 10000;
                    int mes = (birthday - ano * 10000) / 100;
                    int dia = birthday - ano * 10000 - mes * 100;
                    Calendar cal = Calendar.getInstance();
                    cal.setTimeInMillis(0);
                    cal.set(ano, mes - 1, dia);
                    c.setBirthday(cal.getTime());
                }

                c.setEmail(rs.getString("emails")); //$NON-NLS-1$

                if (rs.getDate("profile_timestamp") != null) { //$NON-NLS-1$
                    c.setProfileDate(rs.getDate("profile_timestamp")); //$NON-NLS-1$
                }
                if (rs.getDate("avatar_timestamp") != null) { //$NON-NLS-1$
                    c.setAvatarDate(rs.getDate("avatar_timestamp")); //$NON-NLS-1$
                }
                if (rs.getDate("lastused_timestamp") != null) { //$NON-NLS-1$
                    c.setLastUsed(rs.getDate("lastused_timestamp")); //$NON-NLS-1$
                }
                if (rs.getDate("lastonline_timestamp") != null) { //$NON-NLS-1$
                    c.setLastOnlineDate(rs.getDate("lastonline_timestamp")); //$NON-NLS-1$
                }

                // c.setAvatar(ArrayUtils.subarray(rs.getBytes("avatar_image"), 1,
                // rs.getBytes("avatar_image").length-1));

                resultado.add(c);
            }

            return resultado;

        } catch (SQLException ex) {
            throw new SkypeParserException(ex);
        }
    }

    public static final String SELECT_FILE_TRANSFER = "select  " //$NON-NLS-1$
            + "     t.id as id," //$NON-NLS-1$
            + "     t.convo_id as convo_id," //$NON-NLS-1$
            + "		t.type as type," //$NON-NLS-1$
            + "		t.starttime*1000 as starttime," //$NON-NLS-1$
            + "		t.accepttime*1000 as accepttime," //$NON-NLS-1$
            + "		t.finishtime*1000 as finishtime," //$NON-NLS-1$
            + "		t.partner_handle as partner_handle," //$NON-NLS-1$
            + "		t.filepath as filepath," //$NON-NLS-1$
            + "		t.filename as filename," //$NON-NLS-1$
            + "		t.filesize as filesize," //$NON-NLS-1$
            + "		t.bytestransferred as bytestransferred," //$NON-NLS-1$
            + "		t.status as status," //$NON-NLS-1$
            + "		m.author as author," //$NON-NLS-1$
            + "		m.sending_status," //$NON-NLS-1$
            + "		c.identity as identity," //$NON-NLS-1$
            + "		t2.partner_handle as partner_origem" //$NON-NLS-1$
            + "	from Transfers t " //$NON-NLS-1$
            + "					left join Transfers t2 on t.chatmsg_guid = t2.chatmsg_guid and t.chatmsg_index = t2.chatmsg_index and t2.partner_handle = (select skypename from Accounts)" //$NON-NLS-1$
            + "					left join Conversations c on c.id = t.convo_id " //$NON-NLS-1$
            + " 				left join Messages m on m.guid = t.chatmsg_guid "; //$NON-NLS-1$

    @Override
	public List<SkypeFileTransfer> extraiTransferencias() throws SkypeParserException {
        try (Statement stmt = getConnection().createStatement();) {

            ResultSet rs = stmt.executeQuery(SkypeSqlite.SELECT_FILE_TRANSFER);

            List<SkypeFileTransfer> resultado = new ArrayList<SkypeFileTransfer>();

            if (conversations == null) {
                extraiMensagens();
            }

            while (rs.next()) {
                SkypeFileTransfer f = new SkypeFileTransfer();

                f.setId(rs.getInt("id")); //$NON-NLS-1$
                f.setConversation(conversations.get(new Integer(rs.getInt("convo_id")))); //$NON-NLS-1$
                f.setAccept(rs.getDate("accepttime")); //$NON-NLS-1$
                f.setType(rs.getShort("type")); //$NON-NLS-1$
                f.setStart(rs.getDate("starttime")); //$NON-NLS-1$
                f.setFinish(rs.getDate("finishtime")); //$NON-NLS-1$
                f.setFilePath(rs.getString("filepath")); //$NON-NLS-1$
                f.setFilename(rs.getString("filename")); //$NON-NLS-1$
                f.setBytesTransferred(rs.getInt("bytestransferred")); //$NON-NLS-1$
                f.setFileSize(rs.getInt("filesize")); //$NON-NLS-1$
                f.setStatus(rs.getShort("status")); //$NON-NLS-1$

                f.setFrom(rs.getString("author"));// considera a origem da transferência o autor da //$NON-NLS-1$
                                                  // mensagem
                if (rs.wasNull()) {
                    // se o autor foi nulo
                    f.setFrom(rs.getString("partner_origem")); // considera a origem o partner_handle de //$NON-NLS-1$
                                                               // um registro com a mesma chatmsg_guid + chatmsg_index e
                                                               // com mesmo skypename
                    if (rs.wasNull()) {
                        // se ainda nulo, considera a origem o próprio partner_handle da transferência
                        f.setFrom(rs.getString("partner_handle")); //$NON-NLS-1$
                    } else {
                        // se não for nulo e o partner_handle for igual à conta, indica um registro de
                        // oferta de arquivo
                        if (f.getFrom().equals(rs.getString("partner_handle"))) { //$NON-NLS-1$
                            f.setType((short) 3); // oferta de arquivo
                        }
                    }
                }
                if (!skypeName.equals(f.getFrom())) {
                    f.setTo(skypeName); // se o autor não for o proprio usuário considera-o como destino da troca de
                                        // arquivo
                } else {
                    if (f.getType() == 2) {
                        f.setTo(rs.getString("partner_handle")); //$NON-NLS-1$
                    } else {
                        f.setTo(rs.getString("identity")); //$NON-NLS-1$
                    }
                }

                resultado.add(f);
            }

            return resultado;

        } catch (SQLException ex) {
            throw new SkypeParserException(ex);
        }
    }

    @Override
	public String getSkypeName() {
        return skypeName;
    }

    @Override
	public SkypeAccount getAccount() {
        return account;
    }

}
