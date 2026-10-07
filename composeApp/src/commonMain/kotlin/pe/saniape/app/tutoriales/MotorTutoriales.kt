package pe.saniape.app.tutoriales

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pe.saniape.app.data.Preferencias

/**
 * Una instancia de un ancla en pantalla (`Modifier.tourAncla`). Un mismo id
 * puede estar varias veces (✓ Completar en cada cita): se señala la primera visible.
 */
class RegistroAncla(
    val id: String,
    /** Pantalla en la que vive (null = global: la barra inferior). */
    val pantalla: String?,
    /** Ventana/capa en la que se dibuja (0 = la principal; cada diálogo con host, la suya). */
    val capa: Int,
) {
    /** Rectángulo en coordenadas de SU ventana (recortado por sus padres). */
    var rect: Rect? = null
}

/** "Reducir movimiento" del sistema (lo pone la capa nativa; iOS: siempre false por ahora). */
object Movimiento {
    var reducido by mutableStateOf(false)
}

/**
 * Motor de los tutoriales "hazlo conmigo" (contrato §1.2). Uno solo para toda
 * la app del staff.
 *
 *  - NADA arranca solo: un tutorial empieza solo con [iniciar] (centro de
 *    ayuda, píldora, "Hazlo conmigo" de Primeros pasos).
 *  - Sin tutorial en curso no procesa señales ni pide nada al servidor: las
 *    anclas solo se anotan en un mapa (costo ~0). DALU no paga nada.
 *  - La máquina de estados es [Maquina] (pura, con tests de paridad con la web).
 */
object MotorTutoriales {

