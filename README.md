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
- **Icono**: una hoja de hiedra sobre verde muy oscuro, adaptable (`herramientas/icono-hiedra.py`; si hay `docs/iconos/hiedra-original.jpg`
  lo saca de ahí, y si no la dibuja).
- **Se actualiza sola** desde la carpeta «versiones» de tu kDrive (`Hiedra-<versión>.apk`).

## Capturas

Generadas en el ordenador con juegos de ejemplo (`CapturasTest`, `-Proborazzi.test.record=true`): [portada](docs/capturas/2_portada.png) ·
[estantes](docs/capturas/20b_inicio_estantes.png) · [editando la portada](docs/capturas/21_inicio_editando.png) · [en oscuro](docs/capturas/7_portada_oscuro.png) ·
[Jugando](docs/capturas/3_juegos.png) · [Pendiente](docs/capturas/3b_pendiente.png) · [Pasado](docs/capturas/3c_pasado.png) ·
[Pasado, solo Emulación](docs/capturas/3d_pasado_emulacion.png) · [Sagas](docs/capturas/3e_sagas.png) · [una saga](docs/capturas/3g_pagina_saga.png) ·
[ficha](docs/capturas/6_ficha.png) · [ficha con dos lanzadores](docs/capturas/6c_ficha_dos_lanzadores.png) · [ficha sin lanzador](docs/capturas/6b_ficha_sin_lanzador.png) ·
[ficha, casillas](docs/capturas/12_ficha_casillas.png) · [añadir](docs/capturas/9_anadir.png) · [sistemas](docs/capturas/4_sistemas.png) ·
[Tu PC](docs/capturas/23_tu_pc.png) · [biblioteca del PC](docs/capturas/24_tu_pc_biblioteca.png) · [ficha de un juego del PC](docs/capturas/26_ficha_pc.png) ·
[ajustes](docs/capturas/5_ajustes.png) · [ajustes con fondo](docs/capturas/30_ajustes_fondo_oscuro.png) · [vacía](docs/capturas/1_portada_vacia.png).
Los widgets, en `docs/widgets/`.

## Cómo se trabaja

- Pruebas: `gradle :app:testDebugUnitTest` (0 fallos). Con `-Proborazzi.test.record=true` se graban las capturas (`app/build/outputs/roborazzi/`)
  y los widgets (`app/build/outputs/widgets/`, de donde salen las vistas previas `w_prev_*.png`). Las de sincronización y nube usan los servidores
  de mentira de Atrio (`Atrio-Suite-2.0/herramientas/fakefirebase.js` y `fakekdrive.js`, con `node`); sin ellos se saltan.
- Versión nueva: subir `versionCode`/`versionName` (`app/build.gradle.kts`) y `VERSION` (`PantEstilo.kt`), `gradle :app:assembleRelease`, y
  `python3 herramientas/subir-version.py Hiedra-<versión>.apk` la deja en tu kDrive para que los móviles se actualicen solos.
- El modelo y lo que viaja: `docs/MODELO.md`; lo que Azabache lee: `docs/CONTRATO-DATOS.md`.
