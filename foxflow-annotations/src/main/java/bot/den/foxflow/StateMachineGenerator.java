package bot.den.foxflow;

import bot.den.foxflow.builders.names.Names;
import bot.den.foxflow.builders.classes.*;
import bot.den.foxflow.builders.classes.rules.RulesBuilder;
import bot.den.foxflow.validator.EnumValidator;
import bot.den.foxflow.validator.RecordValidator;
import bot.den.foxflow.validator.Validator;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ElementKind;

public class StateMachineGenerator {
    final ProcessingEnvironment processingEnv;
    private final Environment environment;

    private final Validator validator;

    private final Names names;

    public StateMachineGenerator(Environment environment) {
        this.environment = environment;
        this.processingEnv = environment.processingEnvironment();
        var element = environment.element();

        if (element.getKind() == ElementKind.ENUM) {
            this.validator = new EnumValidator(environment);
        } else if (element.getKind() == ElementKind.RECORD) {
            this.validator = new RecordValidator(environment);
        } else {
            throw new RuntimeException("The StateMachine annotation is only valid on enums and records");
        }

        this.names = new Names(
                environment,
                validator
        );
    }

    public void generate() {
        if (validator instanceof RecordValidator recordValidator) {
            for (var type : recordValidator.typesToWrite) {
                this.environment.writeType(type.getFirst(), type.getSecond());
            }
        }

        // Rules class
        this.environment.writeType(
                names.rules().name().packageName(),
                new RulesBuilder(names).build()
        );

        // Pair class
        this.environment.writeType(
                names.pairName().packageName(),
                new PairBuilder(names).build()
        );

        // Time class
        this.environment.writeType(
                names.timeName().packageName(),
                new TimeBuilder(names).build()
        );

        // LimitedTo class
        this.environment.writeType(
                names.limitedToClassName().packageName(),
                new LimitedToBuilder(names).build()
        );

        // To class
        this.environment.writeType(
                names.toClassName().packageName(),
                new ToBuilder(names).build()
        );

        // From class
        this.environment.writeType(
                names.fromClassName().packageName(),
                new FromBuilder(names).build()
        );

        var stateMachineBuilder = new StateMachineBuilder(names);

        this.environment.writeType(
                names.stateMachineClassName().packageName(),
                stateMachineBuilder.build()
        );
    }
}
