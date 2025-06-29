package naihe2010.csplayer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class DirectoryCardAdapter(
    private val items: List<DisplayDirectory>,
    private val onClick: (String) -> Unit
) : RecyclerView.Adapter<DirectoryCardAdapter.ViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val card =
            LayoutInflater.from(parent.context).inflate(R.layout.item_directory_card, parent, false)
        return ViewHolder(card)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], onClick)
    }

    override fun getItemCount() = items.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(displayDirectory: DisplayDirectory, onClick: (String) -> Unit) {
            val tvName = itemView.findViewById<TextView>(R.id.tvDirectoryName)
            tvName.text = displayDirectory.displayName
            itemView.setOnClickListener { onClick(displayDirectory.fullPath) }
        }
    }
}