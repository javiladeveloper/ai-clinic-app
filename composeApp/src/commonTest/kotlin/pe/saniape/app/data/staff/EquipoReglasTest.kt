package pe.saniape.app.data.staff

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EquipoReglasTest {

    private fun miembro(
        esAdmin: Boolean = false,
        esYo: Boolean = false,
        atiende: Boolean = false,
        vinculado: Boolean = false,
        sedes: List<String>? = null,
    ) = MiembroEquipo(
        id = "m1", nombre = "Ana", rol = if (esAdmin) "Admin" else "Fisioterapeuta", rolId = if (esAdmin) null else "r1",
        esAdmin = esAdmin, esYo = esYo, atiendePacientes = atiende,
        terapeuta = if (vinculado) PersonalRef("t1", "Ana") else null, sedesPermitidas = sedes,
    )

    @Test
    fun sinSerAdminNoHayNingunaAccion() {
        val a = ReglasEquipo.acciones(miembro(atiende = true), soyAdmin = false)
        assertFalse(a.alguna)
    }

    @Test
    fun adminSobreUnMiembroComunVeTodo() {
        val a = ReglasEquipo.acciones(miembro(atiende = true), soyAdmin = true)
        assertTrue(a.editar && a.permisos && a.vincular && a.reenviar && a.revocar)
        assertFalse(a.desvincular)
    }

    @Test
    fun aOtroAdminNoSeLeRevocaNiAjustanPermisos() {
        val a = ReglasEquipo.acciones(miembro(esAdmin = true), soyAdmin = true)
        assertTrue(a.editar)
        assertFalse(a.permisos)
        assertFalse(a.reenviar)
        assertFalse(a.revocar)
        // Un Admin que atiende puede vincularse a su registro de personal.
        assertTrue(a.vincular)
    }

    @Test
    fun aUnoMismoNoSeLeRevoca() {
        val a = ReglasEquipo.acciones(miembro(esYo = true), soyAdmin = true)
        assertFalse(a.revocar)
        assertFalse(a.reenviar)
    }

    @Test
    fun vinculadoSeOfreceDesvincularYNoVincular() {
        val a = ReglasEquipo.acciones(miembro(atiende = true, vinculado = true), soyAdmin = true)
        assertTrue(a.desvincular)
        assertFalse(a.vincular)
        // Un rol que no atiende pacientes (recepción) no se vincula.
        assertFalse(ReglasEquipo.acciones(miembro(atiende = false), soyAdmin = true).vincular)
    }

    @Test
    fun emailComoLaWeb() {
        assertTrue(ReglasEquipo.emailValido("juan@clinica.pe"))
        assertTrue(ReglasEquipo.emailValido("  juan@clinica.com  "))
        assertFalse(ReglasEquipo.emailValido("juan@clinica.p"))
        assertFalse(ReglasEquipo.emailValido("juan clinica@x.com"))
        assertFalse(ReglasEquipo.emailValido("juan@"))
    }

    @Test
    fun resumenYCambioDeSedes() {
        val sedes = listOf(SedeEquipo("s1", "Centro"), SedeEquipo("s2", "Norte"))
        assertEquals("Todas las sedes", ReglasEquipo.resumenSedes(null, sedes))
        assertEquals("Centro, Norte", ReglasEquipo.resumenSedes(listOf("s1", "s2"), sedes))
        assertEquals("Sede desactivada", ReglasEquipo.resumenSedes(listOf("s9"), sedes))
        assertFalse(ReglasEquipo.cambiaronSedes(listOf("s1", "s2"), listOf("s2", "s1")))
        assertFalse(ReglasEquipo.cambiaronSedes(null, null))
        assertFalse(ReglasEquipo.cambiaronSedes(emptyList(), null))
        assertTrue(ReglasEquipo.cambiaronSedes(null, listOf("s1")))
        assertTrue(ReglasEquipo.cambiaronSedes(listOf("s1"), null))
        assertTrue(ReglasEquipo.cambiaronSedes(null, emptyList()))
    }

    @Test
    fun sedesSoloConMultisedeYSinRolAdmin() {
        assertTrue(ReglasEquipo.ofreceSedes(multiSede = true, soyAdmin = true, rolElegido = "r1"))
        assertFalse(ReglasEquipo.ofreceSedes(multiSede = true, soyAdmin = true, rolElegido = "Admin"))
        assertFalse(ReglasEquipo.ofreceSedes(multiSede = false, soyAdmin = true, rolElegido = "r1"))
        assertFalse(ReglasEquipo.ofreceSedes(multiSede = true, soyAdmin = false, rolElegido = "r1"))
    }

    @Test
    fun personalInvitableYVinculable() {
        val p = listOf(
            PersonalEquipo("a", "Activo sin cuenta"),
            PersonalEquipo("b", "Con cuenta", perfilId = "x"),
            PersonalEquipo("c", "Inactivo sin cuenta", estado = "Inactivo"),
        )
        assertEquals(listOf("a"), ReglasEquipo.personalInvitable(p).map { it.id })
        assertEquals(listOf("a", "c"), ReglasEquipo.personalVinculable(p).map { it.id })
    }

    @Test
    fun rolPorDefectoDelEnlaceEsElQueAtiende() {
        val roles = listOf(RolEquipo("r1", "Recepcionista"), RolEquipo("r2", "Fisioterapeuta", atiendePacientes = true))
        assertEquals("r2", ReglasEquipo.rolPorDefectoEnlace(roles))
        assertEquals("r1", ReglasEquipo.rolPorDefectoEnlace(roles.take(1)))
        assertNull(ReglasEquipo.rolPorDefectoEnlace(emptyList()))
    }

    @Test
    fun parseaLaRespuestaDelServidor() {
        val cuerpo = """
            {"esAdmin":true,"yoId":"u1","rolesPersonalizados":false,
             "permisosMeta":[{"clave":"pacientes","label":"Pacientes (lista completa)","descripcion":"..."}],
             "miembros":[{"id":"u1","nombre":"Dueña","rol":"Admin","rolId":null,"rolNombre":"Admin","esAdmin":true,"esYo":true,
                          "override":null,"permisos":{"pacientes":true},"atiendePacientes":false,"sedesPermitidas":null,"terapeuta":null},
                         {"id":"u2","nombre":"Ana","rol":"Fisioterapeuta","rolId":"r1","rolNombre":"Fisioterapeuta","esAdmin":false,"esYo":false,
                          "override":{"pagos":true},"permisos":{"pacientes":false},"atiendePacientes":true,"sedesPermitidas":["s1"],
                          "terapeuta":{"id":"t1","nombre":"Ana"},"campoNuevo":1}],
             "roles":[{"id":"r1","nombre":"Fisioterapeuta","descripcion":null,"atiendePacientes":true,"permisos":{"sesiones":true},"miembros":1}],
             "terapeutas":[{"id":"t1","nombre":"Ana","email":null,"perfilId":"u2","estado":"Activo"}],
             "sedes":[{"id":"s1","nombre":"Centro","estado":"Activa","esPrincipal":true}]}
        """.trimIndent()
        val d = assertNotNull(EquipoRepo.parsear(cuerpo))
        assertTrue(d.esAdmin)
        assertEquals(2, d.miembros.size)
        assertNull(d.miembros[0].override)
        assertNotNull(d.miembros[1].override)
        assertEquals("t1", d.miembros[1].terapeuta?.id)
        assertEquals(listOf("s1"), d.miembros[1].sedesPermitidas)
        assertEquals(true, d.roles[0].permisos["sesiones"])
        assertEquals("Pacientes (lista completa)", d.permisosMeta[0].label)
    }

    @Test
    fun cuerpoDePermisosNullEsRestablecer() {
        val nulo = EquipoRepo.cuerpoPermisos("p1", null)
        assertEquals(JsonNull, nulo["permisos"])
        val con = EquipoRepo.cuerpoPermisos("p1", mapOf("pagos" to true, "finanzas" to false))
        val p = con["permisos"] as JsonObject
        assertEquals(JsonPrimitive(true), p["pagos"])
        assertEquals(JsonPrimitive(false), p["finanzas"])
        assertEquals(JsonPrimitive("p1"), con["perfilId"])
    }

    @Test
    fun mensajeDeErrorDelServidor() {
        assertEquals("Debe quedar al menos un administrador.", EquipoRepo.mensajeDeError("""{"error":"Debe quedar al menos un administrador.","codigo":"ULTIMO_ADMIN"}"""))
        assertNull(EquipoRepo.mensajeDeError("<html>"))
    }

    @Test
    fun sinRolElegidoNoSeAsumeAdmin() {
        val legado = miembro().copy(rolId = null, rolNombre = "Recepcionista")
        assertEquals("Recepcionista", ReglasEquipo.rolEfectivo(legado, null))
        assertEquals("Admin", ReglasEquipo.rolEfectivo(miembro(esAdmin = true), null))
        assertEquals("r9", ReglasEquipo.rolEfectivo(legado, "r9"))
        // Sin elegir no hay "dejará de ser administrador".
        assertFalse(ReglasEquipo.dejaDeSerAdmin(miembro(esAdmin = true), null))
        assertTrue(ReglasEquipo.dejaDeSerAdmin(miembro(esAdmin = true), "r1"))
        // El PATCH sin rol no manda rolId (el servidor mantiene el actual).
        assertNull(EquipoRepo.cuerpoEditar("p1", "Ana", null, null)["rolId"])
        assertEquals(JsonPrimitive("r1"), EquipoRepo.cuerpoEditar("p1", "Ana", "r1", null)["rolId"])
    }
}
