# Pub/Sub module extraction — plan

**Spec:** `docs/superpowers/specs/2026-11-01-pubsub-module-design.md` (issue `Writeopia/Writeopia#843`).

## Global Constraints

- Pub/Sub topic ids are an external contract and must stay exactly:
  `account-deletion-requested`, `account-deletion-workspaces-completed`,
  `account-deletion-media-completed`, `account-deletion-finalized`.
- Outbox `event_type` values must stay exactly: `AccountDeletionRequested`,
  `AccountDeletionFinalized`.
- Outbox `aggregate_type` must stay exactly `user`.
- `AccountDeletionEventPayload` wire shape must stay `{"userId": "<id>"}`.
- Java 21 / Kotlin JVM target 21 for `backend/core/pubsub`.
- `backend/core/pubsub` keeps its flat `io.writeopia.pubsub` package and its current
  Gradle dependencies; no new module, interface or dependency edge.
- Move-only: no behavior change in publisher, decoder, auth, documents or media.

## Review Focus

Failure modes this plan must survive (each tied to a test or an existing suite):

1. A topic id, event type or aggregate type is silently renamed during the move →
   `AccountDeletionPubsubContractTest` asserts the literal external values.
2. The moved `@Serializable` payload serializer is not visible to consumers because
   `pubsub` declares serialization as `implementation` → compilation of auth, documents
   and media tests is the check; all three declare their own serialization dependency.
3. `decodePubsubPushData` still decodes the base64 `message.data` envelope after the
   payload changes package → round-trip test builds a real Pub/Sub push body.
4. A consumer keeps importing the deleted auth symbol and the build goes red →
   `:backend:core:auth:test`, `:backend:documents:documents:test`,
   `:backend:micro:media:test` compile and pass.
5. A dependency cycle is introduced (pubsub → auth/models/database) → pubsub
   `build.gradle.kts` is unchanged; only `:backend:core:connection` is declared.

## Scope check

One subsystem: the backend Pub/Sub contract. One task; no product plans split needed.

## Self-check

- [x] Task has exact file paths.
- [x] Every AC line maps to a test or an existing suite.
- [x] No new dependency, module or interface.
- [x] External string contract pinned by a test.
- [x] Baseline suites for all touched modules run in the gate.

### Task 1: Move the account-deletion Pub/Sub contract into `backend/core/pubsub`

**Files (create):**

- `backend/core/pubsub/src/main/java/io/writeopia/pubsub/AccountDeletionTopics.kt`
- `backend/core/pubsub/src/main/java/io/writeopia/pubsub/AccountDeletionEventTypes.kt`
- `backend/core/pubsub/src/main/java/io/writeopia/pubsub/AccountDeletionAggregateType.kt`
- `backend/core/pubsub/src/main/java/io/writeopia/pubsub/AccountDeletionEventPayload.kt`
- `backend/core/pubsub/src/test/kotlin/io/writeopia/pubsub/AccountDeletionPubsubContractTest.kt`

**Files (delete):**

- `backend/core/auth/src/main/java/io/writeopia/api/core/auth/models/AccountDeletionTopics.kt`
- `backend/core/auth/src/main/java/io/writeopia/api/core/auth/models/AccountDeletionEventTypes.kt`
- `backend/core/auth/src/main/java/io/writeopia/api/core/auth/models/AccountDeletionAggregateType.kt`
- `backend/core/auth/src/main/java/io/writeopia/api/core/auth/dto/AccountDeletionEventPayload.kt`

**Files (import updates only):**

- `backend/core/auth/src/main/java/io/writeopia/api/core/auth/service/AccountDeletionService.kt`
- `backend/core/auth/src/main/java/io/writeopia/api/core/auth/service/AccountDeletionReconciliationService.kt`
- `backend/core/auth/src/main/java/io/writeopia/api/core/auth/routing/AccountDeletionEventsRouting.kt`
- `backend/core/auth/src/test/kotlin/io/writeopia/api/core/auth/service/AccountDeletionServiceTest.kt`
- `backend/core/auth/src/test/kotlin/io/writeopia/api/core/auth/service/AccountDeletionReconciliationServiceTest.kt`
- `backend/documents/documents/src/main/java/io/writeopia/api/documents/documents/service/AccountDeletionWorkspaceService.kt`
- `backend/documents/documents/src/main/java/io/writeopia/api/documents/routing/AccountDeletionEventsRouting.kt`
- `backend/documents/documents/src/test/kotlin/io/writeopia/api/documents/documents/service/AccountDeletionWorkspaceServiceTest.kt`
- `backend/micro/media/src/main/kotlin/io/writeopia/api/media/service/AccountDeletionMediaService.kt`
- `backend/micro/media/src/main/kotlin/io/writeopia/api/media/routing/AccountDeletionEventsRouting.kt`
- `backend/micro/media/src/test/kotlin/io/writeopia/api/media/service/AccountDeletionMediaServiceTest.kt`

**Interfaces:** no new API. `io.writeopia.pubsub.AccountDeletionTopics`,
`AccountDeletionEventTypes`, `ACCOUNT_DELETION_AGGREGATE_TYPE` and
`AccountDeletionEventPayload` keep their names, member names and values, only the package
becomes `io.writeopia.pubsub`.

**AC → test**

| AC | Test |
|---|---|
| 1. declared in pubsub, gone from auth | `AccountDeletionPubsubContractTest` (imports new package), compile of pubsub |
| 2. auth/documents/media import pubsub | `:backend:core:auth:test`, `:backend:documents:documents:test`, `:backend:micro:media:test` |
| 3. no Pub/Sub code added elsewhere | `git grep` review in gate; `AccountDeletionPubsubContractTest` |
| 4. existing tests pass unchanged | the three suites above run without edits to assertions |

**TDD steps**

1. RED: write `AccountDeletionPubsubContractTest` importing
   `io.writeopia.pubsub.AccountDeletionEventPayload`, `AccountDeletionTopics`,
   `AccountDeletionEventTypes`, `ACCOUNT_DELETION_AGGREGATE_TYPE`; run
   `:backend:core:pubsub:test` and capture `unresolved reference` output.
2. GREEN: `git mv` the four declarations into `backend/core/pubsub`, change their
   `package` line to `io.writeopia.pubsub`, delete now-empty auth `dto`/`models` dirs if
   empty; update every import; run the same pubsub test.
3. Run `:backend:core:auth:test :backend:documents:documents:test :backend:micro:media:test`.
4. Commit in two atomic commits: (a) failing-contract test + moved declarations and
   import updates form one reviewable change is acceptable only as one commit if the
   tree is never broken between them; prefer one commit
   `refactor(backend): move account-deletion pubsub contract into pubsub module`.
