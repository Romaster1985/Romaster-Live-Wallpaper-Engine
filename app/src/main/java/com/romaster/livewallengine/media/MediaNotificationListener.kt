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
 */

package com.romaster.livewallengine.media

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Habilita [android.media.session.MediaSessionManager.getActiveSessions] para leer
 * metadatos del reproductor (Spotify, YT Music, etc.), igual que KLWP.
 * El usuario debe activar el acceso a notificaciones en Ajustes del sistema.
 */
class MediaNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        MusicInfoProvider.onListenerConnected(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        MusicInfoProvider.refresh(this)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        MusicInfoProvider.refresh(this)
    }
}
