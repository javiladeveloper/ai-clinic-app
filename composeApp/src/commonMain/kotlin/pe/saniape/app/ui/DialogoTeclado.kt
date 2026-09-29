package pe.saniape.app.ui

import androidx.compose.runtime.Composable

/**
 * Hace que el diálogo que lo llama se ACHIQUE cuando sale el teclado, en vez de
 * quedar tapado por él. Se llama desde el contenido de un Dialog/AlertDialog.
 *
 * En Android la ventana de un diálogo solo se desplaza para que se vea el campo
 * donde se escribe: el resto del formulario y el botón de guardar quedaban
 * debajo del teclado y no había forma de llegar a ellos sin cerrarlo
 * ("Completar diagnóstico", 28/09/2026). Achicada, el cuerpo se desplaza y el
 * botón queda a la vista.
 */
@Composable
expect fun AjustarDialogoAlTeclado()
