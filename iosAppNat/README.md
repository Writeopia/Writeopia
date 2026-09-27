# iosAppNat — native iOS app

A SwiftUI version of the Writeopia iOS app. The existing `iosApp` hosts the Compose Multiplatform UI; this one is fully native and talks to the Writeopia API gateway (`https://writeopia.io`) directly.

Requirements: Xcode 26+ (Swift 6.2 toolchain). Deployment target iOS 17. No third-party dependencies.

```
iosAppNat/
├── iosAppNat.xcodeproj
├── iosAppNat/            App target: entry point, RootView (phase router), MainTabView
├── iosAppNatUITests/     XCUITest smoke tests (no backend needed)
├── Config/Info.plist     ATS exception for local networking (Ollama)
└── Packages/
    ├── Core/             Local package with the core modules
    │   ├── WrModels      Domain models (User, Workspace, Folder, WrDocument, StoryStep, AI…)
    │   ├── WrStorage     Keychain token store, UserDefaults preferences
    │   ├── WrNetwork     APIClient (bearer auth, one-shot token refresh with rotation), errors
    │   ├── WrData        AuthAPI, WorkspacesAPI, AiAPI, OllamaAPI, DocumentsRepository
    │   │                 (remote for the open space, JSON files for the private space)
    │   ├── WrSession     AppSession: app phase, space, user, workspace, theme and shared services
    │   └── WrDesign      Colors, buttons, text fields, state overlays
    └── Features/         Local package with the feature modules
        ├── AuthFeature       Space choice, login, register, email confirmation,
        │                     password recovery (email → code → new password), workspace choice
        ├── DocumentsFeature  Folder/document grid (drag items onto a folder to move them),
        │                     new folder/document, document viewer
        ├── SearchFeature     Debounced search (backend or local files)
        └── SettingsFeature   General (color theme), Teams, AI (cloud usage + local Ollama), Account
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

## Tests

```bash
# Core (runs on macOS)
cd Packages/Core && swift test

# Features (iOS only)
cd Packages/Features && xcodebuild test -scheme Features-Package -destination 'platform=iOS Simulator,name=iPhone 17'

# UI smoke tests (they expect a fresh private space: uninstall the app from the simulator first)
xcodebuild test -project iosAppNat.xcodeproj -scheme iosAppNat -destination 'platform=iOS Simulator,name=iPhone 17'
```
