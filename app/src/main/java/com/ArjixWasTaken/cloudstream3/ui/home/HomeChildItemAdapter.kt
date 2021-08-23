package com.ArjixWasTaken.cloudstream3.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.ArjixWasTaken.cloudstream3.*
import com.ArjixWasTaken.cloudstream3.ui.search.SEARCH_ACTION_LOAD
import com.ArjixWasTaken.cloudstream3.ui.search.SEARCH_ACTION_SHOW_METADATA
import com.ArjixWasTaken.cloudstream3.ui.search.SearchClickCallback
import com.ArjixWasTaken.cloudstream3.utils.UIHelper.setImage
import kotlinx.android.synthetic.main.home_result_grid.view.*

class HomeChildItemAdapter(
    var cardList: List<SearchResponse>,
    private val clickCallback: (SearchClickCallback) -> Unit
) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val layout = R.layout.home_result_grid
        return CardViewHolder(
            LayoutInflater.from(parent.context).inflate(layout, parent, false), clickCallback
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
    constructor(itemView: View, private val clickCallback: (SearchClickCallback) -> Unit) :
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

        fun bind(card: SearchResponse) {
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
