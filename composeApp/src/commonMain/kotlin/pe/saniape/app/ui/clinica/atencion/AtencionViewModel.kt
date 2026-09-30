package pe.saniape.app.ui.clinica.atencion

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.math.abs
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pe.saniape.app.data.offline.ResultadoEscritura
import pe.saniape.app.data.staff.AtencionRepo
import pe.saniape.app.data.staff.BorradorAtencion
import pe.saniape.app.data.staff.DatosConsultaApp
import pe.saniape.app.data.staff.DiagnosticoCie
import pe.saniape.app.data.staff.ExamenSolicitado
import pe.saniape.app.data.staff.PasoAtencion
import pe.saniape.app.ui.Toaster

/**
 * ViewModel de la consulta guiada (gemelo de ConsultaGuiada.tsx en la web).
 *
 * Solo orquesta estado: las reglas (qué pasos hay, qué falta, avisos del cierre,
 * quién puede atender) las resuelve el servidor en `flags`. Aquí vive el
 * BORRADOR que el profesional va escribiendo y el guardado automático al
 * cambiar de paso.
 *
 * Ojo: `guardar` REEMPLAZA la atención completa, por eso el borrador lleva
 * SIEMPRE todas las claves (textos y vitales) y se manda entero.
 *
 * Carga sola al crearse; [cargar] sirve también de "Reintentar".
 */
class AtencionViewModel(private val citaId: String) : ViewModel() {

    var datos by mutableStateOf<DatosConsultaApp?>(null); private set
    /** Carga fallida (la pantalla ofrece reintentar). */
    var error by mutableStateOf<String?>(null); private set
    var cargando by mutableStateOf(true); private set
    var borrador by mutableStateOf(BorradorAtencion()); private set
    /** Hay cambios sin guardar. */
    var sucio by mutableStateOf(false); private set
    /** Índice en [pasos]. */
    var paso by mutableStateOf(0); private set
    var guardando by mutableStateOf(false); private set
    var terminando by mutableStateOf(false); private set
    /** true → la pantalla vuelve a la agenda. */
    var terminada by mutableStateOf(false); private set

    val pasos: List<PasoAtencion> get() = datos?.flags?.pasos ?: emptyList()
    val pasoActual: PasoAtencion? get() = pasos.getOrNull(paso)
    /** Sin datos todavía = solo lectura (no se edita lo que no cargó). */
    val soloLectura: Boolean get() = datos?.flags?.soloLectura ?: true
    val esUltimoPaso: Boolean get() = pasos.isNotEmpty() && paso >= pasos.lastIndex

    /** Un guardado a la vez (el automático de [irA] puede cruzarse con uno a mano). */
    private val candado = Mutex()

    init { cargar() }

    // ── Carga ────────────────────────────────────────────────────────────────

    /** GET consulta y arma el borrador desde cero. También es el "Reintentar". */
    fun cargar() {
        viewModelScope.launch {
            cargando = true
            error = null
            when (val r = AtencionRepo.cargar(citaId)) {
                is AtencionRepo.Carga.Ok -> {
                    val d = r.datos
                    datos = d
                    borrador = borradorDesde(d)
                    // El motivo sugerido (de la cita/reserva) queda como cambio por guardar.
                    sucio = motivoSugeridoAplica(d) && !d.flags.soloLectura
                    paso = 0
                }
                is AtencionRepo.Carga.Error -> {
                    error = if (r.sinRed) "Sin conexión: la atención necesita internet." else r.mensaje
                }
            }
            cargando = false
        }
    }

    /** Refresca `datos` (flags, recetas, indicados…) SIN tocar el borrador: lo escrito se respeta. */
    private suspend fun recargarDatos() {
        val r = AtencionRepo.cargar(citaId)
        if (r is AtencionRepo.Carga.Ok) {
            datos = r.datos
            if (paso > pasos.lastIndex) paso = pasos.lastIndex.coerceAtLeast(0)
        }
    }

