<!--
  GENERATED FILE - DO NOT EDIT.
  Template: fusion-build-internal/src/main/resources/agents/module/fusion-cli.md
  Refresh with: mvn install -pl fusion-build-internal (any full build does it too).
-->
# Fusion :: CLI (`fusion-cli`)

Command line application support with compile time generated commands.

Classes annotated with `@Command` (from `fusion-build-api`) become executable CLI commands with their
parameters mapped from a `@RootConfiguration` record.

## Declaring a command

```java
@Command(name = "my-command", description = "A super command.")
public class MyCommand implements Runnable {
    private final Conf conf;
    private final JsonMapper jsonMapper; // any other constructor parameter is an injected bean

    public MyCommand(final Conf conf, final JsonMapper jsonMapper) {
        this.conf = conf;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void run() {
        // command implementation
    }

    @RootConfiguration("my-command") // options become --my-command-xxx (use prefix "-" for plain --xxx)
    public record Conf(String name) {}
}
```

- The command must be a `Runnable`; its FIRST constructor parameter must be the configuration record.
- Constructor injections are supported after the configuration parameter: any simple-type bean can be
  injected (no lists - wrap a list in a dedicated bean if needed).

## Launching

- `io.yupiik.fusion.framework.api.main.CliLauncher` (in `fusion-api`) is the PREFERRED main: unlike plain
  `Launcher` it skips the first argument (the command name), so `my-app my-command --my-command-name foo`
  maps the remaining args to the command configuration.
- Without the launcher integration, call `io.yupiik.fusion.cli.CliAwaiter` yourself and register an
  `Args` instance in the container.

## Module rules

- Command parameter binding reuses the configuration subsystem: options are documented/validated like any
  `@RootConfiguration`.
- Test commands with `@FusionCLITest` from `fusion-testing` (captures `Stdout`/`Stderr`).



## Working in this module

- Build it: `mvn install -pl fusion-cli -am` (from the repository root).
- The root [AGENTS.md](/AGENTS.md) holds the global rules: reflectionless design, license headers, parallel JUnit 5 tests, ASCII-only.

