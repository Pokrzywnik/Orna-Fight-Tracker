package com.frodo.fighttracker

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.frodo.fighttracker.databinding.ItemGleamingHeaderBinding
import com.frodo.fighttracker.databinding.ItemGleamingMobBinding
import android.view.Gravity

/** Rows of the gleaming grid: a tier header (with found/total) or a mob tile. */
sealed class GleamingRow {

    data class Header(val tier: Int, val found: Int, val total: Int) : GleamingRow()

    data class Mob(val mob: GleamingMob, val discovered: Boolean) : GleamingRow()
}

class GleamingAdapter(
    private val onMobClick: (GleamingMob, Boolean) -> Unit
) : ListAdapter<GleamingRow, RecyclerView.ViewHolder>(Diff) {

    private object Diff : DiffUtil.ItemCallback<GleamingRow>() {

        override fun areItemsTheSame(a: GleamingRow, b: GleamingRow): Boolean =
            when {
                a is GleamingRow.Header && b is GleamingRow.Header -> a.tier == b.tier
                a is GleamingRow.Mob && b is GleamingRow.Mob -> a.mob.id == b.mob.id
                else -> false
            }

        override fun areContentsTheSame(a: GleamingRow, b: GleamingRow): Boolean =
            a == b
    }

    private companion object {

        const val TYPE_HEADER = 0
        const val TYPE_MOB = 1

        /** RGB -> black, alpha untouched: a pure silhouette of the sprite. */
        val SILHOUETTE = ColorMatrixColorFilter(
            ColorMatrix(
                floatArrayOf(
                    0f, 0f, 0f, 0f, 0f,
                    0f, 0f, 0f, 0f, 0f,
                    0f, 0f, 0f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
    }

    class HeaderVH(val b: ItemGleamingHeaderBinding) : RecyclerView.ViewHolder(b.root)

    class MobVH(val b: ItemGleamingMobBinding) : RecyclerView.ViewHolder(b.root) {
        /** created lazily, only for discovered mobs; reused when the holder is recycled */
        var aura: AuraWebView? = null
    }

    /** Used by the fragment's SpanSizeLookup: headers span the whole row. */
    fun isHeader(position: Int): Boolean = getItem(position) is GleamingRow.Header

    override fun getItemViewType(position: Int): Int =
        if (getItem(position) is GleamingRow.Header) TYPE_HEADER else TYPE_MOB

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {

        val inflater = LayoutInflater.from(parent.context)

        return if (viewType == TYPE_HEADER) {
            HeaderVH(ItemGleamingHeaderBinding.inflate(inflater, parent, false))
        } else {
            MobVH(ItemGleamingMobBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {

        when (val row = getItem(position)) {

            is GleamingRow.Header -> {
                val b = (holder as HeaderVH).b
                val ctx = b.root.context
                b.headerTier.text = ctx.getString(R.string.ft_gleaming_header, row.tier)
                b.headerCount.text =
                    ctx.getString(R.string.ft_gleaming_progress, row.found, row.total)
            }

            is GleamingRow.Mob -> {
                val h = holder as MobVH
                val b = h.b
                val mob = row.mob

                b.mobName.text = mob.name

                // black = not seen gleaming yet, real sprite + aura = discovered
                b.mobSprite.colorFilter = if (row.discovered) null else SILHOUETTE
                b.mobCircle.visibility = if (row.discovered) View.GONE else View.VISIBLE
                b.mobCard.setBackgroundResource(
                    if (row.discovered) R.drawable.bg_card_gleam else R.drawable.bg_card
                )
                b.mobCard.translationZ = if (row.discovered) 1f else 0f
                b.mobCard.outlineProvider = null

                if (row.discovered) {

                    val aura = h.aura ?: AuraWebView(b.root.context).apply {
                        val auraSize = UiKit.dp(b.root.context, 152)
                        layoutParams = FrameLayout.LayoutParams(auraSize, auraSize, Gravity.CENTER)
                        isClickable = false
                        isFocusable = false
                        loadAura("gleaming.webp")
                    }.also {
                        h.aura = it
                        // between the grey circle (0) and the sprite
                        b.mobFrame.addView(it, 1)
                    }

                    aura.visibility = View.VISIBLE

                } else {
                    h.aura?.visibility = View.GONE
                }

                // same recipe as the materials icons: software layer + no bitmap
                // filtering keeps pixel-art sprites sharp
                b.mobSprite.setLayerType(View.LAYER_TYPE_SOFTWARE, null)

                b.mobSprite.load(mob.iconUrl) {
                    crossfade(false)
                    allowHardware(false)
                    listener(
                        onSuccess = { _, _ ->
                            b.mobSprite.drawable?.setFilterBitmap(false)
                        }
                    )
                    error(android.R.drawable.ic_menu_help)
                }

                val click = View.OnClickListener { onMobClick(mob, row.discovered) }
                b.mobCard.setOnClickListener(click)
                b.mobTouch.setOnClickListener(click)
            }
        }
    }
}