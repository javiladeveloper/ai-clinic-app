package pe.saniape.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import pe.saniape.app.data.staff.CalendarioTraido
import pe.saniape.app.data.staff.StaffContextoRepo
import pe.saniape.app.data.staff.BOTON_REINTENTAR_PENDIENTES
import pe.saniape.app.data.staff.TEXTO_AJUSTA_Y_REINTENTA
import pe.saniape.app.data.staff.TEXTO_IMPORTADA_UNA_VEZ
import pe.saniape.app.data.staff.cuerpoGoogle
import pe.saniape.app.data.staff.ConfigFuenteCal
import pe.saniape.app.data.staff.DecisionesCal
import pe.saniape.app.data.staff.TEXTO_PLAN_UNA_IMPORTACION
import pe.saniape.app.data.staff.accionesFuente
import pe.saniape.app.data.staff.ajustesEditables
import pe.saniape.app.data.staff.estadoCalendarioDe
import pe.saniape.app.data.staff.puedeAgregarCalendarios
import pe.saniape.app.data.staff.puedeImportar
import pe.saniape.app.data.staff.textoBotonImportar
import pe.saniape.app.data.staff.textoEstadoFuente
import pe.saniape.app.data.staff.textoPie
import pe.saniape.app.data.staff.textoPorRevisarResultado
import pe.saniape.app.data.staff.textoSinCupoResultado

/**
 * Agenda de Google Calendar según el plan (lógica pura de la pantalla): Básico =
 * UNA importación; Premium/Plus = sincronización continua. Gemelo de
 * lib/calendario/limite-plan.ts + textos.ts de la web.
 */
