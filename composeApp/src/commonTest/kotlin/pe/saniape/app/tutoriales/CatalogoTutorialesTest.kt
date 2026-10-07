package pe.saniape.app.tutoriales

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Parseo del catálogo (contrato §1.1) y reglas de UI del centro de ayuda / píldora. */
class CatalogoTutorialesTest {

    private val CATALOGO = """
    {
      "version": 1, "plataforma": "app",
      "primerosPasos": "visible", "primerosPasosActivos": true,
      "tutoriales": [
        { "id": "agendar-cita", "titulo": "Agendar una cita", "descripcion": "Con un paciente nuevo: lo registras por DNI.",
          "duracionSeg": 45, "duracionTexto": "45 s", "categoria": "basico", "icono": "📅",
          "claves": ["cita","agenda","reservar","turno","dni","nueva cita"],
          "pantallaApp": "Agenda", "pantallasApp": ["Inicio","Agenda","Pacientes"],
          "pasos": [
            { "id": "nueva", "titulo": "Toca \"+ Nueva\"", "texto": "Arriba de la agenda del día.",
              "ancla": "agenda.nueva_cita", "anclas": ["agenda.nueva_cita"], "pantallas": ["Agenda"],
              "espera": { "tipo": "aparece", "valor": "cita_form", "valores": ["cita_form"] },
              "boton": null, "opcional": false, "saltarSiYa": true, "campoNuevo": 1 }
          ],
          "meta": { "tarea": "cita_creada" } },
        { "id": "cobrar-cita", "titulo": "Cobrar una cita", "descripcion": "Con más de un medio de pago.",
          "duracionSeg": 40, "categoria": "cobros", "icono": "💰", "claves": ["pago"],
          "pantallaApp": "Agenda", "pantallasApp": ["Agenda"],
          "pasos": [ { "id": "c", "espera": { "tipo": "tarea", "valor": "cobro_registrado" } } ],
          "meta": { "tarea": "cobro_registrado" } },
        { "id": "conoce-menu", "titulo": "Conoce el menú", "pantallaApp": "Inicio", "pantallasApp": ["Inicio","Agenda"],
          "pasos": [ { "id": "m", "espera": { "tipo": "manual" }, "boton": "Entendido →" } ], "meta": { "tarea": null } },
        { "id": "futuro", "titulo": "Algo nuevo", "pantallaApp": "Inventario",
          "pasos": [ { "id": "x", "pantallas": ["Inventario"], "espera": { "tipo": "manual" } } ] }
      ],
      "excluidos": [ { "id": "invitar-equipo", "titulo": "Invitar a tu equipo", "razon": "Se gestiona en la web.", "rutaWeb": "/equipo" } ],
      "pantallas": [
        { "id": "Agenda", "pantallaWeb": "citas", "titulo": "Agenda",
          "guia": [ { "icono": "📆", "titulo": "Elegir el día", "texto": "Desliza la tira de días." } ],
          "sugeridos": ["cobrar-cita","agendar-cita","conoce-menu"], "pildora": "cobrar-cita", "clavePildora": "pildora:citas" },
        { "id": "Caja", "pantallaWeb": null, "titulo": "Caja de hoy", "guia": [], "sugeridos": [], "pildora": null, "clavePildora": null }
      ],
      "tutorialDeTarea": { "cita": "agendar-cita", "cobro": "cobrar-cita" },
      "progreso": { "agendar-cita": "completado", "pildora:pacientes": "visto" },
      "pagina": { "slug": "movivida", "url": "https://movivida.saniape.com" }
    }
    """.trimIndent()

    @Test
    fun parseaElCatalogoDelContrato() {
        val c = assertNotNull(parsearCatalogo(CATALOGO))
        assertTrue(c.primerosPasosActivos)
        assertEquals("visible", c.primerosPasos)
        // "futuro" habla de una pantalla que esta versión no tiene: se descarta.
        assertEquals(listOf("agendar-cita", "cobrar-cita", "conoce-menu"), c.tutoriales.map { it.id })
        val t = c.tutorial("agendar-cita")!!
        assertEquals("45 s", t.duracion)
        assertEquals("cita_creada", t.meta.tarea)
        val p = t.pasos.single()
        assertEquals(listOf("agenda.nueva_cita"), p.anclasEfectivas)
        assertEquals("aparece", p.espera.tipo)
        assertTrue(p.saltarSiYa)
        assertEquals("/equipo", c.excluidos.single().rutaWeb)
        assertEquals("pildora:citas", c.guia("Agenda")?.clavePildora)
        assertNull(c.guia("Caja")?.clavePildora)
        assertEquals("https://movivida.saniape.com", c.pagina?.url)
        assertEquals("cobrar-cita", c.tutorialDeTarea["cobro"])
        // Defaults: sin duracionTexto se arma igual que la web.
        assertEquals("40 s", c.tutorial("cobrar-cita")!!.duracion)
    }

