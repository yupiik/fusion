# {{{name}}} (`{{{artifactId}}}`)

{{{description}}}

Records annotated with `@Table`/`@Id`/`@Column` (from `fusion-build-api`) get their entity model generated at
compile time; this module executes SQL over plain JDBC with those models - no lazy loading, no dirty checking,
just typed statements and mapping.

```java
@Table("CUSTOMER")
public record CustomerEntity(
        @Id(autoIncremented = true) Integer id,
        @Column String firstname,
        @Column(name = "LAST_NAME") String lastname) {

    @OnInsert // lifecycle callbacks return a new instance (records are immutable)
    public CustomerEntity onInsert() {
        return this;
    }
}

// Database is created from a DataSource (DatabaseFactory) or injected as a bean
final CustomerEntity saved = database.insert(new CustomerEntity(null, "John", "Doe"));
final List<CustomerEntity> all = database.query(
        CustomerEntity.class, "select * from CUSTOMER where LAST_NAME = ?", b -> b.bind("Doe"));
```

## Entry points

- `io.yupiik.fusion.persistence.api.Database` / `ContextLessDatabase` / `BaseDatabase`: main query APIs
  (with or without a thread-bound connection).
- `io.yupiik.fusion.persistence.api.DatabaseFactory`: creation from a `DataSource`.
- `io.yupiik.fusion.persistence.api.TransactionManager`: transaction handling.
- `io.yupiik.fusion.persistence.api.Entity` / `StatementBinder` / `ResultSetWrapper` / `SqlBuilder`: generated-model SPI and helpers.
- `io.yupiik.fusion.persistence.spi.DatabaseTranslation`: per-database SQL dialect SPI (see `impl.translation` for the provided ones).
- `io.yupiik.fusion.persistence.impl.datasource`: simple and Tomcat-pool backed datasources (internal).

## Module rules

- Entity model generation lives in `fusion-processor` (`internal/persistence`); only runtime execution belongs here.
- New database dialects go through `DatabaseTranslation` implementations, not conditionals in the core.
- Tests use H2 and run in parallel: use per-test database names.

{{{footer}}}
