/*
	DBFWriter
	Class for defining a DBF structure and addin data to that structure and 
	finally writing it to an OutputStream.

	This file is part of JavaDBF packege.

	author: anil@linuxense.com
	license: LGPL (http://www.gnu.org/copyleft/lesser.html)

	$Id: DBFWriter.java,v 1.9 2004/03/31 10:57:16 anil Exp $
 */
package com.linuxense.javadbf;

import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Vector;

/**
 * An object of this class can create a DBF file.
 * 
 * Create an object, <br>
 * then define fields by creating DBFField objects and<br>
 * add them to the DBFWriter object<br>
 * add records using the addRecord() method and then<br>
 * call write() method.
 */
public class DBFWriter extends DBFBase {

	/* other class variables */
	DBFHeader header;
	Vector v_records = new Vector();
	int recordCount = 0;
	RandomAccessFile raf = null; /* Open and append records to an existing DBF */
	boolean appendMode = false;

	/**
	 * Creates an empty Object.
	 */
	public DBFWriter() {

		this.header = new DBFHeader();
	}

	/**
	 * Creates a DBFWriter which can append to records to an existing DBF file.
	 * 
	 * @param dbfFile
	 *            . The file passed in shouls be a valid DBF file.
	 * @exception Throws
	 *                DBFException if the passed in file does exist but not a
	 *                valid DBF file, or if an IO error occurs.
	 */
	public DBFWriter(File dbfFile) throws DBFException {

		try {

			this.raf = new RandomAccessFile(dbfFile, "rw");

			/*
			 * before proceeding check whether the passed in File object is an
			 * empty/non-existent file or not.
			 */
			if (!dbfFile.exists() || dbfFile.length() == 0) {

				this.header = new DBFHeader();
				return;
			}

			header = new DBFHeader();
			this.header.read(raf);

			/* position file pointer at the end of the raf */
			this.raf.seek(this.raf.length() - 1 /*
												 * to ignore the END_OF_DATA
												 * byte at EoF
												 */);
		} catch (FileNotFoundException e) {

			throw new DBFException("Specified file is not found. " + e.getMessage());
		} catch (IOException e) {

			throw new DBFException(e.getMessage() + " while reading header");
		}

		this.recordCount = this.header.numberOfRecords;
	}

	/**
	 * Sets fields.
	 */
	public void setFields(DBFField[] fields) throws DBFException {

		if (this.header.fieldArray != null) {

			throw new DBFException("Fields has already been set");
		}

		if (fields == null || fields.length == 0) {

			throw new DBFException("Should have at least one field");
		}

		for (int i = 0; i < fields.length; i++) {

			if (fields[i] == null) {

				throw new DBFException("Field " + (i + 1) + " is null");
			}
		}

		this.header.fieldArray = fields;

		try {

			if (this.raf != null && this.raf.length() == 0) {

				/*
				 * this is a new/non-existent file. So write header before
				 * proceeding
				 */
				this.header.write(this.raf);
			}
		} catch (IOException e) {

			throw new DBFException("Error accesing file");
		}
	}

	/**
	 * Add a record.
	 */
	public void addRecord(Object[] values) throws DBFException {

		if (this.header.fieldArray == null) {

			throw new DBFException("Fields should be set before adding records");
		}

		if (values == null) {

			throw new DBFException("Null cannot be added as row");
		}

		if (values.length != this.header.fieldArray.length) {

			throw new DBFException("Invalid record. Invalid number of fields in row");
		}

		for (int i = 0; i < this.header.fieldArray.length; i++) {

			if (values[i] == null) {

				continue;
			}

			switch (this.header.fieldArray[i].getDataType()) {

			case 'C':
				if (!(values[i] instanceof String)) {
					throw new DBFException("Invalid value for field " + i);
				}
				break;

			case 'L':
				if (!(values[i] instanceof Boolean)) {
					throw new DBFException("Invalid value for field " + i);
				}
				break;

			case 'N':
				if (!(values[i] instanceof Double)) {
					throw new DBFException("Invalid value for field " + i);
				}
				break;

			case 'D':
				if (!(values[i] instanceof Date)) {
					throw new DBFException("Invalid value for field " + i);
				}
				break;

			case 'F':
				if (!(values[i] instanceof Double)) {

					throw new DBFException("Invalid value for field " + i);
				}
				break;
			}
		}

		if (this.raf == null) {

			v_records.addElement(values);
		} else {

			try {

				writeRecord(this.raf, values);
				this.recordCount++;
			} catch (IOException e) {

				throw new DBFException("Error occured while writing record. " + e.getMessage());
			}
		}
	}

