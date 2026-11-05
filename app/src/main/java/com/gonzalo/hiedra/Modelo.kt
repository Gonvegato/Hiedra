package com.gonzalo.hiedra

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/*
 * El modelo de Hiedra (docs/MODELO.md): un juego es una cosa con identidad propia (su id viaja con la cuenta), y cómo se abre es una lista
 * de lanzadores (la ROM, la app, la web, el juego del PC). Las horas se guardan por fuente: las de este dispositivo, las de tus otros
 * móviles (se suman) y las externas, Steam y el PC (se queda el máximo, nunca se suman dos veces).
 */

/** En qué punto estás con el juego. «Pasado» = terminado; «Abandonado» = lo dejaste. */
enum class Estado(val nombre: String) { JUGANDO("Jugando"), PENDIENTE("Pendiente"), PASADO("Pasado"), ABANDONADO("Abandonado") }

/** Cómo se abre un juego: una ROM (con su emulador), una app de Android, una web o un juego del PC (de la biblioteca que Atrio del PC publica). */
enum class TipoLanzador { ROM, APP, WEB, PC }

/** Los menús pequeños de las secciones: de dónde es el juego. */
enum class Menu(val nombre: String) { PC("PC"), EMULACION("Emulación"), ANDROID("Android"), WEB("Web") }

/**
 * Una manera de abrir un juego. Su [clave] es lo que identifica al lanzador en todos tus dispositivos (sirve para no duplicar al añadir, para
 * importar y para que Azabache enlace lo que valoró por esa clave). Dónde está el archivo de una ROM NO va aquí: es de cada dispositivo ([Archivos]).
 */
data class Lanzador(
    val tipo: TipoLanzador,
    /** ROM: el sistema (id del catálogo), el nombre del archivo sin extensión y el emulador elegido solo para este juego ("" = el del sistema). */
    val sistema: String = "", val archivo: String = "", val emulador: String = "",
    /** App: el paquete. */
    val paquete: String = "",
    /** Web: la dirección y la pantalla (0 automática, 1 horizontal, 2 vertical). */
    val url: String = "", val orientacion: Int = 0,
    /** PC: su clave en la biblioteca del PC (la de Atrio) y el PC donde está. */
    val pcKey: String = "", val pcDev: String = "",
) {
    val clave: String get() = when (tipo) {
        TipoLanzador.ROM -> "rom:$sistema:${Catalogo.norm(archivo)}"
        TipoLanzador.APP -> "app:$paquete"
        TipoLanzador.WEB -> "web:" + url.lowercase().removePrefix("https://").removePrefix("http://").removePrefix("www.").trimEnd('/')
        TipoLanzador.PC -> "pc:$pcKey"
    }
    val menu: Menu get() = when (tipo) { TipoLanzador.ROM -> Menu.EMULACION; TipoLanzador.APP -> Menu.ANDROID; TipoLanzador.WEB -> Menu.WEB; TipoLanzador.PC -> Menu.PC }

    fun aJson(): JSONObject = JSONObject().put("tp", tipo.name).apply {
        if (sistema.isNotBlank()) put("s", sistema); if (archivo.isNotBlank()) put("a", archivo); if (emulador.isNotBlank()) put("em", emulador)
        if (paquete.isNotBlank()) put("pk", paquete); if (url.isNotBlank()) put("u", url); if (orientacion != 0) put("o", orientacion)
        if (pcKey.isNotBlank()) put("pck", pcKey); if (pcDev.isNotBlank()) put("pcd", pcDev)
    }

    companion object {
        fun deJson(o: JSONObject): Lanzador? {
            val t = TipoLanzador.entries.firstOrNull { it.name == o.optString("tp") } ?: return null
            return Lanzador(t, o.optString("s"), o.optString("a"), o.optString("em"), o.optString("pk"), o.optString("u"), o.optInt("o"), o.optString("pck"), o.optString("pcd"))
        }
        fun rom(sistema: String, archivo: String) = Lanzador(TipoLanzador.ROM, sistema = sistema, archivo = archivo)
        fun app(paquete: String) = Lanzador(TipoLanzador.APP, paquete = paquete)
        fun web(url: String, orientacion: Int = 0) = Lanzador(TipoLanzador.WEB, url = url, orientacion = orientacion)
        fun pc(key: String, dev: String) = Lanzador(TipoLanzador.PC, pcKey = key, pcDev = dev)
    }
}

