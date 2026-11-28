package bot.den.foxflow.tests;

import bot.den.foxflow.RobotState;
import org.wpilib.hardware.hal.RobotMode;
import org.wpilib.simulation.DriverStationSim;

public class Util {
    /**
     * This is a self-contained method that handles updating the Driver Station and ensuring the data is propagated in
     * a way that our code and the test can see it.
     *
     * @param state The state to set the driver station to
     */
    public static void setDriverStationState(RobotState state) {
        switch(state) {
            case DISABLED -> {
                DriverStationSim.setEnabled(false);
                DriverStationSim.setRobotMode(RobotMode.UNKNOWN);
            }
            case AUTO -> {
                DriverStationSim.setRobotMode(RobotMode.AUTONOMOUS);
                DriverStationSim.setEnabled(true);
                DriverStationSim.setDsAttached(true);
            }
            case TELEOP -> {
                DriverStationSim.setRobotMode(RobotMode.TELEOPERATED);
                DriverStationSim.setEnabled(true);
                DriverStationSim.setDsAttached(true);
            }
            case TEST -> {
                DriverStationSim.setRobotMode(RobotMode.UTILITY);
                DriverStationSim.setEnabled(true);
                DriverStationSim.setDsAttached(true);
            }
        }

        DriverStationSim.notifyNewData();
    }
}
