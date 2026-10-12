package pe.saniape.app.ui.clinica.atencion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import pe.saniape.app.data.staff.AtencionRepo
import pe.saniape.app.data.staff.SugerenciaMedicamento
import pe.saniape.app.data.staff.TotalPetitorio

/**
 * El banco de medicamentos que se abre al enfocar el DCI vacío (gemelo de
 * sugerenciasIniciales / perfilPrescriptor de lib/recetas-acciones.ts; contrato §12).
 */
class BancoMedicamentosTest {

    private fun clinica(dci: String, conc: String = "", forma: String = "", usos: Int = 3) =
        SugerenciaMedicamento(dci, conc, forma, usos = usos, origen = "clinica")

    private fun pnume(dci: String, conc: String = "", forma: String = "") =
        SugerenciaMedicamento(dci, conc, forma, usos = 0, origen = "petitorio")

    // ── Perfil del prescriptor ──

    @Test
    fun perfilSegunLaProfesion() {
        assertEquals(PERFIL_ODONTOLOGIA, perfilPrescriptor(esDentista = true, noMedico = false))
        assertEquals(PERFIL_MEDICINA, perfilPrescriptor(esDentista = false, noMedico = false))
        assertEquals(PERFIL_PSIQUIATRIA, perfilPrescriptor(esDentista = false, noMedico = false, psiquiatra = true))
        // Quien no prescribe (fisio, estética) no tiene frecuentes: solo lo de su clínica.
        assertNull(perfilPrescriptor(esDentista = false, noMedico = true, psiquiatra = true))
        // El dentista manda aunque se marque psiquiatría (no pasa, pero el orden es el de la web).
        assertEquals(PERFIL_ODONTOLOGIA, perfilPrescriptor(esDentista = true, noMedico = false, psiquiatra = true))
    }

    @Test
    fun psiquiatriaCaeAMedicinaSiElServidorAunNoLaTiene() {
        val viejo = setOf(PERFIL_ODONTOLOGIA, PERFIL_MEDICINA)
        assertEquals(PERFIL_MEDICINA, perfilDisponible(PERFIL_PSIQUIATRIA, viejo))
        assertEquals(PERFIL_PSIQUIATRIA, perfilDisponible(PERFIL_PSIQUIATRIA, viejo + PERFIL_PSIQUIATRIA))
        assertEquals(PERFIL_ODONTOLOGIA, perfilDisponible(PERFIL_ODONTOLOGIA, viejo))
        assertNull(perfilDisponible(null, viejo))
        // Servidor sin frecuentes: el perfil queda, pero no trae nada (solo lo de la clínica).
        assertEquals(PERFIL_MEDICINA, perfilDisponible(PERFIL_MEDICINA, emptySet()))
    }

    @Test
    fun tituloDelGrupoPorPerfil() {
        assertEquals("Frecuentes en odontología · Petitorio MINSA", tituloPerfil(PERFIL_ODONTOLOGIA))
        assertEquals("Frecuentes en medicina general · Petitorio MINSA", tituloPerfil(PERFIL_MEDICINA))
        assertEquals("Frecuentes en psiquiatría · Petitorio MINSA", tituloPerfil(PERFIL_PSIQUIATRIA))
        // Un perfil nuevo del servidor se muestra igual, con su nombre.
        assertEquals("Frecuentes en ginecologia · Petitorio MINSA", tituloPerfil("ginecologia"))
    }

    // ── Grupos iniciales ──

    @Test
    fun cincoDeLaClinicaYLosDelPerfilSinRepetir() {
        val lista = listOf(
            clinica("Paracetamol", "500 mg", "Tableta", usos = 12),
            clinica("Ibuprofeno", "400 mg", "Tableta", usos = 9),
            clinica("Amoxicilina", "500 mg", "Cápsula", usos = 7),
            clinica("Loratadina", "10 mg", "Tableta", usos = 5),
            clinica("Omeprazol", "20 mg", "Cápsula", usos = 4),
            clinica("Clindamicina", "300 mg", "Cápsula", usos = 2),   // 6.ª: no entra arriba
            pnume("Metamizol", "1 g", "Inyectable"),
        )
        val porPerfil = mapOf(
            PERFIL_MEDICINA to listOf(
                pnume("Paracetamol", "500mg", "Tableta"),        // ya está arriba (sin espacios = misma clave)
                pnume("Clindamicina", "300 mg", "Capsula"),      // la clínica la aprendió (sin tilde = misma)
                pnume("Salbutamol", "100 mcg", "Inhalador"),
            ),
        )
        val g = sugerenciasIniciales(lista, porPerfil, PERFIL_MEDICINA)
        assertEquals(listOf("Paracetamol", "Ibuprofeno", "Amoxicilina", "Loratadina", "Omeprazol"), g.clinica.map { it.dci })
        assertEquals(listOf("Clindamicina", "Salbutamol"), g.perfil.map { it.dci })
        // La aprendida gana (con sus usos y su origen).
        assertEquals("clinica", g.perfil[0].origen)
        assertEquals(2, g.perfil[0].usos)
        assertEquals("petitorio", g.perfil[1].origen)
        assertEquals(7, g.todas.size)
    }

