import Foundation
import Combine

@MainActor
final class APIClient: ObservableObject {
    static let shared = APIClient()
    @Published private(set) var token: String? = TokenStore.shared.read()
    @Published var baseURL = UserDefaults.standard.string(forKey: "moneyflow.api.baseURL") ?? "https://YOUR_API_HOST/"
    private let session: URLSession
    private let decoder: JSONDecoder
    private let encoder: JSONEncoder
    private init() {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 20
        config.timeoutIntervalForResource = 35
        session = URLSession(configuration: config)
        decoder = JSONDecoder(); encoder = JSONEncoder()
    }

    func setBaseURL(_ value: String) { baseURL = value.hasSuffix("/") ? value : value + "/"; UserDefaults.standard.set(baseURL, forKey: "moneyflow.api.baseURL") }
    func authenticate(email: String, password: String, register: Bool) async throws -> SessionResponse {
        let payload = try encoder.encode(AuthBody(email: email, password: password))
        let result: SessionResponse = try await request(register ? "api/auth/register" : "api/auth/login", method: "POST", bodyData: payload, authenticated: false)
        try TokenStore.shared.save(result.token); token = result.token
        return result
    }
    func logout() { TokenStore.shared.clear(); token = nil }

    func get<T: Decodable>(_ path: String) async throws -> T { try await request(path, method: "GET", authenticated: true) }
    func post<T: Decodable, B: Encodable>(_ path: String, body: B) async throws -> T { try await request(path, method: "POST", bodyData: encoder.encode(body), authenticated: true) }
    func patch<T: Decodable, B: Encodable>(_ path: String, body: B) async throws -> T { try await request(path, method: "PATCH", bodyData: encoder.encode(body), authenticated: true) }
    func delete<T: Decodable>(_ path: String) async throws -> T { try await request(path, method: "DELETE", authenticated: true) }

    private func request<T: Decodable>(_ path: String, method: String, bodyData: Data? = nil, authenticated: Bool = true) async throws -> T {
        guard let root = URL(string: baseURL), ["https", "http"].contains(root.scheme?.lowercased() ?? ""), let url = URL(string: path, relativeTo: root) else { throw ClientError.badURL }
        if root.scheme?.lowercased() == "http" && !["localhost", "127.0.0.1"].contains(root.host ?? "") { throw ClientError.insecureHTTP }
        var req = URLRequest(url: url); req.httpMethod = method; req.setValue("application/json", forHTTPHeaderField: "Accept")
        if let bodyData { req.httpBody = bodyData; req.setValue("application/json", forHTTPHeaderField: "Content-Type") }
        if authenticated, let token { req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization") }
        let (data, response) = try await session.data(for: req)
        guard let http = response as? HTTPURLResponse else { throw ClientError.invalidResponse }
        guard (200..<300).contains(http.statusCode) else {
            let apiError = try? decoder.decode(APIError.self, from: data)
            if http.statusCode == 401 { logout() }
            throw ClientError.server(apiError?.message ?? apiError?.error ?? "Request failed (HTTP \(http.statusCode))")
        }
        if T.self == EmptyResponse.self && data.isEmpty { return EmptyResponse() as! T }
        do { return try decoder.decode(T.self, from: data) } catch { throw ClientError.decoding(error.localizedDescription) }
    }
}

private struct AuthBody: Encodable { let email: String; let password: String }

enum ClientError: LocalizedError {
    case badURL, insecureHTTP, invalidResponse, server(String), decoding(String)
    var errorDescription: String? {
        switch self {
        case .badURL: return "Укажи корректный адрес API в настройках."
        case .insecureHTTP: return "Для удалённого сервера требуется HTTPS. HTTP разрешён только для localhost."
        case .invalidResponse: return "Сервер вернул некорректный ответ."
        case .server(let message): return message
        case .decoding(let message): return "Не удалось прочитать ответ сервера: \(message)"
        }
    }
}
