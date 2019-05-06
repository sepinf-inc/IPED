package dpf.sp.gpinf.carver.api;

public class Hit {

    Signature sig;
    long off;

    public Hit(Signature sig, long off) {
        this.sig = sig;
        this.off = off;
    }

    public long getOffset() {
        return off;
    }

    public Signature getSignature() {
        return sig;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Hit) {
            Hit hit = (Hit) obj;
            return (hit.off == this.off) && (this.sig.getCarverType().getName().equals(hit.getSignature().getCarverType().getName()));
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        return super.toString() + "--Offset:" + this.off;
    }
}

