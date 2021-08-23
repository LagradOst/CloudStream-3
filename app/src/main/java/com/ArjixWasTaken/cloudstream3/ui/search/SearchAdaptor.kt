package com.ArjixWasTaken.cloudstream3.ui.search

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.ArjixWasTaken.cloudstream3.HentaiSearchResponse
import com.ArjixWasTaken.cloudstream3.DubStatus
import com.ArjixWasTaken.cloudstream3.SearchResponse
import com.ArjixWasTaken.cloudstream3.TvType
import com.ArjixWasTaken.cloudstream3.utils.UIHelper.getGridFormatId
import com.ArjixWasTaken.cloudstream3.utils.UIHelper.getGridIsCompact
import com.ArjixWasTaken.cloudstream3.utils.UIHelper.toPx
import com.ArjixWasTaken.cloudstream3.ui.AutofitRecyclerView
import com.ArjixWasTaken.cloudstream3.utils.UIHelper.setImage
import kotlinx.android.synthetic.main.search_result_compact.view.backgroundCard
import kotlinx.android.synthetic.main.search_result_compact.view.imageText
import kotlinx.android.synthetic.main.search_result_compact.view.imageView
import kotlinx.android.synthetic.main.search_result_grid.view.*
import kotlin.math.roundToInt

const val SEARCH_ACTION_LOAD = 0
const val SEARCH_ACTION_SHOW_METADATA = 1

class SearchClickCallback(val action: Int, val view: View, val card: SearchResponse)

class SearchAdapter(
    var cardList: List<SearchResponse>,
    private val resView: AutofitRecyclerView,
    private val clickCallback: (SearchClickCallback) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val layout = parent.context.getGridFormatId()
        return CardViewHolder(
            LayoutInflater.from(parent.context).inflate(layout, parent, false),
            clickCallback,
            resView
        )
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is CardViewHolder -> {
                holder.bind(cardList[position])
            }
        }
    }

    override fun getItemCount(): Int {
        return cardList.size
    }

    class CardViewHolder
    constructor(
        itemView: View,
        private val clickCallback: (SearchClickCallback) -> Unit,
        resView: AutofitRecyclerView
    ) :
        RecyclerView.ViewHolder(itemView) {
        val cardView: ImageView = itemView.imageView
        private val cardText: TextView = itemView.imageText
        private val textType: TextView? = itemView.text_type
        // val search_result_lang: ImageView? = itemView.search_result_lang

        private val textIsDub: View? = itemView.text_is_dub
        private val textIsSub: View? = itemView.text_is_sub

        //val cardTextExtra: TextView? = itemView.imageTextExtra
        //val imageTextProvider: TextView? = itemView.imageTextProvider
        private val bg: CardView = itemView.backgroundCard
        private val compactView = itemView.context.getGridIsCompact()
        private val coverHeight: Int = if (compactView) 80.toPx else (resView.itemWidth / 0.68).roundToInt()

        fun bind(card: SearchResponse) {
            if (!compactView) {
                cardView.apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        coverHeight
                    )
                }
            }

            textType?.text = when (card.type) {
                TvType.Hentai -> "Hentai"
            }
            // search_result_lang?.visibility = View.GONE

            textIsDub?.visibility = View.GONE
            textIsSub?.visibility = View.GONE

            cardText.text = card.name

            //imageTextProvider.text = card.apiName
            cardView.setImage(card.posterUrl)

            bg.setOnClickListener {
                clickCallback.invoke(SearchClickCallback(SEARCH_ACTION_LOAD, it, card))
            }

            bg.setOnLongClickListener {
                clickCallback.invoke(SearchClickCallback(SEARCH_ACTION_SHOW_METADATA, it, card))
                return@setOnLongClickListener true
            }

            when (card) {
                is HentaiSearchResponse -> {
                    if (card.dubStatus?.size == 1) {
                        //search_result_lang?.visibility = View.VISIBLE
                        if (card.dubStatus.contains(DubStatus.Dubbed)) {
                            textIsDub?.visibility = View.VISIBLE
                            //search_result_lang?.setColorFilter(ContextCompat.getColor(activity, R.color.dubColor))
                        } else if (card.dubStatus.contains(DubStatus.Subbed)) {
                            //search_result_lang?.setColorFilter(ContextCompat.getColor(activity, R.color.subColor))
                            textIsSub?.visibility = View.VISIBLE
                        }
                    }
                }
            }
        }
    }
}
