package iped.parsers.threema;

import com.dd.plist.NSDictionary;
import com.dd.plist.NSObject;
import com.dd.plist.PropertyListParser;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

public class ThreemaAccount extends ThreemaContact {

    private boolean isUnknown = false;

    public ThreemaAccount() {
        super(null, null, null, null, null);
        setUnknown(true);
    }

    public ThreemaAccount(String firstName, String lastName, String nickName, String identity, String deviceID) {
        super(firstName, lastName, nickName, identity, deviceID);
    }

    public String getTitle() {
        return "Threema Account: " + getFullId(); //$NON-NLS-1$
    }

    public static ThreemaAccount getFromIOSPlist(InputStream is) {
        try {
            NSDictionary rootDict = (NSDictionary) PropertyListParser.parse(is);
            NSObject value = rootDict.get("PushFromName");

            if (value == null) {
                return null;
            }

            String strPushName = value.toString();

            value = rootDict.get("Threema device ID");

            if (value == null) {
                value = rootDict.get("SentryAppDevice");
            }

            if (value == null) {
                return new ThreemaAccount(null, null, strPushName, null, "missing device info");
            } else {
                return new ThreemaAccount(null, null, strPushName, null, value.toString());
            }

        } catch (Exception e) {
            return null;
        }
    }

    public static void main(String[] args) {
        try (FileInputStream fis = new FileInputStream("F:\\group.ch.threema.work.plist")) {
            ThreemaAccount a = getFromIOSPlist(fis);
            assert a != null;
            System.out.println(a.getFullId());
            System.out.println(a.getFirstName());
            System.out.println(a.getLastName());
        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public boolean isUnknown() {
        return isUnknown;
    }

    public void setUnknown(boolean isUnknown) {
        this.isUnknown = isUnknown;
    }

}