    // ── Edición del borrador ─────────────────────────────────────────────────

    private inline fun editar(cambio: (BorradorAtencion) -> BorradorAtencion) {
        if (soloLectura) return
        borrador = cambio(borrador)
        sucio = true
    }

    fun texto(k: String, v: String) = editar { it.copy(textos = it.textos + (k to v)) }
    fun vital(k: String, v: String) = editar { it.copy(vitales = it.vitales + (k to v)) }
    fun diagnosticos(l: List<DiagnosticoCie>) = editar { it.copy(diagnosticos = l) }
    fun examenes(l: List<ExamenSolicitado>) = editar { it.copy(examenes = l) }
    fun terapeuta(id: String?) = editar { it.copy(terapeutaId = id) }

    // ── Guardado ─────────────────────────────────────────────────────────────

    /**
     * Botón "Guardar": manda el borrador completo. Ok → `sucio=false`, toast de
     * éxito y recarga `datos` (faltantes y avisos cambian). Error → toast con el
     * texto del servidor. [silencioso] = sin ningún toast (guardado automático).
     */
    suspend fun guardar(silencioso: Boolean = false): Boolean =
        guardarInterno(avisarExito = !silencioso, avisarError = !silencioso)

    /**
     * Como la web: sin cambios y con la atención ya creada no hay nada que mandar.
     * Sin atención todavía se guarda igual (así las acciones del plan tienen dónde colgarse).
     */
    private suspend fun guardarInterno(avisarExito: Boolean, avisarError: Boolean): Boolean {
        if (soloLectura) return true
        return candado.withLock {
            if (!sucio && datos?.atencion != null) return@withLock true
            guardando = true
            val enviado = borrador
            val r = AtencionRepo.guardar(citaId, enviado)
            if (r.registrada) {
                // Si el usuario siguió escribiendo mientras se guardaba, sigue sucio.
                if (borrador == enviado) sucio = false
                if (avisarExito) Toaster.exito("Guardado en la historia clínica")
                recargarDatos()
            } else if (avisarError) {
                Toaster.error(r.rechazo?.error ?: "No se pudo guardar la atención.")
            }
            guardando = false
            r.registrada
        }
    }

    // ── Navegación entre pasos ───────────────────────────────────────────────

    /**
     * Cambia de paso; si hay cambios los guarda en segundo plano. El paso cambia
     * aunque el guardado falle (lo escrito sigue en el borrador y sigue sucio).
     */
    fun irA(i: Int) {
        if (pasos.isEmpty()) return
        val destino = i.coerceIn(0, pasos.lastIndex)
        if (sucio && !soloLectura) {
            viewModelScope.launch {
                if (!guardar(silencioso = true)) {
                    Toaster.error("No se pudo guardar. Tus cambios siguen aquí; toca Guardar para reintentar.")
                }
            }
        }
        paso = destino
    }

    fun siguiente() { if (paso < pasos.lastIndex) irA(paso + 1) }
    fun atras() { if (paso > 0) irA(paso - 1) }

    // ── Acciones del plan (control, procedimiento, examen, firma, receta…) ───

    /**
     * Guarda antes (el `guardarAntes` de la web), corre [bloque] y recarga los
     * datos (el borrador se respeta). Error → toast con el texto del servidor.
     */
    suspend fun accionPlan(bloque: suspend () -> ResultadoEscritura): Boolean {
        if (!guardarInterno(avisarExito = false, avisarError = true)) return false
        val r = bloque()
        if (r.registrada) {
            recargarDatos()
            return true
        }
        Toaster.error(r.rechazo?.error ?: "No se pudo completar.")
        return false
    }

    // ── Cierre ───────────────────────────────────────────────────────────────

