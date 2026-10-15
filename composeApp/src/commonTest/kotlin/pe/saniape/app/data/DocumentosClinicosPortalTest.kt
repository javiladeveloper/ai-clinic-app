package pe.saniape.app.data

import pe.saniape.app.data.staff.DOC_DESCANSO
import pe.saniape.app.data.staff.DOC_INFORME
import pe.saniape.app.data.staff.DOC_ORDEN
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** /api/paciente/mis-documentos-clinicos (contrato del portal §5 de la web). */
class DocumentosClinicosPortalTest {

    private val completo = """
    {
      "informes": [{
        "id": "11111111-1111-1111-1111-111111111111", "tipo": "informe", "fecha": "2026-10-09", "numeroTexto": "IM N° 000012",
        "titulo": "Informe psiquiátrico", "detalle": "Dirigido a: Colegio San José",
        "clinica": { "nombre": "Clínica Andina", "logo_url": "https://x/logo.png", "color_principal": null },
        "profesional": { "nombre": "Dra. Rosa Quispe", "profesion": "Médico Psiquiatra" },
        "diagnosticos": [{ "codigo": "F51.0", "descripcion": "Insomnio no orgánico" }],
        "diagnosticoReservado": false, "descanso": null, "examenes": [], "indicaciones": null,
        "ruta": "/portal/documento/informe/11111111-1111-1111-1111-111111111111",
        "urlHtml": "/api/paciente/mis-documentos-clinicos/informe/11111111-1111-1111-1111-111111111111"
      }],
      "descansos": [{
        "id": "d1", "tipo": "descanso", "fecha": "2026-10-09", "numeroTexto": "DM N° 000003",
        "titulo": "Certificado de descanso médico", "detalle": "3 días de descanso",
        "clinica": null, "profesional": null, "diagnosticos": [], "diagnosticoReservado": true,
        "descanso": { "desde": "2026-10-09", "hasta": "2026-10-11", "dias": "3" },
        "urlHtml": "https://evil.example/robar"
      }],
      "ordenes": [{
        "id": "o1", "tipo": "orden_examenes", "fecha": "2026-10-09", "numeroTexto": "OE N° 000007",
        "diagnosticos": [{ "codigo": "E03.9", "descripcion": "Hipotiroidismo" }], "diagnosticoReservado": false,
        "examenes": [{ "nombre": "Hemograma completo", "indicacion": "En ayunas" }, { "nombre": "TSH", "indicacion": null }, "Glucosa"],
        "indicaciones": "Traer los resultados al control"
      }],
      "presupuestos": [
        { "id": "p2", "fecha": "2026-10-02T15:00:00.000Z", "titulo": "Presupuesto dental", "detalle": "2 tratamientos",
          "estado": "Enviado", "puedeAceptar": true, "total": "320.50", "moneda": "pen",
          "vence": "2026-11-01T15:00:00.000Z", "aceptadoAt": null,
          "items": [{ "nombre": "Resina", "detalle": "16, 26", "fase": 1, "precio": "240" }, { "nombre": "Profilaxis", "fase": null, "precio": 80 }],
          "clinica": { "nombre": "Dental Sur" }, "profesional": null, "enlace": "/presupuesto/abc123" },
        { "id": "p1", "fecha": "2026-09-01T03:00:00.000Z", "titulo": "Presupuesto dental", "estado": "Reemplazado",
          "puedeAceptar": false, "total": 500, "moneda": "BOB", "items": [], "enlace": null },
        { "id": "p0", "fecha": "2026-08-01", "estado": "Enviado", "puedeAceptar": true, "total": null, "items": null, "enlace": null }
      ],
      "cuotas": [{
        "id": "t1", "tratamientoId": "t1", "fecha": "2026-09-01", "titulo": "Cuotas de Ortodoncia",
        "clinica": { "nombre": "Dental Sur" }, "profesional": "Dr. Luis Mamani", "moneda": "BOB", "hoy": "2026-10-15",
        "cuotas": [
          { "numero": 1, "monto": "100", "vence": "2026-10-01", "estado": "atrasada", "pagado": "50", "saldo": "50",
            "diasAtraso": 14, "etiqueta": "1 de 3", "estadoTexto": "Atrasada 14 días · parcial" },
          { "numero": 0, "monto": 300, "vence": "2026-09-01", "estado": "pagada", "pagado": 300, "saldo": 0, "etiqueta": "Inicial", "estadoTexto": "Pagada" },
          { "numero": 2, "monto": 100, "vence": "2026-10-15", "estado": "vence_hoy", "pagado": 0, "saldo": 100 },
          { "numero": 3, "monto": 100, "vence": "2026-11-15", "estado": "programada", "pagado": null, "saldo": null }
        ],
        "resumen": { "totalPlan": "600", "pagado": 350, "saldo": "250", "pagadas": 1, "nCuotas": 3,
          "atrasadas": 1, "montoAtrasado": 50, "alDia": false, "proxima": null }
      }]
    }
    """.trimIndent()

