# Contrato de datos de Hiedra (lo que otras apps pueden leer)

Todo vive en tu cuenta de Firebase bajo `/users/<uid>/hiedra/`. Hiedra escribe; Azabache (y quien venga) **lee**. Nada de esto cambia Atrio ni
Carmesí. Es el hermano del contrato de Marfil (`lectura-app/docs/CONTRATO-DATOS.md`) y de Cobalto (`cobalto/docs/CONTRATO-DATOS.md`): mismos
nombres y mismas ideas. Estado: **propuesta**; sustituye al de Carmesí (`/portico`) cuando Hiedra esté entera.

## Identidad de un juego

Un juego se identifica por su **`id`** (`j_` + 12 hex), el mismo en todos los dispositivos (se crea una vez y viaja). Azabache debe enlazar la
valoración y la reseña de un juego por ese `id`.

Además, cada juego lleva sus **claves de lanzador** (`k`), por las que se le conoció y se le conoce:

| Lanzador | Clave | Ejemplo |
|---|---|---|
| ROM | `rom:<sistema>:<nombre del archivo normalizado>` | `rom:gba:pokemonesmeralda` |
| App de Android | `app:<paquete>` | `app:com.chucklefish.stardewvalley` |
| Web | `web:<dirección sin esquema ni www>` | `web:pokerogue.net` |
| Juego del PC (la biblioteca que Atrio del PC publica en `suite/lib`) | `pc:<clave en la biblioteca del PC>` | `pc:steam:1145360` |
| Ficha importada de Atrio o de Carmesí (sin lanzador) | `juego:<título normalizado>` | `juego:hades` |

`norm` = minúsculas, sin tildes y solo letras y números. Una valoración antigua de Azabache hecha por clave (`juego:hades`, `rom:…`) se
enlaza buscando esa clave en `k` de los juegos; a partir de ahí, por `id`.

## Nodos

| Nodo | Contenido | Quién escribe |
|---|---|---|
| `dev/<dispositivo>/data` | La biblioteca de ese dispositivo (texto JSON `v:1`): por juego `id`, `t` título, `pl` plataforma, `es` estado (`JUGANDO`/`PENDIENTE`/`PASADO`/`ABANDONADO`), `f` favorito, `fi` cuándo lo pasó (ms), `an` añadido, `ul` última vez, `mod`, `k` claves, `ln` lanzadores (`[{tp, k, s, a, pk, u, o, pck, pcd, em}]`), `pu` portada de internet, `pa` portada automática (la caja de libretro de la ROM; solo si no hay `pu`), `ez`/`eox`/`eoy` encuadre, `dh`/`de`/`dc` lo que suele durar (minutos), `msp` ms jugados en ese dispositivo, `vp` veces, `dd` ms por día (`"AAAA-MM-DD"` → ms), `ex` fuentes externas (`{steam: {ms, dd}, "pc:<pc>": {ms, dd}, "carmesi:<dispositivo>": {ms, v, dd}}`; `carmesi:` es lo jugado en Carmesí antes de pasarse a Hiedra, importado una vez); `borrados` (id → hora); `sg` sagas (`id`, `n` nombre, `j` ids en orden, `mod`) y `sgb` sagas borradas; `ini` la portada (`mod`, `d` destacado, `f` estantes `{e, g, on}`, `k` «Mis elegidos» (ids)); `aj` la apariencia. **Pesado**: no leerlo para listados. | cada Hiedra, solo el suyo |
| `stamps/<dispositivo>` | Hora de la última subida de ese dispositivo (servidor). | cada Hiedra |
| `tiempo/<id>/<fuente>` | `{t, k, m, p, total, days, fin}`: título, claves, menú (`pc`/`emulacion`/`android`/`web`), plataforma, ms jugados, `days` (`"AAAA-MM-DD"` → ms) y cuándo lo pasó. `<fuente>` es un dispositivo Hiedra (sus propias horas), `steam` (las de la API de Steam), `pc:<pc>` (las partidas del PC vistas desde el móvil) o `carmesi:<dispositivo>` (lo jugado en Carmesí en ese dispositivo, importado una vez). **Ligero**: es lo que debe leer un Diario. Cada fuente es un solo nodo: no hay nada repetido. Las horas totales del PC NO van aquí: están en `suite/lib/<PC>` (`m`, minutos), que ya publica Atrio del PC. | cada Hiedra (su fuente), cualquier Hiedra (`steam`, `pc:<pc>`) |
| `nube/portadas/<id>` | La portada que pusiste tú, en kDrive (`f.img`: `id` del archivo, `n` nombre, `h` hash, `s` tamaño, `dev`, `t` hora del servidor). Si la quitaste (volviste a la automática), no hay `f.img` y `x` es cuándo. Manda el cambio más reciente: lo que pones o quitas en un móvil llega a los demás. | cualquier Hiedra |
| `nube/roms/<id>`, `nube/saves/<id>` | La ROM y las partidas guardadas en kDrive. | cualquier Hiedra |
| `nube/states/<id>/f/<hueco>` | Los estados del jugador interno (el guardado rápido; `<hueco>` = `auto`, `1`, `2`, `3`), en kDrive (`Hiedra/Estados/<sistema>/<id>/<hueco>.state`): `n` nombre, `id` del archivo en kDrive, `h` hash, `s` tamaño, `dev`/`dn` dispositivo y su nombre, `nucleo` y `version` del núcleo (un estado solo lo entiende el mismo núcleo), `t` hora del servidor. **Solo para Hiedra**: no sirve a otros emuladores. La partida del cartucho (SRAM) va en `nube/saves`, como las de los demás emuladores. | cualquier Hiedra |

