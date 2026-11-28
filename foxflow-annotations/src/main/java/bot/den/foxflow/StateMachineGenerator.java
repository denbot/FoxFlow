package bot.den.foxflow;

import bot.den.foxflow.builders.HasClassName;
import bot.den.foxflow.builders.OnlyWriteOnce;
import bot.den.foxflow.builders.TypedBuilder;
import bot.den.foxflow.builders.names.Names;
import bot.den.foxflow.builders.classes.*;
import bot.den.foxflow.builders.classes.rules.RuleBuilder;
import bot.den.foxflow.exceptions.ClassAlreadyExists;
import bot.den.foxflow.exceptions.InvalidSetupException;
import bot.den.foxflow.validator.EnumValidator;
import bot.den.foxflow.validator.RecordValidator;
import bot.den.foxflow.validator.Validator;
import com.palantir.javapoet.TypeSpec;

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
            throw new InvalidSetupException("The StateMachine annotation is only valid on enums and records");
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
        generate(new RuleBuilder(names));
        generate(new PairBuilder(names));
        generate(new TimeBuilder(names));
        generate(new LimitedToBuilder(names));
        generate(new ToBuilder(names));
        generate(new FromBuilder(names));
        generate(new GatedCommandBuilder(names.gatesCommandName(), names.features()));
        generate(new RuntimeCacheBuilder(names));
        generate(new EnabledCacheBuilder(names));
        generate(new StateMachineBuilder(names));
    }

    private <T extends TypedBuilder<TypeSpec> & HasClassName> void generate(T myClass) {
        try {
            this.environment.writeType(
                    myClass.className().packageName(),
                    myClass.build()
            );
        } catch(ClassAlreadyExists e) {
            if(!(myClass instanceof OnlyWriteOnce)) {
                throw e;
            }
        }
    }
}