    @Test fun parseaLasCincoListas() {
        val d = assertNotNull(parsearDocumentosClinicos(completo))
        assertTrue(d.tieneDatos)
        assertEquals(1, d.informes.size)
        assertEquals(1, d.descansos.size)
        assertEquals(1, d.ordenes.size)
        assertEquals(3, d.presupuestos.size)
        assertEquals(1, d.cuotas.size)

        val inf = d.informes[0]
        assertEquals(DOC_INFORME, inf.tipo)
        assertEquals("Dra. Rosa Quispe", inf.profesional)
        assertEquals("Clínica Andina", inf.clinica?.nombre)
        assertEquals(listOf("F51.0"), inf.diagnosticos.map { it.codigo })
        assertEquals("/api/paciente/mis-documentos-clinicos/informe/11111111-1111-1111-1111-111111111111", inf.urlHtml)
    }

    @Test fun descansoConNumerosComoTextoYUrlAjenaSeRearma() {
        val des = parsearDocumentosClinicos(completo)!!.descansos[0]
        assertEquals(DOC_DESCANSO, des.tipo)
        assertEquals(3, des.descanso?.dias)
        assertTrue(des.diagnosticoReservado)
        assertTrue(des.diagnosticos.isEmpty())
        // Nunca se manda el Bearer a otro host: la ruta se arma con tipo e id.
        assertEquals("/api/paciente/mis-documentos-clinicos/descanso/d1", des.urlHtml)
    }

    @Test fun ordenSinTituloNiUrlUsaLosDelTipo() {
        val o = parsearDocumentosClinicos(completo)!!.ordenes[0]
        assertEquals(DOC_ORDEN, o.tipo)
        assertEquals("Orden de exámenes", o.titulo)
        assertEquals(listOf("Hemograma completo", "TSH", "Glucosa"), o.examenes.map { it.nombre })
        assertEquals("En ayunas", o.examenes[0].indicacion)
        assertNull(o.examenes[1].indicacion)
        assertEquals("/api/paciente/mis-documentos-clinicos/orden_examenes/o1", o.urlHtml)
        assertNull(o.clinica)
        assertNull(o.profesional)
    }

    @Test fun diagnosticoReservadoNoSeMuestraAunqueViaje() {
        val t = """{"informes":[{"id":"a","tipo":"informe","diagnosticoReservado":"true",
            "diagnosticos":[{"codigo":"F32","descripcion":"Depresión"}]}]}"""
        val inf = parsearDocumentosClinicos(t)!!.informes[0]
        assertTrue(inf.diagnosticoReservado)
        assertTrue(inf.diagnosticos.isEmpty())
    }

    @Test fun rutasSeguras() {
        assertEquals("/api/paciente/mis-documentos-clinicos/informe/x", rutaHojaSegura(null, "informe", "x"))
        assertEquals("/api/paciente/mis-documentos-clinicos/informe/x", rutaHojaSegura("//evil/a", "informe", "x"))
        assertEquals("/api/paciente/mis-documentos-clinicos/informe/x",
            rutaHojaSegura("/api/paciente/mis-documentos-clinicos/../../staff/x", "informe", "x"))
        assertEquals("/api/paciente/mis-documentos-clinicos/informe/x?tam=A5",
            rutaHojaSegura("/api/paciente/mis-documentos-clinicos/informe/x?tam=A5", "informe", "x"))
        assertEquals("/presupuesto/abc", enlaceSeguro("/presupuesto/abc"))
        assertNull(enlaceSeguro(null))
        assertNull(enlaceSeguro(""))
        assertNull(enlaceSeguro("//evil.example/x"))
        assertNull(enlaceSeguro("https://evil.example/x"))
    }

