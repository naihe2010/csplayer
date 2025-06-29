package naihe2010.csplayer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class HomeFragment : Fragment() {

    private lateinit var recyclerAllDirectories: RecyclerView
    private lateinit var recyclerNowPlaying: RecyclerView
    private lateinit var tvNowPlayingTitle: TextView
    private lateinit var tvAllDirectoriesTitle: TextView

    private lateinit var allDirectoriesAdapter: DirectoryCardAdapter
    private lateinit var nowPlayingAdapter: DirectoryCardAdapter

    private lateinit var playerConfig: PlayerConfig

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        playerConfig = PlayerConfig.getInstance(requireContext())

        recyclerAllDirectories = view.findViewById(R.id.recyclerDirectories)
        recyclerNowPlaying = view.findViewById(R.id.recyclerNowPlaying)
        tvNowPlayingTitle = view.findViewById(R.id.tvNowPlayingTitle)
        tvAllDirectoriesTitle = view.findViewById(R.id.tvAllDirectoriesTitle)

        recyclerAllDirectories.layoutManager = LinearLayoutManager(context)
        recyclerNowPlaying.layoutManager = LinearLayoutManager(context)

        return view
    }

    override fun onResume() {
        super.onResume()
        playerConfig = PlayerConfig.getInstance(requireContext())
        updateDirectoryDisplay()
    }

    private fun updateDirectoryDisplay() {
        val allDirectories = playerConfig.directories.toList()
        val currentPlayingDirectoryPath = playerConfig.currentDirectory

        val nowPlayingDirectory = allDirectories.find { it == currentPlayingDirectoryPath }
        val otherDirectories = allDirectories.filter { it != currentPlayingDirectoryPath }

        // Update "Now Playing" section
        if (nowPlayingDirectory != null) {
            tvNowPlayingTitle.visibility = View.VISIBLE
            recyclerNowPlaying.visibility = View.VISIBLE
            val displayNowPlaying = generateDisplayDirectories(listOf(nowPlayingDirectory))
            nowPlayingAdapter = DirectoryCardAdapter(
                displayNowPlaying,
                currentPlayingDirectoryPath
            ) { displayDirectory ->
                (activity as? MainActivity)?.navigateToPlaylist(displayDirectory.path)
            }
            recyclerNowPlaying.adapter = nowPlayingAdapter
        } else {
            tvNowPlayingTitle.visibility = View.GONE
            recyclerNowPlaying.visibility = View.GONE
        }

        // Update "All Directories" section
        val displayOtherDirectories = generateDisplayDirectories(otherDirectories)
        allDirectoriesAdapter = DirectoryCardAdapter(
            displayOtherDirectories,
            currentPlayingDirectoryPath
        ) { displayDirectory ->
            (activity as? MainActivity)?.navigateToPlaylist(displayDirectory.path)
        }
        recyclerAllDirectories.adapter = allDirectoriesAdapter
    }

    private fun generateDisplayDirectories(paths: List<String>): List<DisplayDirectory> {
        val displayMap =
            mutableMapOf<String, MutableList<String>>() // displayName -> list of fullPaths
        val result = mutableListOf<DisplayDirectory>()

        // First pass: group by last segment
        for (fullPath in paths) {
            val file = File(fullPath)
            val lastName = file.name
            displayMap.getOrPut(lastName) { mutableListOf() }.add(fullPath)
        }

        // Second pass: resolve conflicts
        for ((lastName, fullPaths) in displayMap) {
            if (fullPaths.size == 1) {
                result.add(DisplayDirectory(fullPaths[0], lastName))
            } else {
                // Conflict: need to add more segments
                val conflictMap = mutableMapOf<String, String>() // fullPath -> currentDisplayName
                for (path in fullPaths) {
                    conflictMap[path] = File(path).name
                }

                var level = 1
                var resolvedCount = 0

                while (resolvedCount < fullPaths.size) {
                    val currentLevelNames =
                        mutableMapOf<String, MutableList<String>>() // currentDisplayName -> list of fullPaths

                    for (path in fullPaths) {
                        if (path !in conflictMap) continue // Already resolved

                        val currentDisplayName = conflictMap[path]!!
                        val parentFile = File(path).parentFile
                        if (parentFile != null) {
                            val newDisplayName = "${parentFile.name}/${currentDisplayName}"
                            currentLevelNames.getOrPut(newDisplayName) { mutableListOf() }.add(path)
                        } else {
                            // Reached root or invalid path, use full path as display name
                            result.add(DisplayDirectory(path, path))
                            resolvedCount++
                            conflictMap.remove(path)
                        }
                    }

                    for ((newDisplayName, pathsAtThisLevel) in currentLevelNames) {
                        if (pathsAtThisLevel.size == 1) {
                            result.add(DisplayDirectory(pathsAtThisLevel[0], newDisplayName))
                            resolvedCount++
                            conflictMap.remove(pathsAtThisLevel[0])
                        } else {
                            // Still conflict, update display name for next iteration
                            for (path in pathsAtThisLevel) {
                                conflictMap[path] = newDisplayName
                            }
                        }
                    }
                    level++
                    if (level > 10) break // Safety break to prevent infinite loops for malformed paths
                }
            }
        }
        return result.sortedBy { it.displayName }
    }
}