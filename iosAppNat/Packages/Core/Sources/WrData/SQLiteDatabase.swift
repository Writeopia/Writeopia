import Foundation
import SQLite3

/// Thin wrapper around the system SQLite library.
final class SQLiteDatabase {
    enum Value {
        case text(String?)
        case integer(Int64?)
        case real(Double?)
    }

    struct SQLiteError: Error, CustomStringConvertible {
        let description: String
    }

    nonisolated(unsafe) private var handle: OpaquePointer?
    nonisolated(unsafe) private var statements: [String: OpaquePointer] = [:]

    init(url: URL) throws {
        try FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
        guard sqlite3_open_v2(url.path(percentEncoded: false), &handle, SQLITE_OPEN_READWRITE | SQLITE_OPEN_CREATE, nil) == SQLITE_OK else {
            throw SQLiteError(description: "Could not open the database: \(String(cString: sqlite3_errmsg(handle)))")
        }
        try execute("PRAGMA journal_mode = WAL")
        try execute("PRAGMA foreign_keys = ON")
    }

    deinit {
        for statement in statements.values {
            sqlite3_finalize(statement)
        }
        sqlite3_close(handle)
    }

    /// Runs one or more statements without parameters.
    func execute(_ sql: String) throws {
        guard sqlite3_exec(handle, sql, nil, nil, nil) == SQLITE_OK else {
            throw error()
        }
    }

    /// Runs a statement with parameters.
    func run(_ sql: String, _ values: [Value] = []) throws {
        let statement = try prepare(sql, values)
        defer { sqlite3_reset(statement) }
        let result = sqlite3_step(statement)
        guard result == SQLITE_DONE || result == SQLITE_ROW else { throw error() }
    }

    /// Runs a query and maps each row.
    func query<T>(_ sql: String, _ values: [Value] = [], row: (Row) throws -> T?) throws -> [T] {
        let statement = try prepare(sql, values)
        defer { sqlite3_reset(statement) }

        var results: [T] = []
        while true {
            let result = sqlite3_step(statement)
            if result == SQLITE_DONE { break }
            guard result == SQLITE_ROW else { throw error() }
            if let value = try row(Row(statement: statement)) {
                results.append(value)
            }
        }
        return results
    }

    func transaction(_ body: () throws -> Void) throws {
        try execute("BEGIN IMMEDIATE")
        do {
            try body()
            try execute("COMMIT")
        } catch {
            try? execute("ROLLBACK")
            throw error
        }
    }

    struct Row {
        let statement: OpaquePointer

        func text(_ index: Int32) -> String? {
            guard sqlite3_column_type(statement, index) != SQLITE_NULL, let text = sqlite3_column_text(statement, index) else {
                return nil
            }
            return String(cString: text)
        }

        func integer(_ index: Int32) -> Int64? {
            sqlite3_column_type(statement, index) == SQLITE_NULL ? nil : sqlite3_column_int64(statement, index)
        }

        func real(_ index: Int32) -> Double? {
            sqlite3_column_type(statement, index) == SQLITE_NULL ? nil : sqlite3_column_double(statement, index)
        }

        func bool(_ index: Int32) -> Bool {
            sqlite3_column_int64(statement, index) != 0
        }
    }

    private func prepare(_ sql: String, _ values: [Value]) throws -> OpaquePointer {
        let statement: OpaquePointer
        if let cached = statements[sql] {
            statement = cached
        } else {
            var prepared: OpaquePointer?
            guard sqlite3_prepare_v2(handle, sql, -1, &prepared, nil) == SQLITE_OK, let prepared else {
                throw error()
            }
            statements[sql] = prepared
            statement = prepared
        }

        sqlite3_clear_bindings(statement)
        let transient = unsafeBitCast(-1, to: sqlite3_destructor_type.self)
        for (offset, value) in values.enumerated() {
            let index = Int32(offset + 1)
            switch value {
            case .text(let text?):
                sqlite3_bind_text(statement, index, text, -1, transient)
            case .integer(let integer?):
                sqlite3_bind_int64(statement, index, integer)
            case .real(let real?):
                sqlite3_bind_double(statement, index, real)
            case .text(nil), .integer(nil), .real(nil):
                sqlite3_bind_null(statement, index)
            }
        }
        return statement
    }

    private func error() -> SQLiteError {
        SQLiteError(description: String(cString: sqlite3_errmsg(handle)))
    }
}

extension SQLiteDatabase.Value {
    static func bool(_ value: Bool) -> Self { .integer(value ? 1 : 0) }
}
