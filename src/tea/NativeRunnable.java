package tea;

public class NativeRunnable {
    private final Func r;

    public java.lang.Object run(Object o) {
        r.run(o);

        return null;
    }

    public NativeRunnable(Func r) {
        this.r = r;
    }
}
