package pe.saniape.app

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/**
 * Punto de entrada de iOS: envuelve el App() de Compose en un UIViewController que el
 * proyecto Xcode (iosApp) monta con UIViewControllerRepresentable en SwiftUI.
 *
 * Equivale a lo que en Android hace MainActivity.setContent { App() }.
 * A diferencia de Android, iOS no necesita Preferencias.init(): NSUserDefaults está
 * siempre disponible.
 */
fun MainViewController(): UIViewController = ComposeUIViewController { App() }
