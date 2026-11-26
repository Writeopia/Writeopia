import Foundation

/// What became of a call that writes: mirrors `LiveDocuments.Outcome` of the Compose app.
public enum LiveOutcome: Equatable, Sendable {
    case written(documentId: String, title: String, blocks: Int)
    case folderCreated(folderId: String, name: String, parentId: String)
    case folders([LiveFolder], currentFolderId: String)
    case finished
    case headerImageSet(documentId: String, title: String, url: String)
    case tags([LiveTag])
    case tagged(documentId: String, title: String, added: [LiveTagged], already: [String])
    case failed(String)
    /// The person stopped Claude Code, in the middle of this call or a moment before it.
    case stopped
}

/// Creates documents and adds to them, in the editor on screen.
public protocol LiveDocumentsWriting: AnyObject {
    func create(title: String, markdown: String?, folderId: String?) async -> LiveOutcome
    func append(documentId: String?, markdown: String) async -> LiveOutcome
    func createFolder(name: String, parentId: String?) async -> LiveOutcome
    func listFolders() async -> LiveOutcome
    func finish() async -> LiveOutcome
    func setHeaderImage(documentId: String?, description: String?) async -> LiveOutcome
    func listTags() async -> LiveOutcome
    func addTags(documentId: String?, names: [String]) async -> LiveOutcome
}

/// A tag of the workspace catalogue, as Claude Code sees it.
public struct LiveTag: Equatable, Sendable {
    public let id: String
    public let name: String
    public let description: String

    public init(id: String, name: String, description: String = "") {
        self.id = id
        self.name = name
        self.description = description
    }
}

/// A tag put on a document, and whether it was made for it.
public struct LiveTagged: Equatable, Sendable {
    public let name: String
    public let created: Bool

    public init(name: String, created: Bool) {
        self.name = name
        self.created = created
    }
}

/// The Model Context Protocol, as JSON-RPC over HTTP POST (the "streamable HTTP" transport,
/// answering with plain JSON, no event stream). Two tools, which only add: create_document and
/// append_to_document.
///
/// Mirrors `McpHandler.kt` of the Compose app: same tools, descriptions and answers.
public final class McpHandler {
    private let documents: LiveDocumentsWriting
    private let serverVersion: String

    public init(documents: LiveDocumentsWriting, serverVersion: String) {
        self.documents = documents
        self.serverVersion = serverVersion
    }

    /// The answer to `body`, or nil when it holds only notifications (HTTP 202, no body).
    public func handle(_ body: Data) async -> Data? {
        guard let message = try? JSONSerialization.jsonObject(with: body, options: [.fragmentsAllowed]) else {
            return encode(error(id: NSNull(), code: Self.parseError, message: "The body is not JSON"))
        }

        if let batch = message as? [Any] {
            var answers: [[String: Any]] = []
            for element in batch {
                if let answer = await handleOne(element) { answers.append(answer) }
            }
            return answers.isEmpty ? nil : encode(answers)
        }
        return await handleOne(message).map(encode)
    }

    private func handleOne(_ element: Any) async -> [String: Any]? {
        guard let request = element as? [String: Any] else {
            return error(id: NSNull(), code: Self.invalidRequest, message: "A request must be an object")
        }
        // A notification (no id) or a response from the client: nothing to answer.
        guard let id = request["id"], !(id is NSNull) else { return nil }
        guard let method = request["method"] as? String else {
            return error(id: id, code: Self.invalidRequest, message: "The request has no method")
        }
        let params = request["params"] as? [String: Any] ?? [:]

        switch method {
        case "initialize": return result(id: id, initialize(params))
        case "ping": return result(id: id, [:])
        case "tools/list": return result(id: id, ["tools": Self.tools])
        case "tools/call": return await callTool(id: id, params: params)
        default: return error(id: id, code: Self.methodNotFound, message: "Unknown method: \(method)")
        }
    }

    private func initialize(_ params: [String: Any]) -> [String: Any] {
        let asked = params["protocolVersion"] as? String
        let version = asked.flatMap { Self.supportedVersions.contains($0) ? $0 : nil } ?? Self.supportedVersions[0]
        return [
            "protocolVersion": version,
            "capabilities": ["tools": ["listChanged": false]],
            "serverInfo": ["name": "writeopia", "title": "Writeopia", "version": serverVersion],
            "instructions": Self.instructions,
        ]
    }

