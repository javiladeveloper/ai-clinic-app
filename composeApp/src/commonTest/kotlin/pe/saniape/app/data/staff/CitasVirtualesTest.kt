package pe.saniape.app.data.staff

import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Citas virtuales (2026-10-16). MISMOS casos que la web
 * (lib/__tests__/citas-virtuales.test.ts): si uno cambia, cambia el otro.
 */
class CitasVirtualesTest {

    private fun ok(v: Any?): String = (validarEnlaceVirtual(v) as ResultadoEnlace.Ok).url
    private fun malo(v: Any?) = assertIs<ResultadoEnlace.Error>(validarEnlaceVirtual(v), "debió rechazar: $v")

    // ── validarEnlaceVirtual ──

    @Test
    fun acepta_https_de_meet_zoom_y_teams_tal_cual() {
        assertEquals("https://meet.google.com/abc-defg-hij", ok("https://meet.google.com/abc-defg-hij"))
        assertEquals("https://us02web.zoom.us/j/8123456789?pwd=Xy12", ok("  https://us02web.zoom.us/j/8123456789?pwd=Xy12  "))
        assertIs<ResultadoEnlace.Ok>(validarEnlaceVirtual("https://teams.microsoft.com/l/meetup-join/19%3ameeting_abc"))
    }

    @Test
    fun minimo_tecleo_agrega_https_y_normaliza_el_esquema() {
        assertEquals("https://meet.google.com/abc-defg-hij", ok("meet.google.com/abc-defg-hij"))
        assertEquals("https://zoom.us/j/123", ok("zoom.us/j/123"))
        assertEquals("https://meet.google.com/x-y-z", ok("HTTPS://meet.google.com/x-y-z"))
    }

    @Test
    fun rechaza_http_javascript_data_y_otros_esquemas() {
        malo("http://meet.google.com/abc")
        malo("javascript:alert(1)")
        malo("JavaScript:alert(document.cookie)")
        malo("data:text/html,<script>alert(1)</script>")
        malo("ftp://x.com/a")
    }

    @Test
    fun rechaza_espacios_comillas_mayor_menor_y_barra_invertida() {
        malo("https://meet.google.com/abc def")
        malo("https://x.com/\"onmouseover=\"alert(1)")
        malo("https://x.com/'a")
        malo("https://x.com/<b>")
        malo("https://x.com/a\\b")
    }

    @Test
    fun rechaza_espacios_no_ascii_como_el_s_de_javascript() {
        // NBSP (pegado desde WhatsApp/Word), espacio fino, ideográfico, BOM, separador de línea.
        listOf(' ', ' ', ' ', '　', '﻿', ' ', '\u000B').forEach { ch ->
            malo("https://meet.google.com/abc${ch}def")
        }
        assertEquals("https://meet.google.com/abc-defg-hij", ok("https://meet.google.com/abc-defg-hij"))
    }

    @Test
    fun enlace_seguro_revalida_antes_de_abrir() {
        assertEquals("https://zoom.us/j/1", enlaceSeguro("zoom.us/j/1"))
        assertNull(enlaceSeguro("javascript:alert(1)"))
        assertNull(enlaceSeguro("https://meet.google.com/a b"))
        assertNull(enlaceSeguro(null))
    }

    @Test
    fun rechaza_vacio_no_texto_sin_dominio_con_usuario_y_largo() {
        malo("")
        malo(null)
        malo(42)
        malo("https://localhost/sala")
        malo("https://user:pass@meet.google.com/x")
        malo("https://x.com/" + "a".repeat(ENLACE_MAX))
    }

    @Test
    fun http_dice_por_que() {
        assertEquals(
            "El enlace tiene que empezar con https:// (conexión segura)",
            (validarEnlaceVirtual("http://meet.google.com/abc") as ResultadoEnlace.Error).error,
        )
    }

    // ── modalidad y proveedor ──

    @Test
    fun solo_virtual_es_videollamada() {
        assertTrue(esCitaVirtual("virtual"))
        assertFalse(esCitaVirtual("presencial"))
        assertFalse(esCitaVirtual(null))
    }

