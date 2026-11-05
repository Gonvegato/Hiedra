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
  y el mando; las secciones Jugando · Pendiente · Pasado · Abandonado · Sagas como páginas, con los menús pequeños PC · Emulación · Android · Web
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
[Jugando](docs/capturas/3_juegos.png) · [Pendiente](docs/capturas/3b_pendiente.png) · [Pasado](docs/capturas/3c_pasado.png) ·
[Pasado, solo Emulación](docs/capturas/3d_pasado_emulacion.png) · [Sagas](docs/capturas/3e_sagas.png) · [una saga](docs/capturas/3g_pagina_saga.png) ·
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
