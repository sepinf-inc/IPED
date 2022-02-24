package dpf.mt.gpinf.registro.model;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public class Registry {
    byte[] fileHeader = new byte[4096];

    public static void montaArvore(KeyNode k, int level) {
        String j = "--";
        for (int i = 0; i < level; i++) {
            System.out.print(j);
        }
        System.out.println(k.getKeyName());

        KeyValue[] vs = k.getValues();
        if (vs != null) {
            for (int i = 0; i < vs.length; i++) {
                for (int vi = 0; vi < level; vi++) {
                    System.out.print(j);
                }
                System.out.print("++");
                System.out.println(vs[i].getValueName() + "==" + vs[i].getValueDataAsString());
            }
        }

        ArrayList<KeyNode> subs = k.getSubKeys();
        if (subs != null) {
            for (int i = 0; i < subs.size(); i++) {
                montaArvore(subs.get(i), level + 1);
            }
        }
    }

}
