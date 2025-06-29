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

    // 注册目录选择结果回调
    private val selectDirectoryLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            uri?.let {
                val directoryPath = getRealPathFromUri(it)
                if (!directoryPath.isNullOrEmpty()) {
                    directories.add(directoryPath)
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

        directories = loadDirectories().toMutableList()
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
            .setTitle("删除目录")
            .setMessage("确定要删除该目录吗？")
            .setPositiveButton("删除") { _, _ ->
                directories.removeAt(position)
                adapter.notifyItemRemoved(position)
                saveDirectories()
            }
            .setNegativeButton("取消", null)
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
            holder.itemView.findViewById<android.widget.TextView>(android.R.id.text1).text =
                items[position]
            holder.itemView.setOnLongClickListener {
                onItemLongClick(position)
                true
            }
        }

        override fun getItemCount() = items.size
        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
    }
}