/** Lo jugado desde una fuente: tiempo, veces y cada día («AAAA-MM-DD» → ms). */
data class Horas(val ms: Long = 0L, val veces: Int = 0, val dias: Map<String, Long> = emptyMap()) {
    val vacias: Boolean get() = ms <= 0L && veces <= 0 && dias.isEmpty()
    operator fun plus(o: Horas) = Horas(ms + o.ms, veces + o.veces, sumaDias(dias, o.dias))
    /** De dos lecturas de la misma fuente externa, la mayor (día a día): nunca se suma dos veces. */
    fun max(o: Horas) = Horas(maxOf(ms, o.ms), maxOf(veces, o.veces), (dias.keys + o.dias.keys).associateWith { maxOf(dias[it] ?: 0L, o.dias[it] ?: 0L) })

    fun aJson(): JSONObject = JSONObject().put("ms", ms).put("v", veces).put("dd", JSONObject().also { d -> dias.entries.sortedByDescending { it.key }.take(400).forEach { (k, v) -> d.put(k, v) } })
    companion object {
        fun deJson(o: JSONObject?): Horas = if (o == null) Horas() else Horas(o.optLong("ms"), o.optInt("v"), o.optJSONObject("dd")?.let { d -> d.keys().asSequence().associateWith { k -> d.optLong(k) } } ?: emptyMap())
        fun sumaDias(a: Map<String, Long>, b: Map<String, Long>): Map<String, Long> = (a.keys + b.keys).associateWith { (a[it] ?: 0L) + (b[it] ?: 0L) }
    }
}

/**
 * Las fuentes externas de horas: la API de Steam, cada PC con Atrio (`pc:<dispositivo del PC>`) y lo jugado en cada dispositivo de Carmesí
 * antes de pasarse a Hiedra (`carmesi:<dispositivo>`: se importa una vez y ya no cambia; viaja con la cuenta y se mezcla por máximo).
 */
object Fuentes {
    const val STEAM = "steam"
    fun pc(dev: String) = "pc:$dev"
    fun esPc(f: String) = f.startsWith("pc:")
    fun carmesi(dev: String) = "carmesi:$dev"
    fun esCarmesi(f: String) = f.startsWith("carmesi:")
}

