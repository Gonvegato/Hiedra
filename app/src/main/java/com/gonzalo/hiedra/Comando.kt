package com.gonzalo.hiedra

/*
 * Intérprete de los comandos de lanzamiento de ES-DE para Android (es_systems.xml), por ejemplo:
 *   %EMULATOR_MELONDS% %ACTION%=me.magnum.melonds.LAUNCH_ROM %EXTRA_uri%=%ROMSAF%
 * Aquí solo se interpreta el texto y se obtiene una [Especificacion] (sin tocar nada de Android: se prueba en el ordenador);
 * Lanzador.kt la convierte en un Intent.
 */

class Extra(val clave: String, val valor: Any)

/** Lo que hay que lanzar: componente, acción, datos, extras y avisos. */
class Especificacion(
    val paquete: String, val clase: String?, val accion: String?, val datos: String?, val mime: String?,
    val categorias: List<String>, val extras: List<Extra>, val limpiarTarea: Boolean, val limpiarArriba: Boolean,
    /** Alguna parte del comando lleva el documento de la ROM (content://…): hay que dar permiso de lectura al emulador. */
    val conceder: String?,
    val emulador: String,
)

/** Lo que el sistema le cuenta al intérprete. */
interface Entorno {
    fun instalado(paquete: String): Boolean
    /** /data/data */
    val internos: String
    /** /storage/emulated/0 */
    val externos: String
}

/** La ROM: el documento (content://) y, si se sabe, su ruta real. */
class RomRef(val uri: String, val ruta: String?)

sealed class Resultado {
    class Ok(val spec: Especificacion) : Resultado()
    /** No se puede lanzar: [motivo] legible; [emulador] el nombre de la regla (para buscarlo) y [paquete] el primero que se conoce. */
    class Falta(val motivo: String, val emulador: String? = null, val paquete: String? = null) : Resultado()
}

object Comando {
    private val CORTE = Regex("\\s+(?=%[A-Za-z_]+%)")
    private val NO_SOPORTADOS = setOf("GAMEDIRRAW", "ROMPATHRAW", "ROMRAW", "INJECT", "BASENAME", "ANDROIDAPP", "FILEINJECT", "ROMPATH")
    private val TOKEN = Regex("%([A-Za-z_]+?)%")

    private fun trozos(p: String): List<String> = p.trim().split(CORTE).filter { it.isNotBlank() }

    /** Nombres de las reglas de emulador que usa el comando (RETROARCH, MELONDS…). */
    fun emuladoresDe(p: String): List<String> = Regex("%EMULATOR_([^%]+)%").findAll(p).map { it.groupValues[1] }.toList()

    /** ¿Puede Hiedra interpretarlo? (algunos comandos de ES-DE usan cosas solo de su aplicación) */
    fun soportado(p: String): Boolean =
        TOKEN.findAll(p).none { it.groupValues[1] in NO_SOPORTADOS || it.groupValues[1].startsWith("EXTRAARRAY") }

    /** ¿Va por RetroArch (hace falta que tenga el núcleo descargado)? */
    fun esRetroArch(p: String) = "RETROARCH" in emuladoresDe(p)

    fun resolver(plantilla: String, rom: RomRef, env: Entorno): Resultado {
        if (!soportado(plantilla)) return Resultado.Falta("Este comando solo funciona dentro de ES-DE")
        var comp: Pair<String, String?>? = null
        var emu = ""
        // 1) el emulador: la primera de sus apps (paquete/actividad) que esté instalada
        val nombre = emuladoresDe(plantilla).firstOrNull() ?: return Resultado.Falta("El comando no dice qué emulador usar")
        emu = nombre
        val entradas = Catalogo.emus[nombre].orEmpty()
        if (entradas.isEmpty()) return Resultado.Falta("Emulador desconocido: $nombre", nombre)
        val primero = entradas.first().substringBefore('/')
        for (e in entradas) {
            val pk = e.substringBefore('/'); var cl = e.substringAfter('/', "").ifEmpty { null }
            if (cl != null && cl.startsWith(".")) cl = pk + cl
            if (env.instalado(pk)) { comp = pk to cl; break }
        }
        if (comp == null) return Resultado.Falta("No está instalado", nombre, primero)
        val paquete = comp.first

        fun sustituir(v: String): String? {
            var falta = false
            val r = TOKEN.replace(v) { m ->
                when (m.groupValues[1]) {
                    "ROM" -> rom.ruta ?: run { falta = true; "" }
                    "ROMSAF", "ROMPROVIDER" -> rom.uri
                    "ANDROIDPACKAGE" -> paquete
                    "INTERNALDATA" -> env.internos
                    "EXTERNALDATA" -> env.externos
                    else -> m.value
                }
            }
            return if (falta) null else r
        }

        var accion: String? = null; var datos: String? = null; var mime: String? = null
        val cats = ArrayList<String>(); val extras = ArrayList<Extra>()
        var tarea = false; var arriba = false; var conceder: String? = null
        for (t in trozos(plantilla)) {
            val m = Regex("^%([A-Za-z_]+?)%(?:=(.*))?$", RegexOption.DOT_MATCHES_ALL).matchEntire(t.trim()) ?: continue
            val k = m.groupValues[1]; val vRaw = m.groups[2]?.value
            if (k.startsWith("EMULATOR_")) continue
            if (k == "ACTIVITY_CLEAR_TASK") { tarea = true; continue }
            if (k == "ACTIVITY_CLEAR_TOP") { arriba = true; continue }
            if (vRaw == null) continue
            val v = sustituir(vRaw) ?: return Resultado.Falta("Este emulador necesita la ruta del archivo, y esta ROM no la tiene (elígela desde una carpeta del almacenamiento del móvil)")
            if (vRaw.contains("%ROMSAF%") || vRaw.contains("%ROMPROVIDER%")) conceder = rom.uri
            when {
                k == "ACTION" -> accion = v
                k == "DATA" -> datos = v
                k == "MIMETYPE" -> mime = v
                k == "CATEGORY" -> cats.add(v)
                k.startsWith("EXTRABOOL_") -> extras.add(Extra(k.removePrefix("EXTRABOOL_"), v.equals("true", true)))
                k.startsWith("EXTRAINTEGER_") -> extras.add(Extra(k.removePrefix("EXTRAINTEGER_"), v.toIntOrNull() ?: 0))
                k.startsWith("EXTRA_") -> extras.add(Extra(k.removePrefix("EXTRA_"), v))
            }
        }
        return Resultado.Ok(Especificacion(paquete, comp.second, accion, datos, mime, cats, extras, tarea, arriba, conceder, emu))
    }

    /** Ruta real de un documento del almacenamiento del móvil: "primary:Roms/gba/x.gba" → /storage/emulated/0/Roms/gba/x.gba. */
    fun rutaDeDocId(docId: String, externos: String = "/storage/emulated/0"): String? {
        val i = docId.indexOf(':'); if (i <= 0) return null
        val vol = docId.substring(0, i); val resto = docId.substring(i + 1)
        if (resto.isEmpty() || resto.contains("..")) return null
        return when {
            vol.equals("primary", true) -> "$externos/$resto"
            vol.matches(Regex("[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}")) -> "/storage/$vol/$resto"
            else -> null
        }
    }
}
