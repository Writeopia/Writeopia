# Writeopia JSON Document Manual for Claude Code

This manual provides context for Claude Code to understand and modify Writeopia documents directly. Writeopia is a text editor that stores documents in JSON format.

## File Types

- **Documents**: `*.wrdoc.json` - Contains document content
- **Folders**: `*.wrfolder.json` - Contains folder metadata
- **Config**: `writeopia_config_file.json` - Workspace configuration

---

## Document Structure

A Writeopia document has this structure:

```json
{
    "id": "unique_document_id",
    "title": "Document Title",
    "workspaceId": "workspace_id",
    "content": [ /* array of StorySteps */ ],
    "createdAt": 1776609896651,
    "lastUpdatedAt": 1776609896651,
    "lastSyncedAt": 1776609896651,
    "parentId": "parent_folder_id_or_root",
    "isLocked": false,
    "isFavorite": false,
    "deleted": false,
    "icon": {
        "label": "icon_name",
        "tint": -65536
    }
}
```

### Important Fields

| Field | Type | Description |
|-------|------|-------------|
| `id` | string | Unique identifier (10 alphanumeric characters recommended) |
| `title` | string | Document title |
| `workspaceId` | string | Workspace identifier (use `"disconnected_user"` for local) |
| `content` | array | Array of StoryStep objects |
| `createdAt` | long | Creation timestamp in milliseconds |
| `lastUpdatedAt` | long | Last update timestamp in milliseconds |
| `parentId` | string | Parent folder ID, or `"root"` for top-level |
| `icon` | object | Optional icon with `label` and `tint` |

---

## StoryStep Structure (Content Elements)

Each element in the `content` array is a StoryStep:

```json
{
    "id": "unique_step_id",
    "type": {
        "name": "message",
        "number": 0
    },
    "text": "The actual text content",
    "position": 1,
    "tags": [],
    "spans": [],
    "decoration": {},
    "checked": false,
    "documentLink": null
}
```

### StoryStep Fields

| Field | Type | Description |
|-------|------|-------------|
| `id` | string | Unique identifier for this step |
| `type` | object | Type definition with `name` and `number` |
| `text` | string | Text content of the step |
| `position` | int | Order position in document (0-indexed) |
| `tags` | array | Block-level formatting (headers, etc.) |
| `spans` | array | Inline formatting (bold, italic, etc.) |
| `decoration` | object | Background color and other decorations |
| `checked` | boolean | For check_item type only |
| `documentLink` | object | For document_link type only |

---

## Story Types

### Available Types

| Name | Number | Description | Usage |
|------|--------|-------------|-------|
| `title` | 11 | Document title | First element, one per document |
| `message` | 0 | Regular text paragraph | Main content type |
| `unordered_list_item` | 16 | Bullet point | List items |
| `check_item` | 10 | Checkbox item | Todo lists with `checked` field |
| `code_block` | 23 | Code block | Code snippets |
| `document_link` | 20 | Link to another document | Uses `documentLink` field |
| `divider` | 21 | Horizontal divider | Section separators |
| `image` | 2 | Image | Uses `url` or `path` field |
| `space` | 7 | Empty space | Spacing element |

### Type Examples

**Regular Text (message)**
```json
{
    "id": "abc123",
    "type": { "name": "message", "number": 0 },
    "text": "This is a paragraph of text.",
    "position": 1
}
```

**Title**
```json
{
    "id": "title123",
    "type": { "name": "title", "number": 11 },
    "text": "My Document Title",
    "decoration": { "backgroundColor": -65536 }
}
```

**Bullet Point**
```json
{
    "id": "list123",
    "type": { "name": "unordered_list_item", "number": 16 },
    "text": "A list item",
    "position": 2
}
```

**Checkbox Item**
```json
{
    "id": "check123",
    "type": { "name": "check_item", "number": 10 },
    "text": "Todo item",
    "checked": false,
    "position": 3
}
```

**Document Link**
```json
{
    "id": "link123",
    "type": { "name": "document_link", "number": 20 },
    "position": 4,
    "documentLink": {
        "id": "target_document_id",
        "title": "Linked Document Title"
    }
}
```

---

## Text Formatting

### Tags (Block-Level Formatting)

Tags apply to the entire text block (like headers).

| Tag | Description |
|-----|-------------|
| `H1` | Heading 1 (largest) |
| `H2` | Heading 2 |
| `H3` | Heading 3 |
| `H4` | Heading 4 (smallest) |
| `HIGH_LIGHT_BLOCK` | Highlighted block |
| `CODE_BLOCK` | Code block formatting |
| `COLLAPSED` | Collapsed/hidden content |