/** Un juego de la biblioteca. Lo que no se dice aquí (dónde está cada ROM en este móvil) está en [Archivos]. */
data class Juego(
    val id: String,
    val titulo: String,
    /** En qué se juega (PC, Switch, PS5…): decide el menú pequeño si no hay lanzador. */
    val plataforma: String = "",
    val estado: Estado = Estado.PENDIENTE,
    val favorito: Boolean = false,
    /** Cuándo lo pasaste (ms; 0 = no). */
    val finalizado: Long = 0L,
    val anadido: Long = 0L,
    val ultima: Long = 0L,
    /** Última vez que se cambió lo editable (ms): al sincronizar gana el cambio más reciente. */
    val mod: Long = 0L,
    /** Tu portada: archivo en la carpeta de la app (de este móvil; viaja por kDrive). */
    val portada: String = "",
    /** Una portada de internet (SteamGridDB, la biblioteca del PC…): se usa cuando no has puesto ninguna. */
    val portadaUrl: String = "",
    /** El encuadre de tu portada: zoom (1 = cubre el hueco) y desplazamiento como fracción del hueco (-1..1). */
    val zoom: Float = 1f, val ox: Float = 0f, val oy: Float = 0f,
    /** Lo que suele durar (minutos; 0 = no se sabe): la historia, con extras y al completo. */
    val durHistoria: Int = 0, val durExtras: Int = 0, val durCompleto: Int = 0,
    /** Claves por las que se conoció al juego además de las de sus lanzadores (`juego:<título>` de Atrio, las de lanzadores que ya no tiene…). */
    val otrasClaves: List<String> = emptyList(),
    val lanzadores: List<Lanzador> = emptyList(),
    /** Lo jugado en ESTE dispositivo; en tus otros móviles (por dispositivo; se suma); y las fuentes externas (Steam, cada PC; máximo). */
    val propias: Horas = Horas(),
    val ajenas: Map<String, Horas> = emptyMap(),
    val externas: Map<String, Horas> = emptyMap(),
) {
    /** Todas las claves por las que se le conoce: las de sus lanzadores y las de antes. */
    val claves: List<String> get() = (lanzadores.map { it.clave } + otrasClaves).distinct()
    val msJugados: Long get() = propias.ms + ajenas.values.sumOf { it.ms } + externas.values.sumOf { it.ms }
    val veces: Int get() = propias.veces + ajenas.values.sumOf { it.veces } + externas.values.sumOf { it.veces }
    /** Lo jugado cada día, sumando todas las fuentes. */
    fun dias(): Map<String, Long> = (ajenas.values + externas.values).fold(propias.dias) { acc, h -> Horas.sumaDias(acc, h.dias) }
    /** Las horas de una fuente externa (Steam, un PC), o nada. */
    fun externa(f: String): Horas = externas[f] ?: Horas()

    val sinLanzador: Boolean get() = lanzadores.isEmpty()
    val rom: Lanzador? get() = lanzadores.firstOrNull { it.tipo == TipoLanzador.ROM }
    val app: Lanzador? get() = lanzadores.firstOrNull { it.tipo == TipoLanzador.APP }
    val web: Lanzador? get() = lanzadores.firstOrNull { it.tipo == TipoLanzador.WEB }
    val pc: Lanzador? get() = lanzadores.firstOrNull { it.tipo == TipoLanzador.PC }
    /** Es (también) del PC: se juega en el PC o en streaming. */
    val enPc: Boolean get() = pc != null
    /** Los menús pequeños donde sale: los de sus lanzadores; sin ninguno, el de su plataforma. */
    val menus: Set<Menu> get() = lanzadores.map { it.menu }.toSet().ifEmpty { setOf(menuDePlataforma(plataforma)) }
    /** El menú principal (para el nodo «tiempo»): el primero de los suyos. */
    val menu: Menu get() = menus.first()
    /** Tiene una ROM, pero su archivo no está en este móvil (y nada más con que abrirlo aquí). */
    val enOtroDispositivo: Boolean get() = rom != null && !Archivos.tiene(rom!!.clave) && lanzadores.none { it.tipo == TipoLanzador.APP || it.tipo == TipoLanzador.WEB }
    /** ¿Se puede abrir desde este móvil ahora? */
    val abrible: Boolean get() = lanzadores.any { it.tipo == TipoLanzador.APP || it.tipo == TipoLanzador.WEB || (it.tipo == TipoLanzador.ROM && Archivos.tiene(it.clave)) }
    /** ¿Has movido o ampliado la portada? */
    val encuadrada: Boolean get() = zoom != 1f || ox != 0f || oy != 0f
    /** Cuánto llevas de la historia (0–1), si se sabe lo que dura. */
    val avance: Float? get() = if (durHistoria > 0 && msJugados > 0) (msJugados / 60_000f / durHistoria).coerceIn(0f, 1f) else null
    /** El lanzador con el que se abre desde el móvil por defecto: la ROM que está aquí, la app, la web; si no, el del PC. */
    val principal: Lanzador? get() = lanzadores.firstOrNull { it.tipo == TipoLanzador.ROM && Archivos.tiene(it.clave) } ?: app ?: web ?: lanzadores.firstOrNull()
    fun lanzador(clave: String): Lanzador? = lanzadores.firstOrNull { it.clave == clave }
}