    private func callTool(id: Any, params: [String: Any]) async -> [String: Any] {
        let name = params["name"] as? String
        let arguments = params["arguments"] as? [String: Any] ?? [:]
        func argument(_ key: String) -> String? { arguments[key] as? String }

        let outcome: LiveOutcome
        switch name {
        case Self.create:
            guard let title = argument("title"), !title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
                return result(id: id, toolText("The title is required.", isError: true))
            }
            outcome = await documents.create(title: title, markdown: argument("markdown"), folderId: argument("folder_id"))
        case Self.append:
            guard let markdown = argument("markdown"), !markdown.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
                return result(id: id, toolText("The markdown is required.", isError: true))
            }
            outcome = await documents.append(documentId: argument("document_id"), markdown: markdown)
        case Self.listFolders:
            outcome = await documents.listFolders()
        case Self.finish:
            outcome = await documents.finish()
        case Self.listTags:
            outcome = await documents.listTags()
        case Self.addTags:
            guard let names = arguments["tags"] as? [Any] else {
                return result(id: id, toolText("The tags are required: a list of names.", isError: true))
            }
            outcome = await documents.addTags(documentId: argument("document_id"), names: names.compactMap { $0 as? String })
        case Self.setHeaderImage:
            outcome = await documents.setHeaderImage(documentId: argument("document_id"), description: argument("description"))
        case Self.createFolder:
            guard let name = argument("name"), !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
                return result(id: id, toolText("The name is required.", isError: true))
            }
            outcome = await documents.createFolder(name: name, parentId: argument("parent_folder_id"))
        default:
            return error(id: id, code: Self.invalidParams, message: "Unknown tool: \(name ?? "")")
        }

        switch outcome {
        case let .written(documentId, title, blocks):
            return result(id: id, toolText(
                "Wrote \(blocks) blocks to \"\(title)\" (document_id: \(documentId)). " +
                    "To add more, call \(Self.append) with this document_id."
            ))
        case let .folderCreated(folderId, name, parentId):
            return result(id: id, toolText(
                "Created the folder \"\(name)\" (folder_id: \(folderId)) inside \(parentId). " +
                    "Pass this folder_id to \(Self.create) to write documents in it, or as parent_folder_id to " +
                    "\(Self.createFolder) to make folders inside it."
            ))
        case .finished:
            return result(id: id, toolText(Self.finished))
        case let .tags(tags):
            return result(id: id, toolText(Self.describeTags(tags)))
        case let .tagged(documentId, title, added, already):
            return result(id: id, toolText(Self.describeTagged(documentId: documentId, title: title, added: added, already: already)))
        case let .headerImageSet(documentId, title, url):
            return result(id: id, toolText("Put a header picture in \"\(title)\" (document_id: \(documentId)): \(url)"))
        case let .folders(folders, currentFolderId):
            return result(id: id, toolText(FolderTree.describe(folders, currentFolderId: currentFolderId)))
        case let .failed(reason):
            return result(id: id, toolText(reason, isError: true))
        case .stopped:
            return result(id: id, toolText(Self.stopped, isError: true))
        }
    }

    static func describeTags(_ tags: [LiveTag]) -> String {
        guard !tags.isEmpty else { return "The workspace has no tags yet. \(addTags) makes them as it tags a document." }
        return "Tags of the workspace (\(tags.count)):\n" + tags.map { tag in
            "- \(tag.name)" + (tag.description.trimmingCharacters(in: .whitespaces).isEmpty ? "" : ": \(tag.description)")
        }.joined(separator: "\n")
    }

    static func describeTagged(documentId: String, title: String, added: [LiveTagged], already: [String]) -> String {
        let document = "\"\(title)\" (document_id: \(documentId))"
        var text = added.isEmpty
            ? "Nothing to add to \(document)."
            : "Tagged \(document) with: " + added.map { $0.created ? "\($0.name) (new)" : $0.name }.joined(separator: ", ") + "."
        if !already.isEmpty { text += " It already had: \(already.joined(separator: ", "))." }
        return text
    }

    private func toolText(_ text: String, isError: Bool = false) -> [String: Any] {
        ["content": [["type": "text", "text": text]], "isError": isError]
    }

    private func result(id: Any, _ result: [String: Any]) -> [String: Any] {
        ["jsonrpc": "2.0", "id": id, "result": result]
    }

    private func error(id: Any, code: Int, message: String) -> [String: Any] {
        ["jsonrpc": "2.0", "id": id, "error": ["code": code, "message": message]]
    }

    private func encode(_ value: Any) -> Data {
        (try? JSONSerialization.data(withJSONObject: value, options: [.withoutEscapingSlashes])) ?? Data()
    }

    // MARK: - The tools, word for word the same as the Compose app

    public static let create = "create_document"
    public static let append = "append_to_document"
    public static let createFolder = "create_folder"
    public static let listFolders = "list_folders"
    public static let finish = "finish_writing"
    public static let setHeaderImage = "set_header_image"
    public static let listTags = "list_tags"
    public static let addTags = "add_tags"

    static let finished = "Writeopia left AI mode: the person can edit the documents again."

    public static let supportedVersions = ["2025-06-18", "2025-03-26", "2024-11-05"]

    private static let parseError = -32700
    private static let invalidRequest = -32600
    private static let methodNotFound = -32601
    private static let invalidParams = -32602

    static let stopped =
        "The person stopped you from writing in Writeopia (they pressed Stop). What was typed until " +
        "then is kept. Stop now: do not call the Writeopia tools again unless the person asks you to."

    static let markdownGuide =
        "Markdown, one block per line: `#` to `####` headings, `- item` bullets, `- [ ] task` and " +
        "`- [x] done` checkboxes, `> text` highlighted blocks, ``` fenced code, `---` dividers, " +
        "`![alt](https://...)` images on a line of their own, `$$latex$$` equations. Inside a " +
        "line: **bold**, *italic*, ==highlight==, [text](https://...) links. Blank lines only " +
        "separate blocks. Numbered lists and tables are not supported: write bullets instead."

    static let instructions =
        "Writes documents in the Writeopia app open on this computer. The person watches the text " +
        "appear in the editor as it is written. Tools only add: they never change nor delete " +
        "what is already there. To organize documents, see the folders with \(listFolders) (reuse " +
        "one that fits before making a new one), make folders with \(createFolder), and pass a " +
        "folder_id to \(create). For anything longer than a few paragraphs, write in parts so " +
        "the person can follow along: \(create) with the title and the first section, then one " +
        "\(append) call per following section, with the document_id \(create) returned. When you " +
        "are done writing, call \(finish): until then the app stays in AI mode and the person " +
        "can't edit. " +
        markdownGuide

    static var tools: [[String: Any]] {
        [
            [
                "name": create,
                "title": "Create a Writeopia document",
                "description":
                    "Creates a new document in the folder folder_id (or the folder open in Writeopia), opens it in the editor and " +
                    "types the title and the markdown into it. Returns the document_id. Start long " +
                    "documents with the first section only and add the others with \(append). " +
                    markdownGuide,
                "inputSchema": [
                    "type": "object",
                    "properties": [
                        "title": ["type": "string", "description": "The title of the document, plain text."],
                        "markdown": [
                            "type": "string",
                            "description": "The first content of the document. Do not repeat the title.",
                        ],
                        "folder_id": [
                            "type": "string",
                            "description":
                                "The folder_id returned by \(createFolder). Leave it out to use the folder open in Writeopia.",
                        ],
                    ],
                    "required": ["title"],
                    "additionalProperties": false,
                ] as [String: Any],
            ],
            [
                "name": append,
                "title": "Append to a Writeopia document",
                "description":
                    "Opens the document in the Writeopia editor and types the markdown at its end, after " +
                    "everything already there. Without document_id, writes into the document open in " +
                    "the editor now. " + markdownGuide,
                "inputSchema": [
                    "type": "object",
                    "properties": [
                        "markdown": ["type": "string", "description": "The content to add at the end of the document."],
                        "document_id": [
                            "type": "string",
                            "description":
                                "The document_id returned by \(create). Leave it out to write into the document open in the editor.",
                        ],
                    ],
                    "required": ["markdown"],
                    "additionalProperties": false,
                ] as [String: Any],
            ],
            [
                "name": setHeaderImage,
                "title": "Draw a header picture for a Writeopia document",
                "description":
                    "Has the Writeopia cloud AI draw a header picture for the document, from its title " +
                    "and text plus your description, and puts it in the header. Write the content " +
                    "first: the picture is drawn from it. It costs the person's Writeopia AI tokens " +
                    "and takes a while, so use it only when the person asks for a header picture or " +
                    "an illustrated document, once per document. Fails when the document already has " +
                    "a header picture, or when the workspace is offline.",
                "inputSchema": [
                    "type": "object",
                    "properties": [
                        "document_id": [
                            "type": "string",
                            "description":
                                "The document_id returned by \(create). Leave it out to use the document open in the editor.",
                        ],
                        "description": [
                            "type": "string",
                            "description":
                                "What the picture should show or its style, in a sentence. Optional: the picture follows the document anyway.",
                        ],
                    ],
                    "additionalProperties": false,
                ] as [String: Any],
            ],
            [
                "name": listTags,
                "title": "List the Writeopia tags",
                "description":
                    "Lists the tags of the workspace open in Writeopia, with their descriptions. Only " +
                    "reads. Call it before \(addTags), to reuse the tags that fit instead of making " +
                    "near-duplicates.",
                "inputSchema": [
                    "type": "object",
                    "properties": [String: Any](),
                    "additionalProperties": false,
                ] as [String: Any],
            ],
            [
                "name": addTags,
                "title": "Tag a Writeopia document",
                "description":
                    "Adds tags to a document, by name: a tag of the workspace with that name (any " +
                    "capitalization) is reused, and a new one is made otherwise. Tags the document " +
                    "already has stay. Prefer the existing tags from \(listTags); make new ones only " +
                    "when none fits. At most 10 at a time.",
                "inputSchema": [
                    "type": "object",
                    "properties": [
                        "tags": [
                            "type": "array",
                            "items": ["type": "string"],
                            "description": "The tag names, short: one to three words each.",
                        ] as [String: Any],
                        "document_id": [
                            "type": "string",
                            "description":
                                "The document_id returned by \(create). Leave it out to use the document open in the editor.",
                        ],
                    ],
                    "required": ["tags"],
                    "additionalProperties": false,
                ] as [String: Any],
            ],
            [
                "name": finish,
                "title": "Finish writing in Writeopia",
                "description":
                    "Call once, when you are done with all the writing for now (after the last " +
                    "\(append)). Writeopia leaves AI mode, so the person can edit again. Writing " +
                    "again later is fine: the next call puts the app back in AI mode.",
                "inputSchema": [
                    "type": "object",
                    "properties": [String: Any](),
                    "additionalProperties": false,
                ] as [String: Any],
            ],
            [
                "name": listFolders,
                "title": "List the Writeopia folders",
                "description":
                    "Lists the folders of the workspace open in Writeopia as a tree, each with its folder_id, " +
                    "and tells which one the app has open. Only reads. Call it before \(createFolder), to " +
                    "reuse a folder that already fits, and to find the folder_id for \(create).",
                "inputSchema": [
                    "type": "object",
                    "properties": [String: Any](),
                    "additionalProperties": false,
                ] as [String: Any],
            ],
            [
                "name": createFolder,
                "title": "Create a Writeopia folder",
                "description":
                    "Creates a folder in Writeopia, inside parent_folder_id (or the folder open in the " +
                    "app), and shows it. Returns the folder_id: pass it to \(create) to write documents " +
                    "in the folder, or as parent_folder_id to make folders inside it.",
                "inputSchema": [
                    "type": "object",
                    "properties": [
                        "name": ["type": "string", "description": "The name of the folder, plain text."],
                        "parent_folder_id": [
                            "type": "string",
                            "description":
                                "The folder_id of the folder to make it in, as \(createFolder) returned it. Leave it out to use the folder open in Writeopia.",
                        ],
                    ],
                    "required": ["name"],
                    "additionalProperties": false,
                ] as [String: Any],
            ],
        ]
    }
}
