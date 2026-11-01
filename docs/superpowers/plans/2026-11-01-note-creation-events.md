# Note creation sync events Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Write a `CREATE_DOCUMENT` / `CREATE_FOLDER` sync event when a note or folder is first stored on the server, so the events feed carries the full change history.

**Architecture:** Two small `WriteopiaDbBackend` save wrappers check existence before the insert and append one `sync_event` row after it. The service functions that create rows call the wrappers and take the actor id from the route's `requirePremiumUserId`.

**Tech Stack:** Kotlin, Ktor, SQLDelight over PostgreSQL, embedded Postgres in tests.

**Spec:** `docs/superpowers/specs/2026-11-01-note-creation-events-design.md`

## Global Constraints

- Event type strings are exactly `CREATE_DOCUMENT` and `CREATE_FOLDER`.
- No schema change, no migration, no new event DTO fields.
- `sync_event.user_id` is the authenticated route user; `sync_event.workspace_id` is the document's or folder's workspace.
- An event is written only when the row did not exist before the save.
- No client (`application/`, `plugins/`) change: `EventSync` already ignores event types it does not know.
- Build: `JAVA_HOME=/private/var/folders/b4/__v5zlz14v1fbx8r2709srhr0000gn/T/opencode/oc-tools/jdk-21.0.13+9/Contents/Home`.
- Test task: `./gradlew :backend:documents:documents:test`.

## Review Focus

1. Saving an existing document (every edit push) must not write a second `CREATE_DOCUMENT` — otherwise history floods with repeats. Pinned by `a second upload of the same document writes no creation event`.
2. A bulk upload from a fresh device must write exactly one event per new document, not one per save. Pinned by `a first upload of a document writes one creation event`.
3. A deleted folder sent by a stale client must not produce a creation event. Pinned by `a deleted folder sent by a client writes no creation event`.
4. The event must carry the actor and workspace so `events/diff` returns it to the right users. Pinned by the `user_id`/`workspace_id` assertions in `a first upload of a document writes one creation event`.
5. A document auto-created by step sync or link-card describe must still be announced once. Pinned by `a step sync that creates the document writes a creation event` and `a link card describe that creates the document writes a creation event`.

---

### Task 1: Document creation events

**Files:**
- Modify: `backend/documents/documents/src/main/java/io/writeopia/api/documents/documents/repository/SyncEventRepository.kt`
- Modify: `backend/documents/documents/src/main/java/io/writeopia/api/documents/documents/repository/DocumentsRepository.kt`
- Modify: `backend/documents/documents/src/main/java/io/writeopia/api/documents/documents/DocumentsService.kt`
- Modify: `backend/documents/documents/src/main/java/io/writeopia/api/documents/routing/DocumentsRouting.kt`
- Modify: `backend/documents/documents/src/test/kotlin/io/writeopia/api/documents/documents/StoryStepSyncTest.kt`
- Modify: `backend/documents/documents/src/test/kotlin/io/writeopia/api/documents/documents/StoryStepTombstoneTest.kt`
- Modify: `backend/documents/documents/src/test/kotlin/io/writeopia/api/documents/documents/DescribeLinkCardTest.kt`
- Test: `backend/documents/documents/src/test/kotlin/io/writeopia/api/documents/documents/CreationSyncEventTest.kt`

**Interfaces:**
- Consumes: `WriteopiaDbBackend.createSyncEvent(workspaceId, eventType, entityId, userId, oldParentId = null, newParentId = null)` (already exists).
- Produces:
  - `SyncEventType.CREATE_DOCUMENT = "CREATE_DOCUMENT"`
  - `suspend fun WriteopiaDbBackend.saveDocumentWithCreationEvent(document: Document, userId: String)` in `...documents.repository`
  - `DocumentsService.receiveDocuments(documents, workspaceId, userId, writeopiaDb, useAi, embeddingService = null)`
  - `DocumentsService.upsertDocument(document, workspaceId, userId, writeopiaDb, embeddingService)`
  - `DocumentsService.cloneDocuments(documentIds, workspaceId, userId, writeopiaDb, useAi, embeddingService = null)`
  - `DocumentsService.syncStorySteps(documentId, workspaceId, userId, request, writeopiaDb, embeddingService = null)`
  - `DocumentsService.describeLinkCard(documentId, workspaceId, userId, request, describe, writeopiaDb)`

- [ ] **Step 1: Write the failing test**

Create `CreationSyncEventTest.kt`. Helpers:

```kotlin
private val db = configureTestPersistence()
private val workspaceId = "workspace-creation-events"
private val userId = "user-creation-events"
private val now = 1_792_000_000_000L

private fun document(id: String) = Document(
    id = id,
    title = "Note",
    createdAt = Instant.fromEpochMilliseconds(now),
    lastUpdatedAt = Instant.fromEpochMilliseconds(now),
    lastSyncedAt = Instant.fromEpochMilliseconds(now),
    workspaceId = workspaceId,
    parentId = "root"
)

private fun events() =
    db.syncEventEntityQueries.selectByWorkspaceAfterTime(workspaceId, 0).executeAsList()
```