fun menuDePlataforma(plataforma: String): Menu {
    val p = Catalogo.norm(plataforma)
    return when {
        p.isEmpty() -> Menu.PC
        listOf("pc", "steam", "epic", "gog", "windows", "linux", "mac", "ordenador").any { p.startsWith(it) || p.endsWith(it) } || p.contains("steamdeck") -> Menu.PC
        listOf("android", "movil", "ios", "iphone", "ipad", "tablet").any { p.contains(it) } -> Menu.ANDROID
        listOf("web", "navegador", "browser").any { p.contains(it) } -> Menu.WEB
        else -> Menu.EMULACION
    }
}

/** La clave de ficha de un título (la que Atrio y Carmesí daban a un juego sin lanzador): sirve para importar y para Azabache. */
fun claveFicha(titulo: String) = "juego:" + Catalogo.norm(titulo)

/** Un juego nuevo con un título (y, si se quiere, un lanzador). */
fun nuevoJuego(titulo: String, plataforma: String = "", lanzador: Lanzador? = null, anadido: Long = System.currentTimeMillis()): Juego =
    Juego(Biblioteca.nuevoId(), titulo.trim(), plataforma = plataforma.trim(), anadido = anadido, lanzadores = listOfNotNull(lanzador))

/** Cambia el estado (y lo que va con él: «Pasado» lleva su fecha). */
fun Juego.conEstado(e: Estado, ahora: Long = System.currentTimeMillis()): Juego =
    copy(estado = e, finalizado = if (e == Estado.PASADO) (if (finalizado > 0) finalizado else ahora) else 0L)

/** Añade un lanzador (si no lo tenía ya, por su clave). */
fun Juego.conLanzador(l: Lanzador): Juego = if (lanzadores.any { it.clave == l.clave }) this else copy(lanzadores = lanzadores + l)

/** Quita un lanzador: su clave se queda entre las de antes (así Azabache y la importación siguen reconociendo el juego). */
fun Juego.sinLanzador(clave: String): Juego = copy(lanzadores = lanzadores.filter { it.clave != clave }, otrasClaves = (otrasClaves + clave).distinct())

/** Corrige las horas jugadas a mano: lo que falta o sobra lo pone este dispositivo (nunca por debajo de lo que sumen las demás fuentes). */
fun Juego.conTotalMs(total: Long): Juego {
    val otros = ajenas.values.sumOf { it.ms } + externas.values.sumOf { it.ms }
    return copy(propias = propias.copy(ms = (total - otros).coerceAtLeast(0L)))
}

/** Lo que se edita de un juego y viaja con la cuenta (gana el cambio más reciente). */
fun compartidos(j: Juego) = listOf(j.titulo, j.plataforma, j.estado, j.favorito, j.finalizado, j.portadaUrl, j.zoom, j.ox, j.oy, j.durHistoria, j.durExtras, j.durCompleto, j.otrasClaves, j.lanzadores)

/**
 * Une dos juegos que son el mismo: [destino] se queda con los lanzadores y las claves de [origen], y las horas se juntan (las propias se suman;
 * las de cada dispositivo y fuente, se suman si son de dispositivos distintos y se queda el máximo si son de la misma fuente externa).
 */
