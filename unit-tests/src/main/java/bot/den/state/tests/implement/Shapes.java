package bot.den.state.tests.implement;

import bot.den.state.LimitsTypeTransitions;

public enum Shapes implements MyInterface, LimitsTypeTransitions<Shapes> {
    SQUARE, CIRCLE;

    @Override
    public boolean canTransitionType(Object other) {
        // Squares hate strawberry
        boolean hater = this.equals(SQUARE) && other instanceof Flavors flavor && flavor.equals(Flavors.Strawberry);
        return ! hater;
    }
}
