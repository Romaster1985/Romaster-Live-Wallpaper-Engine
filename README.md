# Romaster LiveWall Engine

<p align="center">
  <img src="pictures/imagen_10.png" alt="Romaster LiveWall Engine" width="360"/>
</p>

**Motor y editor de live wallpapers para Android** con video de fondo, video overlay, capas de imagen, **widgets con fórmulas (estilo KLWP)**, reloj avanzado (cristal, reflejo, fuentes variables), reproducción reactiva al bloqueo, galerías online y proyectos importables/exportables.

> Crea fondos animados con varias capas, chroma key, audio independiente, ping-pong, crossfade de loops, soft start, widgets de hora/batería/clima y comportamientos distintos cuando el teléfono está bloqueado o desbloqueado.

**Versión actual:** `1.0.0-alpha2` (versionCode 2)

<p align="center">
  <img src="pictures/imagen_1.png" alt="Editor con preview en vivo" width="280"/>
  &nbsp;
  <img src="pictures/imagen_2.png" alt="Live wallpaper en el home / lock screen" width="280"/>
</p>

---

## Características

### Capas de composición (OpenGL ES 2)

| Capa | Descripción |
|------|-------------|
| **Video de fondo (Video-BG)** | Video principal a pantalla completa. Stretch / Fit / Fill / Free, escala, posición, audio. Importar **video**, **GIF** (→ MP4) o **pantalla negra**; reset a valores de fábrica. |
| **Video overlay (Video-OL)** | Segunda capa de video: posición, escala, rotación, opacidad, **chroma key**, audio, opción de ocultar en pantalla de bloqueo. Mismas opciones de carga/GIF/negro/reset. |
| **Reloj / fecha (Clock-OL)** | Reloj y fecha con tipografías (fijas y **variables**), colores, bordes, formatos (incluido **HH/MM vertical**), cristal, textura, reflejo, relieve, desenfoque de fondo y soft start. |
| **Imágenes (Pics-OL)** | Capas de imagen/GIF independientes: transparencia, posición, zoom, rotación, orden Z respecto a Video-BG, Video-OL y Clock-OL, soft start y visibilidad en lock screen. |

<p align="center">
  <img src="pictures/imagen_3.png" alt="Composición de capas" width="320"/>
</p>

### Reproducción reactiva (Playback)

El **Overlay Loop Inteligente** permite que el video overlay se comporte distinto según el estado del dispositivo:

- **Cue Locked** — punto de tiempo y modo (LOOP / PAUSE) al **bloquear**
- **Cue Unlocked** — punto de tiempo y modo al **desbloquear**
- **Ping-Pong** — ida y vuelta fluida con clip de reversa preprocesado (exportable en el ZIP)
- **Transición suave de loops** — dual MediaPlayer con intercambio de roles:
  - **Normal** — fade-out + fade-in simultáneos (compatible con proyectos anteriores)
  - **Altern** — capa de atrás al 100 % y fade-out del video de encima (sin transparencia intermedia)
- **Soft Start** — fade-in configurable al volver a ser visible (video BG, OL, reloj, capas de imagen y widgets)
- **Delay Start** — retrasos por módulo; con desenfoque del reloj, el reloj espera a que el resto termine su soft start
- **Simulación de bloqueo** en el editor (mismo comportamiento que el dispositivo real)

Ideal para personajes o escenas que “duermen” en la pantalla de bloqueo y “despiertan” al desbloquear.

<p align="center">
  <img src="pictures/imagen_4.png" alt="Pestaña Playback" width="320"/>
</p>

### Reloj avanzado (Clock-OL)

