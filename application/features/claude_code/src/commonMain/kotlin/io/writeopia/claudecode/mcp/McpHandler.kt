package io.writeopia.claudecode.mcp

import io.writeopia.claudecode.LiveTag
import io.writeopia.claudecode.LiveDocuments
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * The Model Context Protocol, as JSON-RPC over HTTP POST (the "streamable HTTP" transport,
 * answering with plain JSON, no event stream). Two tools, which only add: create_document and
 * append_to_document.
 *
 * Mirrored by `McpHandler.swift` in the native app: same tools, descriptions and answers.
 */
class McpHandler(
    private val documents: LiveDocuments,
    private val serverVersion: String,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** The answer to [body], or null when it holds only notifications (HTTP 202, no body). */
    suspend fun handle(body: String): String? {
        val message = try {
            json.parseToJsonElement(body)
        } catch (_: Exception) {
            return error(JsonNull, PARSE_ERROR, "The body is not JSON").toString()
        }

        return when (message) {
            is JsonArray -> message.mapNotNull { element -> handleOne(element) }
                .takeIf { it.isNotEmpty() }
                ?.let(::JsonArray)
                ?.toString()

            else -> handleOne(message)?.toString()
        }
    }

    private suspend fun handleOne(element: JsonElement): JsonObject? {
        val request = element as? JsonObject
            ?: return error(JsonNull, INVALID_REQUEST, "A request must be an object")
        val id = request["id"]
        val method = (request["method"] as? JsonPrimitive)?.contentOrNull
        val params = request["params"] as? JsonObject ?: JsonObject(emptyMap())

        // A notification (no id) or a response from the client: nothing to answer.
        if (id == null || id is JsonNull) return null
        if (method == null) return error(id, INVALID_REQUEST, "The request has no method")

        return when (method) {
            "initialize" -> result(id, initialize(params))
            "ping" -> result(id, JsonObject(emptyMap()))
            "tools/list" -> result(id, buildJsonObject { put("tools", tools) })
            "tools/call" -> callTool(id, params)
            else -> error(id, METHOD_NOT_FOUND, "Unknown method: $method")
        }
    }

    private fun initialize(params: JsonObject): JsonObject {
        val asked = (params["protocolVersion"] as? JsonPrimitive)?.contentOrNull
        val version = asked?.takeIf { it in SUPPORTED_VERSIONS } ?: SUPPORTED_VERSIONS.first()

        return buildJsonObject {
            put("protocolVersion", version)
            putJsonObject("capabilities") { putJsonObject("tools") { put("listChanged", false) } }
            putJsonObject("serverInfo") {
                put("name", "writeopia")
                put("title", "Writeopia")
                put("version", serverVersion)
            }
            put("instructions", INSTRUCTIONS)
        }
    }

    private suspend fun callTool(id: JsonElement, params: JsonObject): JsonObject {
        val name = (params["name"] as? JsonPrimitive)?.contentOrNull
        val arguments = params["arguments"] as? JsonObject ?: JsonObject(emptyMap())

        fun argument(key: String): String? =
            (arguments[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

        val outcome = when (name) {
            CREATE -> {
                val title = argument("title")?.takeIf { it.isNotBlank() }
                    ?: return result(id, toolText("The title is required.", isError = true))
                documents.create(title, argument("markdown"), argument("folder_id"))
            }

            APPEND -> {
                val markdown = argument("markdown")?.takeIf { it.isNotBlank() }
                    ?: return result(id, toolText("The markdown is required.", isError = true))
                documents.append(argument("document_id"), markdown)
            }

            LIST_FOLDERS -> documents.listFolders()

            FINISH -> documents.finish()

            SET_HEADER_IMAGE -> documents.setHeaderImage(argument("document_id"), argument("description"))

            LIST_TAGS -> documents.listTags()

            ADD_TAGS -> {
                val names = (arguments["tags"] as? JsonArray)
                    ?.mapNotNull { element -> (element as? JsonPrimitive)?.takeIf { it.isString }?.content }
                    ?: return result(id, toolText("The tags are required: a list of names.", isError = true))
                documents.addTags(argument("document_id"), names)
            }

            CREATE_FOLDER -> {
                val folderName = argument("name")?.takeIf { it.isNotBlank() }
                    ?: return result(id, toolText("The name is required.", isError = true))
                documents.createFolder(folderName, argument("parent_folder_id"))
            }

            else -> return error(id, INVALID_PARAMS, "Unknown tool: $name")
        }

        return result(
            id,
            when (outcome) {
                is LiveDocuments.Outcome.Written -> toolText(
                    "Wrote ${outcome.blocks} blocks to \"${outcome.title}\" (document_id: ${outcome.documentId}). " +
                        "To add more, call $APPEND with this document_id."
                )

                is LiveDocuments.Outcome.FolderCreated -> toolText(
                    "Created the folder \"${outcome.name}\" (folder_id: ${outcome.folderId}) inside ${outcome.parentId}. " +
                        "Pass this folder_id to $CREATE to write documents in it, or as parent_folder_id to " +
                        "$CREATE_FOLDER to make folders inside it."
                )

                LiveDocuments.Outcome.Finished -> toolText(FINISHED)

                is LiveDocuments.Outcome.Tags -> toolText(describeTags(outcome.tags))

                is LiveDocuments.Outcome.Tagged -> toolText(describeTagged(outcome))

                is LiveDocuments.Outcome.HeaderImageSet -> toolText(
                    "Put a header picture in \"${outcome.title}\" (document_id: ${outcome.documentId}): ${outcome.url}"
                )

                is LiveDocuments.Outcome.Folders -> toolText(FolderTree.describe(outcome.folders, outcome.currentFolderId))

                is LiveDocuments.Outcome.Failed -> toolText(outcome.reason, isError = true)

                LiveDocuments.Outcome.Stopped -> toolText(STOPPED, isError = true)
            }
        )
    }

    private fun describeTags(tags: List<LiveTag>): String =
        if (tags.isEmpty()) {
            "The workspace has no tags yet. $ADD_TAGS makes them as it tags a document."
        } else {
            "Tags of the workspace (${tags.size}):\n" + tags.joinToString("\n") { tag ->
                "- ${tag.name}" + if (tag.description.isBlank()) "" else ": ${tag.description}"
            }
        }

    private fun describeTagged(tagged: LiveDocuments.Outcome.Tagged): String {
        val document = "\"${tagged.title}\" (document_id: ${tagged.documentId})"
        val added = tagged.added.joinToString(", ") { (name, created) -> if (created) "$name (new)" else name }
        return buildString {
            append(if (tagged.added.isEmpty()) "Nothing to add to $document." else "Tagged $document with: $added.")
            if (tagged.already.isNotEmpty()) append(" It already had: ${tagged.already.joinToString(", ")}.")
        }
    }

    private fun toolText(text: String, isError: Boolean = false) = buildJsonObject {
        put(
            "content",
            buildJsonArray {
                addJsonObject {
                    put("type", "text")
                    put("text", text)
                }
            }
        )
        put("isError", isError)
    }

    private fun result(id: JsonElement, result: JsonObject) = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id)
        put("result", result)
    }

    private fun error(id: JsonElement, code: Int, message: String) = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id)
        putJsonObject("error") {
            put("code", code)
            put("message", message)
        }
    }

    companion object {
        const val CREATE = "create_document"
        const val APPEND = "append_to_document"
        const val CREATE_FOLDER = "create_folder"
        const val LIST_FOLDERS = "list_folders"
        const val FINISH = "finish_writing"
        const val SET_HEADER_IMAGE = "set_header_image"
        const val LIST_TAGS = "list_tags"
        const val ADD_TAGS = "add_tags"

        const val FINISHED = "Writeopia left AI mode: the person can edit the documents again."

        val SUPPORTED_VERSIONS = listOf("2025-06-18", "2025-03-26", "2024-11-05")

        private const val PARSE_ERROR = -32700
        private const val INVALID_REQUEST = -32600
        private const val METHOD_NOT_FOUND = -32601
        private const val INVALID_PARAMS = -32602

        const val STOPPED =
            "The person stopped you from writing in Writeopia (they pressed Stop). What was typed until " +
                "then is kept. Stop now: do not call the Writeopia tools again unless the person asks you to."

        const val MARKDOWN_GUIDE =
            "Markdown, one block per line: `#` to `####` headings, `- item` bullets, `- [ ] task` and " +
                "`- [x] done` checkboxes, `> text` highlighted blocks, ``` fenced code, `---` dividers, " +
                "`![alt](https://...)` images on a line of their own, `\$\$latex\$\$` equations. Inside a " +
                "line: **bold**, *italic*, ==highlight==, [text](https://...) links. Blank lines only " +
                "separate blocks. Numbered lists and tables are not supported: write bullets instead."

        const val INSTRUCTIONS =
            "Writes documents in the Writeopia app open on this computer. The person watches the text " +
                "appear in the editor as it is written. Tools only add: they never change nor delete " +
                "what is already there. To organize documents, see the folders with $LIST_FOLDERS (reuse " +
                "one that fits before making a new one), make folders with $CREATE_FOLDER, and pass a " +
                "folder_id to $CREATE. For anything longer than a few paragraphs, write in parts so " +
                "the person can follow along: $CREATE with the title and the first section, then one " +
                "$APPEND call per following section, with the document_id $CREATE returned. When you " +
                "are done writing, call $FINISH: until then the app stays in AI mode and the person " +
                "can't edit. " +
                MARKDOWN_GUIDE

        val tools: JsonArray = buildJsonArray {
            addJsonObject {
                put("name", CREATE)
                put("title", "Create a Writeopia document")
                put(
                    "description",
                    "Creates a new document in the folder folder_id (or the folder open in Writeopia), opens it in the editor and " +
                        "types the title and the markdown into it. Returns the document_id. Start long " +
                        "documents with the first section only and add the others with $APPEND. " +
                        MARKDOWN_GUIDE
                )
                putJsonObject("inputSchema") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("title") {
                            put("type", "string")
                            put("description", "The title of the document, plain text.")
                        }
                        putJsonObject("markdown") {
                            put("type", "string")
                            put("description", "The first content of the document. Do not repeat the title.")
                        }
                        putJsonObject("folder_id") {
                            put("type", "string")
                            put(
                                "description",
                                "The folder_id returned by $CREATE_FOLDER. Leave it out to use the folder open in Writeopia."
                            )
                        }
                    }
                    put("required", buildJsonArray { add("title") })
                    put("additionalProperties", false)
                }
            }
            addJsonObject {
                put("name", APPEND)
                put("title", "Append to a Writeopia document")
                put(
                    "description",
                    "Opens the document in the Writeopia editor and types the markdown at its end, after " +
                        "everything already there. Without document_id, writes into the document open in " +
                        "the editor now. " + MARKDOWN_GUIDE
                )
                putJsonObject("inputSchema") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("markdown") {
                            put("type", "string")
                            put("description", "The content to add at the end of the document.")
                        }
                        putJsonObject("document_id") {
                            put("type", "string")
                            put(
                                "description",
                                "The document_id returned by $CREATE. Leave it out to write into the document open in the editor."
                            )
                        }
                    }
                    put("required", buildJsonArray { add("markdown") })
                    put("additionalProperties", false)
                }
            }
            addJsonObject {
                put("name", SET_HEADER_IMAGE)
                put("title", "Draw a header picture for a Writeopia document")
                put(
                    "description",
                    "Has the Writeopia cloud AI draw a header picture for the document, from its title " +
                        "and text plus your description, and puts it in the header. Write the content " +
                        "first: the picture is drawn from it. It costs the person's Writeopia AI tokens " +
                        "and takes a while, so use it only when the person asks for a header picture or " +
                        "an illustrated document, once per document. Fails when the document already has " +
                        "a header picture, or when the workspace is offline."
                )
                putJsonObject("inputSchema") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("document_id") {
                            put("type", "string")
                            put(
                                "description",
                                "The document_id returned by $CREATE. Leave it out to use the document open in the editor."
                            )
                        }
                        putJsonObject("description") {
                            put("type", "string")
                            put(
                                "description",
                                "What the picture should show or its style, in a sentence. Optional: the picture follows the document anyway."
                            )
                        }
                    }
                    put("additionalProperties", false)
                }
            }
            addJsonObject {
                put("name", LIST_TAGS)
                put("title", "List the Writeopia tags")
                put(
                    "description",
                    "Lists the tags of the workspace open in Writeopia, with their descriptions. Only " +
                        "reads. Call it before $ADD_TAGS, to reuse the tags that fit instead of making " +
                        "near-duplicates."
                )
                putJsonObject("inputSchema") {
                    put("type", "object")
                    putJsonObject("properties") {}
                    put("additionalProperties", false)
                }
            }
            addJsonObject {
                put("name", ADD_TAGS)
                put("title", "Tag a Writeopia document")
                put(
                    "description",
                    "Adds tags to a document, by name: a tag of the workspace with that name (any " +
                        "capitalization) is reused, and a new one is made otherwise. Tags the document " +
                        "already has stay. Prefer the existing tags from $LIST_TAGS; make new ones only " +
                        "when none fits. At most 10 at a time."
                )
                putJsonObject("inputSchema") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("tags") {
                            put("type", "array")
                            putJsonObject("items") { put("type", "string") }
                            put("description", "The tag names, short: one to three words each.")
                        }
                        putJsonObject("document_id") {
                            put("type", "string")
                            put(
                                "description",
                                "The document_id returned by $CREATE. Leave it out to use the document open in the editor."
                            )
                        }
                    }
                    put("required", buildJsonArray { add("tags") })
                    put("additionalProperties", false)
                }
            }
            addJsonObject {
                put("name", FINISH)
                put("title", "Finish writing in Writeopia")
                put(
                    "description",
                    "Call once, when you are done with all the writing for now (after the last " +
                        "$APPEND). Writeopia leaves AI mode, so the person can edit again. Writing " +
                        "again later is fine: the next call puts the app back in AI mode."
                )
                putJsonObject("inputSchema") {
                    put("type", "object")
                    putJsonObject("properties") {}
                    put("additionalProperties", false)
                }
            }
            addJsonObject {
                put("name", LIST_FOLDERS)
                put("title", "List the Writeopia folders")
                put(
                    "description",
                    "Lists the folders of the workspace open in Writeopia as a tree, each with its folder_id, " +
                        "and tells which one the app has open. Only reads. Call it before $CREATE_FOLDER, to " +
                        "reuse a folder that already fits, and to find the folder_id for $CREATE."
                )
                putJsonObject("inputSchema") {
                    put("type", "object")
                    putJsonObject("properties") {}
                    put("additionalProperties", false)
                }
            }
            addJsonObject {
                put("name", CREATE_FOLDER)
                put("title", "Create a Writeopia folder")
                put(
                    "description",
                    "Creates a folder in Writeopia, inside parent_folder_id (or the folder open in the " +
                        "app), and shows it. Returns the folder_id: pass it to $CREATE to write documents " +
                        "in the folder, or as parent_folder_id to make folders inside it."
                )
                putJsonObject("inputSchema") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("name") {
                            put("type", "string")
                            put("description", "The name of the folder, plain text.")
                        }
                        putJsonObject("parent_folder_id") {
                            put("type", "string")
                            put(
                                "description",
                                "The folder_id of the folder to make it in, as $CREATE_FOLDER returned it. Leave it out to use the folder open in Writeopia."
                            )
                        }
                    }
                    put("required", buildJsonArray { add("name") })
                    put("additionalProperties", false)
                }
            }
        }
    }
}
