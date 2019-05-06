/**
 * Copyright 2010 Richard Johnson & Orin Eman
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * ---
 *
 * This file is part of java-libpst.
 *
 * java-libpst is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * java-libpst is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with java-libpst.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package com.pff;

//import java.util.Date;
import java.util.HashMap;

/**
 * Class containing recipient information
 * 
 * @author Orin Eman
 * 
 * 
 */
public class PSTRecipient {
	private HashMap<Integer, PSTTable7CItem> details;

	public static final int MAPI_TO = 1;
	public static final int MAPI_CC = 2;
	public static final int MAPI_BCC = 3;

	PSTRecipient(HashMap<Integer, PSTTable7CItem> recipientDetails) {
		details = recipientDetails;
	}

	public String getDisplayName() {
		return getString(0x3001);
	}

	public int getRecipientType() {
		return getInt(0x0c15);
	}

	public String getEmailAddressType() {
		return getString(0x3002);
	}

	public String getEmailAddress() {
		return getString(0x3003);
	}

	public int getRecipientFlags() {
		return getInt(0x5ffd);
	}

	public int getRecipientOrder() {
		return getInt(0x5fdf);
	}

	public String getSmtpAddress() {
		// If the recipient address type is SMTP,
		// we can simply return the recipient address.
		String addressType = getEmailAddressType();
		if (addressType != null && addressType.equalsIgnoreCase("smtp")) {
			String addr = getEmailAddress();
			if (addr != null && addr.length() != 0) {
				return addr;
			}
		}
		// Otherwise, we have to hope the SMTP address is
		// present as the PidTagPrimarySmtpAddress property.
		return getString(0x39FE);
	}

	private String getString(int id) {
		if (details.containsKey(id)) {
			PSTTable7CItem item = details.get(id);
			return item.getStringValue();
		}

		return "";
	}

	/*
	 * private boolean getBoolean(int id) { if ( details.containsKey(id) ) {
	 * PSTTable7CItem item = details.get(id); if ( item.entryValueType == 0x000B
	 * ) { return (item.entryValueReference & 0xFF) == 0 ? false : true; } }
	 * 
	 * return false; }
	 */
	private int getInt(int id) {
		if (details.containsKey(id)) {
			PSTTable7CItem item = details.get(id);
			if (item.entryValueType == 0x0003) {
				return item.entryValueReference;
			}

			if (item.entryValueType == 0x0002) {
				short s = (short) item.entryValueReference;
				return s;
			}
		}

		return 0;
	}

	/*
	 * private Date getDate(int id) { long lDate = 0;
	 * 
	 * if ( details.containsKey(id) ) { PSTTable7CItem item = details.get(id);
	 * if ( item.entryValueType == 0x0040 ) { int high =
	 * (int)PSTObject.convertLittleEndianBytesToLong(item.data, 4, 8); int low =
	 * (int)PSTObject.convertLittleEndianBytesToLong(item.data, 0, 4);
	 * 
	 * return PSTObject.filetimeToDate(high, low); } } return new Date(lDate); }
	 */
}
