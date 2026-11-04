# Hiedra: el modelo de datos (para aprobar antes de programar)

Hiedra es el lanzador de juegos del móvil hecho de una vez, sin la herencia de Pórtico. Fuera: paquete `com.gonzalo.hiedra`, nodo
`/users/<uid>/hiedra`, misma base que Cobalto (revista, tema, cuenta, kDrive, actualizador, widgets). Carmesí sigue funcionando hasta que
Hiedra lo tenga todo; lo tuyo se trae con una importación una sola vez (de Carmesí y, si falta algo, de Atrio).

## 1. La idea que lo cambia todo: el juego no es el lanzador

En Pórtico/Carmesí un juego **es** su lanzador: su identidad es `rom:gba:zelda`, `app:com.x`, `web:…`, `pc:…`. Por eso hubo que inventar
las fichas sin lanzador (`juego:título`), los alias, «unir con otro», «vincular», las gemelas… Cada vez que un juego tenía dos formas de
abrirse (la ROM en el móvil y el mismo juego en Steam), había que hacer trampas.

En Hiedra un **juego** es una cosa con identidad propia, y **cómo se abre** es una lista:

```
Juego
  id            «j_» + 12 hex, se crea una vez y viaja con la cuenta (como las obras de Escribir)
  titulo
  plataforma    texto libre (Switch, PS5, PC, Game Boy Advance…), lo que decide el menú pequeño si no hay lanzador
  estado        JUGANDO · PENDIENTE · PASADO · ABANDONADO
  favorito
  finalizado    ms (cuándo lo pasaste; 0 = no)
  anadido, ultima (ms)
  mod           ms del último cambio de lo editable (gana el más reciente entre dispositivos)
  portada       { url de internet (SteamGridDB, PC…) · tu imagen (en kDrive, por id) · encuadre zoom/ox/oy }
  dura          { historia, extras, completo } en minutos (de Atrio: HowLongToBeat/RAWG)
  claves        [ ] las claves de lanzador por las que se le conoce (ver 2): sirven para no duplicar al añadir, para importar y para que
                Azabache enlace lo que valoró con la clave antigua
  lanzadores    [ Lanzador ] (ver 2)
  horas         (ver 3)
```

Lo que no vuelve: la nota (es de Azabache), los apuntes de texto libre, la maqueta de casillas, «terminado» aparte del estado (es `estado =
PASADO` + `finalizado`), `claveFija`, `alias`, `pcKey/pcDev` sueltos (van en su lanzador).

## 2. Lanzadores

```
Lanzador
  tipo        ROM · APP · WEB · PC
  clave       rom:<sistema>:<archivo normalizado> · app:<paquete> · web:<dominio/ruta> · pc:<clave en la biblioteca del PC>
  ROM:  sistema, archivo (nombre sin extensión), emulador elegido para este juego ("" = el del sistema)
  APP:  paquete
  WEB:  url, orientacion (0 auto, 1 horizontal, 2 vertical)
  PC:   pcKey, pcDev (el PC de Atrio donde está)
```

- Un juego puede tener **varios lanzadores** (la ROM y el juego del PC; la app y la web). Sale en los menús pequeños que le toquen:
  PC · Emulación · Android · Web. Sin lanzador, su menú lo decide la plataforma (consolas → Emulación, como ahora).
- **Dónde está el archivo de una ROM es de cada dispositivo** y no viaja: cada móvil guarda `clave → uri` en su tabla local. Un juego con lanzador
  ROM cuyo archivo no está aquí sale como «en otro dispositivo» (igual que ahora), y al escanear una carpeta con ese archivo se enlaza solo por
  la clave. Lo mismo con las apps: se mira si el paquete está instalado.
- Al añadir una ROM, app o web, si ya hay un juego con esa clave se usa ese; si hay uno **con el mismo título normalizado** y sin ese lanzador, se
  le añade el lanzador (es lo que antes era «vincular» y «unir gemelas», pero sin fichas fantasma ni alias).
- Al tocar un juego con más de un lanzador se elige con qué abrirlo (y se recuerda el último).

## 3. Horas: fuentes, no trampas

```
horas
  propias:   { ms, veces, dias: { "AAAA-MM-DD": ms } }           lo jugado en ESTE dispositivo (ROM, app o web)
  ajenas:    { <dispositivo Hiedra>: { ms, veces, dias } }        lo de tus otros móviles (viene al sincronizar; se SUMA)
  externas:  { steam: { ms, dias }, "pc:<pc>": { ms, dias } }     lo de fuera (se queda el MÁXIMO, nunca se suma dos veces)
  total = propias + Σ ajenas + Σ externas
```

- **Steam**: las horas de la API de Steam son una fuente externa, no un dispositivo. Dos móviles que las traigan escriben lo mismo y gana el
  máximo. (En Carmesí se guardaban como «dispositivo steam» en `ajenosMs` y había que borrar las importadas de Atrio para no sumarlas dos veces.)
