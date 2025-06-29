package naihe2010.csplayer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class HomeFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: DirectoryCardAdapter
    private var directories: MutableList<String> = mutableListOf()
    private lateinit var playerConfig: PlayerConfig

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        playerConfig = PlayerConfig.load(requireContext())
        directories = playerConfig.directories.toMutableList()

        val displayDirectories = generateDisplayDirectories(directories)
        adapter = DirectoryCardAdapter(displayDirectories) { directoryPath ->
            (activity as? MainActivity)?.navigateToPlaylist(directoryPath)
        }
        recyclerView = view.findViewById(R.id.recyclerDirectories)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter

        return view
    }

    override fun onResume() {
        super.onResume()
        playerConfig = PlayerConfig.load(requireContext())
        val newDirs = playerConfig.directories.toList()
        directories.clear()
        directories.addAll(newDirs)
        val displayDirectories = generateDisplayDirectories(directories)
        if (::adapter.isInitialized) {
            adapter = DirectoryCardAdapter(displayDirectories) { directoryPath ->
                (activity as? MainActivity)?.navigateToPlaylist(directoryPath)
            }
            recyclerView.adapter = adapter
            adapter.notifyDataSetChanged()
        }
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