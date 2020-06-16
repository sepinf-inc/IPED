package dpf.sp.gpinf.indexer.process.task.regex;

public class RegexHits{
    
    private String hit;
    private long[] offsets = new long[1];
    private int numOffsets = 0;
    
    public RegexHits(String hit) {
        this.hit = hit;
    }

    public String getHit() {
        return hit;
    }
    
    public void addOffset(long offset) {
        if(numOffsets == offsets.length) {
            long[] array = new long[offsets.length * 2];
            System.arraycopy(offsets, 0, array, 0, offsets.length);
            offsets = array;
        }
        offsets[numOffsets++] = offset;
    }

    public long[] getOffsets() {
        if(numOffsets == offsets.length)
            return offsets;
        else {
            long[] array = new long[numOffsets];
            System.arraycopy(offsets, 0, array, 0, array.length);
            return array;
        }
    }
    
    public void addAll(long[] offsets) {
        for(long l : offsets) {
            addOffset(l);
        }
    }
    
    @Override
    public String toString() {
        return hit;
    }
}