    @Test
    fun nombra_el_servicio_por_el_dominio() {
        assertEquals("Google Meet", proveedorDeEnlace("https://meet.google.com/a"))
        assertEquals("Zoom", proveedorDeEnlace("https://us02web.zoom.us/j/1"))
        assertEquals("Microsoft Teams", proveedorDeEnlace("https://teams.microsoft.com/l/x"))
        assertEquals("Videollamada", proveedorDeEnlace("https://sala.miclinica.pe/x"))
        assertEquals("Videollamada", proveedorDeEnlace("no es url"))
        assertEquals("Videollamada", proveedorDeEnlace(null))
    }

    @Test
    fun etiqueta_junto_a_unirse_proveedor_o_dominio() {
        assertEquals("Zoom", etiquetaEnlace("https://zoom.us/j/1"))
        assertEquals("sala.clinica.pe", etiquetaEnlace("https://www.sala.clinica.pe/x"))
        assertEquals("sala.clinica.pe", etiquetaEnlace("https://sala.clinica.pe:8443/x"))
        assertEquals("Videollamada", etiquetaEnlace("no es url"))
    }

    // ── plan → Meet automático o enlace manual ──

    private fun valor(r: ResultadoDecision) = (r as ResultadoDecision.Ok).valor

    @Test
    fun virtual_sin_enlace_premium_meet_basico_sin_enlace() {
        assertEquals(DecisionEnlace("virtual", null, "meet_auto", true), valor(resolverEnlaceCita(modalidad = "virtual", meetPermitido = true)))
        assertEquals(DecisionEnlace("virtual", null, null, false), valor(resolverEnlaceCita(modalidad = "virtual", meetPermitido = false)))
    }

    @Test
    fun meet_pedido_en_basico_queda_virtual_sin_enlace_y_avisa() {
        val v = valor(resolverEnlaceCita(modalidad = "virtual", meet = true, meetPermitido = false))
        assertEquals(DecisionEnlace("virtual", null, null, false, AVISO_MEET_PLAN), v)
    }

    @Test
    fun el_enlace_pegado_gana_siempre_y_se_valida() {
        assertEquals(
            DecisionEnlace("virtual", "https://zoom.us/j/99", "manual", false),
            valor(resolverEnlaceCita(modalidad = "virtual", enlace = "zoom.us/j/99", meet = true, meetPermitido = true)),
        )
        assertIs<ResultadoDecision.Error>(resolverEnlaceCita(modalidad = "virtual", enlace = "javascript:alert(1)", meetPermitido = true))
    }

    @Test
    fun servicio_con_enlace_fijo_la_cita_lo_hereda_en_cualquier_plan() {
        val servicio = ServicioModalidad("virtual", "fijo", "https://zoom.us/j/555")
        for (meetPermitido in listOf(true, false)) {
            assertEquals(
                DecisionEnlace("virtual", "https://zoom.us/j/555", "servicio", false),
                valor(resolverEnlaceCita(servicio = servicio, meetPermitido = meetPermitido)),
            )
        }
        // Pegar el MISMO enlace del servicio cuenta como del servicio.
        assertEquals("servicio", valor(resolverEnlaceCita(modalidad = "virtual", enlace = "https://zoom.us/j/555", servicio = servicio, meetPermitido = true)).enlaceOrigen)
    }

    @Test
    fun servicio_con_meet_en_basico_virtual_sin_enlace_y_aviso() {
        val v = valor(resolverEnlaceCita(servicio = ServicioModalidad("virtual", "meet_auto"), meetPermitido = false))
        assertEquals("virtual", v.modalidad)
        assertFalse(v.pedirMeet)
        assertEquals(AVISO_MEET_PLAN, v.aviso)
    }

    @Test
    fun presencial_a_mano_se_guarda_y_sin_pedir_nada_es_null() {
        val servicio = ServicioModalidad("virtual", "meet_auto")
        assertEquals(DecisionEnlace("presencial", null, null, false), valor(resolverEnlaceCita(modalidad = "presencial", servicio = servicio, meetPermitido = true)))
        assertEquals(DecisionEnlace(null, null, null, false), valor(resolverEnlaceCita(meetPermitido = true)))
    }