    @Test fun presupuestosEstadosMonedaYAceptar() {
        val ps = parsearDocumentosClinicos(completo)!!.presupuestos
        val vigente = ps[0]
        assertEquals(PRESUPUESTO_ENVIADO, vigente.estado)
        assertTrue(vigente.puedeAceptar)
        assertEquals(320.5, vigente.total)
        assertEquals("PEN", vigente.moneda)                 // "pen" normalizado
        assertEquals("2026-10-02", vigente.fecha)          // instante → día de la clínica
        assertEquals(listOf(240.0, 80.0), vigente.items.map { it.precio })
        assertEquals("Resina · Profilaxis", resumenItemsPresupuesto(vigente))

        val viejo = ps[1]
        assertEquals(PRESUPUESTO_REEMPLAZADO, viejo.estado)
        assertFalse(viejo.puedeAceptar)
        assertNull(viejo.enlace)
        assertEquals("BOB", viejo.moneda)
        // 03:00 UTC del 1/9 = 31/8 en Lima.
        assertEquals("2026-08-31", viejo.fecha)

        // puedeAceptar sin enlace: sin botón (no se abre una página vacía).
        val sinEnlace = ps[2]
        assertFalse(sinEnlace.puedeAceptar)
        assertNull(sinEnlace.enlace)
        assertEquals(0.0, sinEnlace.total)
        assertEquals("PEN", sinEnlace.moneda)
        assertEquals("Presupuesto", sinEnlace.titulo)
    }

    @Test fun estadoDesconocidoDePresupuestoEsVencidoSinAccion() {
        val t = """{"presupuestos":[{"id":"z","estado":"Raro","puedeAceptar":true,"enlace":"/presupuesto/t"}]}"""
        val p = parsearDocumentosClinicos(t)!!.presupuestos[0]
        assertEquals(PRESUPUESTO_VENCIDO, p.estado)
        assertFalse(p.puedeAceptar)
        assertEquals("Vencido" to TonoDoc.NEUTRO, estadoPresupuestoUi(p.estado))
    }

    @Test fun cuotasOrdenadasConNulosYResumenDelServidor() {
        val plan = parsearDocumentosClinicos(completo)!!.cuotas[0]
        assertEquals("BOB", plan.moneda)
        assertEquals("Dr. Luis Mamani", plan.profesional)
        assertEquals(listOf(0, 1, 2, 3), plan.cuotas.map { it.numero })
        val q3 = plan.cuotas[3]
        assertEquals(0.0, q3.pagado)
        assertEquals(100.0, q3.saldo)                     // saldo null → monto − pagado
        assertEquals("3 de 3", q3.etiqueta)                // sin etiqueta → la de la web
        assertEquals("Programada", q3.estadoTexto)
        assertEquals("Vence hoy", plan.cuotas[2].estadoTexto)
        assertEquals(250.0, plan.resumen.saldo)
        assertEquals(1, plan.resumen.atrasadas)
        assertFalse(plan.resumen.alDia)
    }

    @Test fun resumenSeCalculaSiNoViene() {
        val t = """{"cuotas":[{"id":"t","cuotas":[
            {"numero":"0","monto":"100","estado":"pagada"},
            {"numero":1,"monto":50,"estado":"atrasada","pagado":"10"},
            {"numero":2,"monto":50,"estado":"programada"}]}]}"""
        val plan = parsearDocumentosClinicos(t)!!.cuotas[0]
        assertEquals("PEN", plan.moneda)
        assertEquals(100.0, plan.cuotas[0].pagado)         // pagada sin "pagado" → el monto
        val r = plan.resumen
        assertEquals(200.0, r.totalPlan)
        assertEquals(110.0, r.pagado)
        assertEquals(90.0, r.saldo)
        assertEquals(1, r.pagadas)
        assertEquals(2, r.nCuotas)
        assertEquals(1, r.atrasadas)
        assertEquals(40.0, r.montoAtrasado)
        assertFalse(r.alDia)
    }

