# iosAppNat — native iOS and macOS app

A SwiftUI version of the Writeopia app for iOS and, from the same target, macOS. The existing `iosApp` hosts the Compose Multiplatform UI; this one is fully native and talks to the Writeopia API gateway (`https://writeopia.io`) directly.

Requirements: Xcode 26+ (Swift 6.2 toolchain). Deployment targets iOS 17 and macOS 26. No third-party dependencies.

```
iosAppNat/
├── iosAppNat.xcodeproj
├── iosAppNat/            App target (iOS + macOS): entry point, RootView (phase router),
│                         MainTabView (iOS), MacMainView (macOS), macOS entitlements and Info.plist
├── iosAppNatUITests/     XCUITest smoke tests (no backend needed)
└── Packages/
    ├── Core/             Local package with the core modules
    │   ├── WrModels      Domain models (User, Workspace, Folder, WrDocument, StoryStep, AI…)
    │   ├── WrStorage     Keychain token store, UserDefaults preferences
    │   ├── WrNetwork     APIClient (bearer auth, one-shot token refresh with rotation), errors
    │   ├── WrData        AuthAPI, WorkspacesAPI, AiAPI, DocumentsRepository
    │   │                 (remote for the open space, JSON files for the private space)
    │   ├── WrSession     AppSession: app phase, space, user, workspace, theme and shared services
    │   └── WrDesign      Colors, buttons, text fields, state overlays
    ├── Editor/           Local package with the text editor, split like the Kotlin SDK
    │   ├── Writeopia     Editing logic: StoryState, Action, ContentManager (line break,
    │   │                 erase, delete, move), FocusHandler, SpansHandler, WriteopiaManager
    │   ├── WriteopiaUI   WriteopiaStateManager (observable state), StepsModifier (SPACE /
    │   │                 ON_DRAG_SPACE between steps), drawers per step type, WriteopiaEditor
    │   ├── Drawing       Free drawing: DrawingData/Stroke models (same JSON as Android),
    │   │                 canvas, drawing editor and the preview shown in documents
    │   └── NoteEditor    NoteEditorViewModel (counterpart of NoteEditorKmpViewModel) and screen
    └── Features/         Local package with the feature modules
        ├── AuthFeature       Space choice, login, register, email confirmation,
        │                     password recovery (email → code → new password), workspace choice
        ├── DocumentsFeature  Folder/document grid (drag items onto a folder to move them, slide
        │                     cards to select them: copy, favorite, AI summary, delete),
        │                     new folder/document; documents open in the NoteEditor
        ├── SearchFeature     Debounced search (backend or local files)
        ├── SetupFeature      Shared by the setup and Settings: Apple Intelligence status, the
        │                     local AI (Ollama / llmman) configuration and the private space folder
        └── SettingsFeature   General (color theme, folder on Mac), Teams, AI (provider, local AI,
                              cloud usage), Account
```

## Flow

`AppSession.phase` drives `RootView`:

- `spaceChoice`: pick the **private space** (offline, documents stored in Application Support) or the **open space** (online).
- `offlineSetup`: on the Mac only, the first-run setup of the desktop app after choosing the private space: the AI (Apple Intelligence or a local model through Ollama / llmman, with the same "Auto configure" wizard and manual configuration as the Compose app) and then the folder of the workspace. Both can be changed later in Settings.
- `signedOut`: login, with register and password recovery.
- `emailConfirmation`: shown after registering, or when logging in with an unconfirmed email.
- `chooseWorkspace`: list or create team workspaces.
- `ready`: tabs for Documents, Search and Settings.

From Settings > Account, the private space offers **Sign in** and **Switch space**. The open space offers Change workspace, Switch space, Change password, Sign out and Delete account.

## Running

Open `iosAppNat.xcodeproj` and run the `iosAppNat` scheme on an iPhone, an iPad or **My Mac** (the product is `Writeopia.app` there). To point the app at another gateway, set the `WRITEOPIA_BASE_URL` environment variable in the scheme; `LOCAL_AI_URL` overrides where Ollama is looked for.

## macOS

The Mac app is the same target with the `macosx` platform: App Sandbox, hardened runtime, outgoing network, user-selected files and app-scope bookmarks (`macOS.entitlements`), and `NSAllowsLocalNetworking` (`macOS-Info.plist`) for a local AI on an IP address. It shows the auth and setup flows of the desktop app and the sidebar shell (`MacMainView`: the landscape `SideGlobalMenu` in a `NavigationSplitView`, then documents, search and settings).