Tests (each `runBlocking`, all asserting on `events()`):

1. `a first upload of a document writes one creation event` — `receiveDocuments(listOf(document("doc-1")), workspaceId, userId, db, useAi = false)`; assert one row, `event_type == "CREATE_DOCUMENT"`, `entity_id == "doc-1"`, `user_id == userId`, `workspace_id == workspaceId`.
2. `a second upload of the same document writes no creation event` — call it twice; assert `1` row.
3. `upserting a new document writes a creation event` — `upsertDocument(document("doc-2"), workspaceId, userId, db, embeddingService = null)`; assert one `CREATE_DOCUMENT` for `doc-2`.
4. `upserting an existing document writes no creation event` — upsert twice; assert `1` row.
5. `cloning a document writes a creation event per clone` — `db.saveDocument(document("doc-3"))`, then `cloneDocuments(listOf("doc-3"), workspaceId, userId, db, useAi = false)`; assert the single event's `entity_id` equals the clone's id.
6. `a step sync that creates the document writes a creation event` — `syncStorySteps("doc-4", workspaceId, userId, StoryStepSyncRequest(documentId = "doc-4", workspaceId = workspaceId, lastSyncTimestamp = 0L, requestTimestamp = now, changes = emptyList(), deletions = emptyList()), db)`; assert one `CREATE_DOCUMENT` for `doc-4`.
7. `a link card describe that creates the document writes a creation event` — `describeLinkCard("doc-5", workspaceId, userId, LinkCardDescriptionRequest(storyStep = StoryStep(id = "card-5", type = StoryTypes.LINK_CARD.type, url = "https://writeopia.io").toApi(Rank.at(1)), position = Rank.at(1), requestTimestamp = now), { "A description." }, db)`; assert one `CREATE_DOCUMENT` for `doc-5`.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :backend:documents:documents:test --tests "io.writeopia.api.documents.documents.CreationSyncEventTest"`
Expected: compilation failure — `receiveDocuments`/`upsertDocument`/`cloneDocuments`/`syncStorySteps`/`describeLinkCard` have no `userId` parameter. Record the compiler output as RED evidence.

- [ ] **Step 3: Implement**

`SyncEventRepository.kt`: add `const val CREATE_DOCUMENT = "CREATE_DOCUMENT"` first in `SyncEventType`.

`DocumentsRepository.kt`, next to `saveDocument`:

```kotlin
/** Saves [document] and records a CREATE_DOCUMENT event when the row was not stored before. */
suspend fun WriteopiaDbBackend.saveDocumentWithCreationEvent(document: Document, userId: String) {
    val isNew = getDocumentById(document.id, document.workspaceId) == null
    saveDocument(document)
    if (isNew) {
        createSyncEvent(document.workspaceId, SyncEventType.CREATE_DOCUMENT, document.id, userId)
    }
}
```

`DocumentsService.kt`:

- `receiveDocuments`: take `userId: String` after `workspaceId`. Per document, read the stored row once (`val stored = writeopiaDb.getDocumentById(document.id, workspaceId)`), `saveDocument`, emit `CREATE_DOCUMENT` when `stored == null`, and feed `stored?.parentId` to `embeddingService.onDocumentSaved` as the previous parent.
- `upsertDocument` (suspend overload): take `userId` after `workspaceId`; read `existingParentId = writeopiaDb.getDocumentById(...)?.parentId` before the save, save, embed with it, emit `CREATE_DOCUMENT` when the row did not exist.
- `cloneDocuments`: take `userId` after `workspaceId`; replace `writeopiaDb.saveDocument(clonedDocument)` with `writeopiaDb.saveDocumentWithCreationEvent(clonedDocument, userId)`.
- `syncStorySteps`: take `userId` after `workspaceId`; replace the auto-create `writeopiaDb.saveDocument(newDocument)` with `saveDocumentWithCreationEvent(newDocument, userId)`.
- `describeLinkCard`: take `userId` after `workspaceId`; same replacement at the auto-create branch.

`DocumentsRouting.kt`: pass `userId = userId` at the `receiveDocuments`, `upsertDocument`, `cloneDocuments`, `syncStorySteps` and `describeLinkCard` call sites.

Update the three existing test files to pass `private val userId = "<their-workspace>-user"` to `syncStorySteps` and `describeLinkCard`.

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :backend:documents:documents:test`
Expected: BUILD SUCCESSFUL, every `CreationSyncEventTest` case green.

- [ ] **Step 5: Commit**

```bash
git add backend/documents/documents/src docs/superpowers
git commit -m "feat(documents): emit a creation sync event for new notes"
```

---

### Task 2: Folder creation events

