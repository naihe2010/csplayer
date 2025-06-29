package naihe2010.csplayer

import android.app.Notification
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

class PlayerService : Service() {
    private lateinit var player: ExoPlayer
    private val binder = PlayerBinder()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var playerConfig: PlayerConfig

    companion object {
        const val ACTION_STATE_CHANGED = "naihe2010.csplayer.ACTION_STATE_CHANGED"
        const val ACTION_TOGGLE_PLAY_PAUSE = "naihe2010.csplayer.ACTION_TOGGLE_PLAY_PAUSE"
        const val ACTION_REWIND = "naihe2010.csplayer.ACTION_REWIND"
        const val ACTION_FORWARD = "naihe2010.csplayer.ACTION_FORWARD"
        const val EXTRA_IS_PLAYING = "isPlaying"
        const val EXTRA_TITLE = "title"
        const val EXTRA_DURATION = "duration"
        const val EXTRA_CURRENT_POSITION = "currentPosition"
    }

    private val controlReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_TOGGLE_PLAY_PAUSE -> togglePlayPause()
                ACTION_REWIND -> seekBackward()
                ACTION_FORWARD -> seekForward()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        playerConfig = PlayerConfig.getInstance(this)
        player = ExoPlayer.Builder(this).build()
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                sendStateBroadcast()
                if (isPlaying) {
                    handler.post(progressUpdateRunnable)
                } else {
                    handler.removeCallbacks(progressUpdateRunnable)
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                sendStateBroadcast()
            }
        })
        val intentFilter = IntentFilter().apply {
            addAction(ACTION_TOGGLE_PLAY_PAUSE)
            addAction(ACTION_REWIND)
            addAction(ACTION_FORWARD)
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(controlReceiver, intentFilter)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // This method is kept for starting the service from background
        return START_STICKY
    }

    override fun onDestroy() {
        player.release()
        handler.removeCallbacks(progressUpdateRunnable)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(controlReceiver)
        super.onDestroy()
    }

    fun stopService() {
        stopForeground(true)
        stopSelf()
    }

    inner class PlayerBinder : Binder() {
        fun getService(): PlayerService = this@PlayerService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    fun play(
        directory: String,
        file: String,
        url: String,
        startTimeMs: Long = 0L,
        endTimeMs: Long = C.TIME_UNSET
    ) {
        playerConfig = playerConfig.updateCurrentDirectory(directory)
        playerConfig = playerConfig.updateCurrentFile(file)
        playerConfig.save(this@PlayerService)

        val mediaItem = MediaItem.Builder()
            .setUri(url)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(startTimeMs)
                    .setEndPositionMs(endTimeMs)
                    .build()
            )
            .build()
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
        updateNotification()
        handler.post(progressUpdateRunnable)
    }

    fun togglePlayPause() {
        if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
    }

    fun seekForward() {
        player.seekForward()
    }

    fun seekBackward() {
        player.seekBack()
    }

    private fun updateNotification() {
        val notification: Notification = MediaNotificationManager(this, player).buildNotification()
        startForeground(1, notification)
    }

    private fun sendStateBroadcast() {
        val intent = Intent(ACTION_STATE_CHANGED)
        intent.putExtra(EXTRA_IS_PLAYING, player.isPlaying)
        player.currentMediaItem?.mediaMetadata?.title?.let {
            intent.putExtra(EXTRA_TITLE, it.toString())
        }
        intent.putExtra(EXTRA_DURATION, player.duration)
        intent.putExtra(EXTRA_CURRENT_POSITION, player.currentPosition)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private val progressUpdateRunnable = object : Runnable {
        override fun run() {
            if (player.isPlaying) {
                playerConfig = playerConfig.updateCurrentPosition(player.currentPosition)
                sendStateBroadcast()
                handler.postDelayed(this, 1000)
            }
        }
    }
}