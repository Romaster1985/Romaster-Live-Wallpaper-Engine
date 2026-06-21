# Romaster LiveWall Engine
# Reglas iniciales

# Mantener nombres de modelos de proyecto
-keep class com.romaster.livewallengine.model.** { *; }

# Mantener WallpaperService
-keep class com.romaster.livewallengine.wallpaper.** { *; }

# Mantener clases públicas
-keep public class * extends android.app.Service

# Mantener clases públicas
-keep public class * extends android.app.Activity

# Mantener información de anotaciones
-keepattributes *Annotation*

# Mantener firmas genéricas
-keepattributes Signature