# Native macOS app: evaluation and plan

This document evaluates making the macOS version of Writeopia a native (Swift) app, built by adding a macOS destination to `iosAppNat`, and lists the work needed.

## Why go native on Mac

For a text editor, most of the gain is in text input and system integration:

- **The native text system (`NSTextView`).** System spell check and grammar, autocorrect, dictation, input methods (Japanese, Chinese…), Look Up, the Services menu, native undo and text selection, and Writing Tools. The Compose Desktop text field is weakest in exactly these areas.
- **Apple Intelligence.** The FoundationModels framework is Swift-only. From the JVM desktop app it would need a JNI/Swift bridge; a native Mac app calls it directly and reuses the iOS code (`WrData/AppleIntelligenceAi.swift`).
- **Mac conventions.** A real menu bar, standard shortcuts, multiple windows and window tabs, native full screen, toolbar and sidebar, drag and drop with Finder.
- **System integration.** Spotlight indexing of documents, Quick Look for `.wrdoc.json`, the Share menu, Handoff with the iOS app, iCloud Drive for the private space.
- **Footprint.** No bundled JVM: smaller download, faster startup, less memory. Easier fit for the Mac App Store sandbox.
- **Accessibility.** VoiceOver support is much better with native controls.

## Costs

- **Compose Desktop stays.** Windows and Linux still need it. The Mac-specific UI is extra surface.
- **Feature parity.** Desktop has power-user features that need Swift ports: `documents_graph`, local AI through Ollama, export, drawing, the local folder workspace.
- **Duplicated editor logic.** `Packages/Editor/Writeopia` already reimplements the Kotlin SDK's editing logic. A Mac app raises the stakes on keeping them in sync.

## Approach

- Add macOS to the `iosAppNat` packages and use platform checks for the text view, colors, fonts and pasteboard. No separate project.
- Build a Mac-specific shell: split view, menu bar commands, multiple windows.
- Keep Compose Desktop for Windows and Linux. Decide later whether the Compose Mac build stays, based on parity for graph, Ollama and local folder sync.
- Skip Mac Catalyst: it's cheaper at first but never feels fully like a Mac app, and for an editor the `NSTextView` port is where most of the value is.

## Current state (updated with the first Mac slice)

The `iosAppNat` target now builds for macOS as well (`SUPPORTED_PLATFORMS = iphoneos iphonesimulator macosx`, minimum macOS 26), and all three packages build and pass their tests on a Mac host. Done from the plan below: parts 1 (mechanical fixes: `toolbarTitleDisplayMode`, `WrColors` with `NSColor`, `ToolbarItemPlacement.wrTrailing`, `SystemSettings`), the Mac shell of part 4 (`MacMainView`: `NavigationSplitView` with `SideGlobalMenu`), part 5 (CI builds the Mac app and runs the package tests on macOS), plus the auth and first-run setup flow of the desktop app (AI with Apple Intelligence or Ollama/llmman, private space folder). Not done: parts 2 and 3 (`NSTextView`) and the rest of part 4; on macOS `NoteEditorView` is a read-only placeholder (`NoteEditor/NoteEditorView+macOS.swift`) and `SwipeSelection` compiles to no-ops.

## State before the first slice

The packages already declare `.macOS(.v14)`. Running `swift build` on each package for macOS:

- **Core** builds.
- **Editor** fails on `Drawing/DrawingEditorView.swift` (`systemBackground`; `glassEffect` needs macOS 26). More importantly, most of the editor UI is wrapped in whole-file `#if canImport(UIKit)`, so about 2,300 lines compile to nothing on macOS:

  | Lines | File |
  |---|---|
  | 492 | `Editor/Sources/WriteopiaUI/StoryStepDrawers.swift` |
  | 450 | `Editor/Sources/NoteEditor/EditorBottomMenu.swift` |
  | 388 | `Editor/Sources/NoteEditor/NoteEditorView.swift` |
  | 267 | `Editor/Sources/NoteEditor/NoteMenuSheet.swift` |
  | 258 | `Core/Sources/WrDesign/SwipeSelection.swift` |
  | 214 | `Editor/Sources/WriteopiaUI/ReorderDrag.swift` |
  | 176 | `Editor/Sources/WriteopiaUI/StepTextView.swift` |
  | 109 | `Editor/Sources/WriteopiaUI/TextStyles.swift` |
  | 95 | `Editor/Sources/WriteopiaUI/ImageDrawer.swift` |
  | 68 | `Editor/Sources/WriteopiaUI/WriteopiaEditor.swift` |
  | 52 | `Editor/Sources/WriteopiaUI/EditorFont.swift` |
  | 49 | `Core/Sources/WrDesign/WrColors.swift` (partially guarded) |
  | 42 | `Editor/Sources/WriteopiaUI/SwipeToSelect.swift` |
  | 29 | `Editor/Sources/NoteEditor/LinePasteboard.swift` |
  | 29 | `Editor/Sources/NoteEditor/ImageProcessing.swift` |

- **Features** fails, starting with `SettingsFeature/AccountSettingsView.swift`. Once those errors are fixed it would also hit `NoteEditorView` not existing on macOS (used by `DocumentsFeature/FolderContentsView.swift` and `SearchFeature/SearchView.swift`).

Everything below `WrDesign` (`WrModels`, `WrNetwork`, `WrStorage`, `WrData`, `WrSession`, and the `Writeopia` editing logic) needs no changes.