The editor is the same as on iOS. Each step is an `NSTextView` (`WriteopiaUI/StepTextView+macOS.swift`) with the native spell check and input methods; Return, Backspace at the start, Up and Down at the first or last line, Tab (accepts AI suggestions) and Shift+Up/Down (grows the line selection) go to the state manager, like the key handling of the Compose desktop app. The side options of the landscape layout replace the bottom menu: Page (lock, font, delete), Text (bold, italic, underline, link, highlights, checkbox, list, code block, box, card, headings, drawing, image from a file), Export (JSON and Markdown through a save panel, publish) and AI. Steps are reordered by dragging the grip, which shows on hover; clicking the grip selects the line. Shortcuts: Cmd+B/I/U, Cmd+Shift+B (box), Cmd+- (list item), Cmd+Shift+A (select all lines), Cmd+L (link the selected lines to a new page), Cmd+K (ask the AI), Esc (clear the selection, stop the AI), Cmd+C/X on selected lines.

- **Private space folder**: chosen with the file importer and kept as a security-scoped bookmark. The database (`writeopia.sqlite`, single file, no WAL) is created in that folder; files already there are left alone. Documents in the previous location are not moved.
- **Local AI**: `OllamaAPI` (`/api/tags`, `/api/pull`, `/api/delete`, `/api/generate` streaming) and `OllamaAi` answer the editor commands with the same prompts as the cloud; `LocalAiConfigController` in the session keeps the URL, the model and a running download; the wizard asks the backend for `/api/ai/local-config` (built-in defaults offline) and probes Ollama and llmman.
- Build and test from the command line: `swift test` in each package, and `xcodebuild build -project iosAppNat.xcodeproj -scheme iosAppNat -destination 'platform=macOS'`.

## Documents list