    @Test
    fun meet_false_explicito_es_sin_meet() {
        assertEquals(DecisionEnlace("virtual", null, null, false), valor(resolverEnlaceCita(modalidad = "virtual", meet = false, meetPermitido = true)))
    }

    @Test
    fun modalidad_desconocida_es_error() {
        assertIs<ResultadoDecision.Error>(resolverEnlaceCita(modalidad = "hibrida", meetPermitido = true))
    }

    // ── formulario de la cita ──

    @Test
    fun arranca_con_lo_que_dice_el_servicio() {
        assertEquals(VALOR_MODALIDAD_INICIAL, valorModalidadDeServicio(null, true))
        val meet = valorModalidadDeServicio(ServicioModalidad("virtual", "meet_auto"), true)
        assertEquals("virtual", meet.modalidad); assertEquals(ModoEnlace.MEET, meet.modo)
        assertEquals(ModoEnlace.ENLACE, valorModalidadDeServicio(ServicioModalidad("virtual", "meet_auto"), false).modo)
        assertEquals(
            ValorModalidad("virtual", ModoEnlace.ENLACE, "https://zoom.us/j/9", false),
            valorModalidadDeServicio(ServicioModalidad("virtual", "fijo", "https://zoom.us/j/9"), true),
        )
    }

    private fun campos(r: ResultadoCampos) = (r as ResultadoCampos.Ok).campos

    @Test
    fun presencial_de_siempre_no_escribe_nada_y_sobre_servicio_virtual_lo_dice() {
        assertEquals(CamposModalidad(), campos(camposModalidadCita(VALOR_MODALIDAD_INICIAL, null, true)))
        assertEquals(CamposModalidad(modalidad = "presencial"), campos(camposModalidadCita(VALOR_MODALIDAD_INICIAL, ServicioModalidad("virtual"), true)))
    }

    @Test
    fun virtual_meet_o_enlace_validado_vacio_se_pega_despues() {
        assertEquals(
            CamposModalidad("virtual", null, "meet_auto", true),
            campos(camposModalidadCita(ValorModalidad("virtual", ModoEnlace.MEET, "", true), null, true)),
        )
        assertEquals(
            CamposModalidad("virtual", "https://zoom.us/j/1", "manual", false),
            campos(camposModalidadCita(ValorModalidad("virtual", ModoEnlace.ENLACE, "zoom.us/j/1", true), null, true)),
        )
        assertEquals(
            CamposModalidad("virtual", null, null, false),
            campos(camposModalidadCita(ValorModalidad("virtual", ModoEnlace.ENLACE, "", true), null, true)),
        )
        assertIs<ResultadoCampos.Error>(camposModalidadCita(ValorModalidad("virtual", ModoEnlace.ENLACE, "http://x.com", true), null, true))
    }

    // ── lo que la app manda a /api/staff/cita/crear: manual vs automático ──

    private fun crear(v: ValorModalidad, s: ServicioModalidad?, meetPlan: Boolean): VirtualParaCrear? =
        virtualParaCrear(v, s, campos(camposModalidadCita(v, s, meetPlan)))

    @Test
    fun presencial_de_siempre_no_manda_nada() {
        assertNull(crear(VALOR_MODALIDAD_INICIAL, null, true))
    }

    @Test
    fun meet_automatico_solo_se_pide_si_el_plan_lo_tiene() {
        assertEquals(VirtualParaCrear("virtual", null, true), crear(ValorModalidad("virtual", ModoEnlace.MEET, "", true), null, true))
        // Básico: queda virtual sin enlace (el servidor avisa), nunca bloquea.
        assertEquals(VirtualParaCrear("virtual", null, false), crear(ValorModalidad("virtual", ModoEnlace.MEET, "", true), null, false))
    }

    @Test
    fun enlace_manual_viaja_normalizado_y_sin_meet() {
        assertEquals(VirtualParaCrear("virtual", "https://zoom.us/j/1", false), crear(ValorModalidad("virtual", ModoEnlace.ENLACE, "zoom.us/j/1", true), null, true))
        assertEquals(VirtualParaCrear("virtual", null, false), crear(ValorModalidad("virtual", ModoEnlace.ENLACE, "", true), null, true))
    }

