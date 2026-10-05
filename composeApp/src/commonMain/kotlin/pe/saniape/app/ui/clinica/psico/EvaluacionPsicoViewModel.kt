package pe.saniape.app.ui.clinica.psico

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import pe.saniape.app.data.staff.AnalisisPsico
import pe.saniape.app.data.staff.ContenidoInformePsico
import pe.saniape.app.data.staff.DiagnosticoCie
import pe.saniape.app.data.staff.DocumentoInformePsico
import pe.saniape.app.data.staff.EntrevistaPsico
import pe.saniape.app.data.staff.EspacioEvalPsico
import pe.saniape.app.data.staff.EstadosPsico
import pe.saniape.app.data.staff.EvaluacionPsico
import pe.saniape.app.data.staff.EvaluacionPsicoRepo
import pe.saniape.app.data.staff.FotoPsico
import pe.saniape.app.data.staff.FuentePsico
import pe.saniape.app.data.staff.InformePsico
import pe.saniape.app.data.staff.ObservacionPsico
import pe.saniape.app.data.staff.PlanPsico
import pe.saniape.app.data.staff.PrefillPlanPsico
import pe.saniape.app.data.staff.SugerenciaSesionesPsico
import pe.saniape.app.data.staff.TestAplicadoPsico
import pe.saniape.app.data.staff.TestCatalogoPsico
import pe.saniape.app.data.staff.fotoDeRespuesta
import pe.saniape.app.data.staff.informeDeRespuesta
import pe.saniape.app.data.staff.jsonAnalisis
import pe.saniape.app.data.staff.jsonDatosTest
import pe.saniape.app.data.staff.jsonDiagnosticos
import pe.saniape.app.data.staff.jsonEntrevista
import pe.saniape.app.data.staff.jsonFuentes
import pe.saniape.app.data.staff.jsonObservacion
import pe.saniape.app.data.staff.jsonPlan
import pe.saniape.app.data.staff.mensajePsico
import pe.saniape.app.data.staff.parsearGuardadoPsico
import pe.saniape.app.data.staff.sinFoto
import pe.saniape.app.data.staff.testDeRespuesta
import pe.saniape.app.data.staff.totalSesionesDeRespuesta
import pe.saniape.app.ui.ArchivoSeleccionado
import pe.saniape.app.ui.Gestion
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.comprimirImagen
import pe.saniape.app.ui.conIndicador

/** Lo que guarda lo pendiente al SALIR: la pantalla ya se cerró, el envío sigue. */
private val alcanceSalida = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/** Demora del autoguardado (la web usa 1,2 s; los tests 1 s). */
private const val DEMORA_COMPONENTE_MS = 1200L
private const val DEMORA_TEST_MS = 1000L
private const val DEMORA_INFORME_MS = 1200L

/**
 * Estado de la pantalla "🧠 Evaluación psicológica" (gemela de
 * EspacioEvaluacionPsico.tsx). Solo orquesta: los chips, la edad, el informe y
 * quién puede ver los resuelve el servidor.
 *
 * Autoguardado como la web: cada componente se manda ENTERO 1,2 s después de
 * dejar de escribir (un componente por llamada) y, al cambiar de pestaña o salir,
 * lo pendiente se manda en el acto. La respuesta solo refresca los chips y la
 * sugerencia de sesiones: lo escrito mientras viajaba NO se pisa.
 */
class EvaluacionPsicoViewModel(private val tratamientoId: String) : ViewModel() {

    var cargando by mutableStateOf(true); private set
    /** Carga fallida (con [sinAcceso] para el mensaje de confidencialidad). */
    var error by mutableStateOf<String?>(null); private set
    var sinAcceso by mutableStateOf(false); private set
    var espacio by mutableStateOf<EspacioEvalPsico?>(null); private set
    var ev by mutableStateOf<EvaluacionPsico?>(null); private set
    var estados by mutableStateOf(EstadosPsico()); private set
    var sugerencia by mutableStateOf<SugerenciaSesionesPsico?>(null); private set
    var tests by mutableStateOf<List<TestAplicadoPsico>>(emptyList()); private set
    var fotos by mutableStateOf<List<FotoPsico>>(emptyList()); private set
    var informe by mutableStateOf<InformePsico?>(null); private set
    var informePdf by mutableStateOf<DocumentoInformePsico?>(null); private set
    var totalCitas by mutableStateOf(0); private set
    /** Envíos en vuelo del autoguardado (el "Guardando…" discreto de la cabecera). */
    var guardando by mutableStateOf(0); private set
    /** Acción explícita corriendo (subir foto, emitir, agregar cita…): evita el doble toque. */
    var accionando by mutableStateOf<String?>(null); private set
    var pestania by mutableStateOf("entrevista"); private set

