package com.romaster.livewallengine

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast

import androidx.appcompat.app.AppCompatActivity

import com.romaster.livewallengine.editor.MainEditorController
import com.romaster.livewallengine.font.FontPicker
import com.romaster.livewallengine.font.FontStorage
import com.romaster.livewallengine.model.DateFormat
import com.romaster.livewallengine.model.TextAlignment
import com.romaster.livewallengine.model.TimeFormat
import com.romaster.livewallengine.model.ClockSettings
import com.romaster.livewallengine.project.ProjectManager
import com.romaster.livewallengine.ui.PreviewView
import com.romaster.livewallengine.ui.SimpleSeekListener
import com.romaster.livewallengine.util.ColorPresets
import com.romaster.livewallengine.video.VideoPicker
import com.romaster.livewallengine.video.VideoStorage

class MainActivity : AppCompatActivity() {

    private lateinit var editor:
        MainEditorController
    
    private var loadingUI = false
    
    private var updatingClockColor =
        false

    private var updatingDateColor =
        false

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
    
        super.onCreate(savedInstanceState)
    
        setContentView(
            R.layout.activity_main
        )
    
        editor =
            MainEditorController(this)
    
        editor.load()
    
        setupColorSpinners()

        setupFontSpinners()
        
        setupHexEditors()
        
        loadingUI = true
        
        loadClockSettings()
        
        loadingUI = false
        
        setupSeekLabels()
    
        updatePreviewProject()
        
