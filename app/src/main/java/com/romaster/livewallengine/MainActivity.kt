package com.romaster.livewallengine

import android.app.Activity
import android.app.ActivityManager
import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import android.widget.AutoCompleteTextView
import android.widget.Toast
import android.widget.ImageView
import android.widget.LinearLayout
import android.util.DisplayMetrics
import android.graphics.Color
import android.graphics.Bitmap
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20

import java.util.Locale

import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.google.android.material.card.MaterialCardView
import com.google.android.material.slider.Slider
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText


import com.romaster.livewallengine.editor.MainEditorController
import com.romaster.livewallengine.font.FontPicker
import com.romaster.livewallengine.font.FontStorage
import com.romaster.livewallengine.model.DateFormat
import com.romaster.livewallengine.model.TextAlignment
import com.romaster.livewallengine.model.TimeFormat
import com.romaster.livewallengine.model.ClockSettings
import com.romaster.livewallengine.model.OverlaySettings
import com.romaster.livewallengine.model.OverlayAspectMode
import com.romaster.livewallengine.model.VideoLayer
import com.romaster.livewallengine.model.VideoFitMode
import com.romaster.livewallengine.model.VideoAspectMode
import com.romaster.livewallengine.model.CueMode
import com.romaster.livewallengine.project.ProjectManager
import com.romaster.livewallengine.project.DefaultProject
import com.romaster.livewallengine.ui.WallpaperPreviewView
import com.romaster.livewallengine.ui.SimpleSeekListener
import com.romaster.livewallengine.video.VideoPicker
import com.romaster.livewallengine.video.VideoStorage
import com.romaster.livewallengine.video.VideoPlayer
import com.romaster.livewallengine.video.OverlayVideoPlayer
import com.romaster.livewallengine.audio.AudioPicker
import com.romaster.livewallengine.audio.AudioStorage
import com.romaster.livewallengine.ui.dialog.ColorPickerDialog
import com.romaster.livewallengine.dialog.ChromaColorPickerDialog
import com.romaster.livewallengine.storage.FilePicker
import com.romaster.livewallengine.storage.ProjectExporter
import com.romaster.livewallengine.storage.ProjectImporter
import com.romaster.livewallengine.storage.StorageManager
import com.romaster.livewallengine.debug.FileLogger
import com.romaster.livewallengine.video.ReverseVideoProcessor
import android.widget.ProgressBar
import java.io.File
import com.romaster.livewallengine.gallery.ProjectGalleryActivity

class MainActivity : AppCompatActivity() {

    private lateinit var editor:
        MainEditorController
    
    private var loadingUI = false
    
    private lateinit var tabLayout: TabLayout

    private lateinit var panelVideo: View
    
    private lateinit var panelOverlay: View
    
    private lateinit var panelPlayback: View
    
    private lateinit var panelClock: View
    
    private lateinit var panelProject: View
    
    private var exportPreviewBitmap: Bitmap? = null
    
    private var clockColorHex =
        "#FFFFFF"
    
    private var dateColorHex =
        "#FFFFFF"
    
    private var dateSpacingValue = 20f
    
    var durationMs: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {

        try {
    
            super.onCreate(savedInstanceState)
            
            FileLogger.startNewSession(this)
            FileLogger.writeDeviceInfo(this)
            FileLogger.log(this, "MainActivity.onCreate()")
            
            FileLogger.log(this, "1 - setContentView")
            setContentView(R.layout.activity_main)
    
            loadDeviceInformation()
            
            setupPreviewSize()
    
            editor = MainEditorController(this)
    
            editor.load()
            
            FileLogger.log(this, "2 - setupTabs")
            setupTabs()
    
            FileLogger.log(this, "3 - setupVideoTab")
            setupVideoTab()
    
            FileLogger.log(this, "4 - setupOverlayTab")
            setupOverlayTab()
            
            FileLogger.log(this, "5 - setupPlaybackTab")
            setupPlaybackTab()
    
            FileLogger.log(this, "6 - setupClockTab")
            setupClockTab()
    
            FileLogger.log(this, "7 - setupProjectTab")
            setupProjectTab()
    
            setupColorButtons()
    
            FileLogger.log(this, "8 - setupMaterialSliders")
            setupMaterialSliders()
    
            setupMaterialDropdowns()
    
            setupMaterialTextFields()
    
            setupFontDropdowns()
    
            setupChromaKey()
    
            FileLogger.log(this, "9 - reloadProjectUI")
            reloadProjectUI()
    
        } catch (e: Exception) {
            
            FileLogger.logException(
                this,
                "MainActivity.onCreate",
                e
            )
    
            throw e
        }
    }
    
    private fun setupTabs() {

        tabLayout = findViewById(R.id.tabLayout)
    
        panelVideo = findViewById(R.id.panelVideo)
    
        panelOverlay = findViewById(R.id.panelOverlay)
        
        panelPlayback = findViewById(R.id.panelPlayback)
        
        panelClock = findViewById(R.id.panelClock)
    
        panelProject = findViewById(R.id.panelProject)
    
        tabLayout.removeAllTabs()
    
        tabLayout.addTab(
            tabLayout.newTab()
                .setText("📺 Video-BG")
        )
    
        tabLayout.addTab(
            tabLayout.newTab()
                .setText("📽️ Video-OL")
        )
        
        tabLayout.addTab(
            tabLayout.newTab()
                .setText("▶️ Playback")
        )
    
        tabLayout.addTab(
            tabLayout.newTab()
                .setText("🕓 Clock-OL")
        )
    
        tabLayout.addTab(
            tabLayout.newTab()
                .setText("⚙️ Proyecto")
        )
    
        panelVideo.visibility = View.VISIBLE
    
        panelOverlay.visibility = View.GONE
        
        panelPlayback.visibility = View.GONE
        
        panelClock.visibility = View.GONE
    
        panelProject.visibility = View.GONE
    
        tabLayout.addOnTabSelectedListener(
    
            object : TabLayout.OnTabSelectedListener {
    
                override fun onTabSelected(
                    tab: TabLayout.Tab
                ) {
    
                    panelVideo.visibility = View.GONE
    
                    panelOverlay.visibility = View.GONE
                    
                    panelPlayback.visibility = View.GONE
    
                    panelClock.visibility = View.GONE
    
                    panelProject.visibility = View.GONE
    
                    when (tab.position) {
    
                        0 ->
                            panelVideo.visibility = View.VISIBLE
    
                        1 ->
                            panelOverlay.visibility = View.VISIBLE
                        
                        2 ->
                            panelPlayback.visibility = View.VISIBLE
    
                        3 ->
                            panelClock.visibility = View.VISIBLE
    
                        4 ->
                            panelProject.visibility = View.VISIBLE
                    }
                }
    
                override fun onTabUnselected(
                    tab: TabLayout.Tab
                ) {
                }
    
                override fun onTabReselected(
                    tab: TabLayout.Tab
                ) {
                }
            }
        )
    }
    
    private fun setupVideoTab() {

        findViewById<MaterialButton>(
            R.id.buttonSelectVideo
        ).setOnClickListener {
    
            VideoPicker.open(
                this,
                VideoPicker.REQUEST_WALLPAPER
            )
        }
        
        findViewById<MaterialButtonToggleGroup>(
            R.id.toggleVideoMode
        ).addOnButtonCheckedListener { _, checkedId, isChecked ->
        
            FileLogger.log(
                this,
                "Toggle -> id=$checkedId isChecked=$isChecked"
            )
        
            if (!isChecked || loadingUI) return@addOnButtonCheckedListener
        
            updateVideoModeUI()

            FileLogger.log(
                this,
                "ANTES updatePreviewProject()"
            )
            
            updatePreviewProject()
            
            FileLogger.log(
                this,
                "DESPUÉS updatePreviewProject()"
            )
            
        }
        
        findViewById<RadioGroup>(
            R.id.radioAspectRatio
        ).setOnCheckedChangeListener { _, checkedId ->
        
            if (loadingUI) return@setOnCheckedChangeListener
        
            FileLogger.log(
                this,
                "Aspect cambiado -> id=$checkedId"
            )
        
            updatePreviewProject()
        }
        
        setupBgSoundControls()
    }
    
    private fun setupOverlayTab() {

        findViewById<MaterialButton>(
            R.id.buttonLoadOverlay
        ).setOnClickListener {
            
            VideoPicker.open(
                this,
                VideoPicker.REQUEST_OVERLAY
            )
        }
        
        findViewById<MaterialCheckBox>(
            R.id.checkOverlayChroma
        ).setOnCheckedChangeListener { _, _ ->
    
            if (loadingUI)
                return@setOnCheckedChangeListener
    
            updatePreviewProject()
        }
        
        findViewById<RadioGroup>(
            R.id.radioGroupOverlayAspect
        ).setOnCheckedChangeListener { _, _ ->
        
            if (loadingUI)
                return@setOnCheckedChangeListener
        
            updatePreviewProject()
        }
        
        setupOverlaySoundControls()
        
    }
    
    private fun setupChromaKey() {

        val button =
    
            findViewById<View>(
                R.id.buttonChromaColor
            )
    
        val preview =
    
            findViewById<View>(
                R.id.viewChromaColor
            )
    
        val textHex =
    
            findViewById<TextView>(
                R.id.textChromaHex
            )
    
        button.setOnClickListener {
    
            ChromaColorPickerDialog(this)
    
                .show(
    
                    ProjectManager
                        .getProject()
                        .overlay
                        .chromaColor
    
                ) { color ->
    
                    ProjectManager
                        .getProject()
                        .overlay
                        .chromaColor = color
    
                    preview.setBackgroundColor(
                        color
                    )
    
                    textHex.text =
    
                        String.format(
    
                            "#%06X",
    
                            0xFFFFFF and color
    
                        )
    
                    updatePreviewProject()
    
                }
    
        }
    
    }
    
