package dpf.sp.gpinf.indexer.util;

import java.util.Date;

public class TimeConverter {
	/**
	 * Converts a 64-bit NTFS time value (number of 100-nanosecond intervals
	 * since January 1, 1601 UTC) to a Java time value (number of milliseconds
	 * since January 1, 1970 UTC.)
	 * 
	 * @param filetime
	 *            the FILETIME value.
	 * @return the number of milliseconds since 1970.
	 */
	private static long filetimeToMillis(long filetime) {
		// Move the starting epoch from 01/01/1601 to 01/01/1970.
		filetime -= 116444736000000000L;

		// Now convert the time into milliseconds, rather than 100-nanosecond units.
		if (filetime < 0) { 
			filetime = -1 - ((-filetime - 1) / 10000);
			filetime = 0;
		} else
			filetime = filetime / 10000;
		
		return filetime;
	}

	/**
	 * Converts a 64-bit NTFS time value (number of 100-nanosecond intervals
	 * since January 1, 1601 UTC) to a Java {@link Date}.
	 * 
	 * @param filetime
	 *            the FILETIME value.
	 * @return the Java date.
	 */
	public static Date fileTimeToDate(long filetime) {
		return new Date(filetimeToMillis(filetime));
	}

	public static Date fileTimeToDate(long[] filetime) {
		long milis = filetime[0] | filetime[1] << 32;
		return new Date(filetimeToMillis(milis));
	}
	
	public static Date systemTimeToDate(long time){
		return new Date(time/1000 -11644473600000L);
	}
	
	public static Date unixTimeToDate(long time){
		return new Date(time * 1000);
	}
	
	public static Date PRTimeToDate(long time){
		return new Date(time / 1000);
	}

}
