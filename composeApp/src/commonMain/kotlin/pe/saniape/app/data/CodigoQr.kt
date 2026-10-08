package pe.saniape.app.data

import kotlin.math.abs

/**
 * Generador de códigos QR en Kotlin puro (sin librerías: corre igual en Android e
 * iOS). Lo usa "Invitar por enlace o QR" del equipo: el QR se dibuja con Canvas.
 *
 * Es una adaptación compacta del algoritmo de referencia de Project Nayuki (MIT),
 * solo con lo que hace falta acá: modo BYTE (UTF-8), versiones 1–40, los cuatro
 * niveles de corrección y la máscara elegida por penalización (o una fija, para
 * los tests). Está verificado módulo a módulo contra la librería `qrcode` que usa
 * la web (ver CodigoQrTest): mismo texto, nivel y máscara ⇒ misma matriz.
 */
object CodigoQr {

    /** Nivel de corrección de errores. `formato` = los 2 bits que van en el QR. */
    enum class Nivel(val formato: Int) { L(1), M(0), Q(3), H(2) }

    /** Matriz cuadrada de módulos: `oscuro(fila, columna)`. */
    class Matriz internal constructor(val tamano: Int, val version: Int, val mascara: Int, private val m: Array<BooleanArray>) {
        fun oscuro(fila: Int, columna: Int): Boolean = m[fila][columna]
    }

    /**
     * Codifica [texto] (UTF-8) con la versión más chica que alcance para [nivel].
     * [mascara] null = la de menor penalización (lo normal); 0..7 = fija.
     * Lanza IllegalArgumentException si el texto no entra ni en la versión 40.
     */
    fun generar(texto: String, nivel: Nivel = Nivel.M, mascara: Int? = null): Matriz {
        require(mascara == null || mascara in 0..7) { "Máscara inválida" }
        val datos = texto.encodeToByteArray()

        // 1) Versión mínima.
        var version = 1
        while (true) {
            val capacidad = numCodigosDatos(version, nivel) * 8
            val usados = 4 + bitsDeConteo(version) + datos.size * 8
            if (usados <= capacidad) break
            require(version < 40) { "Texto demasiado largo para un QR" }
            version++
        }

        // 2) Bits: modo byte (0100), largo, datos, terminador y relleno.
        val bits = ArrayList<Boolean>()
        fun agregar(valor: Int, n: Int) { for (i in n - 1 downTo 0) bits.add(((valor ushr i) and 1) != 0) }
        agregar(0x4, 4)
        agregar(datos.size, bitsDeConteo(version))
        for (b in datos) agregar(b.toInt() and 0xFF, 8)
        val capacidadBits = numCodigosDatos(version, nivel) * 8
        agregar(0, minOf(4, capacidadBits - bits.size))
        agregar(0, (8 - bits.size % 8) % 8)
        var relleno = 0xEC
        while (bits.size < capacidadBits) { agregar(relleno, 8); relleno = relleno xor (0xEC xor 0x11) }
        val codigos = IntArray(bits.size / 8) { i ->
            var v = 0
            for (j in 0 until 8) v = (v shl 1) or (if (bits[i * 8 + j]) 1 else 0)
            v
        }

        // 3) Corrección de errores + intercalado, y armado de la matriz.
        val todos = agregarCorreccion(codigos, version, nivel)
        val q = Constructor(version, nivel)
        q.patronesFijos()
        q.colocar(todos)

        val elegida = mascara ?: (0..7).minBy { msk ->
            q.aplicarMascara(msk)
            q.bitsDeFormato(msk)
            val p = q.penalizacion()
            q.aplicarMascara(msk) // XOR: la deshace
            p
        }
        q.aplicarMascara(elegida)
        q.bitsDeFormato(elegida)
        return Matriz(q.tamano, version, elegida, q.modulos)
    }

    // ── Tablas del estándar (índice = versión; el 0 no se usa) ─────────────────