**Files:**
- Modify: `backend/documents/documents/src/main/java/io/writeopia/api/documents/documents/repository/SyncEventRepository.kt`
- Modify: `backend/documents/documents/src/main/java/io/writeopia/api/documents/documents/repository/DocumentsRepository.kt`
- Modify: `backend/documents/documents/src/main/java/io/writeopia/api/documents/documents/DocumentsService.kt`
- Modify: `backend/documents/documents/src/main/java/io/writeopia/api/documents/routing/DocumentsRouting.kt`
- Test: `backend/documents/documents/src/test/kotlin/io/writeopia/api/documents/documents/FolderCreationSyncEventTest.kt`

**Interfaces:**
- Consumes: `WriteopiaDbBackend.createSyncEvent(...)`, `WriteopiaDbBackend.saveFolder(vararg Folder): List<String>`, `WriteopiaDbBackend.isFolderTombstoned(folderId)`, `SyncEventType.CREATE_DOCUMENT` (Task 1).
- Produces:
  - `SyncEventType.CREATE_FOLDER = "CREATE_FOLDER"`
  - `suspend fun WriteopiaDbBackend.saveFolderWithCreationEvent(folder: Folder, userId: String): Boolean` (true when the folder was stored)
  - `DocumentsService.createFolder(parentFolderId, title, workspaceId, userId, icon = null, writeopiaDb, embeddingService = null)`
  - `DocumentsService.receiveFolders(folders, userId, writeopiaDb, embeddingService = null)`

- [ ] **Step 1: Write the failing test**

Create `FolderCreationSyncEventTest.kt` with the same `db` / `workspaceId` / `userId` / `events()` helpers as Task 1 and:

```kotlin
private fun folder(id: String, deleted: Boolean = false) = Folder(
    id = id,
    parentId = "root",
    title = "Folder",
    createdAt = Instant.fromEpochMilliseconds(now),
    lastUpdatedAt = Instant.fromEpochMilliseconds(now),
    workspaceId = workspaceId,
    itemCount = 0,
    deleted = deleted
)
```

1. `creating a folder writes one creation event` — `createFolder("root", "Folder", workspaceId, userId, writeopiaDb = db)`; assert `event_type == "CREATE_FOLDER"` and `user_id == userId` for the returned folder's id.
2. `an uploaded folder writes a creation event` — `receiveFolders(listOf(folder("folder-1")), userId, db)`; assert one `CREATE_FOLDER` for `folder-1`.
3. `a second upload of the same folder writes no creation event` — call it twice; assert `1` row.
4. `a deleted folder sent by a client writes no creation event` — `receiveFolders(listOf(folder("folder-2", deleted = true)), userId, db)`; assert no `CREATE_FOLDER` for `folder-2`.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :backend:documents:documents:test --tests "io.writeopia.api.documents.documents.FolderCreationSyncEventTest"`
Expected: compilation failure — `createFolder`/`receiveFolders` have no `userId`. Record it as RED evidence.

- [ ] **Step 3: Implement**

`SyncEventRepository.kt`: add `const val CREATE_FOLDER = "CREATE_FOLDER"` after `CREATE_DOCUMENT`.

`DocumentsRepository.kt`:

```kotlin
/** Saves [folder] and records a CREATE_FOLDER event when it is new and not deleted. */
suspend fun WriteopiaDbBackend.saveFolderWithCreationEvent(folder: Folder, userId: String): Boolean {
    val isNew = getFolderById(folder.id, folder.workspaceId) == null
    val kept = saveFolder(folder).isEmpty()
    if (kept && isNew && !folder.deleted) {
        createSyncEvent(folder.workspaceId, SyncEventType.CREATE_FOLDER, folder.id, userId)
    }
    return kept
}
```

`DocumentsService.kt`:

- `createFolder`: take `userId: String` after `workspaceId`; replace `writeopiaDb.saveFolder(folder)` with `writeopiaDb.saveFolderWithCreationEvent(folder, userId)`.
- `receiveFolders`: take `userId: String` after `folders`; replace the single bulk `saveFolder(*folders.toTypedArray())` with a per-folder `saveFolderWithCreationEvent`, collecting the dropped (tombstoned) ids for the existing log line and the existing embedding filter.

`DocumentsRouting.kt`: pass `userId = userId` at the folder create route and the `receiveFolders` call.

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :backend:documents:documents:test`
Expected: BUILD SUCCESSFUL, both new test classes and the pre-existing module tests green.

- [ ] **Step 5: Commit**

```bash
git add backend/documents/documents/src
git commit -m "feat(documents): emit a creation sync event for new folders"
```

---

## Self-check

- Spec coverage: every emission point in the design table has a task and a test.
- Type consistency: `saveDocumentWithCreationEvent` / `saveFolderWithCreationEvent` names and `userId` position match across tasks; `CREATE_FOLDER` is defined only in Task 2.
- Review Focus: all five lines are pinned by a named test above.
