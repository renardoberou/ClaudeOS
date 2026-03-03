package com.claudeos.launcher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ChatAdapter(private val messages: List<ChatMessage>) :
    RecyclerView.Adapter<ChatAdapter.ViewHolder>() {

    companion object {
        const val VIEW_USER      = 0
        const val VIEW_ASSISTANT = 1
        const val VIEW_LOADING   = 2
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val text: TextView? = itemView.findViewById(R.id.message_text)
    }

    override fun getItemViewType(position: Int): Int {
        val msg = messages[position]
        return when {
            msg.isLoading      -> VIEW_LOADING
            msg.role == "user" -> VIEW_USER
            else               -> VIEW_ASSISTANT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layout = when (viewType) {
            VIEW_USER      -> R.layout.item_message_user
            VIEW_LOADING   -> R.layout.item_message_loading
            else           -> R.layout.item_message_assistant
        }
        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val msg = messages[position]
        if (!msg.isLoading) {
            val displayText = when (val content = msg.content) {
                is String -> content
                is List<*> -> content.filterIsInstance<ContentBlock.Text>()
                    .joinToString("\n") { it.text }
                    .ifEmpty { null }
                else -> content.toString()
            }
            holder.text?.text = displayText
        }
    }

    override fun getItemCount() = messages.size
}
