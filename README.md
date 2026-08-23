# Romaster LiveWall Engine

**Motor y editor de live wallpapers para Android** con video de fondo, video overlay, reloj personalizable y reproducción reactiva al bloqueo del dispositivo.

> Crea fondos animados con capas, chroma key, audio independiente y comportamientos distintos cuando el teléfono está bloqueado o desbloqueado.

---

## Características

### Capas de composición (OpenGL ES 2)

| Capa | Descripción |
|------|-------------|
| **Video de fondo (Video-BG)** | Video principal a pantalla completa. Ajuste Stretch / Fit / Fill / Free, escala y posición. |
| **Video overlay (Video-OL)** | Segunda capa de video con posición, escala, rotación, opacidad y **chroma key** (color, umbral y suavizado). |
| **Reloj / fecha (Clock-OL)** | Overlay de reloj y fecha con tipografías propias, colores, formatos y posición. Visible también en pantalla de bloqueo (opcional). |

### Reproducción reactiva (Playback)

El **Overlay Loop Inteligente** permite que el video overlay se comporte distinto según el estado del dispositivo:

- **Cue Locked** — punto de tiempo y modo (LOOP / PAUSE) al **bloquear** el teléfono  
- **Cue Unlocked** — punto de tiempo y modo al **desbloquear**  
- **Simulación en el editor** — probar el modo bloqueado sin bloquear el dispositivo  

Ideal para personajes o escenas que “duermen” en la pantalla de bloqueo y “despiertan” al desbloquear.

### Audio

- Audio del propio video o **pista externa** por capa  
- Volumen independiente para fondo y overlay  

### Proyecto

- **Guardar** la configuración actual  
- **Importar / Exportar** proyectos en ZIP (`project.json` + videos + audio + fuentes)  
- **Nuevo proyecto** con valores por defecto  

### Preview en vivo

El editor muestra un preview OpenGL del mismo motor que usa el live wallpaper, para que lo que ves sea lo que se aplica al fondo de pantalla.

---

## Requisitos

| | |
|---|---|
| **minSdk** | 26 (Android 8.0) |
| **targetSdk** | 36 |
| **Kotlin** | 2.x |
| **Motor gráfico** | OpenGL ES 2.0 + EGL |
| **Video / audio** | AndroidX Media3 (ExoPlayer) |

---

## Instalación (desarrolladores)

```bash
git clone https://github.com/Romaster1985/Romaster-Live-Wallpaper-Engine.git
cd Romaster-Live-Wallpaper-Engine
./gradlew assembleDebug
```

Abre el proyecto en **Android Studio** (Ladybug o superior recomendado) y ejecuta la app en un dispositivo o emulador con soporte OpenGL ES 2.

Para usar el live wallpaper:

1. Abre la app y configura video / overlay / reloj  
2. Guarda el proyecto  
3. Ajustes del sistema → Fondo de pantalla → Live wallpapers → **Romaster LiveWall Engine (GL)**  

---

## Estructura del proyecto

```
app/src/main/java/com/romaster/livewallengine/
├── MainActivity.kt              # Editor (UI por pestañas)
├── model/                       # WallpaperProject, capas, clock, cues
├── project/                     # ProjectManager, DefaultProject
├── render/                      # GLRenderer, shaders, clock, EGL
├── video/                       # VideoPlayer, OverlayVideoPlayer, cues
├── audio/                       # WallpaperSoundPlayer, storage
├── wallpaper/                   # GLWallpaperService (+ Video / Canvas)
├── storage/                     # Import / export ZIP, directorios
├── ui/                          # WallpaperPreviewView, diálogos
├── font/                        # Fuentes del reloj
└── debug/                       # FileLogger
```

### Pestañas del editor

| Tab | Contenido |
|-----|-----------|
| **Video-BG** | Cargar video de fondo, modo de ajuste, escala, posición, audio |
| **Video-OL** | Overlay de video, chroma, transformaciones, audio |
| **Playback** | Overlay Loop Inteligente, cues locked/unlocked, simulación |
| **Clock-OL** | Reloj y fecha: formato, fuente, color, tamaño, posición, lock screen |
| **Proyecto** | Guardar, importar, exportar, nuevo proyecto, info del dispositivo |

---

## Formato de proyecto (ZIP)

Un proyecto exportado contiene aproximadamente:

```
proyecto.zip
├── project.json      # Configuración (capas, cues, clock, fades…)
├── videos/           # Videos de fondo y overlay
├── audio/            # Pistas externas (opcional)
└── fonts/            # Tipografías del reloj (opcional)
```

La importación descomprime en el almacenamiento interno de la app y recarga el proyecto activo.

---

## Servicios de wallpaper

La app registra tres servicios (útiles según el caso de uso):

| Servicio | Uso |
|----------|-----|
| **GLWallpaperService** | Motor completo OpenGL (recomendado) |
| **VideoWallpaperService** | Variante más simple basada en video |
| **CanvasWallpaperService** | Variante Canvas 2D |

El flujo principal de desarrollo y producción es **GL**.

---

## Galería de proyectos (próximamente)

Está previsto un botón **Seleccionar Proyecto** en la pestaña Proyecto que:

1. Liste previews desde la carpeta `LiveWallpapers/` del repositorio  
2. Muestre una grilla de imágenes PNG  
3. Permita **Descargar** el ZIP correspondiente y **Aplicar** el proyecto (mismo flujo que Importar)  

Estructura prevista en el repo:

```
LiveWallpapers/
├── MiFondo.zip
├── MiFondo.png
├── OtroTema.zip
└── OtroTema.png
```

---

## Stack técnico

- **Kotlin** + AndroidX (AppCompat, Material 3)  
- **OpenGL ES 2** (EGL, texturas externas OES, shaders de video y croma)  
- **Media3 ExoPlayer** para decodificación de video  
- **Kotlinx Serialization** para `project.json`  
- **ColorPickerView** (Skydoves) para selectores de color  
- Ciclo de vida del surface con **generaciones de RenderThread** para evitar races al bloquear/desbloquear o cambiar de app  

---

## Licencia

Consulta el archivo [LICENSE](LICENSE) en este repositorio.

---

## Autor

**Romaster** ([@Romaster1985](https://github.com/Romaster1985))

Ideas, issues y pull requests son bienvenidos.