fun unirJuegos(destino: Juego, origen: Juego): Juego {
    val peso = mapOf(Estado.PENDIENTE to 0, Estado.ABANDONADO to 1, Estado.JUGANDO to 2, Estado.PASADO to 3)
    val estado = if ((peso[origen.estado] ?: 0) > (peso[destino.estado] ?: 0)) origen.estado else destino.estado
    val ajenas = (destino.ajenas.keys + origen.ajenas.keys).associateWith { (destino.ajenas[it] ?: Horas()) + (origen.ajenas[it] ?: Horas()) }
    val externas = (destino.externas.keys + origen.externas.keys).associateWith { (destino.externas[it] ?: Horas()).max(origen.externas[it] ?: Horas()) }
    var u = destino.copy(
        titulo = destino.titulo.ifBlank { origen.titulo }, plataforma = destino.plataforma.ifBlank { origen.plataforma }, estado = estado,
        finalizado = if (estado == Estado.PASADO) maxOf(destino.finalizado, origen.finalizado) else 0L, favorito = destino.favorito || origen.favorito,
        portada = destino.portada.ifBlank { origen.portada }, portadaUrl = destino.portadaUrl.ifBlank { origen.portadaUrl },
        zoom = if (destino.portada.isBlank()) origen.zoom else destino.zoom, ox = if (destino.portada.isBlank()) origen.ox else destino.ox, oy = if (destino.portada.isBlank()) origen.oy else destino.oy,
        durHistoria = if (destino.durHistoria > 0) destino.durHistoria else origen.durHistoria, durExtras = if (destino.durExtras > 0) destino.durExtras else origen.durExtras, durCompleto = if (destino.durCompleto > 0) destino.durCompleto else origen.durCompleto,
        propias = destino.propias + origen.propias, ajenas = ajenas, externas = externas,
        ultima = maxOf(destino.ultima, origen.ultima), anadido = listOf(destino.anadido, origen.anadido).filter { it > 0 }.minOrNull() ?: destino.anadido,
        otrasClaves = (destino.otrasClaves + origen.otrasClaves + origen.lanzadores.map { it.clave }).distinct()
    )
    origen.lanzadores.forEach { u = u.conLanzador(it) }
    return u.copy(otrasClaves = u.otrasClaves.filter { k -> u.lanzadores.none { it.clave == k } })
}

/** Una carpeta de ROMs que diste (para volver a escanearla). De este dispositivo. */
data class Carpeta(val uri: String, val nombre: String)

/** Dónde está cada ROM en ESTE móvil (clave del lanzador → documento y ruta real). No viaja con la cuenta. */
object Archivos {
    private class Sitio(val uri: String, val ruta: String)
    private val sitios = HashMap<String, Sitio>()
    private var archivo: File? = null

    fun cargar(c: Context) {
        archivo = File(c.filesDir, "archivos.json")
        sitios.clear()
        runCatching { JSONObject(archivo!!.readText()).let { o -> o.keys().forEach { k -> o.optJSONObject(k)?.let { s -> sitios[k] = Sitio(s.optString("uri"), s.optString("ruta")) } } } }
    }
    private fun guardar() { archivo?.let { f -> runCatching { f.writeText(JSONObject().also { o -> sitios.forEach { (k, s) -> o.put(k, JSONObject().put("uri", s.uri).put("ruta", s.ruta)) } }.toString()) } } }

    fun tiene(clave: String): Boolean = sitios[clave]?.uri?.isNotBlank() == true
    fun uri(clave: String): String = sitios[clave]?.uri.orEmpty()
    fun ruta(clave: String): String = sitios[clave]?.ruta.orEmpty()
    fun poner(clave: String, uri: String, ruta: String) { sitios[clave] = Sitio(uri, ruta); guardar() }
    fun quitar(clave: String) { sitios.remove(clave); guardar() }
    fun limpiarParaPruebas() { sitios.clear() }
    /** Las claves de las ROMs que están aquí. */
    fun claves(): Set<String> = sitios.filterValues { it.uri.isNotBlank() }.keys
}

/** La biblioteca: se guarda en un solo archivo JSON dentro de la app (y se puede copiar a mano desde Ajustes). */
object Biblioteca {
    val juegos = mutableStateListOf<Juego>()
    val carpetas = mutableStateListOf<Carpeta>()
    var cargada by mutableStateOf(false)
    private var archivo: File? = null
    private var appCtx: Context? = null
    /** Los juegos que quitaste (su id y cuándo): así los demás dispositivos también los quitan al sincronizar. */
    val borrados = HashMap<String, Long>()

    fun cargar(c: Context) {
        if (cargada) return
        archivo = File(c.filesDir, "biblioteca.json"); appCtx = c.applicationContext
        Archivos.cargar(c)
        val f = archivo!!
        if (f.exists()) runCatching { leer(f.readText()) }
        cargada = true
        runCatching { unirRepetidos() }
    }