    val soloLectura: Boolean get() = espacio?.soloLectura == true || ev?.estado == "cerrada"

    // ── Carga ────────────────────────────────────────────────────────────────

    init { cargar() }

    /** GET (y POST abrir la primera vez). También es el "Reintentar". */
    fun cargar() {
        viewModelScope.launch {
            cargando = true
            error = null
            sinAcceso = false
            var r = EvaluacionPsicoRepo.cargar(tratamientoId = tratamientoId)
            // Primera vez: se abre sola (idempotente; dos toques no duplican).
            if (r is EvaluacionPsicoRepo.Carga.Ok && r.espacio.esEvaluacionPsico && r.espacio.evaluacion == null && !r.espacio.soloLectura) {
                r = EvaluacionPsicoRepo.abrir(tratamientoId)
            }
            when (r) {
                is EvaluacionPsicoRepo.Carga.Ok -> {
                    val e = r.espacio
                    when {
                        !e.esEvaluacionPsico -> error = "Este tratamiento no es una evaluación psicológica."
                        e.evaluacion == null -> error = "No se pudo abrir la evaluación."
                        else -> aplicar(e)
                    }
                }
                is EvaluacionPsicoRepo.Carga.Error -> {
                    sinAcceso = r.codigo == "SIN_ACCESO_EVALUACION"
                    error = r.mensaje
                }
            }
            cargando = false
        }
    }

    private fun aplicar(e: EspacioEvalPsico) {
        espacio = e
        ev = e.evaluacion
        estados = e.estados
        sugerencia = e.sugerenciaSesiones
        tests = e.tests
        fotos = e.fotos
        informe = e.informe
        informePdf = e.informePdf
        totalCitas = e.tratamiento?.totalSesiones ?: 0
    }

    /** Recarga todo SIN tocar lo que se está escribiendo (tras emitir, agregar test…). */
    private suspend fun refrescar() {
        val r = EvaluacionPsicoRepo.cargar(tratamientoId = tratamientoId)
        if (r is EvaluacionPsicoRepo.Carga.Ok && r.espacio.evaluacion != null) {
            val hayPendientes = pendientes.isNotEmpty() || testsPendientes.isNotEmpty()
            espacio = r.espacio
            estados = r.espacio.estados
            sugerencia = r.espacio.sugerenciaSesiones
            fotos = r.espacio.fotos
            informe = r.espacio.informe
            informePdf = r.espacio.informePdf
            totalCitas = r.espacio.tratamiento?.totalSesiones ?: totalCitas
            if (!hayPendientes) {
                ev = r.espacio.evaluacion
                tests = r.espacio.tests
            }
        }
    }

    fun irA(clave: String) {
        if (clave == pestania) return
        // Lo pendiente del componente que se deja se manda ya (como la web al salir).
        guardarAhora()
        pestania = clave
    }

    // ── Componentes (autoguardado) ───────────────────────────────────────────

    private val pendientes = linkedMapOf<String, JsonElement>()
    private var temporizador: Job? = null
    private val candado = Mutex()

    private fun programar(campo: String, datos: JsonElement) {
        if (soloLectura) return
        pendientes[campo] = datos
        temporizador?.cancel()
        temporizador = viewModelScope.launch {
            delay(DEMORA_COMPONENTE_MS)
            enviarPendientes()
        }
    }

    fun entrevista(v: EntrevistaPsico) { val e = ev ?: return; ev = e.copy(entrevista = v); programar("entrevista", jsonEntrevista(v)) }
    fun fuentes(v: List<FuentePsico>) { val e = ev ?: return; ev = e.copy(fuentes = v); programar("fuentes", jsonFuentes(v)) }
    fun observacion(v: ObservacionPsico) { val e = ev ?: return; ev = e.copy(observacion = v); programar("observacion", jsonObservacion(v)) }
    fun analisis(v: AnalisisPsico) { val e = ev ?: return; ev = e.copy(analisis = v); programar("analisis", jsonAnalisis(v)) }
    fun diagnosticos(v: List<DiagnosticoCie>) { val e = ev ?: return; ev = e.copy(diagnosticos = v); programar("diagnosticos", jsonDiagnosticos(v)) }
    fun plan(v: PlanPsico) { val e = ev ?: return; ev = e.copy(plan = v); programar("plan", jsonPlan(v)) }

