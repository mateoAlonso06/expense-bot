# expense-bot

Expense-tracking bot. Users log expenses through Telegram using natural language, receipt photos or voice notes; an AI agent extracts and categorizes them. A separate web panel shows history, dashboards and limits.

## Requirements

- JDK 21
- Docker and Docker Compose

## Local setup

1. Copy the example environment file and fill in the values:

   ```bash
   cp .env.example .env
   ```

2. Start the local dependencies (Postgres, Keycloak, Grafana LGTM):

   ```bash
   docker compose up -d
   ```

3. Run the application:

   ```bash
   ./mvnw spring-boot:run
   ```

4. Check that it is up: `http://localhost:8080/actuator/health` should return `UP`.

Flyway runs the migrations in `src/main/resources/db/migration` on startup.

## Environment variables

Secrets are never committed. `.env` is ignored by git.

| Variable | Used by | Default | Description |
|---|---|---|---|
| `DB_HOST` | app | `localhost` | Postgres host |
| `DB_PORT` | app | `5432` | Postgres port |
| `DB_NAME` | app, compose | `expense-app` | Database name |
| `DB_USERNAME` | app, compose | `mateo-local` | Database user |
| `DB_PASSWORD` | app, compose | `secret` | Database password |
| `OPENAI_API_KEY` | app | none (required) | OpenAI API key for Spring AI |
| `OTLP_ENDPOINT` | app | `http://localhost:4318/v1/traces` | OpenTelemetry traces endpoint |
| `KEYCLOAK_ADMIN` | compose | `admin` | Keycloak admin user (local only) |
| `KEYCLOAK_ADMIN_PASSWORD` | compose | `admin` | Keycloak admin password (local only) |
| `GOOGLE_CLIENT_ID` | compose | none (required) | Google OAuth client ID used by Keycloak |
| `GOOGLE_CLIENT_SECRET` | compose | none (required) | Google OAuth client secret used by Keycloak |

## Keycloak

Keycloak runs at `http://localhost:8081`. On startup it imports the `expense-bot` realm from `keycloak/expense-bot-realm.json` if the realm does not exist yet. The realm contains:

- A public client `expense-bot-web` (Authorization Code + PKCE) for the SPA, with `http://localhost:5173` as redirect URI and web origin.
- Google as an identity provider, configured through `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET`.

To get the Google credentials, create an OAuth client (type *Web application*) in Google Cloud Console with this authorized redirect URI:

```
http://localhost:8081/realms/expense-bot/broker/google/endpoint
```

The API validates tokens against `http://localhost:8081/realms/expense-bot`.

To re-import the realm, remove only the Keycloak volume (do not use `docker compose down -v`, which also deletes Postgres data):

```bash
docker compose rm -sf keycloak
docker volume rm bills-bot_keycloak-data
docker compose up -d keycloak
```

## Tests

```bash
./mvnw test
```

`ArchitectureTest` enforces the hexagonal module rules.
