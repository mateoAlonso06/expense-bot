# Testing

What kinds of tests this project has, what each one covers, what it uses, and how they could be grouped into suites for CI.

Current state: **107 tests**, run with `./mvnw test` (JDK 21). Every test class carries a JUnit `@Tag` with its category, and two Maven profiles select suites by tag.

## Glossary

| Term | Meaning |
|---|---|
| Unit test | Runs one class in isolation, in milliseconds. No Spring, no database, no network. |
| Mock | A fake collaborator (Mockito) that returns what the test tells it to, and records how it was called. |
| Slice test | Starts only one layer of Spring (for example only the web layer) instead of the whole application. |
| Testcontainers | Starts a real PostgreSQL in a Docker container for the test. Needs Docker running. |
| Integration test | Several real pieces working together (for example controller + service + database). |
| Flow test | An integration test that follows one user scenario from the entry point to the exit point. |
| Smoke test | The cheapest possible check that the application starts at all. |
| Suite | A named group of tests that run together (for example "fast" or "integration"). |

## Categories

| # | Category | Tests | What it proves | Uses | Needs Docker |
|---|---|---|---|---|---|
| 1 | Domain / pure unit | 25 | Invariants and logic of plain objects: factories, validation, formatting, mapping | JUnit 5, AssertJ | No |
| 2 | Unit with mocks | 34 | A use case or adapter does the right thing given collaborators that answer in a known way | JUnit 5, Mockito (`@ExtendWith(MockitoExtension.class)`) | No |
| 3 | HTTP client | 9 | The Telegram client sends the right requests and handles error responses | `MockRestServiceServer` | No |
| 4 | Web slice | 18 | Controllers, security rules (JWT, webhook secret) and filters answer the right status codes | `@WebMvcTest`, `@Import(SecurityConfig)` | No |
| 5 | Persistence slice | 12 | SQL, Flyway migrations and constraints behave correctly on a real PostgreSQL | `@DataJpaTest`, Testcontainers (Postgres 17) | **Yes** |
| 6 | Architecture | 5 | Module and layer rules are not broken (domain has no Spring, application does not touch infrastructure, ...) | ArchUnit (`@ArchTest`) | No |
| 7 | Flow | 3 | A Telegram update posted to the webhook goes through the whole application and produces the right reply | `@SpringBootTest`, Testcontainers, a local fake Telegram server | **Yes** |
| 8 | Smoke | 1 | The application context starts with all beans wired | `@SpringBootTest`, Testcontainers | **Yes** |

91 tests run without Docker; 16 need it.

## Where each test lives

1. **Domain / pure unit:** `LinkCodeFormatTest`, `LinkCodeTest`, `UserProfileTest`, `CategoryTest`, `IncomingMessageTest`, `TelegramUpdateMapperTest`.
2. **Unit with mocks:** `ProvisionUserProfileServiceTest`, `RequestLinkCodeServiceTest`, `ListCategoriesServiceTest`, `IncomingMessageGatewayTest`, `HandleIncomingMessageServiceTest`, `ReplyServiceTest`, `TelegramReplySenderTest`, `TelegramPollerTest`, `TelegramPollingRunnerTest`, `TelegramWebhookRegistrarTest`.
3. **HTTP client:** `TelegramApiTest`.
4. **Web slice:** `UserProvisioningFilterTest`, `TelegramWebhookControllerTest`, `TelegramWebhookWithoutSecretTest`, `JwtAuthenticationTest`, `CategoryControllerTest`, `LinkCodeControllerTest`.
5. **Persistence slice:** `LinkCodePersistenceAdapterTest`, `UserProfilePersistenceAdapterTest`, `ProcessedMessagePersistenceAdapterTest`, `CategoryPersistenceAdapterTest`.
6. **Architecture:** `ArchitectureTest`.
7. **Flow:** `TelegramReplyFlowTest`.
8. **Smoke:** `ExpenseBotApplicationTests`.

Support classes (not tests): `TestcontainersConfiguration`, `TestExpenseBotApplication`.

## Conventions

- One test class per production class, in the same package, named `<Class>Test`.
- Test names describe behavior (`rejectsTheSameMessageDeliveredAgain`), not methods.
- A test that touches the database uses a real PostgreSQL, never an in-memory one: Flyway migrations and constraints are part of what is being verified.
- Flow tests replace only what is outside the system (Telegram), never the application's own beans.
- Every new feature adds tests at the lowest category that can prove it; a flow test is added only for a scenario that crosses modules.

## Suites

Goal: protect the application on every change without making every push wait for Docker.

| Suite | Categories | Tests | Command | Intended use |
|---|---|---|---|---|
| `fast` | 1, 2, 3, 4, 6 | 91 | `./mvnw test -Pfast` | every push; seconds, no Docker |
| `integration` | 5, 7, 8 | 16 | `./mvnw test -Pintegration` | every pull request; needs Docker |
| all | everything | 107 | `./mvnw test` | before merging to `main` |

Category 6 (architecture) sits in `fast` on purpose: it is cheap and it is the test that stops a broken dependency from reaching the PR.

**Tags.** Each class has one `@Tag` from this list: `unit`, `mocks`, `http-client`, `web`, `persistence`, `flow`, `smoke` (category 6 is the exception, see below). A single category can also be run with `./mvnw test -Dgroups=web`.

**Two things worth knowing.**
- `fast` is defined as "everything except `persistence`, `flow` and `smoke`" (`excludedGroups` in the `pom.xml`), not as a list of fast tags. ArchUnit runs on its own JUnit engine, and a tag filter (`groups`) drops it: this was verified, a first version listing the fast tags skipped the 5 ArchUnit tests. `ArchitectureTest` therefore has no tag.
- A new test class that forgets its tag lands in `fast`. If it is a slow one that needs Docker, it fails there and the omission shows up. Always tag a new class with its category.

**Pipeline sketch (GitHub Actions, not written yet).** One job runs `-Pfast` on every push; a second job runs `-Pintegration` on pull requests. The job installs JDK 21. GitHub-hosted Ubuntu runners usually have Docker, which Testcontainers needs (to verify when the workflow is written).

## Known gaps

- No concurrency test for code consumption (arrives with issue #5, T4).
- No coverage measurement (JaCoCo). It can be added later and used as a CI gate only if it proves useful.
