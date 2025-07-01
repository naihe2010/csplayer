package naihe2010.csplayer

import android.content.Context

class PlayerConfig private constructor(
    var directories: Set<String>,
    var currentDirectory: String?,
    var currentFile: String?,
    var currentPosition: Long,
    var playbackRate: Float,
    var playbackOrder: PlaybackOrder,
    var loopType: LoopType,
    var loopInterval: Int,
    var silenceThreshold: Int
) {
    companion object {
        @Volatile
        private var INSTANCE: PlayerConfig? = null

        private const val PREF_NAME = "csplayer_prefs"
        private const val KEY_DIRECTORIES = "directories"
        private const val KEY_CURRENT_DIRECTORY = "current_directory"
        private const val KEY_CURRENT_FILE = "current_file"
        private const val KEY_CURRENT_POSITION = "current_position"
        private const val KEY_PLAYBACK_RATE = "playback_rate"
        private const val KEY_PLAYBACK_ORDER = "playback_order"
        private const val KEY_LOOP_TYPE = "loop_type"
        private const val KEY_LOOP_INTERVAL = "loop_interval"
        private const val KEY_SILENCE_THRESHOLD = "silence_threshold"

        fun getInstance(context: Context): PlayerConfig {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: load(context).also { INSTANCE = it }
            }
        }

        private fun load(context: Context): PlayerConfig {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            return PlayerConfig(
                directories = prefs.getStringSet(KEY_DIRECTORIES, emptySet()) ?: emptySet(),
                currentDirectory = prefs.getString(KEY_CURRENT_DIRECTORY, null),
                currentFile = prefs.getString(KEY_CURRENT_FILE, null),
                currentPosition = prefs.getLong(KEY_CURRENT_POSITION, 0L),
                playbackRate = prefs.getFloat(KEY_PLAYBACK_RATE, 1.0f),
                playbackOrder = PlaybackOrder.valueOf(
                    prefs.getString(KEY_PLAYBACK_ORDER, PlaybackOrder.SEQUENTIAL.name)
                        ?: PlaybackOrder.SEQUENTIAL.name
                ),
                loopType = LoopType.valueOf(
                    prefs.getString(KEY_LOOP_TYPE, LoopType.FILE.name) ?: LoopType.FILE.name
                ),
                loopInterval = prefs.getInt(KEY_LOOP_INTERVAL, 0),
                silenceThreshold = prefs.getInt(KEY_SILENCE_THRESHOLD, 0)
            )
        }
    }

    fun save(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putStringSet(KEY_DIRECTORIES, directories)
            putString(KEY_CURRENT_DIRECTORY, currentDirectory)
            putString(KEY_CURRENT_FILE, currentFile)
            putLong(KEY_CURRENT_POSITION, currentPosition)
            putFloat(KEY_PLAYBACK_RATE, playbackRate)
            putString(KEY_PLAYBACK_ORDER, playbackOrder.name)
            putString(KEY_LOOP_TYPE, loopType.name)
            putInt(KEY_LOOP_INTERVAL, loopInterval)
            putInt(KEY_SILENCE_THRESHOLD, silenceThreshold)
        }.apply()
    }

    fun updateDirectories(directories: Set<String>): PlayerConfig {
        this.directories = directories
        return this
    }

    fun updateCurrentDirectory(directory: String?): PlayerConfig {
        this.currentDirectory = directory
        return this
    }

    fun updateCurrentFile(file: String?): PlayerConfig {
        this.currentFile = file
        return this
    }

    fun updateCurrentPosition(position: Long): PlayerConfig {
        this.currentPosition = position
        return this
    }

    fun updatePlaybackRate(rate: Float): PlayerConfig {
        this.playbackRate = rate
        return this
    }

    fun updatePlaybackOrder(order: PlaybackOrder): PlayerConfig {
        this.playbackOrder = order
        return this
    }

    fun updateLoopSettings(type: LoopType, interval: Int): PlayerConfig {
        this.loopType = type
        this.loopInterval = interval
        return this
    }

    fun updateSilenceThreshold(threshold: Int): PlayerConfig {
        this.silenceThreshold = threshold
        return this
    }
}

enum class PlaybackOrder {
    SEQUENTIAL,    // 顺序播放
    RANDOM,        // 随机播放
    LOOP           // 循环播放
}

enum class LoopType {
    NONE,          // 不循环
    FILE,          // 按文件循环（播放完当前文件从头开始）
    TIME,          // 按时间循环（每X分钟从头开始）
    SEGMENT        // 按片段循环（静音X秒后从头开始）
} 