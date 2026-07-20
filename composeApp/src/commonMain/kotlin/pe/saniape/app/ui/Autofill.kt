package pe.saniape.app.ui

import androidx.compose.ui.Modifier

/**
 * Marca un campo para el gestor de contraseñas del sistema (Autofill de Android,
 * llavero de iOS).
 *
 * Sin esto el gestor no reconoce el formulario de login: ni ofrece guardar la
 * credencial la primera vez ni la rellena después, y el usuario acaba escribiendo
 * su correo y contraseña cada vez que entra.
 *
 * Es `expect/actual` porque el autofill es cosa de cada plataforma; la API común de
 * Compose (`ContentType`) es `internal` en Compose Multiplatform 1.7.3 y no se puede
 * usar desde aquí.
 */
enum class TipoCampo { USUARIO, CONTRASENA }

/** [onRelleno] recibe el valor que eligió el usuario en el gestor de contraseñas. */
expect fun Modifier.autofill(tipo: TipoCampo, onRelleno: (String) -> Unit): Modifier
