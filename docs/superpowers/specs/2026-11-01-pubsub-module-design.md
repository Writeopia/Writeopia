# Pub/Sub code in its own module — design

Issue: `Writeopia/Writeopia#843`.

## Problem

`backend/core/pubsub` already owns the Pub/Sub client wrapper (`PubsubPublisher`) and
push-envelope decoding (`decodePubsubPushData`). The account-deletion saga's shared
Pub/Sub contract still lives inside `backend/core/auth`, and `backend/documents/documents`
plus `backend/micro/media` import those auth internals:

- `io.writeopia.api.core.auth.dto.AccountDeletionEventPayload` — message body wire format.
- `io.writeopia.api.core.auth.models.AccountDeletionTopics` — topic ids (must match
  `pubsub.tf` in the external infra repo).
- `io.writeopia.api.core.auth.models.AccountDeletionEventTypes` — outbox `event_type`
  values consumed by Debezium.
- `io.writeopia.api.core.auth.models.ACCOUNT_DELETION_AGGREGATE_TYPE` — outbox
  `aggregate_type`.

They are not auth-domain types; every Pub/Sub publisher and consumer needs them.

## Decision

Move those four declarations into `backend/core/pubsub` under the existing flat
`io.writeopia.pubsub` package and delete the originals. Update imports in auth,
documents and media. Names, values and shapes stay byte-identical — no wrapper, no new
interface, no new module.

`pubsub` depends only on `backend/core:connection`; auth, documents and media already
depend on `pubsub`, so no new dependency edge and no cycle. Consumers already declare
`kotlinx-serialization`, so the moved `@Serializable` payload's `serializer()` stays
visible.

Rejected alternatives: leaving the contract in auth (does not satisfy the issue);
creating a second module (module already exists); adding a subpackage (pubsub is flat
today, and a move-only change should not invent structure).

## Risk

No runtime behavior change. The only external risk is a silent rename of a topic id,
event type or aggregate type, which breaks the infra/Debezium contract. Pinned by a new
test in the pubsub module that asserts the literal strings and round-trips a push
envelope into the moved payload.

## Acceptance criteria

1. The four declarations are defined in `backend/core/pubsub` and no longer in
   `backend/core/auth`.
2. `auth`, `documents` and `media` import them from `io.writeopia.pubsub`.
3. No other Pub/Sub-specific code is added outside `backend/core/pubsub`.
4. Existing auth/documents/media tests pass unchanged.
