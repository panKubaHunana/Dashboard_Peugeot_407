package cz.kuba.dash407.data

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Služba oznámení: Android díky ní aplikaci dovolí číst běžící přehrávače.
 * Z obsahu oznámení se čtou jen oznámení navigace Mapy.com (viz [NavRepo]).
 */
class MediaListener : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        try {
            activeNotifications?.forEach { NavRepo.posted(this, it) }
        } catch (e: Exception) {
            // Seznam oznámení teď není k dispozici.
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn != null) NavRepo.posted(this, sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn != null) NavRepo.removed(sbn)
    }
}

data class NowPlayingState(
    val allowed: Boolean = false,
    val title: String? = null,
    val artist: String? = null,
    val playing: Boolean = false,
    val hasSession: Boolean = false,
    val art: Bitmap? = null,
    val packageName: String? = null
)

/** Právě hrající hudba z libovolné aplikace (rádio, Spotify, …) a její ovládání. */
object NowPlaying {
    private val _state = MutableStateFlow(NowPlayingState())
    val state: StateFlow<NowPlayingState> = _state

    private var manager: MediaSessionManager? = null
    private var component: ComponentName? = null
    private var controller: MediaController? = null
    private var listening = false

    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { list -> pick(list) }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onSessionDestroyed() {
            controller = null
            publish()
        }
    }

    /** Volá se při každém návratu do aplikace: přístup mohl být mezitím povolen. */
    fun refresh(context: Context) {
        val app = context.applicationContext
        val m = manager ?: (app.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager).also { manager = it }
        val c = component ?: ComponentName(app, MediaListener::class.java).also { component = it }
        if (m == null) return
        try {
            if (!listening) {
                m.addOnActiveSessionsChangedListener(sessionsChanged, c)
                listening = true
            }
            pick(m.getActiveSessions(c))
        } catch (e: SecurityException) {
            // Přístup k oznámením zatím není povolený.
            listening = false
            _state.value = NowPlayingState(allowed = false)
        }
    }

    private fun pick(list: List<MediaController>?) {
        val best = list?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list?.firstOrNull()
        if (best?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(controllerCallback)
            controller = best
            best?.registerCallback(controllerCallback)
        }
        publish()
    }

    private fun publish() {
        val c = controller
        val meta = c?.metadata
        _state.value = NowPlayingState(
            allowed = true,
            title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE),
            artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST),
            playing = c?.playbackState?.state == PlaybackState.STATE_PLAYING,
            hasSession = c != null,
            art = meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_ART),
            packageName = c?.packageName
        )
    }

    fun playPause() {
        val c = controller ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
    }

    fun next() {
        controller?.transportControls?.skipToNext()
    }

    fun previous() {
        controller?.transportControls?.skipToPrevious()
    }
}
