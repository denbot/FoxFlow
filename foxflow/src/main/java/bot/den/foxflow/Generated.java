package bot.den.foxflow;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The Generated annotation is added on all the FoxFlow generated classes. Since the simple name contains "Generated",
 * code coverage utilities such as JaCoCo will ignore it.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Generated {
}
