package net.sf.oereader;

/**
 * Contains the information for a Message object.
 * 
 * This object is only used in the {@link net.sf.oereader.OEFileHeader#type
 * MessageDB} .dbx files. The pointers to the MessageInfo objects are stored in
 * the {@link net.sf.oereader.OETree Tree}.
 * 
 * @author Alex Franchuk
 * @version 1.0
 */
public class OEMessageInfo extends OEIndexedInfo {
	/**
	 * Index
	 */
	public int index;
	/**
	 * Flags, as described <a
	 * href="http://oedbx.aroh.de/doc/OE_Dbx_MessageInfo.html#flags">here</a>
	 */
	public int flags;
	/**
	 * Number of lines in the {@link net.sf.oereader.OEMessage Message} body
	 */
	public int messageBodyLines;
	/**
	 * Pointer to the corresponding {@link net.sf.oereader.OEMessage Message}
	 */
	public int messagep;
	/**
	 * Priority of the email (1 = low, 3 = normal, 5 = high)
	 */
	public int priority;
	/**
	 * Length of the message header and body (can be incorrect)
	 */
	public int messageTextLength;
	/**
	 * {@link net.sf.oereader.OEMessage Message} object of this message
	 */
	public OEMessage message;
	/**
	 * Time of creation of this message
	 */
	public long[] createtime;
	/**
	 * Time of saving of this message
	 */
	public long[] savetime;
	/**
	 * Time of reception of this message
	 */
	public long[] receivetime;
	/**
	 * Id of this message
	 */
	public String messageId;
	/**
	 * Original subject of this message (without "re:" etc.)
	 */
	public String origSubject;
	/**
	 * Subject of this message
	 */
	public String subject;
	/**
	 * Sender address and name
	 */
	public String senderan;
	/**
	 * Answered to {@link #messageId messageId}
	 */
	public String answeredTo;
	/**
	 * Server/Newsgroup/Message number (list)
	 */
	public String serverlist;
	/**
	 * Server the message was taken from
	 */
	public String server;
	/**
	 * Name of the sender ("From")
	 */
	public String sendername;
	/**
	 * Address of the sender ("From")
	 */
	public String senderaddr;
	/**
	 * Name of the recipient ("To")
	 */
	public String receivername;
	/**
	 * Address of the recipient ("To")
	 */
	public String receiveraddr;
	/**
	 * Mail or newsgroup account name
	 */
	public String accountname;

	/**
	 * Constructor for the MessageInfo object.
	 * 
	 * Starts by loading the information in the
	 * {@link net.sf.oereader.OEIndexedInfo IndexedInfo} parent object.
	 * 
	 * @param data
	 *            data to be read
	 * @param m
	 *            index to start from
	 */
	public OEMessageInfo(OEData data, int m) {
		super(data, m);
		for (int i = 0; i < indices.length; i++) {
			IndexValue iv = indices[i];
			switch (iv.index) {
			case 0:
				index = (iv.direct ? iv.value : toInt4(data, datapos + iv.value));
				break;
			case 1:
				flags = (iv.direct ? iv.value : toInt4(data, datapos + iv.value));
				break;
			case 2:
				createtime = toInt8_2(data, datapos + iv.value);
				break;
			case 3:
				messageBodyLines = (iv.direct ? iv.value : toInt4(data, datapos + iv.value));
				break;
			case 4:
				messagep = (iv.direct ? iv.value : toInt4(data, datapos + iv.value));
				break;
			case 5:
				origSubject = toString(data, datapos + iv.value);
				break;
			case 6:
				savetime = toInt8_2(data, datapos + iv.value);
				break;
			case 7:
				messageId = toString(data, datapos + iv.value);
				break;
			case 8:
				subject = toString2(data, datapos + iv.value);
				break;
			case 9:
				senderan = toString(data, datapos + iv.value);
				break;
			case 10:
				answeredTo = toString(data, datapos + iv.value);
				break;
			case 11:
				serverlist = toString(data, datapos + iv.value);
				break;
			case 12:
				server = toString(data, datapos + iv.value);
				break;
			case 13:
				sendername = toString(data, datapos + iv.value);
				break;
			case 14:
				senderaddr = toString(data, datapos + iv.value);
				break;
			case 16:
				priority = (iv.direct ? iv.value : toInt4(data, datapos + iv.value));
				break;
			case 17:
				messageTextLength = (iv.direct ? iv.value : toInt4(data, datapos + iv.value));
				break;
			case 18:
				receivetime = toInt8_2(data, datapos + iv.value);
				break;
			case 19:
				receivername = toString(data, datapos + iv.value);
				break;
			case 20:
				receiveraddr = toString(data, datapos + iv.value);
				break;
			case 26:
				accountname = toString(data, datapos + iv.value);
				break;
			default:
				break;
			}
		}
		message = new OEMessage(data, messagep);
	}
}
