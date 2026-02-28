package com.example.myapplication.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R

data class Challenge(
    val id: String,
    val title: String,
    val description: String,
    val accepted: Boolean
)

class ChallengeAdapter(
    private var challenges: List<Challenge>,
    private val onAccept: (String) -> Unit
) : RecyclerView.Adapter<ChallengeAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvChallengeTitle)
        val tvDesc: TextView = view.findViewById(R.id.tvChallengeDesc)
        val btnAccept: Button = view.findViewById(R.id.btnAcceptChallenge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_challenge_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val challenge = challenges[position]
        holder.tvTitle.text = challenge.title
        holder.tvDesc.text = challenge.description

        if (challenge.accepted) {
            holder.btnAccept.isEnabled = false
            holder.btnAccept.text = "ACCEPTED"
        } else {
            holder.btnAccept.isEnabled = true
            holder.btnAccept.text = "ACCEPT"
            holder.btnAccept.setOnClickListener {
                onAccept(challenge.id)
            }
        }
    }

    override fun getItemCount() = challenges.size

    fun updateChallenges(newChallenges: List<Challenge>) {
        challenges = newChallenges
        notifyDataSetChanged()
    }
}

