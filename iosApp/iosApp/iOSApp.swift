import SwiftUI
import CryptoKit
import AuthenticationServices
import ComposeApp

@main
struct iOSApp: App {
    init() {
        installGoogleWebAuthBridge()
        installAppleSignInBridge()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .ignoresSafeArea(.all)         // Compose dibuja hasta los bordes (edge-to-edge).
                .ignoresSafeArea(.keyboard)    // el teclado lo maneja Compose.
        }
    }

    /// Conecta el login de Google EN-APP (ASWebAuthenticationSession, framework del sistema)
    /// con el puente Kotlin (GoogleWebAuthPuente en AuthGoogle.ios.kt). Reemplaza al SDK
    /// GoogleSignIn (que arrastraba Firebase/AppCheck/etc. y hacía el build ~40 min más lento).
    /// Abre la hoja de autenticación del sistema (Apple lo exige, Guideline 4) y devuelve la
    /// URL de callback (saniape://login#access_token=...) para que handleDeeplinks la complete.
    private func installGoogleWebAuthBridge() {
        GoogleWebAuthPuente.shared.iniciar = { authUrl, callbackScheme, onResult in
            let coordinator = GoogleWebAuthCoordinator(onResult: { callbackUrl, error in
                _ = onResult(callbackUrl, error)
            })
            coordinator.start(authUrl: authUrl, callbackScheme: callbackScheme)
        }
    }

    /// Conecta Sign in with Apple (framework AuthenticationServices, nativo del sistema) con el
    /// puente Kotlin (AppleSignInPuente). El coordinador maneja el flujo y el nonce.
    private func installAppleSignInBridge() {
        AppleSignInPuente.shared.proveedor = { callback in
            // El callback Kotlin devuelve KotlinUnit; lo envolvemos en un cierre -> Void.
            let coordinator = AppleSignInCoordinator(callback: { idToken, nonce, error in
                _ = callback(idToken, nonce, error)
            })
            coordinator.start()
        }
    }
}

/// Ejecuta el login de Google dentro de la app con ASWebAuthenticationSession (Apple lo exige
/// en Guideline 4 en lugar de abrir el Safari externo). Al terminar, entrega a Kotlin la URL de
/// callback (`saniape://login#access_token=...`) para que handleDeeplinks complete la sesión.
/// Se retiene a sí mismo hasta que el sistema responde.
final class GoogleWebAuthCoordinator: NSObject, ASWebAuthenticationPresentationContextProviding {

    private let onResult: (String?, String?) -> Void
    private var session: ASWebAuthenticationSession?
    private var selfRetain: GoogleWebAuthCoordinator?

    init(onResult: @escaping (String?, String?) -> Void) {
        self.onResult = onResult
        super.init()
        self.selfRetain = self   // vivo hasta que responda el sistema
    }

    func start(authUrl: String, callbackScheme: String) {
        guard let url = URL(string: authUrl) else {
            finish(nil, "URL de autorización inválida.")
            return
        }
        let session = ASWebAuthenticationSession(url: url, callbackURLScheme: callbackScheme) { [weak self] callbackURL, error in
            if let error = error {
                let nsErr = error as NSError
                // Cancelación del usuario: sin token y sin error visible.
                if nsErr.domain == ASWebAuthenticationSessionErrorDomain
                    && nsErr.code == ASWebAuthenticationSessionError.canceledLogin.rawValue {
                    self?.finish(nil, nil)
                } else {
                    self?.finish(nil, error.localizedDescription)
                }
                return
            }
            self?.finish(callbackURL?.absoluteString, nil)
        }
        session.presentationContextProvider = self
        session.prefersEphemeralWebBrowserSession = false
        self.session = session
        session.start()
    }

    private func finish(_ callbackUrl: String?, _ error: String?) {
        onResult(callbackUrl, error)
        session = nil
        selfRetain = nil
    }

    func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor {
        let scene = UIApplication.shared.connectedScenes
            .first(where: { $0.activationState == .foregroundActive }) as? UIWindowScene
        return scene?.keyWindow ?? ASPresentationAnchor()
    }
}

/// Maneja el flujo de "Iniciar sesión con Apple" (ASAuthorizationController) y entrega a Kotlin
/// el idToken + el nonce CRUDO. Se retiene a sí mismo hasta que llega el callback del sistema.
final class AppleSignInCoordinator: NSObject, ASAuthorizationControllerDelegate,
    ASAuthorizationControllerPresentationContextProviding {

    private let rawNonce: String
    private let callback: (String?, String?, String?) -> Void
    private var selfRetain: AppleSignInCoordinator?

    init(callback: @escaping (String?, String?, String?) -> Void) {
        self.rawNonce = AppleSignInCoordinator.randomNonce()
        self.callback = callback
        super.init()
        self.selfRetain = self   // vivo hasta que responda el sistema
    }

    func start() {
        let request = ASAuthorizationAppleIDProvider().createRequest()
        request.requestedScopes = [.fullName, .email]
        request.nonce = AppleSignInCoordinator.sha256(rawNonce)   // hash a Apple; crudo a Supabase
        let controller = ASAuthorizationController(authorizationRequests: [request])
        controller.delegate = self
        controller.presentationContextProvider = self
        controller.performRequests()
    }

    func authorizationController(controller: ASAuthorizationController,
                                 didCompleteWithAuthorization authorization: ASAuthorization) {
        defer { selfRetain = nil }
        guard let cred = authorization.credential as? ASAuthorizationAppleIDCredential,
              let tokenData = cred.identityToken,
              let idToken = String(data: tokenData, encoding: .utf8) else {
            callback(nil, nil, "No se obtuvo el token de Apple.")
            return
        }
        callback(idToken, rawNonce, nil)
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithError error: Error) {
        defer { selfRetain = nil }
        callback(nil, nil, error.localizedDescription)
    }

    func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        let scene = UIApplication.shared.connectedScenes
            .first(where: { $0.activationState == .foregroundActive }) as? UIWindowScene
        return scene?.keyWindow ?? ASPresentationAnchor()
    }

    static func sha256(_ input: String) -> String {
        let digest = SHA256.hash(data: Data(input.utf8))
        return digest.map { String(format: "%02x", $0) }.joined()
    }

    static func randomNonce(length: Int = 32) -> String {
        let charset = Array("0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz-._")
        var bytes = [UInt8](repeating: 0, count: length)
        _ = SecRandomCopyBytes(kSecRandomDefault, length, &bytes)
        return String(bytes.map { charset[Int($0) % charset.count] })
    }
}
