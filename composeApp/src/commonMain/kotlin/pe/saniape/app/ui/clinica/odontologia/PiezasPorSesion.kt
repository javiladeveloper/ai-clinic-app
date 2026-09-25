package pe.saniape.app.ui.clinica.odontologia

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import pe.saniape.app.data.staff.COLOR_REALIZADO
import pe.saniape.app.data.staff.DienteHallazgo
import pe.saniape.app.data.staff.HallazgoDental
import pe.saniape.app.data.staff.OdontogramaRepo
import pe.saniape.app.data.staff.SesionFicha
import pe.saniape.app.data.staff.contarPlanPiezas
import pe.saniape.app.data.staff.filasPiezasTratadas
import pe.saniape.app.data.staff.filasPlanPiezas
import pe.saniape.app.ui.theme.Sania

/**
 * "¿Qué se le hizo hoy?" — al completar una sesión DENTAL.
 *
 * Muestra TODO lo pendiente del paciente, no solo lo del tratamiento de la
 * sesión: en el sillón el dentista trata lo que se pueda ese día, y a veces es
 * una pieza de otro plan. Lo marcado viaja como `piezas` al endpoint de
 * completar y la WEB lo pasa a Realizado con la sesión anotada (el odontograma
 * se pinta de azul) — ver lib/odontograma-sesion.ts. Si la sesión se revierte,
 * se cancela o se borra, la web lo devuelve a pendiente.
 *
 * Gemelo de components/odontologia/PiezasTratadas.tsx. SOLO se monta cuando el
 * tratamiento / la cita es dental (lo decide quien llama).
 *
 * [onProcedimiento] recibe el nombre del servicio de la pieza marcada
 * ("Resina") para sumarlo a los procedimientos realizados.
 */
