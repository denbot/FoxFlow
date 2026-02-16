package bot.den.foxflow.builders;

/**
 * This class probably won't be used in its generic typed state. It's mostly just to encourage the same API for all the
 * generators.
 *
 * @param <T> The MethodSpec, TypeSpec, etc. that this class builds.
 */
public interface TypedBuilder<T> {
    T build();
}
