package dpf.mt.gpinf.registro.model;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public class Registry {
    byte[] fileHeader = new byte[4096];

    public static void main(String[] args) throws IOException {

        RegistryFile rf = new RegistryFile(
                new File("/mnt/Ferramentas/JavaCode/BaseTeste/registro/baseteste/patrick.pdb/NTUSER.DAT"));

        rf.load();

        HiveCell c = rf.getRootCell();
        KeyNode k = (KeyNode) c.getCellContent();
        System.out.println("----------------");
        System.out.println(k.getKeyName());

        KeyNode kf = rf.findKeyNode("/Control Panel/Desktop");
        if (kf != null) {
            System.out.println("Papel de parede:" + kf.getValue("WallPaper").getValueDataAsString());
        }

        kf = rf.findKeyNode("/Identities");
        if (kf != null) {
            ArrayList<KeyNode> identities = kf.getSubKeys();
            for (int i = 0; i < identities.size(); i++) {
                System.out.println("Nome do usu�rio:" + identities.get(i).getValue("Username").getValueDataAsString());
                System.out.println("ID do usu�rio:" + identities.get(i).getValue("User ID").getValueDataAsString());
            }
        }

    }

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
