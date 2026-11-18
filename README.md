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
| `TELEGRAM_MODE` | app | `webhook` | `polling` (dev) or `webhook` (prod), see [Telegram](#telegram) |
| `TELEGRAM_BOT_TOKEN` | app | none | Bot token from BotFather. Required in both modes (polling reads updates with it, webhook registration calls the Bot API with it) |
| `TELEGRAM_WEBHOOK_URL` | app | none | Public HTTPS base URL of the app (webhook mode only) |
| `TELEGRAM_WEBHOOK_SECRET` | app | none | Shared secret Telegram sends in a header with every webhook call. 1-256 characters: letters, digits, `_` and `-` |
| `KEYCLOAK_ISSUER_URI` | app | `http://localhost:8081/realms/expense-bot` | Realm whose tokens the API accepts (`iss` claim) |
| `KEYCLOAK_API_AUDIENCE` | app | `expense-bot-api` | Audience the API requires in tokens (`aud` claim) |
| `KEYCLOAK_ADMIN` | compose | `admin` | Keycloak admin user (local only) |
| `KEYCLOAK_ADMIN_PASSWORD` | compose | `admin` | Keycloak admin password (local only) |
| `GOOGLE_CLIENT_ID` | compose | none (required) | Google OAuth client ID used by Keycloak |
| `GOOGLE_CLIENT_SECRET` | compose | none (required) | Google OAuth client secret used by Keycloak |

## Telegram

Create a bot with `@BotFather` in Telegram and put the token it gives you in `TELEGRAM_BOT_TOKEN`. Telegram delivers messages in one of two ways, and a bot can use only one at a time. The app picks it from `TELEGRAM_MODE` and switches automatically, with no manual calls to the Bot API:

| Mode | Use | On startup |
|---|---|---|
| `polling` | Local development, no public URL needed | Removes any registered webhook (`deleteWebhook`), then asks Telegram for updates in a loop (`getUpdates`, long polling) |
| `webhook` (default) | Production | Registers `TELEGRAM_WEBHOOK_URL` + `/api/telegram/webhook` with Telegram (`setWebhook`). Telegram then calls that endpoint, which only accepts requests carrying `TELEGRAM_WEBHOOK_SECRET` |

In webhook mode, if `TELEGRAM_WEBHOOK_URL` or `TELEGRAM_WEBHOOK_SECRET` is missing, the app starts but logs a warning and registers nothing; with no secret the endpoint rejects every call. Run a single instance in polling mode: Telegram allows only one consumer per bot.

For now, received messages are only logged: the agent that handles them is a later issue.

## Keycloak

Keycloak runs at `http://localhost:8081`. On startup it imports the `expense-bot` realm from `keycloak/expense-bot-realm.json` if the realm does not exist yet. The realm contains:

- A public client `expense-bot-web` (Authorization Code + PKCE) for the SPA, with `http://localhost:5173` as redirect URI and web origin.
- A client scope `expense-bot-api` that adds `expense-bot-api` to the `aud` claim of access tokens. It is a default scope of `expense-bot-web` and `expense-bot-cli`.
- A public client `expense-bot-cli` with the password grant, to get tokens with `curl` during local development. **Local only:** never create it in a production realm. The password grant hands the user's password to the client and OAuth 2.1 removes it.
- Google as an identity provider, configured through `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET`.

To get the Google credentials, create an OAuth client (type *Web application*) in Google Cloud Console with this authorized redirect URI:

```
http://localhost:8081/realms/expense-bot/broker/google/endpoint
```

The API accepts a token only if it was issued by `http://localhost:8081/realms/expense-bot` and its `aud` claim contains `expense-bot-api`. Tokens minted for other clients of the realm (for example `admin-cli`) answer 401.

### Getting a token for local testing

The realm has no users. Create one in the admin console (`http://localhost:8081/admin`, realm `expense-bot` → *Users* → *Create user*, with an email), then set a non-temporary password in its *Credentials* tab. Users who sign in with Google have no password and cannot use this flow.

```bash
TOKEN=$(curl -s http://localhost:8081/realms/expense-bot/protocol/openid-connect/token \
  -d grant_type=password -d client_id=expense-bot-cli \
  -d username=<user> -d password=<password> \
  | sed -E 's/.*"access_token":"([^"]+)".*/\1/')

curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/categories
```

To re-import the realm, remove only the Keycloak volume (do not use `docker compose down -v`, which also deletes Postgres data):

```bash
docker compose rm -sf keycloak
docker volume rm expense-bot_keycloak-data
docker compose up -d keycloak
```

## Tests

```bash
./mvnw test
```

`ArchitectureTest` enforces the hexagonal module rules.
