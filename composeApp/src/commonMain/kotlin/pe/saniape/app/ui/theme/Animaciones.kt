package pe.saniape.app.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically

/**
 * Duraciones y curvas del movimiento de la app — igual que `Dimens` para el
 * espaciado: una escala nombrada, para no repartir `tween(300)` sueltos.
 *
 * Criterio: corto y sobrio. En una clínica con prisa, una animación lenta es un
 * estorbo — el trabajo es atender pacientes, no mirar transiciones. Nada por
 * encima de ~320ms, y todo lo que se pueda tocar responde al instante.
 */
object Movim {
    /** Respuesta al toque: tiene que sentirse inmediata. */
    const val toque = 120

    /** Aparecer/desaparecer, cambio de pestaña. */
    const val corto = 200

    /** Entrada de una pantalla o de una hoja. */
    const val medio = 280

    /** Lo más lento que se permite (barras de progreso, contadores). */
    const val largo = 320

    /**
     * Desaceleración natural: entra rápido y frena al final, como algo que se
     * posa. Es la misma curva que usa la web (cubic-bezier .22,1,.36,1).
     */
    val salida: Easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

    /** Retardo entre elementos de una lista que entra escalonada. */
    const val escalon = 45
}

/** Aparecer suave, sin movimiento: para contenido que cambia en su sitio. */
fun aparecer(duracion: Int = Movim.corto): EnterTransition =
    fadeIn(tween(duracion, easing = Movim.salida))

fun desaparecer(duracion: Int = Movim.corto): ExitTransition =
    fadeOut(tween(duracion, easing = Movim.salida))

/**
 * Entrada de una pantalla que se apila sobre otra (ficha, detalle): llega desde
 * la derecha, como en cualquier app nativa. Al volver se va por donde vino, que
 * es lo que hace entender "estoy retrocediendo".
 */
fun entrarDetalle(): EnterTransition =
    slideInHorizontally(tween(Movim.medio, easing = Movim.salida)) { ancho -> ancho / 4 } +
        fadeIn(tween(Movim.medio))

fun salirDetalle(): ExitTransition =
    slideOutHorizontally(tween(Movim.corto, easing = Movim.salida)) { ancho -> ancho / 4 } +
        fadeOut(tween(Movim.corto))

/**
 * Hoja que sube desde abajo (modales, resúmenes). El gesto que la gente ya
 * conoce de su teléfono.
 */
fun entrarHoja(): EnterTransition =
    slideInVertically(tween(Movim.medio, easing = Movim.salida)) { alto -> alto / 3 } +
        fadeIn(tween(Movim.corto))

fun salirHoja(): ExitTransition =
    slideOutVertically(tween(Movim.corto, easing = Movim.salida)) { alto -> alto / 3 } +
        fadeOut(tween(Movim.corto))

/**
 * Elemento de lista que entra: sube un poco mientras aparece. Con `escalon`
 * distinto por índice, la lista se arma de arriba a abajo en vez de aparecer
 * de golpe.
 */
fun entrarItem(indice: Int = 0): EnterTransition {
    val retardo = (indice * Movim.escalon).coerceAtMost(6 * Movim.escalon)
    return fadeIn(tween(Movim.corto, delayMillis = retardo, easing = Movim.salida)) +
        slideInVertically(
            tween(Movim.corto, delayMillis = retardo, easing = Movim.salida),
        ) { alto -> alto / 5 }
}

/** Aparición con un leve crecimiento: para avisos y chips que llegan solos. */
fun entrarChip(): EnterTransition =
    fadeIn(tween(Movim.corto)) + scaleIn(tween(Movim.corto, easing = Movim.salida), initialScale = 0.9f)

/** Spec estándar para animar un valor (color, tamaño, posición). */
fun <T> suave(duracion: Int = Movim.corto): FiniteAnimationSpec<T> =
    tween(duracion, easing = Movim.salida)


/**
 * Respuesta física al toque: el elemento se hunde levemente mientras se
 * mantiene pulsado y vuelve al soltarlo.
 *
 * Sin esto, tocar un botón no da ninguna señal hasta que la acción termina — y
 * si tarda, uno vuelve a tocar creyendo que no registró. Es la diferencia entre
 * una interfaz que responde y una que parece colgada.
 */
@Composable
fun Modifier.tocable(
    habilitado: Boolean = true,
    escala: Float = 0.96f,
    onClick: () -> Unit,
): Modifier {
    val fuente = remember { MutableInteractionSource() }
    val presionado by fuente.collectIsPressedAsState()
    val f by animateFloatAsState(
        targetValue = if (presionado && habilitado) escala else 1f,
        animationSpec = suave(Movim.toque),
        label = "toque",
    )
    return this
        .scale(f)
        .clickable(
            interactionSource = fuente,
            indication = null,
            enabled = habilitado,
            onClick = onClick,
        )
}
