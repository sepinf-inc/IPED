/*
 * Copyright 2020-2020, João Vitor de Sá Hauck
 * 
 * This file is part of Indexador e Processador de Evidencias Digitais (IPED).
 *
 * IPED is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * IPED is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with IPED.  If not, see <http://www.gnu.org/licenses/>.
 */
package dpf.ap.gpinf.telegramextractor;

import java.util.HashMap;

import dpf.sp.gpinf.indexer.parsers.util.Messages;

public class MapTypeMSG {

    public static final String PHONE_CALL_STRING = Messages.getString("TelegramReport.PhoneCall");

    private static final HashMap<Integer, String> msg = initMsg();

    private static final HashMap<String, Integer> androidmsg = initAndroidMsg();

    /*
     * this file is based on the observations made in the file found at
     * https://github.com/TelegramMessenger/Telegram-iOS/blob/master/submodules/
     * TelegramCore/Sources/SyncCore/SyncCore_TelegramMediaAction.swift
     */
    private static HashMap<Integer, String> initMsg() {
        // reference telegramMediaAction.swift
        HashMap<Integer, String> msg = new HashMap<>();
        msg.put(1, Messages.getString("TelegramReport.GroupCreate"));
        msg.put(2, Messages.getString("TelegramReport.AddMember"));
        msg.put(3, Messages.getString("TelegramReport.RemoveMember"));
        msg.put(4, Messages.getString("TelegramReport.PhotoUpdated"));
        msg.put(5, Messages.getString("TelegramReport.TitleUpdated"));

        msg.put(6, Messages.getString("TelegramReport.pinnMessage"));
        msg.put(7, Messages.getString("TelegramReport.UserJoinLink"));
        msg.put(8, Messages.getString("TelegramReport.ChannelMigratedFromGroup"));
        msg.put(9, Messages.getString("TelegramReport.ChangeToChannel"));
        msg.put(10, Messages.getString("TelegramReport.HistoryCleared"));

        msg.put(11, Messages.getString("TelegramReport.HistoryScreenshot"));
        msg.put(12, Messages.getString("TelegramReport.MessageAutoRemove"));
        msg.put(13, Messages.getString("TelegramReport.GameScore"));
        msg.put(14, PHONE_CALL_STRING);
        msg.put(15, Messages.getString("TelegramReport.PaymentSent"));

        msg.put(16, Messages.getString("TelegramReport.CustomText"));

        msg.put(17, Messages.getString("TelegramReport.BotAcess"));
        msg.put(18, Messages.getString("TelegramReport.BotSent"));
        msg.put(19, Messages.getString("TelegramReport.PeerJoin"));
        msg.put(20, Messages.getString("TelegramReport.PhoneNumberRequest"));

        msg.put(21, Messages.getString("TelegramReport.geoProximityReached"));

        msg.put(22, Messages.getString("TelegramReport.groupPhoneCall"));
        msg.put(23, Messages.getString("TelegramReport.inviteToGroupPhoneCall"));

        msg.put(24, Messages.getString("TelegramReport.ChangeChatTheme"));
        msg.put(25, Messages.getString("TelegramReport.joinedByRequest"));

        // extra from android

        msg.put(30, Messages.getString("TelegramReport.ContactSignUp"));
        msg.put(31, Messages.getString("TelegramReport.ChatDeletePhoto"));

        return msg;
    }

    private static HashMap<String, Integer> initAndroidMsg() {
        HashMap<String, Integer> msg = new HashMap<>();

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
        
        msg.put("TL_messageActionChatMigrateTo", 9);

        msg.put("TL_messageActionChatJoinedByLink", 7);

        msg.put("TL_messageActionChannelMigrateFrom", 8);

        msg.put("TL_messageActionHistoryClear", 10);

        msg.put("TL_messageActionScreenshotTaken", 11);

        msg.put("TL_messageActionGameScore", 13);

        msg.put("TL_messageActionPhoneCall", 14);

        msg.put("TL_messageActionInviteToGroupCall", 23);
        
        msg.put("TL_messageActionGroupCall", 22);

        msg.put("TL_messageActionPaymentSent", 15);

        msg.put("TL_messageActionCustomAction", 16);

        msg.put("TL_messageActionBotAllowed", 17);

        msg.put("TL_messageActionSecureValuesSent", 18);

        msg.put("TL_messageActionUserJoined", 19);

        msg.put("TL_messageActionChatJoinedByRequest", 25);
        
        msg.put("TL_messageActionSetChatTheme", 24);

        msg.put("TL_messageActionGeoProximityReached", 21);

        msg.put("TL_messageActionSetMessagesTTL", 12);

        msg.put("TL_messageActionTTLChange", 12);

        // only android
        msg.put("TL_messageActionContactSignUp", 30);

        msg.put("TL_messageActionChatDeletePhoto", 31);


        return msg;
    }

    public static String decodeMsg(int tipo) {
        String result = msg.get(tipo);
        if (result != null)
            return result;
        if (tipo == 0)
            return null;

        return Messages.getString("TelegramReport.UnknownMessage") + tipo;
    }

    public static String decodeMsg(String classname) {
        // toDo decode android types using classname
        classname = classname.replaceAll("_layer[0-9]+", "");
        Integer a = androidmsg.get(classname);

        if (a != null)
            return decodeMsg(a);

        return Messages.getString("TelegramReport.UnknownMessage") + classname;
    }

}
