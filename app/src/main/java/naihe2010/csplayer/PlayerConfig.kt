package naihe2010.csplayer

import android.content.Context

data class PlayerConfig(
    val directories: Set<String> = emptySet(),
    val currentDirectory: String? = null,
    val currentFile: String? = null,
    val playbackRate: Float = 1.0f,
    val playbackOrder: PlaybackOrder = PlaybackOrder.SEQUENTIAL,
    val isLoopEnabled: Boolean = false,
    val loopType: LoopType = LoopType.FILE,
    val loopInterval: Int = 0 // 循环间隔，对于时间循环是分钟数，对于文件循环是文件数
) {
    companion object {
        private const val PREF_NAME = "csplayer_prefs"
        private const val KEY_DIRECTORIES = "directories"
        private const val KEY_CURRENT_DIRECTORY = "current_directory"
        private const val KEY_CURRENT_FILE = "current_file"
        private const val KEY_PLAYBACK_RATE = "playback_rate"
        private const val KEY_PLAYBACK_ORDER = "playback_order"
        private const val KEY_IS_LOOP_ENABLED = "is_loop_enabled"
        private const val KEY_LOOP_TYPE = "loop_type"
        private const val KEY_LOOP_INTERVAL = "loop_interval"

        fun load(context: Context): PlayerConfig {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            return PlayerConfig(
                directories = prefs.getStringSet(KEY_DIRECTORIES, emptySet()) ?: emptySet(),
                currentDirectory = prefs.getString(KEY_CURRENT_DIRECTORY, null),
                currentFile = prefs.getString(KEY_CURRENT_FILE, null),
                playbackRate = prefs.getFloat(KEY_PLAYBACK_RATE, 1.0f),
                playbackOrder = PlaybackOrder.valueOf(
                    prefs.getString(KEY_PLAYBACK_ORDER, PlaybackOrder.SEQUENTIAL.name)
                        ?: PlaybackOrder.SEQUENTIAL.name
                ),
                isLoopEnabled = prefs.getBoolean(KEY_IS_LOOP_ENABLED, false),
                loopType = LoopType.valueOf(
                    prefs.getString(KEY_LOOP_TYPE, LoopType.FILE.name) ?: LoopType.FILE.name
                ),
                loopInterval = prefs.getInt(KEY_LOOP_INTERVAL, 0)
            )
        }
    }

    fun save(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putStringSet(KEY_DIRECTORIES, directories)
            putString(KEY_CURRENT_DIRECTORY, currentDirectory)
            putString(KEY_CURRENT_FILE, currentFile)
            putFloat(KEY_PLAYBACK_RATE, playbackRate)
            putString(KEY_PLAYBACK_ORDER, playbackOrder.name)
            putBoolean(KEY_IS_LOOP_ENABLED, isLoopEnabled)
            putString(KEY_LOOP_TYPE, loopType.name)
            putInt(KEY_LOOP_INTERVAL, loopInterval)
        }.apply()
    }

    fun updateDirectories(directories: Set<String>): PlayerConfig {
        return copy(directories = directories)
    }

    fun updateCurrentDirectory(directory: String?): PlayerConfig {
        return copy(currentDirectory = directory)
    }

    fun updateCurrentFile(file: String?): PlayerConfig {
        return copy(currentFile = file)
    }

    fun updatePlaybackRate(rate: Float): PlayerConfig {
        return copy(playbackRate = rate)
    }

    fun updatePlaybackOrder(order: PlaybackOrder): PlayerConfig {
        return copy(playbackOrder = order)
    }

    fun updateLoopSettings(enabled: Boolean, type: LoopType, interval: Int): PlayerConfig {
        return copy(isLoopEnabled = enabled, loopType = type, loopInterval = interval)
    }
}

enum class PlaybackOrder {
    SEQUENTIAL,    // 顺序播放
    RANDOM,        // 随机播放
    SHUFFLE        // 乱序播放
}

enum class LoopType {
    NONE,          // 不循环
    FILE,          // 按文件循环（播放完当前文件从头开始）
    TIME           // 按时间循环（每X分钟从头开始）
} 