    private val ECC_POR_BLOQUE = arrayOf(
        intArrayOf(-1, 7, 10, 15, 20, 26, 18, 20, 24, 30, 18, 20, 24, 26, 30, 22, 24, 28, 30, 28, 28, 28, 28, 30, 30, 26, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30), // L
        intArrayOf(-1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24, 28, 28, 26, 26, 26, 26, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28), // M
        intArrayOf(-1, 13, 22, 18, 26, 18, 24, 18, 22, 20, 24, 28, 26, 24, 20, 30, 24, 28, 28, 26, 30, 28, 30, 30, 30, 30, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30), // Q
        intArrayOf(-1, 17, 28, 22, 16, 22, 28, 26, 26, 24, 28, 24, 28, 22, 24, 24, 30, 28, 28, 26, 28, 30, 24, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30), // H
    )
    private val BLOQUES = arrayOf(
        intArrayOf(-1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 4, 4, 4, 4, 4, 6, 6, 6, 6, 7, 8, 8, 9, 9, 10, 12, 12, 12, 13, 14, 15, 16, 17, 18, 19, 19, 20, 21, 22, 24, 25), // L
        intArrayOf(-1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5, 5, 8, 9, 9, 10, 10, 11, 13, 14, 16, 17, 17, 18, 20, 21, 23, 25, 26, 28, 29, 31, 33, 35, 37, 38, 40, 43, 45, 47, 49), // M
        intArrayOf(-1, 1, 1, 2, 2, 4, 4, 6, 6, 8, 8, 8, 10, 12, 16, 12, 17, 16, 18, 21, 20, 23, 23, 25, 27, 29, 34, 34, 35, 38, 40, 43, 45, 48, 51, 53, 56, 59, 62, 65, 68), // Q
        intArrayOf(-1, 1, 1, 2, 4, 4, 4, 5, 6, 8, 8, 11, 11, 16, 16, 18, 16, 19, 21, 25, 25, 25, 34, 30, 32, 35, 37, 40, 42, 45, 48, 51, 54, 57, 60, 63, 66, 70, 74, 77, 81), // H
    )

    private fun fila(n: Nivel) = when (n) { Nivel.L -> 0; Nivel.M -> 1; Nivel.Q -> 2; Nivel.H -> 3 }

    private fun bitsDeConteo(version: Int) = if (version <= 9) 8 else 16

    /** Módulos disponibles para datos + corrección (sin patrones fijos). */
    private fun modulosCrudos(version: Int): Int {
        var r = (16 * version + 128) * version + 64
        if (version >= 2) {
            val n = version / 7 + 2
            r -= (25 * n - 10) * n - 55
            if (version >= 7) r -= 36
        }
        return r
    }

    private fun numCodigosDatos(version: Int, nivel: Nivel): Int =
        modulosCrudos(version) / 8 - ECC_POR_BLOQUE[fila(nivel)][version] * BLOQUES[fila(nivel)][version]

    // ── Reed-Solomon sobre GF(256), polinomio 0x11D ────────────────────────────

    private fun mult(x: Int, y: Int): Int {
        var z = 0
        for (i in 7 downTo 0) {
            z = (z shl 1) xor ((z ushr 7) * 0x11D)
            z = z xor (((y ushr i) and 1) * x)
        }
        return z
    }

    private fun divisor(grado: Int): IntArray {
        val r = IntArray(grado)
        r[grado - 1] = 1
        var raiz = 1
        for (i in 0 until grado) {
            for (j in r.indices) {
                r[j] = mult(r[j], raiz)
                if (j + 1 < r.size) r[j] = r[j] xor r[j + 1]
            }
            raiz = mult(raiz, 0x02)
        }
        return r
    }

    private fun resto(datos: IntArray, div: IntArray): IntArray {
        val r = IntArray(div.size)
        for (b in datos) {
            val factor = b xor r[0]
            for (i in 0 until r.size - 1) r[i] = r[i + 1]
            r[r.size - 1] = 0
            for (i in r.indices) r[i] = r[i] xor mult(div[i], factor)
        }
        return r
    }