- Formatos de hora: `HH:MM`, **`HH/MM (Vertical)`** con separación ajustable, `HH:MM:SS`, `HH:MM AM/PM`
- Formatos de fecha, intercambiar hora/fecha, superposición y alineación
- **Fuentes** locales + **Galería de Fuentes** (repo de temas) + limpiar fuentes no usadas
- **Fuentes variables** (Roboto Flex, SF Pro variables, etc.): weight, width, optical size, grade y ejes extendidos (GRAD, XOPQ, XTRA, YOPQ, …)
- Tamaños hasta 1000, deformación vertical, colores con alfa, **bordes** solo hacia afuera
- **Modo cristal** + textura PNG dentro del glifo
- **Reflejo** (intensidad, separación, cantidad de degradado)
- **Relieve** de luz
- **Desenfoque de fondo** dentro de los glifos (Off / Soft / Med / Strong), procesado en segundo plano
- Soft start y “habilitar en pantalla de bloqueo”
- Cards expandibles (mostrar [+] / ocultar [-])

<p align="center">
  <img src="pictures/imagen_5.png" alt="Reloj formato vertical" width="240"/>
  &nbsp;
  <img src="pictures/imagen_6.png" alt="Reloj con cristal y reflejo" width="240"/>
</p>

### Capas de imagen (Pics-OL)

- Añadir capas con el botón **+**
- Carga de PNG, JPEG, WebP, GIF, etc.
- Transparencia, X/Y, zoom, rotación
- Orden de apilado respecto a Video-BG, Video-OL, Clock-OL y otras imágenes (diálogo subir/bajar)
- Soft start, **Deshabilitar en pantalla de bloqueo** y **Deshabilitar en Launcher (Desbloqueado)**
- Reset de sliders a valores por defecto

<p align="center">
  <img src="pictures/imagen_7.png" alt="Pestaña Pics-OL" width="320"/>
</p>

### Widgets con fórmulas (Widgets-OL)

Capa de texto/fórmulas inspirada en KLWP/KWGT:

- Fórmulas entre `$ ... $` con funciones `df`, `bi`, `wi`, `if`, operadores matemáticos y lógicos
- Fuentes de texto + **fuentes de íconos** (TTF+JSON estilo IcoMoon), galería online y reasignación de nombres climáticos
- Tamaño, color, borde, rotación, opacidad, **alineación 3×3** (LEFT/CENTER/RIGHT × TOP/MIDDLE/BOTTOM)
- Orden Z respecto a Video-BG, Video-OL, Clock-OL, imágenes y otros widgets
- Soft start, delay, deshabilitar en lock screen y en launcher
- Variaciones de fuente variable (mismos ejes que Clock-OL)
- Limpiar fuentes de texto e íconos no usadas (conserva las del reloj y widgets en uso)

Ver tabla completa de fórmulas más abajo.

### Video-BG / Video-OL — activar o desactivar capa

Checkbox en el título de cada card (**activado por defecto**). Al destildar, esa capa **no se renderiza** y su player se pausa (menos carga de CPU/GPU). Útil en lugar de cargar un video negro vacío.

### Audio

- Audio del propio video o **pista externa** por capa (fondo y overlay)
- Volumen independiente y mute

### Proyectos y galerías

- **Guardar** configuración actual
- **Importar / Exportar** ZIP (`project.json` + videos + reversas + audio + fuentes + imágenes + preview)
- **Nuevo proyecto** (factory reset)
- **Galería de Wallpapers** — previews desde el repo de temas; descargar y aplicar
- **Galería de Fuentes** — tipografías online con preview y filtro por nombre
- Videos de reversa (ping-pong) incluidos en export/import
- Rectificado opcional de videos al importar (MP4 ligero, bucles más suaves)
- Importar **GIF → MP4** (opción de relleno chroma para transparencias)

<p align="center">
  <img src="pictures/imagen_8.png" alt="Galería de wallpapers" width="280"/>
  &nbsp;
  <img src="pictures/imagen_9.png" alt="Galería de fuentes" width="280"/>
</p>

### Preview en vivo y splash

- Preview OpenGL del **mismo motor** que el live wallpaper (métricas alineadas a la pantalla real)
- **Splash screen** al abrir la app (`res/drawable/splash.png`)

---

## Requisitos

