package tea;

import java.util.concurrent.Callable;

public class NativeRunnable {
    private final Func r;

    public interface Func1Arg<T> {
        void run(T t);
    }

    public void run(Object o) {
        r.run(o);
    }

    public void run(String o) {
        r.run(o);
    }

    public NativeRunnable(Func r) {
        this.r = r;
    }

    public static Func makeFunc(Func1Arg<Object> a, Func1Arg<Double> b) {
        return new Func() {
            @Override
            public void run(Object o) {
                a.run(o);
            }

            @Override
            public void run(double d) {
                b.run(d);
            }
        };
    }

    public static Func makeFunc(Func1Arg<Object> a) {
        return new Func() {
            @Override
            public void run(Object o) {
                a.run(o);
            }

            @Override
            public void run(double d) {
                a.run(d);
            }
        };
    }
}
