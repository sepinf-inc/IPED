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

import java.util.Calendar;
import java.util.SimpleTimeZone;

/**
 * Class containing time zone information
 * 
 * @author Orin Eman
 * 
 * 
 */

public class PSTTimeZone {
	PSTTimeZone(byte[] timeZoneData) {
		this.rule = null;
		name = "";

		try {
			int headerLen = (int) PSTObject.convertLittleEndianBytesToLong(timeZoneData, 2, 4);
			int nameLen = 2 * (int) PSTObject.convertLittleEndianBytesToLong(timeZoneData, 6, 8);
			name = new String(timeZoneData, 8, nameLen, "UTF-16LE");
			int ruleOffset = 8 + nameLen;
			int nRules = (int) PSTObject.convertLittleEndianBytesToLong(timeZoneData, ruleOffset, ruleOffset + 2);

			ruleOffset = 4 + headerLen;
			for (int rule = 0; rule < nRules; ++rule) {
				// Is this rule the effective rule?
				int flags = (int) PSTObject.convertLittleEndianBytesToLong(timeZoneData, ruleOffset + 4, ruleOffset + 6);
				if ((flags & 0x0002) != 0) {
					this.rule = new TZRule(timeZoneData, ruleOffset + 6);
					break;
				}
				ruleOffset += 66;
			}
		} catch (Exception e) {
			System.err.printf("Exception reading timezone: %s\n", e.toString());
			e.printStackTrace();
			this.rule = null;
			name = "";
		}
	}

	PSTTimeZone(String name, byte[] timeZoneData) {
		this.name = name;
		this.rule = null;

		try {
			this.rule = new TZRule(new SYSTEMTIME(), timeZoneData, 0);
		} catch (Exception e) {
			System.err.printf("Exception reading timezone: %s\n", e.toString());
			e.printStackTrace();
			this.rule = null;
			name = "";
		}
	}

	public String getName() {
		return name;
	}

	public SimpleTimeZone getSimpleTimeZone() {
		if (simpleTimeZone != null) {
			return simpleTimeZone;
		}

		if (rule.startStandard.wMonth == 0) {
			// A time zone with no daylight savings time
			simpleTimeZone = new SimpleTimeZone((rule.lBias + rule.lStandardBias) * 60 * 1000, name);

			return simpleTimeZone;
		}

		int startMonth = (rule.startDaylight.wMonth - 1) + Calendar.JANUARY;
		int startDayOfMonth = (rule.startDaylight.wDay == 5) ? -1 : ((rule.startDaylight.wDay - 1) * 7) + 1;
		int startDayOfWeek = rule.startDaylight.wDayOfWeek + Calendar.SUNDAY;
		int endMonth = (rule.startStandard.wMonth - 1) + Calendar.JANUARY;
		int endDayOfMonth = (rule.startStandard.wDay == 5) ? -1 : ((rule.startStandard.wDay - 1) * 7) + 1;
		int endDayOfWeek = rule.startStandard.wDayOfWeek + Calendar.SUNDAY;
		int savings = (rule.lStandardBias - rule.lDaylightBias) * 60 * 1000;

		simpleTimeZone = new SimpleTimeZone(-((rule.lBias + rule.lStandardBias) * 60 * 1000), name, startMonth, startDayOfMonth, -startDayOfWeek,
				(((((rule.startDaylight.wHour * 60) + rule.startDaylight.wMinute) * 60) + rule.startDaylight.wSecond) * 1000) + rule.startDaylight.wMilliseconds, endMonth, endDayOfMonth,
				-endDayOfWeek, (((((rule.startStandard.wHour * 60) + rule.startStandard.wMinute) * 60) + rule.startStandard.wSecond) * 1000) + rule.startStandard.wMilliseconds, savings);

		return simpleTimeZone;
	}

	public boolean isEqual(PSTTimeZone rhs) {
		if (name.equalsIgnoreCase(rhs.name)) {
			if (rule.isEqual(rhs.rule)) {
				return true;
			}

			System.err.printf("Warning: different timezones with the same name: %s\n", name);
		}
		return false;
	}

	public SYSTEMTIME getStart() {
		return rule.dtStart;
	}

	public int getBias() {
		return rule.lBias;
	}

	public int getStandardBias() {
		return rule.lStandardBias;
	}

	public int getDaylightBias() {
		return rule.lDaylightBias;
	}

	public SYSTEMTIME getDaylightStart() {
		return rule.startDaylight;
	}

	public SYSTEMTIME getStandardStart() {
		return rule.startStandard;
	}

