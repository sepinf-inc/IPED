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

/*
 import java.text.SimpleDateFormat;
 /**/

import java.util.Calendar;
import java.util.Date;
import java.util.SimpleTimeZone;

/**
 * Class containing recurrence information for a recurring appointment
 * 
 * @author Orin Eman
 * 
 * 
 */

public class PSTAppointmentRecurrence {

	// Access methods

	public short getExceptionCount() {
		return ExceptionCount;
	}

	public PSTAppointmentException getException(int i) {
		if (i < 0 || i >= ExceptionCount) {
			return null;
		}
		return Exceptions[i];
	}

	public short getCalendarType() {
		return CalendarType;
	}

	public short getPatternType() {
		return PatternType;
	}

	public int getPeriod() {
		return Period;
	}

	public int getPatternSpecific() {
		return PatternSpecific;
	}

	public int getFirstDOW() {
		return FirstDOW;
	}

	public int getPatternSpecificNth() {
		return PatternSpecificNth;
	}

	public int getFirstDateTime() {
		return FirstDateTime;
	}

	public int getEndType() {
		return EndType;
	}

	public int getOccurrenceCount() {
		return OccurrenceCount;
	}

	public int getEndDate() {
		return EndDate;
	}

	public int getStartTimeOffset() {
		return StartTimeOffset;
	}

	public PSTTimeZone getTimeZone() {
		return RecurrenceTimeZone;
	}

	public int getRecurFrequency() {
		return RecurFrequency;
	}

	public int getSlidingFlag() {
		return SlidingFlag;
	}

	public int getStartDate() {
		return StartDate;
	}

	public int getEndTimeOffset() {
		return EndTimeOffset;
	}

