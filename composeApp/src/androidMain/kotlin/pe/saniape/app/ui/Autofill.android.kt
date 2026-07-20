package pe.saniape.app.ui

import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.autofill.AutofillNode
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalAutofill
import androidx.compose.ui.platform.LocalAutofillTree

/**
 * Android: registra el campo en el árbol de Autofill y pide al sistema que muestre
 * las credenciales guardadas cuando el campo recibe el foco.
 *
 * Se usa la API `AutofillNode` (experimental pero PÚBLICA en Compose 1.7) en vez de
 * `ContentType`, que es `internal` en Compose Multiplatform 1.7.3.
 *
 * Cómo funciona: se declara qué tipo de dato espera el campo, se le dice al árbol
 * dónde está en pantalla (`onGloballyPositioned`) y al enfocarlo se pide el popup del
 * gestor. Sin las tres piezas no aparece nada.
 */
@OptIn(ExperimentalComposeUiApi::class)
actual fun Modifier.autofill(tipo: TipoCampo, onRelleno: (String) -> Unit): Modifier = composed {
    val autofill = LocalAutofill.current
    val arbol = LocalAutofillTree.current

    val nodo = remember(tipo) {
        AutofillNode(
            autofillTypes = listOf(
                when (tipo) {
                    TipoCampo.USUARIO -> AutofillType.Username
                    TipoCampo.CONTRASENA -> AutofillType.Password
                }
            ),
            // El sistema entrega aquí lo que el usuario eligió en el gestor; hay que
            // volcarlo al estado del campo o el toque no haría nada visible.
            onFill = { onRelleno(it) },
        )
    }
    remember(nodo) { arbol.plusAssign(nodo); nodo }

    this
        .onGloballyPositioned { nodo.boundingBox = it.boundsInWindow() }
        .onFocusChanged { estado ->
            autofill?.run {
                if (estado.isFocused) requestAutofillForNode(nodo) else cancelAutofillForNode(nodo)
            }
        }
}
