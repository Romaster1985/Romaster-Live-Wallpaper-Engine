/*
 * Copyright 2026 Román Ignacio Romero (Romaster)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Nota: Este proyecto incluye ColorPickerView (skydoves) licenciado bajo Apache 2.0.
 */

package com.romaster.livewallengine.render

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.view.SurfaceHolder

class EGLHelper {

    private var display =
        EGL14.EGL_NO_DISPLAY

    private var context =
        EGL14.EGL_NO_CONTEXT

    private var surface =
        EGL14.EGL_NO_SURFACE

    private var config:
        EGLConfig? = null

    fun initialize(
        holder: SurfaceHolder
    ): Boolean {

        display =
            EGL14.eglGetDisplay(
                EGL14.EGL_DEFAULT_DISPLAY
            )

        if (
            display == EGL14.EGL_NO_DISPLAY
        ) {
            return false
        }

        val version =
            IntArray(2)

        if (
            !EGL14.eglInitialize(
                display,
                version,
                0,
                version,
                1
            )
        ) {
            return false
        }

        val attributes =
            intArrayOf(

                EGL14.EGL_RENDERABLE_TYPE,
                EGL14.EGL_OPENGL_ES2_BIT,

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

                attributes,
                0,

                configs,
                0,

                1,

                numConfigs,
                0
            )
        ) {
            return false
        }

        config =
            configs[0]

        val contextAttributes =
            intArrayOf(

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

        if (
            context == EGL14.EGL_NO_CONTEXT
        ) {
            return false
        }

        surface =
            EGL14.eglCreateWindowSurface(

                display,

                config,

                holder.surface,

                intArrayOf(
                    EGL14.EGL_NONE
                ),

                0
            )

        if (
            surface == EGL14.EGL_NO_SURFACE
        ) {
            return false
        }

        return EGL14.eglMakeCurrent(

            display,

            surface,

            surface,

            context
        )
    }

    fun swapBuffers(): Boolean {

        return EGL14.eglSwapBuffers(
            display,
            surface
        )
    }

    fun getError(): Int {

        return EGL14.eglGetError()
    }

    fun release() {

        if (
            display == EGL14.EGL_NO_DISPLAY
        ) {
            return
        }

        EGL14.eglMakeCurrent(

            display,

            EGL14.EGL_NO_SURFACE,

            EGL14.EGL_NO_SURFACE,

            EGL14.EGL_NO_CONTEXT
        )

        if (
            surface != EGL14.EGL_NO_SURFACE
        ) {

            EGL14.eglDestroySurface(
                display,
                surface
            )

            surface =
                EGL14.EGL_NO_SURFACE
        }

        if (
            context != EGL14.EGL_NO_CONTEXT
        ) {

            EGL14.eglDestroyContext(
                display,
                context
            )

            context =
                EGL14.EGL_NO_CONTEXT
        }

        EGL14.eglTerminate(
            display
        )

        display =
            EGL14.EGL_NO_DISPLAY
    }
}