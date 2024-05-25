package tea;

public class NativeRunnable {
    private final Func r;

    public void run(Object o) {
        r.run(o);
    }

    public void run(String o) {
        r.run(o);
    }

    public NativeRunnable(Func r) {
        this.r = r;
    }
}
