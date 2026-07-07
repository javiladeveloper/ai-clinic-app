import SwiftUI

@main
struct iOSApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
                .ignoresSafeArea(.all)         // Compose dibuja hasta los bordes (edge-to-edge).
                .ignoresSafeArea(.keyboard)    // el teclado lo maneja Compose.
        }
    }
}
