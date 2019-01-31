package dpf.sp.gpinf.indexer.process.task.photodna;

import com.sun.jna.Library;

public interface PhotoDNAJNA extends Library{
    
    public long RobustHashInitBuffer(int initialSize);

    public void RobustHashReleaseBuffer(long buffer);

    public void RobustHashResetBuffer(long buffer);

    public int ComputeRobustHash(byte[] imageData, int width, int height, int stride, byte[] hashValue,
            long buffer);

    public int ComputeRobustHashAltColor(byte[] imageData, int width, int height, int stride, int color_type,
            byte[] hashValue, long buffer);

    public int ComputeRobustHashBorder(byte[] imageData, int width, int height, int stride, int color_type,
            int[] border, byte[] hashValue, byte[] hashValueTrimmed, long buffer);

    public int ComputeRobustHashAdvanced(byte[] imageData, int width, int height, int stride, int color_type,
            int sub_x, int sub_y, int sub_w, int sub_h, int[] border, byte[] hashValue, byte[] hashValueTrimmed,
            long buffer);

    public int ComputeShortHash(byte[] imageData, int width, int height, int stride, int color_type,
            byte[] shortHashValue, long buffer);

    public int ComputeCheckArray(byte[] imageData, int width, int height, int stride, int color_type,
            byte[] checkValue, long buffer);

    public int RobustHashVersion();

}
