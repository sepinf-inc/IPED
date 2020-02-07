package dpf.sp.gpinf.indexer.util;

import javax.swing.JTabbedPane;

public class SwingUtil {

    public static int getIndexOfTab(JTabbedPane jtab, String tabTitle) {
        for (int i = 0; i < jtab.getTabCount(); i++)
            if (jtab.getTitleAt(i).equals(tabTitle))
                return i;

        return -1;
    }

}