    @Test
    fun servicio_con_enlace_fijo_sin_tocar_lo_hereda_la_base() {
        val s = ServicioModalidad("virtual", "fijo", "https://zoom.us/j/555")
        assertNull(crear(valorModalidadDeServicio(s, true), s, true))
        // Atendida en el local: se dice explícito para que no herede.
        assertEquals(VirtualParaCrear("presencial", null, null), crear(ValorModalidad("presencial", tocado = true), s, true))
    }

    // ── ventana del botón Unirse ──

    private val inicio = Instant.parse("2026-10-20T15:00:00Z").toEpochMilliseconds() // 10:00 Lima

    @Test
    fun ventana_se_abre_15_min_antes_y_cierra_60_despues_del_final() {
        assertTrue(ventanaUnirse(inicio, 45, inicio - 16 * 60_000L).antes)
        assertFalse(ventanaUnirse(inicio, 45, inicio - 16 * 60_000L).abierta)
        assertTrue(ventanaUnirse(inicio, 45, inicio - 15 * 60_000L).abierta)
        assertTrue(ventanaUnirse(inicio, 45, inicio + 105 * 60_000L).abierta)
        assertTrue(ventanaUnirse(inicio, 45, inicio + 106 * 60_000L).despues)
        assertEquals(inicio + 105 * 60_000L, ventanaUnirse(inicio, null, 0).hastaMs)
    }

    @Test
    fun portal_unirse_desde_15_min_antes() {
        val desde = "2026-10-20T14:45:00.000Z"
        val hasta = "2026-10-20T16:35:00.000Z"
        val e = "https://meet.google.com/aaa-bbbb-ccc"
        assertEquals(EstadoUnirsePaciente.ANTES, estadoUnirsePaciente(e, desde, hasta, inicio - 20 * 60_000L))
        assertEquals(EstadoUnirsePaciente.ABIERTA, estadoUnirsePaciente(e, desde, hasta, inicio - 10 * 60_000L))
        assertEquals(EstadoUnirsePaciente.CERRADA, estadoUnirsePaciente(e, desde, hasta, inicio + 200 * 60_000L))
        assertEquals(EstadoUnirsePaciente.SIN_ENLACE, estadoUnirsePaciente(null, desde, hasta, inicio))
        assertEquals("9:45 a. m.", horaUnirseDesde(desde, "America/Lima"))
    }

    // ── estado del Meet en la pantalla ──

    @Test
    fun estado_listo_creando_error_sin_enlace_y_presencial() {
        assertEquals(EstadoMeet.LISTO, estadoMeetCita(FilaVirtual("1", "virtual", enlaceVirtual = "https://meet.google.com/a")))
        assertEquals(EstadoMeet.CREANDO, estadoMeetCita(FilaVirtual("1", "virtual", enlaceOrigen = "meet_auto", meetPendiente = true)))
        assertEquals(EstadoMeet.ERROR, estadoMeetCita(FilaVirtual("1", "virtual", enlaceOrigen = "meet_auto", meetError = "x")))
        assertEquals(EstadoMeet.SIN_ENLACE, estadoMeetCita(FilaVirtual("1", "virtual")))
        assertNull(estadoMeetCita(FilaVirtual("1", "presencial", enlaceVirtual = "https://a.b")))
    }

    @Test
    fun unirse_en_la_agenda_solo_cita_viva_con_enlace() {
        assertTrue(puedeUnirseStaff("virtual", "https://meet.google.com/a", "Confirmada"))
        assertFalse(puedeUnirseStaff("virtual", "https://meet.google.com/a", "Completada"))
        assertFalse(puedeUnirseStaff("virtual", null, "Pendiente"))
        assertFalse(puedeUnirseStaff(null, "https://meet.google.com/a", "Pendiente"))
    }

