package com.ArjixWasTaken.cloudstream3.ui.search

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.ArjixWasTaken.cloudstream3.SearchResponse
import com.ArjixWasTaken.cloudstream3.ui.AutofitRecyclerView
import com.ArjixWasTaken.cloudstream3.utils.UIHelper.getGridFormatId
import com.ArjixWasTaken.cloudstream3.utils.UIHelper.getGridIsCompact
import com.ArjixWasTaken.cloudstream3.utils.UIHelper.toPx
import kotlinx.android.synthetic.main.search_result_compact.view.*
import kotlin.math.roundToInt

const val SEARCH_ACTION_LOAD = 0
const val SEARCH_ACTION_SHOW_METADATA = 1
const val SEARCH_ACTION_PLAY_FILE = 2

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

            SearchResultBuilder.bind(clickCallback, card,  itemView)
        }
    }
}
