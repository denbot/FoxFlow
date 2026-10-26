package bot.den.foxflow.tests;

public class Actions {
    private Actions() {}

    public static void reset() {}

    public static Runnable runOnce(Runnable runnable) {
        return runnable;
    }

    public static Runnable run(Runnable runnable) {
        return runnable;
    }

    public static void poll() {}
}
