package dpf.ap.gpinf.telegramextractor;

import java.util.HashMap;

public class mapTypeMSG {
	
	private static final HashMap<Integer,String> msg=initMsg();
	
	private static final HashMap<String,Integer> androidmsg=initAndroidMsg();
	
	private static HashMap<Integer,String> initMsg(){
		//reference telegramMediaAction.swift
		HashMap<Integer,String> msg=new HashMap<>();
		msg.put(1, "Group/chat/channel created");
		msg.put(2, "Add members");
		msg.put(3, "Remove members");
		msg.put(4, "Photo Updated");
		msg.put(5, "Title Updated");
		
		msg.put(6, "pinned Message Updated");
		msg.put(7, "User joined by Link");
		msg.put(8, "Change channel to group");
		msg.put(9, "Change group to channel");
		msg.put(10, "history Cleared");
		
		msg.put(11, "history Screenshot");
		msg.put(12, "message Autoremove");
		msg.put(13, "game Score");
		msg.put(14, "phone Call");
		msg.put(15, "payment Sent");
		
		msg.put(16, "custom Text");
		msg.put(17, "bot Domain Access Granted");
		msg.put(18, "bot SentSecure Values");
		msg.put(19, "peer Joined");
		msg.put(20, "phone Number Request");
		
		//extra from android
		
		msg.put(30, "Contact sign up");
		msg.put(31, "Chat delete photo");
        
        		
		return msg;
	}
	
	private static HashMap<String,Integer> initAndroidMsg(){
		HashMap<String,Integer> msg=new HashMap<>();
	
		msg.put("TL_messageActionChatCreate", 1);
		msg.put("TL_messageActionChannelCreate", 1);
		msg.put("TL_messageActionCreatedBroadcastList", 1);
		
			
		msg.put("TL_messageActionChatAddUser", 2);
		msg.put("TL_messageActionChatAddUser_old", 2);
		
		
		
		
		msg.put("TL_messageActionChatDeleteUser", 3);
		
		msg.put("TL_messageActionChatEditPhoto", 4);
		msg.put("TL_messageActionUserUpdatedPhoto", 4);
		
		msg.put("TL_messageActionChatEditTitle", 5);
		
		msg.put("TL_messageActionPinMessage", 6);
		
		
		msg.put("TL_messageActionChatJoinedByLink",7);
		
		msg.put("TL_messageActionChannelMigrateFrom", 9);
		
		msg.put("TL_messageActionHistoryClear", 10);
		
		msg.put("TL_messageActionScreenshotTaken", 11);
		
		msg.put("TL_messageActionGameScore", 13);
		
		msg.put("TL_messageActionPhoneCall", 14);
		
		msg.put("TL_messageActionPaymentSent", 15);
		
		msg.put("TL_messageActionCustomAction", 16);
		
		msg.put("TL_messageActionBotAllowed", 17);
		
		msg.put("TL_messageActionSecureValuesSent", 18);
		
		msg.put("TL_messageActionUserJoined", 19);
		
		//only android		
		msg.put("TL_messageActionContactSignUp", 30);
		
		msg.put("TL_messageActionChatDeletePhoto", 31);
		
		
		
		
		
		
		return msg;
	}
	
	
	public static String decodeMsg(int tipo) {
		return msg.get(tipo);
	}
	
	public static String decodeMsg(String classname) {
		//toDo decode android types using classname
		Integer a=androidmsg.get(classname);

		if(a!=null)
			return decodeMsg(a);
		
		return null;
	}
	

}