**Tag Example (H2 Header)**
```json
{
    "id": "header123",
    "type": { "name": "message", "number": 0 },
    "text": "Section Title",
    "tags": [
        { "tag": "H2", "position": 0 }
    ],
    "position": 5
}
```

### Spans (Inline Formatting)

Spans apply to specific character ranges within the text.

| Span | Description |
|------|-------------|
| `BOLD` | Bold text |
| `ITALIC` | Italic text |
| `UNDERLINE` | Underlined text |
| `HIGHLIGHT` | Yellow highlight |
| `HIGHLIGHT_GREEN` | Green highlight |
| `HIGHLIGHT_RED` | Red highlight |
| `LINK` | Hyperlink (use `extra` field for URL) |

**Span Structure**
```json
{
    "start": 0,      // Starting character index (inclusive)
    "end": 10,       // Ending character index (exclusive)
    "span": "BOLD",  // Span type
    "extra": null    // Optional: URL for LINK spans
}
```

**Span Examples**

Bold text "Hello" in "Hello World":
```json
{
    "text": "Hello World",
    "spans": [
        { "start": 0, "end": 5, "span": "BOLD" }
    ]
}
```

Multiple formatting:
```json
{
    "text": "Bold and italic text here",
    "spans": [
        { "start": 0, "end": 4, "span": "BOLD" },
        { "start": 9, "end": 15, "span": "ITALIC" }
    ]
}
```

---

## Decoration

The `decoration` object controls background colors.

```json
{
    "decoration": {
        "backgroundColor": -65536  // ARGB integer
    }
}
```

### Common Background Colors (ARGB Integers)

| Color | ARGB Value | Description |
|-------|------------|-------------|
| Red | `-65536` | `0xFFFF0000` |
| Blue | `-16776961` | `0xFF0000FF` |
| Green | `-16711936` | `0xFF00FF00` |
| Yellow | `-256` | `0xFFFFFF00` |
| Magenta | `-65281` | `0xFFFF00FF` |
| Cyan | `-16711681` | `0xFF00FFFF` |
| Gray | `-7829368` | `0xFF888888` |
| Dark Gray | `-12303292` | `0xFF444444` |
| White | `-1` | `0xFFFFFFFF` |
| Black | `-16777216` | `0xFF000000` |

---

## Document Icons

Documents and folders can have icons with optional tint colors.

```json
{
    "icon": {
        "label": "home",
        "tint": -65281
    }
}
```

### Available Icon Labels

| Label | Description |
|-------|-------------|
| `home` | Home icon |
| `save` | Save/disk icon |
| `folder` | Folder icon |
| `file` | File icon |
| `search` | Search/magnifying glass |
| `settings` | Settings/gear |
| `favorites` | Heart/favorite |
| `notifications` | Bell |
| `bold` | Bold text |
| `italic` | Italic text |
| `underline` | Underline |
| `code` | Code/brackets |
| `image` | Image/picture |
| `link` | Link/chain |
| `ai` | AI/wand sparkles |
| `command` | Command icon |
| `chart` | Chart/graph |
| `drawing` | Pencil/drawing |
| `highlight` | Highlighter |
| `play` | Play button |
| `download` | Cloud download |
| `sync` | Sync/refresh |
| `delete` | Trash/delete |
| `add` | Plus sign |
| `close` | X/close |
| `undo` | Undo arrow |
| `redo` | Redo arrow |
| `person` | Person/user |
| `zap` | Lightning bolt |

### Icon Tint Colors

Use the same ARGB integer values as background colors.

---

## Folder Structure

Folders use `.wrfolder.json` extension:

```json
{
    "id": "folder_id",
    "parentId": "root",
    "title": "Folder Name",
    "createdAt": "2026-04-19T14:47:10.866Z",
    "lastUpdatedAt": "2026-04-19T14:47:18.236Z",
    "workspaceId": "disconnected_user",
    "itemCount": 0
}
```

---

## Complete Document Examples

### Simple Note

```json
{
    "id": "note123abc",
    "title": "My Note",
    "workspaceId": "disconnected_user",
    "content": [
        {
            "id": "t1",
            "type": { "name": "title", "number": 11 },
            "text": "My Note"
        },
        {
            "id": "p1",
            "type": { "name": "message", "number": 0 },
            "text": "This is the first paragraph.",
            "position": 1
        },
        {
            "id": "p2",
            "type": { "name": "message", "number": 0 },
            "text": "",
            "position": 2
        }
    ],
    "createdAt": 1776609896651,
    "lastUpdatedAt": 1776609896651,
    "parentId": "root"
}
```

### Document with Headers and Lists