    private fun agregarCorreccion(datos: IntArray, version: Int, nivel: Nivel): IntArray {
        val nBloques = BLOQUES[fila(nivel)][version]
        val eccLen = ECC_POR_BLOQUE[fila(nivel)][version]
        val crudos = modulosCrudos(version) / 8
        val cortos = nBloques - crudos % nBloques
        val largoCorto = crudos / nBloques
        val div = divisor(eccLen)
        val bloques = ArrayList<IntArray>()
        var k = 0
        for (i in 0 until nBloques) {
            val n = largoCorto - eccLen + (if (i < cortos) 0 else 1)
            val dat = datos.copyOfRange(k, k + n)
            k += n
            val ecc = resto(dat, div)
            // Los bloques cortos llevan un hueco para intercalar parejo.
            val bloque = IntArray(largoCorto + 1)
            dat.copyInto(bloque, 0)
            ecc.copyInto(bloque, largoCorto + 1 - eccLen)
            bloques.add(bloque)
        }
        val salida = ArrayList<Int>(crudos)
        for (i in 0 until largoCorto + 1) {
            for (j in bloques.indices) {
                if (i != largoCorto - eccLen || j >= cortos) salida.add(bloques[j][i])
            }
        }
        return salida.toIntArray()
    }

    // ── Armado de la matriz ────────────────────────────────────────────────────

    private class Constructor(val version: Int, val nivel: Nivel) {
        val tamano = version * 4 + 17
        val modulos = Array(tamano) { BooleanArray(tamano) }
        val fijo = Array(tamano) { BooleanArray(tamano) }

        fun poner(x: Int, y: Int, oscuro: Boolean) { modulos[y][x] = oscuro; fijo[y][x] = true }

        fun patronesFijos() {
            for (i in 0 until tamano) { poner(6, i, i % 2 == 0); poner(i, 6, i % 2 == 0) }
            buscador(3, 3); buscador(tamano - 4, 3); buscador(3, tamano - 4)
            val pos = posicionesAlineacion()
            val n = pos.size
            for (i in 0 until n) for (j in 0 until n) {
                if (!(i == 0 && j == 0 || i == 0 && j == n - 1 || i == n - 1 && j == 0)) alineacion(pos[i], pos[j])
            }
            bitsDeFormato(0) // provisorio: reserva las zonas
            bitsDeVersion()
        }

        private fun buscador(x: Int, y: Int) {
            for (dy in -4..4) for (dx in -4..4) {
                val d = maxOf(abs(dx), abs(dy))
                val xx = x + dx; val yy = y + dy
                if (xx in 0 until tamano && yy in 0 until tamano) poner(xx, yy, d != 2 && d != 4)
            }
        }

        private fun alineacion(x: Int, y: Int) {
            for (dy in -2..2) for (dx in -2..2) poner(x + dx, y + dy, maxOf(abs(dx), abs(dy)) != 1)
        }

        private fun posicionesAlineacion(): IntArray {
            if (version == 1) return IntArray(0)
            val n = version / 7 + 2
            val paso = (version * 8 + n * 3 + 5) / (n * 4 - 4) * 2
            val r = IntArray(n)
            r[0] = 6
            var p = tamano - 7
            for (i in n - 1 downTo 1) { r[i] = p; p -= paso }
            return r
        }

        fun bitsDeFormato(mascara: Int) {
            val dato = (nivel.formato shl 3) or mascara
            var rem = dato
            repeat(10) { rem = (rem shl 1) xor ((rem ushr 9) * 0x537) }
            val bits = ((dato shl 10) or rem) xor 0x5412
            fun bit(i: Int) = ((bits ushr i) and 1) != 0
            for (i in 0..5) poner(8, i, bit(i))
            poner(8, 7, bit(6)); poner(8, 8, bit(7)); poner(7, 8, bit(8))
            for (i in 9 until 15) poner(14 - i, 8, bit(i))
            for (i in 0 until 8) poner(tamano - 1 - i, 8, bit(i))
            for (i in 8 until 15) poner(8, tamano - 15 + i, bit(i))
            poner(8, tamano - 8, true)
        }

