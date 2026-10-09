package pe.saniape.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import pe.saniape.app.data.staff.formatearDinero
import pe.saniape.app.data.staff.monedaActiva
import pe.saniape.app.data.staff.simboloMoneda

/**
 * Multipaís: la moneda de lo que se está mirando. La ficha de un paciente la
 * fija con la sede del paciente; sin nadie que la fije, la de la sede activa
 * (y PEN en una clínica de un solo local, como siempre).
 */
val LocalMoneda = staticCompositionLocalOf<String?> { null }

/** Moneda vigente en este punto de la pantalla. */
@Composable
@ReadOnlyComposable
fun monedaUI(): String = LocalMoneda.current ?: monedaActiva()

/** Símbolo de la moneda vigente ("S/" en Perú). */
@Composable
@ReadOnlyComposable
fun simboloUI(): String = simboloMoneda(monedaUI())

/** Monto con la moneda vigente (PEN: idéntico a soles()). */
@Composable
@ReadOnlyComposable
fun dineroUI(monto: Double?): String = formatearDinero(monto, monedaUI())