```json
{
    "id": "doc456xyz",
    "title": "Shopping List",
    "workspaceId": "disconnected_user",
    "content": [
        {
            "id": "t1",
            "type": { "name": "title", "number": 11 },
            "text": "Shopping List",
            "decoration": { "backgroundColor": -16711936 }
        },
        {
            "id": "h1",
            "type": { "name": "message", "number": 0 },
            "text": "Groceries",
            "tags": [{ "tag": "H2", "position": 0 }],
            "position": 1
        },
        {
            "id": "i1",
            "type": { "name": "check_item", "number": 10 },
            "text": "Milk",
            "checked": false,
            "position": 2
        },
        {
            "id": "i2",
            "type": { "name": "check_item", "number": 10 },
            "text": "Bread",
            "checked": true,
            "position": 3
        },
        {
            "id": "i3",
            "type": { "name": "check_item", "number": 10 },
            "text": "Eggs",
            "checked": false,
            "position": 4
        },
        {
            "id": "e1",
            "type": { "name": "message", "number": 0 },
            "text": "",
            "position": 5
        }
    ],
    "createdAt": 1776609896651,
    "lastUpdatedAt": 1776609896651,
    "parentId": "root",
    "icon": { "label": "favorites", "tint": -65536 }
}
```

### Document with Formatted Text

```json
{
    "id": "formatted789",
    "title": "Formatted Text Example",
    "workspaceId": "disconnected_user",
    "content": [
        {
            "id": "t1",
            "type": { "name": "title", "number": 11 },
            "text": "Formatted Text Example"
        },
        {
            "id": "p1",
            "type": { "name": "message", "number": 0 },
            "text": "This text has bold and italic formatting.",
            "spans": [
                { "start": 14, "end": 18, "span": "BOLD" },
                { "start": 23, "end": 29, "span": "ITALIC" }
            ],
            "position": 1
        },
        {
            "id": "p2",
            "type": { "name": "message", "number": 0 },
            "text": "Important: This is highlighted text.",
            "spans": [
                { "start": 0, "end": 9, "span": "BOLD" },
                { "start": 11, "end": 36, "span": "HIGHLIGHT" }
            ],
            "position": 2
        },
        {
            "id": "e1",
            "type": { "name": "message", "number": 0 },
            "text": "",
            "position": 3
        }
    ],
    "createdAt": 1776609896651,
    "lastUpdatedAt": 1776609896651,
    "parentId": "root"
}
```

---

## Editing Guidelines

### When Modifying Documents

1. **Preserve IDs**: Keep existing `id` values unless creating new elements
2. **Update Timestamps**: Update `lastUpdatedAt` when making changes
3. **Maintain Positions**: Keep `position` values sequential (0, 1, 2, ...)
4. **Title First**: The first content element should always be `type: "title"`
5. **End with Empty**: Documents typically end with an empty message element
6. **Generate Valid IDs**: Use 10 alphanumeric characters for new IDs

### When Adding Content

1. Create a new StoryStep object with unique ID
2. Set appropriate `type` based on content
3. Assign the next sequential `position` value
4. Add `tags` for headers or `spans` for inline formatting as needed

### When Removing Content

1. Remove the StoryStep from the content array
2. Renumber remaining `position` values to be sequential

### When Formatting Text

1. Use `tags` for block-level formatting (headers)
2. Use `spans` for inline formatting (bold, italic)
3. Ensure span `start` and `end` indices are within text length
4. Multiple spans can overlap for combined formatting

---

## File Naming Convention

Documents: `{Title}_{id}.wrdoc.json`
- Example: `My_Document_abc123xyz.wrdoc.json`

Folders: `{Title}_{id}.wrfolder.json`
- Example: `Projects_folder123.wrfolder.json`

---

## Quick Reference

### Create a Simple Paragraph
```json
{ "id": "NEW_ID", "type": { "name": "message", "number": 0 }, "text": "Your text here", "position": N }
```

### Create a Header
```json
{ "id": "NEW_ID", "type": { "name": "message", "number": 0 }, "text": "Header Text", "tags": [{ "tag": "H2", "position": 0 }], "position": N }
```

### Create a Bullet Point
```json
{ "id": "NEW_ID", "type": { "name": "unordered_list_item", "number": 16 }, "text": "List item", "position": N }
```

### Create a Checkbox
```json
{ "id": "NEW_ID", "type": { "name": "check_item", "number": 10 }, "text": "Todo item", "checked": false, "position": N }
```

### Add Bold Formatting
```json
{ "spans": [{ "start": 0, "end": 4, "span": "BOLD" }] }
```

### Add Header Tag (H1-H4)
```json
{ "tags": [{ "tag": "H1", "position": 0 }] }
```
