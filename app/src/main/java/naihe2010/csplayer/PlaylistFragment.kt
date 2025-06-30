package naihe2010.csplayer

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.util.concurrent.TimeUnit

import android.content.BroadcastReceiver
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager

data class PlaylistItem(
    val filePath: String,
    val displayName: String,
    val startTimeMs: Long = 0L,
    val endTimeMs: Long = 0L
)

class PlaylistFragment : Fragment() {

    private lateinit var playlistRecyclerView: RecyclerView
    private lateinit var tvEmptyPlaylist: TextView
    private lateinit var playerService: PlayerService
    private var playerBound = false
    private lateinit var playerConfig: PlayerConfig
    private var playlistAdapter: PlaylistAdapter? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as PlayerService.PlayerBinder
            playerService = binder.getService()
            playerBound = true
            // No longer playing first file here, as it's handled by PlayerService
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            playerBound = false
        }
    }

    private val playerStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == PlayerService.ACTION_STATE_CHANGED) {
                val currentMediaItemIndex = intent.getIntExtra(PlayerService.EXTRA_CURRENT_MEDIA_ITEM_INDEX, -1)
                playlistAdapter?.setNowPlaying(currentMediaItemIndex)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        playerConfig = PlayerConfig.getInstance(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.activity_playlist, container, false)

        playlistRecyclerView = view.findViewById(R.id.playlistRecyclerView)
        tvEmptyPlaylist = view.findViewById(R.id.tvEmptyPlaylist)
        playlistRecyclerView.layoutManager = LinearLayoutManager(context)

        loadPlaylist()

        return view
    }

    private fun loadPlaylist() {
        Log.d("PlaylistFragment", "loadPlaylist called")
        val directoryPath = playerConfig.currentDirectory
        Log.d("PlaylistFragment", "Current directory from PlayerConfig: $directoryPath")
        if (directoryPath.isNullOrEmpty()) {
            tvEmptyPlaylist.visibility = View.VISIBLE
            playlistRecyclerView.visibility = View.GONE
            Log.d("PlaylistFragment", "Directory path is null or empty.")
            return
        }

        val playlistItems = generatePlaylistItems(directoryPath)
        Log.d("PlaylistFragment", "Generated playlist items count: ${playlistItems.size}")
        if (playlistItems.isEmpty()) {
            tvEmptyPlaylist.visibility = View.VISIBLE
            playlistRecyclerView.visibility = View.GONE
            Log.d("PlaylistFragment", "Playlist is empty.")
        } else {
            tvEmptyPlaylist.visibility = View.GONE
            playlistRecyclerView.visibility = View.VISIBLE
            val adapter = PlaylistAdapter(playlistItems) { _, position ->
            if (playerBound) {
                playerService.play(
                    playlistItems,
                    position,
                    0L // Always start from the beginning of the clip
                )
            }
        }
        playlistAdapter = adapter
        playlistRecyclerView.adapter = playlistAdapter
        }
    }

    override fun onStart() {
        super.onStart()
        Intent(requireContext(), PlayerService::class.java).also { intent ->
            requireContext().bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
        LocalBroadcastManager.getInstance(requireContext())
            .registerReceiver(playerStateReceiver, IntentFilter(PlayerService.ACTION_STATE_CHANGED))
    }

    override fun onStop() {
        super.onStop()
        requireContext().unbindService(connection)
        playerBound = false
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(playerStateReceiver)
    }

    private fun generatePlaylistItems(directoryPath: String): List<PlaylistItem> {
        val playlist = mutableListOf<PlaylistItem>()
        val directory = File(directoryPath)
        Log.d("PlaylistFragment", "Processing directory: $directoryPath")
        if (!directory.exists() || !directory.isDirectory) {
            Log.e(
                "PlaylistFragment",
                "Directory does not exist or is not a directory: $directoryPath"
            )
            return playlist
        }
        val files = directory.listFiles()?.filter { it.isFile } ?: emptyList()
        Log.d("PlaylistFragment", "Found ${files.size} files in directory.")

        for (file in files) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(file.absolutePath)
                val durationStr =
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                val durationMs = durationStr?.toLong() ?: 0L
                Log.d("PlaylistFragment", "File: ${file.name}, Duration: $durationMs ms")

                if (durationMs == 0L) {
                    Log.w(
                        "PlaylistFragment",
                        "Skipping file with 0 duration or unretrievable duration: ${file.name}"
                    )
                    continue
                }

                if (playerConfig.isLoopEnabled && playerConfig.loopType == LoopType.TIME && playerConfig.loopInterval > 0) {
                    val intervalMs = TimeUnit.MINUTES.toMillis(playerConfig.loopInterval.toLong())
                    var currentStart = 0L
                    while (currentStart < durationMs) {
                        val currentEnd = (currentStart + intervalMs).coerceAtMost(durationMs)
                        val displayName =
                            "${file.name} [${formatMillis(currentStart)}-${
                                formatMillis(
                                    currentEnd
                                )
                            }]"
                        playlist.add(
                            PlaylistItem(
                                file.absolutePath,
                                displayName,
                                currentStart,
                                currentEnd
                            )
                        )
                        currentStart =
                            currentEnd + 1 // Move to the next millisecond after the current segment
                    }
                } else {
                    playlist.add(
                        PlaylistItem(
                            file.absolutePath,
                            file.name,
                            0L,
                            durationMs
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e("PlaylistFragment", "Error processing file: ${file.name}", e)
            } finally {
                retriever.release()
            }
        }
        Log.d("PlaylistFragment", "Generated ${playlist.size} playlist items.")
        return playlist
    }

    private fun formatMillis(millis: Long): String {
        return String.format(
            "%02d:%02d",
            TimeUnit.MILLISECONDS.toMinutes(millis),
            TimeUnit.MILLISECONDS.toSeconds(millis) -
                    TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millis))
        )
    }

    class PlaylistAdapter(
        private val items: List<PlaylistItem>,
        private val onItemClick: (PlaylistItem, Int) -> Unit
    ) :
        RecyclerView.Adapter<PlaylistAdapter.ViewHolder>() {

        private var nowPlayingPosition = -1

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val fileNameTextView: TextView = view.findViewById(R.id.fileNameTextView)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_playlist_file, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.fileNameTextView.text = item.displayName

            if (position == nowPlayingPosition) {
                holder.itemView.setBackgroundResource(R.color.playlist_item_playing)
            } else {
                if (position % 2 == 0) {
                    holder.itemView.setBackgroundResource(R.color.playlist_item_even)
                } else {
                    holder.itemView.setBackgroundResource(R.color.playlist_item_odd)
                }
            }

            holder.itemView.setOnClickListener {
                onItemClick(item, position)
                setNowPlaying(position)
            }
        }

        override fun getItemCount() = items.size

        fun setNowPlaying(position: Int) {
            val previousPosition = nowPlayingPosition
            nowPlayingPosition = position
            notifyItemChanged(previousPosition)
            notifyItemChanged(nowPlayingPosition)
        }
    }

    companion object {
        fun newInstance(directoryPath: String) = PlaylistFragment().apply {
            arguments = Bundle().apply {
                putString("directoryPath", directoryPath)
            }
        }
    }
}