package com.frodo.fighttracker

import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.frodo.fighttracker.databinding.ItemRunBinding

/**
 * Run history list. Only one run is expanded at a time
 * (per-minute rates + Rename / Delete).
 */
class RunHistoryAdapter(
    private val onRename: (RunRecord) -> Unit,
    private val onDelete: (RunRecord) -> Unit
) : ListAdapter<RunRecord, RunHistoryAdapter.VH>(Diff) {

    private var expandedId: String? = null

    class VH(val b: ItemRunBinding) : RecyclerView.ViewHolder(b.root)

    private object Diff : DiffUtil.ItemCallback<RunRecord>() {

        override fun areItemsTheSame(a: RunRecord, b: RunRecord) =
            a.runid == b.runid

        override fun areContentsTheSame(a: RunRecord, b: RunRecord) =
            a == b
    }

    private companion object {
        const val PAYLOAD_EXPANSION = "expansion"
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemRunBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        bindRun(holder, getItem(position))
    }

    override fun onBindViewHolder(holder: VH, position: Int, payloads: MutableList<Any>) {

        if (payloads.contains(PAYLOAD_EXPANSION)) {
            applyExpansion(holder, getItem(position), animate = true)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    private fun bindRun(holder: VH, run: RunRecord) {

        val b = holder.b
        val ctx = b.root.context

        val type = RunType.fromKey(run.type)

        b.runType.text =
            ctx.getString(type?.labelRes ?: R.string.ft_type_other).uppercase()

        b.runName.text =
            run.name.ifBlank { ctx.getString(R.string.ft_run_default) }

        b.runDuration.text = run.duration
        b.runDate.text = run.date

        b.runGold.text = UiKit.fmt(run.gold)
        b.runOrns.text = UiKit.fmt(run.orns)
        b.runExp.text = UiKit.fmt(run.exp)
        b.runShards.text =
            if (run.shards > 0L) UiKit.fmt(run.shards)
            else ctx.getString(R.string.ft_dash)

        // proof chips - only proofs that were actually collected
        b.runProofs.removeAllViews()

        for (proof in ProofTracking.allProofs) {

            val count = run.proofs[proof.id] ?: 0L

            if (count > 0L) {
                b.runProofs.addView(UiKit.proofChip(ctx, proof.iconUrl, count))
            }
        }

        b.runProofs.visibility =
            if (b.runProofs.childCount > 0) View.VISIBLE else View.GONE

        b.runItemCard.setOnClickListener {
            toggle(holder, run)
        }

        b.renameButton.setOnClickListener { onRename(run) }
        b.deleteButton.setOnClickListener { onDelete(run) }

        applyExpansion(holder, run, animate = false)
    }

    private fun applyExpansion(holder: VH, run: RunRecord, animate: Boolean) {

        val b = holder.b
        val ctx = b.root.context

        val expanded = run.runid == expandedId

        if (animate) {
            TransitionManager.beginDelayedTransition(
                b.runItemCard,
                AutoTransition().setDuration(160)
            )
        }

        b.runExpanded.visibility = if (expanded) View.VISIBLE else View.GONE

        if (!expanded) return

        val minutes = UiKit.durationMinutes(run.duration)

        fun rate(value: Long): String {

            if (minutes <= 0.0 || value <= 0L) return ctx.getString(R.string.ft_dash)

            return ctx.getString(R.string.ft_per_min, UiKit.compact(value / minutes))
        }

        b.rateGold.text = rate(run.gold)
        b.rateOrns.text = rate(run.orns)
        b.rateExp.text = rate(run.exp)
        b.rateShards.text = rate(run.shards)
    }

    private fun toggle(holder: VH, run: RunRecord) {

        val oldId = expandedId

        expandedId = if (oldId == run.runid) null else run.runid

        // collapse the previously expanded run
        if (oldId != null && oldId != run.runid) {

            val oldIndex = currentList.indexOfFirst { it.runid == oldId }

            if (oldIndex >= 0) {
                notifyItemChanged(oldIndex, PAYLOAD_EXPANSION)
            }
        }

        val pos = holder.bindingAdapterPosition

        if (pos != RecyclerView.NO_POSITION) {
            notifyItemChanged(pos, PAYLOAD_EXPANSION)
        }
    }
}
