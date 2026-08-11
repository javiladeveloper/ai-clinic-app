package pe.saniape.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * Señal de "la app volvió al frente".
 *
 * Las pantallas cargaban sus datos con `LaunchedEffect(Unit)`, que corre UNA vez
 * por montaje: si la pantalla seguía montada —el caso normal, porque el usuario
 * no la cierra— nunca se volvía a consultar. Se agendaba una cita desde el panel
 * y en el celular no aparecía hasta refrescar a mano, aunque el aviso ya hubiera
 * llegado (reportado por el dueño 2026-08-11: "en el home tampoco llega
 * automáticamente, hay que refrescar").
 *
 * Al usar el contador como clave de un LaunchedEffect, cada vuelta al frente
 * vuelve a ejecutar la carga:
 *
 *     LaunchedEffect(Reanudacion.contador) { cargar() }
 *
 * Se elige esto en vez de un sondeo periódico porque el momento en que importa
 * es exactamente ese: el usuario toca el aviso o abre la app para ver lo nuevo.
 * Sondear cada N segundos gastaría batería y datos para nada mientras el celular
 * está en el bolsillo.
 */
object Reanudacion {
    /** Sube cada vez que la app vuelve al frente. Sirve de clave en LaunchedEffect. */
    var contador by mutableIntStateOf(0)
        private set

    /** Lo llama la capa nativa (MainActivity.onResume en Android). */
    fun volvioAlFrente() { contador++ }
}
