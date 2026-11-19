# Testing

What kinds of tests this project has, what each one covers, what it uses, and how they could be grouped into suites for CI.

Current state: **268 tests** (plus the opt-in LLM tests), run with `./mvnw test` (JDK 21). Every test class carries a JUnit `@Tag` with its category, and two Maven profiles select suites by tag.

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
| 1 | Domain / pure unit | 87 | Invariants and logic of plain objects: factories, validation, formatting, mapping, eval scoring | JUnit 5, AssertJ | No |
| 2 | Unit with mocks | 79 | A use case or adapter does the right thing given collaborators that answer in a known way | JUnit 5, Mockito (`@ExtendWith(MockitoExtension.class)`) | No |
| 3 | HTTP client | 10 | The Telegram client sends the right requests and handles error responses | `MockRestServiceServer` | No |
| 4 | Web slice | 33 | Controllers, security rules (JWT, CORS, webhook secret) and filters answer the right status codes | `@WebMvcTest`, `@Import(SecurityConfig)` | No |
| 5 | Persistence slice | 46 | SQL, Flyway migrations and constraints behave correctly on a real PostgreSQL | `@DataJpaTest`, Testcontainers (Postgres 17) | **Yes** |
| 6 | Architecture | 5 | Module and layer rules are not broken (domain has no Spring, application does not touch infrastructure, ...) | ArchUnit (`@ArchTest`) | No |
| 7 | Flow | 6 | A Telegram update posted to the webhook goes through the whole application and produces the right reply | `@SpringBootTest`, Testcontainers, a local fake Telegram server | **Yes** |
| 8 | Smoke | 2 | The application context starts with all beans wired, and the Spring AI `ChatClient.Builder` exists without an API key | `@SpringBootTest`, Testcontainers | **Yes** |
| 9 | LLM connection | 1 | The OpenAI configuration (key, model) works end to end with one real call. **Opt-in and not part of any suite**: it costs tokens | `@SpringBootTest`, a real `OPENAI_API_KEY` | **Yes** |
| 9 | LLM extraction eval | 1 | The real extractor runs over the labeled dataset in `eval/extraction/` and writes a report (amount, category, `Other` fallback, per tag). It measures and never fails on a low score. **Opt-in and not part of any suite**: one call per case. See the README | `@SpringBootTest` with only Spring AI and the extractor, a real `OPENAI_API_KEY` | No |

214 tests run without Docker; 54 need it.

## Where each test lives

1. **Domain / pure unit:** `LinkCodeFormatTest`, `LinkCodeTest`, `ChannelIdentityTest`, `UserProfileTest`, `CategoryTest`, `MoneyTest`, `ExpenseTest`, `CategoryMatcherTest`, `IncomingMessageTest`, `TelegramUpdateMapperTest`, `ExtractionMetricsTest`, `ExtractionDatasetTest`, `ExtractionReportTest`, `EvalCategoryResolverTest`.
2. **Unit with mocks:** `ProvisionUserProfileServiceTest`, `RequestLinkCodeServiceTest`, `ResolveChannelIdentityServiceTest`, `ListCategoriesServiceTest`, `RegisterExpenseServiceTest`, `EditExpenseServiceTest`, `SpringAiExpenseExtractorTest`, `IncomingMessageGatewayTest`, `HandleIncomingMessageServiceTest`, `RegisterExpenseFromTextTest`, `ReplyServiceTest`, `TelegramReplySenderTest`, `TelegramPollerTest`, `TelegramPollingRunnerTest`, `TelegramWebhookRegistrarTest`.
3. **HTTP client:** `TelegramApiTest`.
4. **Web slice:** `UserProvisioningFilterTest`, `TelegramWebhookControllerTest`, `TelegramWebhookWithoutSecretTest`, `JwtAuthenticationTest`, `CorsTest`, `CategoryControllerTest`, `ExpenseControllerTest`, `LinkCodeControllerTest`.
5. **Persistence slice:** `LinkCodePersistenceAdapterTest`, `ChannelIdentityPersistenceAdapterTest`, `UserProfilePersistenceAdapterTest`, `ProcessedMessagePersistenceAdapterTest`, `CategoryPersistenceAdapterTest`, `ExpensePersistenceAdapterTest`, `LinkChannelIdentityServiceTest` (a service tested against the real database).
6. **Architecture:** `ArchitectureTest`.
7. **Flow:** `TelegramReplyFlowTest`.
8. **Smoke:** `ExpenseBotApplicationTests`.

9. **LLM:** `OpenAiConnectionTest`, `ExtractionEval`.

Support classes (not tests): `TestcontainersConfiguration`, `TestExpenseBotApplication`, and the eval classes in `conversation/eval` (`ExtractionCase`, `ExtractionDataset`, `ExtractionOutcome`, `ExtractionMetrics`, `ExtractionReport`) plus `EvalCategoryResolver`.

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
| `fast` | 1, 2, 3, 4, 6 | 94 | `./mvnw test -Pfast` | locally before every push; CI job; seconds, no Docker |
| `integration` | 5, 7, 8 | 22 | `./mvnw test -Pintegration` | CI job; needs Docker |
| all | everything | 116 | `./mvnw test` | locally when a change touches persistence or flows |

Category 6 (architecture) sits in `fast` on purpose: it is cheap and it is the test that stops a broken dependency from reaching the PR.

**Tags.** Each class has one `@Tag` from this list: `unit`, `mocks`, `http-client`, `web`, `persistence`, `flow`, `smoke`, `llm` (category 6 is the exception, see below). A single category can also be run with `./mvnw test -Dgroups=web`.

**Two things worth knowing.**
- `fast` is defined as "everything except `persistence`, `flow` and `smoke`" (`excludedGroups` in the `pom.xml`), not as a list of fast tags. ArchUnit runs on its own JUnit engine, and a tag filter (`groups`) drops it: this was verified, a first version listing the fast tags skipped the 5 ArchUnit tests. `ArchitectureTest` therefore has no tag.
- `llm` is excluded from `fast` and absent from `integration`, and the test is also guarded by `RUN_LLM_TESTS=true`, so it never calls OpenAI by accident. Run it with `RUN_LLM_TESTS=true ./mvnw test -Dtest=OpenAiConnectionTest` and a real `OPENAI_API_KEY` in the shell. The extraction eval runs the same way with `-Dtest=ExtractionEval` (see the README).
- A new test class that forgets its tag lands in `fast`. If it is a slow one that needs Docker, it fails there and the omission shows up. Always tag a new class with its category.

**CI (GitHub Actions).** `.github/workflows/ci.yml` runs on every pull request to `main` and on every push to `main`. It has two parallel jobs, `fast` and `integration`, which together run every test. Both install Temurin JDK 21 and cache the Maven repository. The `integration` job relies on the Docker engine preinstalled on GitHub's `ubuntu-latest` runners for Testcontainers. When a job fails, its Surefire reports are uploaded as a workflow artifact (kept 7 days). Pushes to feature branches without a pull request do not trigger CI: run `-Pfast` locally before pushing.

## Known gaps

- No concurrency test for code consumption (arrives with issue #5, T4).
- No coverage measurement (JaCoCo). It can be added later and used as a CI gate only if it proves useful.