    @Test
    fun linea_del_mensaje() {
        assertEquals("🎥 Enlace: https://meet.google.com/a", lineaEnlaceVirtual(" https://meet.google.com/a "))
        assertEquals("🎥 Cita por videollamada (le enviaremos el enlace)", lineaEnlaceVirtual("  "))
    }

    // ── respuestas del servidor ──

    private fun json(t: String) = Json.parseToJsonElement(t) as JsonObject

    @Test
    fun capacidad_y_fila_del_servidor() {
        val cap = parsearCapacidadMeet(json("""{"meet":{"permitido":true,"disponible":false,"motivo":"sin_permiso","origen":null}}"""))
        assertEquals(CapacidadMeet(true, false, "sin_permiso", null), cap)
        assertNull(parsearCapacidadMeet(json("""{"error":"x"}""")))
        val f = parsearFilaVirtual(json("""{"ok":true,"cita":{"id":"c1","modalidad":"virtual","enlace_virtual":null,"enlace_origen":"meet_auto","meet_pendiente":false,"meet_error":"Sin permiso: reconecta en Ajustes"}}"""))
        assertEquals(FilaVirtual("c1", "virtual", null, "meet_auto", false, "Sin permiso: reconecta en Ajustes"), f)
        assertTrue(errorPideReconectar(f?.meetError))
    }

    @Test
    fun aviso_tras_crear_el_meet() {
        assertEquals(TipoAvisoMeet.EXITO, avisoTrasMeet(parsearResultadoMeet(json("""{"meet":{"estado":"listo","enlace":"https://meet.google.com/a"}}""")))?.tipo)
        assertEquals(TipoAvisoMeet.INFO, avisoTrasMeet(parsearResultadoMeet(json("""{"meet":{"estado":"pendiente","enlace":null}}""")))?.tipo)
        assertEquals(AvisoMeet("Google dijo que no", TipoAvisoMeet.ERROR), avisoTrasMeet(parsearResultadoMeet(json("""{"meet":{"estado":"error","enlace":null,"mensaje":"Google dijo que no"}}"""))))
        assertEquals(FALLO_MEET, avisoTrasMeet(ResultadoMeet("error", null, null, null))?.texto)
        assertNull(avisoTrasMeet(parsearResultadoMeet(json("""{"ok":true,"citaId":"c1"}"""))))
        assertEquals(AVISO_MEET_PLAN, parsearResultadoMeet(json("""{"ok":true,"aviso":${JsonPrimitive(AVISO_MEET_PLAN)}}"""))?.aviso)
    }

    // ── servicio virtual (catálogo) ──

    @Test
    fun servicio_virtual_valida_el_enlace_fijo_y_presencial_lo_limpia() {
        val fijo = FormServicio(nombre = "Terapia", precio = "80", modalidad = "virtual", enlaceModo = "fijo", enlaceFijo = "zoom.us/j/1")
        assertNull(problemaFormServicio(fijo, emptyList(), false, false))
        assertEquals(Triple("virtual", "fijo", "https://zoom.us/j/1"), camposModalidadServicio(fijo, null))
        assertTrue(problemaFormServicio(fijo.copy(enlaceFijo = "javascript:alert(1)"), emptyList(), false, false)!!.startsWith("Enlace fijo:"))
        assertTrue(problemaFormServicio(fijo.copy(enlaceFijo = ""), emptyList(), false, false)!!.startsWith("Enlace fijo:"))
        // Presencial de siempre: el cuerpo no cambia. Virtual que pasa a presencial: se limpia.
        val presencial = fijo.copy(modalidad = "presencial")
        assertNull(camposModalidadServicio(presencial, null))
        assertEquals(Triple(null, null, null), camposModalidadServicio(presencial, "virtual"))
        val cuerpo = cuerpoGuardarServicio(presencial, emptyList(), "s1", null, false, false, modalidadInicial = null)
        assertFalse("modalidad" in (cuerpo["servicio"] as JsonObject))
        val virt = cuerpoGuardarServicio(fijo.copy(enlaceModo = "meet_auto"), emptyList(), null, null, false, false)
        assertEquals("meet_auto", ((virt["servicio"] as JsonObject)["enlace_modo"] as JsonPrimitive).content)
    }
}