    /** Manda ya lo pendiente (sin esperar la demora). */
    fun guardarAhora() {
        temporizador?.cancel()
        viewModelScope.launch { enviarPendientes() }
    }

    /** Envía cada componente pendiente (uno por llamada, en orden). true = todo entró. */
    suspend fun enviarPendientes(): Boolean = candado.withLock {
        val evId = ev?.id ?: return@withLock true
        var ok = true
        while (pendientes.isNotEmpty()) {
            val (campo, datos) = pendientes.entries.first()
            pendientes.remove(campo)
            guardando++
            val r = try { EvaluacionPsicoRepo.guardar(evId, campo, datos) } finally { guardando-- }
            if (r.registrada) {
                val g = parsearGuardadoPsico(r.cuerpo)
                g.estados?.let { estados = it }
                sugerencia = g.sugerencia
            } else {
                ok = false
                // Sin señal: queda pendiente para el próximo intento (no se pierde lo escrito).
                if (r.codigo == "SIN_RED" && !pendientes.containsKey(campo)) pendientes[campo] = datos
                Toaster.error(r.mensajePsico())
                if (r.codigo == "EVALUACION_CERRADA") { pendientes.clear(); refrescar() }
                break
            }
        }
        ok
    }

    /** Al salir de la pantalla: lo pendiente se manda igual, aunque la pantalla ya no esté. */
    fun guardarAlSalir() {
        temporizador?.cancel()
        flushTests()
        flushInforme()
        if (pendientes.isEmpty()) return
        alcanceSalida.launch {
            if (!enviarPendientes()) Toaster.error("No se guardó el último cambio de la evaluación")
        }
    }

    // ── Cita de evaluación ───────────────────────────────────────────────────

    fun agregarCita() {
        if (accionando != null) return
        accionando = "cita"
        viewModelScope.launch {
            val r = conIndicador { EvaluacionPsicoRepo.agregarCita(tratamientoId) }
            accionando = null
            if (r.registrada) {
                // Cómo se cuentan las citas lo decide el servidor: se toma lo que
                // responde y, por si acaso, se relee el espacio.
                totalSesionesDeRespuesta(r.cuerpo)?.let { totalCitas = it }
                Toaster.exito("Se agregó una cita a la evaluación (mismo precio)")
                refrescar()
            } else Toaster.error(r.mensajePsico())
        }
    }

    // ── Tests aplicados ──────────────────────────────────────────────────────

    private val testsPendientes = linkedMapOf<String, TestAplicadoPsico>()
    private val temporizadoresTest = mutableMapOf<String, Job>()

    fun agregarTest(t: TestCatalogoPsico, alListo: (String?) -> Unit) {
        val evId = ev?.id ?: return
        if (accionando != null) return
        accionando = "test"
        viewModelScope.launch {
            val r = conIndicador { EvaluacionPsicoRepo.crearTest(evId, t.id) }
            accionando = null
            val nuevo = testDeRespuesta(r.cuerpo)
            if (r.registrada && nuevo != null) {
                tests = tests + nuevo
                alListo(nuevo.id)
                refrescarEstados()
            } else {
                Toaster.error(if (r.registrada) "Se agregó el test, pero no se pudo leer. Recarga." else r.mensajePsico())
                alListo(null)
            }
        }
    }

    /** Edición de un test: se ve al instante y se manda 1 s después del último cambio. */
    fun cambiarTest(t: TestAplicadoPsico) {
        if (soloLectura) return
        tests = tests.map { if (it.id == t.id) t else it }
        testsPendientes[t.id] = t
        temporizadoresTest[t.id]?.cancel()
        temporizadoresTest[t.id] = viewModelScope.launch {
            delay(DEMORA_TEST_MS)
            enviarTest(t.id)
        }
    }

    private suspend fun enviarTest(id: String) {
        val t = testsPendientes.remove(id) ?: return
        guardando++
        val r = try { EvaluacionPsicoRepo.editarTest(id, jsonDatosTest(t)) } finally { guardando-- }
        if (r.registrada) {
            // Si siguió escribiendo mientras viajaba, no se pisa lo nuevo con la respuesta.
            if (!testsPendientes.containsKey(id)) testDeRespuesta(r.cuerpo)?.let { s -> tests = tests.map { if (it.id == id) s else it } }
            refrescarEstados()
        } else {
            if (r.codigo == "SIN_RED" && !testsPendientes.containsKey(id)) testsPendientes[id] = t
            Toaster.error(r.mensajePsico())
        }
    }