| | |
|---|---|
| **minSdk** | 26 (Android 8.0) |
| **targetSdk** | 36 |
| **Kotlin** | 2.x |
| **Motor gráfico** | OpenGL ES 2.0 + EGL |
| **Video / audio** | AndroidX Media3 (ExoPlayer) + MediaPlayer |

---

## Instalación (desarrolladores)

```bash
git clone https://github.com/Romaster1985/Romaster-Live-Wallpaper-Engine.git
cd Romaster-Live-Wallpaper-Engine
./gradlew assembleDebug
```

Abre el proyecto en **Android Studio** (Ladybug o superior) y ejecuta en un dispositivo o emulador con OpenGL ES 2.

**Temas y fuentes públicos** (galerías de la app):

- [Romaster-Live-Wallpaper-Themes](https://github.com/Romaster1985/Romaster-Live-Wallpaper-Themes)

Para usar el live wallpaper:

1. Abre la app y configura video / overlay / reloj / imágenes  
2. Guarda el proyecto  
3. Ajustes del sistema → Fondo de pantalla → Live wallpapers → **Romaster LiveWall Engine (GL)**  

---

## Estructura del código

```
app/src/main/java/com/romaster/livewallengine/
├── MainActivity.kt / SplashActivity.kt
├── model/          # WallpaperProject, capas, clock, cues, formatos
├── project/        # ProjectManager, DefaultProject
├── render/         # GL*, ClockRenderer, shaders, blur, capas de imagen
├── video/          # players, cues, transcoder, GIF→MP4, reverse / ping-pong
├── audio/
├── wallpaper/      # GLWallpaperService (+ Video / Canvas)
├── storage/        # ZIP import/export, directorios
├── gallery/        # ProjectGallery, FontGallery, IconFontGallery
├── formula/        # FormulaEngine (df, bi, wi, if, math)
├── weather/        # WeatherProvider (Open-Meteo)
├── ui/             # WallpaperPreviewView, diálogos
├── font/           # FontStorage, IconFontStorage, FontManager (variables)
└── debug/          # FileLogger
```

### Pestañas del editor

| Tab | Contenido |
|-----|-----------|
| **Video-BG** | Video / GIF / pantalla negra, ajuste, escala, posición, audio, reset de tab |
| **Video-OL** | Overlay, chroma, transformaciones, lock screen, audio, reset de tab |
| **Playback** | Cues, ping-pong, transición suave (Normal/Altern), soft/delay start, simulación de bloqueo |
| **Clock-OL** | Formatos, fuentes, variables, cristal, reflejo, blur, posición, bordes |
| **Pics-OL** | Capas de imagen, orden Z, soft start, lock/launcher |
| **Widgets-OL** | Fórmulas KLWP-like, fuentes texto/íconos, alineación 3×3, clima, batería |
| **Proyecto** | Guardar, importar, exportar, nuevo, galería de wallpapers, Acerca de |

---

## Formato de proyecto (ZIP)

```
proyecto.zip
├── project.json       # Capas, cues, clock, fades, ping-pong, imágenes…
├── preview.png        # Miniatura del diseño
├── videos/            # wallpaper_video, overlay_video, reversas
├── audio/             # Pistas externas (opcional)
├── fonts/             # Tipografías de texto (reloj / widgets)
├── icons/             # Fuentes de íconos (TTF+JSON) de widgets
└── images/            # Capas Pics-OL (opcional)
```

---

## Servicios de wallpaper

| Servicio | Uso |
|----------|-----|
| **GLWallpaperService** | Motor completo OpenGL (**recomendado**) |
| **VideoWallpaperService** | Variante más simple basada en video |
| **CanvasWallpaperService** | Variante Canvas 2D |

---


---

## Motor de fórmulas (Widgets-OL)

Sintaxis inspirada en KLWP. El texto **fuera** de `$...$` es literal (incluye saltos de línea con Enter). **Dentro** de cada par `$...$` se evalúan funciones, operadores y condiciones.

### Funciones

| Fórmula | Resultado / descripción |
|---------|-------------------------|
| `$df(hh:mm)$` | Hora 24 h con dos dígitos (ej. `17:05`) |
| `$df(HH:mm)$` | Igual que `hh`/`HH` (hora 0–23 rellenada) |
| `$df(h:m)$` | Hora y minutos sin ceros a la izquierda |
| `$df(hh:mm:ss)$` / `$df(ss)$` | Con segundos |
| `$df(EEEE)$` | Día de la semana largo (ej. `jueves`) |
| `$df(EEE)$` | Día de la semana corto |
| `$df(dd)$` / `$df(d)$` | Día del mes (con/sin cero) |
| `$df(MMMM)$` / `$df(MMM)$` | Mes largo / corto |
| `$df(yyyy)$` / `$df(yy)$` | Año 4 / 2 dígitos |
| `$df(EEEE), df(dd) df(MMM) df(yyyy)$` | Varias piezas en un solo bloque |
| `$df(yyyy)+4$` | Aritmética sobre el resultado (→ año + 4) |
| `$bi(level)$` | Nivel de batería 0–100 |
| `$bi(level)$%` | Nivel con sufijo literal `%` |
| `$bi(charging)$` | `1` cargando/lleno, `0` si no |
| `$bi(temp)$` / `$bi(tempc)$` | Temperatura de batería (°C, entero) |
| `$bi(volt)$` | Voltaje de batería (mV del sistema) |
| `$wi(temp)$` / `$wi(tempc)$` | Temperatura ambiente °C (Open-Meteo) |
| `$wi(tempf)$` | Temperatura °F |
| `$wi(icon)$` | Clave de ícono KLWP (`CLEAR`, `RAIN`, `TSTORM`, …) |
| `$wi(humidity)$` | Humedad relativa % |
| `$wi(wind)$` | Viento |
| `$wi(feels)$` / `$wi(feelsc)$` | Sensación térmica |
| `$if(cond, sí, no)$` | Condicional; el branch elegido se evalúa de nuevo |
| `$"texto"$` | Literal: no se calcula (ej. `$"20*2+5"$` → `20*2+5`) |

### Operadores y condiciones

| Operador | Uso |
|----------|-----|
| `+` `-` `*` `/` | Aritmética dentro de `$...$` |
| `( )` | Agrupación |
| `==` `!=` `<` `>` `<=` `>=` | Comparación (números o texto) |
| `&` / `and` | AND lógico en condiciones de `if` |
| `\|` / `or` | OR lógico |

**Ejemplos**

```
$df(hh:mm)$
Bat: $bi(level)$%
$if(bi(level)<20, LOW, OK)$
$wi(icon)$   → RAIN  (o el glifo si usás fuente de íconos)
$df(yyyy)+4$
```

Claves de clima (`$wi(icon)$`) alineadas con KLWP:  
`CLEAR`, `PCLOUDY`, `MCLOUDY`, `FOG`, `WINDY`, `RAIN`, `SHOWER`, `SLEET`, `SNOW`, `LSNOW`, `HAIL`, `TSTORM`, `TSHOWER`, `TORNADO`, `UNKNOWN`.

---

## Stack técnico

- **Kotlin** + AndroidX (AppCompat, Material 3)  
- **OpenGL ES 2** (EGL, texturas OES, shaders de video, croma y cristal)  
- **Media3 ExoPlayer** + MediaPlayer  
- **Kotlinx Serialization** (`project.json`)  
- **ColorPickerView** (Skydoves)  
- Procesado en segundo plano del reloj (bitmap buffer) y blur de backdrop  
- Ciclo de vida del surface con generaciones de **RenderThread**  

---

## Licencia

Consulta el archivo [LICENSE](LICENSE).

Este proyecto incluye **ColorPickerView** (skydoves) bajo Apache License 2.0.

```
Copyright 2026 Román Ignacio Romero (Romaster)
Licensed under the Apache License 2.0
```

---

## Autor

**Romaster** ([@Romaster1985](https://github.com/Romaster1985))

Ideas, issues y pull requests son bienvenidos.
