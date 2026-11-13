package cz.kuba.dash407.data

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import cz.kuba.dash407.nav.Mapy
import cz.kuba.dash407.nav.NavNotice
import cz.kuba.dash407.nav.NavNoticeState

/**
 * Služba přístupu k oznámením. Android díky ní dovolí číst běžící přehrávače.
 * Z obsahu oznámení čte jen oznámení navigace aplikace Mapy.com (další manévr).
 */
class MediaListener : NotificationListenerService() {
    override fun onListenerConnected() {
        try {
            activeNotifications?.forEach { onNotificationPosted(it) }
        } catch (e: Exception) {
            // Seznam oznámení teď nejde přečíst.
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null || sbn.packageName != Mapy.APP) return
        val n = sbn.notification ?: return
        val extras = n.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val sub = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
        if (title.isNullOrBlank() && text.isNullOrBlank()) return
        val icon: ImageBitmap? = try {
            n.getLargeIcon()?.loadDrawable(this)?.toBitmap(128, 128)?.asImageBitmap()
        } catch (e: Exception) {
            null
        }
        NavNotice.set(NavNoticeState(sbn.key, title, text, sub, icon, System.currentTimeMillis()))
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn != null && sbn.packageName == Mapy.APP) NavNotice.clear(sbn.key)
    }
}

data class NowPlayingState(
    val allowed: Boolean = false,
    val title: String? = null,
    val artist: String? = null,
    val playing: Boolean = false,
    val hasSession: Boolean = false,
    val art: ImageBitmap? = null,
    val packageName: String? = null
)

/** Právě hrající hudba z libovolné aplikace (rádio, Spotify, …) a její ovládání. */
object NowPlaying {
    private val _state = kotlinx.coroutines.flow.MutableStateFlow(NowPlayingState())
    val state: kotlinx.coroutines.flow.StateFlow<NowPlayingState> = _state

    private var manager: MediaSessionManager? = null
    private var component: ComponentName? = null
    private var controller: MediaController? = null
    private var listening = false
    private var artKey: Any? = null
    private var artCache: ImageBitmap? = null

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

    private fun artOf(meta: MediaMetadata?): ImageBitmap? {
        val bmp: Bitmap = meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            ?: return null
        if (bmp === artKey) return artCache
        artKey = bmp
        artCache = try {
            val side = 320
            val scaled = if (bmp.width > side || bmp.height > side) Bitmap.createScaledBitmap(bmp, side, side * bmp.height / bmp.width.coerceAtLeast(1), true) else bmp
            scaled.asImageBitmap()
        } catch (e: Exception) {
            null
        }
        return artCache
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
            art = artOf(meta),
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