    @Test
    fun sinPerfilOServidorViejoSoloLoDeLaClinica() {
        val lista = listOf(clinica("Paracetamol"), pnume("Amoxicilina"))
        val porPerfil = mapOf(PERFIL_MEDICINA to listOf(pnume("Salbutamol")))
        assertEquals(listOf("Paracetamol"), sugerenciasIniciales(lista, porPerfil, null).todas.map { it.dci })
        assertEquals(listOf("Paracetamol"), sugerenciasIniciales(lista, emptyMap(), PERFIL_MEDICINA).todas.map { it.dci })
        // Perfil que el servidor no manda: tampoco inventa nada.
        assertEquals(listOf("Paracetamol"), sugerenciasIniciales(lista, porPerfil, PERFIL_ODONTOLOGIA).todas.map { it.dci })
    }

    // ── Pie de la lista ──

    @Test
    fun pieConYSinTotal() {
        assertEquals(
            "Escribe para buscar en el Petitorio del MINSA (158 medicamentos, 252 presentaciones).",
            pieListaMedicamentos(vacio = true, sinCoincidencias = false, total = TotalPetitorio(158, 252)),
        )
        assertEquals(
            "Escribe para buscar en el Petitorio del MINSA.",
            pieListaMedicamentos(vacio = true, sinCoincidencias = false, total = null),
        )
        assertEquals(
            "No está en el petitorio: escribe la DCI completa y queda guardada para la próxima.",
            pieListaMedicamentos(vacio = false, sinCoincidencias = true, total = TotalPetitorio(158, 252)),
        )
    }

    @Test
    fun sinCoincidenciasDesdeDosLetras() {
        assertFalse(sinCoincidenciasMedicamento("x", emptyList()))
        assertTrue(sinCoincidenciasMedicamento("xz", emptyList()))
        assertFalse(sinCoincidenciasMedicamento("pa", listOf(clinica("Paracetamol"))))
    }

    // ── Parseo (§12) ──

    @Test
    fun leeLosCamposNuevosConCualquierPerfil() {
        val b = AtencionRepo.parsearBanco("""{
          "sugerencias": [
            { "dci": "Paracetamol", "concentracion": "500 mg", "forma": "Tableta", "via": "Oral", "usos": 12, "origen": "clinica" },
            { "dci": "Amoxicilina", "concentracion": "500 mg", "forma": "Cápsula", "usos": 0, "origen": "petitorio" }
          ],
          "frecuentesPorPerfil": {
            "odontologia": [{ "dci": "Clindamicina", "concentracion": "300mg", "forma": "Tableta" }],
            "medicina": [{ "dci": "Loratadina", "concentracion": "10mg", "forma": "Tableta" }],
            "psiquiatria": [{ "dci": "Sertralina", "concentracion": "50mg", "forma": "Tableta" }, { "nada": 1 }],
            "raro": "no es una lista"
          },
          "totalPetitorio": { "presentaciones": 252, "medicamentos": 158 }
        }""")
        assertEquals(2, b.sugerencias.size)
        assertEquals(setOf("odontologia", "medicina", "psiquiatria"), b.frecuentesPorPerfil.keys)
        assertEquals(listOf("Sertralina"), b.frecuentesPorPerfil.getValue("psiquiatria").map { it.dci })
        assertEquals(TotalPetitorio(158, 252), b.totalPetitorio)
    }

    @Test
    fun servidorViejoSinCamposNuevos() {
        val b = AtencionRepo.parsearBanco("""{ "sugerencias": [ { "dci": "Paracetamol", "usos": 3, "origen": "clinica" } ] }""")
        assertEquals(1, b.sugerencias.size)
        assertTrue(b.frecuentesPorPerfil.isEmpty())
        assertNull(b.totalPetitorio)
        assertTrue(AtencionRepo.parsearBanco("no es json").sugerencias.isEmpty())
    }
}
