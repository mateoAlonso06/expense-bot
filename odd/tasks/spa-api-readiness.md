# Feature: spa-api-readiness (issue #38)

## Objective
Get the API ready for the SPA (separate repository): accept only access tokens issued for this API (JWT audience) and let the browser call the API from the SPA origin (CORS).

## Problem / why
- The API validates signature, issuer and expiration, but not `aud`. Any token of the `expense-bot` realm is accepted, including tokens minted for other clients (`admin-cli` today, an admin tool or second app later).
- The SPA runs on `http://localhost:5173` and the API on `:8080`. With no CORS configuration, the browser blocks the SPA's calls (the preflight for requests with `Authorization` fails).

## Scope
- Realm: a shared `expense-bot-api` client scope with an audience mapper, default on `expense-bot-web` and on a new dev-only client.
- Dev-only client `expense-bot-cli` (public, direct access grants) so tokens can be obtained with `curl` until the SPA exists. Decided with the user (option A): the password grant is deprecated (OAuth 2.1 drops it) and must never exist in a production realm; the realm export is only imported locally.
- API: `spring.security.oauth2.resourceserver.jwt.audiences` from an environment variable.
- API: CORS in `SecurityConfig` with allowed origins from a property.
- README: how to get a token for local testing; new variables.
- Out of scope: the SPA itself, a production realm, Keycloak theme.

## Constraints
- `SecurityConfig` keeps `jwt(Customizer.withDefaults())`: Spring Boot 4.1.1 adds the audience validator when the property is set (checked in `JwtDecoderConfiguration#getValidator` bytecode). No custom decoder in production code.
- No secrets committed. The dev client is public (no secret); no seeded users.
- TDD: not configured (ordinary functional checks). Runner: `./mvnw test` (JDK 21).

## Tasks
- [x] P1 Audience: client scope + mapper + `expense-bot-cli` in the realm export, `audiences` property, `JwtAuthenticationTest` accepts a token with the audience and rejects one without it (401), README section and variable.
- [x] P2 CORS: `http.cors(...)` + `CorsConfigurationSource` with allowed origins from a property; web tests for allowed origin (preflight and authenticated request) and not-allowed origin; README variable; docs/testing.md counts.

## Acceptance criteria (from #38)
- A token from `expense-bot-web` with the API audience is accepted as before.
- A valid token from the same realm without the API audience (e.g. `admin-cli`) answers 401.
- The audience mapper is part of `keycloak/expense-bot-realm.json`.
- Expected audience and allowed origins are configured through properties.
- README explains how to get a token for local testing.
- Allowed origin: preflight succeeds and responses carry CORS headers, including authenticated endpoints. Other origins get no CORS headers.

## Progress / next step
Branch `feat/spa-api-readiness` from `main` (`804a8ee`).
P1 done (not committed, waiting for review): realm export has the `expense-bot-api` client scope (oidc-audience-mapper, access token only) as a default scope of `expense-bot-web` and the new public `expense-bot-cli` (password grant only, no standard flow). `audiences: ${KEYCLOAK_API_AUDIENCE:expense-bot-api}`. `JwtAuthenticationTest` test decoder now builds issuer + audience validators from the bound `OAuth2ResourceServerProperties`; new tests reject a token without `aud` and with another `aud`. With `KEYCLOAK_API_AUDIENCE=wrong-api` the accept test fails, so the property wiring is covered. Checked on a throwaway Keycloak 26.7.4 (port 18081, removed): the realm imports; `expense-bot-cli` and `expense-bot-web` tokens carry `aud: [expense-bot-api, account]` plus `sub` and `email`; `admin-cli` tokens have no `aud`; `expense-bot-web` refuses the password grant. README: variables, dev client warning, curl steps without `jq`. Full suite: 237 run (235 + 2 opt-in skipped), 0 failures; docs/testing.md counts refreshed. Committed as `44c2399`.
P2 done: `SecurityConfig` passes its CORS source explicitly (`.cors(cors -> cors.configurationSource(...))`), built by a private method, not a bean: Spring MVC already registers another `CorsConfigurationSource` (`mvcHandlerMappingIntrospector`), so `.cors(withDefaults())` only works by the bean name `corsConfigurationSource` and type injection only by parameter-name match (verified: renaming the parameter gives `NoUniqueBeanDefinitionException`). Source covers `/api/**` (origins from `cors.allowed-origins` / `CORS_ALLOWED_ORIGINS`, methods GET/POST/PUT/PATCH/DELETE, headers Authorization/Content-Type, max age 1h, no credentials). `CorsTest` (5): preflight from the SPA origin without token, authenticated and 401 responses carry `Access-Control-Allow-Origin`, other origin gets 403 on preflight and no CORS headers. Two comma-separated origins via env also pass. Full suite: 242 run (240 + 2 opt-in skipped), 0 failures; README variable and docs/testing.md counts updated. Next: PR closing #38.
