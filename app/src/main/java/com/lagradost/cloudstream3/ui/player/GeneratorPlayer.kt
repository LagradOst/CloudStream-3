package com.lagradost.cloudstream3.ui.player

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import com.lagradost.cloudstream3.MainActivity.Companion.showToast
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorUri
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.UIHelper.popCurrentPage
import kotlinx.android.synthetic.main.fragment_player.*

class GeneratorPlayer : FullScreenPlayer() {
    companion object {
        private var lastUsedGenerator: IGenerator? = null
        fun newInstance(generator: IGenerator): Bundle {
            lastUsedGenerator = generator
            return Bundle()
        }
    }

    private lateinit var viewModel: PlayerGeneratorViewModel //by activityViewModels()
    private var currentLinks: Set<Pair<ExtractorLink?, ExtractorUri?>> = setOf()
    private var currentSubs: Set<SubtitleData> = setOf()

    private var currentSelectedLink: Pair<ExtractorLink?, ExtractorUri?>? = null

    private fun startLoading() {
        player.release()
        overlay_loading_skip_button?.isVisible = false
        player_loading_overlay?.isVisible = true
    }

    private fun loadLink(link: Pair<ExtractorLink?, ExtractorUri?>?, sameEpisode: Boolean) {
        if (link == null) return
        player_loading_overlay?.isVisible = false
        uiReset()
        currentSelectedLink = link

        context?.let { ctx ->
            val (url, uri) = link
            player.loadPlayer(
                ctx,
                sameEpisode,
                url,
                uri,
                startPosition = if (sameEpisode) null else DataStoreHelper.getViewPos(viewModel.getId())?.position
            )
        }
    }

    private fun sortLinks(): List<Pair<ExtractorLink?, ExtractorUri?>> {
        return currentLinks.sortedBy {
            val (linkData, _) = it
            var quality = linkData?.quality ?: Qualities.Unknown.value

            // we set all qualities above current max as max -1
            if (quality > currentPrefQuality) {
                quality = currentPrefQuality - 1
            }
            // negative because we want to sort highest quality first
            -(quality)
        }
    }

    private fun noLinksFound() {
        showToast(activity, R.string.no_links_found_toast, Toast.LENGTH_SHORT)
        activity?.popCurrentPage()
    }

    private fun startPlayer() {
        val links = sortLinks()
        if (links.isEmpty()) {
            noLinksFound()
            return
        }
        loadLink(links.first(), false)
    }

    override fun nextEpisode() {
        viewModel.loadLinksNext()
    }

    override fun prevEpisode() {
        viewModel.loadLinksPrev()
    }

    override fun playerPositionChanged(posDur: Pair<Long, Long>) {
        val (position, duration) = posDur
        viewModel.getId()?.let {
            DataStoreHelper.setViewPos(it, position, duration)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        viewModel = ViewModelProvider(this)[PlayerGeneratorViewModel::class.java]
        viewModel.attachGenerator(lastUsedGenerator)
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (currentSelectedLink == null) {
            viewModel.loadLinks()
        }

        overlay_loading_skip_button?.setOnClickListener {
            startPlayer()
        }

        player_loading_go_back?.setOnClickListener {
            player.release()
            activity?.popCurrentPage()
        }

        observe(viewModel.loadingLinks) {
            when (it) {
                is Resource.Loading -> {
                    startLoading()
                }
                is Resource.Success -> {
                    // provider returned false
                    if (it.value != true) {
                        showToast(activity, R.string.unexpected_error, Toast.LENGTH_SHORT)
                    }
                    startPlayer()
                }
                is Resource.Failure -> {
                    showToast(activity, it.errorString, Toast.LENGTH_LONG)
                    startPlayer()
                }
            }
        }

        observe(viewModel.currentLinks) {
            currentLinks = it
            overlay_loading_skip_button?.isVisible = it.isNotEmpty()
        }

        observe(viewModel.currentSubs) {
            currentSubs = it
        }
    }
}