    private fun loadChromaSettings() {

        val overlay =
            ProjectManager
                .getProject()
                .overlay
    
        findViewById<MaterialCheckBox>(
            R.id.checkOverlayChroma
        ).isChecked =
            overlay.chromaEnabled
    
        findViewById<View>(
            R.id.viewChromaColor
        ).setBackgroundColor(
            overlay.chromaColor
        )
    
        findViewById<TextView>(
            R.id.textChromaHex
        ).text =
            String.format(
                "#%06X",
                0xFFFFFF and overlay.chromaColor
            )
    
        findViewById<Slider>(
            R.id.sliderChromaThreshold
        ).value =
            overlay.threshold
        
        findViewById<TextView>(
            R.id.textChromaThresholdValue
        ).text =
            overlay.threshold
                .toInt()
                .toString()
        
        findViewById<Slider>(
            R.id.sliderChromaSoftness
        ).value =
            overlay.softness
        
        findViewById<TextView>(
            R.id.textChromaSoftnessValue
        ).text =
            overlay.softness
                .toInt()
                .toString()
    }
    

    private fun invalidatePingPongIfNeeded(locked: Boolean) {
        val checkId = if (locked) R.id.checkCueLockedPingPong
            else R.id.checkCueUnlockedPingPong
        val box = findViewById<CheckBox>(checkId)
        if (box.isChecked) {
            loadingUI = true
            box.isChecked = false
            loadingUI = false
            clearPingPong(locked)
            Toast.makeText(
                this,
                "Ping-pong desactivado: el cue cambió",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun clearPingPong(locked: Boolean) {
        val project = ProjectManager.getProject()
        ReverseVideoProcessor.deleteIfExists(this, locked)
        if (locked) {
            project.cueLockedReverseFile = null
            project.cueLockedPingPong = false
        } else {
            project.cueUnlockedReverseFile = null
            project.cueUnlockedPingPong = false
        }
        ProjectManager.saveProject(project)
        StorageManager.saveProject(this, project)
    }

    private fun startPingPongProcessing(locked: Boolean) {
        val project = ProjectManager.getProject()
        val overlayName = project.overlayVideo
        if (overlayName.isNullOrBlank()) {
            Toast.makeText(
                this,
                "Primero cargá un video overlay",
                Toast.LENGTH_SHORT
            ).show()
            uncheckPingPongSilently(locked)
            return
        }

        val source = VideoStorage.getVideoFile(this, overlayName)
        if (!source.exists()) {
            Toast.makeText(
                this,
                "No se encontró el video overlay",
                Toast.LENGTH_SHORT
            ).show()
            uncheckPingPongSilently(locked)
            return
        }

        val duration = project.overlayDurationMs.toInt().coerceAtLeast(0)
        val fromMs: Int
        val toMs: Int
        if (locked) {
            fromMs = 0
            toMs = project.cueLockedMs.coerceIn(0, duration)
        } else {
            fromMs = project.cueUnlockedMs.coerceIn(0, duration)
            toMs = duration
        }

        val interval = toMs - fromMs
        if (interval < 200) {
            Toast.makeText(
                this,
                "El intervalo es demasiado corto para ping-pong",
                Toast.LENGTH_SHORT
            ).show()
            uncheckPingPongSilently(locked)
            return
        }

        if (interval > ReverseVideoProcessor.MAX_INTERVAL_MS) {
            Toast.makeText(
                this,
                "Máximo ${ReverseVideoProcessor.MAX_INTERVAL_MS / 1000} s de intervalo para ping-pong",
                Toast.LENGTH_LONG
            ).show()
            uncheckPingPongSilently(locked)
            return
        }

        val progressView = layoutInflater.inflate(
            android.R.layout.simple_list_item_1,
            null
        )
        // Diálogo custom simple
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }
        val label = TextView(this).apply {
            text = "Procesando reversa…"
            textSize = 16f
        }
        val bar = ProgressBar(
            this,
            null,
            android.R.attr.progressBarStyleHorizontal
        ).apply {
            max = 100
            isIndeterminate = false
            progress = 0
        }
        val detail = TextView(this).apply {
            text = "0 %"
            textSize = 13f
            setPadding(0, 16, 0, 0)
        }
        container.addView(label)
        container.addView(bar)
        container.addView(detail)

        val dialog = AlertDialog.Builder(this)
            .setView(container)
            .setCancelable(false)
            .create()
        dialog.show()

        // Nombres fijos en videos/ (se sobrescriben si ya existían)
        val outName = VideoStorage.reverseFileName(locked)

        lifecycleScope.launch {
            try {
                val outFile = withContext(Dispatchers.IO) {
                    ReverseVideoProcessor.process(
                        context = this@MainActivity,
                        sourceFile = source,
                        fromMs = fromMs,
                        toMs = toMs,
                        locked = locked
                    ) { p ->
                        runOnUiThread {
                            val pct = (p * 100f).toInt().coerceIn(0, 100)
                            bar.progress = pct
                            detail.text = "$pct %"
                        }
                    }
                }

                val proj = ProjectManager.getProject()
                if (locked) {
                    proj.cueLockedReverseFile = outFile.name // overlay_video_reverse_locked.mp4
                    proj.cueLockedPingPong = true
                } else {
                    proj.cueUnlockedReverseFile = outFile.name // overlay_video_reverse_unlocked.mp4
                    proj.cueUnlockedPingPong = true
                }
                ProjectManager.saveProject(proj)
                StorageManager.saveProject(this@MainActivity, proj)

                dialog.dismiss()
                Toast.makeText(
                    this@MainActivity,
                    "Ping-pong listo",
                    Toast.LENGTH_SHORT
                ).show()
                updatePreviewProject()
            } catch (e: Exception) {
                dialog.dismiss()
                uncheckPingPongSilently(locked)
                clearPingPong(locked)
                Toast.makeText(
                    this@MainActivity,
                    "Error al procesar: ${e.message ?: e.javaClass.simpleName}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun uncheckPingPongSilently(locked: Boolean) {
        val checkId = if (locked) R.id.checkCueLockedPingPong
            else R.id.checkCueUnlockedPingPong
        loadingUI = true
        findViewById<CheckBox>(checkId).isChecked = false
        loadingUI = false
    }


    private fun setupPlaybackTab() {

        // =====================================================
        // HABILITAR OVERLAY LOOP
        // =====================================================
    
        findViewById<CheckBox>(
            R.id.checkEnableOverlayLoop
        ).setOnCheckedChangeListener { _, _ ->
    
            if (loadingUI)
                return@setOnCheckedChangeListener
    
            updatePreviewProject()
    
        }

        findViewById<CheckBox>(
            R.id.checkCueLockedPingPong
        ).setOnCheckedChangeListener { button, isChecked ->

            if (loadingUI)
                return@setOnCheckedChangeListener

            if (isChecked) {
                startPingPongProcessing(locked = true)
            } else {
                clearPingPong(locked = true)
                updatePreviewProject()
            }
        }

        findViewById<CheckBox>(
            R.id.checkCueUnlockedPingPong
        ).setOnCheckedChangeListener { button, isChecked ->

            if (loadingUI)
                return@setOnCheckedChangeListener

            if (isChecked) {
                startPingPongProcessing(locked = false)
            } else {
                clearPingPong(locked = false)
                updatePreviewProject()
            }
        }
    
    
        // =====================================================
        // SIMULACIÓN DE BLOQUEO
        // =====================================================
    
        findViewById<com.google.android.material.materialswitch.MaterialSwitch>(
            R.id.switchPreviewLocked
        ).setOnCheckedChangeListener { _, _ ->
    
            if (loadingUI)
                return@setOnCheckedChangeListener
    
            updatePreviewProject()
    
        }
    
    
        // =====================================================
        // CUE LOCKED
        // =====================================================
    
        findViewById<Slider>(
            R.id.sliderCueLocked
        ).addOnChangeListener { slider, value, fromUser ->
    
            if (!fromUser)
                return@addOnChangeListener
    
            if (loadingUI)
                return@addOnChangeListener
    
            val duration =
                ProjectManager
                    .getProject()
                    .overlayDurationMs
    
            val cueTime =
                (duration * value)
                    .toInt()
    
            findViewById<TextView>(
                R.id.textCueLocked
            ).text =
                formatCueTime(
                    cueTime
                )

            // Mover el cue invalida el ping-pong locked
            invalidatePingPongIfNeeded(locked = true)
    
            updatePreviewProject()
    
        }
    
    
        // =====================================================
        // CUE UNLOCKED
        // =====================================================
    
        findViewById<Slider>(
            R.id.sliderCueUnlocked
        ).addOnChangeListener { slider, value, fromUser ->
    
            if (!fromUser)
                return@addOnChangeListener
    
            if (loadingUI)
                return@addOnChangeListener
    
            val duration =
                ProjectManager
                    .getProject()
                    .overlayDurationMs
    
            val cueTime =
                (duration * value)
                    .toInt()
    
            findViewById<TextView>(
                R.id.textCueUnlocked
            ).text =
                formatCueTime(
                    cueTime
                )

            invalidatePingPongIfNeeded(locked = false)
    
            updatePreviewProject()
    
        }
        
        findViewById<MaterialButtonToggleGroup>(
            R.id.toggleCueLockedMode
        ).addOnButtonCheckedListener { _, checkedId, isChecked ->
        
            if (!isChecked) return@addOnButtonCheckedListener
            
            if (loadingUI) return@addOnButtonCheckedListener
            
            val project =
                ProjectManager.getProject()
        
            project.cueLockedMode =
                if (checkedId == R.id.buttonCueLockedLoop)
                    CueMode.LOOP
                else
                    CueMode.PAUSE

            // Pausa incompatible con ping-pong: destildar y borrar clip
            if (project.cueLockedMode == CueMode.PAUSE &&
                project.cueLockedPingPong
            ) {
                clearPingPong(locked = true)
                loadingUI = true
                findViewById<CheckBox>(R.id.checkCueLockedPingPong).isChecked = false
                loadingUI = false
                Toast.makeText(
                    this,
                    "Ping-pong desactivado: modo Pausa",
                    Toast.LENGTH_SHORT
                ).show()
            }
        
            ProjectManager.saveProject(project)
        
            updatePreviewProject()
        }
        
        // =====================================================
        // EDITAR CUE LOCKED
        // =====================================================
    
        findViewById<MaterialButton>(
            R.id.buttonCueLocked
        ).setOnClickListener {
    
            showCueTimeDialog(
                locked = true
            )
    
        }
    
    
        // =====================================================
        // EDITAR CUE UNLOCKED
        // =====================================================
    
        findViewById<MaterialButton>(
            R.id.buttonCueUnlocked
        ).setOnClickListener {
    
            showCueTimeDialog(
                locked = false
            )
    
        }
        
        findViewById<MaterialButtonToggleGroup>(
            R.id.toggleCueUnlockedMode
        ).addOnButtonCheckedListener { _, checkedId, isChecked ->
        
            if (!isChecked) return@addOnButtonCheckedListener
            
            if (loadingUI) return@addOnButtonCheckedListener
        
            val project =
                ProjectManager.getProject()
        
            project.cueUnlockedMode =
                if (checkedId == R.id.buttonCueUnlockedLoop)
                    CueMode.LOOP
                else
                    CueMode.PAUSE

            if (project.cueUnlockedMode == CueMode.PAUSE &&
                project.cueUnlockedPingPong
            ) {
                clearPingPong(locked = false)
                loadingUI = true
                findViewById<CheckBox>(R.id.checkCueUnlockedPingPong).isChecked = false
                loadingUI = false
                Toast.makeText(
                    this,
                    "Ping-pong desactivado: modo Pausa",
                    Toast.LENGTH_SHORT
                ).show()
            }
        
            ProjectManager.saveProject(project)
        
            updatePreviewProject()
        }
        
        // =====================================================
        // CONFIGURACIÓN DE SOFT START
        // =====================================================
        
        findViewById<MaterialButton>(
            R.id.buttonVideoFadeDuration
        ).setOnClickListener {
        
            val project =
                ProjectManager.getProject()
        
            showFadeDurationDialog(
                title = "Fade Video principal",
                currentValue =
                    project.videoFadeDurationMs,
                target =
                    FadeTarget.VIDEO
            )
        }
        
        findViewById<MaterialButton>(
            R.id.buttonOverlayFadeDuration
        ).setOnClickListener {
        
            val project =
                ProjectManager.getProject()
        
            showFadeDurationDialog(
                title = "Fade Video Overlay",
                currentValue =
                    project.overlayFadeDurationMs,
                target =
                    FadeTarget.OVERLAY
            )
        }
        
        findViewById<MaterialButton>(
            R.id.buttonClockFadeDuration
        ).setOnClickListener {
        
            val project =
                ProjectManager.getProject()
        
            showFadeDurationDialog(
                title = "Fade Reloj",
                currentValue =
                    project.clockFadeDurationMs,
                target =
                    FadeTarget.CLOCK
            )
        }
    
    }
    
    private fun loadPlaybackSettings() {

        val project =
            ProjectManager
                .getProject()
    
    
        // =====================================================
        // HABILITAR OVERLAY LOOP
        // =====================================================
    
        findViewById<CheckBox>(
            R.id.checkEnableOverlayLoop
        ).isChecked =
            project.overlayLoopEnabled

        findViewById<CheckBox>(
            R.id.checkCueLockedPingPong
        ).isChecked =
            project.cueLockedPingPong

        findViewById<CheckBox>(
            R.id.checkCueUnlockedPingPong
        ).isChecked =
            project.cueUnlockedPingPong
    
    
        // =====================================================
        // SIMULACIÓN DE BLOQUEO
        // =====================================================
    
        findViewById<com.google.android.material.materialswitch.MaterialSwitch>(
            R.id.switchPreviewLocked
        ).isChecked =
            project.previewLocked
    
    
        // =====================================================
        // DURACIÓN DEL OVERLAY
        // =====================================================
    
        val duration =
            project.overlayDurationMs
    
    
        if (
            duration <= 0L
        ) {
    
            findViewById<Slider>(
                R.id.sliderCueLocked
            ).value =
                0f
    
            findViewById<TextView>(
                R.id.textCueLocked
            ).text =
                "00:00.000"
    
    
            findViewById<Slider>(
                R.id.sliderCueUnlocked
            ).value =
                0f
    
            findViewById<TextView>(
                R.id.textCueUnlocked
            ).text =
                "00:00.000"
    
    
            return
        }
    
    
        // =====================================================
        // CUE LOCKED
        // =====================================================
    
        val lockedProgress =
            project.cueLockedMs
                .toFloat() /
                duration.toFloat()
    
    
        findViewById<Slider>(
            R.id.sliderCueLocked
        ).value =
            lockedProgress
                .coerceIn(
                    0f,
                    1f
                )
    
    
        findViewById<TextView>(
            R.id.textCueLocked
        ).text =
            formatCueTime(
                project.cueLockedMs
            )
        
        val toggleLocked =
            findViewById<MaterialButtonToggleGroup>(
                R.id.toggleCueLockedMode
            )
        
        toggleLocked.check(
            if (project.cueLockedMode == CueMode.LOOP)
                R.id.buttonCueLockedLoop
            else
                R.id.buttonCueLockedPause
        )
        
        // =====================================================
        // CUE UNLOCKED
        // =====================================================
    
        val unlockedProgress =
            project.cueUnlockedMs
                .toFloat() /
                duration.toFloat()
    
    
        findViewById<Slider>(
            R.id.sliderCueUnlocked
        ).value =
            unlockedProgress
                .coerceIn(
                    0f,
                    1f
                )
    
    
        findViewById<TextView>(
            R.id.textCueUnlocked
        ).text =
            formatCueTime(
                project.cueUnlockedMs
            )
        
        val toggleUnlocked =
            findViewById<MaterialButtonToggleGroup>(
                R.id.toggleCueUnlockedMode
            )
        
        toggleUnlocked.check(
            if (project.cueUnlockedMode == CueMode.LOOP)
                R.id.buttonCueUnlockedLoop
            else
                R.id.buttonCueUnlockedPause
        )
        
        updateSoftStartUI()
    
    }
    
    private fun formatCueTime(
        milliseconds: Int
    ): String {
    
        val minutes =
            milliseconds / 60000
    
        val seconds =
            (milliseconds % 60000) / 1000
    
        val millis =
            milliseconds % 1000
    
        return String.format(
            "%02d:%02d.%03d",
            minutes,
            seconds,
            millis
        )
    }
    
    private fun showCueTimeDialog(
        locked: Boolean
    ) {
    
        val dialogView =
            layoutInflater.inflate(
                R.layout.dialog_cue_time,
                null
            )
    
        val editCueTime =
            dialogView.findViewById<TextInputEditText>(
                R.id.editCueTime
            )
    
        val project =
            ProjectManager
                .getProject()
    
        val currentValue =
            if (locked)
                project.cueLockedMs
            else
                project.cueUnlockedMs
    
        editCueTime.setText(
            formatCueTime(
                currentValue
            )
        )
    
        AlertDialog.Builder(this)
    
            .setTitle(
                if (locked)
                    "Editar Cue Locked"
                else
                    "Editar Cue Unlocked"
            )
    
            .setView(
                dialogView
            )
    
            .setNegativeButton(
                "Cancelar",
                null
            )
    
            .setPositiveButton(
                "Aceptar"
            ) { _, _ ->
    
                val milliseconds =
                    parseCueTime(
                        editCueTime
                            .text
                            ?.toString()
                            ?: ""
                    )
    
                if (
                    milliseconds == null
                ) {
    
                    return@setPositiveButton
    
                }
    
                val duration =
                    ProjectManager
                        .getProject()
                        .overlayDurationMs
    
                if (
                    duration <= 0
                ) {
    
                    return@setPositiveButton
    
                }
    
                val sliderValue =
                    milliseconds.toFloat() /
                            duration.toFloat()
    
                if (locked) {

                    findViewById<Slider>(
                        R.id.sliderCueLocked
                    ).value =
                        sliderValue
                
                    findViewById<TextView>(
                        R.id.textCueLocked
                    ).text =
                        formatCueTime(milliseconds)
                
                } else {
                
                    findViewById<Slider>(
                        R.id.sliderCueUnlocked
                    ).value =
                        sliderValue
                
                    findViewById<TextView>(
                        R.id.textCueUnlocked
                    ).text =
                        formatCueTime(milliseconds)
                
                }

                invalidatePingPongIfNeeded(locked)
    
                updatePreviewProject()
    
            }
    
            .show()
    
    }
    
    private fun updateSoftStartUI() {

        val project =
            ProjectManager.getProject()
    
        findViewById<TextView>(
            R.id.textVideoFadeDuration
        ).text =
            "${project.videoFadeDurationMs} ms"
    
        findViewById<TextView>(
            R.id.textOverlayFadeDuration
        ).text =
            "${project.overlayFadeDurationMs} ms"
    
        findViewById<TextView>(
            R.id.textClockFadeDuration
        ).text =
            "${project.clockFadeDurationMs} ms"
    }
    
    private enum class FadeTarget {
        VIDEO,
        OVERLAY,
        CLOCK
    }
    
    private fun showFadeDurationDialog(
        title: String,
        currentValue: Long,
        target: FadeTarget
    ) {
    
        val input =
            EditText(this).apply {
    
                inputType =
                    android.text.InputType.TYPE_CLASS_NUMBER
    
                setText(
                    currentValue.toString()
                )
    
                selectAll()
            }
    
        val container =
            LinearLayout(this).apply {
    
                orientation =
                    LinearLayout.VERTICAL
    
                setPadding(
                    48,
                    0,
                    48,
                    0
                )
    
                addView(input)
            }
    
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(
                "Duración del fade en milisegundos."
            )
            .setView(container)
            .setNegativeButton(
                "Cancelar",
                null
            )
            .setPositiveButton(
                "Aceptar"
            ) { _, _ ->
    
                val value =
                    input.text
                        .toString()
                        .toLongOrNull()
    
                if (
                    value == null ||
                    value < 0L
                ) {
    
                    Toast.makeText(
                        this,
                        "Ingresá un valor válido.",
                        Toast.LENGTH_SHORT
                    ).show()
    
                    return@setPositiveButton
                }
    
                val project =
                    ProjectManager.getProject()
    
                when (target) {
    
                    FadeTarget.VIDEO ->
                        project.videoFadeDurationMs =
                            value
    
                    FadeTarget.OVERLAY ->
                        project.overlayFadeDurationMs =
                            value
    
                    FadeTarget.CLOCK ->
                        project.clockFadeDurationMs =
                            value
                }
    
                ProjectManager.saveProject(
                    project
                )
    
                updateSoftStartUI()
            }
            .show()
    }
    
    private fun showDateSpacingDialog() {

        val dialogView =
            layoutInflater.inflate(
                R.layout.dialog_date_spacing,
                null
            )
    
        val editDateSpacing =
            dialogView.findViewById<TextInputEditText>(
                R.id.editDateSpacing
            )
    
        val currentSpacing =
            if (dateSpacingValue % 1f == 0f) {
                dateSpacingValue
                    .toInt()
                    .toString()
            } else {
                dateSpacingValue
                    .toString()
            }
    
        editDateSpacing.setText(
            currentSpacing
        )
    
        AlertDialog.Builder(this)
    
            .setTitle(
                "Espacio entre reloj y fecha"
            )
    
            .setView(
                dialogView
            )
    
            .setNegativeButton(
                "Cancelar",
                null
            )
    
            .setPositiveButton(
                "Aceptar"
            ) { _, _ ->
    
                val value =
                    editDateSpacing
                        .text
                        ?.toString()
                        ?.trim()
                        ?.replace(",", ".")
                        ?.toFloatOrNull()
    
                if (value == null) {
                    return@setPositiveButton
                }
    
                dateSpacingValue =
                    value.coerceAtLeast(0f)
                
                findViewById<TextView>(
                    R.id.textDateSpacingValue
                ).text =
                    formatDateSpacing(
                        dateSpacingValue
                    )
    
                updatePreviewProject()
            }
    
            .show()
    }
    
    private fun formatDateSpacing(
        value: Float
    ): String {
    
        return if (value % 1f == 0f) {
    
            "${value.toInt()} px"
    
        } else {
    
            "$value px"
        }
    }
    
    private fun parseCueTime(
        value: String
    ): Int? {
    
        return try {
    
            val parts =
                value
                    .trim()
                    .split(":")
    
            if (
                parts.size != 2
            ) {
    
                return null
    
            }
    
            val minutes =
                parts[0]
                    .toInt()
    
            val secondParts =
                parts[1]
                    .split(".")
    
            val seconds =
                secondParts[0]
                    .toInt()
    
            val milliseconds =
                if (
                    secondParts.size > 1
                ) {
    
                    secondParts[1]
                        .padEnd(
                            3,
                            '0'
                        )
                        .take(3)
                        .toInt()
    
                } else {
    
                    0
    
                }
    
            (
                minutes * 60000
            ) +
            (
                seconds * 1000
            ) +
            milliseconds
    
        } catch (
            e: Exception
        ) {
    
            null
    
        }
    
    }
    
    private fun readPlaybackSettingsFromUI() {

        val project =
            ProjectManager
                .getProject()
    
        val duration =
            project.overlayDurationMs
    
    
        // =====================================================
        // OVERLAY LOOP
        // =====================================================
    
        project.overlayLoopEnabled =
            findViewById<CheckBox>(
                R.id.checkEnableOverlayLoop
            ).isChecked

        project.cueLockedPingPong =
            findViewById<CheckBox>(
                R.id.checkCueLockedPingPong
            ).isChecked

        project.cueUnlockedPingPong =
            findViewById<CheckBox>(
                R.id.checkCueUnlockedPingPong
            ).isChecked
    
    
        // =====================================================
        // SIMULACIÓN DE BLOQUEO
        // =====================================================
    
        project.previewLocked =
            findViewById<com.google.android.material.materialswitch.MaterialSwitch>(
                R.id.switchPreviewLocked
            ).isChecked
    
    
        // =====================================================
        // CUE LOCKED
        // =====================================================
    
        project.cueLockedMs =
            (
                duration *
                findViewById<Slider>(
                    R.id.sliderCueLocked
                ).value
            ).toInt()
    
    
        // =====================================================
        // CUE UNLOCKED
        // =====================================================
    
        project.cueUnlockedMs =
            (
                duration *
                findViewById<Slider>(
                    R.id.sliderCueUnlocked
                ).value
            ).toInt()
    
    }
    
    private fun setupClockTab() {

        findViewById<MaterialButton>(
            R.id.buttonImportFont
        ).setOnClickListener {
    
            FontPicker.open(this)
        }
    
        // ---------------------------------
        // ESPACIO ENTRE RELOJ Y FECHA
        // ---------------------------------
    
        findViewById<MaterialButton>(
            R.id.buttonDateSpacing
        ).setOnClickListener {
    
            showDateSpacingDialog()
        }
    }
    
    private fun setupProjectTab() {

        // BOTON DE GUARDAR
        
        findViewById<MaterialButton>(
            R.id.buttonSave
        ).setOnClickListener {
    
            saveClockSettings()
    
            refreshPreview()
    
            editor.save()
        }
        
        // BOTON DE SELECCIONAR PROYECTO (galería GitHub)

        findViewById<MaterialButton>(
            R.id.buttonSelectProject
        ).setOnClickListener {

            startActivityForResult(
                Intent(
                    this,
                    ProjectGalleryActivity::class.java
                ),
                FilePicker.REQUEST_GALLERY_PROJECT
            )
        }

        // BOTON DE IMPORTAR PROYECTO
    
        findViewById<MaterialButton>(
            R.id.buttonImportProject
        ).setOnClickListener {
    
            val intent = Intent(
                Intent.ACTION_OPEN_DOCUMENT
            )
            
            intent.addCategory(
                Intent.CATEGORY_OPENABLE
            )
            
            intent.type = "application/zip"
            
            startActivityForResult(
                intent,
                FilePicker.REQUEST_IMPORT_PROJECT
            )
    
        }
        
        // BOTON DE EXPORTAR PROYECTO
    
        findViewById<MaterialButton>(
            R.id.buttonExportProject
        ).setOnClickListener {
        
            findViewById<WallpaperPreviewView>(
                R.id.previewView
            ).capturePreview {
        
                exportPreviewBitmap = it
        
                val intent =
                    Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
        
                        addCategory(Intent.CATEGORY_OPENABLE)
        
                        type = "application/zip"
        
                        putExtra(
                            Intent.EXTRA_TITLE,
                            "RomasterProject.zip"
                        )
        
                    }
        
                startActivityForResult(
                    intent,
                    FilePicker.REQUEST_EXPORT_PROJECT
                )
        
            }
        
        }
        
        // BOTON DE PROYECTO NUEVO / FACTORY RESET
    
        findViewById<MaterialButton>(
            R.id.buttonNewProject
        ).setOnClickListener {

            // Limpiar clips de reversa del proyecto anterior
            ReverseVideoProcessor.clearAll(this)
        
            ProjectManager.resetProject()
        
            StorageManager.saveProject(
                this,
                ProjectManager.getProject()
            )
        
            reloadProjectUI()
        
            findViewById<WallpaperPreviewView>(
                R.id.previewView
            ).reloadPlayers()
        
        }
    }
    
    private fun setupButtons() {
        //Vacío, para futuros usos
    }
    
    private fun setupColorButtons() {

        findViewById<MaterialButton>(
            R.id.buttonClockColor
        ).setOnClickListener {
    
            ColorPickerDialog.show(
    
                this,
    
                clockColorHex
    
            ) { hex ->
    
                clockColorHex = hex
    
                updateClockColorUI()
    
                updatePreviewProject()
            }
        }
    
        findViewById<MaterialButton>(
            R.id.buttonDateColor
        ).setOnClickListener {
    
            ColorPickerDialog.show(
    
                this,
    
                dateColorHex
    
            ) { hex ->
    
                dateColorHex = hex
    
                updateDateColorUI()
    
                updatePreviewProject()
            }
        }
    }
    
    private fun updateClockColorUI() {

        findViewById<View>(
            R.id.viewClockColor
        ).setBackgroundColor(
            Color.parseColor(clockColorHex)
        )
    
        findViewById<TextView>(
            R.id.textClockColor
        ).text =
            clockColorHex
    }
    
    private fun updateDateColorUI() {

        findViewById<View>(
            R.id.viewDateColor
        ).setBackgroundColor(
            Color.parseColor(dateColorHex)
        )
    
        findViewById<TextView>(
            R.id.textDateColor
        ).text =
            dateColorHex
    }
    
    private fun setupMaterialSliders() {

        setupClockSliders()
    
        setupVideoSliders()
    
        setupOverlaySliders()
    }
    
    private fun setupClockSliders() {

        connectSlider(
            R.id.sliderClockSize,
            R.id.textClockSizeValue
        )
    
        connectSlider(
            R.id.sliderDateSize,
            R.id.textDateSizeValue
        )
    
        connectSlider(
            R.id.sliderX,
            R.id.textXValue
        )
    
        connectSlider(
            R.id.sliderY,
            R.id.textYValue
        )
    }
    
    private fun setupVideoSliders() {

        connectSlider(
            R.id.sliderVideoZoom,
            R.id.textVideoZoomValue
        )
    
        connectSlider(
            R.id.sliderVideoPosX,
            R.id.textVideoPosXValue
        )
    
        connectSlider(
            R.id.sliderVideoPosY,
            R.id.textVideoPosYValue
        )
    }
    
    private fun setupOverlaySliders() {

        connectSlider(
            R.id.sliderOverlayAlpha,
            R.id.textOverlayAlphaValue
        )
    
        connectSlider(
            R.id.sliderOverlayZoom,
            R.id.textOverlayZoomValue
        )
    
        connectSlider(
            R.id.sliderOverlayPosX,
            R.id.textOverlayPosXValue
        )
    
        connectSlider(
            R.id.sliderOverlayPosY,
            R.id.textOverlayPosYValue
        )
    
        connectSlider(
            R.id.sliderOverlayRotation,
            R.id.textOverlayRotationValue
        )
    
        // -----------------------------
        // Chroma Key
        // -----------------------------
    
        connectSlider(
            R.id.sliderChromaThreshold,
            R.id.textChromaThresholdValue
        )
    
        connectSlider(
            R.id.sliderChromaSoftness,
            R.id.textChromaSoftnessValue
        )
    }
    
    private fun setupChromaCheckbox() {

        findViewById<MaterialCheckBox>(
            R.id.checkOverlayChroma
        ).setOnCheckedChangeListener { _, _ ->
    
            if (loadingUI)
                return@setOnCheckedChangeListener
    
            updatePreviewProject()
        }
    
    }
    
    private fun connectSlider(

        sliderId: Int,
    
        labelId: Int
    
    ) {
    
        val slider =
            findViewById<Slider>(
                sliderId
            )
    
        val label =
            findViewById<TextView>(
                labelId
            )
    
        slider.addOnChangeListener {
    
                _,
                value,
                fromUser ->
    
            label.text =
                value.toInt().toString()
    
            if (!fromUser || loadingUI)
                return@addOnChangeListener
    
            updatePreviewProject()
        }
    }
    
    private fun connectVolumeSlider(

        sliderId: Int,
    
        labelId: Int,
    
        iconId: Int
    
    ) {
    
        val slider =
            findViewById<Slider>(sliderId)
    
        val label =
            findViewById<TextView>(labelId)
    
        val icon =
            findViewById<ImageView>(iconId)
    
        slider.addOnChangeListener { _, value, fromUser ->
    
            label.text =
                value.toInt().toString()
    
            icon.setImageResource(
    
                if (value == 0f)
    
                    R.drawable.baseline_volume_off_24
    
                else
    
                    R.drawable.baseline_volume_up_24
    
            )
    
            if (!fromUser || loadingUI)
                return@addOnChangeListener
    
            updatePreviewProject()
    
        }
    
    }
    
    private fun setupMaterialDropdowns() {
        //Vacío, para futuros agregados
    }
    
    private fun setupMaterialTextFields() {
        //Vacío, para futuros agregados
    }
    
    private fun setupBgSoundControls() {

        connectVolumeSlider(
            R.id.sliderBgSoundVolume,
            R.id.textBgSoundVolumeValue,
            R.id.imageBgSoundIcon
        )
    
        findViewById<MaterialButton>(
            R.id.buttonLoadBgSound
        ).setOnClickListener {
    
            AudioPicker.open(
                this,
                AudioPicker.REQUEST_BG_SOUND
            )
    
        }
        
        findViewById<MaterialButton>(
            R.id.buttonResetBgSound
        ).setOnClickListener {
        
            resetBackgroundSound()
        
        }
    
    }
    
    private fun setupOverlaySoundControls() {

        connectVolumeSlider(
            R.id.sliderOverlaySoundVolume,
            R.id.textOverlaySoundVolumeValue,
            R.id.imageOverlaySoundIcon
        )
    
        findViewById<MaterialButton>(
            R.id.buttonLoadOverlaySound
        ).setOnClickListener {
    
            AudioPicker.open(
                this,
                AudioPicker.REQUEST_OVERLAY_SOUND
            )
    
        }
        
        findViewById<MaterialButton>(
            R.id.buttonResetOverlaySound
        ).setOnClickListener {
        
            resetOverlaySound()
        
        }
    
    }
    
    private fun resetBackgroundSound() {

        val project =
            ProjectManager.getProject()
    
        if (project.layers.isEmpty()) {
    
            project.layers.add(
                VideoLayer()
            )
    
        }
    
        val layer =
            project.layers.first()
    
        layer.soundPath = null
        layer.soundDisplayName = null
        layer.soundDuration = 0L
        layer.soundVolume = 1f
    
        findViewById<Slider>(
            R.id.sliderBgSoundVolume
        ).value = 100f
    
        findViewById<TextView>(
            R.id.textBgSoundVolumeValue
        ).text = "100"
    
        findViewById<TextView>(
            R.id.textBgSoundFile
        ).text =
            "Ningún sonido seleccionado"
    
        findViewById<TextView>(
            R.id.textBgSoundDuration
        ).text =
            "Duración: --:--.---"
        
        editor.save()
    
    }
    
    private fun resetOverlaySound() {

        val overlay =
            ProjectManager
                .getProject()
                .overlay
    
        overlay.soundPath = null
        overlay.soundDisplayName = null
        overlay.soundDuration = 0L
        overlay.soundVolume = 1f
    
        findViewById<Slider>(
            R.id.sliderOverlaySoundVolume
        ).value = 100f
    
        findViewById<TextView>(
            R.id.textOverlaySoundVolumeValue
        ).text = "100"
    
        findViewById<TextView>(
            R.id.textOverlaySoundFile
        ).text =
            "Ningún sonido seleccionado"
    
        findViewById<TextView>(
            R.id.textOverlaySoundDuration
        ).text =
            "Duración: --:--.---"
        
        editor.save()
    
    }

    private fun loadClockSettings() {

        val clock =
            ProjectManager
                .getProject()
                .clock
        
        dateSpacingValue =
            clock.dateSpacing
        
        findViewById<TextView>(
            R.id.textDateSpacingValue
        ).text =
            formatDateSpacing(
                dateSpacingValue
            )
        
        findViewById<CheckBox>(
            R.id.checkClockLockScreen
        ).isChecked =
            clock.enabledOnLockScreen

        findViewById<CheckBox>(
            R.id.checkClock
        ).isChecked =
            clock.enabled

        findViewById<CheckBox>(
            R.id.checkDate
        ).isChecked =
            clock.showDate

        when (
            clock.timeFormat
        ) {

            TimeFormat.HH_MM ->
                findViewById<RadioGroup>(
                    R.id.radioTimeFormat
                ).check(
                    R.id.radioHHMM
                )

            TimeFormat.HH_MM_SS ->
                findViewById<RadioGroup>(
                    R.id.radioTimeFormat
                ).check(
                    R.id.radioHHMMSS
                )

            TimeFormat.HH_MM_AM_PM ->
                findViewById<RadioGroup>(
                    R.id.radioTimeFormat
                ).check(
                    R.id.radioAMPM
                )
        }

        when (
            clock.dateFormat
        ) {

            DateFormat.DOW_DD_MON ->
                findViewById<RadioGroup>(
                    R.id.radioDateFormat
                ).check(
                    R.id.radioDateShort
                )

            DateFormat.DD_MM_YYYY ->
                findViewById<RadioGroup>(
                    R.id.radioDateFormat
                ).check(
                    R.id.radioDateNumeric
                )

            DateFormat.DOW_DD_MON_YYYY ->
                findViewById<RadioGroup>(
                    R.id.radioDateFormat
                ).check(
                    R.id.radioDateLong
                )
        }
        findViewById<Slider>(
            R.id.sliderClockSize
        ).value =
            clock.clockSize
        
        findViewById<TextView>(
            R.id.textClockSizeValue
        ).text =
            clock.clockSize.toInt().toString()
        
        findViewById<Slider>(
            R.id.sliderDateSize
        ).value =
            clock.dateSize
        
        findViewById<TextView>(
            R.id.textDateSizeValue
        ).text =
            clock.dateSize.toInt().toString()
        
        findViewById<Slider>(
            R.id.sliderX
        ).value =
            clock.x * 100f
        
        findViewById<TextView>(
            R.id.textXValue
        ).text =
            (clock.x * 100f).toInt().toString()
        
        findViewById<Slider>(
            R.id.sliderY
        ).value =
            clock.y * 100f
        
        findViewById<TextView>(
            R.id.textYValue
        ).text =
            (clock.y * 100f).toInt().toString()
        
        when (clock.alignment) {

            TextAlignment.LEFT ->
                findViewById<RadioGroup>(
                    R.id.radioAlign
                ).check(
                    R.id.radioAlignLeft
                )
        
            TextAlignment.RIGHT ->
                findViewById<RadioGroup>(
                    R.id.radioAlign
                ).check(
                    R.id.radioAlignRight
                )
        
            else ->
                findViewById<RadioGroup>(
                    R.id.radioAlign
                ).check(
                    R.id.radioAlignCenter
                )
        }
        
        clockColorHex =
            clock.clockColor
        
        updateClockColorUI()
        
        dateColorHex =
            clock.dateColor
        
        updateDateColorUI()
        
        setupFontDropdown(
            R.id.dropClockFont,
            clock.clockFont
        )
        
        setupFontDropdown(
            R.id.dropDateFont,
            clock.dateFont
        )
        
        findViewById<CheckBox>(
            R.id.checkClockLockScreen
        ).setOnCheckedChangeListener { _, _ ->
        
            if (loadingUI)
                return@setOnCheckedChangeListener
        
            updatePreviewProject()
        }
        
        findViewById<CheckBox>(
            R.id.checkClock
        ).setOnCheckedChangeListener { _, _ ->
        
            if (loadingUI)
                return@setOnCheckedChangeListener
        
            updatePreviewProject()
        }
        
        findViewById<CheckBox>(
            R.id.checkDate
        ).setOnCheckedChangeListener { _, _ ->
        
            if (loadingUI)
                return@setOnCheckedChangeListener
        
            updatePreviewProject()
        }
        
        findViewById<RadioGroup>(
            R.id.radioTimeFormat
        ).setOnCheckedChangeListener { _, _ ->
        
            if (loadingUI)
                return@setOnCheckedChangeListener
        
            updatePreviewProject()
        }
        
        findViewById<RadioGroup>(
            R.id.radioDateFormat
        ).setOnCheckedChangeListener { _, _ ->
        
            if (loadingUI)
                return@setOnCheckedChangeListener
        
            updatePreviewProject()
        }
        
        findViewById<RadioGroup>(
            R.id.radioAlign
        ).setOnCheckedChangeListener { _, _ ->
        
            if (loadingUI)
                return@setOnCheckedChangeListener
        
            updatePreviewProject()
        }
    }

    private fun saveClockSettings() {

        val project =
            ProjectManager.getProject()
    
        project.clock =
            readClockSettingsFromUI()
    
        ProjectManager.saveProject(
            project
        )
    }

    @Deprecated("Activity Result API")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
    
        super.onActivityResult(
            requestCode,
            resultCode,
            data
        )
    
        if (resultCode != Activity.RESULT_OK)
            return

        // Galería: el proyecto ya fue importado en ProjectGalleryActivity
        if (requestCode == FilePicker.REQUEST_GALLERY_PROJECT) {
            reloadProjectUI()
            findViewById<WallpaperPreviewView>(
                R.id.previewView
            ).reloadPlayers()
            return
        }
    
        val uri =
            data?.data ?: return
    
        when (requestCode) {
    
            // ---------------------------------
            // Wallpaper
            // ---------------------------------
    
            VideoPicker.REQUEST_WALLPAPER -> {
    
                val path =
                    VideoStorage.importWallpaperVideo(
                        this,
                        uri
                    )
    
                ProjectManager
                    .getProject()
                    .wallpaperVideo = path
    
                editor.save()
    
                refreshPreview()
            }
    
            // ---------------------------------
            // Overlay
            // ---------------------------------
    
            VideoPicker.REQUEST_OVERLAY -> {
    
                val path =
                    VideoStorage.importOverlayVideo(
                        this,
                        uri
                    )
    
                ProjectManager
                    .getProject()
                    .overlayVideo = path

                // Nuevo video: invalidar ambos ping-pong
                clearPingPong(locked = true)
                clearPingPong(locked = false)
                loadingUI = true
                findViewById<CheckBox>(R.id.checkCueLockedPingPong).isChecked = false
                findViewById<CheckBox>(R.id.checkCueUnlockedPingPong).isChecked = false
                loadingUI = false
    
                editor.save()
    
                refreshPreview()
            }
    
            // ---------------------------------
            // Fuente
            // ---------------------------------
    
            FontPicker.REQUEST_FONT -> {
    
                FontStorage.importFont(
                    this,
                    uri
                )
    
                reloadFontLibrary()
    
                editor.save()
            }
    
            // ---------------------------------
            // Sonido BG
            // ---------------------------------
    
            AudioPicker.REQUEST_BG_SOUND -> {
    
                AudioStorage.copyAudio(
                    this,
                    uri,
                    AudioStorage.BG_SOUND
                )
    
                val project =
                    ProjectManager.getProject()
    
                if (project.layers.isEmpty()) {
    
                    project.layers.add(
                        VideoLayer()
                    )
    
                }
    
                val layer =
                    project.layers.first()
    
                val displayName =
                    AudioStorage.getDisplayName(
                        this,
                        uri
                    )
    
                val duration =
                    AudioStorage.getDuration(
                        this,
                        uri
                    )
    
                layer.soundPath =
                    AudioStorage.BG_SOUND
    
                layer.soundDisplayName =
                    displayName
    
                layer.soundDuration =
                    duration
    
                findViewById<TextView>(
                    R.id.textBgSoundFile
                ).text =
                    displayName
    
                findViewById<TextView>(
                    R.id.textBgSoundDuration
                ).text =
                    "Duración: ${
                        AudioStorage.formatDuration(
                            duration
                        )
                    }"
    
                ProjectManager.saveProject(
                    project
                )
    
                editor.save()
            }
    
            // ---------------------------------
            // Sonido Overlay
            // ---------------------------------
    
            AudioPicker.REQUEST_OVERLAY_SOUND -> {
    
                AudioStorage.copyAudio(
                    this,
                    uri,
                    AudioStorage.OVERLAY_SOUND
                )
    
                val project =
                    ProjectManager.getProject()
    
                val overlay =
                    project.overlay
    
                val displayName =
                    AudioStorage.getDisplayName(
                        this,
                        uri
                    )
    
                val duration =
                    AudioStorage.getDuration(
                        this,
                        uri
                    )
    
                overlay.soundPath =
                    AudioStorage.OVERLAY_SOUND
    
                overlay.soundDisplayName =
                    displayName
    
                overlay.soundDuration =
                    duration
    
                findViewById<TextView>(
                    R.id.textOverlaySoundFile
                ).text =
                    displayName
    
                findViewById<TextView>(
                    R.id.textOverlaySoundDuration
                ).text =
                    "Duración: ${
                        AudioStorage.formatDuration(
                            duration
                        )
                    }"
    
                ProjectManager.saveProject(
                    project
                )
    
                editor.save()
            }
            
            FilePicker.REQUEST_EXPORT_PROJECT -> {

                val previewBitmap =
                    exportPreviewBitmap
            
                exportPreviewBitmap = null
            
                lifecycleScope.launch {
            
                    try {
            
                        withContext(
                            Dispatchers.IO
                        ) {
            
                            ProjectExporter.export(
                                this@MainActivity,
                                uri,
                                previewBitmap
                            )
                        }
            
                    } catch (e: Exception) {
            
                        e.printStackTrace()
                    }
                }
            }
            
            FilePicker.REQUEST_IMPORT_PROJECT -> {

                // Antes de importar: borrar reversas del proyecto actual
                ReverseVideoProcessor.clearAll(this)

                ProjectImporter.import(
                    this,
                    uri
                )
                
                reloadProjectUI()

                findViewById<WallpaperPreviewView>(
                    R.id.previewView
                ).reloadPlayers()
                
            }
    
        }
    
    }
    
    private fun loadOverlaySettings() {

        val overlay =
            ProjectManager
                .getProject()
                .overlay
    
        findViewById<Slider>(
            R.id.sliderOverlayAlpha
        ).value =
            overlay.opacity * 100f
    
        findViewById<TextView>(
            R.id.textOverlayAlphaValue
        ).text =
            (overlay.opacity * 100f)
                .toInt()
                .toString()
        
        findViewById<RadioGroup>(
            R.id.radioGroupOverlayAspect
        ).check(
            when (overlay.aspectMode) {
                
                OverlayAspectMode.ORIGINAL ->
                    R.id.radioOverlayAspectOriginal
        
                OverlayAspectMode.SCREEN ->
                    R.id.radioOverlayAspectScreen
            }
        )
    
        findViewById<Slider>(
            R.id.sliderOverlayZoom
        ).value =
            overlay.scale * 100f
    
        findViewById<TextView>(
            R.id.textOverlayZoomValue
        ).text =
            (overlay.scale * 100f)
                .toInt()
                .toString()
    
        findViewById<Slider>(
            R.id.sliderOverlayPosX
        ).value =
            overlay.x
    
        findViewById<TextView>(
            R.id.textOverlayPosXValue
        ).text =
            overlay.x.toInt().toString()
    
        findViewById<Slider>(
            R.id.sliderOverlayPosY
        ).value =
            overlay.y
    
        findViewById<TextView>(
            R.id.textOverlayPosYValue
        ).text =
            overlay.y.toInt().toString()
    
        findViewById<Slider>(
            R.id.sliderOverlayRotation
        ).value =
            overlay.rotation
    
        findViewById<TextView>(
            R.id.textOverlayRotationValue
        ).text =
            overlay.rotation.toInt().toString()
        
        // -----------------------------
        // Audio Overlay
        // -----------------------------
        
        val volume =
            overlay.soundVolume * 100f
        
        findViewById<Slider>(
            R.id.sliderOverlaySoundVolume
        ).value =
            volume
        
        findViewById<TextView>(
            R.id.textOverlaySoundVolumeValue
        ).text =
            volume.toInt().toString()
        
        findViewById<ImageView>(
            R.id.imageOverlaySoundIcon
        ).setImageResource(
        
            if (volume == 0f)
        
                R.drawable.baseline_volume_off_24
        
            else
        
                R.drawable.baseline_volume_up_24
        
        )
        
        findViewById<TextView>(
            R.id.textOverlaySoundFile
        ).text =
            overlay.soundDisplayName
                ?: "Ningún sonido seleccionado"
        
        findViewById<TextView>(
            R.id.textOverlaySoundDuration
        ).text =
        
            if (overlay.soundDuration > 0L)
        
                "Duración: ${
                    AudioStorage.formatDuration(
                        overlay.soundDuration
                    )
                }"
        
            else
        
                "Duración: --:--.---"
    }
    
