import SwiftUI
import GoogleSignIn
import CryptoKit
import ComposeApp

@main
struct iOSApp: App {
    init() {
        installGoogleSignInBridge()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .ignoresSafeArea(.all)         // Compose dibuja hasta los bordes (edge-to-edge).
                .ignoresSafeArea(.keyboard)    // el teclado lo maneja Compose.
                .onOpenURL { url in
                    GIDSignIn.sharedInstance.handle(url)   // callback OAuth de Google
                }
        }
    }

    /// Conecta el flujo nativo GoogleSignIn (SDK iOS) con el puente Kotlin
    /// (GoogleSignInPuente en AuthGoogle.ios.kt). GIDSignIn lee GIDClientID/GIDServerClientID
    /// del Info.plist.
    ///
    /// NONCE: GoTrue valida sha256(nonce_enviado) == nonce_del_token. Generamos un nonce
    /// crudo, mandamos su SHA-256 a Google (que lo devuelve en el idToken) y pasamos el crudo
    /// a Kotlin para Supabase. Así ambos lados coinciden.
    private func installGoogleSignInBridge() {
        GoogleSignInPuente.shared.proveedorIdToken = { callback in
            guard let root = topViewController() else {
                callback(nil, nil, "No se pudo presentar el login de Google.")
                return
            }
            let rawNonce = randomNonceString()
            let hashedNonce = sha256(rawNonce)
            GIDSignIn.sharedInstance.signIn(
                withPresenting: root,
                hint: nil,
                additionalScopes: nil,
                nonce: hashedNonce
            ) { result, error in
                if let error = error {
                    callback(nil, nil, error.localizedDescription)
                    return
                }
                guard let idToken = result?.user.idToken?.tokenString else {
                    callback(nil, nil, "No se obtuvo el idToken de Google.")
                    return
                }
                callback(idToken, rawNonce, nil)   // idToken + nonce CRUDO para Supabase
            }
        }
    }

    private func topViewController() -> UIViewController? {
        let scene = UIApplication.shared.connectedScenes
            .first(where: { $0.activationState == .foregroundActive }) as? UIWindowScene
        var vc = scene?.keyWindow?.rootViewController
        while let presented = vc?.presentedViewController { vc = presented }
        return vc
    }

    /// Nonce aleatorio (crudo) para el flujo OIDC.
    private func randomNonceString(length: Int = 32) -> String {
        let charset = Array("0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz-._")
        var result = ""
        var bytes = [UInt8](repeating: 0, count: length)
        _ = SecRandomCopyBytes(kSecRandomDefault, length, &bytes)
        for b in bytes {
            result.append(charset[Int(b) % charset.count])
        }
        return result
    }

    /// SHA-256 en hexadecimal (lo que espera GoTrue del claim `nonce`).
    private func sha256(_ input: String) -> String {
        let digest = SHA256.hash(data: Data(input.utf8))
        return digest.map { String(format: "%02x", $0) }.joined()
    }
}