- Slide a card sideways to select it, like the Compose notes list (`SwipeBox`); several can be selected. The selection menu mirrors `NotesSelectionMenu`: **Copy** (documents with new ids; folders with what's inside), **Favorite** (or remove from favorites when all are), **AI Summary** (open space: the selected documents as Markdown are sent to `/api/ai/summary` and the answer becomes a new document in the folder; the Compose app uses local AI for this), **Delete** (after confirmation; folders with their contents; synced like single deletions) and an "N selected ✕" chip.

## Editor

Documents open in an editable editor. Supported step types: `TITLE`, `TEXT`, `CODE_BLOCK`, `IMAGE`, `CHECK_ITEM`, `UNORDERED_LIST_ITEM`, `DIVIDER`, `DOCUMENT_LINK`, `AI_ANSWER`, `LOADING`, `SPACE` and `ON_DRAG_SPACE`. Other types (spreadsheets, videos...) are kept in the document but not drawn.

- Text is edited in place. Return splits a step (the title continues as a paragraph, lists and checklists continue as items, also from an empty item). Backspace at the start merges into the previous line, turns a list item into a paragraph (the way to leave a list), or removes a divider/link above.
- Existing spans (bold, italic, underline, highlights, links) are drawn and follow the text as it's edited. Bold, italic, underline, highlights (yellow, green, red) and links can be applied to the selected text from the menu. Comments aren't supported.
- Hold the grip on the left of a step and drop it on another step or space to reorder.
- Slide a line sideways to select it (slide again to unselect), like the SDK's `SwipeBox`. Several lines can be selected. While lines are selected, the bottom menu becomes the selection menu of the SDK's `EditionScreen`: AI on the selected lines, bold/italic/underline, checkbox, list item, code block, Box/Card, Title/SubTitle/Header, link to a new page, copy (plain and rich text), cut, delete, and an "N selected ✕" chip that shows the count and clears the selection.
- A bottom menu mirrors the Compose editor (AI, bold, italic, underline, highlight, link, drawing, image, undo, redo). Everything works except undo and redo, which are placeholders.
- AI (open space only) opens a dialog to apply a command to the document or the line with the cursor: Prompt, Summary, Action Points, FAQ and Tags. The answer streams from `/api/ai/*` (Server-Sent Events) into an `AI_ANSWER` step, with a `LOADING` step until it starts.
- **Image** opens the photo picker. The image is saved on the device as a JPEG (at most 2048 px) and added where the cursor is, like the SDK's `addImage`: after the title, in place of an empty line, or after the current line (at the end with no cursor). In the open space it's uploaded to `/api/media/upload` and the step switches from the local `path` to the returned `url`; if the upload fails it stays local. Long press an image to delete it.
- **Drawing** opens a full screen canvas (pen, highlighter, eraser, 11 colors, 5 widths, undo, clear) like the Compose drawing feature. Saving adds a `DRAWING` step (type 100) at the end of the document with the strokes as JSON in its text, in the same format as Android. In the document the drawing is cropped to its strokes; tap it to edit, long press to delete.
- The top right menu mirrors `NoteGlobalActionsMenu` of the Compose app:
  - **Lock document**: read-only mode (text, checkboxes, dragging, selection and the bottom menu are disabled); a lock shows next to the title.
  - **Font**: System, Serif, Monospace or Cursive, remembered for every document.
  - **Export as Json / Markdown**: writes `{"data": <document>}` or the SDK's Markdown to a file and opens the share sheet.
  - **Publish to Web**: for premium users in the open space, publish/unpublish (`/document/{id}/publish`, `/unpublish`, `/published`) and copy the `https://app.writeopia.io/site/<id>` link. Others see the "Premium Feature" dialog, as in the Compose app. The backend doesn't send the user tier yet, so everyone currently counts as free.

## Persistence and sync

Mirrors the Compose app (`DocumentLoadUseCase`, `DocumentMerger`, `FolderSync`, the conflict handlers and `OnUpdateStoryStepSyncTracker`).

- **Storage**: a SQLite database per space, with a schema that mirrors the Compose app (`document`, `story_step`, `folder`). The private space lives in `Application Support/Writeopia/PrivateSpace/writeopia.sqlite`; each workspace of the open space has a local cache in `Application Support/Writeopia/Workspaces/<id>/writeopia.sqlite`. Each step is a row (its content as JSON), so an edit writes only the steps that changed. Documents keep `lastUpdatedAt` / `lastSyncedAt`, and steps their own `lastUpdatedAt`. JSON files from earlier versions are imported once and moved to `imported-json/`.
- **Opening a folder** shows the cache right away, then syncs it: `POST /api/docs/workspace/document/folder/diff`, conflicts settled like the Compose app (the copy updated last wins, documents deleted on this device aren't brought back), local changes sent with `POST /api/docs/workspace/document` and `/folder`, and the result shown. Synced items the backend no longer has in the folder are removed (deleted or moved on another device). Pull to refresh syncs again.
- **Opening a document** shows the local copy, then merges the backend copy (`GET .../document/{id}`): steps matched by id, the newest wins, steps from both sides kept. The merge replaces what's shown only if nothing was typed yet.
- **Editing** saves on the device on every change (only the changed steps, like the Compose app's `OnUpdateDocumentTracker`), and sends the changed and deleted steps with `POST .../document/{id}/steps/sync` after 2 s without typing, and when leaving the editor. When a push fails, the document stays outdated and the next folder sync sends it whole.
- New documents and folders are created locally with their final id and sent right away when possible.
- **Deleting** a document (editor menu > Delete document) removes it for good in the private space. In the open space it's marked deleted locally (so no sync brings it back), deleted on the backend with `POST .../document/delete`, then removed; when the backend can't be reached, the deletion is sent first thing in the next sync of its folder.

Differences with the Compose app: steps are stamped with their own `lastUpdatedAt` (so merges pick the newest copy of each step instead of always the local one), and a successful `steps/sync` marks the document as synced.

## Translations

The app is in English and Portuguese (`pt`). All the strings are in one String Catalog, `iosAppNat/Localizable.xcstrings`; code in the packages looks them up in the app bundle too.

- In views, literals are translated automatically (`Text("Settings")`, `Button("Save")`, and the `WrDesign` components, which take `LocalizedStringKey`). Text built outside views uses `String(localized:)` (errors, enum titles, the welcome document).
- Counts use plural variations (`"%lld items"` → "1 item" / "3 itens").
- To find new strings, build with `SWIFT_EMIT_LOC_STRINGS=YES`: the compiler writes a `.stringsdata` file per source file (packages included, they set `defaultLocalization: "en"`) with every key and its format specifiers. Add the missing keys and their Portuguese to the catalog.
- To see the app in Portuguese: `xcrun simctl launch <device> io.writeopia.WriteopiaNative -AppleLanguages "(pt)"`, or pick Portuguese in the scheme's App Language.

## Tests

```bash
# Core (runs on macOS)
cd Packages/Core && swift test

# Editor and Features (iOS only)
cd Packages/Editor && xcodebuild test -scheme Editor-Package -destination 'platform=iOS Simulator,name=iPhone 17'

cd Packages/Features && xcodebuild test -scheme Features-Package -destination 'platform=iOS Simulator,name=iPhone 17'

# UI smoke tests (they expect a fresh private space: uninstall the app from the simulator first)
xcodebuild test -project iosAppNat.xcodeproj -scheme iosAppNat -destination 'platform=iOS Simulator,name=iPhone 17'
```