    /** "✓ Terminar atención": guarda → termina → [terminada]. */
    fun terminar(notaProcedimiento: String?) {
        if (terminando) return
        viewModelScope.launch {
            terminando = true
            try {
                if (!guardarInterno(avisarExito = false, avisarError = true)) return@launch
                val esProc = datos?.flags?.esProcedimiento == true
                val textos = borrador.textos.filterKeys { it in camposFrases(esProc) }
                val nota = notaProcedimiento?.trim()?.takeIf { it.isNotEmpty() }
                val r = AtencionRepo.terminar(citaId, nota, textos)
                if (r.registrada) {
                    terminada = true
                    Toaster.exito("Atención terminada")
                } else {
                    Toaster.error(r.rechazo?.error ?: "No se pudo terminar la atención.")
                }
            } finally {
                terminando = false
            }
        }
    }
}

// ── Puras (testeables) ───────────────────────────────────────────────────────

/**
 * Lo que `terminar` usa para aprender frases frecuentes. Como la web: en un
 * procedimiento no se aprenden motivo ni examen (no se escriben ahí).
 */
internal fun camposFrases(esProcedimiento: Boolean): Set<String> =
    if (esProcedimiento) setOf("tratamiento") else setOf("motivo_consulta", "examen_fisico", "tratamiento")

/** Número de la base → texto editable, sin ".0" al final (70.0 → "70", 36.5 → "36.5"). */
internal fun vitalATexto(v: Double?): String {
    if (v == null) return ""
    return if (v % 1.0 == 0.0 && abs(v) < 1e15) v.toLong().toString() else v.toString()
}

/** true si el motivo está vacío y la cita trae uno sugerido (se precarga y queda por guardar). */
internal fun motivoSugeridoAplica(d: DatosConsultaApp): Boolean =
    d.atencion?.motivo_consulta.isNullOrBlank() && d.motivoSugerido.isNotBlank()

/**
 * Borrador inicial desde `GET consulta`. Lleva TODAS las claves (9 textos y 9
 * vitales) porque `guardar` reemplaza la atención completa.
 */
internal fun borradorDesde(d: DatosConsultaApp): BorradorAtencion {
    val a = d.atencion
    val textos = mapOf(
        "motivo_consulta" to (a?.motivo_consulta ?: ""),
        "tiempo_enfermedad" to (a?.tiempo_enfermedad ?: ""),
        "relato" to (a?.relato ?: ""),
        "funciones_biologicas" to (a?.funciones_biologicas ?: ""),
        "examen_fisico" to (a?.examen_fisico ?: ""),
        "plan_trabajo" to (a?.plan_trabajo ?: ""),
        "tratamiento" to (a?.tratamiento ?: ""),
        "observaciones" to (a?.observaciones ?: ""),
        "nota_procedimiento" to (a?.nota_procedimiento ?: ""),
    ).let { if (motivoSugeridoAplica(d)) it + ("motivo_consulta" to d.motivoSugerido) else it }
    val vitales = mapOf(
        "presion_sistolica" to vitalATexto(a?.presion_sistolica),
        "presion_diastolica" to vitalATexto(a?.presion_diastolica),
        "frecuencia_cardiaca" to vitalATexto(a?.frecuencia_cardiaca),
        "frecuencia_respiratoria" to vitalATexto(a?.frecuencia_respiratoria),
        "temperatura" to vitalATexto(a?.temperatura),
        "saturacion_o2" to vitalATexto(a?.saturacion_o2),
        "peso" to vitalATexto(a?.peso),
        "talla" to vitalATexto(a?.talla),
        "perimetro_abdominal" to vitalATexto(a?.perimetro_abdominal),
    )
    return BorradorAtencion(
        terapeutaId = a?.terapeuta_id ?: d.cita.terapeuta_id,
        tratamientoId = d.cita.tratamiento_id,
        textos = textos,
        vitales = vitales,
        diagnosticos = a?.diagnosticos?.takeIf { it.isNotEmpty() } ?: d.diagnosticosSugeridos,
        examenes = a?.examenes ?: emptyList(),
    )
}
