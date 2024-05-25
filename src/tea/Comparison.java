package tea;

// This is the most stupid standard class ever because I am too lazy to find a way to do it in the compiled bytecode
public class Comparison {
    public static boolean dblLt(double lhs, double rhs) {
        return lhs < rhs;
    }

    public static boolean dblGt(double lhs, double rhs) {
        return lhs > rhs;
    }

    public static boolean dblPos(double d) {
        return Math.abs(d) == d;
    }
}