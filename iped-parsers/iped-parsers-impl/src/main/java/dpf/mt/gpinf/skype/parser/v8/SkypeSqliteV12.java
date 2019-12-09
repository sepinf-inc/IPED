package dpf.mt.gpinf.skype.parser.v8;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import dpf.mt.gpinf.skype.parser.SkypeAccount;
import dpf.mt.gpinf.skype.parser.SkypeContact;
import dpf.mt.gpinf.skype.parser.SkypeConversation;
import dpf.mt.gpinf.skype.parser.SkypeFileTransfer;
import dpf.mt.gpinf.skype.parser.SkypeMessage;
import dpf.mt.gpinf.skype.parser.SkypeParserException;
import dpf.mt.gpinf.skype.parser.SkypeStorage;
import iped3.search.IItemSearcher;

public class SkypeSqliteV12 implements SkypeStorage {
	private File mainDb;

	private Connection conn = null;

	private String skypeName;
	private Hashtable<String, SkypeConversation> conversations = null;

	SimpleDateFormat sdf = new SimpleDateFormat("YYYY-MM-DD");

	SkypeAccount account;
	JSONParser jp;

	public SkypeSqliteV12(File file, String mainDbPath) {
		this.mainDb = file;
		jp = new JSONParser();
		carregaSkypeName();
	}