    private fun flushTests() {
        val ids = testsPendientes.keys.toList()
        temporizadoresTest.values.forEach { it.cancel() }
        temporizadoresTest.clear()
        if (ids.isEmpty()) return
        alcanceSalida.launch { ids.forEach { enviarTest(it) } }
    }

    fun borrarTest(t: TestAplicadoPsico) {
        if (accionando != null) return
        accionando = "borrar:${t.id}"
        viewModelScope.launch {
            temporizadoresTest.remove(t.id)?.cancel()
            testsPendientes.remove(t.id)
            val r = conIndicador(Gestion.ELIMINANDO) { EvaluacionPsicoRepo.borrarTest(t.id) }
            accionando = null
            if (r.registrada) {
                tests = tests.filter { it.id != t.id }
                fotos = fotos.filter { it.testAplicadoId != t.id }
                refrescarEstados()
            } else Toaster.error(r.mensajePsico())
        }
    }

    /** Los chips sin pisar lo escrito (el GET los trae calculados). */
    private suspend fun refrescarEstados() {
        val r = EvaluacionPsicoRepo.cargar(tratamientoId = tratamientoId)
        if (r is EvaluacionPsicoRepo.Carga.Ok) estados = r.espacio.estados
    }

    // ── Fotos (material protegido) ───────────────────────────────────────────

    /**
     * Sube una foto (comprimida al agregarla, 1600 px / JPEG 70 como FotosSesion)
     * o un PDF. [uso]: test (con [testAplicadoId]), genograma o fuente.
     * [alSubir] recibe la foto registrada (o null si falló).
     */
    fun subirFoto(archivo: ArchivoSeleccionado, uso: String, testAplicadoId: String? = null, alSubir: (FotoPsico?) -> Unit = {}) {
        val evId = ev?.id ?: return
        if (soloLectura) return
        if (accionando != null) { Toaster.error("Espera a que termine lo anterior"); return }
        accionando = "foto:$uso:${testAplicadoId.orEmpty()}"
        viewModelScope.launch {
            val listo = if (esImagen(archivo)) {
                withContext(Dispatchers.Default) { runCatching { comprimirImagen(archivo) }.getOrDefault(archivo) }
            } else archivo
            if (listo.bytes.size > 15 * 1024 * 1024) {
                accionando = null
                Toaster.error("El archivo supera 15 MB")
                alSubir(null)
                return@launch
            }
            val r = conIndicador {
                EvaluacionPsicoRepo.subirFoto(evId, listo.bytes, listo.nombre, listo.mime, uso, testAplicadoId)
            }
            accionando = null
            val foto = fotoDeRespuesta(r.cuerpo)
            if (r.registrada && foto != null) {
                fotos = fotos + foto
                if (uso == "genograma") {
                    // El servidor ya apuntó la entrevista al genograma nuevo; el anterior se borra.
                    val previo = ev?.entrevista?.genogramaDocumentoId
                    ev = ev?.let { it.copy(entrevista = it.entrevista.copy(genogramaDocumentoId = foto.id)) }
                    if (previo != null && previo != foto.id) {
                        fotos = fotos.filter { it.id != previo }
                        launch { EvaluacionPsicoRepo.borrarFoto(previo) }
                    }
                }
                Toaster.exito("Foto guardada 🔒")
                alSubir(foto)
            } else {
                Toaster.error(if (r.registrada) "Se subió, pero no se pudo leer la respuesta. Recarga." else r.mensajePsico())
                alSubir(null)
            }
        }
    }

    fun borrarFoto(f: FotoPsico) {
        if (accionando != null) return
        accionando = "borrar:${f.id}"
        viewModelScope.launch {
            val r = conIndicador(Gestion.ELIMINANDO) { EvaluacionPsicoRepo.borrarFoto(f.id) }
            accionando = null
            if (r.registrada) {
                fotos = fotos.filter { it.id != f.id }
                // El servidor ya quitó las referencias (genograma y adjuntos de las
                // fuentes); aquí se refleja igual, y si había un guardado pendiente
                // de esos componentes, se manda ya sin la foto.
                val limpio = sinFoto(ev ?: return@launch, f.id)
                ev = limpio
                if (pendientes.containsKey("fuentes")) pendientes["fuentes"] = jsonFuentes(limpio.fuentes)
                if (pendientes.containsKey("entrevista")) pendientes["entrevista"] = jsonEntrevista(limpio.entrevista)
            } else Toaster.error(r.mensajePsico())
        }
    }