    private fun loadVideoLayerSettings() {

        val layer =
            ProjectManager
                .getProject()
                .layers
                .firstOrNull()
                ?: return
        
        // -----------------------------
        // Aspect Ratio
        // -----------------------------
        
        when (layer.fitMode) {

            VideoFitMode.FIT ->
                findViewById<MaterialButton>(
                    R.id.buttonFit
                ).isChecked = true
        
            VideoFitMode.FILL ->
                findViewById<MaterialButton>(
                    R.id.buttonFill
                ).isChecked = true
        
            VideoFitMode.STRETCH ->
                findViewById<MaterialButton>(
                    R.id.buttonStretch
                ).isChecked = true
        
            VideoFitMode.FREE ->
                findViewById<MaterialButton>(
                    R.id.buttonFree
                ).isChecked = true
        }
        
        findViewById<RadioGroup>(
            R.id.radioAspectRatio
        ).check(
        
            when (layer.aspectMode) {
        
                VideoAspectMode.R16_9 ->
                    R.id.radioAspect16x9
        
                VideoAspectMode.R16_10 ->
                    R.id.radioAspect16x10
        
                VideoAspectMode.R18_9 ->
                    R.id.radioAspect18x9
                
                VideoAspectMode.R20_9 ->
                    R.id.radioAspect20x9
        
                VideoAspectMode.R4_3 ->
                    R.id.radioAspect4x3
        
                VideoAspectMode.R3_2 ->
                    R.id.radioAspect3x2
        
                else ->
                    R.id.radioAspectOriginal
            }
        
        )
        
        // -----------------------------
        // Transformaciones
        // -----------------------------
        
        findViewById<Slider>(
            R.id.sliderVideoZoom
        ).value =
            layer.scale
    
        findViewById<TextView>(
            R.id.textVideoZoomValue
        ).text =
            layer.scale.toInt().toString()
    
        findViewById<Slider>(
            R.id.sliderVideoPosX
        ).value =
            layer.x
    
        findViewById<TextView>(
            R.id.textVideoPosXValue
        ).text =
            layer.x.toInt().toString()
    
        findViewById<Slider>(
            R.id.sliderVideoPosY
        ).value =
            layer.y
    
        findViewById<TextView>(
            R.id.textVideoPosYValue
        ).text =
            layer.y.toInt().toString()
        
        // -----------------------------
        // Audio
        // -----------------------------
    
        val volume =
            layer.soundVolume * 100f
    
        findViewById<Slider>(
            R.id.sliderBgSoundVolume
        ).value =
            volume
    
        findViewById<TextView>(
            R.id.textBgSoundVolumeValue
        ).text =
            volume.toInt().toString()
    
        findViewById<ImageView>(
            R.id.imageBgSoundIcon
        ).setImageResource(
    
            if (volume == 0f)
    
                R.drawable.baseline_volume_off_24
    
            else
    
                R.drawable.baseline_volume_up_24
    
        )
    
        findViewById<TextView>(
            R.id.textBgSoundFile
        ).text =
            layer.soundDisplayName
                ?: "Ningún sonido seleccionado"
    
        findViewById<TextView>(
            R.id.textBgSoundDuration
        ).text =
    
            if (
                layer.soundDuration > 0
            )
    
                "Duración: ${
                    AudioStorage.formatDuration(
                        layer.soundDuration
                    )
                }"
    
