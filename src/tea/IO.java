package tea;

public class IO {
    public final NativeRunnable println =new NativeRunnable(System.out::println);

    public final NativeRunnable print = new NativeRunnable(System.out::print);

    public final NativeRunnable eprintln = new NativeRunnable(System.err::println);

    public final NativeRunnable eprint = new NativeRunnable(System.err::print);
    
    public String readln() {
        byte[] b = {};
        
        try {
            b = System.in.readAllBytes();
        } catch (java.io.IOException e) {
            eprintln.run("Error reading input from user -- %s".formatted(e.getLocalizedMessage()));
        }

        return new String(b);
    }
}