## Cómo leer el tiempo para un Diario

Para cada juego, lee `tiempo/<id>/*` y **suma** las fuentes (cada una trae solo lo suyo y no hay nada repetido). Lo del PC, de `suite/lib`.

Cuándo cambia: al volver a Hiedra tras jugar (la sesión se cierra y se sube antes del sello `stamps`) y, con el permiso de uso de
Android, también sin volver a Hiedra: cada cuarto de hora se mira si el emulador sigue delante y, cuando ya no, se cierra la sesión y se
sube (desde 0.3.9).

## La valoración

En Hiedra no se valora: la ficha solo LEE la general (0–100) y la reseña de Azabache, enlazadas por `id` (y por las claves antiguas).
Azabache es la dueña de la valoración.

## Lo que no está (a propósito)

- Apuntes de texto libre, nota de 0 a 10 y maqueta de casillas: no existen en Hiedra.
- Las capturas del PC se quedan en el móvil (y en la galería): no se publican aparte de lo que hace Atrio del PC en `suite/shots`.

## Verse entre hermanas

- **Verse entre hermanas**: desde Android 11 una app no ve a otra (`getLaunchIntentForPackage` da null) si no la declara en `<queries>` de su
  manifiesto. Cada hermana declara a las demás (`com.gonzalo.hiedra`, `com.gonzalo.marfil`, `com.gonzalo.cobalto`, `com.gonzalo.gamerate`);
  un test (`ManifiestoHermanasTest`) lo vigila. Sin esto, «Jugar en Hiedra», «Leer en Marfil» y «Escribir en Cobalto» no salen nunca en un móvil.

## Días seguidos (la racha)

La misma regla en Hiedra, Marfil, Cobalto y Azabache (`Racha.kt` en cada una, con las mismas pruebas, `RachaTest`):

- Un día **cuenta** si ese día jugaste o leíste **al menos un minuto**, o escribiste **al menos una palabra**. Cada app mira lo suyo
  (Hiedra lo jugado, Marfil lo leído, Cobalto lo escrito); Azabache, las tres a la vez (basta con una).
- La racha son los días seguidos que cuentan **hasta hoy**; si hoy todavía no cuenta, **hasta ayer** (no se rompe a primera hora).
- Los días son los del calendario del móvil (`"AAAA-MM-DD"` en los nodos de tiempo y de palabras).