	public class SYSTEMTIME {

		SYSTEMTIME() {
			wYear = 0;
			wMonth = 0;
			wDayOfWeek = 0;
			wDay = 0;
			wHour = 0;
			wMinute = 0;
			wSecond = 0;
			wMilliseconds = 0;
		}

		SYSTEMTIME(byte[] timeZoneData, int offset) {
			wYear = (short) (PSTObject.convertLittleEndianBytesToLong(timeZoneData, offset, offset + 2) & 0x7FFF);
			wMonth = (short) (PSTObject.convertLittleEndianBytesToLong(timeZoneData, offset + 2, offset + 4) & 0x7FFF);
			wDayOfWeek = (short) (PSTObject.convertLittleEndianBytesToLong(timeZoneData, offset + 4, offset + 6) & 0x7FFF);
			wDay = (short) (PSTObject.convertLittleEndianBytesToLong(timeZoneData, offset + 6, offset + 8) & 0x7FFF);
			wHour = (short) (PSTObject.convertLittleEndianBytesToLong(timeZoneData, offset + 8, offset + 10) & 0x7FFF);
			wMinute = (short) (PSTObject.convertLittleEndianBytesToLong(timeZoneData, offset + 10, offset + 12) & 0x7FFF);
			wSecond = (short) (PSTObject.convertLittleEndianBytesToLong(timeZoneData, offset + 12, offset + 14) & 0x7FFF);
			wMilliseconds = (short) (PSTObject.convertLittleEndianBytesToLong(timeZoneData, offset + 14, offset + 16) & 0x7FFF);
		}

		boolean isEqual(SYSTEMTIME rhs) {
			return wYear == rhs.wYear && wMonth == rhs.wMonth && wDayOfWeek == rhs.wDayOfWeek && wDay == rhs.wDay && wHour == rhs.wHour && wMinute == rhs.wMinute && wSecond == rhs.wSecond
					&& wMilliseconds == rhs.wMilliseconds;
		}

		public short wYear;
		public short wMonth;
		public short wDayOfWeek;
		public short wDay;
		public short wHour;
		public short wMinute;
		public short wSecond;
		public short wMilliseconds;
	}

	/**
	 * A static copy of the UTC time zone, available for others to use
	 */
	public static SimpleTimeZone utcTimeZone = new SimpleTimeZone(0, "UTC");

	private class TZRule {

		TZRule(SYSTEMTIME dtStart, byte[] timeZoneData, int offset) {
			this.dtStart = dtStart;
			InitBiases(timeZoneData, offset);
			@SuppressWarnings("unused")
			short wStandardYear = (short) PSTObject.convertLittleEndianBytesToLong(timeZoneData, offset + 12, offset + 14);
			startStandard = new SYSTEMTIME(timeZoneData, offset + 14);
			@SuppressWarnings("unused")
			short wDaylightYear = (short) PSTObject.convertLittleEndianBytesToLong(timeZoneData, offset + 30, offset + 32);
			startDaylight = new SYSTEMTIME(timeZoneData, offset + 32);
		}

		TZRule(byte[] timeZoneData, int offset) {
			dtStart = new SYSTEMTIME(timeZoneData, offset);
			InitBiases(timeZoneData, offset + 16);
			startStandard = new SYSTEMTIME(timeZoneData, offset + 28);
			startDaylight = new SYSTEMTIME(timeZoneData, offset + 44);
		}

		private void InitBiases(byte[] timeZoneData, int offset) {
			lBias = (int) PSTObject.convertLittleEndianBytesToLong(timeZoneData, offset, offset + 4);
			lStandardBias = (int) PSTObject.convertLittleEndianBytesToLong(timeZoneData, offset + 4, offset + 8);
			lDaylightBias = (int) PSTObject.convertLittleEndianBytesToLong(timeZoneData, offset + 8, offset + 12);
		}

		boolean isEqual(TZRule rhs) {
			return dtStart.isEqual(rhs.dtStart) && lBias == rhs.lBias && lStandardBias == rhs.lStandardBias && lDaylightBias == rhs.lDaylightBias && startStandard.isEqual(rhs.startStandard)
					&& startDaylight.isEqual(rhs.startDaylight);
		}

		SYSTEMTIME dtStart;
		int lBias;
		int lStandardBias;
		int lDaylightBias;
		SYSTEMTIME startStandard;
		SYSTEMTIME startDaylight;
	}

	private String name;
	private TZRule rule;
	private SimpleTimeZone simpleTimeZone = null;
}