    private val scope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Main) }

    // ── Clínica / usuario ──────────────────────────────────────────────────
    private var clinicaId: String? = null
    private var userId: String? = null
    var primerosPasosActivos by mutableStateOf(false)
        private set
    var esAdmin by mutableStateOf(false)
        private set

    // ── Catálogo y progreso ────────────────────────────────────────────────
    var catalogo by mutableStateOf<CatalogoTutoriales?>(null)
        private set
    /** La web todavía no tiene el endpoint (404): la ayuda se oculta. */
    var noDisponible by mutableStateOf(false)
        private set
    var cargandoCatalogo by mutableStateOf(false)
        private set
    var errorCatalogo by mutableStateOf<String?>(null)
        private set
    var progreso by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    private val marcasSesion = mutableMapOf<String, String>()
    private var catalogoEn: Long = 0

    // ── Estado del tutorial ────────────────────────────────────────────────
    var estado by mutableStateOf(INACTIVO)
        private set
    val tutorial: TutorialApp? get() = catalogo?.tutorial(estado.tourId)
    val pasoActual: PasoApp? get() = tutorial?.pasos?.getOrNull(estado.paso)

    // ── Centro de ayuda ────────────────────────────────────────────────────
    var ayudaAbierta by mutableStateOf(false)
        private set
    var pantallaAyuda by mutableStateOf<String?>(null)
        private set

    /** Navegar a una pantalla de la app ("Llévame", "Retomar"). Lo pone ClinicaConTabs. */
    var navegador: ((String) -> Unit)? = null
    /** Pantallas a las que [navegador] sabe llevar (para ofrecer "Llévame"). */
    var navegables: Set<String> = emptySet()

    // ── Pantallas ──────────────────────────────────────────────────────────
    private val pila = mutableStateListOf<Pair<Any, String>>()
    val pantallaActual: String? get() = pila.lastOrNull()?.second

    fun entrarPantalla(token: Any, id: String) {
        val antes = pantallaActual
        pila.removeAll { it.first === token }
        pila.add(token to id)
        if (pantallaActual != antes) alCambiarPantalla()
    }

    fun salirPantalla(token: Any) {
        val antes = pantallaActual
        pila.removeAll { it.first === token }
        if (pantallaActual != antes) alCambiarPantalla()
    }

    private fun alCambiarPantalla() {
        if (!estado.enCurso) return
        senal(Senal.Pantalla(pantallaActual))
        programarDom()
    }

    // ── Hosts (capas que dibujan foco/tarjeta: la principal y cada diálogo) ──
    private val hosts = mutableStateListOf<Int>()
    private var ultimaCapa = 0
    fun nuevaCapa(): Int = ++ultimaCapa
    fun registrarHost(capa: Int) { hosts.remove(capa); hosts.add(capa) }
    fun quitarHost(capa: Int) { hosts.remove(capa) }
    /** La capa de más arriba (un diálogo abierto tapa a la principal). */
    val hostSuperior: Int? get() = hosts.lastOrNull()
    /** ¿Hay un diálogo (con host) abierto encima de la pantalla? */
    val hayDialogo: Boolean get() = hosts.size > 1

    // ── Anclas ─────────────────────────────────────────────────────────────
    private val registros = HashMap<String, MutableList<RegistroAncla>>()
    var objetivo by mutableStateOf<RegistroAncla?>(null)
        private set
    var rectObjetivo by mutableStateOf<Rect?>(null)
        private set

    fun registrar(r: RegistroAncla) {
        registros.getOrPut(r.id) { mutableListOf() }.add(r)
        if (estado.fase == Fase.ACTIVO) programarDom()
    }

    fun quitar(r: RegistroAncla) {
        registros[r.id]?.let { l -> l.remove(r); if (l.isEmpty()) registros.remove(r.id) }
        if (objetivo === r) { objetivo = null; rectObjetivo = null }
        if (estado.fase == Fase.ACTIVO) programarDom()
    }

    /** onGloballyPositioned del ancla. Barato: solo escribe estado si es el objetivo. */
    fun posicion(r: RegistroAncla, rect: Rect) {
        if (r.rect == rect) return
        r.rect = rect
        if (objetivo === r) rectObjetivo = rect
        else if (objetivo == null && estado.fase == Fase.ACTIVO && pasoActual?.anclasEfectivas?.contains(r.id) == true) actualizarObjetivo()
    }

    private fun enPantalla(r: RegistroAncla): Boolean = r.pantalla == null || r.pantalla == pantallaActual

    /** ¿El ancla está en pantalla? (montada en la pantalla actual o global) */
    fun visible(ancla: String): Boolean = registros[ancla]?.any { enPantalla(it) && (it.rect?.let { x -> x.width > 0f || x.height > 0f } ?: true) } == true

    private fun entorno() = Entorno(pantallaActual, ::visible)

    private fun actualizarObjetivo() {
        val paso = pasoActual
        if (estado.fase != Fase.ACTIVO || paso == null) { objetivo = null; rectObjetivo = null; return }
        for (a in paso.anclasEfectivas) {
            val cands = registros[a]?.filter(::enPantalla).orEmpty()
            if (cands.isEmpty()) continue
            val conRect = cands.filter { it.rect?.let { x -> x.width > 0f && x.height > 0f } == true }
            val elegido = conRect.minByOrNull { it.rect!!.top } ?: cands.first()
            objetivo = elegido
            rectObjetivo = elegido.rect
            return
        }
        objetivo = null; rectObjetivo = null
    }

    private var domPendiente = false
    private fun programarDom() {
        if (domPendiente) return
        domPendiente = true
        scope.launch {
            delay(60)
            domPendiente = false
            if (estado.fase == Fase.ACTIVO) senal(Senal.Dom(::visible))
            actualizarObjetivo()
        }
    }

    // ── Señales públicas ──────────────────────────────────────────────────
    /** Tocó un ancla (o algo dentro de ella). */
    fun toque(ancla: String) {
        if (estado.fase == Fase.ACTIVO) senal(Senal.Clic(listOf(ancla)))
    }

    /** Cambió el valor de un campo dentro de un ancla. */
    fun campo(ancla: String, valor: String) {
        if (estado.fase == Fase.ACTIVO) senal(Senal.Campo(listOf(ancla), valor))
    }

    /**
     * La app terminó una acción REAL (el servidor respondió OK o quedó en la
     * cola offline). Se llama desde los ViewModels / repos. Sin tutorial en
     * curso no hace nada.
     */
    fun tarea(nombre: String) {
        if (!estado.enCurso) return
        scope.launch { senal(Senal.Tarea(nombre)) }
    }

    private fun senal(s: Senal) {
        val e = estado
        val tut = tutorial ?: return
        if (!e.enCurso) return
        aplicar(Maquina.procesarSenal(e, tut.pasos, tut.meta.tarea, s, entorno()))
    }

    private fun aplicar(t: Transicion) {
        val antes = estado
        val tour = antes.tourId ?: t.estado.tourId
        if (t.medir.isNotEmpty() && tour != null) medir(tour, t.medir)
        if (t.estado == antes) return
        estado = t.estado
        if (t.estado.fase == Fase.COMPLETADO && tour != null) marcarLocal(tour, "completado")
        persistir()
        actualizarObjetivo()
    }

    private fun medir(tour: String, m: List<Medicion>) {
        m.forEach { x -> scope.launch { TutorialesRepo.evento(tour, x.evento, x.paso) } }
    }

    // ── Acciones ──────────────────────────────────────────────────────────
    fun iniciar(id: String) {
        val tut = catalogo?.tutorial(id) ?: return
        ayudaAbierta = false
        if (estado.enCurso) aplicar(Maquina.abandonar(estado, "pospuesto"))
        val t = Maquina.iniciar(id, tut.pasos, entorno())
        if (t.medir.isNotEmpty()) medir(id, t.medir)
        restaurado = true
        estado = t.estado
        marcarLocal(id, if (ReglasTutorial.hecho(id, progreso)) "completado" else "visto")
        persistir()
        actualizarObjetivo()
        // Igual que la web: al empezar se evalúa la pantalla (si el paso es de otra, queda en pausa).
        senal(Senal.Pantalla(pantallaActual))
    }

    fun manual() = aplicar(Maquina.avanzar(estado, tutorial?.pasos.orEmpty(), entorno()))
    fun saltar() = aplicar(Maquina.abandonar(estado, "saltado"))
    fun posponer() = aplicar(Maquina.abandonar(estado, "pospuesto"))
    fun cerrarCelebracion() = aplicar(Maquina.cerrar())

    fun retomar() {
        aplicar(Maquina.reanudar(estado))
        val p = pasoActual ?: return
        if (p.pantallas.isNotEmpty() && pantallaActual !in p.pantallas) navegador?.invoke(p.pantallas.first())
        else programarDom()
    }

    /** "Llévame": un paso que espera una pantalla. */
    fun llevar() {
        val destino = pasoActual?.espera?.takeIf { it.tipo == "pantalla" }?.opciones?.firstOrNull() ?: return
        navegador?.invoke(destino)
    }

    fun otroTutorial() {
        aplicar(Maquina.cerrar())
        abrirAyuda()
    }

    // ── Centro de ayuda ───────────────────────────────────────────────────
    fun abrirAyuda(pantalla: String? = pantallaActual) {
        pantallaAyuda = pantalla
        ayudaAbierta = true
        cargarCatalogo(maxEdadMs = 120_000)
    }

    fun cerrarAyuda() { ayudaAbierta = false }

    fun lanzarDesdeAyuda(id: String) {
        ayudaAbierta = false
        scope.launch { delay(if (Movimiento.reducido) 0 else 220); iniciar(id) }
    }

    // ── Progreso ──────────────────────────────────────────────────────────
    private fun marcarLocal(clave: String, valor: String) {
        marcasSesion[clave] = valor
        progreso = progreso + (clave to valor)
    }

    /** Píldora vista / "No mostrar más": local + servidor. */
    fun marcarVisto(clave: String) {
        marcarLocal(clave, "visto")
        scope.launch { TutorialesRepo.marcarVisto(clave) }
    }

    // ── Ciclo de vida ─────────────────────────────────────────────────────
    /**
     * Al cargar el contexto de la clínica. El catálogo se pide SOLO si la
     * clínica tiene Primeros pasos (las nuevas) o hay un tutorial en pausa
     * guardado; si no, recién al abrir la ayuda.
     */
    fun configurar(clinica: String, usuario: String?, primerosPasos: Boolean, admin: Boolean) {
        if (clinica != clinicaId || usuario != userId) {
            clinicaId = clinica; userId = usuario
            catalogo = null; progreso = emptyMap(); marcasSesion.clear(); catalogoEn = 0
            noDisponible = false; errorCatalogo = null
            estado = INACTIVO; objetivo = null; rectObjetivo = null
            restaurado = false
        }
        primerosPasosActivos = primerosPasos
        esAdmin = admin
        if (primerosPasos || pausaGuardada() != null) cargarCatalogo()
    }

    /** La tarjeta de Primeros pasos se minimizó/descartó/volvió: las píldoras siguen ese estado. */
    fun actualizarPrimerosPasos(activos: Boolean) { primerosPasosActivos = activos }

    /** Al volver al frente: refresca el catálogo si ya se usaba y está viejo. */
    fun alVolverAlFrente() {
        if (catalogo != null) cargarCatalogo(maxEdadMs = 300_000)
    }

    fun limpiar() {
        clinicaId = null; userId = null
        catalogo = null; progreso = emptyMap(); marcasSesion.clear()
        estado = INACTIVO; objetivo = null; rectObjetivo = null
        ayudaAbierta = false; noDisponible = false; restaurado = false
        primerosPasosActivos = false; esAdmin = false
    }

    fun cargarCatalogo(maxEdadMs: Long = Long.MAX_VALUE) {
        if (cargandoCatalogo || noDisponible) return
        if (catalogo != null && ahora() - catalogoEn < maxEdadMs) return
        cargandoCatalogo = true
        errorCatalogo = null
        val clinica = clinicaId
        scope.launch {
            val r = TutorialesRepo.catalogo()
            cargandoCatalogo = false
            if (clinica != clinicaId) return@launch // cambió de clínica mientras tanto
            when (r) {
                is TutorialesRepo.Resultado.Ok -> {
                    catalogo = r.catalogo
                    catalogoEn = ahora()
                    progreso = r.catalogo.progreso + marcasSesion
                    restaurarPausa()
                }
                TutorialesRepo.Resultado.NoDisponible -> { noDisponible = true; ayudaAbierta = false }
                is TutorialesRepo.Resultado.Error -> errorCatalogo = r.mensaje
            }
        }
    }

    private fun ahora(): Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()

    // ── Pausa persistida (por clínica + usuario) ───────────────────────────
    private var restaurado = false
    private fun clavePausa(): String? {
        val c = clinicaId ?: return null
        val u = userId ?: return null
        return "sania_tutorial_${c}_$u"
    }

    private fun pausaGuardada(): Pair<String, Int>? {
        val crudo = clavePausa()?.let { Preferencias.texto(it) } ?: return null
        return decodificarPausa(crudo)
    }

    private fun restaurarPausa() {
        if (restaurado) return
        restaurado = true
        val (tourId, paso) = pausaGuardada() ?: return
        val tut = catalogo?.tutorial(tourId)
        if (tut == null) { clavePausa()?.let { Preferencias.setTexto(it, null) }; return }
        if (estado.fase != Fase.INACTIVO) return
        estado = Maquina.restaurar(tut.id, paso, tut.pasos.size)
    }

    private fun persistir() {
        val clave = clavePausa() ?: return
        val e = estado
        if (e.enCurso && e.tourId != null) Preferencias.setTexto(clave, codificarPausa(e.tourId, e.paso))
        else if (restaurado) Preferencias.setTexto(clave, null)
    }
}

/** "tourId|paso" — lo que se guarda en el teléfono para retomar. */
internal fun codificarPausa(tourId: String, paso: Int): String = "$tourId|$paso"

internal fun decodificarPausa(crudo: String?): Pair<String, Int>? {
    val partes = crudo?.split("|") ?: return null
    if (partes.size != 2 || partes[0].isBlank()) return null
    return partes[0] to (partes[1].toIntOrNull() ?: 0)
}
