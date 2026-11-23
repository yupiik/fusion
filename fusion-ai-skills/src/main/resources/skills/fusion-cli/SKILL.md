---
name: fusion-cli
description: How to write and run Fusion CLI applications: @Command Runnable beans, @RootConfiguration parameter records (with the @Property defaultValue-is-a-Java-literal gotcha), arg binding (--member value), and booting via Launcher/CliLauncher which auto-discovers commands as CliCommand so a CliAwaiter runs them. Use when authoring a new command, a standalone executable jar with a main, or debugging command dispatch.
---

# Fusion CLI commands

A Fusion CLI command is an ordinary bean running the framework, dispatched by name from the first command
line token.

## The command pattern

```java
@Command(name = "install", description = "What the command does.")
public class InstallCommand implements Runnable {
    private final Conf conf;

    public InstallCommand(final Conf conf) {   // config record injected by the generated bean
        this.conf = conf;
    }

    @Override
    public void run() {
        // do work using conf
    }

    @RootConfiguration("-")                    // "-" = no config prefix
    public record Conf(
        @Property(required = true, documentation = "Which thing to act on.") String name,
        @Property(defaultValue = "\"local\"", documentation = "Scope.") String scope,
        @Property(defaultValue = "false", documentation = "Overwrite.") boolean force) {
    }
}
```

Key points:
- The class implements `Runnable` and takes its `@RootConfiguration` record in the constructor; the
  annotation processor generates compact command/bean registrations and configuration binding code
  that builds the record from runtime configuration, not a per-record factory class.
- Statement order REQUIREMENT: `@Property.defaultValue` is a Java expression emitted verbatim (see
  `fusion-annotation-processing` skill). String defaults need escaped quotes: `defaultValue = "\"local\""`.
  Required-but-no-default params must be declared `required = true`.
- Long `documentation` strings can be multi-segment javadoc text; keep `@Property` values ASCII and avoid
  unescaped `.md`/asciidoc gotchas.

For manual configuration construction, keep module generation enabled and configure
`-Afusion.moduleFqn=com.acme.FusionModule` in the configuration record's package.
`FusionModule.InstallCommand$Conf$FusionConfigurationFactory()` returns a typed
`BiFunction<Configuration, Function<String, String>, InstallCommand.Conf>`; apply it to the runtime
configuration and diagnostic key mapper (often `Function.identity()`).
The public root-module method is stable even if its implementation overflows into a binding holder.
Do not instantiate the removed `Xxx$FusionConfigurationFactory` class or depend on `FusionBindings_*`
owner names; normally command dispatch handles configuration and lifecycle for you.
See https://www.yupiik.io/fusion/tooling.html#manual-binding-lookup for package and module-generation boundaries.

## Arg binding and invocation

Args bind to config record members with a `--` prefix token then value: `install --scope global --name foo`.
Bare tokens (the command path segments) are ignored, and an option value which looks like an option is
never consumed. Every dash-led option requires a value (`--help` is skipped); `--no-install-force` is
the only implicit-value form and binds `false` (`no.install.force=false` too); use
`--install-force=false` for an explicit value. The framework resolves the leading tokens as the command
name (longest match wins), constructs the config from the remaining options, and runs the `Runnable`.

## Booting

`CliLauncher` (in `fusion-api`, `io.yupiik.fusion.framework.api.main`) extends `Launcher` and IS the entry
point for CLI apps. Its `main(String...)` boots the container; `fusion-cli` registers a `CliAwaiter`
(an `Awaiter` bean) that resolves and runs the command, then the launcher closes when done. A consumer module
just needs fusion-cli on the runtime classpath and calls `CliLauncher.main(args)` (or extends
`CliLauncher`). For a shaded executable jar, set `Main-Class` to a tiny class that delegates to
`CliLauncher.main(args)`.

## Resolver internals

`CliCommandResolver` (in `fusion-cli`, `io.yupiik.fusion.cli.internal`) maps the first arg token to a command
or a sub-command group. Auto-discovered `CliCommand<? extends Runnable>` are gathered from the running
container. Debug dispatch/binding issues there first.

## Distributing an executable

- Bundle `fusion-build-api` + `fusion-processor` (provided) and `fusion-api` + `fusion-cli` (compile) into
  the module.
- Shade into a one-jar with `Main-Class` set. Keep it runtime-dependency-light.
<!-- generated from fusion-build-internal src/main/resources/agents/skills/fusion-cli/SKILL.md - do not edit -->
