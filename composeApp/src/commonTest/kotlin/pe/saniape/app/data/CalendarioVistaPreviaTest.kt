package pe.saniape.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import pe.saniape.app.data.staff.AsignacionEvento
import pe.saniape.app.data.staff.Cabecera
import pe.saniape.app.data.staff.CalendarioTraido
import pe.saniape.app.data.staff.ConfigFuenteCal
import pe.saniape.app.data.staff.DecisionPaciente
import pe.saniape.app.data.staff.DecisionesCal
import pe.saniape.app.data.staff.EventoVista
import pe.saniape.app.data.staff.GrupoUnido
import pe.saniape.app.data.staff.PacienteDetectado
import pe.saniape.app.data.staff.PestanaEventos
import pe.saniape.app.data.staff.Texto
import pe.saniape.app.data.staff.aJson
import pe.saniape.app.data.staff.alternarMarca
import pe.saniape.app.data.staff.avisoCupoCruces
import pe.saniape.app.data.staff.calendariosGoogleDe
import pe.saniape.app.data.staff.citasAImportar
import pe.saniape.app.data.staff.configFuenteDe
import pe.saniape.app.data.staff.corregirPaciente
import pe.saniape.app.data.staff.cuerpoGoogle
import pe.saniape.app.data.staff.distancia
import pe.saniape.app.data.staff.esCitaMarcada
import pe.saniape.app.data.staff.estadoCabecera
import pe.saniape.app.data.staff.estadoCalendarioDe
import pe.saniape.app.data.staff.eventosDePestana
import pe.saniape.app.data.staff.fechaCortaCal
import pe.saniape.app.data.staff.filasPacientes
import pe.saniape.app.data.staff.hexColor
import pe.saniape.app.data.staff.marcarTodos
import pe.saniape.app.data.staff.nombreColor
import pe.saniape.app.data.staff.normalizarNombre
import pe.saniape.app.data.staff.opcionesFicha
import pe.saniape.app.data.staff.otrosPorParecido
import pe.saniape.app.data.staff.puedeImportar
import pe.saniape.app.data.staff.quePasa
import pe.saniape.app.data.staff.resolverUnion
import pe.saniape.app.data.staff.resultadoImportacionDe
import pe.saniape.app.data.staff.textoBotonImportar
import pe.saniape.app.data.staff.textoEstadoFuente
import pe.saniape.app.data.staff.textoQuitarFuente
import pe.saniape.app.data.staff.toastImportacion
import pe.saniape.app.data.staff.usarActual
import pe.saniape.app.data.staff.vistaPreviaDe

/**
 * Vista previa de la importación de Google Calendar en la app: lectura de las
 * respuestas, armado de las decisiones y los mismos cálculos que la web
 * (lib/calendario/revision.ts y el pie de VistaPreviaCalendario.tsx).
 */
