# Hiedra

El lanzador de juegos del móvil, hecho de nuevo. Hermana de [Atrio](https://github.com/gvegagato-prog/Atrio-Suite-2.0),
[Marfil](https://github.com/Gonvegato/lectura-app) y [Cobalto](https://github.com/Gonvegato/cobalto): mismo sistema de temas (paletas, letras,
«estilos»), la misma cuenta de Atrio y el mismo kDrive; otro paquete (`com.gonzalo.hiedra`) y otro hueco en la cuenta (`/users/<uid>/hiedra`), así
que **convive con Carmesí** en el mismo móvil hasta que lo tengas todo aquí. Sustituye a [Carmesí](https://github.com/gvegagato-prog/Android-Launcher)
(la que nació como Pórtico): hace lo mismo y lo que Carmesí no podía, con **un modelo de datos nuevo** (`docs/MODELO.md`) y una importación
de una sola vez.

## La idea

En Carmesí un juego **era** su lanzador (su ROM, su app, su web, su clave del PC), y por eso hacían falta fichas sin lanzador, alias, gemelas y
«unir». En Hiedra **un juego es una cosa con identidad propia** (un `id` que viaja con la cuenta) y **cómo se abre es una lista**: la ROM en el
móvil, la app, la web y el mismo juego en el PC, todo en una ficha, en los menús pequeños que toquen (Emulación y PC a la vez). Las horas se
guardan **por fuente**: las de este móvil, las de cada uno de tus otros móviles (se suman) y las externas (Steam, cada PC con Atrio, lo jugado en
Carmesí), que se mezclan por máximo y nunca se suman dos veces. Dónde está cada ROM es cosa de cada móvil y no viaja.

## 0.4.5

- **Juegos repetidos, juntados solos.** Al importar de Atrio o de Carmesí en dos móviles, cada uno creaba su copia del mismo juego (con
  su propio id): al quitar una, volvía la del otro móvil (pasaba, por ejemplo, con Baldur's Gate 3). Ahora, dos fichas con una clave en común
  (la misma ROM, app, juego de Steam o del PC, o el mismo título) y la misma plataforma se juntan en una (horas sumadas, el estado más
  avanzado) al abrir Hiedra y al sincronizar, y la copia sobrante se quita también en tus otros móviles.
- **La misma proporción de portada en toda la app (2:3)**, la de las carátulas verticales de SteamGridDB (600×900): la pantalla de inicio y
  las listas usaban 3:4 y la ficha 2:3, por eso en el inicio se cortaban arriba y abajo.
- El orden «Pasado» salía en todas las secciones: ahora se llama «Cuándo lo pasaste» y solo está en Pasados.
- **Fechas y cifras como en las otras tres** (`Formato.kt`, con las mismas pruebas en las cuatro): «16 oct 2026» en fichas y textos, «16/10/2026» en las tablas de datos y siempre con punto de millar («4.010»). Los meses van escritos en la app (Android, según la versión, les ponía punto: «oct.»).
- **«Jugar» es un botón grande** en la ficha, como «Seguir leyendo» en Marfil y «Seguir escribiendo» en Cobalto (la saga, al lado, en pequeño).
- Ajustes: la pestaña «Servicios» se llama **«Cuenta»**, como en Marfil y Cobalto (Servicios sigue dentro, como sección).
- «En Azabache» sin nota: una línea, «Sin valorar aún · Valorar en Azabache →» (antes eran dos que decían lo mismo).
- Los contadores de las pestañas van en tinta (más oscura la de la pestaña elegida), no en rojo: el rojo queda para lo que se toca.
- En las listas, la línea de debajo del título (plataforma, horas…) un punto más grande y más legible.
- **Las portadas de letra, con las telas de Marfil**: la misma paleta en las cuatro apps (un título, siempre el mismo color) y la inicial en la letra de los títulos. Antes cada sistema tenía su color.
- **La cabecera de la portada se pliega al bajar**: arriba solo se quedan el nombre y «Buscar · + Añadir»; el número, la fecha, las barras y el menú se van con la página.
- Debajo de «Hiedra · hecho en casa», en gris, la versión instalada.

## 0.4.4

- **Cambiar qué juego sigue un widget sin quitarlo**: déjalo pulsado en el escritorio y sale «Reconfigurar» (Android 12 o más, en los
  lanzadores que lo ofrecen, como el de Pixel): abre la misma lista que al ponerlo («Sigue jugando» y «En portada»). El menú del dedo pulsado es del escritorio,
  no de la app: lo único que una app puede hacer es decir que su widget se puede reconfigurar, y eso es lo que se ha hecho.

## 0.4.3

- **Las portadas que eliges, de verdad en todos tus móviles** (como los libros en Atrio). Antes solo se miraban cuando cambiaba la
  biblioteca de este móvil (y elegir una portada no contaba como cambio), un móvil que ya tenía una no recibía la nueva, y quitar una no
  llegaba a los demás. Ahora se miran cada vez que se sincroniza (al abrir Hiedra, al llegar algo de otro móvil y al cambiar algo aquí) y
  manda el cambio más reciente: la pones o la cambias en un móvil y llega a los demás; vuelves a la automática y se quita en todos.
- **Las que se habían «perdido» vuelven solas**: si el juego dejó de apuntar a su portada pero esa portada sigue en tu kDrive, se vuelve a
  poner (del archivo que aún está en el móvil, sin bajar nada, o bajándola). Si el archivo de una portada ya no está, se ve la de internet
  o la automática en vez de un hueco vacío.
- La portada de internet (SteamGridDB…) de un juego ya no se borra al mezclar con un móvil que no la tenía.

## 0.4.2

- Arreglo: al mezclar tu biblioteca con la de tus otros móviles se podía perder la portada que acababas de poner (o que se acababa de
  bajar de tu kDrive) si llegaba justo mientras se mezclaba. Ahora la portada se toma de como está en ese momento.

## 0.4.1 — las ROMs, de un móvil a otro

- **Al añadir una ROM** (sueltas o al recorrer una carpeta) se sube sola a tu kDrive, con wifi, sin esperar a volver a abrir Hiedra. Si
  añades más mientras sube, también. Desde su ficha se puede subir ya, también con datos.
- **En tu otro móvil, «Jugar» la baja**: si la ROM de un juego está en tu kDrive y no en ese móvil, «Jugar» (o su acceso directo)
  enseña de qué archivo se trata y cuánto pesa, la baja con su progreso a tu carpeta de ROMs (en la subcarpeta del sistema, «gba»,
  «snes»…, donde la encuentran tus emuladores), comprueba que ha llegado entera, la deja apuntada y abre el juego. La primera vez, si
  no tienes carpeta de ROMs en la que se pueda escribir, te pide una y se queda.
- **El jugador de Hiedra (GBA) la baja él solo** al abrir el juego, con su progreso en la pantalla de carga; sin carpeta de ROMs, la
  guarda dentro de Hiedra.
- **Ajustes → Avanzado → «Bajar las ROMs que faltan en este móvil»**: todas las que están en tu kDrive y aquí no, con wifi y en segundo
  plano (sigue aunque cierres Hiedra; si se corta, sigue por donde iba).
- Las carpetas de ROMs se guardan ahora con permiso de escritura (para dejar ahí las que bajas). Las que ya tenías solo con lectura:
  la primera bajada te pide elegirla otra vez.
- Los «días seguidos» siguen la regla común de la familia (`Racha.kt`, documentada en el contrato): un minuto jugado cuenta. Sin
  cambios para ti: era la misma regla.

Lo que no se ha podido probar en un móvil: la bajada y la subida de verdad contra tu kDrive (la parte de red es la misma que ya usaban
las ROMs desde la ficha; lo nuevo es cuándo se lanza y dónde se deja).

## 0.4.0 — el jugador interno (Fase A: Game Boy Advance)

Hiedra juega ella misma a los juegos de **GBA**, sin otra app: con el núcleo **mGBA** de libretro (vía LibretroDroid), y con lo que un
emulador de fuera no puede darle: sabe cuándo acabas, guarda donde estás al salir y lo sube a tu nube para seguir en otro móvil.

- **Hiedra es un emulador más** en la lista del sistema («Hiedra», el primero) y el automático para GBA. Si prefieres otro, se elige como
  siempre en la ficha o en Sistemas. En la ficha, en «Emulador», está el núcleo: cuál es, de cuándo, cuánto ocupa, y **Descargar ahora**,
  **Actualizar** (avisa de que los estados de la versión vieja pueden no cargar; la anterior se guarda) y **Quitar**. Si no lo tienes, se
  baja solo la primera vez que juegas (del buildbot de libretro, unos 400 KB).
- **Guardado rápido a la nube**: al salir del juego por cualquier camino (el menú, atrás, el botón de inicio, otra app encima) se hace un
  estado automático, se guarda la partida del cartucho y se sube a tu kDrive en segundo plano (con reintentos si no hay red). Mientras
  juegas, también cada pocos minutos. Al volver a jugar, aquí o en otro móvil, se carga el más reciente; si el de la nube lo hizo otra
  versión del núcleo, te pregunta. Además, tres huecos a mano en el menú (con miniatura).
- **La partida del cartucho** (.sav) se deja en la carpeta de guardados del sistema, con el nombre de la ROM: la misma que ya sincroniza
  Hiedra y que entienden los demás emuladores.
- **Avance rápido**: ×2, ×3, ×4 u ×8, mientras lo pulsas o con un toque para encender y otro para apagar, con o sin sonido. En el mando,
  R2 o el gatillo derecho (GBA no los usa); en la pantalla, el botón ▶▶. Arriba se ve a cuánto va y, si el móvil no llega, la velocidad
  real («▶▶ ×8 · real ×5,3»).
- **Controles táctiles** (RadialGamePad, el de Lemuroid): cruceta con diagonales, A y B, L y R, Start y Select. Opacidad, tamaño y vibración.
- **Mandos físicos**: en cuanto conectas uno (Bluetooth o USB), los controles táctiles se ocultan; si tocas la pantalla vuelven hasta que
  uses el mando. Los botones van **por posición** (el de la derecha es el A, como en la consola; lo que hacen RetroArch y Lemuroid) o, si
  lo prefieres, por la letra pintada. Cruceta o stick izquierdo, gatillos analógicos como botones. Cada mando guarda su configuración: en
  «Cambiar botones del mando» eliges una función y pulsas el botón. El botón central del mando (Home/Guide) o Select + Start abren el
  menú; con el mando se navega por él.
- **Menú en pausa**: continuar, guardar y cargar estado, avance rápido, mando y controles, imagen (Nítido, Suave, LCD, CRT; apaisada,
  vertical o la del móvil), captura (a Imágenes/Hiedra), reiniciar y salir.
- **Diagnóstico de mando**: el nombre, los ids y las últimas teclas y ejes que manda tu mando, para copiarlos y pegarlos si algo no va.
- Licencia: LibretroDroid y RadialGamePad son GPL-3.0, así que **Hiedra pasa a ser GPL-3.0** (archivo `LICENSE`). mGBA es MPL-2.0.

Lo que falta y lo que no se ha podido probar (honesto):

- **No se ha probado en un móvil de verdad**: ni el núcleo corriendo, ni el rendimiento, ni tus mandos (el GameSir que se divide y el
  8BitDo). Las pruebas del ordenador comprueban las reglas (mandos, avance rápido, qué estado se carga) y las capturas enseñan el táctil
  y el menú, pero el juego en sí (código nativo de ARM) no corre en el ordenador. Si un mando hace algo raro, el diagnóstico lo dice.
- La velocidad real del avance rápido se mide contando imágenes por segundo: es una aproximación.
- Solo GBA y solo móviles de 64 bits (arm64). Los demás sistemas 2D llegan en la Fase B; PSX, N64, DS, PSP y arcade, en la C.
- Sin pantalla de BIOS (GBA no la necesita), sin ajustes del núcleo y sin segundo jugador.
- Un estado solo lo entiende el mismo núcleo: no sirve para seguir en otro emulador (la partida del cartucho, sí).

## 0.3.14

- En las filas de datos (etiqueta · puntos · cifra), si no cabe todo en una línea, la etiqueta va entera arriba y la cifra debajo, en vez
  de cortar la etiqueta con puntos suspensivos (como en Azabache 1.2.8).

## 0.3.13

- El widget «Sigue jugando» alto y estrecho ya no corta su rótulo: ahí dice «Jugando».

## 0.3.12

- La fila de orden y filtros (Recientes · Nombre · …) lleva el mismo degradado en los bordes que las pestañas.
- El degradado de los bordes de las pestañas se pintaba fuera de la vista (no se veía): ahora sí. Y el título de la sección va en plural
  como su pestaña («Pasados»).

## 0.3.11

- Las secciones se llaman en plural, como en Marfil y Azabache: Jugando · Pendientes · Pasados · Abandonados · Sagas (también los estantes
  de la pantalla de inicio). El estado de cada juego en su ficha sigue en singular (Pendiente, Pasado…).
- Las pestañas (secciones, Ajustes) se desplazan solas solo cuando la elegida no se ve entera, y entonces la centran: antes se
  pegaba al borde izquierdo y la de al lado salía cortada.
  Y en los bordes, un degradado cuando hay más pestañas por ese lado.

## 0.3.10

- Arreglo del cierre automático de la sesión (0.3.9): la siguiente mirada se encadena detrás de la que está corriendo, en vez de
  reemplazarla (reemplazarla podía cancelar la que estaba a medias y dejar la sesión sin cerrar).

## 0.3.9

- Lo que no llegó a subirse (sin red al volver de jugar, o Android congeló Hiedra antes de acabar) lo sube un trabajo de fondo
  (SubidaWorker) en cuanto hay red. Si Hiedra ya lo subió, no hace nada.
- Con el permiso de uso, la sesión de juego se cierra sola cuando el emulador (o el juego) deja de estar delante, aunque no vuelvas a
  Hiedra: cada cuarto de hora se mira (SesionWorker) y, al cerrarla, las horas se apuntan y suben a tu cuenta al momento (Azabache las
  ve en su Diario). Antes solo se apuntaban al abrir Hiedra otra vez.

## 0.3.8

- **Arreglo del tiempo exacto**: con el permiso de uso, un emulador con varias pantallas (Dolphin, PPSSPP, DuckStation… pasan de su menú a la
  pantalla del juego) hacía que se perdiera casi toda la partida: Android apunta «detrás» la pantalla vieja un momento después de poner
  «delante» la nueva, y eso se tomaba por salir de la app. Ahora la app cuenta como delante mientras alguna de sus pantallas lo esté.

## 0.3.7

- Textos al día: lo que seguía diciendo «Atrio» para hablar del móvil (seguir sus colores en directo, copiar su apariencia de tu cuenta,
  «los mismos que en Atrio») dice ahora Azabache, que es quien está en el móvil. La cuenta sigue siendo la cuenta de Atrio.

## 0.3.6

- **Arreglo**: en Android 13 o más, los avisos del actualizador («Hiedra tiene una versión nueva», «Hiedra se ha actualizado, toca para abrirla»)
  no se veían nunca: faltaba el permiso de avisos. Ahora se declara y se concede desde Ajustes → Avanzado («Avisos de actualización»).

## 0.3.5

- Lo que Azabache sube (sus fichas, para las claves de los servicios y «En Azabache») no se vuelve a bajar si sus sellos no han cambiado:
  menos datos cada vez que Hiedra sincroniza.

## 0.3.4: la nota de Azabache, al momento

- Hiedra escucha el sello de Azabache: en cuanto valoras un juego allí, «En Azabache» en su ficha cambia sin salir ni esperar los 15 minutos.

## 0.3.3

- En la portada, «+ Añadir» en el acento, como en las listas y como en Marfil y Cobalto; el pie de Ajustes dice de quién es hermana.

## 0.3.2: el tiempo de juego exacto

- **Ajustes → Juegos → Tiempo de juego**: con el permiso «acceso al uso» de Android (se da desde ahí), al volver a Hiedra se cuenta solo lo
  que el emulador o el juego **estuvo delante**, y la sesión se reparte entre días según cuándo acabó de verdad. Sin el permiso, como siempre:
  todo el rato hasta volver (tope de 12 horas). Los juegos web ya se contaban dentro de Hiedra. `UsoTest`.

## 0.3.1

- El nodo ligero `tiempo` (lo que lee el Diario de Azabache) se sube **antes** del sello `stamps`: Azabache refresca al oír el sello y antes
  podía leer las horas viejas.

## 0.3.0: Ajustes en pestañas y la caja automática para Azabache

- **Ajustes** en pestañas, como Atrio: Apariencia · Juegos (sistemas y emuladores, portadas automáticas, pantalla de inicio, capturas del PC) ·
  Servicios (la cuenta de Atrio, traer de Carmesí y de Atrio, las claves) · Avanzado (kDrive y actualizaciones, copias). Lo mismo, sin tenerlo todo a la vista.
- **Portadas para Azabache**: si un juego no tiene portada tuya y «Portadas automáticas» está encendido, la dirección de su caja de libretro viaja
  en la biblioteca (`pa`), así Azabache enseña la misma caja que ves aquí. Las tuyas siguen viajando por `pu` (internet) o kDrive (galería).

## 0.2.1

- **Azabache**: una ficha que Azabache refleja de Hiedra y que aún no has valorado (todas las notas a 0, sin reseña) ya no sale en «En
  Azabache» como un 0: sigue «sin valorar».

## 0.2.0: la ficha cuenta más, y las claves se ven

- **Datos** en cada ficha, como en Marfil: cuándo salió, el estudio, el editor, los géneros, la nota de Metacritic y un resumen, más lo de aquí
  (plataforma, ROM, app, web, PC, saga, añadido, pasado, partidas). Vienen de la **tienda de Steam** (sin clave: por el appid si es un juego de
  Steam en tu PC, o buscándolo por nombre si coincide) y, si no está ahí, de **RAWG** (con tu clave; tiene también los juegos de consola de
  siempre). Se guardan dos semanas en el móvil; «Buscar de nuevo» los pide otra vez.
- **Lo que suele durar** se rellena solo en los juegos que no lo tengan: de **IGDB** (lo que registran sus jugadores: historia, con extras y al
  completo; con tu Client ID y secreto de Twitch) o, si no, la media de horas de RAWG. Se dice de dónde salió. Solo sale en los juegos que lo tienen.
- **Servicios**, en Ajustes: ahora se ve qué clave hay de cada servicio (la tuya, la de Atrio o ninguna) y el estado del kDrive. Y se arregla lo
  que fallaba: Hiedra no leía las claves de Atrio nunca (la función existía pero no se llamaba); ahora se traen al vincular la cuenta, en cada
  «Sincronizar ahora» y al abrir Ajustes. Hay campos para RAWG e IGDB.
- El **token de kDrive no viene de la cuenta**, a propósito: todo lo que hay en `/users/<uid>` lo puede leer tu pareja (modo pareja), y el token
  da acceso a todos tus archivos. Atrio tiene una prueba que lo prohíbe expresamente. Se pega en Ajustes → Actualizaciones, como en Atrio.

## 0.1.1

- **Icono nuevo**: una hoja de hiedra rellena (sus cinco lóbulos, el tallo y los nervios vaciados) en verde claro sobre verde muy oscuro, dibujada
  por `herramientas/icono-hiedra.py`.
- **La importación de Carmesí trae también la nube**: los guardados y las ROMs que Carmesí subió a tu kDrive pasan a ser los de Hiedra (se copia
  su índice de la cuenta al de Hiedra, por el id de cada juego; los archivos de kDrive son los mismos), sin volver a subir nada. Probado de punta a
  punta contra el Firebase de mentira (`SincroTest`).

## 0.1.0: la primera

- **Lo de Carmesí, entero, sobre el modelo nuevo**: portada de revista con el destacado, los estantes (orden, tamaño, a la vista, «Mis elegidos»)
  y el mando; las secciones Jugando · Pendientes · Pasados · Abandonados · Sagas como páginas, con los menús pequeños PC · Emulación · Android · Web
  y los órdenes; la ficha (estado, favorito, portada propia o de internet con su encuadre, lo que suele durar, la saga, «En Azabache» de solo
  lectura, la nube); añadir (carpetas de ROMs con escaneo, apps, webs, el PC); sistemas y emuladores; **Tu PC** (en directo, biblioteca, capturas,
  equipo; jugar en el PC o en streaming); las horas de Steam; guardados, ROMs y portadas por tu kDrive; el buscador de portadas; la apariencia;
  los widgets (*Sigue jugando*, *Estante*, *Portada*, *El siguiente*, *HIEDRA*, *HIEDRA en papel*).
- **La ficha enseña cada manera de abrirse** («Cómo se abre»: cada lanzador con su emulador, quitar, «+ Añadir») y el desglose de las horas por
  fuente (este móvil, otros móviles, Steam, el PC, Carmesí).
- **Traer mi biblioteca de Carmesí** (Ajustes → Cuenta): lee lo que Carmesí subió a tu cuenta (en Carmesí no cambia nada) y hace de cada juego un
  juego de Hiedra con su lanzador, sus claves, estado, favorito, plataforma, portada de internet, encuadre y duraciones; las horas de cada
  dispositivo de Carmesí quedan como fuente `carmesi:<dispositivo>`; las sagas, la portada de inicio y la apariencia se traducen; tus portadas se
  bajan de kDrive. Lo que ya tengas aquí se completa sin duplicarse. Se puede repetir. **Traer mis juegos de Atrio** sigue estando, para lo que no
  estuviera en Carmesí.
- **Para Azabache** (`docs/CONTRATO-DATOS.md`): el nodo ligero `tiempo/<id>/<fuente>` con las claves del juego (para enlazar lo valorado por la
  clave antigua), un solo nodo por fuente.
- **Icono**: una hoja de hiedra sobre verde muy oscuro, adaptable (`herramientas/icono-hiedra.py`).
- **Se actualiza sola** desde la carpeta «versiones» de tu kDrive (`Hiedra-<versión>.apk`).

## Capturas

Generadas en el ordenador con juegos de ejemplo (`CapturasTest`, `-Proborazzi.test.record=true`): [portada](docs/capturas/2_portada.png) ·
[estantes](docs/capturas/20b_inicio_estantes.png) · [editando la portada](docs/capturas/21_inicio_editando.png) · [en oscuro](docs/capturas/7_portada_oscuro.png) ·
[Jugando](docs/capturas/3_juegos.png) · [Pendientes](docs/capturas/3b_pendiente.png) · [Pasados](docs/capturas/3c_pasado.png) ·
[Pasados, solo Emulación](docs/capturas/3d_pasado_emulacion.png) · [Sagas](docs/capturas/3e_sagas.png) · [una saga](docs/capturas/3g_pagina_saga.png) ·
[ficha](docs/capturas/6_ficha.png) · [ficha con dos lanzadores](docs/capturas/6c_ficha_dos_lanzadores.png) · [ficha sin lanzador](docs/capturas/6b_ficha_sin_lanzador.png) ·
[ficha, casillas](docs/capturas/12_ficha_casillas.png) · [ficha, datos (RAWG)](docs/capturas/12b_ficha_datos.png) · [ficha, datos (Steam)](docs/capturas/12c_ficha_datos_steam.png) · [servicios](docs/capturas/31_ajustes_servicios.png) · [añadir](docs/capturas/9_anadir.png) · [sistemas](docs/capturas/4_sistemas.png) ·
[Tu PC](docs/capturas/23_tu_pc.png) · [biblioteca del PC](docs/capturas/24_tu_pc_biblioteca.png) · [ficha de un juego del PC](docs/capturas/26_ficha_pc.png) ·
[ajustes](docs/capturas/5_ajustes.png) · [ajustes: juegos](docs/capturas/5b_ajustes_juegos.png) · [ajustes: servicios](docs/capturas/5c_ajustes_servicios.png) · [ajustes: avanzado](docs/capturas/5d_ajustes_avanzado.png) · [ajustes con fondo](docs/capturas/30_ajustes_fondo_oscuro.png) · [vacía](docs/capturas/1_portada_vacia.png).
Los widgets, en `docs/widgets/`.

## Cómo se trabaja

- `ContratoTest` deja en `app/build/outputs/contrato/hiedra.json` lo que Hiedra sube a la cuenta tal cual; se copia a
  `azabache/herramientas/casos/hermanas/` y la prueba `ContratoHermanasTest` de Azabache comprueba que lo lee entero. Al revés,
  `herramientas/casos/azabache.json` es lo que sube Azabache (su `ContratoTest`) y `NotaAzabacheContratoTest` comprueba que «En Azabache» lo lee.
- Pruebas: `gradle :app:testDebugUnitTest` (0 fallos). Con `-Proborazzi.test.record=true` se graban las capturas (`app/build/outputs/roborazzi/`)
  y los widgets (`app/build/outputs/widgets/`, de donde salen las vistas previas `w_prev_*.png`). Las de sincronización y nube usan los servidores
  de mentira de Atrio (`Atrio-Suite-2.0/herramientas/fakefirebase.js` y `fakekdrive.js`, con `node`); sin ellos se saltan.
- Versión nueva: subir `versionCode`/`versionName` (`app/build.gradle.kts`) y `VERSION` (`PantEstilo.kt`), `gradle :app:assembleRelease`, y
  `python3 herramientas/subir-version.py Hiedra-<versión>.apk` la deja en tu kDrive para que los móviles se actualicen solos.
- El modelo y lo que viaja: `docs/MODELO.md`; lo que Azabache lee: `docs/CONTRATO-DATOS.md`.