        setupButtons()
    }
    
    private fun setupButtons() {

        findViewById<Button>(
            R.id.buttonSelectVideo
        ).setOnClickListener {
    
            VideoPicker.open(this)
        }
    
        findViewById<Button>(
            R.id.buttonImportFont
        ).setOnClickListener {
        
            FontPicker.open(this)
        }
    
        findViewById<Button>(
            R.id.buttonSave
        ).setOnClickListener {
    
            saveClockSettings()
    
            refreshPreview()
    
            editor.save()
        }
    }

    private fun loadClockSettings() {

        val clock =
            ProjectManager
                .getProject()
                .clock
        
        val fonts =
            FontStorage.getInstalledFonts(
                this
            )

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
        findViewById<SeekBar>(
            R.id.seekClockSize
        ).progress =
            clock.clockSize.toInt()
        
        findViewById<SeekBar>(
            R.id.seekDateSize
        ).progress =
            clock.dateSize.toInt()
        
        findViewById<SeekBar>(
            R.id.seekX
        ).progress =
            (clock.x * 100f).toInt()
        
        findViewById<SeekBar>(
            R.id.seekY
        ).progress =
            (clock.y * 100f).toInt()
        
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
        
        val clockSpinner =
            findViewById<Spinner>(
                R.id.spinnerClockColor
            )
        
        clockSpinner.setSelection(
        
            ColorPresets.names().indexOf(
        
                ColorPresets.getPresetName(
                    clock.clockColor
                )
            )
        )
        
        val dateSpinner =
            findViewById<Spinner>(
                R.id.spinnerDateColor
            )
        
        dateSpinner.setSelection(
        
            ColorPresets.names().indexOf(
        
                ColorPresets.getPresetName(
                    clock.dateColor
                )
            )
        )
        
        findViewById<EditText>(
            R.id.editClockHex
        ).setText(
            clock.clockColor
        )
        
        findViewById<EditText>(
            R.id.editDateHex
        ).setText(
            clock.dateColor
        )
        
        clock.clockFont?.let {

            val index =
                fonts.indexOf(it)
        
            if (index >= 0) {
        
                findViewById<Spinner>(
                    R.id.spinnerClockFont
                ).setSelection(index)
            }
        }
        
        clock.dateFont?.let {

            val index =
                fonts.indexOf(it)
        
            if (index >= 0) {
        
                findViewById<Spinner>(
                    R.id.spinnerDateFont
                ).setSelection(index)
            }
        }
        
        setupFontSpinner(
            R.id.spinnerClockFont,
            clock.clockFont
        )
        
        setupFontSpinner(
            R.id.spinnerDateFont,
            clock.dateFont
        )
        
        findViewById<CheckBox>(
            R.id.checkClock
        ).setOnCheckedChangeListener { _, _ ->
        
            updatePreviewProject()
        }
        
        findViewById<CheckBox>(
            R.id.checkDate
        ).setOnCheckedChangeListener { _, _ ->
        
            updatePreviewProject()
        }
        
        findViewById<RadioGroup>(
            R.id.radioTimeFormat
        ).setOnCheckedChangeListener { _, _ ->
        
            updatePreviewProject()
        }
        
        findViewById<RadioGroup>(
            R.id.radioDateFormat
        ).setOnCheckedChangeListener { _, _ ->
        
            updatePreviewProject()
        }
        
        findViewById<RadioGroup>(
            R.id.radioAlign
        ).setOnCheckedChangeListener { _, _ ->
        
            updatePreviewProject()
        }
    }
    
    private fun updateSpinnerFromHex(

        spinner: Spinner,
    
        hex: String
    
    ) {
    
        val name =
    
            ColorPresets.getPresetName(
                hex
            )
    
        val index =
    
            ColorPresets.names()
                .indexOf(name)
    
        if (index >= 0) {
    
            spinner.setSelection(index)
        }
    }
    
    private fun updateHexFromSpinner(

        spinner: Spinner,
    
        edit: EditText
    
    ) {
    
        val selected =
    
            spinner.selectedItem.toString()
    
        if (
    
            ColorPresets.isCustom(
                selected
            )
    
        ) {
    
            if (
    
                edit.text.isBlank()
    
            ) {
    
                edit.setText(
    
                    ColorPresets
                        .defaultCustomColor()
    
                )
            }
    
        } else {
    
            edit.setText(
    
                ColorPresets.getHex(
                    selected
                )
            )
        }
    }
    
    private fun setupSeekLabels() {

        val clockSizeSeek =
            findViewById<SeekBar>(
                R.id.seekClockSize
            )
    
        val dateSizeSeek =
            findViewById<SeekBar>(
                R.id.seekDateSize
            )
    
        val xSeek =
            findViewById<SeekBar>(
                R.id.seekX
            )
    
        val ySeek =
            findViewById<SeekBar>(
                R.id.seekY
            )
    
        val clockSizeLabel =
            findViewById<TextView>(
                R.id.textClockSizeValue
            )
    
        val dateSizeLabel =
            findViewById<TextView>(
                R.id.textDateSizeValue
            )
    
        val xLabel =
            findViewById<TextView>(
                R.id.textXValue
            )
    
        val yLabel =
            findViewById<TextView>(
                R.id.textYValue
            )
    
        clockSizeLabel.text =
            clockSizeSeek.progress.toString()
    
        dateSizeLabel.text =
            dateSizeSeek.progress.toString()
    
        xLabel.text =
            xSeek.progress.toString()
    
        yLabel.text =
            ySeek.progress.toString()
    
        clockSizeSeek
            .setOnSeekBarChangeListener(
                SimpleSeekListener {
    
                    clockSizeLabel.text =
                        it.toString()
                    updatePreviewProject()
                }
            )
    
        dateSizeSeek
            .setOnSeekBarChangeListener(
                SimpleSeekListener {
    
                    dateSizeLabel.text =
                        it.toString()
                    updatePreviewProject()
                }
            )
    
        xSeek.setOnSeekBarChangeListener(
            SimpleSeekListener {
    
                xLabel.text =
                    it.toString()
                updatePreviewProject()
            }
        )
    
        ySeek.setOnSeekBarChangeListener(
            SimpleSeekListener {
    
                yLabel.text =
                    it.toString()
                updatePreviewProject()
            }
        )
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

    @Deprecated(
        "Activity Result API"
    )
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
    
        if (
            requestCode ==
            VideoPicker.REQUEST_CODE
        ) {
    
            if (
                resultCode ==
                Activity.RESULT_OK
            ) {
    
                data?.data?.let {
    
                    val path =
                        VideoStorage.importVideo(
                            this,
                            it
                        )
    
                    ProjectManager
                        .setWallpaperVideo(
                            path
                        )
    
                    editor.save()
                }
            }
        }
        
        if (
            requestCode ==
            FontPicker.REQUEST_FONT
        ) {
        
            if (
                resultCode ==
                Activity.RESULT_OK
            ) {
        
                data?.data?.let {
        
                    FontStorage.importFont(
                        this,
                        it
                    )
        
                    reloadFontLibrary()
                    
                    editor.save()
                }
            }
        }
    }
    private fun setupColorSpinners() {

        val adapter =
            ArrayAdapter(
                this,
                android.R.layout.simple_spinner_item,
                ColorPresets.names()
            )
    
        adapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        )
    
        findViewById<Spinner>(
            R.id.spinnerClockColor
        ).adapter =
            adapter
    
        findViewById<Spinner>(
            R.id.spinnerDateColor
        ).adapter =
            adapter
    }
    
    private fun setupHexEditors() {

        setupColorControl(
    
            spinner =
                findViewById(
                    R.id.spinnerClockColor
                ),
    
            editor =
                findViewById(
                    R.id.editClockHex
                ),
    
            isClock = true
        )
    
        setupColorControl(
    
            spinner =
                findViewById(
                    R.id.spinnerDateColor
                ),
    
            editor =
                findViewById(
                    R.id.editDateHex
                ),
    
            isClock = false
        )
    }
    
    private fun setupColorControl(

        spinner: Spinner,
    
        editor: EditText,
    
        isClock: Boolean
    
    ) {
    
        spinner.onItemSelectedListener =
    
            object :
                AdapterView.OnItemSelectedListener {
    
                override fun onItemSelected(
    
                    parent: AdapterView<*>?,
    
                    view: View?,
    
                    position: Int,
    
                    id: Long
    
                ) {
    
                    if (
    
                        if (isClock)
    
                            updatingClockColor
    
                        else
    
                            updatingDateColor
    
                    ) {
    
                        return
                    }
    
                    if (isClock) {
    
                        updatingClockColor = true
    
                    } else {
    
                        updatingDateColor = true
                    }
    
                    updateHexFromSpinner(
    
                        spinner,
    
                        editor
                    )
    
                    updatePreviewProject()
    
                    if (isClock) {
    
                        updatingClockColor = false
    
                    } else {
    
                        updatingDateColor = false
                    }
                }
    
                override fun onNothingSelected(
    
                    parent: AdapterView<*>?
    
                ) {
                }
            }
    
        editor.addTextChangedListener(
    
            object :
                android.text.TextWatcher {
    
                override fun beforeTextChanged(
    
                    s: CharSequence?,
    
                    start: Int,
    
                    count: Int,
    
                    after: Int
    
                ) {
                }
    
                override fun onTextChanged(
    
                    s: CharSequence?,
    
                    start: Int,
    
                    before: Int,
    
                    count: Int
    
                ) {
                }
    
                override fun afterTextChanged(
    
                    editable:
                    android.text.Editable?
    
                ) {
    
                    val hex =
    
                        editable
                            .toString()
                            .trim()
                            .uppercase()
    
                    if (
    
                        if (isClock)
    
                            updatingClockColor
    
                        else
    
                            updatingDateColor
    
                    ) {
    
                        return
                    }
    
                    if (
    
                        !isValidHexColor(
                            hex
                        )
    
                    ) {
    
                        return
                    }
    
                    if (isClock) {
    
                        updatingClockColor = true
    
                    } else {
    
                        updatingDateColor = true
                    }
    
                    updateSpinnerFromHex(
    
                        spinner,
    
                        hex
                    )
    
                    updatePreviewProject()
    
                    if (isClock) {
    
                        updatingClockColor = false
    
                    } else {
    
                        updatingDateColor = false
                    }
                }
            }
        )
    }
    
    private fun isValidHexColor(
        value: String
    ): Boolean {
    
        return Regex(
            "^#[0-9A-Fa-f]{6}$"
        ).matches(
            value.trim()
        )
    }
    
    private fun setupFontSpinners() {

        setupFontSpinner(
            R.id.spinnerClockFont
        )
    
        setupFontSpinner(
            R.id.spinnerDateFont
        )
    }
    
    private fun reloadFontLibrary() {

        val clockSelection =
            findViewById<Spinner>(
                R.id.spinnerClockFont
            ).selectedItem?.toString()
    
        val dateSelection =
            findViewById<Spinner>(
                R.id.spinnerDateFont
            ).selectedItem?.toString()
    
        setupFontSpinner(
            R.id.spinnerClockFont,
            clockSelection
        )
    
        setupFontSpinner(
            R.id.spinnerDateFont,
            dateSelection
        )
    }
    
    private fun setupFontSpinner(
        spinnerId: Int,
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
                android.R.layout.simple_spinner_item,
                fonts
            )
    
        adapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        )
    
        val spinner =
            findViewById<Spinner>(
                spinnerId
            )
    
        spinner.adapter =
            adapter
    
        val index =
            selected?.let {
    
                fonts.indexOf(it)
    
            }?.takeIf {
    
                it >= 0
    
            } ?: 0
    
        spinner.setSelection(
            index,
            false
        )
    
        spinner.onItemSelectedListener =
            object :
                AdapterView.OnItemSelectedListener {
    
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
    
                    if (!loadingUI) {
    
                        updatePreviewProject()
                    }
                }
    
                override fun onNothingSelected(
                    parent: AdapterView<*>?
                ) {
                }
            }
    }
    
    private fun refreshPreview() {

        findViewById<PreviewView>(
            R.id.previewView
        ).setProject(
            ProjectManager.getProject()
        )
    }
    private fun updatePreviewProject() {

        ProjectManager
            .getProject()
            .clock =
            readClockSettingsFromUI()
    
        refreshPreview()
    }
    
    private fun readClockSettingsFromUI(): ClockSettings {

        val clock = ClockSettings()
    
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
            findViewById<SeekBar>(
                R.id.seekClockSize
            ).progress.toFloat()
    
        clock.dateSize =
            findViewById<SeekBar>(
                R.id.seekDateSize
            ).progress.toFloat()
    
        clock.x =
            findViewById<SeekBar>(
                R.id.seekX
            ).progress / 100f
    
        clock.y =
            findViewById<SeekBar>(
                R.id.seekY
            ).progress / 100f
    
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
    
        val clockPreset =
            findViewById<Spinner>(
                R.id.spinnerClockColor
            ).selectedItem.toString()
    
        clock.clockColorPreset =
            clockPreset
    
        clock.clockColor =
            if (
                ColorPresets.isCustom(
                    clockPreset
                )
            ) {
    
                val hex =
                    findViewById<EditText>(
                        R.id.editClockHex
                    ).text.toString()
    
                if (
                    isValidHexColor(
                        hex
                    )
                ) {
                    hex.uppercase()
                } else {
                    "#FFFFFF"
                }
    
            } else {
    
                ColorPresets.getHex(
                    clockPreset
                )
            }
    
        val datePreset =
            findViewById<Spinner>(
                R.id.spinnerDateColor
            ).selectedItem.toString()
    
        clock.dateColorPreset =
            datePreset
    
        clock.dateColor =
            if (
                ColorPresets.isCustom(
                    datePreset
                )
            ) {
    
                val hex =
                    findViewById<EditText>(
                        R.id.editDateHex
                    ).text.toString()
    
                if (
                    isValidHexColor(
                        hex
                    )
                ) {
                    hex.uppercase()
                } else {
                    "#FFFFFF"
                }
    
            } else {
    
                ColorPresets.getHex(
                    datePreset
                )
            }
    
        clock.clockFont =
            findViewById<Spinner>(
                R.id.spinnerClockFont
            ).selectedItem
                .toString()
                .takeIf {
                    it != "Fuente predeterminada"
                }
    
        clock.dateFont =
            findViewById<Spinner>(
                R.id.spinnerDateFont
            ).selectedItem
                .toString()
                .takeIf {
                    it != "Fuente predeterminada"
                }
    
        return clock
    }
}