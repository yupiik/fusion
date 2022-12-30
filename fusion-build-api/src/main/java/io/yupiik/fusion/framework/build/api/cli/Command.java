package io.yupiik.fusion.framework.build.api.cli;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.SOURCE;

/**
 * Mark a bean as being registered as a CLI command.
 * This works OOTB with the {@code Launcher} main or you would need to register a custom {@code Args} instance in the container.
 * <p>
 * The command takes a configuration ({@link io.yupiik.fusion.framework.build.api.configuration.RootConfiguration})
 * as parameter which is built from the args and implements {@link Runnable}.
 * <p>
 * Example:
 *
 * <pre>
 * {@code @DefaultScoped}
 * {@code @Command(name = "mycommand", description = "....")}
 * public class MyCommand implements Runnable {
 *     public MyCommand(final MyConf conf) { ... }
 *
 *     {@code @Override}
 *     public void run() { ... }
 *
 *     {@code @RootConfiguration("...")}
 *     public record MyConf(....) {}
 * }
 * </pre>
 */
@Target(TYPE)
@Retention(SOURCE)
public @interface Command {
    /**
     * @return command name (first arg of the CLI in general).
     */
    String name();

    /**
     * @return command description/usage.
     */
    String description();
}
