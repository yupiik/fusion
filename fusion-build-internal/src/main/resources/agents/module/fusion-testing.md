# {{{name}}} (`{{{artifactId}}}`)

{{{description}}}

```java
@FusionSupport // starts/stops a container around the test class (@MonoFusionSupport: one per JVM, faster)
class MyTest {
    @Test
    void run(@Fusion final MyService service) { // beans are injected as test method parameters
        // assertions on service
    }
}
```

## Entry points

- `io.yupiik.fusion.testing.FusionSupport`: JUnit 5 extension starting/stopping a container per test class.
- `io.yupiik.fusion.testing.MonoFusionSupport`: single shared container for the whole surefire execution (faster).
- `io.yupiik.fusion.testing.Fusion`: parameter injection of beans in test methods.
- `io.yupiik.fusion.testing.launcher.FusionCLITest` + `Stdout`/`Stderr`: run a CLI command and capture output.
- `io.yupiik.fusion.testing.task.Task` / `TaskResult`: run logic around the container lifecycle.
- `io.yupiik.fusion.testing.assertion.Asserts` / `JsonAsserts`: assertion helpers.
- `io.yupiik.fusion.testing.module.TestingModule`: programmatic bean replacement/addition for tests.

## Module rules

- This module is consumers' test-facing API: keep it JUnit 5 only, no other test framework dependency.
- Everything must be safe under parallel execution (it is the default in this build and in consumer setups).

{{{footer}}}
