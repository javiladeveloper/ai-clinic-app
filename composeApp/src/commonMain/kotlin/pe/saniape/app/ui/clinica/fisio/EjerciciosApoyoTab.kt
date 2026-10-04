package pe.saniape.app.ui.clinica.fisio

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pe.saniape.app.data.offline.ResultadoEscritura
import pe.saniape.app.data.staff.Adherencia
import pe.saniape.app.data.staff.DIAS_SEMANA
import pe.saniape.app.data.staff.DestinoEjercicios
import pe.saniape.app.data.staff.DosisEdicion
import pe.saniape.app.data.staff.EjerciciosRepo
import pe.saniape.app.data.staff.GrupoDestinos
import pe.saniape.app.data.staff.ItemPlanEjercicios
import pe.saniape.app.data.staff.PedidoEjercicios
import pe.saniape.app.data.staff.PlanEjercicios
import pe.saniape.app.data.staff.RegistroEjercicio
import pe.saniape.app.data.staff.SesionDePlan
import pe.saniape.app.data.staff.TratamientoDestino
import pe.saniape.app.data.staff.adherenciaDeItem
import pe.saniape.app.data.staff.adherenciaDePlan
import pe.saniape.app.data.staff.claveDestino
import pe.saniape.app.data.staff.destinoPorDefecto
import pe.saniape.app.data.staff.destinosEjercicios
import pe.saniape.app.data.staff.enlaceWhatsApp
import pe.saniape.app.data.staff.etiquetaPlan
import pe.saniape.app.data.staff.hastaDePlan
import pe.saniape.app.data.staff.hoyClinicaIso
import pe.saniape.app.data.staff.mensajeCompartirPlan
import pe.saniape.app.data.staff.nombreMaterial
import pe.saniape.app.data.staff.planDeDestino
import pe.saniape.app.data.staff.separarPlanes
import pe.saniape.app.data.staff.textoDias
import pe.saniape.app.data.staff.textoDosis
import pe.saniape.app.data.staff.tokenDeRespuesta
import pe.saniape.app.data.staff.urlPlanEjercicios
import pe.saniape.app.data.staff.vigenciaPlan
import pe.saniape.app.data.staff.vigenteDeTratamiento
import pe.saniape.app.ui.AnimacionEjercicio
import pe.saniape.app.ui.MiniaturaEjercicio
import pe.saniape.app.ui.PasosEjercicio
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.clinica.pacientes.CajaSelectorForm
import pe.saniape.app.ui.clinica.pacientes.DialogoForm
import pe.saniape.app.ui.clinica.pacientes.EtqForm
import pe.saniape.app.ui.clinica.pacientes.coloresCampoForm
import pe.saniape.app.ui.recordarAcciones
import pe.saniape.app.ui.theme.Sania

/**
 * Pestaña 🏠 Ejercicios de apoyo de la ficha (solo FISIOTERAPIA). Gemelo de
 * `components/fisio/EjerciciosApoyoTab.tsx`: lo que el fisio le deja al paciente
 * para hacer en casa, elegido de la biblioteca y con SU dosis (series,
 * repeticiones, días, lado, carga, indicaciones), y cuánto lo cumple.
 *
 * SE INDICAN POR SESIÓN: arriba se elige "para qué sesión" (por defecto la última
 * atendida) y lo indicado queda vigente hasta la siguiente; en la siguiente se
 * repiten con un toque o se eligen otros, y lo anterior pasa a historial. También
 * se pueden dejar "al terminar el tratamiento", para que siga en casa.
 *
 * Carga SOLO cuando la pestaña se abre (la monta el padre en su `when`): tres
 * consultas chicas por paciente, en paralelo. Escribe por
 * POST /api/staff/ejercicios/plan — el mismo endpoint que usa la web.
 */

/**
 * Se viene de cerrar una sesión con "dejarle ejercicios": la pestaña abre con ESA
 * sesión elegida — o con "al terminar" si con ella el tratamiento quedó terminado.
 * Sin [sesionId] (la sesión nace al completar la cita y aún no se conoce) cae en
 * la última atendida de [tratamientoId].
 */
data class IndicarEjercicios(val sesionId: String?, val tratamientoId: String?)

/**
 * "🏠 ¿Le dejas ejercicios de apoyo?" del cierre de una sesión de FISIOTERAPIA
 * (ficha, agenda y Sesiones; gemelo de `ofrecerEjercicios` en CierreSesion.tsx).
 * Aquí solo se pregunta: quien lo monta lee [estado] al confirmar y, si la sesión
 * se completó, abre la ficha en la pestaña 🏠 con esa sesión elegida.
 */
@Composable
fun BloqueDejarEjercicios(estado: androidx.compose.runtime.MutableState<Boolean>) {
    val c = Sania.colors
    val si = estado.value
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
            .background(if (si) c.chipBg else c.fondo)
            .border(1.dp, if (si) c.navy else c.borde, RoundedCornerShape(Sania.shape.sm.dp))
            .clickable { estado.value = !si }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(22.dp).clip(RoundedCornerShape(Sania.shape.sm.dp))
                .background(if (si) c.navy else c.superficie)
                .border(1.dp, if (si) c.navy else c.borde, RoundedCornerShape(Sania.shape.sm.dp)),
            contentAlignment = Alignment.Center,
        ) { if (si) Text("✓", color = c.sobreNavy, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("¿Le dejas ejercicios de apoyo?", color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(
                if (si) "Al completar se abre la pestaña 🏠 Ejercicios de su ficha"
                else "Para que los haga en casa hasta la próxima sesión",
                color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 1.dp),
            )
        }
        Text("🏠", fontSize = 16.sp)
    }
}

