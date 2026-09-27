import Foundation
import WrModels
import WrNetwork

struct ServerResponse: Decodable {
    let message: String
}

/// Workspaces (teams) the user belongs to, and their members.
public final class WorkspacesAPI {
    private let client: APIClient

    public init(client: APIClient) {
        self.client = client
    }

    public func userWorkspaces() async throws -> [Workspace] {
        try await client.get("api/workspace/user")
    }

    public func createWorkspace(name: String) async throws {
        struct Body: Encodable { let name: String }
        try await client.perform(.post, "api/workspace/create", body: Body(name: name))
    }

    /// Only admins can list users. An empty team answers 404, which is mapped to an empty list.
    public func users(workspaceId: String) async throws -> [WorkspaceUser] {
        do {
            return try await client.get("api/workspace/\(workspaceId)/users")
        } catch APIError.notFound {
            return []
        }
    }

    public func addUser(email: String, workspaceId: String, role: WorkspaceRole) async throws {
        struct Body: Encodable {
            let email: String
            let workspaceId: String
            let role: String
        }

        do {
            try await client.perform(
                .post,
                "api/workspace/user",
                body: Body(email: email.trimmingCharacters(in: .whitespaces).lowercased(), workspaceId: workspaceId, role: role.rawValue)
            )
        } catch APIError.notFound {
            throw APIError.badRequest("There's no Writeopia user with this email.")
        } catch APIError.conflict {
            throw APIError.badRequest("This user is already in the team.")
        }
    }

    public func changeRole(workspaceId: String, userId: String, role: WorkspaceRole) async throws {
        struct Body: Encodable {
            let workspaceId: String
            let userId: String
            let newRole: String
        }

        do {
            try await client.perform(
                .put, "api/workspace/role", body: Body(workspaceId: workspaceId, userId: userId, newRole: role.rawValue)
            )
        } catch APIError.conflict {
            throw APIError.badRequest("A team needs at least one admin.")
        }
    }

    public func removeUser(workspaceId: String, userId: String) async throws {
        try await client.perform(.delete, "api/workspace/\(workspaceId)/user/\(userId)")
    }

    public func rename(workspaceId: String, newName: String) async throws {
        struct Body: Encodable {
            let workspaceId: String
            let newName: String
        }
        try await client.perform(.put, "api/workspace/name", body: Body(workspaceId: workspaceId, newName: newName))
    }
}
