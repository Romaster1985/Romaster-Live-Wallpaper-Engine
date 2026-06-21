package com.romaster.livewallengine.ui

import android.widget.SeekBar

class SimpleSeekListener(
    private val callback:
    (Int) -> Unit
) : SeekBar.OnSeekBarChangeListener {

    override fun onProgressChanged(
        seekBar: SeekBar?,
        progress: Int,
        fromUser: Boolean
    ) {

        callback(progress)
    }

    override fun onStartTrackingTouch(
        seekBar: SeekBar?
    ) {
    }

    override fun onStopTrackingTouch(
        seekBar: SeekBar?
    ) {
    }
}