	@Override
	public void close() {
		try {
			if (conn != null)
				conn.close();
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

	public static final String SELECT_SKYPENAME = "select nsp_data from internaldata where nsp_pk = 'Cpriv_prefs_v2'"; //$NON-NLS-1$
	public static final String SELECT_ACCOUNT = "SELECT * from profilecachev8 where nsp_pk like '%%' "; //$NON-NLS-1$

	public void carregaSkypeName() {
		try (Statement stmt = getConnection().createStatement()) {
			ResultSet rs = stmt.executeQuery(SkypeSqliteV12.SELECT_SKYPENAME);
			rs.next();

			JSONObject json = (JSONObject) jp.parse(rs.getString("nsp_data"));
			json = (JSONObject) json.get("value");
			skypeName = (String) json.get("skypeName"); //$NON-NLS-1$
			rs.close();

			rs = stmt.executeQuery(SkypeSqliteV12.SELECT_ACCOUNT.replace("'%%'", "'%"+skypeName+"%'"));
			rs.next();

			json = (JSONObject) jp.parse(rs.getString("nsp_data"));
			SkypeContact accountContact = parseJsonContact(rs.getString("nsp_data"));

			account = new SkypeAccount();
			account.setSkypeName(skypeName);
			account.setId(accountContact.getId()); //$NON-NLS-1$
			account.setAbout(accountContact.getSobre()); //$NON-NLS-1$
			account.setMood(accountContact.getSobre()); //$NON-NLS-1$
			account.setFullname((String) json.get("fullname")); //$NON-NLS-1$
			account.setPhoneHome((String) json.get("phone_home")); //$NON-NLS-1$
			account.setPhoneMobile((String) json.get("phone_mobile")); //$NON-NLS-1$
			account.setPhoneOffice((String) json.get("phone_office")); //$NON-NLS-1$
			account.setCity(accountContact.getCity()); //$NON-NLS-1$
			account.setProvince((String) json.get("province")); //$NON-NLS-1$
			account.setCountry((String) json.get("country")); //$NON-NLS-1$
			account.setBirthday(accountContact.getBirthday());			
			account.setAvatar(null);
			account.setEmail(jsonStringArrayToString((JSONArray) json.get("emails"))); //$NON-NLS-1$

		} catch (SQLException e) {
			e.printStackTrace();
		} catch (SkypeParserException e) {
			e.printStackTrace();
		} catch (ParseException e) {
			e.printStackTrace();
		}
	}


	private static String jsonPhoneArrayToString(JSONArray array) {
		if (array == null) return "";
		
		String result = "";
		for (@SuppressWarnings("rawtypes")
		Iterator iterator = array.iterator(); iterator.hasNext();) {
			JSONObject json = (JSONObject) iterator.next();
			result+=((String) json.get("number"))+",";
		}
		if(!"".equals(result)) {
			result = result.substring(0,result.length()-1);
		}
		return result;		
	}
	
	
	private static String jsonStringArrayToString(JSONArray array) {
		if (array == null) return "";
		
		String result = "";
		for (@SuppressWarnings("rawtypes")
		Iterator iterator = array.iterator(); iterator.hasNext();) {
			String json = (String) iterator.next();
			result+=json+",";
		}
		if(!"".equals(result)) {
			result = result.substring(0,result.length()-1);
		}
		return result;		
	}

	public static final String SELECT_MESSAGES = " select * from messagesv12 order by nsp_i_convidcreatedtime"; //$NON-NLS-1$

	public static final String SELECT_CONVERSATIONS = " select * from conversationsv14 "; //$NON-NLS-1$
	public static final String SELECT_CONVERSATIONS_V13 = " select * from conversationsv13 "; //$NON-NLS-1$

	private void loadConversations() throws SkypeParserException {
		conversations = new Hashtable<String, SkypeConversation>();
		PreparedStatement partsstmt=null;
		try{
			partsstmt = getConnection().prepareStatement(SELECT_CONVERSATIONS);
			ResultSet convs;
			try {
				convs = partsstmt.executeQuery();
			}catch (SQLException e) {
				partsstmt = getConnection().prepareStatement(SELECT_CONVERSATIONS_V13);
				convs = partsstmt.executeQuery();
			}
			while (convs.next()) {
				SkypeConversation conv;
				try {
					conv = parseJsonConversation(convs.getString("nsp_data"));
					conversations.put(conv.getId(), conv); // $NON-NLS-1$
				} catch (ClassCastException | ParseException e) {
					System.out.print("SkypeSqliteV12: Error parsing skype conversation:"+convs.getString("nsp_pk"));
					e.printStackTrace();
				}
			}
		} catch (SQLException e) {
			if(partsstmt!=null) {
				try {
					partsstmt.close();
				}catch(SQLException ex) {
				}
			}
			throw new SkypeParserException(e);
		}
	}

	private final SkypeConversation parseJsonConversation(String string) throws ParseException {
		SkypeConversationV14 conv = new SkypeConversationV14();
		
		conv.setJSONdata(string);
		JSONObject json = (JSONObject) jp.parse(string);
		JSONObject jsonConv = (JSONObject) json.get("conv");

		conv.setId((String) jsonConv.get("id"));

		JSONObject threadProps = (JSONObject) jsonConv.get("_threadProps");
		if(threadProps!=null) {
			conv.setChatName((String) threadProps.get("topic"));
			conv.setDisplayName((String) threadProps.get("topic"));
			if(threadProps.get("createdat")!=null) {
				try {
					conv.setCreationDate(new Date((Long)threadProps.get("createdat")));
				}catch(ClassCastException e) {
					//ignore
				}
			}
		}

		JSONArray jsonMembers = (JSONArray) jsonConv.get("_threadMembers");
		if(jsonMembers!=null) {
			ArrayList<String> participantes = new ArrayList<String>();		
			for (@SuppressWarnings("unchecked")
			Iterator<JSONObject> iterator = jsonMembers.iterator(); iterator.hasNext();) {
				JSONObject member = iterator.next();
				participantes.add((String) member.get("id"));
			}
			conv.setParticipantes(participantes);		
		}

		return conv;
	}

	@Override
	public Collection<SkypeConversation> extraiMensagens() throws SkypeParserException {
		loadConversations();
		ArrayList<Exception> parseExceptions = new ArrayList<Exception>();

		try (Statement stmt = getConnection().createStatement();) {
			ResultSet rs = stmt.executeQuery(SkypeSqliteV12.SELECT_MESSAGES);

			while (rs.next()) {
				String jsonData = rs.getString("nsp_data");
				SkypeMessageV12 sm = null;
				SkypeConversation conv = null;
				try {
					JSONObject jsonMessage = (JSONObject) jp.parse(jsonData);
					sm = parseJsonMessage(jsonMessage);
					conv = conversations.get((String) jsonMessage.get("conversationId"));
				} catch (ParseException e) {
					sm = new SkypeMessageV12();
					conv = new SkypeConversation();
				}
				sm.setJSONdata(jsonData);
				sm.setConversation(conv);
				List<SkypeMessage> mensagens = conv.getMessages();
				if(mensagens==null) {
					mensagens = new ArrayList<SkypeMessage>();
					conv.setMessages(mensagens);
				}
				mensagens.add(sm);
			}

			return conversations.values();
		} catch (SQLException ex) {
			throw new SkypeParserException(ex);
		} 
	}

	private SkypeMessageV12 parseJsonMessage(JSONObject jsonMessage) {
		SkypeMessageV12 sm = new SkypeMessageV12();
		try{
			sm.setId( (String) jsonMessage.get("cuid") ); //$NON-NLS-1$
			sm.setAutor((String) jsonMessage.get("creator")); //$NON-NLS-1$
			sm.setFromMe(((Long)jsonMessage.get("_isMyMessage")).intValue() == 1);

			if (!sm.getAutor().endsWith(":"+skypeName)){
				if(sm.getAutor().endsWith("@thread.skype")) {
					/* if the author of the message was any other participant on a group conversation */
					sm.setDestino((String) jsonMessage.get("conversationId")); //$NON-NLS-1$
				}else {
					/* if the author of the message was the other participant on a direct conversation */
					sm.setDestino(skypeName);
				}
			} else {
				sm.setDestino((String) jsonMessage.get("conversationId")); //$NON-NLS-1$
			}

			sm.setConteudo( (String) jsonMessage.get("content") ); //$NON-NLS-1$
			sm.setData(new Date((Long) jsonMessage.get("createdTime"))); //$NON-NLS-1$
		}catch (ClassCastException e) {
			//ingore with the recognized values
		}

		return sm;
	}

	public static final String SELECT_CONTACTS = "SELECT * from profilecachev8"; //$NON-NLS-1$

	@Override
	public Collection<SkypeContact> extraiContatos() throws SkypeParserException {
		try (Statement stmt = getConnection().createStatement();) {
			ResultSet rs = stmt.executeQuery(SkypeSqliteV12.SELECT_CONTACTS);

			Collection<SkypeContact> resultado = new ArrayList<SkypeContact>();

			while (rs.next()) {
				SkypeContact c;
				try {
					c = parseJsonContact(rs.getString("nsp_data"));
					resultado.add(c);
				} catch (ParseException e) {
					System.out.print("SkypeSqliteV12: Error parsing skype contact:"+rs.getString("nsp_pk"));
				}
			}

			return resultado;
		} catch (SQLException ex) {
			throw new SkypeParserException(ex);
		}
	}

	private SkypeContact parseJsonContact(String string) throws ParseException {
		SkypeContactV8 c = new SkypeContactV8();

		JSONObject json = (JSONObject) jp.parse(string);
		
		String mri = (String) json.get("mri");
		c.setId(mri); //$NON-NLS-1$
		String firstName = (String) json.get("firstName");
		String fullName = firstName;
		String lastName = (String) json.get("lastName");
		if(lastName!=null && "".contentEquals(lastName)) {
			fullName = firstName + " " + lastName;
		}
		c.setFullName(fullName); //$NON-NLS-1$

		c.setSkypeName(mri.substring(mri.lastIndexOf(":")+1)); //$NON-NLS-1$

		c.setCity((String) json.get("city")); //$NON-NLS-1$
		c.setDisplayName((String) json.get("displayNameOverride")); //$NON-NLS-1$

		c.setAssignedPhone(jsonPhoneArrayToString((JSONArray) json.get("phones")));

		try {
			String birthday = (String) json.get("birthday");

			if((birthday!=null) && (!"".contentEquals(birthday))) {
				c.setBirthday(sdf.parse((String) json.get("birthday")));
			}
		}catch (Exception e) {
			System.out.print("SkypeSqliteV12: birthday parse error:"+(String) json.get("birthday"));
		}

		c.setSobre((String) json.get("mood")); //$NON-NLS-1$

		c.setAvatar(null);
		c.setThumbUrl((String) json.get("thumbUrl"));
		c.setJSONdata(string);

		return c;
	}

	/* Deprecated for the new Skype Schema */
	@Override
	public List<SkypeFileTransfer> extraiTransferencias() throws SkypeParserException {
		return new ArrayList<SkypeFileTransfer>();
	}

	@Override
	public String getSkypeName() {
		return skypeName;
	}

	@Override
	public SkypeAccount getAccount() {
		return account;
	}

	/* Deprecated for the new Skype Schema */
	@Override
	public void searchMediaCache(IItemSearcher searcher) {
	}

}
