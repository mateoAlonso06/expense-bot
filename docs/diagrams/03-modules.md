# Module and dependency diagram

How the five modules depend on each other. An arrow `A --> B` means "A uses B's public API" (the module's base package:
use-case interfaces, small records, events). Nobody may touch another module's `domain`, `application` or `infrastructure`.

```mermaid
flowchart TB
    CH["channels<br/>Telegram / WhatsApp adapters<br/>dedupe (processed_message)"]
    CONV["conversation<br/>agent, chat memory,<br/>pending clarification"]
    ACC["accounts<br/>profile, channel identity,<br/>link codes"]
    EXP["expenses<br/>expenses, categories"]
    BUD["budgets (v1)<br/>limits, notifications"]

    CH -- "HandleIncomingMessage" --> CONV
    CH -. "implements ReplySender" .-> CONV
    CONV -- "resolve identity, link account" --> ACC
    CONV -- "register/edit expense, list categories" --> EXP
    CONV -. "listens: LimitThresholdReached" .-> BUD
    BUD -. "listens: ExpenseRecorded / Updated" .-> EXP

    classDef later stroke-dasharray: 5 5
    class BUD later
```

Legend: solid = direct call to a use-case interface; dotted = event listener or an interface implemented by the other side.

The graph has no cycles: `channels -> conversation -> {accounts, expenses, budgets} ; budgets -> expenses`.
`accounts` and `expenses` depend on nobody.

## Why the arrows go this way

| Decision | Reason |
|---|---|
| `channels` depends on `conversation`, not the reverse | `conversation` declares a `ReplySender` port in its public API; `channels` implements it. Dependency inversion avoids the `channels <-> conversation` cycle |
| `budgets` publishes an event instead of sending messages | It does not need to know about chat, identities or channels. `conversation` listens, resolves the user's identity via `accounts` and replies through `ReplySender` (#21) |
| `expenses` does not depend on `accounts` | It stores `user_id` and checks ownership by that id. The authenticated `sub` comes from the controller, not from another module |
| REST controllers live in their own module's `infrastructure/web` | The SPA reaches `expenses`, `accounts` and `budgets` directly; only chat goes through `conversation` |

## Planned public API per module

| Module | Public API (base package) |
|---|---|
| `accounts` | `ProvisionUserProfile`, `RequestLinkCode`, `LinkChannelIdentity`, `FindUserByIdentity` |
| `expenses` | `RegisterExpense`, `EditExpense`, `ListExpenses`, `ListCategories` (exists), `CategoryView` (exists), events `ExpenseRecorded` / `ExpenseUpdated` |
| `conversation` | `HandleIncomingMessage`, `ReplySender` (port) |
| `budgets` | `SetLimit`, `GetSpentVsLimit`, event `LimitThresholdReached` |
| `channels` | none needed: nobody calls it directly |

## Inside one module (hexagonal)

```mermaid
flowchart LR
    subgraph Module["a module, e.g. expenses"]
        direction LR
        API["base package<br/>public API<br/>use-case interfaces, records, events"]
        subgraph APP["application"]
            UC["use-case services"]
            PORT["ports (e.g. CategoryRepository)"]
        end
        DOM["domain<br/>plain Java"]
        subgraph INFRA["infrastructure"]
            WEB["web (controllers)"]
            PERS["persistence (JPA adapters)"]
        end
    end
    WEB --> API
    API -.-> UC
    UC --> DOM
    UC --> PORT
    PERS -. "implements" .-> PORT
```

`ArchitectureTest` enforces: `domain` is framework-free, `application` never imports `infrastructure`, and modules only use each other's base package.

## Design notes and open points

1. **`ArchitectureTest` does not forbid cycles between modules.** Its rules only stop access to another module's internals, so `channels -> conversation -> channels` through public APIs would pass. A slice rule (`slices().matching("com.expensebot.(*)..").should().beFreeOfCycles()`) would protect the graph above. To be added together with the first cross-module call.
2. **The webhook must answer fast, the agent is slow.** Telegram retries if the endpoint takes too long, and an LLM call can take seconds. `channels` should record the dedupe row, hand the message to `conversation` asynchronously and return `200`. How (`@Async`, in-process queue) is open and affects #6 and #8.
3. **Event semantics.** Notifications must not break expenses: #7 requires that a failed send does not lose the stored expense. Events should be handled after the expense transaction commits (e.g. `@TransactionalEventListener(AFTER_COMMIT)`), so a listener failure cannot roll it back.
4. **Where profile provisioning (#4) runs.** The first authenticated request must create the `user_profile`. A web filter or interceptor in `accounts` would run on every request, so it needs a cheap "already known" check.
5. **Who owns pending clarification (#11).** It sits in `conversation`, as it is part of the dialogue state, not an expense.
6. **`config` is outside the modules** (it holds `SecurityConfig`). That is fine, but it must stay limited to cross-cutting wiring and not grow business code.