## Work plan

### 1. Mechanical fixes (about 1 day)

SwiftUI modifiers and colors that don't exist on macOS.

| Files | Issue |
|---|---|
| `AuthFeature/RegisterView`, `AuthFeature/ForgotPasswordViews`, `DocumentsFeature/{FolderContentsView,FolderEditSheet,FolderMenuPresentations,FolderPickerSheet}`, `SettingsFeature/AccountSettingsView`, `NoteEditorView`, `EditorBottomMenu`, `NoteMenuSheet`, `DrawingEditorView` | `navigationBarTitleDisplayMode` |
| `AccountSettingsView`, `TeamsSettingsView`, `WrDesign/Components`, `NoteEditorView` | `keyboardType`, `textInputAutocapitalization` |
| `FolderContentsView` | `.topBarTrailing` |
| `DrawingEditorView`, `DrawingPreview`, `NoteEditorView`, `StoryStepDrawers`, `ImageDrawer` | `systemBackground`, `secondarySystemBackground`, `secondarySystemFill` |
| `DocumentsSelectionMenu`, `EditorBottomMenu`, `DrawingEditorView` | `glassEffect` (macOS 26+) |

- Add helpers in `WrDesign` (`.wrInlineTitle()`, `.wrEmailField()`, `WrColors.surface`, …) instead of scattering `#if` blocks.
- Raise the macOS minimum to 26: FoundationModels requires it and it removes the `glassEffect` availability problem.

### 2. Platform shims (about 1–2 days)

Add `PlatformFont`, `PlatformColor`, `PlatformImage` and `PlatformPasteboard` typealiases in `WrDesign`.

| File | UIKit → AppKit |
|---|---|
| `WrDesign/WrColors.swift` | `UIColor` → `NSColor` |
| `WriteopiaUI/TextStyles.swift` | `UIFont`/`UIColor.label`/`.tintColor` → `NSFont`/`.labelColor`/`.controlAccentColor`. `UIFontMetrics` has no macOS equivalent: drop Dynamic Type scaling there. Symbolic traits map almost 1:1. |
| `WriteopiaUI/EditorFont.swift` | `UIFont` → `NSFont` |
| `NoteEditor/LinePasteboard.swift`, `StoryStepDrawers` | `UIPasteboard` → `NSPasteboard` |
| `NoteEditor/ImageProcessing.swift` | `UIGraphicsImageRenderer` → `NSImage` + `CGContext` |
| `WriteopiaUI/ImageDrawer.swift` | `UIImage` → `NSImage` |
| `NoteEditor/NoteMenuSheet.swift` | `UIActivityViewController` → SwiftUI `ShareLink` (both platforms; removes the wrapper) |
| `NoteEditorView` | `fullScreenCover` → `.sheet` or a separate window on Mac |

### 3. `NSTextView` port (about 3–4 days)

`WriteopiaUI/StepTextView.swift` is small and isolated: rewrite it as an `NSViewRepresentable` of roughly 250 lines.

| iOS | macOS | Difficulty |
|---|---|---|
| `shouldChangeTextIn` to split on Return | `doCommandBy: #selector(insertNewline:)` | Easy |
| `deleteBackward()` override for merge-at-start | `doCommandBy: #selector(deleteBackward:)` | Easy |
| `markedTextRange == nil` (IME guard) | `hasMarkedText()` | Easy |
| `becomeFirstResponder()` | `window?.makeFirstResponder(textView)` | Easy |
| `isScrollEnabled = false` + `sizeThatFits` | Bare `NSTextView` (no `NSScrollView`) with `intrinsicContentSize` computed from the layout manager and invalidated on every change | **Hard.** `NSTextView` doesn't size itself; getting heights right inside a `LazyVStack` takes trial and error. |
| Removing `textDropInteraction` | `unregisterDraggedTypes()` | Easy |
| (new on Mac) | Up/Down at the first/last line moves to the previous/next step (`moveUp:`/`moveDown:`) | Medium |
| (new on Mac) | Cmd-B / Cmd-I / Cmd-U for spans (spans can't be created in the editor on iOS yet either) | Medium |

Known limitation carried over from iOS: each step is its own text view, so selecting text across paragraphs doesn't work. Mac users will notice this more.

### 4. Redesign rather than port (about 3–5 days)

Touch-gesture UI that shouldn't be translated one-to-one:

- `WrDesign/SwipeSelection.swift` (`UIPanGestureRecognizer`) and `WriteopiaUI/SwipeToSelect.swift` → Cmd/Shift-click selection and context menus.
- `WriteopiaUI/ReorderDrag.swift` (`UIScrollView` autoscroll, haptics) → SwiftUI `.draggable` / `.dropDestination`.
- `NoteEditor/EditorBottomMenu.swift` → window toolbar plus menu-bar `Commands`.
- App target (`MainTabView`) → `NavigationSplitView` with folder tree, document list and editor, plus a `Settings` scene.

### 5. CI

Add a macOS `swift build` of each package to CI. `Features` is already broken on macOS without anyone noticing.

## Estimate

About **2–3 weeks** to a Mac app at parity with the iOS app. Suggested order: parts 1 and 2 first (every package builds for macOS, no whole-file guards left except `StepTextView`), then the `NSTextView` port, then the Mac shell and redesigns.