    @Test fun planSinCuotasNoSeMuestra() {
        val t = """{"cuotas":[{"id":"t","cuotas":[]},{"id":"u"},{"cuotas":[{"numero":1,"monto":1}]}]}"""
        assertTrue(parsearDocumentosClinicos(t)!!.cuotas.isEmpty())
    }

    @Test fun coloresDeLasCuotas() {
        assertEquals(TonoDoc.OK, tonoCuota(CUOTA_PAGADA))
        assertEquals(TonoDoc.ERROR, tonoCuota(CUOTA_ATRASADA))
        assertEquals(TonoDoc.PEND, tonoCuota(CUOTA_VENCE_HOY))
        assertEquals(TonoDoc.PEND, tonoCuota(CUOTA_POR_VENCER))
        assertEquals(TonoDoc.NEUTRO, tonoCuota(CUOTA_PROGRAMADA))
        assertEquals(TonoDoc.NEUTRO, tonoCuota("otra"))
        assertEquals("Por revisar" to TonoDoc.PEND, estadoPresupuestoUi(PRESUPUESTO_ENVIADO))
        assertEquals("Aceptado" to TonoDoc.OK, estadoPresupuestoUi(PRESUPUESTO_ACEPTADO))
        assertEquals("No aceptado" to TonoDoc.ERROR, estadoPresupuestoUi(PRESUPUESTO_RECHAZADO))
        assertEquals("Reemplazado" to TonoDoc.NEUTRO, estadoPresupuestoUi(PRESUPUESTO_REEMPLAZADO))
    }

    @Test fun cadaMontoEnSuMoneda() {
        val d = parsearDocumentosClinicos(completo)!!
        assertEquals("S/ 320.50", dineroDoc(d.presupuestos[0].total, d.presupuestos[0].moneda))
        assertEquals("Bs 500.00", dineroDoc(d.presupuestos[1].total, d.presupuestos[1].moneda))
        val plan = d.cuotas[0]
        assertEquals("Bs 250.00", dineroDoc(plan.resumen.saldo, plan.moneda))
        assertEquals("CLP$ 1,500", dineroDoc(1500.0, "CLP"))
    }

    @Test fun cuotasVisiblesSoloLoPendiente() {
        val plan = parsearDocumentosClinicos(completo)!!.cuotas[0]
        assertEquals(listOf(1, 2, 3), cuotasVisibles(plan, completo = false).map { it.numero })
        assertEquals(listOf(1, 2), cuotasVisibles(plan, completo = false, max = 2).map { it.numero })
        assertEquals(4, cuotasVisibles(plan, completo = true).size)
        val pagado = plan.copy(cuotas = plan.cuotas.map { it.copy(estado = CUOTA_PAGADA) })
        assertTrue(cuotasVisibles(pagado, completo = false).isEmpty())
    }

    @Test fun lineaDeOrigen() {
        assertEquals("Dra. X · 🏥 Andina", lineaOrigenDoc("Dra. X", ClinicaDocPortal("Andina", null, null)))
        assertEquals("🏥 Andina", lineaOrigenDoc(null, ClinicaDocPortal("Andina", null, null)))
        assertEquals("", lineaOrigenDoc(null, null))
    }

    @Test fun toleraVacioYBasura() {
        val v = assertNotNull(parsearDocumentosClinicos("""{"informes":[],"descansos":[],"ordenes":[],"presupuestos":[],"cuotas":[]}"""))
        assertFalse(v.tieneDatos)
        // Servidor viejo / parte caída: listas ausentes o null → vacías.
        val parcial = assertNotNull(parsearDocumentosClinicos("""{"informes":null,"ordenes":[{"tipo":"orden_examenes"},42,null]}"""))
        assertFalse(parcial.tieneDatos)
        assertNull(parsearDocumentosClinicos("no es json"))
        assertNull(parsearDocumentosClinicos("[]"))
    }

    @Test fun repetidosPorIdSeQuitan() {
        val t = """{"informes":[{"id":"a","tipo":"informe"},{"id":"a","tipo":"informe"}]}"""
        assertEquals(1, parsearDocumentosClinicos(t)!!.informes.size)
    }
}
