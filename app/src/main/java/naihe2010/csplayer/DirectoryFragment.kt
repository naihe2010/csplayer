package naihe2010.csplayer

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.io.File

class DirectoryFragment : Fragment() {
    private val perfName = "csplayer_prefs"
    private val keyDirectories = "directories"
    private lateinit var directories: MutableList<String>
    private lateinit var adapter: DirectoryAdapter
    private lateinit var playerConfig: PlayerConfig

    // 注册目录选择结果回调
    private val selectDirectoryLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            uri?.let {
                val directoryPath = getRealPathFromUri(it)
                if (!directoryPath.isNullOrEmpty()) {
                    directories.add(directoryPath)
                    playerConfig.updateDirectories(directories.toSet())
                    adapter.notifyItemInserted(directories.size - 1)
                    saveDirectories()
                }
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_directory, container, false)

        playerConfig = PlayerConfig.getInstance(requireContext())
        directories = playerConfig.directories.toMutableList()
        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerView)
        val fabAdd = view.findViewById<FloatingActionButton>(R.id.fabAdd)
        adapter = DirectoryAdapter(directories) { position ->
            showDeleteDialog(position)
        }
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter

        fabAdd.setOnClickListener {
            selectDirectoryLauncher.launch(null)
        }
        return view
    }

    override fun onResume() {
        super.onResume()
        loadAndDisplayDirectories()
    }

    private fun loadAndDisplayDirectories() {
        val directories = loadDirectories().toMutableList()

        adapter = DirectoryAdapter(directories) { position ->
            showDeleteDialog(position)
        }
        (view?.findViewById<RecyclerView>(R.id.recyclerView))?.adapter = adapter
    }

    // 将 Uri 转换为实际路径
    private fun getRealPathFromUri(uri: Uri): String? {
        val documentId = DocumentsContract.getTreeDocumentId(uri)
        val split = documentId.split(":")
        if (split.size >= 2) {
            val type = split[0]
            if ("primary".equals(type, ignoreCase = true)) {
                return "${File.separator}storage${File.separator}emulated${File.separator}0${File.separator}${split[1]}"
            }
        }
        return null
    }

    private fun showDeleteDialog(position: Int) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.dialog_delete_title))
            .setMessage(getString(R.string.dialog_delete_message))
            .setPositiveButton(getString(R.string.button_delete)) { _, _ ->
                directories.removeAt(position)
                playerConfig.updateDirectories(directories.toSet())
                adapter.notifyItemRemoved(position)
                saveDirectories()
            }
            .setNegativeButton(getString(R.string.button_cancel), null)
            .show()
    }

    private fun saveDirectories() {
        val prefs = requireContext().getSharedPreferences(perfName, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(keyDirectories, directories.toSet()).apply()
    }

    private fun loadDirectories(): List<String> {
        val prefs = requireContext().getSharedPreferences(perfName, Context.MODE_PRIVATE)
        return prefs.getStringSet(keyDirectories, emptySet())?.toList() ?: emptyList()
    }

    class DirectoryAdapter(
        private val items: List<String>,
        private val onItemLongClick: (Int) -> Unit
    ) : RecyclerView.Adapter<DirectoryAdapter.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_1, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val directoryPath = items[position]
            holder.itemView.findViewById<android.widget.TextView>(android.R.id.text1).text =
                directoryPath

            holder.itemView.setBackgroundResource(android.R.color.transparent)

            holder.itemView.setOnClickListener {
                (holder.itemView.context as? MainActivity)?.navigateToPlaylist(directoryPath)
            }

            holder.itemView.setOnLongClickListener {
                onItemLongClick(position)
                true
            }
        }

        override fun getItemCount() = items.size
        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
    }
}