// Genera composeApp/.../data/staff/CrecimientoOmsDatos.kt desde el módulo de
// datos de la WEB (lib/crecimiento-oms-datos.ts, que a su vez sale de las tablas
// LMS OFICIALES de la OMS con scripts/generar-tablas-oms.mjs del repo web).
// Mismos nodos [x, L, M, S], mismos números: la app calcula igual que la web.
//
// Compacto a propósito: cada curva va como UN texto "x,l,m,s;x,l,m,s;…" (la DEX
// guarda el texto tal cual, ~25 KB) y la app lo convierte a números recién al
// abrir la pestaña 👶 Crecimiento (`TablasOms.cargar`), no al arrancar.
//
// Uso (desde la raíz de la app):
//   node scripts/generar-tablas-oms-kt.mjs ../ai-clinic-dashboard/lib/crecimiento-oms-datos.ts \
//     > composeApp/src/commonMain/kotlin/pe/saniape/app/data/staff/CrecimientoOmsDatos.kt

import fs from 'node:fs'

const origen = process.argv[2]
if (!origen) { console.error('Falta la ruta de lib/crecimiento-oms-datos.ts'); process.exit(1) }
const ts = fs.readFileSync(origen, 'utf8')
const i = ts.indexOf('= {', ts.indexOf('export const TABLAS_OMS'))
if (i < 0) { console.error('No se encontró TABLAS_OMS'); process.exit(1) }
// El literal es JS puro (claves sin comillas, arreglos de números).
const tablas = new Function(`return ${ts.slice(i + 2).trim().replace(/;?\s*$/, '')}`)()

const ORDEN = ['wfa', 'lhfa', 'bfa', 'hcfa', 'wfl', 'wfh', 'wfa07', 'hfa07', 'bfa07']
let total = 0
const constantes = []
const ramas = []
for (const t of ORDEN) {
  const par = tablas[t]
  if (!par) { console.error(`Falta la tabla ${t}`); process.exit(1) }
  const nombres = {}
  for (const s of ['m', 'f']) {
    const nodos = par[s]
    for (const n of nodos) {
      if (n.length !== 4 || n.some(v => !Number.isFinite(v))) { console.error(`Nodo inválido en ${t}.${s}`); process.exit(1) }
    }
    total += nodos.length
    const nombre = `${t.toUpperCase()}_${s.toUpperCase()}`
    nombres[s] = nombre
    constantes.push(`    private const val ${nombre} =\n        "${nodos.map(n => n.map(String).join(',')).join(';')}"`)
  }
  ramas.push(`        TablaOms.${t.toUpperCase()} -> if (femenino) ${nombres.f} else ${nombres.m}`)
}

process.stdout.write(`// GENERADO por scripts/generar-tablas-oms-kt.mjs — NO editar a mano.
//
// Tablas LMS oficiales de la OMS reducidas a nodos (interpolación lineal de
// L, M y S con error < 0,002 z frente a la tabla oficial), copiadas TAL CUAL de
// la web (lib/crecimiento-oms-datos.ts). ${total} nodos.
//  · WFA/LHFA/BFA/HCFA: x = edad en DÍAS (0–1856), WHO Child Growth Standards 2006.
//    LHFA/BFA: hasta el día 730 = longitud (acostado); desde 731 = talla (de pie).
//  · WFL: x = longitud en cm (45–110, < 2 años) · WFH: x = talla en cm (65–120, ≥ 2 años).
//  · WFA07/HFA07/BFA07: x = edad en MESES, WHO Reference 2007.
// Cada curva: "x,L,M,S;x,L,M,S;…" (se convierte a números al abrir la pestaña).
// Fuentes: docs/superpowers/specs/2026-10-16-pediatria-crecimiento-vacunas-design.md (web).

package pe.saniape.app.data.staff

internal object CrecimientoOmsDatos {
    const val NODOS = ${total}

    fun texto(tabla: TablaOms, femenino: Boolean): String = when (tabla) {
${ramas.join('\n')}
    }

${constantes.join('\n')}
}
`)