    fun leer(txt: String, reemplazar: Boolean = true) {
        val o = JSONObject(txt)
        val js = o.optJSONArray("juegos") ?: JSONArray()
        val nuevos = (0 until js.length()).mapNotNull { runCatching { aJuego(js.getJSONObject(it)) }.getOrNull() }
        val cs = o.optJSONArray("carpetas") ?: JSONArray()
        val carp = (0 until cs.length()).map { cs.getJSONObject(it).let { x -> Carpeta(x.getString("uri"), x.optString("nombre")) } }
        if (reemplazar) { juegos.clear(); carpetas.clear(); borrados.clear() }
        o.optJSONObject("borrados")?.let { b -> b.keys().forEach { k -> borrados[k] = maxOf(borrados[k] ?: 0L, b.optLong(k)) } }
        val ids = juegos.map { it.id }.toSet()
        juegos.addAll(nuevos.filter { it.id !in ids })
        val us = carpetas.map { it.uri }.toSet()
        carpetas.addAll(carp.filter { it.uri !in us })
    }

    fun aTexto(): String = JSONObject().apply {
        put("version", 1)
        put("juegos", JSONArray().apply { juegos.forEach { put(aJson(it)) } })
        put("carpetas", JSONArray().apply { carpetas.forEach { put(JSONObject().put("uri", it.uri).put("nombre", it.nombre)) } })
        put("borrados", JSONObject().also { b -> borrados.forEach { (k, v) -> b.put(k, v) } })
    }.toString()

    fun guardar() {
        val f = archivo ?: return
        runCatching { val t = File(f.path + ".tmp"); t.writeText(aTexto()); t.renameTo(f) }
        appCtx?.let { c -> Widgets.pedir(c); SincroP.cambio(c) }
    }

    /** Guarda un juego (apuntando que se ha cambiado ahora si cambió lo editable: al sincronizar, gana el cambio más reciente). Jugar no cuenta como cambio. */
    fun poner(j: Juego) {
        val i = juegos.indexOfFirst { it.id == j.id }
        val nuevo = if (i >= 0 && compartidos(juegos[i]) == compartidos(j)) j.copy(mod = juegos[i].mod) else j.copy(mod = System.currentTimeMillis())
        if (i >= 0) juegos[i] = nuevo else { juegos.add(0, nuevo); borrados.remove(nuevo.id) }
        guardar()
    }

    /** Pone en la biblioteca lo que ha salido de mezclar con tus otros dispositivos (sin tocar la hora de cambio de nadie). */
    fun aplicarMezcla(nuevos: List<Juego>, borr: Map<String, Long>) {
        // La mezcla se hizo con una copia de hace un momento: la portada (que es de este móvil) se toma de como está AHORA, por si
        // mientras tanto pusiste una o se bajó de tu kDrive (si no, se perdía al aplicar la mezcla)
        val ahora = juegos.associateBy { it.id }
        val unidos = nuevos.map { n -> ahora[n.id]?.let { cur -> if (cur.portada != n.portada) n.copy(portada = cur.portada, zoom = cur.zoom, ox = cur.ox, oy = cur.oy) else n } ?: n }
        juegos.clear(); juegos.addAll(unidos)
        borrados.clear(); borrados.putAll(borr)
        guardar()
    }

    /** Cambia un juego a partir de su versión más reciente (así dos cambios seguidos no se pisan). */
    fun cambiar(id: String, f: (Juego) -> Juego) { por(id)?.let { poner(f(it)) } }

    fun quitar(id: String) { por(id)?.let { j -> borrados[j.id] = System.currentTimeMillis(); j.lanzadores.forEach { if (it.tipo == TipoLanzador.ROM) Archivos.quitar(it.clave) } }; juegos.removeAll { it.id == id }; guardar() }
    fun por(id: String): Juego? = juegos.firstOrNull { it.id == id }

