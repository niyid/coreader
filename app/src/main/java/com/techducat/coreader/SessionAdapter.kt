package com.techducat.coreader

import android.annotation.SuppressLint
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SessionAdapter(
    private var sessions: List<Session>,
    private val onClick: (Session) -> Unit
) : RecyclerView.Adapter<SessionAdapter.SessionViewHolder>() {

    companion object {
        private const val TAG = "SessionAdapter"
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateSessions(newSessions: List<Session>) {
        Log.i(TAG, "updateSessions - $newSessions")

        sessions = newSessions
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SessionViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_session, parent, false)
        return SessionViewHolder(view, onClick)
    }

    override fun onBindViewHolder(holder: SessionViewHolder, position: Int) {
        holder.bind(sessions[position])
    }

    override fun getItemCount(): Int = sessions.size

    class SessionViewHolder(
        itemView: View,
        private val onClick: (Session) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val textViewTitle: TextView = itemView.findViewById(R.id.text_view_title)

        fun bind(session: Session) {
            textViewTitle.text = session.title
            itemView.setOnClickListener {
                onClick(session)
            }
        }
    }
}