	public PSTAppointmentRecurrence(byte[] recurrencePattern, PSTAppointment appt, PSTTimeZone tz) {
		RecurrenceTimeZone = tz;
		SimpleTimeZone stz = RecurrenceTimeZone.getSimpleTimeZone();

		// Read the structure
		RecurFrequency = (short) PSTObject.convertLittleEndianBytesToLong(recurrencePattern, 4, 6);
		PatternType = (short) PSTObject.convertLittleEndianBytesToLong(recurrencePattern, 6, 8);
		CalendarType = (short) PSTObject.convertLittleEndianBytesToLong(recurrencePattern, 8, 10);
		FirstDateTime = (int) PSTObject.convertLittleEndianBytesToLong(recurrencePattern, 10, 14);
		Period = (int) PSTObject.convertLittleEndianBytesToLong(recurrencePattern, 14, 18);
		SlidingFlag = (int) PSTObject.convertLittleEndianBytesToLong(recurrencePattern, 18, 22);
		int offset = 22;
		if (PatternType != 0) {
			PatternSpecific = (int) PSTObject.convertLittleEndianBytesToLong(recurrencePattern, offset, offset + 4);
			offset += 4;
			if (PatternType == 0x0003 || PatternType == 0x000B) {
				PatternSpecificNth = (int) PSTObject.convertLittleEndianBytesToLong(recurrencePattern, offset, offset + 4);
				offset += 4;
			}
		}
		EndType = (int) PSTObject.convertLittleEndianBytesToLong(recurrencePattern, offset, offset + 4);
		offset += 4;
		OccurrenceCount = (int) PSTObject.convertLittleEndianBytesToLong(recurrencePattern, offset, offset + 4);
		offset += 4;
		FirstDOW = (int) PSTObject.convertLittleEndianBytesToLong(recurrencePattern, offset, offset + 4);
		offset += 4;

		DeletedInstanceCount = (int) PSTObject.convertLittleEndianBytesToLong(recurrencePattern, offset, offset + 4);
		offset += 4;
		DeletedInstanceDates = new Calendar[DeletedInstanceCount];
		for (int i = 0; i < DeletedInstanceCount; ++i) {
			DeletedInstanceDates[i] = PSTObject.apptTimeToUTC((int) PSTObject.convertLittleEndianBytesToLong(recurrencePattern, offset, offset + 4), RecurrenceTimeZone);
			offset += 4;
			/*
			 * SimpleDateFormat f = new SimpleDateFormat("yyyyMMdd'T'HHmmss");
			 * f.setTimeZone(RecurrenceTimeZone.getSimpleTimeZone());
			 * System.out.printf("DeletedInstanceDates[%d]: %s\n", i,
			 * f.format(DeletedInstanceDates[i].getTime())); /*
			 */
		}

		ModifiedInstanceCount = (int) PSTObject.convertLittleEndianBytesToLong(recurrencePattern, offset, offset + 4);
		offset += 4;
		ModifiedInstanceDates = new Calendar[ModifiedInstanceCount];
		for (int i = 0; i < ModifiedInstanceCount; ++i) {
			ModifiedInstanceDates[i] = PSTObject.apptTimeToUTC((int) PSTObject.convertLittleEndianBytesToLong(recurrencePattern, offset, offset + 4), RecurrenceTimeZone);
			offset += 4;
			/*
			 * SimpleDateFormat f = new SimpleDateFormat("yyyyMMdd'T'HHmmss");
			 * f.setTimeZone(RecurrenceTimeZone.getSimpleTimeZone());
			 * System.out.printf("ModifiedInstanceDates[%d]: %s\n", i,
			 * f.format(ModifiedInstanceDates[i].getTime())); /*
			 */
		}

		StartDate = (int) PSTObject.convertLittleEndianBytesToLong(recurrencePattern, offset, offset + 4);
		offset += 4;
		EndDate = (int) PSTObject.convertLittleEndianBytesToLong(recurrencePattern, offset, offset + 4);
		offset += 4 + 4; // Skip ReaderVersion2

		writerVersion2 = (int) PSTObject.convertLittleEndianBytesToLong(recurrencePattern, offset, offset + 4);
		offset += 4;

		StartTimeOffset = (int) PSTObject.convertLittleEndianBytesToLong(recurrencePattern, offset, offset + 4);
		offset += 4;
		EndTimeOffset = (int) PSTObject.convertLittleEndianBytesToLong(recurrencePattern, offset, offset + 4);
		offset += 4;
		ExceptionCount = (short) PSTObject.convertLittleEndianBytesToLong(recurrencePattern, offset, offset + 2);
		offset += 2;

		// Read exceptions
		Exceptions = new PSTAppointmentException[ExceptionCount];
		for (int i = 0; i < ExceptionCount; ++i) {
			Exceptions[i] = new PSTAppointmentException(recurrencePattern, offset, writerVersion2, appt);
			offset += Exceptions[i].getLength();
		}

		if ((offset + 4) <= recurrencePattern.length) {
			int ReservedBlock1Size = (int) PSTObject.convertLittleEndianBytesToLong(recurrencePattern, offset, offset + 4);
			offset += 4 + (ReservedBlock1Size * 4);
		}

		// Read extended exception info
		for (int i = 0; i < ExceptionCount; ++i) {
			Exceptions[i].ExtendedException(recurrencePattern, offset);
			offset += Exceptions[i].getExtendedLength();
			/*
			 * Calendar c =
			 * PSTObject.apptTimeToUTC(Exceptions[i].getStartDateTime(),
			 * RecurrenceTimeZone);
			 * System.out.printf("Exception[%d] start: %s\n", i,
			 * FormatUTC(c.getTime())); c =
			 * PSTObject.apptTimeToUTC(Exceptions[i].getEndDateTime(),
			 * RecurrenceTimeZone); System.out.printf("Exception[%d] end: %s\n",
			 * i, FormatUTC(c.getTime())); c =
			 * PSTObject.apptTimeToUTC(Exceptions[i].getOriginalStartDate(),
			 * RecurrenceTimeZone);
			 * System.out.printf("Exception[%d] original start: %s\n", i,
			 * FormatUTC(c.getTime())); /*
			 */
		}
		// Ignore any extra data - see
		// http://msdn.microsoft.com/en-us/library/cc979209(office.12).aspx

		// Get attachments, if any
		PSTAttachment[] attachments = new PSTAttachment[appt.getNumberOfAttachments()];
		for (int i = 0; i < attachments.length; ++i) {
			try {
				attachments[i] = appt.getAttachment(i);
			} catch (Exception e) {
				e.printStackTrace();
				attachments[i] = null;
			}
		}

		PSTAppointment embeddedMessage = null;
		for (int i = 0; i < ExceptionCount; ++i) {
			try {
				// Match up an attachment to this exception...
				for (int iAttachment = 0; iAttachment < attachments.length; ++iAttachment) {
					if (attachments[iAttachment] != null) {
						PSTMessage message = attachments[iAttachment].getEmbeddedPSTMessage();
						if (!(message instanceof PSTAppointment)) {
							continue;
						}
						embeddedMessage = (PSTAppointment) message;
						Date replaceTime = embeddedMessage.getRecurrenceBase();
						/*
						 * SimpleDateFormat f = new
						 * SimpleDateFormat("yyyyMMdd'T'HHmmss");
						 * f.setTimeZone(stz);
						 * System.out.printf("Attachment[%d] time: %s\n",
						 * iAttachment, f.format(replaceTime)); /*
						 */
						Calendar c = Calendar.getInstance(stz);
						c.setTime(replaceTime);
						if (c.get(Calendar.YEAR) == ModifiedInstanceDates[i].get(Calendar.YEAR) && c.get(Calendar.MONTH) == ModifiedInstanceDates[i].get(Calendar.MONTH)
								&& c.get(Calendar.YEAR) == ModifiedInstanceDates[i].get(Calendar.YEAR)) {
							/*
							 * System.out.println("\tEmbedded Message matched");
							 * /*
							 */

							Exceptions[i].setEmbeddedMessage(embeddedMessage);
							break;
						}
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		attachments = null;
	}

	/*
	 * private String FormatUTC(Date date) { SimpleDateFormat f = new
	 * SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'");
	 * f.setTimeZone(PSTTimeZone.utcTimeZone); return f.format(date); } /*
	 */

	private short RecurFrequency;
	private short PatternType;
	private short CalendarType;
	private int FirstDateTime;
	private int Period;
	private int SlidingFlag;
	private int PatternSpecific;
	private int PatternSpecificNth;
	private int EndType;
	private int OccurrenceCount;
	private int FirstDOW;
	private int DeletedInstanceCount;
	private Calendar[] DeletedInstanceDates = null;
	private int ModifiedInstanceCount;
	private Calendar[] ModifiedInstanceDates = null;
	private int StartDate;
	private int EndDate;
	// private int readerVersion2;
	private int writerVersion2;
	private int StartTimeOffset;
	private int EndTimeOffset;
	private short ExceptionCount;
	private PSTAppointmentException[] Exceptions = null;
	private PSTTimeZone RecurrenceTimeZone = null;
}