            else
    
                "Duración: --:--.---"
        
        updateVideoModeUI()
    
    }
    
    private fun updateVideoModeUI() {

        FileLogger.log(
            this,
            "Entró a updateVideoModeUI()"
        )
    
        val freeCard =
            findViewById<MaterialCardView>(
                R.id.cardFreeMode
            )
    
        FileLogger.log(
            this,
            "freeCard = $freeCard"
        )
    
        val mode = readVideoFitModeFromUI()
    
        FileLogger.log(
            this,
            "mode = $mode"
        )
    
        
        freeCard.visibility =
            if (mode == VideoFitMode.FREE)
                View.VISIBLE
            else
                View.GONE
        
    }
    
    private fun readVideoFitModeFromUI(): VideoFitMode {

        val toggle =
            findViewById<MaterialButtonToggleGroup>(
                R.id.toggleVideoMode
            )
    
        return when (toggle.checkedButtonId) {
    
            R.id.buttonFit ->
                VideoFitMode.FIT
    
            R.id.buttonFill ->
                VideoFitMode.FILL
    
            R.id.buttonFree ->
                VideoFitMode.FREE
    
            else ->
                VideoFitMode.STRETCH
        }
    }
    
    private fun readOverlaySettingsFromUI(): OverlaySettings {

        val oldOverlay =
            ProjectManager
                .getProject()
                .overlay
    
        val overlay =
            oldOverlay.copy()
    
        overlay.videoPath =
            ProjectManager
                .getProject()
                .overlay
                .videoPath
    
        overlay.opacity =
            findViewById<Slider>(
                R.id.sliderOverlayAlpha
            ).value / 100f
    
        overlay.scale =
            findViewById<Slider>(
                R.id.sliderOverlayZoom
            ).value / 100f
    
        overlay.x =
            findViewById<Slider>(
                R.id.sliderOverlayPosX
            ).value
    
        overlay.y =
            findViewById<Slider>(
                R.id.sliderOverlayPosY
            ).value
    
        overlay.rotation =
            findViewById<Slider>(
                R.id.sliderOverlayRotation
            ).value
    
        overlay.chromaEnabled =
            findViewById<MaterialCheckBox>(
                R.id.checkOverlayChroma
            ).isChecked
        
        overlay.chromaColor =
            ProjectManager
                .getProject()
                .overlay
                .chromaColor
        
        overlay.threshold =
            findViewById<Slider>(
                R.id.sliderChromaThreshold
            ).value
        
        overlay.softness =
            findViewById<Slider>(
                R.id.sliderChromaSoftness
            ).value
        
        // -----------------------------
        // Audio
        // -----------------------------
        
        overlay.soundVolume =
            findViewById<Slider>(
                R.id.sliderOverlaySoundVolume
            ).value / 100f
        
        overlay.soundEnabled =
            overlay.soundVolume > 0f
        
        // -----------------------------
        // Aspect Ratio
        // -----------------------------
        
        overlay.aspectMode =
            when (
                findViewById<RadioGroup>(
                    R.id.radioGroupOverlayAspect
                ).checkedRadioButtonId
            ) {
                R.id.radioOverlayAspectOriginal ->
                    OverlayAspectMode.ORIGINAL
        
                R.id.radioOverlayAspectScreen ->
                    OverlayAspectMode.SCREEN
                
                else ->
                    OverlayAspectMode.ORIGINAL
            }
    
        return overlay
    }
    
    private fun setupFontDropdowns() {

        setupFontDropdown(
            R.id.dropClockFont
        )
    
        setupFontDropdown(
            R.id.dropDateFont
        )
    }
    
    private fun reloadFontLibrary() {

        val clockSelection =
            findViewById<AutoCompleteTextView>(
                R.id.dropClockFont
            ).text
                .toString()
    
        val dateSelection =
            findViewById<AutoCompleteTextView>(
                R.id.dropDateFont
            ).text
                .toString()
    
        setupFontDropdown(
            R.id.dropClockFont,
            clockSelection
        )
    
        setupFontDropdown(
            R.id.dropDateFont,
            dateSelection
        )
    }
    
    private fun setupFontDropdown(

        dropdownId: Int,
    
        selected: String? = null
    
    ) {
    
        val fonts =
            mutableListOf<String>()
    
        fonts.add(
            "Fuente predeterminada"
        )
    
        fonts.addAll(
            FontStorage.getInstalledFonts(
                this
            )
        )
    
        val adapter =
            ArrayAdapter(
                this,
                android.R.layout.simple_list_item_1,
                fonts
            )
    
        val dropdown =
            findViewById<AutoCompleteTextView>(
                dropdownId
            )
    
        dropdown.setAdapter(
            adapter
        )
    
        val index =
            selected?.let {
    
                fonts.indexOf(it)
    
            }?.takeIf {
    
                it >= 0
    
            } ?: 0
    
        dropdown.setText(
            fonts[index],
            false
        )
    
        dropdown.setOnItemClickListener {
    
            _, _, _, _ ->
    
            if (!loadingUI) {
    
                updatePreviewProject()
            }
        }
    }
    
    private fun refreshPreview() {

        findViewById<WallpaperPreviewView>(
            R.id.previewView
        ).refresh()
    }
    
    private fun updatePreviewProject() {

        syncProjectFromUI()
    
        refreshPreview()
    
        editor.save()
    
    }
    
    private fun syncProjectFromUI() {

        val project =
            ProjectManager.getProject()
    
        project.clock =
            readClockSettingsFromUI()
    
        project.overlay =
            readOverlaySettingsFromUI()
        
        readPlaybackSettingsFromUI()
    
        val layer =
            readVideoLayerFromUI()
    
        if (project.layers.isEmpty()) {
            project.layers.add(layer)
        } else {
            project.layers[0] = layer
        }
    
    }
    
    private fun reloadProjectUI(){
        
        loadingUI = true
                
        reloadFontLibrary()
        
        loadVideoLayerSettings()
        
        loadOverlaySettings()
        
        loadChromaSettings()
        
        loadPlaybackSettings()
        
        loadClockSettings()
        
        loadingUI = false
        
        //updatePreviewProject()
        
        refreshPreview()
        
    }
    
    private fun readClockSettingsFromUI(): ClockSettings {

        val clock = ClockSettings()
        
        clock.enabledOnLockScreen =
            findViewById<CheckBox>(
                R.id.checkClockLockScreen
            ).isChecked
    
        clock.enabled =
            findViewById<CheckBox>(
                R.id.checkClock
            ).isChecked
    
        clock.showDate =
            findViewById<CheckBox>(
                R.id.checkDate
            ).isChecked
    
        clock.timeFormat =
            when (
                findViewById<RadioGroup>(
                    R.id.radioTimeFormat
                ).checkedRadioButtonId
            ) {
    
                R.id.radioHHMMSS ->
                    TimeFormat.HH_MM_SS
    
                R.id.radioAMPM ->
                    TimeFormat.HH_MM_AM_PM
    
                else ->
                    TimeFormat.HH_MM
            }
    
        clock.dateFormat =
            when (
                findViewById<RadioGroup>(
                    R.id.radioDateFormat
                ).checkedRadioButtonId
            ) {
    
                R.id.radioDateNumeric ->
                    DateFormat.DD_MM_YYYY
    
                R.id.radioDateLong ->
                    DateFormat.DOW_DD_MON_YYYY
    
                else ->
                    DateFormat.DOW_DD_MON
            }
    
        clock.clockSize =
            findViewById<Slider>(
                R.id.sliderClockSize
            ).value
    
        clock.dateSize =
            findViewById<Slider>(
                R.id.sliderDateSize
            ).value
        
        clock.x =
            findViewById<Slider>(
                R.id.sliderX
            ).value / 100f
        
        clock.y =
            findViewById<Slider>(
                R.id.sliderY
            ).value / 100f
        
        clock.dateSpacing =
            dateSpacingValue
    
        clock.alignment =
            when (
                findViewById<RadioGroup>(
                    R.id.radioAlign
                ).checkedRadioButtonId
            ) {
    
                R.id.radioAlignLeft ->
                    TextAlignment.LEFT
    
                R.id.radioAlignRight ->
                    TextAlignment.RIGHT
    
                else ->
                    TextAlignment.CENTER
            }
    
        clock.clockColor =
            clockColorHex
        
        clock.dateColor =
            dateColorHex
    
        clock.clockFont =
            findViewById<AutoCompleteTextView>(
                R.id.dropClockFont
            ).text
                .toString()
                .takeIf {
                    it != "Fuente predeterminada"
                }
    
        clock.dateFont =
            findViewById<AutoCompleteTextView>(
                R.id.dropDateFont
            ).text
                .toString()
                .takeIf {
                    it != "Fuente predeterminada"
                }
    
        return clock
    }
    
    private fun readVideoLayerFromUI(): VideoLayer {
    
        val oldLayer =
            ProjectManager
                .getProject()
                .layers
                .firstOrNull()
    
        val layer =
            oldLayer ?: VideoLayer()
    
        // -----------------------------
        // Aspect Ratio
        // -----------------------------
        
        layer.fitMode = readVideoFitModeFromUI()

        FileLogger.log(
            this,
            "readVideoLayerFromUI -> fitMode=${layer.fitMode}"
        )
        
        layer.aspectMode =
            when (
                findViewById<RadioGroup>(
                    R.id.radioAspectRatio
                ).checkedRadioButtonId
            ) {
        
                R.id.radioAspect16x9 ->
                    VideoAspectMode.R16_9
        
                R.id.radioAspect16x10 ->
                    VideoAspectMode.R16_10
        
                R.id.radioAspect18x9 ->
                    VideoAspectMode.R18_9
                
                R.id.radioAspect20x9 ->
                VideoAspectMode.R20_9
        
                R.id.radioAspect4x3 ->
                    VideoAspectMode.R4_3
        
                R.id.radioAspect3x2 ->
                    VideoAspectMode.R3_2
        
                else ->
                    VideoAspectMode.ORIGINAL
            }
        // -----------------------------
        // Transformaciones
        // -----------------------------
    
        layer.scale =
            findViewById<Slider>(
                R.id.sliderVideoZoom
            ).value
    
        layer.x =
            findViewById<Slider>(
                R.id.sliderVideoPosX
            ).value
    
        layer.y =
            findViewById<Slider>(
                R.id.sliderVideoPosY
            ).value
    
        // -----------------------------
        // Audio
        // -----------------------------
    
        layer.soundVolume =
            findViewById<Slider>(
                R.id.sliderBgSoundVolume
            ).value / 100f
    
        layer.soundEnabled =
            layer.soundVolume > 0f
    
        return layer
    
    }
    
    private fun setupPreviewSize() {

        val previewCard =
            findViewById<MaterialCardView>(
                R.id.cardPreview
            )
    
        val metrics =
            DisplayMetrics()
    
        windowManager.defaultDisplay.getRealMetrics(
            metrics
        )
    
        val screenWidth =
            metrics.widthPixels
    
        val screenHeight =
            metrics.heightPixels
    
        // El preview ocupará aproximadamente el 75% del alto.
        val previewHeight =
            (screenHeight * 0.65f).toInt()
    
        // Mantener la relación de aspecto REAL del teléfono.
        val previewWidth =
            (previewHeight.toFloat() *
                    screenWidth /
                    screenHeight).toInt()
    
        previewCard.layoutParams =
            previewCard.layoutParams.apply {
    
                width = previewWidth
    
                height = previewHeight
            }
    }
    
    private fun loadDeviceInformation() {

        // ---------------------------------
        // Resolución de pantalla
        // ---------------------------------
    
        val (width, height) =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
    
                val bounds =
                    windowManager
                        .currentWindowMetrics
                        .bounds
    
                bounds.width() to bounds.height()
    
            } else {
    
                val displayMetrics =
                    resources.displayMetrics
    
                displayMetrics.widthPixels to
                        displayMetrics.heightPixels
            }
    
    
        // ---------------------------------
        // Aspect Ratio
        // ---------------------------------
    
        val gcdValue =
            gcd(
                width,
                height
            )
    
        val aspectWidth =
            width / gcdValue
    
        val aspectHeight =
            height / gcdValue
    
    
        val aspectRatio =
            width.toFloat() /
                    height.toFloat()
    
    
        // ---------------------------------
        // OpenGL ES
        // ---------------------------------
    
        val openGlInfo =
            getOpenGLInfo()
    
    
        // ---------------------------------
        // Información del dispositivo
        // ---------------------------------
    
        val manufacturer =
            Build.MANUFACTURER
                .replaceFirstChar {
                    it.uppercase()
                }
    
        val model =
            Build.MODEL
    
    
        val deviceName =
            if (
                model.startsWith(
                    manufacturer,
                    ignoreCase = true
                )
            ) {
    
                model
    
            } else {
    
                "$manufacturer $model"
            }
    
    
        // ---------------------------------
        // Información de Android
        // ---------------------------------
    
        val androidVersion =
            Build.VERSION.RELEASE
    
        val apiLevel =
            Build.VERSION.SDK_INT
    
    
        // ---------------------------------
        // Actualizar UI
        // ---------------------------------
    
        findViewById<TextView>(
            R.id.textScreenResolution
        ).text =
            "Resolución: ${height} × ${width} px"
    
    
        findViewById<TextView>(
            R.id.textAspectRatio
        ).text =
            "Aspect Ratio: " +
                    "$aspectHeight:$aspectWidth " +
                    "(${String.format("%.3f", aspectRatio)})"
    
    
        findViewById<TextView>(
            R.id.textOpenGLVersion
        ).text =
            "OpenGL ES: ${openGlInfo.version}"
    
        findViewById<TextView>(
            R.id.textOpenGLRenderer
        ).text =
            "Renderer: ${openGlInfo.renderer}"
    
        findViewById<TextView>(
            R.id.textOpenGLVendor
        ).text =
            "Vendor: ${openGlInfo.vendor}"
    
        findViewById<TextView>(
            R.id.textDeviceName
        ).text =
            "Dispositivo: $deviceName"
    
    
        findViewById<TextView>(
            R.id.textAndroidVersion
        ).text =
            "Versión Android: Android $androidVersion " +
                    "(API $apiLevel)"
    }
    
    private fun gcd(
        a: Int,
        b: Int
    ): Int {
    
        var x = a
        var y = b
        
        while (y != 0) {
    
            val temp = x % y
    
            x = y
            y = temp
        }
    
        return x
    }
    
    private data class OpenGLInfo(
        val version: String,
        val renderer: String,
        val vendor: String
    )
    
    private fun getOpenGLInfo(): OpenGLInfo {

        var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
        var context: EGLContext = EGL14.EGL_NO_CONTEXT
        var surface: EGLSurface = EGL14.EGL_NO_SURFACE
    
        return try {
    
            // ---------------------------------
            // Obtener display EGL
            // ---------------------------------
    
            display =
                EGL14.eglGetDisplay(
                    EGL14.EGL_DEFAULT_DISPLAY
                )
    
            if (display == EGL14.EGL_NO_DISPLAY) {
                throw RuntimeException(
                    "No se pudo obtener EGLDisplay"
                )
            }
    
            val version = IntArray(2)
    
            if (
                !EGL14.eglInitialize(
                    display,
                    version,
                    0,
                    version,
                    1
                )
            ) {
                throw RuntimeException(
                    "No se pudo inicializar EGL"
                )
            }
    
            // ---------------------------------
            // Elegir configuración EGL
            // ---------------------------------
    
            val configAttributes = intArrayOf(
    
                EGL14.EGL_RENDERABLE_TYPE,
                EGL14.EGL_OPENGL_ES2_BIT,
    
                EGL14.EGL_SURFACE_TYPE,
                EGL14.EGL_PBUFFER_BIT,
    
                EGL14.EGL_RED_SIZE,
                8,
    
                EGL14.EGL_GREEN_SIZE,
                8,
    
                EGL14.EGL_BLUE_SIZE,
                8,
    
                EGL14.EGL_ALPHA_SIZE,
                8,
    
                EGL14.EGL_NONE
            )
    
            val configs =
                arrayOfNulls<EGLConfig>(1)
    
            val numConfigs =
                IntArray(1)
    
            if (
                !EGL14.eglChooseConfig(
                    display,
                    configAttributes,
                    0,
                    configs,
                    0,
                    1,
                    numConfigs,
                    0
                )
            ) {
                throw RuntimeException(
                    "No se pudo elegir EGLConfig"
                )
            }
    
            val config =
                configs[0]
                    ?: throw RuntimeException(
                        "EGLConfig nulo"
                    )
    
            // ---------------------------------
            // Crear superficie invisible 1x1
            // ---------------------------------
    
            val surfaceAttributes = intArrayOf(
    
                EGL14.EGL_WIDTH,
                1,
    
                EGL14.EGL_HEIGHT,
                1,
    
                EGL14.EGL_NONE
            )
    
            surface =
                EGL14.eglCreatePbufferSurface(
                    display,
                    config,
                    surfaceAttributes,
                    0
                )
    
            if (surface == EGL14.EGL_NO_SURFACE) {
                throw RuntimeException(
                    "No se pudo crear EGLSurface"
                )
            }
    
            // ---------------------------------
            // Crear contexto OpenGL ES 2.0
            // ---------------------------------
    
            val contextAttributes = intArrayOf(
    
                EGL14.EGL_CONTEXT_CLIENT_VERSION,
                2,
    
                EGL14.EGL_NONE
            )
    
            context =
                EGL14.eglCreateContext(
                    display,
                    config,
                    EGL14.EGL_NO_CONTEXT,
                    contextAttributes,
                    0
                )
    
            if (context == EGL14.EGL_NO_CONTEXT) {
                throw RuntimeException(
                    "No se pudo crear EGLContext"
                )
            }
    
            // ---------------------------------
            // Activar contexto
            // ---------------------------------
    
            if (
                !EGL14.eglMakeCurrent(
                    display,
                    surface,
                    surface,
                    context
                )
            ) {
                throw RuntimeException(
                    "No se pudo activar EGLContext"
                )
            }
    
            // ---------------------------------
            // Consultar información real
            // ---------------------------------
    
            val glVersion =
                GLES20.glGetString(
                    GLES20.GL_VERSION
                )
                    ?: "Desconocido"
    
            val renderer =
                GLES20.glGetString(
                    GLES20.GL_RENDERER
                )
                    ?: "Desconocido"
    
            val vendor =
                GLES20.glGetString(
                    GLES20.GL_VENDOR
                )
                    ?: "Desconocido"
    
            OpenGLInfo(
                version = glVersion,
                renderer = renderer,
                vendor = vendor
            )
    
        } catch (e: Exception) {
    
            OpenGLInfo(
                version = "Desconocido",
                renderer = "Desconocido",
                vendor = "Desconocido"
            )
    
        } finally {
    
            // ---------------------------------
            // Liberar contexto
            // ---------------------------------
    
            if (
                display != EGL14.EGL_NO_DISPLAY
            ) {
    
                EGL14.eglMakeCurrent(
                    display,
                    EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_CONTEXT
                )
    
                if (
                    context != EGL14.EGL_NO_CONTEXT
                ) {
    
                    EGL14.eglDestroyContext(
                        display,
                        context
                    )
                }
    
                if (
                    surface != EGL14.EGL_NO_SURFACE
                ) {
    
                    EGL14.eglDestroySurface(
                        display,
                        surface
                    )
                }
    
                EGL14.eglTerminate(
                    display
                )
            }
        }
    }
    
}
