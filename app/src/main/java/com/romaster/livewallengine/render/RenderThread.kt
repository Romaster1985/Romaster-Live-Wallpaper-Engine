package com.romaster.livewallengine.render

class RenderThread(
    private val frameCallback: () -> Unit
) : Thread() {

    @Volatile
    private var running = true

    override fun run() {

        while (running) {

            frameCallback()

            try {

                sleep(16)

            } catch (_: InterruptedException) {
            }
        }
    }

    fun shutdown() {

        running = false

        interrupt()
    }
}