- **PC**: las horas del PC las publica Atrio del PC en `suite/lib/<pc>` (minutos). Hiedra las lee de ahí en directo como fuente `pc:<pc>` y no las
  vuelve a guardar ni a subir (en Carmesí los juegos del PC tenían `msPropios = 0` por decreto). Las partidas del PC que el móvil ve empezar y
  acabar van a `externas["pc:<pc>"].dias` (máximo por día, no suma).
- Corregir las horas a mano ajusta `propias.ms` (nunca por debajo de lo que sumen las demás), como ahora.

## 4. Lo demás que viaja

- **Sagas**: `{ id, nombre, juegos: [id…] en orden, mod }`. Por id de juego, no por clave.
- **Portada de inicio**: destacado (id o vacío), estantes en orden `{ estante, grande, activa }`, «Mis elegidos» (ids). Gana la más reciente.
- **Apariencia**: como en Cobalto.
- **Borrados**: `id → ms` (180 días), como ahora.
- **Carpetas de ROMs, emuladores por sistema, qué PC usar**: de cada dispositivo, no viajan.

## 5. Sincronización

La misma de Marfil/Cobalto (probada): cada dispositivo sube SU biblioteca a `dev/<dispositivo>/data` y baja la de los demás; los juegos se
emparejan por **id**; lo editable gana por `mod`; las horas propias de cada dispositivo se guardan por dispositivo y se suman; las externas,
máximo. Un juego nuevo en otro dispositivo se crea aquí con el mismo id. Nada de documento por juego ni mezcla a tres bandas: no hace falta
para una ficha de juego.

## 6. Qué se importa (una sola vez, desde Ajustes)

- **De Carmesí** (`/users/<uid>/portico/dev/*/data`, el formato de `Mezcla.kt` de Carmesí): cada juego pasa a un `Juego` con id nuevo,
  `claves = [k] + al`, un lanzador según su tipo (ROM/APP/WEB/PC) o ninguno (FICHA), estado, favorito, finalizado, plataforma, portada de
  internet, encuadre, duraciones; las horas de cada dispositivo de Carmesí pasan a la fuente externa `carmesi:<ese dispositivo>` (viaja
  con la cuenta y se mezcla por máximo: así ningún Hiedra las pierde aunque Carmesí ya no suba nada). Las de Steam y del PC no están en lo que
  Carmesí sube (eran locales de cada dispositivo): Hiedra las vuelve a leer de Steam y del PC. Las sagas (`sg`), la portada (`ini`) y la
  apariencia (`aj`) se traducen a ids / se aplican. Las
  portadas tuyas se bajan de kDrive (`nube/portadas/<kid>`); el índice de guardados y ROMs de kDrive (`nube/saves/<kid>`, `nube/roms/<kid>`) se
  copia al de Hiedra por el id del juego, así los archivos ya subidos siguen valiendo. Dos juegos de Carmesí con la misma clave entre dispositivos son uno. Carmesí no se
  toca.
- **De Atrio** (lo que no esté en Carmesí): como hace Carmesí hoy (fichas con `juego:<título>` como clave, estado, horas, duraciones, sagas).
- **De tu PC**: los juegos del PC entran solos si los has jugado o ya estaban; los demás, desde «Tu PC → Biblioteca → Añadir», como ahora.

## 7. Lo que Hiedra hereda tal cual (copiando archivos, como Cobalto hizo con Escribir)

Suite (PC en directo, órdenes, capturas, streaming, encender por red), Lanzador (emuladores, comandos, apps, webs), Escaneo de carpetas,
Catálogo de sistemas, Servicios (SteamGridDB, horas de Steam, claves desde Atrio), Encuadre, kDrive (portadas, ROMs y guardados),
Internet (buscador de portadas), portada de revista con mando, widgets. Todo adaptado al modelo nuevo, que es más simple que el viejo.

## 8. Para Azabache

El contrato está en `CONTRATO-DATOS.md`: `tiempo/<id>/<fuente>` ligero (con las claves del juego, para enlazar lo valorado con la clave
antigua), y nada más que leer. La valoración es de Azabache, enlazada por el `id` del juego (y por sus claves mientras haya valoraciones
antiguas por clave).

## 9. Lo que necesito que confirmes

1. **El juego con varios lanzadores** (punto 1 y 2): es el cambio de fondo. Si lo apruebas, fuera «vincular», «unir» y las fichas sin lanzador
   como cosa aparte.
2. **Las horas de Steam y del PC como fuentes con máximo** (punto 3).
3. **Importar desde Carmesí** y no desde Atrio como primera opción (punto 6).
4. **El icono**: súbelo al repo como `docs/iconos/hiedra-original.jpg` (como en Marfil y Carmesí) y lo saco de ahí; una hiedra verde muy oscura.