@Composable
fun PiezasTratadas(
    pacienteId: String,
    tratamientoId: String?,
    sesionId: String?,
    seleccion: List<String>,
    onSeleccion: (List<String>) -> Unit,
    onProcedimiento: (String) -> Unit = {},
    /**
     * Avisa cuando terminó de cargar: true = listo, false = la red falló. Quien
     * llama NO debe mandar `piezas` mientras tanto (ni si falló): una lista
     * vacía le quitaría a la sesión las piezas que ya tenía.
     */
    onCargado: (ok: Boolean) -> Unit = {},
) {
    val c = Sania.colors
    var catalogo by remember { mutableStateOf<List<HallazgoDental>>(emptyList()) }
    var hallazgos by remember { mutableStateOf<List<DienteHallazgo>>(emptyList()) }
    var procs by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var cargando by remember { mutableStateOf(true) }
    var fallo by remember { mutableStateOf(false) }
    val onSeleccionActual by rememberUpdatedState(onSeleccion)
    val seleccionActual by rememberUpdatedState(seleccion)
    val onCargadoActual by rememberUpdatedState(onCargado)

    LaunchedEffect(pacienteId) {
        cargando = true
        // Las tres lecturas son independientes: en paralelo (antes, en fila:
        // el modal de completar esperaba la suma de las tres).
        val halOk = coroutineScope {
            val cat = async { OdontogramaRepo.catalogo() }
            val hal = async { OdontogramaRepo.hallazgosONull(pacienteId) }
            val pr = async { OdontogramaRepo.nombresProcedimientos() }
            catalogo = cat.await()
            procs = pr.await()
            hal.await()?.also { hallazgos = it }
        }
        fallo = halOk == null
        cargando = false
        onCargadoActual(!fallo)
        // Si se abre sobre una sesión que ya había resuelto piezas, salen marcadas.
        // Se precarga UNA vez, cuando llegan los datos.
        val previas = filasPiezasTratadas(hallazgos, catalogo, sesionId, tratamientoId)
            .filter { sesionId != null && it.sesionId == sesionId }.map { it.id }
        if (previas.isNotEmpty() && seleccionActual.isEmpty()) onSeleccionActual(previas)
    }

    if (cargando) {
        // La forma de la lista (título + 3 filas) en vez de un texto suelto: al
        // llegar los datos el modal no pega un salto.
        EsqueletoPiezas()
        return
    }
    if (fallo) {
        Text(
            "No se pudo cargar el odontograma. La sesión se completa igual; las piezas se marcan después desde el odontograma.",
            color = c.error, fontSize = 12.sp,
        )
        return
    }
    val filas = remember(hallazgos, catalogo, sesionId, tratamientoId) {
        filasPiezasTratadas(hallazgos, catalogo, sesionId, tratamientoId)
    }
    val porId = remember(catalogo) { catalogo.associateBy { it.id } }

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🦷 ¿QUÉ SE LE HIZO HOY?", color = c.textoSuave, fontSize = 10.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (filas.isNotEmpty()) {
                Text("${seleccion.count { id -> filas.any { it.id == id } }} de ${filas.size}",
                    color = c.textoSuave, fontSize = 11.sp)
            }
        }
        if (filas.isEmpty()) {
            Text("No hay hallazgos pendientes en el odontograma.", color = c.textoSuave, fontSize = 12.sp)
            return@Column
        }
        val marcadas = seleccion.toSet()
        val hayDeOtros = filas.any { it.tratamientoId != tratamientoId }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp)),
        ) {
            filas.forEachIndexed { i, r ->
                val h = porId[r.hallazgoId]
                val otro = r.tratamientoId != tratamientoId
                val primeraDeOtros = hayDeOtros && otro && i > 0 && filas[i - 1].tratamientoId == tratamientoId
                if (primeraDeOtros) {
                    Text(
                        "OTROS PENDIENTES DEL PACIENTE", color = c.textoSuave, fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth().background(c.fondo).padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                val marcada = r.id in marcadas
                Row(
                    Modifier.fillMaxWidth()
                        .clickable {
                            if (!marcada) {
                                h?.procedimientoId?.let { procs[it] }?.takeIf { it.isNotBlank() }?.let(onProcedimiento)
                            }
                            onSeleccion(if (marcada) seleccion - r.id else seleccion + r.id)
                        }
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(22.dp).clip(RoundedCornerShape(5.dp))
                            .background(if (marcada) c.navy else c.superficie)
                            .border(1.5.dp, if (marcada) c.navy else c.borde, RoundedCornerShape(5.dp)),
                        contentAlignment = Alignment.Center,
                    ) { if (marcada) Text("✓", color = c.sobreNavy, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.size(10.dp).clip(CircleShape)
                        .background(colorDe(if (marcada) COLOR_REALIZADO else h?.color)))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        val donde = if (r.diente == "BOCA") "Boca" else "Pieza ${r.diente}"
                        val caras = r.superficies?.joinToString("")?.let { " · $it" } ?: ""
                        Text("$donde · ${h?.nombre ?: "Hallazgo"}$caras", color = c.texto, fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold)
                        // De otro plan: se marca como hecha, pero se cobra en SU plan.
                        if (otro && r.tratamientoId != null && marcada) {
                            Text("Es de otro tratamiento: se cobra en ese, no en esta sesión.",
                                color = c.pend, fontSize = 11.sp)
                        }
                    }
                }
                if (i < filas.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(c.borde))
            }
        }
        Text(
            "Lo que marques pasa a realizado (azul) en el odontograma, con esta sesión. Lo demás sigue pendiente.",
            color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/**
 * "Piezas del plan: 2 de 4 hechas" dentro de la tarjeta de un tratamiento
 * DENTAL: cada pieza, su estado y en qué sesión se hizo. Entran las piezas del
 * plan (vinculadas al crearlo desde el presupuesto) y las que sus sesiones
 * resolvieron aunque fueran de otro plan. Sin piezas no dibuja nada.
 *
 * Gemelo de components/odontologia/PlanPiezas.tsx.
 */
@Composable
fun PlanPiezas(
    pacienteId: String,
    tratamientoId: String,
    sesiones: List<SesionFicha>,
    recargaToken: Int = 0,
) {
    val c = Sania.colors
    var catalogo by remember { mutableStateOf<List<HallazgoDental>>(emptyList()) }
    var hallazgos by remember { mutableStateOf<List<DienteHallazgo>?>(null) }
    LaunchedEffect(pacienteId, recargaToken) {
        // Cada tarjeta dental monta esto: el catálogo sale de la caché del repo
        // y los hallazgos del paciente se piden UNA vez para todas las tarjetas
        // (el repo fusiona los pedidos simultáneos). Si la red falla se conserva
        // lo que ya se veía.
        coroutineScope {
            val cat = async { OdontogramaRepo.catalogo() }
            val hal = async { OdontogramaRepo.hallazgosONull(pacienteId) }
            catalogo = cat.await()
            hal.await()?.let { hallazgos = it }
        }
    }
    val sesionPorId = remember(sesiones) { sesiones.associateBy { it.id } }
    val filas = remember(hallazgos, tratamientoId, sesionPorId) {
        hallazgos?.let { filasPlanPiezas(it, tratamientoId, sesionPorId.keys) }.orEmpty()
    }
    val porId = remember(catalogo) { catalogo.associateBy { it.id } }
    // Aparece deslizando cuando llegan los datos (sin salto de la tarjeta) y
    // no se muestra nada si el plan no tiene piezas.
    AnimatedVisibility(
        visible = filas.isNotEmpty(),
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
    val (hechas, total) = contarPlanPiezas(filas)
    Column(
        Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(Sania.shape.sm.dp))
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp)),
    ) {
        Row(
            Modifier.fillMaxWidth().background(c.fondo).padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🦷 PIEZAS DEL PLAN", color = c.textoSuave, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f))
            Text("$hechas de $total hechas", color = if (hechas == total) c.ok else c.textoSuave,
                fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        filas.forEach { r ->
            val h = porId[r.hallazgoId]
            val hecha = r.estado == "Realizado"
            val ses = r.sesionId?.let { sesionPorId[it] }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.borde))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(colorDe(if (hecha) COLOR_REALIZADO else h?.color)))
                Spacer(Modifier.width(8.dp))
                val donde = if (r.diente == "BOCA") "Boca" else "Pieza ${r.diente}"
                val caras = r.superficies?.joinToString("")?.let { " · $it" } ?: ""
                Text("$donde · ${h?.nombre ?: "Hallazgo"}$caras", color = c.texto, fontSize = 12.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(6.dp))
                val etiqueta = if (hecha) {
                    "✓ " + (ses?.let { s ->
                        val f = s.fecha.takeIf { it.length >= 10 }?.let { " · ${it.substring(8, 10)}/${it.substring(5, 7)}" } ?: ""
                        "Sesión #${s.numero}$f"
                    } ?: "Hecha")
                } else "⏳ Pendiente"
                Text(etiqueta, color = if (hecha) colorDe(COLOR_REALIZADO) else c.pend,
                    fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
    }
}

/** Silueta de "¿Qué se le hizo hoy?" mientras carga. Estática (sin animación). */
@Composable
private fun EsqueletoPiezas() {
    val c = Sania.colors
    val gris = c.borde.copy(alpha = 0.6f)
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.padding(bottom = 8.dp).width(150.dp).height(10.dp).clip(RoundedCornerShape(3.dp)).background(gris))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp)),
        ) {
            repeat(3) { i ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(22.dp).clip(RoundedCornerShape(5.dp)).background(gris))
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.width((170 - i * 30).dp).height(12.dp).clip(RoundedCornerShape(3.dp)).background(gris))
                }
                if (i < 2) Box(Modifier.fillMaxWidth().height(1.dp).background(c.borde))
            }
        }
    }
}