class CalendarioPlanTest {
    private fun obj(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject

    private val importada = CalendarioTraido(
        id = "f1", nombre = "Agenda", profesional = null, esIcs = false, importadoEn = "2026-10-09T10:00:00Z",
        ultimaSync = null, error = null, citas = 3, sinCupo = 0,
    )

    @Test fun featureDelPlanEnElContexto() {
        val basico = StaffContextoRepo.parsear(obj("""{"clinicaId":"c","rol":"Admin","permisos":{},
            "planEstado":{"efectivo":"Basico","vencido":false,"features":{"calendarioSync":false}}}"""))
        assertFalse(basico.can("calendarioSync"))
        val premium = StaffContextoRepo.parsear(obj("""{"clinicaId":"c","rol":"Admin","permisos":{},
            "planEstado":{"efectivo":"Premium","vencido":false,"features":{"calendarioSync":true}}}"""))
        assertTrue(premium.can("calendarioSync"))
        // Backend viejo (sin el campo): como siempre; el servidor decide.
        val viejo = StaffContextoRepo.parsear(obj("""{"clinicaId":"c","rol":"Admin","permisos":{},
            "planEstado":{"efectivo":"Basico","vencido":false,"features":{}}}"""))
        assertTrue(viejo.can("calendarioSync"))
    }

    @Test fun leeElPlanDelEstado() {
        val e = estadoCalendarioDe(obj("""{"googleConfigurado":true,"llaveConfigurada":true,"conexiones":[],
            "fuentes":[{"id":"f1","nombre":"Agenda","proveedor":"google","importacion_inicial_at":"2026-10-09T10:00:00Z",
                        "importacion_estado":"lista","citas":3,"puedeImportar":false}],
            "plan":{"sincronizacion":false,"importacionUsada":true}}"""))
        assertFalse(e.sincronizacion)
        assertTrue(e.importacionUsada)
        assertFalse(e.calendarios[0].puedeImportar)
        assertFalse(puedeAgregarCalendarios(e))
        // Sin `plan` (servidor viejo): sin candados.
        val viejo = estadoCalendarioDe(obj("""{"googleConfigurado":true,"llaveConfigurada":true,"fuentes":[{"id":"f1"}]}"""))
        assertTrue(viejo.sincronizacion)
        assertFalse(viejo.importacionUsada)
        assertTrue(viejo.calendarios[0].puedeImportar)
        assertTrue(puedeAgregarCalendarios(viejo))
    }

    @Test fun fuenteImportadaEnBasico() {
        assertEquals("$TEXTO_IMPORTADA_UNA_VEZ · 3 citas importadas", textoEstadoFuente(importada, "hace 5 min", sincroniza = false))
        assertEquals(
            "$TEXTO_IMPORTADA_UNA_VEZ · 3 citas importadas · 2 sin cupo ($TEXTO_AJUSTA_Y_REINTENTA)",
            textoEstadoFuente(importada.copy(sinCupo = 2), "hace 5 min", sincroniza = false),
        )
        assertEquals("Sincronizado hace 5 min · 3 citas importadas", textoEstadoFuente(importada, "hace 5 min"))
        // Básico, sin poder volver a importar: ni "Revisar de nuevo" ni "Sincronizar ahora".
        val a = accionesFuente(importada.copy(puedeImportar = false), sincroniza = false)
        assertNull(a.revisar); assertFalse(a.sincronizar); assertNull(a.candado)
        // Premium: los dos botones.
        val p = accionesFuente(importada, sincroniza = true)
        assertEquals("Revisar de nuevo", p.revisar); assertTrue(p.sincronizar)
    }

    @Test fun otraFuenteTrasUsarLaImportacion() {
        val pendiente = importada.copy(id = "f2", importadoEn = null, puedeImportar = false)
        val a = accionesFuente(pendiente, sincroniza = false)
        assertNull(a.revisar)
        assertEquals("🔒 Tu plan incluye una importación y ya la usaste.", a.candado)
        // Su primera importación (o continuarla): sí.
        val primera = accionesFuente(pendiente.copy(puedeImportar = true), sincroniza = false)
        assertEquals("Revisar e importar", primera.revisar)
        assertFalse(primera.sincronizar)
        assertEquals("Leyendo calendario…", accionesFuente(pendiente.copy(puedeImportar = true), false, leyendo = true).revisar)
        assertNull(accionesFuente(importada.copy(esIcs = true), true).revisar)
    }

    @Test fun vistaPreviaEnBasico() {
        assertEquals("Se importarán 4 citas. Es una importación única. $TEXTO_PLAN_UNA_IMPORTACION", textoPie(4, 0, sincroniza = false))
        assertTrue(textoPie(4, 0).endsWith("llegan solos cada 10 minutos."))
        // Con 0 citas no se gasta la única importación en nada.
        assertEquals("Nada para importar", textoBotonImportar(0, esGoogle = true, importando = false, sincroniza = false))
        assertFalse(puedeImportar(0, esGoogle = true, trabajando = false, sincroniza = false))
        assertTrue(puedeImportar(3, esGoogle = true, trabajando = false, sincroniza = false))
        assertEquals("Después $TEXTO_AJUSTA_Y_REINTENTA.", textoSinCupoResultado(sincroniza = false))
        assertFalse(textoPorRevisarResultado(2, sincroniza = false).contains("Revisar de nuevo"))
        assertTrue(textoPorRevisarResultado(2, sincroniza = false).contains(BOTON_REINTENTAR_PENDIENTES))
        assertTrue(textoPorRevisarResultado(2).contains("Revisar de nuevo"))
    }

    @Test fun reintentarLasPendientesEnBasico() {
        // Ya importada con algo sin cupo o por revisar: botón de reintento (Google y .ics).
        assertTrue(accionesFuente(importada.copy(sinCupo = 2, puedeImportar = false), sincroniza = false).reintentar)
        assertTrue(accionesFuente(importada.copy(porRevisar = 1, esIcs = true), sincroniza = false).reintentar)
        assertFalse(accionesFuente(importada, sincroniza = false).reintentar)                        // nada pendiente
        assertFalse(accionesFuente(importada.copy(sinCupo = 2), sincroniza = true).reintentar)       // Premium: se reintenta solo
        assertFalse(accionesFuente(importada.copy(sinCupo = 2, enCurso = true), sincroniza = false).reintentar)
        // .ics en Básico: nunca "vuelve a subir el archivo".
        assertFalse(textoEstadoFuente(importada.copy(esIcs = true, sinCupo = 1), "hace 1 h", sincroniza = false).contains("vuelve a subir"))
        // La vista previa del reintento.
        assertEquals("Reintentar 2 citas", textoBotonImportar(2, true, false, sincroniza = false, reintento = true))
        assertEquals("Nada para reintentar", textoBotonImportar(0, true, false, sincroniza = true, reintento = true))
        assertFalse(puedeImportar(0, esGoogle = true, trabajando = false, sincroniza = true, reintento = true))
        assertTrue(textoPie(2, 0, sincroniza = false, reintento = true).contains("no se trae nada nuevo"))
        // El cuerpo lleva soloPendientes solo en el reintento.
        assertEquals("true", cuerpoGoogle("f1", ConfigFuenteCal(), DecisionesCal(), soloPendientes = true)["soloPendientes"].toString())
        assertNull(cuerpoGoogle("f1", ConfigFuenteCal(), DecisionesCal())["soloPendientes"])
        // En el reintento los ajustes no se editan: lo que se ve es lo que se aplica.
        assertFalse(ajustesEditables(soloPendientes = true))
        assertTrue(ajustesEditables(soloPendientes = false))
    }
}
