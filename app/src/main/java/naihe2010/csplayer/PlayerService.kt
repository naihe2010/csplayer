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
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import java.io.File

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
        const val ACTION_EXIT = "naihe2010.csplayer.ACTION_EXIT"
        const val EXTRA_IS_PLAYING = "isPlaying"
        const val EXTRA_TITLE = "title"
        const val EXTRA_DURATION = "duration"
        const val EXTRA_CURRENT_POSITION = "currentPosition"
        const val EXTRA_CURRENT_MEDIA_ITEM_INDEX = "currentMediaItemIndex"
    }

    private val controlReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_TOGGLE_PLAY_PAUSE -> togglePlayPause()
                ACTION_REWIND -> seekBackward()
                ACTION_FORWARD -> seekForward()
                SettingFragment.ACTION_PLAYBACK_RATE_CHANGED -> {
                    val rate = intent.getFloatExtra(SettingFragment.EXTRA_PLAYBACK_RATE, 1.0f)
                    player.setPlaybackSpeed(rate)
                }

                SettingFragment.ACTION_PLAYBACK_ORDER_CHANGED -> {
                    val orderName = intent.getStringExtra(SettingFragment.EXTRA_PLAYBACK_ORDER)
                    val newOrder = PlaybackOrder.valueOf(orderName ?: PlaybackOrder.SEQUENTIAL.name)
                    playerConfig = playerConfig.updatePlaybackOrder(newOrder)
                    playerConfig.save(this@PlayerService)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        playerConfig = PlayerConfig.getInstance(this)
        player = ExoPlayer.Builder(this).build()
        player.setPlaybackSpeed(playerConfig.playbackRate)
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                sendStateBroadcast()
                if (isPlaying) {
                    handler.post(progressUpdateRunnable)
                } else {
                    handler.removeCallbacks(progressUpdateRunnable)
                }
                updateNotification()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                sendStateBroadcast()
                mediaItem?.let {
                    val currentFile = it.mediaId
                    playerConfig = playerConfig.updateCurrentFile(currentFile)
                    playerConfig.save(this@PlayerService)
                }
                updateNotification()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    sendStateBroadcast()
                } else if (playbackState == Player.STATE_ENDED) {
                    when (playerConfig.playbackOrder) {
                        PlaybackOrder.SEQUENTIAL -> {
                            if (player.hasNextMediaItem()) {
                                player.seekToNextMediaItem()
                            } else {
                                player.stop()
                                player.clearMediaItems()
                                stopSelf()
                            }
                        }

                        PlaybackOrder.RANDOM -> {
                            val currentMediaId = player.currentMediaItem?.mediaId
                            val availableItems =
                                currentPlaylist.filter { it.filePath != currentMediaId }

                            if (availableItems.isNotEmpty()) {
                                val randomIndex = availableItems.indices.random()
                                val randomPlaylistItem = availableItems[randomIndex]
                                val mediaItem = MediaItem.Builder()
                                    .setUri(randomPlaylistItem.filePath)
                                    .setClippingConfiguration(
                                        MediaItem.ClippingConfiguration.Builder()
                                            .setStartPositionMs(randomPlaylistItem.startTimeMs)
                                            .setEndPositionMs(randomPlaylistItem.endTimeMs)
                                            .build()
                                    )
                                    .build()
                                player.setMediaItem(mediaItem)
                                player.prepare()
                                player.play()
                            } else {
                                // Only one item in the playlist, or all other items have been played
                                player.stop()
                                player.clearMediaItems()
                                stopSelf()
                            }
                        }

                        PlaybackOrder.LOOP -> {
                            player.seekTo(0)
                            player.play()
                        }
                    }
                }
                sendStateBroadcast()
            }
        })
        val intentFilter = IntentFilter().apply {
            addAction(ACTION_TOGGLE_PLAY_PAUSE)
            addAction(ACTION_REWIND)
            addAction(ACTION_FORWARD)
            addAction(SettingFragment.ACTION_PLAYBACK_RATE_CHANGED)
            addAction(SettingFragment.ACTION_PLAYBACK_ORDER_CHANGED)
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(controlReceiver, intentFilter)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE_PLAY_PAUSE -> togglePlayPause()
            ACTION_REWIND -> seekBackward()
            ACTION_FORWARD -> seekForward()
            ACTION_EXIT -> {
                stopSelf()
                val exitIntent = Intent(MainActivity.ACTION_EXIT_APP)
                LocalBroadcastManager.getInstance(this).sendBroadcast(exitIntent)
            }
        }
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

    private var currentPlaylist: List<PlaylistItem> = emptyList()

    fun play(
        playlist: List<PlaylistItem>,
        startIndex: Int,
        startPositionMs: Long = 0L
    ) {
        currentPlaylist = playlist
        if (playlist.isEmpty()) {
            player.clearMediaItems()
            return
        }

        val mediaItems = playlist.map { item ->
            val fileName = File(item.filePath).name
            MediaItem.Builder()
                .setUri(item.filePath)
                .setMediaMetadata(
                    androidx.media3.common.MediaMetadata.Builder()
                        .setTitle(fileName)
                        .build()
                )
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(item.startTimeMs)
                        .setEndPositionMs(item.endTimeMs)
                        .build()
                )
                .build()
        }

        player.setMediaItems(mediaItems, startIndex, startPositionMs)
        player.prepare()
        player.play()

        val currentItem = playlist[startIndex]
        playerConfig = playerConfig.updateCurrentDirectory(File(currentItem.filePath).parent)
        playerConfig = playerConfig.updateCurrentFile(currentItem.filePath)
        playerConfig.save(this@PlayerService)

        updateNotification()
    }

    fun togglePlayPause() {
        if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
    }

    fun seekForward() {
        player.seekToNextMediaItem()
    }

    fun seekBackward() {
        player.seekToPreviousMediaItem()
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
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
        intent.putExtra(EXTRA_CURRENT_MEDIA_ITEM_INDEX, player.currentMediaItemIndex)
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