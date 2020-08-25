package dpf.ap.gpinf.telegramextractor;

import java.util.HashMap;

public class mapTypeMSG {
	
	private static final HashMap<Integer,String> msg=initMsg();
	
	private static HashMap<Integer,String> initMsg(){
		//reference telegramMediaAction.swift
		HashMap<Integer,String> msg=new HashMap<>();
		msg.put(1, "Group created");
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
		msg.put(18, "peer Joined");
		msg.put(19, "phone Number Request");
		msg.put(20, "history Screenshot");
		
		
        
        		
		return msg;
	}
	
	public static String decodeMsg(int tipo) {
		return msg.get(tipo);
	}
	
	public static String decodeMsg(String classname) {
		//toDo decode android types using classname
		return "toDo";
	}
	

}