private fun fechaCorta(f: String?): String {
    if (f.isNullOrBlank() || f.length < 10) return f ?: ""
    return "${f.substring(8, 10)}/${f.substring(5, 7)}/${f.substring(2, 4)}"
}

private val LADOS = listOf("derecho" to "Derecho", "izquierdo" to "Izquierdo", "ambos" to "Ambos")

/** Lo que se puede hacer con un plan desde su tarjeta. */
private class AccionesPlan(
    val agregar: () -> Unit,
    val ajustar: (ItemPlanEjercicios) -> Unit,
    val quitar: (ItemPlanEjercicios) -> Unit,
    val editar: () -> Unit,
    val whatsapp: () -> Unit,
    val copiarEnlace: () -> Unit,
    val verComoPaciente: () -> Unit,
    val estado: (String) -> Unit,
    val revocar: () -> Unit,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EjerciciosApoyoTab(
    pacienteId: String,
    pacienteNombre: String?,
    pacienteTelefono: String?,
    clinicaId: String,
    clinicaNombre: String?,
    /** `puede('sesiones')` y ficha no dada de baja (como la web). */
    puedeEditar: Boolean,
    /** Tratamientos (de fisioterapia) del paciente: de sus sesiones sale "para qué sesión". */
    tratamientos: List<TratamientoDestino>,
    /** La ficha se recargó (Realtime, una gestión): se releen las sesiones y los planes. */
    recargaToken: Int = 0,
    indicarAlAbrir: IndicarEjercicios? = null,
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val acciones = recordarAcciones()
    val hoy = remember { hoyClinicaIso() }
    var token by remember { mutableStateOf(0) }
    var cargando by remember { mutableStateOf(true) }
    var datos by remember { mutableStateOf<EjerciciosRepo.DatosEjercicios?>(null) }
    var fallo by remember { mutableStateOf(false) }
    val idsTratamientos = tratamientos.map { it.id }
    LaunchedEffect(pacienteId, token, recargaToken, idsTratamientos) {
        val r = EjerciciosRepo.cargar(pacienteId, idsTratamientos, hoy)
        if (r != null) { datos = r; fallo = false } else fallo = datos == null
        cargando = false
    }
    fun recargar() { token++ }

    // Se guarda al montar: el destino pedido sigue aquí mientras la pestaña esté abierta.
    val origen = remember(pacienteId) { indicarAlAbrir }
    var elegido by remember(pacienteId) { mutableStateOf<DestinoEjercicios?>(null) }
    var bibliotecaPara by remember { mutableStateOf<DestinoEjercicios?>(null) }
    var agregando by remember { mutableStateOf(false) }
    var ajustar by remember { mutableStateOf<ItemPlanEjercicios?>(null) }
    var editarPlan by remember { mutableStateOf<PlanEjercicios?>(null) }
    var verAnteriores by remember { mutableStateOf(false) }
    var confirmarQuitar by remember { mutableStateOf<ItemPlanEjercicios?>(null) }
    var confirmarFinalizar by remember { mutableStateOf<PlanEjercicios?>(null) }

    if (cargando && datos == null) {
        // Andamio en vez de un aro: la forma de lo que viene.
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(40, 160, 110).forEach { h ->
                Box(Modifier.fillMaxWidth().height(h.dp).clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.chipBg))
            }
        }
        return
    }
    if (fallo) {
        Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No se pudieron cargar los ejercicios de apoyo", color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text("↻ Reintentar", color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp).clickable { cargando = true; recargar() }.padding(8.dp))
        }
        return
    }

    val planes = datos?.planes.orEmpty()
    val registros = datos?.registros.orEmpty()
    val sesiones = datos?.sesiones.orEmpty()
    val (vigentes, anteriores) = remember(planes) { separarPlanes(planes) }
    val grupos = remember(sesiones, tratamientos, hoy) { destinosEjercicios(sesiones, tratamientos, hoy) }
    val opciones = grupos.flatMap { it.opciones }
    // Para cuándo se indica: lo que eligió el fisio; si no, la sesión que se acaba de
    // cerrar ("al terminar" si con ella el tratamiento ya figura terminado) o la última atendida.
    val pedido = origen?.let { o ->
        val terminado = tratamientos.firstOrNull { it.id == o.tratamientoId }?.estado.let { it == "Completado" || it == "Alta" }
        PedidoEjercicios(o.sesionId, o.tratamientoId, alTerminar = terminado)
    }
    val destino = elegido ?: destinoPorDefecto(sesiones, tratamientos, hoy, pedido)
    val planDestino = planDeDestino(planes, destino)
    fun nombreTrat(id: String?): String? = id?.let { t -> tratamientos.firstOrNull { it.id == t }?.nombre }

    /** "Sesión #4 · 24/06/26", "🏁 Al terminar…": cómo se llama un destino en pantalla. */
    fun etiquetaDe(d: DestinoEjercicios): String =
        opciones.firstOrNull { it.clave == claveDestino(d) }?.etiqueta?.removeSuffix(" (la última)")
            ?: planDeDestino(planes, d)?.let { etiquetaPlan(it) }
            ?: sesiones.firstOrNull { it.id == d.sesionId }?.let { etiquetaPlan("sesion", SesionDePlan(it.id, it.numero, it.fecha), it.tratamientoId) }
            ?: "Esta sesión"

    /** Lo vigente de ese tratamiento, si no es el plan del propio destino: lo que se puede repetir. */
    fun repetibleEn(d: DestinoEjercicios): PlanEjercicios? {
        val v = vigenteDeTratamiento(planes, d.tratamientoId) ?: return null
        if (v.id == planDeDestino(planes, d)?.id) return null
        return v.takeIf { it.activos.isNotEmpty() }
    }
    fun textoRepetir(p: PlanEjercicios): String {
        val n = p.activos.size
        return "↩ Repetir ${if (n == 1) "el" else "los $n"} de ${etiquetaPlan(p).lowercase()}"
    }

    /** Una escritura contra el endpoint; refresca la pestaña si salió bien. null = no se guardó (ya se avisó). */
    suspend fun accion(exito: String? = null, bloque: suspend () -> ResultadoEscritura): ResultadoEscritura? {
        val r = bloque()
        if (!r.registrada) { Toaster.error(r.rechazo?.error ?: "No se pudo guardar"); return null }
        // Encolada: enviarOEncolar ya avisó "se registrará al volver la señal".
        if (!r.encolada) { exito?.let { Toaster.exito(it) }; recargar() }
        return r
    }

    fun agregar(ids: List<String>) {
        val d = bibliotecaPara ?: destino
        agregando = true
        scope.launch {
            val r = accion { EjerciciosRepo.agregar(pacienteId, ids, d) }
            agregando = false
            if (r == null) return@launch
            bibliotecaPara = null
            elegido = d
            // Entran con la dosis sugerida: cada uno se ajusta a este paciente.
            if (!r.encolada) Toaster.exito(
                (if (ids.size == 1) "Ejercicio agregado" else "${ids.size} ejercicios agregados") + " con la dosis sugerida",
            )
        }
    }

    fun repetir(d: DestinoEjercicios, desde: PlanEjercicios) {
        agregando = true
        scope.launch {
            val r = accion { EjerciciosRepo.copiar(pacienteId, desde.id, d) }
            agregando = false
            if (r == null) return@launch
            bibliotecaPara = null
            elegido = d
            if (!r.encolada) Toaster.exito("Ejercicios repetidos con la misma dosis")
        }
    }

    /** El enlace del plan (el servidor lo crea la primera vez). */
    suspend fun enlace(plan: PlanEjercicios): String? {
        plan.token?.let { return urlPlanEjercicios(it) }
        val r = EjerciciosRepo.compartir(plan.id)
        val tk = tokenDeRespuesta(r.cuerpo)
        if (!r.registrada || tk == null) { Toaster.error(r.rechazo?.error ?: "No se pudo crear el enlace"); return null }
        recargar()
        return urlPlanEjercicios(tk)
    }

    fun enviarWhatsApp(plan: PlanEjercicios) {
        scope.launch {
            val url = enlace(plan) ?: return@launch
            val texto = mensajeCompartirPlan(pacienteNombre, clinicaNombre, url, plan.activos.size)
            val wa = enlaceWhatsApp(pacienteTelefono, texto)
            if (wa == null) {
                acciones.copiarTexto(texto, "Ejercicios de apoyo")
                Toaster.info("El paciente no tiene un celular válido. Copié el mensaje con el enlace.")
            } else acciones.abrirUrl(wa)
        }
    }

    fun copiarEnlace(plan: PlanEjercicios) {
        scope.launch {
            val url = enlace(plan) ?: return@launch
            acciones.copiarTexto(url, "Enlace de ejercicios")
            Toaster.exito("Enlace copiado")
        }
    }

    fun cambiarEstado(plan: PlanEjercicios, estado: String) {
        if (estado == "finalizado") { confirmarFinalizar = plan; return }
        scope.launch {
            accion(if (estado == "pausado") "Ejercicios en pausa" else "Ejercicios reactivados") { EjerciciosRepo.cambiarEstado(plan.id, estado) }
        }
    }

    fun accionesDe(plan: PlanEjercicios) = AccionesPlan(
        agregar = { bibliotecaPara = DestinoEjercicios(plan.momento, plan.sesionId, plan.tratamientoId) },
        ajustar = { ajustar = it },
        quitar = { confirmarQuitar = it },
        editar = { editarPlan = plan },
        whatsapp = { enviarWhatsApp(plan) },
        copiarEnlace = { copiarEnlace(plan) },
        verComoPaciente = { plan.token?.let { acciones.abrirUrl(urlPlanEjercicios(it)) } },
        estado = { cambiarEstado(plan, it) },
        revocar = { scope.launch { accion("Enlace desactivado") { EjerciciosRepo.revocar(plan.id) } } },
    )

    val repetible = repetibleEn(destino)
    // El plan del destino elegido, si es de historial: se muestra completo para poder corregirlo.
    val planDestinoAnterior = planDestino?.takeIf { it.estado != "activo" }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("🏠 Ejercicios de apoyo", color = c.texto, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        if (puedeEditar) {
            SelectorDestino(
                grupos, destino,
                opciones.firstOrNull { it.clave == claveDestino(destino) }?.etiqueta ?: etiquetaDe(destino),
            ) { elegido = it }
            BotonEjercicios("＋ Elegir ejercicios", lleno = true, modifier = Modifier.fillMaxWidth()) { bibliotecaPara = destino }
        }

        if (planDestino == null && puedeEditar) {
            val alTerminar = destino.momento == "alta"
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.chipBg)
                    .border(1.5.dp, c.lav, RoundedCornerShape(Sania.shape.md.dp)).padding(14.dp),
            ) {
                Text(
                    if (alTerminar) "Todavía no le has dejado ejercicios para seguir en casa al terminar."
                    else "${etiquetaDe(destino)} todavía no tiene ejercicios.",
                    color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                )
                Text(
                    if (alTerminar) "Lo que indiques aquí queda para que el paciente lo siga haciendo en casa después del alta."
                    else "Lo que indiques aquí queda vigente hasta la próxima sesión. Cada ejercicio lleva su animación y las indicaciones.",
                    color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp),
                )
                FlowRow(
                    Modifier.padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BotonEjercicios("＋ Elegir de la biblioteca", lleno = true) { bibliotecaPara = destino }
                    repetible?.let { p -> BotonEjercicios(textoRepetir(p), habilitado = !agregando) { repetir(destino, p) } }
                }
            }
        }

        if (vigentes.isEmpty() && planDestinoAnterior == null && (!puedeEditar || planDestino != null)) {
            TarjetaEjercicios {
                Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🏠", fontSize = 30.sp)
                    Text("No tiene ejercicios vigentes", color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp))
                }
            }
        }

        // Si se eligió una sesión anterior, lo suyo va primero: es lo que se vino a ver.
        planDestinoAnterior?.let { p ->
            TarjetaPlan(p, nombreTrat(p.tratamientoId), registros, hoy, puedeEditar, accionesDe(p))
        }
        vigentes.forEach { p ->
            TarjetaPlan(p, nombreTrat(p.tratamientoId), registros, hoy, puedeEditar, accionesDe(p))
        }

        // ── Historial ──
        if (anteriores.isNotEmpty()) {
            TarjetaEjercicios {
                Row(Modifier.fillMaxWidth().clickable { verAnteriores = !verAnteriores }, verticalAlignment = Alignment.CenterVertically) {
                    Text("SESIONES ANTERIORES (${anteriores.size})", color = c.textoSuave, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp, modifier = Modifier.weight(1f))
                    Text(if (verAnteriores) "▴" else "▾", color = c.textoSuave, fontSize = 12.sp)
                }
                AnimatedVisibility(verAnteriores) {
                    Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        anteriores.forEach { p ->
                            val items = p.activos
                            val a = adherenciaDePlan(p, registros, hoy)
                            Column(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                                    .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(etiquetaPlan(p), color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                    if (p.estado == "pausado") {
                                        InsigniaEjercicios("En pausa", c.pend, c.pendBg)
                                        Spacer(Modifier.width(6.dp))
                                    }
                                    if (a.porcentaje != null) ChipAdherencia(a)
                                }
                                Text(
                                    listOfNotNull(
                                        nombreTrat(p.tratamientoId), "${items.size} ejercicio${if (items.size == 1) "" else "s"}",
                                        p.fechaFin?.let { "hasta el ${fechaCorta(it)}" },
                                    ).joinToString(" · "),
                                    color = c.textoSuave, fontSize = 11.sp,
                                )
                                if (items.isNotEmpty()) Text(items.joinToString(" · ") { "${it.ejercicioNombre} (${textoDosis(it)})" },
                                    color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
                                // Reactivar exige que ese tratamiento no tenga otro vigente.
                                if (puedeEditar && vigenteDeTratamiento(planes, p.tratamientoId) == null) {
                                    Text(if (p.estado == "pausado") "▶ Reanudar" else "Reactivar", color = c.navy, fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold, modifier = Modifier.clickable { cambiarEstado(p, "activo") }.padding(vertical = 4.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    bibliotecaPara?.let { d ->
        val planD = planDeDestino(planes, d)
        BibliotecaEjerciciosModal(
            clinicaId = clinicaId,
            para = etiquetaDe(d) + (nombreTrat(d.tratamientoId)?.let { " · $it" } ?: ""),
            vigencia = vigenciaPlan(d.momento),
            repetir = repetibleEn(d)?.let { p -> RepetirEjercicios(textoRepetir(p)) { repetir(d, p) } },
            idsEnPlan = planD?.activos?.map { it.ejercicioId }.orEmpty(),
            guardando = agregando,
            onCerrar = { bibliotecaPara = null },
            onAgregar = { agregar(it) },
        )
    }
    ajustar?.let { item ->
        ModalDosisEjercicio(
            item = item,
            onCerrar = { ajustar = null },
            onGuardar = { dosis -> accion("Ejercicio actualizado") { EjerciciosRepo.editarItem(item.id, dosis) } != null },
        )
    }
    editarPlan?.let { plan ->
        ModalPlanEjercicios(
            plan = plan,
            onCerrar = { editarPlan = null },
            onGuardar = { titulo, motivo, generales, precauciones, visible ->
                accion("Indicaciones actualizadas") {
                    EjerciciosRepo.editarPlan(plan.id, titulo, motivo, generales, precauciones, visible)
                } != null
            },
        )
    }
    confirmarQuitar?.let { item ->
        AlertDialog(
            onDismissRequest = { confirmarQuitar = null },
            title = { Text("¿Quitar “${item.ejercicioNombre}”?", fontWeight = FontWeight.Bold) },
            text = { Text("Lo que el paciente ya registró se conserva.", color = c.textoSuave) },
            confirmButton = {
                TextButton(onClick = {
                    confirmarQuitar = null
                    scope.launch { accion { EjerciciosRepo.quitarItem(item.id) } }
                }) { Text("Quitar", color = c.error, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirmarQuitar = null }) { Text("Cancelar", color = c.textoSuave) } },
            containerColor = c.superficie,
        )
    }
    confirmarFinalizar?.let { plan ->
        AlertDialog(
            onDismissRequest = { confirmarFinalizar = null },
            title = { Text("¿Finalizar estos ejercicios?", fontWeight = FontWeight.Bold) },
            text = { Text("El paciente deja de verlos. Quedan en el historial.", color = c.textoSuave) },
            confirmButton = {
                TextButton(onClick = {
                    confirmarFinalizar = null
                    scope.launch { accion("Ejercicios finalizados") { EjerciciosRepo.cambiarEstado(plan.id, "finalizado") } }
                }) { Text("Finalizar", color = c.navy, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirmarFinalizar = null }) { Text("Cancelar", color = c.textoSuave) } },
            containerColor = c.superficie,
        )
    }
}

// ─── Piezas de la pestaña ────────────────────────────────────────────────────

@Composable
private fun TarjetaEjercicios(contenido: @Composable ColumnScope.() -> Unit) {
    val c = Sania.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.superficie)
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(14.dp),
        content = contenido,
    )
}

/** Botón de la pestaña: lleno (navy o el [color] dado) o de contorno. */
@Composable
private fun BotonEjercicios(
    texto: String, lleno: Boolean = false, habilitado: Boolean = true, color: Color? = null,
    modifier: Modifier = Modifier, onClick: () -> Unit,
) {
    val c = Sania.colors
    val fondo = color ?: c.navy
    Box(
        modifier.clip(RoundedCornerShape(Sania.shape.sm.dp))
            .background(if (!lleno) c.superficie else if (habilitado) fondo else c.borde)
            .border(1.dp, if (!lleno) c.lav else if (habilitado) fondo else c.borde, RoundedCornerShape(Sania.shape.sm.dp))
            .clickable(enabled = habilitado, onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(texto, color = if (!habilitado) c.textoSuave else if (lleno) c.sobreNavy else c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun InsigniaEjercicios(texto: String, fg: Color, bg: Color) {
    Text(texto, color = fg, fontSize = 10.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(bg).padding(horizontal = 8.dp, vertical = 3.dp))
}

/** "3/4 días" en verde / ámbar / rojo: cuántos de los días que le tocaba lo hizo. */
@Composable
private fun ChipAdherencia(a: Adherencia) {
    val c = Sania.colors
    val p = a.porcentaje
    if (p == null) {
        Text("Aún no le tocaba", color = c.textoSuave, fontSize = 10.sp)
        return
    }
    val (fg, bg) = when { p >= 70 -> c.ok to c.okBg; p >= 40 -> c.pend to c.pendBg; else -> c.error to c.errorBg }
    Text("${a.hechos}/${a.programados} días", color = fg, fontSize = 10.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 6.dp, vertical = 2.dp))
}

/** Selector "Indicar para": las sesiones atendidas (la última primero) y "al terminar", por tratamiento. */
@Composable
private fun SelectorDestino(grupos: List<GrupoDestinos>, actual: DestinoEjercicios, textoActual: String, onElegir: (DestinoEjercicios) -> Unit) {
    val c = Sania.colors
    var menu by remember { mutableStateOf(false) }
    Column {
        EtqForm("Indicar para")
        Box {
            CajaSelectorForm("$textoActual  ▾") { menu = true }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                grupos.forEach { g ->
                    if (grupos.size > 1) Text(g.nombre.uppercase(), color = c.textoSuave, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                    g.opciones.forEach { o ->
                        DropdownMenuItem(
                            text = { Text(o.etiqueta, fontSize = 14.sp, fontWeight = if (o.clave == claveDestino(actual)) FontWeight.Bold else FontWeight.Normal) },
                            onClick = { menu = false; onElegir(o.destino) },
                        )
                    }
                }
            }
        }
    }
}

// ─── Un plan: los ejercicios de una sesión (o del fin del tratamiento) ───────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TarjetaPlan(
    plan: PlanEjercicios, tratamiento: String?, registros: List<RegistroEjercicio>, hoy: String,
    puedeEditar: Boolean, acciones: AccionesPlan,
) {
    val c = Sania.colors
    val items = plan.activos
    val hasta = hastaDePlan(plan.fechaFin, hoy)
    val adherencia = remember(plan, registros, hoy) { adherenciaDePlan(plan, registros, hoy) }
    val vigente = plan.estado == "activo"
    TarjetaEjercicios {
        Text(etiquetaPlan(plan), color = c.texto, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        FlowRow(
            Modifier.padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            when {
                vigente -> InsigniaEjercicios("Vigente · ${vigenciaPlan(plan.momento).lowercase()}", c.ok, c.okBg)
                plan.estado == "pausado" -> InsigniaEjercicios("En pausa", c.pend, c.pendBg)
                else -> InsigniaEjercicios("Anterior", c.textoSuave, c.chipBg)
            }
            if (!plan.visiblePaciente) InsigniaEjercicios("Oculto para el paciente", c.pend, c.pendBg)
        }
        val quien = listOfNotNull(tratamiento, plan.creadoPorNombre?.let { "Los indicó $it" }).joinToString(" · ")
        if (quien.isNotEmpty()) Text(quien, color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        plan.motivo?.let { Text("Motivo: $it", color = c.texto, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
        plan.indicacionesGenerales?.let { Text(it, color = c.texto, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)) }
        plan.precauciones?.let {
            Text("⚠️ $it", color = c.pend, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp).fillMaxWidth()
                .clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.pendBg).padding(horizontal = 10.dp, vertical = 6.dp))
        }

        // ── Cumplimiento ──
        Spacer(Modifier.height(10.dp))
        Text("CUMPLIMIENTO ${if (plan.momento == "sesion") "DESDE LA SESIÓN" else "EN CASA"}", color = c.textoSuave, fontSize = 10.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        val pct = adherencia.porcentaje
        if (pct == null) {
            Text("Aún sin días programados", color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        } else {
            Text("$pct%", color = c.texto, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            val avance by animateFloatAsState(pct / 100f, tween(500), label = "cumplimiento")
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(c.borde)) {
                Box(Modifier.fillMaxWidth(avance).height(6.dp).clip(RoundedCornerShape(3.dp)).background(c.ok))
            }
            Text("${adherencia.hechos} de ${adherencia.programados} veces marcadas por el paciente", color = c.textoSuave, fontSize = 11.sp,
                modifier = Modifier.padding(top = 3.dp))
        }

        // ── Ejercicios ──
        Spacer(Modifier.height(12.dp))
        if (items.isEmpty()) {
            Text("Quedó sin ejercicios. Agrega alguno de la biblioteca.", color = c.textoSuave, fontSize = 13.sp)
        } else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEach { i ->
                FilaItem(i, adherenciaDeItem(i, registros, hasta), puedeEditar, onAjustar = { acciones.ajustar(i) }, onQuitar = { acciones.quitar(i) })
            }
        }

        if (puedeEditar) {
            Box(Modifier.padding(top = 12.dp).fillMaxWidth().height(1.dp).background(c.borde))
            FlowRow(
                Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BotonEjercicios("＋ Agregar", lleno = true, onClick = acciones.agregar)
                if (vigente) BotonEjercicios("📲 Enviar por WhatsApp", lleno = true, habilitado = items.isNotEmpty(), color = c.ok, onClick = acciones.whatsapp)
                if (vigente) BotonEjercicios("🔗 Copiar enlace", habilitado = items.isNotEmpty(), onClick = acciones.copiarEnlace)
                if (vigente && plan.token != null) BotonEjercicios("👁 Ver como paciente", onClick = acciones.verComoPaciente)
                BotonEjercicios("✏️ Indicaciones", onClick = acciones.editar)
                if (vigente) BotonEjercicios("⏸ Pausar") { acciones.estado("pausado") }
                if (vigente) BotonEjercicios("✓ Finalizar") { acciones.estado("finalizado") }
                if (plan.estado == "pausado") BotonEjercicios("▶ Reanudar") { acciones.estado("activo") }
            }
            if (vigente && plan.token != null) {
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("El enlace del paciente está activo y siempre muestra lo vigente.", color = c.textoSuave, fontSize = 11.sp,
                        modifier = Modifier.weight(1f))
                    Text("Desactivarlo", color = c.error, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable(onClick = acciones.revocar).padding(4.dp))
                }
            }
        }
    }
}

/** Un ejercicio del plan: miniatura, dosis, días, extras, indicaciones y cuánto lo cumple. */
@Composable
private fun FilaItem(item: ItemPlanEjercicios, adherencia: Adherencia, puedeEditar: Boolean, onAjustar: () -> Unit, onQuitar: () -> Unit) {
    val c = Sania.colors
    var abierto by remember(item.id) { mutableStateOf(false) }
    val e = item.ejercicio
    val extras = listOfNotNull(
        if (item.vecesAlDia > 1) "${item.vecesAlDia} veces al día" else null,
        item.lado?.let { if (it == "ambos") "Ambos lados" else "Lado $it" },
        item.carga?.let { "Carga: $it" },
        item.descansoSeg?.takeIf { it != 0 }?.let { "Descanso $it s" },
        item.dolorMaximo?.let { "Parar si el dolor pasa de $it/10" },
    )
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))) {
        Row(Modifier.padding(10.dp)) {
            MiniaturaEjercicio(e?.posturaInicialUrl, e?.gifUrl, Modifier.size(width = 84.dp, height = 63.dp).clickable { abierto = !abierto })
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(item.ejercicioNombre, color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(6.dp))
                    ChipAdherencia(adherencia)
                }
                Text("${textoDosis(item)} · ${textoDias(item.dias)}", color = c.texto, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 2.dp))
                if (extras.isNotEmpty()) Text(extras.joinToString(" · "), color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                item.indicaciones?.let {
                    Text("“$it”", color = c.texto, fontSize = 12.sp, fontStyle = FontStyle.Italic, modifier = Modifier.padding(top = 3.dp))
                }
                adherencia.ultimoDolor?.let { d ->
                    Text("Último dolor que anotó: $d/10" + (adherencia.ultimaFecha?.let { " (${fechaCorta(it)})" } ?: ""),
                        color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                }
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(if (abierto) "Ocultar ▴" else "Ver cómo se hace ▾", color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { abierto = !abierto }.padding(vertical = 4.dp))
                    if (puedeEditar) Text("✏️ Ajustar", color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable(onClick = onAjustar).padding(vertical = 4.dp))
                    if (puedeEditar) Text("Quitar", color = c.error, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable(onClick = onQuitar).padding(vertical = 4.dp))
                }
            }
        }
        AnimatedVisibility(abierto) {
            Column {
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.borde))
                Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AnimacionEjercicio(e?.gifUrl, e?.posturaInicialUrl, item.ejercicioNombre)
                    if (!e?.pasos.isNullOrEmpty()) PasosEjercicio(e?.pasos.orEmpty())
                    if (!e?.materiales.isNullOrEmpty()) Text("Necesita: " + e?.materiales.orEmpty().joinToString(", ") { nombreMaterial(it) },
                        color = c.textoSuave, fontSize = 11.sp)
                }
            }
        }
    }
}

// ─── Ajustar la dosis de un ejercicio ────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModalDosisEjercicio(item: ItemPlanEjercicios, onCerrar: () -> Unit, onGuardar: suspend (DosisEdicion) -> Boolean) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    var series by remember { mutableStateOf(item.series.toString()) }
    var reps by remember { mutableStateOf(item.repeticiones?.toString() ?: "") }
    var sostener by remember { mutableStateOf(item.sostenerSeg?.toString() ?: "") }
    var descanso by remember { mutableStateOf(item.descansoSeg?.toString() ?: "") }
    var veces by remember { mutableStateOf(item.vecesAlDia) }
    var dias by remember { mutableStateOf(item.dias) }
    var lado by remember { mutableStateOf(item.lado) }
    var carga by remember { mutableStateOf(item.carga.orEmpty()) }
    var dolor by remember { mutableStateOf(item.dolorMaximo) }
    var indicaciones by remember { mutableStateOf(item.indicaciones.orEmpty()) }
    var guardando by remember { mutableStateOf(false) }

    fun alternarDia(n: Int) { dias = if (n in dias) dias - n else (dias + n).sorted() }
    fun numero(v: String) = v.filter { it.isDigit() }.take(3)
    // Lo mismo que avisa la web antes de enviar; los rangos los valida el servidor.
    val falta = when {
        reps.isBlank() && sostener.isBlank() -> "Indica las repeticiones o los segundos que se sostiene"
        dias.isEmpty() -> "Elige al menos un día"
        else -> ""
    }

    DialogoForm(
        titulo = "Ajustar ejercicio",
        subtitulo = listOfNotNull(item.ejercicioNombre, item.ejercicio?.zonaNombre).joinToString(" · "),
        textoAccion = if (guardando) "Guardando…" else "Guardar",
        accionHabilitada = falta.isEmpty() && !guardando,
        onCancelar = { if (!guardando) onCerrar() },
        onAccion = {
            if (falta.isEmpty() && !guardando) {
                guardando = true
                scope.launch {
                    val ok = onGuardar(DosisEdicion(series, reps, sostener, descanso, veces, dias, lado, carga, dolor, indicaciones))
                    guardando = false
                    if (ok) onCerrar()
                }
            }
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CampoDosis("Series", series, Modifier.weight(1f)) { series = numero(it) }
            CampoDosis("Repeticiones", reps, Modifier.weight(1f)) { reps = numero(it) }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CampoDosis("Sostener (s)", sostener, Modifier.weight(1f)) { sostener = numero(it) }
            CampoDosis("Descanso (s)", descanso, Modifier.weight(1f)) { descanso = numero(it) }
        }

        Spacer(Modifier.height(12.dp))
        EtqForm("Días de la semana")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DIAS_SEMANA.forEach { d ->
                val on = d.n in dias
                Box(
                    Modifier.size(38.dp).clip(CircleShape).background(if (on) c.navy else c.superficie)
                        .border(1.5.dp, if (on) c.navy else c.borde, CircleShape).clickable { alternarDia(d.n) },
                    contentAlignment = Alignment.Center,
                ) { Text(d.letra, color = if (on) c.sobreNavy else c.textoSuave, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            }
        }
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            listOf("Todos los días" to listOf(1, 2, 3, 4, 5, 6, 7), "Lunes a viernes" to listOf(1, 2, 3, 4, 5), "Interdiario" to listOf(1, 3, 5))
                .forEach { (texto, valor) ->
                    Text(texto, color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { dias = valor }.padding(vertical = 4.dp))
                }
        }

        Spacer(Modifier.height(12.dp))
        EtqForm("Veces al día")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..6).forEach { n -> ChipEval(if (n == 1) "1 vez" else "$n veces", activo = veces == n) { veces = n } }
        }

        Spacer(Modifier.height(12.dp))
        EtqForm("Lado")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ChipEval("No aplica", activo = lado == null) { lado = null }
            LADOS.forEach { (id, nombre) -> ChipEval(nombre, activo = lado == id) { lado = id } }
        }

        Spacer(Modifier.height(12.dp))
        EtqForm("Carga o resistencia")
        OutlinedTextField(
            colors = coloresCampoForm(), value = carga, onValueChange = { carga = it.take(120) },
            placeholder = { Text("Banda roja, 1 kg…", color = c.textoSuave) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))
        EtqForm("Parar si el dolor pasa de")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ChipEval("Sin tope", activo = dolor == null) { dolor = null }
            (0..10).forEach { n -> ChipEval("$n", activo = dolor == n) { dolor = n } }
        }

        Spacer(Modifier.height(12.dp))
        EtqForm("Indicaciones para este paciente")
        OutlinedTextField(
            colors = coloresCampoForm(), value = indicaciones, onValueChange = { indicaciones = it.take(1500) },
            placeholder = { Text("Ej.: hazlo despacio, sin llegar al dolor; apóyate en la pared si pierdes el equilibrio.", color = c.textoSuave) },
            minLines = 3, modifier = Modifier.fillMaxWidth(),
        )
        Text("El paciente las ve antes que los pasos del ejercicio.", color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
        if (falta.isNotEmpty()) Text(falta, color = c.error, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun CampoDosis(etiqueta: String, valor: String, modifier: Modifier, onChange: (String) -> Unit) {
    Column(modifier) {
        EtqForm(etiqueta)
        CampoTextoEval(valor, onChange, "—", Modifier.fillMaxWidth(), teclado = KeyboardType.Number)
    }
}

// ─── Datos del plan: motivo, indicaciones generales, precauciones ────────────

@Composable
private fun ModalPlanEjercicios(
    plan: PlanEjercicios,
    onCerrar: () -> Unit,
    onGuardar: suspend (titulo: String, motivo: String, generales: String, precauciones: String, visible: Boolean) -> Boolean,
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    var titulo by remember { mutableStateOf(plan.titulo) }
    var motivo by remember { mutableStateOf(plan.motivo.orEmpty()) }
    var generales by remember { mutableStateOf(plan.indicacionesGenerales.orEmpty()) }
    var precauciones by remember { mutableStateOf(plan.precauciones.orEmpty()) }
    var visible by remember { mutableStateOf(plan.visiblePaciente) }
    var guardando by remember { mutableStateOf(false) }

    DialogoForm(
        titulo = "Indicaciones del plan",
        subtitulo = etiquetaPlan(plan),
        textoAccion = if (guardando) "Guardando…" else "Guardar",
        accionHabilitada = titulo.isNotBlank() && !guardando,
        onCancelar = { if (!guardando) onCerrar() },
        onAccion = {
            if (titulo.isNotBlank() && !guardando) {
                guardando = true
                scope.launch {
                    val ok = onGuardar(titulo, motivo, generales, precauciones, visible)
                    guardando = false
                    if (ok) onCerrar()
                }
            }
        },
    ) {
        EtqForm("Título")
        OutlinedTextField(colors = coloresCampoForm(), value = titulo, onValueChange = { titulo = it.take(120) },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        EtqForm("Malestar o motivo")
        OutlinedTextField(colors = coloresCampoForm(), value = motivo, onValueChange = { motivo = it.take(300) },
            placeholder = { Text("Ej.: lumbalgia mecánica, rigidez de hombro derecho", color = c.textoSuave) },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        EtqForm("Indicaciones generales")
        OutlinedTextField(colors = coloresCampoForm(), value = generales, onValueChange = { generales = it.take(2000) },
            placeholder = { Text("Ej.: hazlos por la mañana, después de una ducha tibia. Respira con calma.", color = c.textoSuave) },
            minLines = 3, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        EtqForm("Precauciones")
        OutlinedTextField(colors = coloresCampoForm(), value = precauciones, onValueChange = { precauciones = it.take(1000) },
            placeholder = { Text("Ej.: si aparece hormigueo en la pierna, detente y avísanos.", color = c.textoSuave) },
            minLines = 2, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.superficie)
                .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                .clickable { visible = !visible }.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(if (visible) "☑" else "☐", fontSize = 18.sp, color = if (visible) c.navy else c.textoSuave)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("El paciente puede verlo", color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("En su portal, en la app y en el enlace que le mandes. Apágalo mientras lo armas.", color = c.textoSuave, fontSize = 11.sp)
            }
        }
    }
}
