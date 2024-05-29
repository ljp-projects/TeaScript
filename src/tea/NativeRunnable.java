package tea;

import java.util.concurrent.Callable;

public class NativeRunnable {
    private final Func r;

    public interface Func1Arg {
        void run(Object t);
    }

    public void run(Object o) {
        r.run(o);
    }

    public void run(String o) {
        r.run(o);
    }

    public void run(double o) {
        r.run(o);
    }

    public NativeRunnable(Func r) {
        this.r = r;
    }
}
