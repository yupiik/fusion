# {{{name}}} (`{{{artifactId}}}`)

{{{description}}}

Records annotated with `@JsonModel` get a codec generated at compile time; this module provides the runtime
mapper executing those codecs plus a streaming parser.

```java
@JsonModel
public record MyModel(
        @JsonProperty("boolean") boolean aBool, // renames the attribute in the JSON payload
        String simplest,
        LocalDate date,
        AnotherModel nested,
        List<AnotherModel> list,
        @JsonOthers Map<String, Object> extensions) { // catch-all for unknown attributes
}

// JsonMapper is a bean once fusion-json is on the classpath, inject it
final MyModel model = jsonMapper.fromString(MyModel.class, "{\"simplest\":\"value\"}");
final String json = jsonMapper.toString(model);
```

## Entry points

- `io.yupiik.fusion.json.JsonMapper`: main user API (a bean once `FusionJsonModule` is on the classpath).
- `io.yupiik.fusion.json.serialization.JsonCodec`: SPI implemented by generated codecs.
- `io.yupiik.fusion.json.spi.Parser`: streaming parser abstraction.
- `io.yupiik.fusion.json.pretty.PrettyJsonMapper`: formatting decorator.
- `io.yupiik.fusion.json.patch`/`pointer`/`diff`: JSON-Patch, JSON-Pointer and diff utilities.
- `io.yupiik.fusion.json.internal.JsonMapperImpl`: implementation (internal, do not expose in signatures).

## Module rules

- Reflectionless: no reflection-based (de)serialization fallback, unsupported types must fail clearly.
- Codec generation lives in `fusion-processor` (`internal/json`), only the runtime belongs here.

{{{footer}}}
