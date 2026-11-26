import Foundation

/// An HTTP/1.1 request read off a socket, enough for the MCP server: the method, the path, the
/// headers (names in lowercase) and the body, which `Content-Length` says the size of.
struct HTTPRequest: Equatable {
    let method: String
    let path: String
    let headers: [String: String]
    let body: Data

    /// Requests larger than this are refused.
    static let maxSize = 4 * 1024 * 1024

    enum Parse: Equatable {
        /// More bytes are needed.
        case incomplete
        case request(HTTPRequest)
        case invalid
    }

    static func parse(_ data: Data) -> Parse {
        let separator = Data("\r\n\r\n".utf8)
        guard let headEnd = data.range(of: separator) else {
            return data.count > maxSize ? .invalid : .incomplete
        }
        guard let head = String(data: data[data.startIndex..<headEnd.lowerBound], encoding: .utf8) else {
            return .invalid
        }

        var lines = head.components(separatedBy: "\r\n")
        let requestLine = lines.removeFirst().split(separator: " ")
        guard requestLine.count >= 2 else { return .invalid }

        var headers: [String: String] = [:]
        for line in lines {
            guard let colon = line.firstIndex(of: ":") else { continue }
            let name = line[..<colon].trimmingCharacters(in: .whitespaces).lowercased()
            headers[name] = line[line.index(after: colon)...].trimmingCharacters(in: .whitespaces)
        }

        let length = headers["content-length"].flatMap(Int.init) ?? 0
        guard length >= 0, length <= maxSize else { return .invalid }
        let bodyStart = headEnd.upperBound
        guard data.distance(from: bodyStart, to: data.endIndex) >= length else { return .incomplete }

        let path = String(requestLine[1]).split(separator: "?", maxSplits: 1).first.map(String.init) ?? ""
        return .request(HTTPRequest(
            method: String(requestLine[0]).uppercased(),
            path: path,
            headers: headers,
            body: data[bodyStart..<data.index(bodyStart, offsetBy: length)]
        ))
    }
}

/// The bytes of an HTTP/1.1 answer that closes the connection.
func httpResponse(status: Int, body: Data? = nil) -> Data {
    let reason = switch status {
    case 200: "OK"
    case 202: "Accepted"
    case 400: "Bad Request"
    case 401: "Unauthorized"
    case 403: "Forbidden"
    case 404: "Not Found"
    case 405: "Method Not Allowed"
    case 503: "Service Unavailable"
    default: "Error"
    }
    var head = "HTTP/1.1 \(status) \(reason)\r\nConnection: close\r\nContent-Length: \(body?.count ?? 0)\r\n"
    if body != nil { head += "Content-Type: application/json\r\n" }
    head += "\r\n"
    return Data(head.utf8) + (body ?? Data())
}