    /** El juego al que corresponde una clave (la de uno de sus lanzadores, o una de antes). */
    fun porClave(k: String): Juego? = juegos.firstOrNull { it.lanzadores.any { l -> l.clave == k } } ?: juegos.firstOrNull { k in it.otrasClaves }

    /** El juego que se llama así (por su título normalizado), si lo hay. */
    fun porTitulo(titulo: String): Juego? = Catalogo.norm(titulo).takeIf { it.isNotEmpty() }?.let { n -> juegos.firstOrNull { Catalogo.norm(it.titulo) == n } }

    /** El juego al que iría un lanzador nuevo: el que ya lo tiene (por su clave) o el que se llama igual que [titulo]. */
    fun destinoDe(l: Lanzador, titulo: String): Juego? = porClave(l.clave) ?: porTitulo(titulo)

    /**
     * Añade un lanzador: si ya hay un juego con esa clave, se queda como está; si hay uno que se llama igual, se le añade; si no, nace un juego
     * nuevo con ese lanzador. Devuelve el juego y si era nuevo.
     */
    fun anadirLanzador(titulo: String, l: Lanzador, plataforma: String = "", anadido: Long = System.currentTimeMillis()): Pair<Juego, Boolean> {
        porClave(l.clave)?.let { return it to false }
        porTitulo(titulo)?.let { j -> val u = j.conLanzador(l); poner(u); return (por(j.id) ?: u) to false }
        val n = nuevoJuego(titulo, plataforma, l, anadido)
        poner(n)
        return (por(n.id) ?: n) to true
    }

    /**
     * Juegos repetidos: dos fichas con una clave en común (la misma ROM, la misma app, el mismo juego del PC o de Steam, el mismo título) y la
     * misma plataforma (o una sin plataforma) son el mismo juego. Pasaba al importar de Atrio o de Carmesí en dos móviles: cada uno creaba su
     * copia, con su propio id, y al quitar una volvía la del otro. Se juntan en la de id más bajo (la misma en todos tus móviles, así no se
     * pisan) y las demás se quitan (y se quitan también en tus otros móviles). Devuelve cuántas ha juntado.
     */
    fun unirRepetidos(): Int {
        val grupos = gruposRepetidos(juegos.toList())
        var n = 0
        for (g in grupos) {
            val queda = g.minOf { it }
            for (otro in g) if (otro != queda && unir(queda, otro) != null) n++
        }
        return n
    }

    /** Une dos juegos (ver [unirJuegos]): [origenId] desaparece dentro de [destinoId]. Devuelve el resultado o null si falta alguno. */
    fun unir(destinoId: String, origenId: String): Juego? {
        val d = por(destinoId) ?: return null; val o = por(origenId) ?: return null
        if (d.id == o.id) return d
        val u = unirJuegos(d, o)
        juegos.removeAll { it.id == o.id }; borrados[o.id] = System.currentTimeMillis()
        poner(u)
        return u
    }

    fun aJson(j: Juego) = JSONObject().apply {
        put("id", j.id); put("titulo", j.titulo); put("pl", j.plataforma); put("est", j.estado.name); put("fav", j.favorito); put("fin", j.finalizado)
        put("anadido", j.anadido); put("ultima", j.ultima); put("mod", j.mod); put("portada", j.portada); put("pu", j.portadaUrl)
        put("ez", j.zoom.toDouble()); put("eox", j.ox.toDouble()); put("eoy", j.oy.toDouble()); put("dh", j.durHistoria); put("de", j.durExtras); put("dc", j.durCompleto)
        put("k", JSONArray().also { a -> j.otrasClaves.forEach { a.put(it) } })
        put("ln", JSONArray().also { a -> j.lanzadores.forEach { a.put(it.aJson()) } })
        put("hp", j.propias.aJson())
        put("ha", JSONObject().also { o -> j.ajenas.forEach { (k, v) -> o.put(k, v.aJson()) } })
        put("hx", JSONObject().also { o -> j.externas.forEach { (k, v) -> o.put(k, v.aJson()) } })
    }

