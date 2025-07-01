package naihe2010.csplayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media3.exoplayer.ExoPlayer

class MediaNotificationManager(
    private val context: Context,
    private val player: ExoPlayer
) {
    private val channelId = "csplayer_media"

    fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val title =
            player.currentMediaItem?.mediaMetadata?.title ?: context.getString(R.string.app_name)
        val isPlaying = player.isPlaying

        val playPauseAction = NotificationCompat.Action(
            if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            context.getString(if (isPlaying) R.string.pause else R.string.play),
            createPendingIntent(PlayerService.ACTION_TOGGLE_PLAY_PAUSE)
        )

        val rewindAction = NotificationCompat.Action(
            android.R.drawable.ic_media_rew,
            context.getString(R.string.rewind),
            createPendingIntent(PlayerService.ACTION_REWIND)
        )

        val forwardAction = NotificationCompat.Action(
            android.R.drawable.ic_media_ff,
            context.getString(R.string.forward),
            createPendingIntent(PlayerService.ACTION_FORWARD)
        )

        val exitAction = NotificationCompat.Action(
            android.R.drawable.ic_menu_close_clear_cancel,
            context.getString(R.string.exit),
            createPendingIntent(PlayerService.ACTION_EXIT)
        )

        val contentIntent = Intent(context, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.notification_playing))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentIntent(contentPendingIntent)
            .addAction(rewindAction)
            .addAction(playPauseAction)
            .addAction(forwardAction)
            .addAction(exitAction)
            .setStyle(
                MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()
    }

    private fun createPendingIntent(action: String): PendingIntent {
        val intent = Intent(context, PlayerService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
} 