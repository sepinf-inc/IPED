package dpf.sp.gpinf.indexer.util;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class DateUtil {

  // Thread local variable
  private static final ThreadLocal<DateFormat> threadLocal
      = new ThreadLocal<DateFormat>() {
        @Override
        protected DateFormat initialValue() {
          SimpleDateFormat df = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss"); //$NON-NLS-1$
          df.setTimeZone(TimeZone.getTimeZone("GMT")); //$NON-NLS-1$
          return df;
        }
      };

  public static String dateToString(Date date) {
    return threadLocal.get().format(date);
  }

  public static Date stringToDate(String date) throws ParseException {
    return threadLocal.get().parse(date);
  }
}
