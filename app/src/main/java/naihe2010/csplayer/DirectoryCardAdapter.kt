package naihe2010.csplayer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class DirectoryCardAdapter(
    private val directories: List<DisplayDirectory>,
    private val nowPlayingDirectory: String?,
    private val onDirectoryClick: (DisplayDirectory) -> Unit
) : RecyclerView.Adapter<DirectoryCardAdapter.ViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val card =
            LayoutInflater.from(parent.context).inflate(R.layout.item_directory_card, parent, false)
        return ViewHolder(card)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val directory = directories[position]
        holder.bind(directory, onDirectoryClick)

        if (directory.path == nowPlayingDirectory) {
            holder.itemView.setBackgroundResource(R.color.directory_card_playing)
        } else {
            holder.itemView.setBackgroundResource(android.R.color.transparent)
        }
    }

    override fun getItemCount() = directories.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(displayDirectory: DisplayDirectory, onClick: (DisplayDirectory) -> Unit) {
            val tvName = itemView.findViewById<TextView>(R.id.tvDirectoryName)
            tvName.text = displayDirectory.displayName
            itemView.setOnClickListener { onClick(displayDirectory) }
        }
    }
}