	/**
	 * Writes the set data to the OutputStream.
	 */
	public void write(OutputStream out) throws DBFException {

		try {

			if (this.raf == null) {

				DataOutputStream outStream = new DataOutputStream(out);

				this.header.numberOfRecords = v_records.size();
				this.header.write(outStream);

				/* Now write all the records */
				int t_recCount = v_records.size();
				for (int i = 0; i < t_recCount; i++) { /*
														 * iterate through
														 * records
														 */

					Object[] t_values = (Object[]) v_records.elementAt(i);

					writeRecord(outStream, t_values);
				}

				outStream.write(END_OF_DATA);
				outStream.flush();
			} else {

				/*
				 * everything is written already. just update the header for
				 * record count and the END_OF_DATA mark
				 */
				this.header.numberOfRecords = this.recordCount;
				this.raf.seek(0);
				this.header.write(this.raf);
				this.raf.seek(raf.length());
				this.raf.writeByte(END_OF_DATA);
				this.raf.close();
			}

		} catch (IOException e) {

			throw new DBFException(e.getMessage());
		}
	}

	public void write() throws DBFException {

		this.write(null);
	}

	private void writeRecord(DataOutput dataOutput, Object[] objectArray) throws IOException {

		dataOutput.write((byte) ' ');
		for (int j = 0; j < this.header.fieldArray.length; j++) { /*
																 * iterate
																 * throught
																 * fields
																 */

			switch (this.header.fieldArray[j].getDataType()) {

			case 'C':
				if (objectArray[j] != null) {

					String str_value = objectArray[j].toString();
					dataOutput.write(Utils.textPadding(str_value, characterSetName, this.header.fieldArray[j].getFieldLength()));
				} else {

					dataOutput.write(Utils.textPadding("", this.characterSetName, this.header.fieldArray[j].getFieldLength()));
				}

				break;

			case 'D':
				if (objectArray[j] != null) {

					GregorianCalendar calendar = new GregorianCalendar();
					calendar.setTime((Date) objectArray[j]);
					StringBuffer t_sb = new StringBuffer();
					dataOutput.write(String.valueOf(calendar.get(Calendar.YEAR)).getBytes());
					dataOutput.write(Utils.textPadding(String.valueOf(calendar.get(Calendar.MONTH) + 1), this.characterSetName, 2, Utils.ALIGN_RIGHT, (byte) '0'));
					dataOutput.write(Utils.textPadding(String.valueOf(calendar.get(Calendar.DAY_OF_MONTH)), this.characterSetName, 2, Utils.ALIGN_RIGHT, (byte) '0'));
				} else {

					dataOutput.write("        ".getBytes());
				}

				break;

			case 'F':

				if (objectArray[j] != null) {

					dataOutput.write(Utils.doubleFormating((Double) objectArray[j], this.characterSetName, this.header.fieldArray[j].getFieldLength(), this.header.fieldArray[j].getDecimalCount()));
				} else {

					dataOutput.write(Utils.textPadding("?", this.characterSetName, this.header.fieldArray[j].getFieldLength(), Utils.ALIGN_RIGHT));
				}

				break;

			case 'N':

				if (objectArray[j] != null) {

					dataOutput.write(Utils.doubleFormating((Double) objectArray[j], this.characterSetName, this.header.fieldArray[j].getFieldLength(), this.header.fieldArray[j].getDecimalCount()));
				} else {

					dataOutput.write(Utils.textPadding("?", this.characterSetName, this.header.fieldArray[j].getFieldLength(), Utils.ALIGN_RIGHT));
				}

				break;
			case 'L':

				if (objectArray[j] != null) {

					if ((Boolean) objectArray[j] == Boolean.TRUE) {

						dataOutput.write((byte) 'T');
					} else {

						dataOutput.write((byte) 'F');
					}
				} else {

					dataOutput.write((byte) '?');
				}

				break;

			case 'M':

				break;

			default:
				throw new DBFException("Unknown field type " + this.header.fieldArray[j].getDataType());
			}
		} /* iterating through the fields */
	}
}
