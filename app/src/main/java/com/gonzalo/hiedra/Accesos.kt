package com.gonzalo.hiedra

/*
 * Atajos de lectura sobre un juego: lo que sus lanzadores dicen, visto desde el juego. Son solo para LEER (en las pantallas y los widgets);
 * cambiar un lanzador se hace con [Juego.conLanzador] y [Juego.sinLanzador], y dónde está una ROM con [Archivos].
 */

val Juego.sistema: String get() = rom?.sistema.orEmpty()
val Juego.archivo: String get() = rom?.archivo.orEmpty()
val Juego.emulador: String get() = rom?.emulador.orEmpty()
val Juego.paquete: String get() = app?.paquete.orEmpty()
val Juego.url: String get() = web?.url.orEmpty()
val Juego.orientacion: Int get() = web?.orientacion ?: 0
val Juego.pcKey: String get() = pc?.pcKey.orEmpty()
val Juego.pcDev: String get() = pc?.pcDev.orEmpty()
/** El documento (content://) de la ROM en este móvil y su ruta real, si se sabe. */
val Juego.uri: String get() = rom?.let { Archivos.uri(it.clave) }.orEmpty()
val Juego.ruta: String get() = rom?.let { Archivos.ruta(it.clave) }.orEmpty()
/** La clave principal: la del lanzador con el que se abre; sin lanzador, la de antes o la de ficha de su título. */
val Juego.clave: String get() = principal?.clave ?: otrasClaves.firstOrNull() ?: claveFicha(titulo)
/** Las horas de este dispositivo (para las pantallas). */
val Juego.msPropios: Long get() = propias.ms
val Juego.diasPropios: Map<String, Long> get() = propias.dias
/** El tipo del lanzador principal (null = sin lanzador). */
val Juego.tipo: TipoLanzador? get() = principal?.tipo