    // ── Informe ──────────────────────────────────────────────────────────────

    private var informePendiente: ContenidoInformePsico? = null
    private var temporizadorInforme: Job? = null

    /** Arma (o rearma, pisando lo editado: la pantalla confirma antes) el borrador. */
    fun armarInforme() {
        val evId = ev?.id ?: return
        if (accionando != null) return
        accionando = "armar"
        viewModelScope.launch {
            // Lo último escrito en los componentes tiene que entrar al armado.
            enviarPendientes()
            temporizadorInforme?.cancel(); informePendiente = null
            val r = conIndicador { EvaluacionPsicoRepo.armarInforme(evId) }
            accionando = null
            val i = informeDeRespuesta(r.cuerpo)
            if (r.registrada && i != null) informe = i else Toaster.error(r.mensajePsico())
        }
    }

    fun editarInforme(c: ContenidoInformePsico) {
        val i = informe ?: return
        if (i.emitido || soloLectura) return
        informe = i.copy(contenido = c)
        informePendiente = c
        temporizadorInforme?.cancel()
        temporizadorInforme = viewModelScope.launch {
            delay(DEMORA_INFORME_MS)
            enviarInforme()
        }
    }

    private suspend fun enviarInforme(): Boolean {
        val i = informe ?: return true
        val c = informePendiente ?: return true
        informePendiente = null
        guardando++
        val r = try { EvaluacionPsicoRepo.guardarInforme(i.id, c) } finally { guardando-- }
        if (!r.registrada) {
            if (r.codigo == "SIN_RED" && informePendiente == null) informePendiente = c
            Toaster.error(r.mensajePsico())
        }
        return r.registrada
    }

    private fun flushInforme() {
        temporizadorInforme?.cancel()
        if (informePendiente == null) return
        alcanceSalida.launch { enviarInforme() }
    }

    /** "Emitir": congela el informe, genera el PDF y cierra la evaluación. */
    fun emitirInforme(alTerminar: (Boolean) -> Unit) {
        val i = informe ?: return
        if (accionando != null) return
        accionando = "emitir"
        viewModelScope.launch {
            temporizadorInforme?.cancel()
            if (!enviarInforme()) { accionando = null; alTerminar(false); return@launch }
            val r = conIndicador { EvaluacionPsicoRepo.emitirInforme(i.id) }
            accionando = null
            if (r.registrada) {
                Toaster.exito("Informe emitido: ya está en los documentos del paciente")
                refrescar()
                alTerminar(true)
            } else {
                Toaster.error(r.mensajePsico())
                alTerminar(false)
            }
        }
    }

    suspend fun htmlInforme(): String? {
        val i = informe ?: return null
        if (informePendiente != null) { temporizadorInforme?.cancel(); enviarInforme() }
        return conIndicador(Gestion.CARGANDO) { EvaluacionPsicoRepo.htmlInforme(i.id) }
    }

    // ── Plan → tratamiento ───────────────────────────────────────────────────

    /**
     * Antes de "Crear tratamiento con este plan": guarda lo pendiente y relee el
     * espacio (el `planPrefill` lo arma el servidor con lo último guardado).
     * null si no se pudo.
     */
    suspend fun prefillParaCrear(): Pair<String, PrefillPlanPsico>? {
        if (!enviarPendientes()) return null
        val r = conIndicador(Gestion.CARGANDO) { EvaluacionPsicoRepo.cargar(tratamientoId = tratamientoId) }
        if (r !is EvaluacionPsicoRepo.Carga.Ok) {
            Toaster.error((r as EvaluacionPsicoRepo.Carga.Error).mensaje)
            return null
        }
        val e = r.espacio.evaluacion ?: return null
        val prefill = r.espacio.planPrefill ?: PrefillPlanPsico()
        return e.id to prefill
    }

    private fun esImagen(a: ArchivoSeleccionado): Boolean =
        a.mime?.startsWith("image/") == true ||
            a.nombre.substringAfterLast('.', "").lowercase() in listOf("jpg", "jpeg", "png", "webp", "heic")

    override fun onCleared() {
        guardarAlSalir()
        super.onCleared()
    }
}
