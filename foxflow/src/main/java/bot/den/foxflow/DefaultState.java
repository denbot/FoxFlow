package bot.den.foxflow;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an enum field as the default state for a state machine. Only one field in a given enum
 * may be annotated with {@code @DefaultState}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface DefaultState {
    /**
     * Whether a user-defined state machine is allowed to override this default with its own.
     * When {@code false}, this default is fixed and cannot be replaced. Defaults to {@code true}.
     */
    boolean userCanOverride() default true;
}