    @Test
    fun catalogoIlegibleEsNullYCamposAusentesTienenDefault() {
        assertNull(parsearCatalogo("<html>404</html>"))
        val c = assertNotNull(parsearCatalogo("""{"tutoriales":[]}"""))
        assertFalse(c.primerosPasosActivos)
        assertNull(c.pagina)
        assertEquals(emptyMap(), c.progreso)
    }

    @Test
    fun anclaSinListaUsaLaUnica() {
        val p = PasoApp("x", ancla = "nav.mas")
        assertEquals(listOf("nav.mas"), p.anclasEfectivas)
        assertEquals(listOf("cita_form"), EsperaApp("aparece", "cita_form").opciones)
    }

    @Test
    fun buscadorSinTildesYTodasLasPalabras() {
        val c = parsearCatalogo(CATALOGO)!!
        assertEquals(listOf("agendar-cita"), ReglasTutorial.buscar("AGENDA dní", c.tutoriales).map { it.id })
        assertEquals(listOf("cobrar-cita"), ReglasTutorial.buscar("cobrár", c.tutoriales).map { it.id })
        assertEquals(emptyList(), ReglasTutorial.buscar("cita receta", c.tutoriales))
        assertEquals(3, ReglasTutorial.buscar("   ", c.tutoriales).size)
        assertEquals("nino pequeno camion", ReglasTutorial.normalizar("Niño Pequeño Camión"))
    }

    @Test
    fun sugeridosPendientesPrimero() {
        val c = parsearCatalogo(CATALOGO)!!
        val s = ReglasTutorial.sugeridos(c.guia("Agenda"), c, c.progreso)
        assertEquals(listOf("cobrar-cita", "conoce-menu", "agendar-cita"), s.map { it.id })
        assertEquals(emptyList(), ReglasTutorial.sugeridos(null, c, c.progreso))
    }

    @Test
    fun pildoraMismasReglasQueLaWeb() {
        val c = parsearCatalogo(CATALOGO)!!
        val g = c.guia("Agenda")
        assertEquals("cobrar-cita", ReglasTutorial.pildora(g, c, c.progreso, true)?.id)
        // Clínicas sin Primeros pasos (DALU): nunca.
        assertNull(ReglasTutorial.pildora(g, c, c.progreso, false))
        // Ya la vio (aquí o en la web, misma clave).
        assertNull(ReglasTutorial.pildora(g, c, c.progreso + ("pildora:citas" to "visto"), true))
        // Apagó las sugerencias.
        assertNull(ReglasTutorial.pildora(g, c, c.progreso + ("pildoras:off" to "visto"), true))
        // Ya vio el tutorial sugerido: queda conoce-menu, que nunca se ofrece.
        assertNull(ReglasTutorial.pildora(g, c, c.progreso + ("cobrar-cita" to "visto"), true))
        // Pantalla sin píldora.
        assertNull(ReglasTutorial.pildora(c.guia("Caja"), c, c.progreso, true))
    }

    @Test
    fun textosDeUi() {
        assertEquals("Profesionales", pluralPersonal("Profesional"))
        assertEquals("Fisioterapeutas", pluralPersonal("Fisioterapeuta"))
        assertEquals("Especialistas", pluralPersonal("Especialistas"))
        assertEquals("Tócalo tú, te espero", ReglasTutorial.textoEspera("clic"))
        assertNull(ReglasTutorial.textoEspera("manual"))
        assertEquals("30 s", textoDuracion(30))
        assertEquals("10 s", textoDuracion(4))
        assertEquals("2 min", textoDuracion(90))
    }
}
