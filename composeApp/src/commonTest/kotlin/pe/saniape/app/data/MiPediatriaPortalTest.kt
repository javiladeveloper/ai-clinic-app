package pe.saniape.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import pe.saniape.app.data.staff.EstadoDosis
import pe.saniape.app.data.staff.IndicadorCrecimiento
import pe.saniape.app.data.staff.NivelCrecimiento
import pe.saniape.app.data.staff.ventanaCurva

/**
 * 👶 / 💉 del portal: el parseo de /api/paciente/mi-crecimiento y /mis-vacunas
 * (contrato en la web: docs/app-contrato-portal-paciente.md §11 y §12) y el
 * filtro `enNutricion` / `mostrarEvolucion` de §4 en ⚖️ Mi evolución.
 */
class MiPediatriaPortalTest {

    private val crecimiento = """{"ninos":[{
        "pacienteId":"p1","nombre":"Mateo Quispe","sexo":"M","fechaNacimiento":"2025-03-10",
        "clinica":{"nombre":"BABYMED","logo_url":null,"color_principal":"#2c3e7a"},
        "mediciones":[
          {"fecha":"2026-03-10","edadDias":365,"peso":9.6,"talla":"75.7","perimetroCefalico":46.1,"imc":16.8},
          {"fecha":"2025-09-10","edadDias":184,"peso":7.9,"talla":67.6,"perimetroCefalico":null,"imc":17.3},
          {"fecha":"2025-05-10","edadDias":61,"peso":null,"talla":null,"perimetroCefalico":null,"imc":null}
        ],
        "ultima":{"fecha":"2026-03-10","edadTexto":"12 meses","indicadores":[
          {"indicador":"peso_edad","sigla":"P/E","nombre":"Peso para la edad","valor":9.6,"unidad":"kg","z":0.01,"percentil":50.4,"clasificacion":{"texto":"Normal","nivel":"ok"}},
          {"indicador":"imc_edad","sigla":"IMC/E","nombre":"IMC para la edad","valor":16.8,"unidad":"kg/m²","z":-0.1,"percentil":46,"clasificacion":null},
          {"indicador":"raro","valor":1,"z":0}
        ]}
      },
      {"pacienteId":"p2","nombre":"Sin fecha","fechaNacimiento":null,"mediciones":[{"fecha":"2026-01-01","peso":5}]}
    ]}"""

    @Test fun crecimientoTolerante() {
        val d = parsearMiCrecimiento(crecimiento)!!
        assertEquals(listOf("p1"), d.ninos.map { it.pacienteId })
        val n = d.ninos[0]
        // Cronológicas; la medición vacía se descarta.
        assertEquals(listOf("2025-09-10", "2026-03-10"), n.mediciones.map { it.fecha })
        assertEquals(75.7, n.mediciones[1].talla)
        assertEquals("BABYMED", n.clinica?.nombre)
        assertEquals(2, n.ultima!!.indicadores.size)
        assertEquals(NivelCrecimiento.OK, n.ultima!!.indicadores[0].clasificacion?.nivel)
        assertNull(n.ultima!!.indicadores[1].clasificacion)
        // Curvas: solo los indicadores de la última medición con ventana a esa edad.
        assertEquals(listOf(IndicadorCrecimiento.PESO_EDAD, IndicadorCrecimiento.IMC_EDAD), indicadoresCurvaPortal(n))
        val pts = puntosPortal(n, IndicadorCrecimiento.PESO_EDAD, ventanaCurva(IndicadorCrecimiento.PESO_EDAD, 12.0)!!)
        assertEquals(listOf(7.9, 9.6), pts.map { it.valor })
        assertNull(parsearMiCrecimiento("no es json"))
        assertTrue(!parsearMiCrecimiento("""{"ninos":[]}""")!!.tieneDatos)
    }