class CalendarioVistaPreviaTest {
    private fun obj(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject

    private fun ev(id: String, accion: String = "crear", fecha: String = "2026-10-20", hora: String = "09:00", pasada: Boolean = false, revisarFlags: Boolean = false) = EventoVista(
        id = id, titulo = "Paciente $id", fecha = fecha, hora = hora, duracion = 60, color = "sin_color", recurrente = false,
        todoElDia = false, accion = accion, motivo = null, pasada = pasada, estado = "Confirmada", paciente = null, pacienteId = null,
        nombre = "Paciente $id", telefono = null, direccion = null, domicilio = false, sugeridoNoCita = revisarFlags, cruce = false, sinCupo = false,
    )

    private fun pac(clave: String, nombre: String, eventos: Int, unidos: List<GrupoUnido> = emptyList(), revisar: Boolean = false) = PacienteDetectado(
        clave = clave, nombre = nombre, telefono = null, direccion = null, eventos = eventos, existente = null,
        candidatas = emptyList(), usar = null, revisar = revisar, motivoRevisar = null, unidos = unidos,
    )

    // ── Lectura ──

    private val vistaJson = """
        {"hoy":"2026-10-15",
         "resumen":{"eventos":40,"crearFuturas":10,"crearPasadas":5,"actualizar":0,"cancelar":0,"ignorar":3,"omitir":1,
                    "protegidas":0,"sinCambios":0,"revisar":2,"cruces":1,"pacientesNuevos":7,"pacientesExistentes":2,
                    "pacientesPorRevisar":1,"sinCupo":4,"mostrados":{"revisar":6,"futuras":10,"pasadas":5}},
         "pacientes":[{"clave":"JENNY PEREZ","nombre":"Jenny Pérez","telefono":"952123456","direccion":null,"eventos":3,
                       "existente":{"id":"f1","nombre":"Jenny Pérez","inactivo":true},
                       "candidatas":[{"id":"f2","nombre":"Jenny Perez Q","telefono":null,"inactivo":false}],
                       "usar":null,"revisar":true,"motivoRevisar":"Hay varias fichas",
                       "unidos":[{"clave":"JENY PEREZ","nombre":"Jeny Perez","eventos":1}]}],
         "colores":[{"clave":"sin_color","eventos":30},{"clave":"11","eventos":10}],
         "items":[{"id":"e2","titulo":"Rosa","fecha":"2026-10-21","hora":"10:00","duracion":45,"color":"11","recurrente":true,
                   "todoElDia":false,"accion":"revisar","motivo":"Solo un nombre","pasada":false,"estado":null,"paciente":null,
                   "pacienteId":null,"nombre":"Rosa","telefono":null,"direccion":null,"domicilio":false,"sugeridoNoCita":false,
                   "cruce":true,"sinCupo":false}],
         "nombreCalendario":"Agenda","avisos":["El calendario es muy grande"]}
    """.trimIndent()

    @Test fun leeLaVistaPrevia() {
        val v = vistaPreviaDe(obj(vistaJson))
        assertEquals("2026-10-15", v.hoy)
        assertEquals(10, v.resumen.crearFuturas); assertEquals(4, v.resumen.sinCupo); assertEquals(6, v.resumen.mostrados.revisar)
        val p = v.pacientes.single()
        assertEquals("JENNY PEREZ", p.clave); assertTrue(p.existente!!.inactivo); assertEquals("f2", p.candidatas.single().id)
        assertEquals("JENY PEREZ", p.unidos.single().clave); assertNull(p.direccion)
        assertEquals(listOf("sin_color", "11"), v.colores.map { it.clave })
        val e = v.items.single()
        assertEquals("revisar", e.accion); assertTrue(e.cruce); assertTrue(e.recurrente); assertEquals(45, e.duracion)
        assertEquals("Agenda", v.nombreCalendario); assertEquals(1, v.avisos.size)
    }

    @Test fun leeElResultadoDeImportar() {
        val r = resultadoImportacionDe(obj("""{"ok":true,"creadas":1,"actualizadas":0,"canceladas":0,"pacientesCreados":1,"porRevisar":2,
            "sinCupo":[{"eventId":"x","titulo":"Ana","fecha":"2026-10-20","hora":"09:00"}],"errores":["uno"],"enCurso":false}"""))
        assertEquals(1, r.creadas); assertEquals("Ana", r.sinCupo.single().titulo); assertEquals(listOf("uno"), r.errores)
        assertEquals("Listo: 1 cita importada", toastImportacion(r))
        assertEquals("Van 3 citas: sigue importando", toastImportacion(r.copy(creadas = 3, enCurso = true)))
    }

    @Test fun leeCalendariosYFuentes() {
        val cals = calendariosGoogleDe(obj("""{"calendarios":[{"id":"a@x","nombre":"Agenda","principal":true,"color":"#039be5","rol":"owner"},
            {"id":"b","nombre":"Feriados","principal":false,"color":null,"rol":"freeBusyReader"}]}"""))
        assertEquals(2, cals.size); assertTrue(cals[0].principal); assertEquals("freeBusyReader", cals[1].rol)
        val e = estadoCalendarioDe(obj("""{"googleConfigurado":true,"llaveConfigurada":true,"conexiones":[],
            "fuentes":[{"id":"f1","conexion_id":"c1","calendario_id":"a@x","nombre":"Agenda","terapeuta_id":"t1","proveedor":"google",
            "config":{"pasadas":"omitir","mapaColores":{"11":"ignorar"},"recordatoriosPacientes":true,"nuevosEventos":"crear"}}]}"""))
        val f = e.calendarios.single()
        assertEquals("c1", f.conexionId); assertEquals("a@x", f.calendarioId); assertEquals("t1", f.terapeutaId)
        assertEquals("omitir", f.config.pasadas); assertEquals("ignorar", f.config.mapaColores["11"]); assertEquals("crear", f.config.nuevosEventos)
    }

    // ── Decisiones y cuerpo ──

    @Test fun armaElCuerpoComoLaWeb() {
        val dec = DecisionesCal(
            noCita = setOf("e1"), siCita = setOf("e2"),
            pacientes = mapOf(
                "JENY" to DecisionPaciente(unirA = "JENNY"),
                "SUSANA" to DecisionPaciente(telefono = Texto("952123456"), direccion = Texto(null)),
            ),
            porEvento = mapOf("e3" to AsignacionEvento(pacienteId = "p1", etiqueta = "Ana Ruiz"), "e4" to AsignacionEvento(nombre = "Rosa Quispe")),
        )
        val cfg = ConfigFuenteCal(mapaColores = mapOf("11" to "ignorar"), pasadas = "atendida", desde = "2025-01-01", recordatoriosPacientes = false)
        val b = cuerpoGoogle("f1", cfg, dec)
        assertEquals("f1", b["fuenteId"]!!.jsonPrimitive.content)
        val d = b["decisiones"]!!.jsonObject
        assertEquals("e1", d["noCita"]!!.jsonArray.single().jsonPrimitive.content)
        assertEquals("JENNY", d["pacientes"]!!.jsonObject["JENY"]!!.jsonObject["unirA"]!!.jsonPrimitive.content)
        val sus = d["pacientes"]!!.jsonObject["SUSANA"]!!.jsonObject
        assertEquals("952123456", sus["telefono"]!!.jsonPrimitive.content)
        assertEquals(JsonNull, sus["direccion"])                         // borrado a propósito = null
        assertFalse(d["pacientes"]!!.jsonObject["JENY"]!!.jsonObject.containsKey("telefono"))  // sin tocar = ausente
        val e3 = d["porEvento"]!!.jsonObject["e3"]!!.jsonObject
        assertEquals("p1", e3["pacienteId"]!!.jsonPrimitive.content)
        assertFalse(e3.containsKey("etiqueta"))                          // la etiqueta es solo de pantalla
        val c = b["config"]!!.jsonObject
        assertEquals("ignorar", c["mapaColores"]!!.jsonObject["11"]!!.jsonPrimitive.content)
        assertEquals(JsonPrimitive(false), c["recordatoriosPacientes"])
        // Ida y vuelta de la config.
        assertEquals(cfg, configFuenteDe(c))
    }

    // ── revision.ts ──

    @Test fun esCitaYAlternar() {
        val crear = ev("a"); val ignorar = ev("b", "ignorar"); val revisar = ev("c", "revisar"); val protegida = ev("d", "protegida")
        var d = DecisionesCal()
        assertTrue(esCitaMarcada(crear, d)); assertFalse(esCitaMarcada(ignorar, d)); assertTrue(esCitaMarcada(revisar, d))
        d = alternarMarca(ignorar, d)
        assertTrue("b" in d.siCita); assertTrue(esCitaMarcada(ignorar, d))
        d = alternarMarca(crear, d)
        assertTrue("a" in d.noCita); assertFalse(esCitaMarcada(crear, d))
        // Volver a marcar "por revisar" no pide siCita (sin paciente no se crea).
        d = alternarMarca(revisar, d); d = alternarMarca(revisar, d)
        assertFalse("c" in d.siCita); assertTrue(esCitaMarcada(revisar, d))
        assertTrue(esCitaMarcada(protegida, d))
    }

    @Test fun seleccionarTodosRespetaEditablesYProtegidas() {
        val lista = listOf(ev("a"), ev("b", "ignorar"), ev("c", "protegida"), ev("d", "omitir", pasada = true), ev("e", "omitir"))
        var d = DecisionesCal()
        assertEquals(Cabecera.MEZCLA, estadoCabecera(lista, d))
        d = marcarTodos(lista, true, d)
        assertEquals(Cabecera.TODOS, estadoCabecera(lista, d))
        assertEquals(setOf("b", "e"), d.siCita)                           // la protegida y la pasada omitida no cambian
        d = marcarTodos(lista, false, d)
        assertEquals(Cabecera.NINGUNO, estadoCabecera(lista, d))
        assertEquals(setOf("a", "b", "e"), d.noCita)
        assertFalse("c" in d.noCita)
        assertEquals(Cabecera.VACIO, estadoCabecera(listOf(ev("c", "protegida")), d))
    }

    // ── El pie ──

    @Test fun ajusteManualDelContador() {
        val v = vistaPreviaDe(obj(vistaJson)).let {
            it.copy(
                resumen = it.resumen.copy(crearFuturas = 2, crearPasadas = 0),
                items = listOf(ev("a"), ev("b"), ev("c", "ignorar"), ev("d", "revisar"), ev("e", "omitir")),
            )
        }
        var d = DecisionesCal()
        assertEquals(2, citasAImportar(v, d))
        d = alternarMarca(v.items[0], d)                                   // desmarcar una que se creaba
        assertEquals(1, citasAImportar(v, d))
        d = alternarMarca(v.items[2], d)                                   // "no es cita" → sí es cita
        assertEquals(2, citasAImportar(v, d))
        d = d.copy(porEvento = d.porEvento + ("d" to AsignacionEvento(nombre = "Rosa Quispe")))  // por revisar con paciente
        assertEquals(3, citasAImportar(v, d))
        d = marcarTodos(v.items, false, d)
        assertEquals(0, citasAImportar(v, d))                              // nunca negativo
        assertEquals("Activar sincronización (sin citas por ahora)", textoBotonImportar(0, esGoogle = true, importando = false))
        assertTrue(puedeImportar(0, esGoogle = true, trabajando = false))
        assertFalse(puedeImportar(0, esGoogle = false, trabajando = false))
        assertEquals("Importar 1 cita", textoBotonImportar(1, true, false))
        assertEquals("Importar 12 citas", textoBotonImportar(12, true, false))
        assertFalse(puedeImportar(5, esGoogle = true, trabajando = true))
    }

    @Test fun pestanasPorFechaYHora() {
        val items = listOf(
            ev("tarde", fecha = "2026-10-20", hora = "15:00"),
            ev("pasada", fecha = "2026-09-01", hora = "08:00", pasada = true),
            ev("temprano", fecha = "2026-10-20", hora = "08:30"),
            ev("rev", "revisar", fecha = "2026-10-19", hora = "11:00"),
            ev("sug", fecha = "2026-10-25", hora = "07:00", revisarFlags = true),
        )
        assertEquals(listOf("pasada", "rev", "temprano", "tarde", "sug"), eventosDePestana(items, PestanaEventos.TODAS).map { it.id })
        assertEquals(listOf("rev", "temprano", "tarde", "sug"), eventosDePestana(items, PestanaEventos.FUTURAS).map { it.id })
        assertEquals(listOf("pasada"), eventosDePestana(items, PestanaEventos.PASADAS).map { it.id })
        assertEquals(listOf("rev", "sug"), eventosDePestana(items, PestanaEventos.REVISAR).map { it.id })
    }

    @Test fun quePasaConCadaEvento() {
        val cfg = ConfigFuenteCal()
        var d = DecisionesCal()
        assertEquals("Se crea", quePasa(ev("a"), d, cfg))
        assertEquals("Atendida (historial)", quePasa(ev("p", pasada = true), d, cfg))
        assertEquals("Por revisar", quePasa(ev("r", "revisar"), d, cfg))
        assertEquals("No es cita", ETIQUETA("ignorar"))
        d = alternarMarca(ev("a"), d)
        assertEquals("No se importa", quePasa(ev("a"), d, cfg))
        d = d.copy(porEvento = mapOf("r" to AsignacionEvento(nombre = "Rosa Quispe")))
        assertEquals("Se crea", quePasa(ev("r", "revisar"), d, cfg))
    }

    private fun ETIQUETA(a: String) = pe.saniape.app.data.staff.ETIQUETA_ACCION[a]

    // ── Pacientes: unir / separar ──

    @Test fun resolverUnionComoLaWeb() {
        val existe = { k: String -> k in setOf("A", "B", "C", "D", "E") }
        fun dec(vararg p: Pair<String, String>) = p.associate { it.first to DecisionPaciente(unirA = it.second) }
        assertEquals("A", resolverUnion("A", emptyMap(), existe))
        assertEquals("B", resolverUnion("A", dec("A" to "B"), existe))
        assertEquals("D", resolverUnion("A", dec("A" to "B", "B" to "C", "C" to "D"), existe))
        assertEquals("A", resolverUnion("A", dec("A" to "B", "B" to "C", "C" to "D", "D" to "E"), existe))
        assertEquals("A", resolverUnion("A", dec("A" to "B", "B" to "A"), existe))
        assertEquals("A", resolverUnion("A", dec("A" to "Z"), existe))
        assertEquals("B", resolverUnion("A", dec("A" to "B", "B" to "Z"), existe))
    }

    @Test fun unirYSepararEnPantalla() {
        val lista = listOf(pac("JENNY", "Jenny Pérez", 2), pac("JENY", "Jeny Perez", 1), pac("ROSA", "Rosa Quispe", 1))
        // Unir en pantalla: la fila desaparece y sus citas se suman.
        var dec = mapOf("JENY" to DecisionPaciente(unirA = "JENNY"))
        var filas = filasPacientes(lista, dec)
        assertEquals(listOf("JENNY", "ROSA"), filas.map { it.p.clave })
        assertEquals(3, filas[0].eventos)
        assertEquals("JENY", filas[0].unidos.single().clave); assertTrue(filas[0].unidos.single().local)
        // Separar: vuelve la fila.
        dec = mapOf("JENY" to DecisionPaciente(unirA = null))
        filas = filasPacientes(lista, dec)
        assertEquals(3, filas.size); assertEquals(2, filas[0].eventos)
    }

    @Test fun separarUnaUnionQueYaVinoDelServidor() {
        // Tras "Actualizar", el servidor ya trae a Jeny dentro de Jenny (3 = 2 + 1).
        val lista = listOf(pac("JENNY", "Jenny Pérez", 3, unidos = listOf(GrupoUnido("JENY", "Jeny Perez", 1))), pac("ROSA", "Rosa Quispe", 1))
        val unida = filasPacientes(lista, mapOf("JENY" to DecisionPaciente(unirA = "JENNY")))
        assertEquals(3, unida[0].eventos); assertFalse(unida[0].unidos.single().local)
        // La persona la separa: se descuenta y deja de mostrarse (vuelve al actualizar).
        val separada = filasPacientes(lista, mapOf("JENY" to DecisionPaciente(unirA = null)))
        assertEquals(2, separada[0].eventos); assertTrue(separada[0].unidos.isEmpty())
    }

    @Test fun mismaPersonaOrdenadaPorParecido() {
        val todos = listOf("ROSA" to "Rosa Quispe", "JENY" to "Jeny Perez", "ANA" to "Ana Pérez", "JENNY" to "Jenny Pérez")
        val otros = otrosPorParecido("JENNY", "Jenny Pérez", todos)
        assertEquals("JENY", otros.first().first)                         // la más parecida primero
        assertFalse(otros.any { it.first == "JENNY" })                    // sin sí misma
        assertEquals(3, otros.size)
        assertEquals("JENNY PEREZ", normalizarNombre("Jenny  Pérez!"))
        assertEquals(1, distancia("JENNY PEREZ", "JENY PEREZ"))
        assertEquals(99, distancia("ANA", "MARIA FERNANDA"))
    }

    @Test fun corregirFichaNueva() {
        val p = pac("SUSANA", "Susana Chávez", 2).copy(telefono = "999888777", direccion = "Calle 1")
        var d = DecisionesCal()
        // Nada cambia → no se guarda nada.
        assertEquals(d, corregirPaciente(p, d, "Susana Chávez", "999888777", "Calle 1"))
        d = corregirPaciente(p, d, "Susana Chávez Ruiz", "", "  Av.  Bolognesi 120 ")
        val dp = d.pacientes.getValue("SUSANA")
        assertEquals("Susana Chávez Ruiz", dp.nombre)
        assertEquals(Texto(null), dp.telefono)                            // borrado a propósito
        assertEquals(Texto("Av. Bolognesi 120"), dp.direccion)
        // Volver al nombre detectado limpia la corrección.
        d = corregirPaciente(p, d, "Susana Chávez", "", "Av. Bolognesi 120")
        assertNull(d.pacientes.getValue("SUSANA").nombre)
    }

    @Test fun opcionesDeFicha() {
        val p = pac("JENNY", "Jenny", 1, revisar = true).copy(
            existente = pe.saniape.app.data.staff.FichaExistente("f1", "Jenny", inactivo = true),
            candidatas = listOf(pe.saniape.app.data.staff.FichaCandidata("f2", "Jenny Q", "952000111", false)),
        )
        assertEquals(
            listOf("" to "Elegir…", "f1" to "Usar su ficha (DADA DE BAJA)", "f2" to "Es “Jenny Q” · 952000111", "nuevo" to "Crear ficha nueva"),
            opcionesFicha(p),
        )
        assertEquals("", usarActual(p, null))
        assertEquals("f2", usarActual(p, DecisionPaciente(usar = "f2")))
        assertEquals("nuevo", usarActual(pac("X", "X Y", 1), null))
        assertTrue(opcionesFicha(pac("X", "X Y", 1)).isEmpty())
    }

    // ── Textos ──

    @Test fun textosDeLaLista() {
        val f = CalendarioTraido(id = "f", nombre = "Agenda", profesional = null, esIcs = false, importadoEn = "2026-10-01T00:00:00Z",
            ultimaSync = null, error = null, citas = 1, sinCupo = 0)
        assertEquals("Sincronizado hace 5 min · 1 cita importada", textoEstadoFuente(f, "hace 5 min"))
        assertEquals("Sincronizado hace 5 min · 3 citas importadas · 2 por revisar", textoEstadoFuente(f.copy(citas = 3, porRevisar = 2), "hace 5 min"))
        assertEquals("Falta revisar e importar", textoEstadoFuente(f.copy(importadoEn = null), "nunca"))
        assertEquals("Dejar de traer", textoQuitarFuente("Agenda", esIcs = false).boton)
        assertTrue(textoQuitarFuente("Agenda", esIcs = false).confirmar.startsWith("¿Dejar de traer \"Agenda\"?"))
        assertEquals("Quitar archivo", textoQuitarFuente("a.ics", esIcs = true).boton)
        assertEquals("⚠ 2 citas próximas caen fuera del horario del profesional o sin cupo y 1 se cruzan entre sí. Las demás se importan igual.", avisoCupoCruces(2, 1))
        assertNull(avisoCupoCruces(0, 0))
        assertEquals("Color del calendario", nombreColor("sin_color")); assertEquals("Tomate (rojo)", nombreColor("11"))
        assertEquals("#d50000", hexColor("11")); assertEquals("#9aa3c7", hexColor("raro"))
        assertEquals("mar 20 oct 26", fechaCortaCal("2026-10-20"))
    }

    @Test fun falloDeImportarYCelular() {
        val f = pe.saniape.app.data.staff.FalloImportar.POSIBLE_EN_CURSO
        assertEquals(f, pe.saniape.app.data.staff.falloImportar("SIN_RED", 0))
        assertEquals(f, pe.saniape.app.data.staff.falloImportar("OCUPADO", 409))
        assertEquals(f, pe.saniape.app.data.staff.falloImportar(null, 504))
        assertEquals(pe.saniape.app.data.staff.FalloImportar.RECONECTAR, pe.saniape.app.data.staff.falloImportar("RECONECTAR", 409))
        assertEquals(pe.saniape.app.data.staff.FalloImportar.OTRO, pe.saniape.app.data.staff.falloImportar(null, 400))
        assertEquals("+51 (952) 123-456", pe.saniape.app.data.staff.soloCaracteresCelular("+51 (952) 123-456abc."))
    }
}
