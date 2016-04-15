package gpinf.die;

import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.List;

public abstract class AbstractDie {

  public abstract List<Float> extractFeatures(BufferedImage img);

  public abstract int getExpectedImageSize();

  public static final AbstractDie loadImplementation(File file) {
    try {
      DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)));
      int len = in.readInt();
      byte[] b = new byte[len];
      in.read(b, 0, len);
      in.close();

      DieClassLoader classLoader = new DieClassLoader();
      Class<?> dieClass = classLoader.loadClass(b);
      return (AbstractDie) dieClass.newInstance();
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }
}

class DieClassLoader extends ClassLoader {

  public Class<?> loadClass(byte[] classData) throws ClassNotFoundException {
    return defineClass("gpinf.die.Die", classData, 0, classData.length);
  }
}