        private fun bitsDeVersion() {
            if (version < 7) return
            var rem = version
            repeat(12) { rem = (rem shl 1) xor ((rem ushr 11) * 0x1F25) }
            val bits = (version shl 12) or rem
            for (i in 0 until 18) {
                val b = ((bits ushr i) and 1) != 0
                val a = tamano - 11 + i % 3
                val c = i / 3
                poner(a, c, b); poner(c, a, b)
            }
        }

        fun colocar(datos: IntArray) {
            var i = 0
            var derecha = tamano - 1
            while (derecha >= 1) {
                if (derecha == 6) derecha = 5
                for (vert in 0 until tamano) {
                    for (j in 0..1) {
                        val x = derecha - j
                        val arriba = ((derecha + 1) and 2) == 0
                        val y = if (arriba) tamano - 1 - vert else vert
                        if (!fijo[y][x] && i < datos.size * 8) {
                            modulos[y][x] = ((datos[i ushr 3] ushr (7 - (i and 7))) and 1) != 0
                            i++
                        }
                    }
                }
                derecha -= 2
            }
        }

        fun aplicarMascara(m: Int) {
            for (y in 0 until tamano) for (x in 0 until tamano) {
                val inv = when (m) {
                    0 -> (x + y) % 2 == 0
                    1 -> y % 2 == 0
                    2 -> x % 3 == 0
                    3 -> (x + y) % 3 == 0
                    4 -> (x / 3 + y / 2) % 2 == 0
                    5 -> x * y % 2 + x * y % 3 == 0
                    6 -> (x * y % 2 + x * y % 3) % 2 == 0
                    else -> ((x + y) % 2 + x * y % 3) % 2 == 0
                }
                if (inv && !fijo[y][x]) modulos[y][x] = !modulos[y][x]
            }
        }

        fun penalizacion(): Int {
            var r = 0
            fun linea(oscuro: (Int) -> Boolean) {
                var color = false
                var largo = 0
                val hist = IntArray(7)
                for (i in 0 until tamano) {
                    val o = oscuro(i)
                    if (o == color) {
                        largo++
                        if (largo == 5) r += 3 else if (largo > 5) r++
                    } else {
                        historia(largo, hist)
                        if (!color) r += patrones(hist) * 40
                        color = o
                        largo = 1
                    }
                }
                r += terminar(color, largo, hist) * 40
            }
            for (y in 0 until tamano) linea { x -> modulos[y][x] }
            for (x in 0 until tamano) linea { y -> modulos[y][x] }
            for (y in 0 until tamano - 1) for (x in 0 until tamano - 1) {
                val c = modulos[y][x]
                if (c == modulos[y][x + 1] && c == modulos[y + 1][x] && c == modulos[y + 1][x + 1]) r += 3
            }
            var oscuros = 0
            for (fila in modulos) for (b in fila) if (b) oscuros++
            val total = tamano * tamano
            val k = (abs(oscuros * 20 - total * 10) + total - 1) / total - 1
            return r + k * 10
        }

        private fun patrones(h: IntArray): Int {
            val n = h[1]
            val nucleo = n > 0 && h[2] == n && h[3] == n * 3 && h[4] == n && h[5] == n
            return (if (nucleo && h[0] >= n * 4 && h[6] >= n) 1 else 0) +
                (if (nucleo && h[6] >= n * 4 && h[0] >= n) 1 else 0)
        }

        private fun terminar(color: Boolean, largo: Int, h: IntArray): Int {
            var l = largo
            if (color) { historia(l, h); l = 0 }
            l += tamano
            historia(l, h)
            return patrones(h)
        }

        private fun historia(largo: Int, h: IntArray) {
            var l = largo
            if (h[0] == 0) l += tamano
            for (i in h.size - 1 downTo 1) h[i] = h[i - 1]
            h[0] = l
        }
    }
}
