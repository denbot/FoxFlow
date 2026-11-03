package bot.den.foxflow.tests;

import org.wpilib.command2.Command;
import org.wpilib.command2.CommandScheduler;
import org.wpilib.command2.Commands;

public class Actions {
    public static void reset() {
        CommandScheduler.getInstance().cancelAll();
        CommandScheduler.getInstance().run();
    }

    public static Command runOnce(Runnable runnable) {
        return Commands.runOnce(runnable).ignoringDisable(true);
    }

    public static Command run(Runnable runnable) {
        return Commands.run(runnable).ignoringDisable(true);
    }

    public static void poll() {
        CommandScheduler.getInstance().run();
    }
}
