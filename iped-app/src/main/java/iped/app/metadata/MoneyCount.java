package iped.app.metadata;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MoneyCount extends ValueCount implements Comparable<MoneyCount> {

    private static Pattern pattern = Pattern.compile("[\\$\\s\\.\\,]"); //$NON-NLS-1$
    // String val;
    long money;

    MoneyCount(LookupOrd lo, int ord, int count) {
        super(lo, ord, count);
        String val = getVal();
        char centChar = val.charAt(val.length() - 3);
        if (centChar == '.' || centChar == ',')
            val = val.substring(0, val.length() - 3);
        Matcher matcher = pattern.matcher(val);
        money = Long.valueOf(matcher.replaceAll("")); //$NON-NLS-1$
    }

    @Override
    public int compareTo(MoneyCount o) {
        return Long.compare(o.money, this.money);
    }
}
