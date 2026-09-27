# iosAppNat — native iOS app

A SwiftUI version of the Writeopia iOS app. The existing `iosApp` hosts the Compose Multiplatform UI; this one is fully native and talks to the Writeopia API gateway (`https://writeopia.io`) directly.

Requirements: Xcode 26+ (Swift 6.2 toolchain). Deployment target iOS 17. No third-party dependencies.

```
iosAppNat/
├── iosAppNat.xcodeproj
├── iosAppNat/            App target: entry point, RootView (phase router), MainTabView
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
    │   └── NoteEditor    NoteEditorViewModel (counterpart of NoteEditorKmpViewModel) and screen
    └── Features/         Local package with the feature modules
        ├── AuthFeature       Space choice, login, register, email confirmation,
        │                     password recovery (email → code → new password), workspace choice
        ├── DocumentsFeature  Folder/document grid (drag items onto a folder to move them),
        │                     new folder/document; documents open in the NoteEditor
        ├── SearchFeature     Debounced search (backend or local files)
        └── SettingsFeature   General (color theme), Teams, AI (cloud usage), Account
```

## Flow

`AppSession.phase` drives `RootView`:

- `spaceChoice`: pick the **private space** (offline, documents stored as `.wrdoc.json`/`.wrfolder.json` in Application Support) or the **open space** (online).
- `signedOut`: login, with register and password recovery.
- `emailConfirmation`: shown after registering, or when logging in with an unconfirmed email.
- `chooseWorkspace`: list or create team workspaces.
- `ready`: tabs for Documents, Search and Settings.

From Settings > Account, the private space offers **Sign in** and **Switch space**. The open space offers Change workspace, Switch space, Change password, Sign out and Delete account.

## Running

Open `iosAppNat.xcodeproj` and run the `iosAppNat` scheme. To point the app at another gateway, set the `WRITEOPIA_BASE_URL` environment variable in the scheme.

## Editor

Documents open in an editable editor. Supported step types: `TITLE`, `TEXT`, `CHECK_ITEM`, `UNORDERED_LIST_ITEM`, `DIVIDER`, `DOCUMENT_LINK`, `AI_ANSWER`, `LOADING`, `SPACE` and `ON_DRAG_SPACE`. Other types (images, spreadsheets...) are kept in the document but not drawn.

- Text is edited in place. Return splits a step (the title continues as a paragraph, lists and checklists continue as items, Return on an empty item leaves the list). Backspace at the start merges into the previous line, turns a list item into a paragraph, or removes a divider/link above.
- Existing spans (bold, italic, underline, highlights, links) are drawn and follow the text as it's edited. Bold, italic, underline, highlights (yellow, green, red) and links can be applied to the selected text from the menu. Comments aren't supported.
- Hold the grip on the left of a step and drop it on another step or space to reorder.
- A bottom menu mirrors the Compose editor (AI, bold, italic, underline, highlight, link, drawing, image, spreadsheet, undo, redo). AI and the text formats work; drawing, image, spreadsheet, undo and redo are placeholders.
- AI (open space only) opens a dialog to apply a command to the document or the line with the cursor: Prompt, Summary, Action Points, FAQ and Tags. The answer streams from `/api/ai/*` (Server-Sent Events) into an `AI_ANSWER` step, with a `LOADING` step until it starts.
- Edits are kept in memory only: nothing is saved yet.

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
