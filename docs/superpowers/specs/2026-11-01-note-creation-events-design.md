# Note creation sync events — design

**Issue:** Writeopia/Writeopia#826. Scope agreed with the maintainer: backend events only, no client UI.

## Problem

The sync-event stream (`sync_event`, `POST /api/docs/workspace/events/diff`) already carries
`DELETE_DOCUMENT`, `DELETE_FOLDER`, `MOVE_DOCUMENT` and `MOVE_FOLDER`. Creation writes nothing,
so the change history a user pulls is missing every note that was created.

## Decision

Emit one `CREATE_DOCUMENT` / `CREATE_FOLDER` event when a document or folder row is inserted on the
server for the first time. Everything after that (edits, moves, deletes) keeps using the existing
event types.

- Actor: the authenticated user of the request (`call.requirePremiumUserId`), stored in
  `sync_event.user_id`.
- Workspace: the document's/folder's `workspaceId`, stored in `sync_event.workspace_id`.
- No schema, DTO or client change: `event_type` is free text and `EventSync.processEvents` already
  ignores types it does not know. `GET`/diff returns the new rows as they are.
- No backfill: notes created before this change stay without a creation event.

## Emission points

| Entry point | When an event is written |
|---|---|
| `DocumentsService.receiveDocuments` | per document whose row did not exist before the save |
| `DocumentsService.upsertDocument` (suspend overload) | when the row did not exist before the save |
| `DocumentsService.cloneDocuments` | once per clone (a clone is always new) |
| `DocumentsService.syncStorySteps` | when the sync auto-creates the missing document |
| `DocumentsService.describeLinkCard` | when the describe auto-creates the missing document |
| `DocumentsService.createFolder` | always (the route creates a new folder) |
| `DocumentsService.receiveFolders` | per folder that was new, not deleted, and not tombstoned |

Tutorial seeding goes through the non-suspend `upsertDocument` and stays event-free: seeded content
is not a user change.

## Consequences

- A fresh device that uploads N notes writes N creation events — this is the intended history.
- A save of an existing note costs one extra primary-key `SELECT` per document to tell "new" from
  "edited".
- Concurrent first saves of the same id can write the event twice; the stream is append-only and the
  window is a single row insert, so no lock is taken.