    /** §11.1: 30 sem al nacer; la de 20 días es antes del término; la de 120 días, 50 corregidos. */
    @Test fun prematuroEdadCorregidaEnLaCurva() {
        val d = parsearMiCrecimiento("""{"ninos":[{
            "pacienteId":"p1","nombre":"Mateo","sexo":"M","fechaNacimiento":"2026-01-01",
            "semanasAlNacer":30,"diasAlNacer":0,"prematuro":true,
            "mediciones":[
              {"fecha":"2026-01-21","edadDias":20,"peso":1.9,"edadCorregidaDias":null,"usaEdadCorregida":false,"antesDelTermino":true},
              {"fecha":"2026-05-01","edadDias":120,"peso":4.9,"talla":55,"edadCorregidaDias":50,"usaEdadCorregida":true,"antesDelTermino":false}
            ],
            "ultima":{"fecha":"2026-05-01","edadTexto":"4 meses","edadCorregidaTexto":"1 mes","edadCorregidaMeses":1.6,
              "usaEdadCorregida":true,"antesDelTermino":false,"edadPostmenstrualTexto":null,
              "indicadores":[{"indicador":"peso_edad","valor":4.9,"z":0.01,"percentil":50,"clasificacion":{"texto":"Normal","nivel":"ok"}}]}
        }]}""")!!
        val n = d.ninos.single()
        assertEquals(30, n.semanasAlNacer); assertEquals(0, n.diasAlNacer); assertTrue(n.prematuro)
        assertEquals("1 mes", n.ultima!!.edadCorregidaTexto)
        assertEquals(1.6, n.ultima!!.edadCorregidaMeses)
        // La ventana se elige con la edad corregida; el punto se ubica en 50 días, no en 120.
        assertEquals(50 / pe.saniape.app.data.staff.DIAS_POR_MES, mesesCurvaPortal(n))
        val v = ventanaCurva(IndicadorCrecimiento.PESO_EDAD, mesesCurvaPortal(n)!!)!!
        val pts = puntosPortal(n, IndicadorCrecimiento.PESO_EDAD, v)
        assertEquals(listOf(50 / pe.saniape.app.data.staff.DIAS_POR_MES), pts.map { it.x })
        // P/T (eje talla): tampoco se dibuja la de antes del término.
        val vpt = ventanaCurva(IndicadorCrecimiento.PESO_TALLA, 1.6)!!
        assertEquals(listOf("2026-05-01"), puntosPortal(n, IndicadorCrecimiento.PESO_TALLA, vpt).map { it.fecha })

        // Última medición antes del término: sin z (aunque llegara alguno) ni curva.
        val antes = parsearMiCrecimiento("""{"ninos":[{
            "pacienteId":"p1","nombre":"Mateo","sexo":"M","fechaNacimiento":"2026-01-01","semanasAlNacer":30,"prematuro":true,
            "mediciones":[{"fecha":"2026-01-21","edadDias":20,"peso":1.9,"antesDelTermino":true}],
            "ultima":{"fecha":"2026-01-21","edadTexto":"20 días","antesDelTermino":true,"edadPostmenstrualTexto":"32+6 sem",
              "indicadores":[{"indicador":"peso_edad","valor":1.9,"z":-5,"percentil":0}]}
        }]}""")!!.ninos.single()
        assertTrue(antes.ultima!!.antesDelTermino)
        assertEquals("32+6 sem", antes.ultima!!.edadPostmenstrualTexto)
        assertTrue(antes.ultima!!.indicadores.isEmpty())
        assertNull(mesesCurvaPortal(antes))
        assertTrue(indicadoresCurvaPortal(antes).isEmpty())

        // Servidor viejo (sin los campos): la edad cronológica, como antes.
        val viejo = parsearMiCrecimiento(crecimiento)!!.ninos.single()
        assertNull(viejo.semanasAlNacer); assertTrue(!viejo.prematuro)
        assertTrue(viejo.mediciones.none { it.usaEdadCorregida || it.antesDelTermino })
        assertEquals(365 / pe.saniape.app.data.staff.DIAS_POR_MES, mesesCurvaPortal(viejo))
    }

    @Test fun vacunasYaCalculadas() {
        val d = parsearMisVacunas("""{"ninos":[{
            "pacienteId":"p1","nombre":"Mateo Quispe","fechaNacimiento":"2025-03-10","clinica":{"nombre":"BABYMED"},
            "resumen":{"texto":"1 atrasada · 2 le tocan","aplicadas":9,"total":24,"atrasadas":1,"pendientes":2,"alDia":false},
            "dosis":[
              {"id":"penta-1","grupo":"2 meses","vacuna":"Pentavalente / Hexavalente","dosis":"1.ª","estado":"aplicada","estadoTexto":"Aplicada",
               "fechaRecomendada":"2025-05-10","fechaAplicacion":"2025-05-12","lote":"L2231","lugar":"En la clínica"},
              {"id":"spr-1","grupo":"12 meses","vacuna":"SPR","dosis":"1.ª","estado":"atrasada","estadoTexto":"Atrasada",
               "fechaRecomendada":"2026-03-10","fechaAplicacion":null,"lote":null,"lugar":null},
              {"id":"x","grupo":"12 meses","vacuna":"X","dosis":"1.ª","estado":"inventado"}
            ],
            "otras":[{"vacuna":"Meningococo B","dosis":"1.ª","fechaAplicacion":"2025-09-01","lote":null,"lugar":"Clínica X"}]
        }]}""")!!
        val n = d.ninos.single()
        assertEquals(2, n.dosis.size)
        assertEquals(EstadoDosis.ATRASADA, n.dosis[1].estado)
        assertEquals(listOf("spr-1"), n.ahora.map { it.id })
        assertEquals(listOf("2 meses", "12 meses"), n.grupos)
        assertEquals("1 atrasada · 2 le tocan", n.resumen.texto)
        assertEquals(24, n.resumen.total)
        assertEquals("Clínica X", n.otras.single().lugar)
    }

    @Test fun nutricionEsconeLasMedicionesPediatricas() {
        val conNino = parsearMiNutricion("""{"planes":[],"mostrarEvolucion":false,"mediciones":[
            {"id":"m1","fecha":"2026-09-01","peso":9.6,"talla":75,"enNutricion":false}]}""")!!
        assertTrue(!conNino.tieneDatos)
        val mixta = parsearMiNutricion("""{"planes":[],"mostrarEvolucion":true,"mediciones":[
            {"id":"m1","fecha":"2026-09-01","peso":9.6,"enNutricion":false},
            {"id":"m2","fecha":"2026-09-02","peso":72.5,"enNutricion":true}]}""")!!
        assertEquals(listOf("m2"), mixta.mediciones.map { it.medicion.id })
        // Servidor viejo (sin los campos): todo se muestra, como antes.
        val viejo = parsearMiNutricion("""{"planes":[],"mediciones":[{"id":"m1","fecha":"2026-09-01","peso":72.5}]}""")!!
        assertEquals(1, viejo.mediciones.size)
    }
}
