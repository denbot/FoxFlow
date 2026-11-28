package fixtures;

import bot.den.foxflow.tests.BasicEnum;
import bot.den.foxflow.tests.BasicEnumStateMachine;

public class Trigger {
    void configure() {
        var machine = new BasicEnumStateMachine(BasicEnum.START);
        machine.state(BasicEnum.STATE_A).trigger();
    }
}