    fun aJuego(o: JSONObject) = Juego(
        id = o.getString("id"), titulo = o.getString("titulo"), plataforma = o.optString("pl"),
        estado = Estado.entries.firstOrNull { it.name == o.optString("est") } ?: Estado.PENDIENTE, favorito = o.optBoolean("fav"), finalizado = o.optLong("fin"),
        anadido = o.optLong("anadido"), ultima = o.optLong("ultima"), mod = o.optLong("mod"), portada = o.optString("portada"), portadaUrl = o.optString("pu"),
        zoom = o.optDouble("ez", 1.0).toFloat(), ox = o.optDouble("eox", 0.0).toFloat(), oy = o.optDouble("eoy", 0.0).toFloat(),
        durHistoria = o.optInt("dh"), durExtras = o.optInt("de"), durCompleto = o.optInt("dc"),
        otrasClaves = o.optJSONArray("k")?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() } } ?: emptyList(),
        lanzadores = o.optJSONArray("ln")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(Lanzador::deJson) } } ?: emptyList(),
        propias = Horas.deJson(o.optJSONObject("hp")),
        ajenas = o.optJSONObject("ha")?.let { a -> a.keys().asSequence().associateWith { k -> Horas.deJson(a.optJSONObject(k)) } } ?: emptyMap(),
        externas = o.optJSONObject("hx")?.let { a -> a.keys().asSequence().associateWith { k -> Horas.deJson(a.optJSONObject(k)) } } ?: emptyMap(),
    )

    fun nuevoId(): String = "j_" + java.util.UUID.randomUUID().toString().replace("-", "").take(12)
}

/** Ajustes de Hiedra que no son del tema. De este dispositivo. */
object Ajustes {
    /** Emulador elegido por sistema: la etiqueta del comando; sin entrada = automático. */
    val emuladorPorSistema = HashMap<String, String>()
    var portadasLibretro by mutableStateOf(true)
    private fun p(c: Context) = c.getSharedPreferences("hiedra_ajustes", Context.MODE_PRIVATE)

    fun cargar(c: Context) {
        val p = p(c)
        portadasLibretro = p.getBoolean("libretro", true)
        emuladorPorSistema.clear()
        runCatching { JSONObject(p.getString("emus", "{}")!!).let { o -> o.keys().forEach { k -> emuladorPorSistema[k] = o.getString(k) } } }
    }

    fun guardar(c: Context) {
        p(c).edit().putBoolean("libretro", portadasLibretro).putString("emus", JSONObject(emuladorPorSistema as Map<*, *>).toString()).apply()
    }
}


/** Los grupos de ids que son el mismo juego (ver [Biblioteca.unirRepetidos]). Pura (se prueba). */
fun gruposRepetidos(juegos: List<Juego>): List<Set<String>> {
    val padre = HashMap<String, String>()
    fun raiz(x: String): String { var r = x; while (padre[r] != null && padre[r] != r) r = padre[r]!!; return r }
    fun junta(a: String, b: String) { val ra = raiz(a); val rb = raiz(b); if (ra != rb) padre[maxOf(ra, rb)] = minOf(ra, rb) }
    juegos.forEach { padre[it.id] = it.id }
    val porClave = HashMap<String, MutableList<Juego>>()
    juegos.forEach { j -> j.claves.forEach { k -> porClave.getOrPut(k) { ArrayList() }.add(j) } }
    for (l in porClave.values) for (i in l.indices) for (k in i + 1 until l.size) {
        val a = l[i]; val b = l[k]
        if (a.id == b.id) continue
        val pa = a.plataforma.trim().lowercase(); val pb = b.plataforma.trim().lowercase()
        if (pa.isBlank() || pb.isBlank() || pa == pb) junta(a.id, b.id)
    }
    return juegos.map { it.id }.groupBy { raiz(it) }.values.filter { it.size > 1 }.map { it.toSet() }
}
