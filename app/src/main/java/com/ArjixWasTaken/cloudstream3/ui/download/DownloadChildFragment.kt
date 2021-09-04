package com.ArjixWasTaken.cloudstream3.ui.download

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ArjixWasTaken.cloudstream3.R
import com.ArjixWasTaken.cloudstream3.ui.download.DownloadButtonSetup.handleDownloadClick
import com.ArjixWasTaken.cloudstream3.utils.Coroutines.main
import com.ArjixWasTaken.cloudstream3.utils.DataStore.getKey
import com.ArjixWasTaken.cloudstream3.utils.DataStore.getKeys
import com.ArjixWasTaken.cloudstream3.utils.UIHelper.fixPaddingStatusbar
import com.ArjixWasTaken.cloudstream3.utils.VideoDownloadHelper
import com.ArjixWasTaken.cloudstream3.utils.VideoDownloadManager
import kotlinx.android.synthetic.main.fragment_child_downloads.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DownloadChildFragment : Fragment() {
    companion object {
        fun newInstance(headerName: String, folder: String) =
            DownloadChildFragment().apply {
                arguments = Bundle().apply {
                    putString("folder", folder)
                    putString("name", headerName)
                }
            }
    }

    override fun onDestroyView() {
        (download_child_list?.adapter as DownloadChildAdapter?)?.killAdapter()
        super.onDestroyView()
    }

    override fun onDestroy() {
        downloadDeleteEventListener?.let { VideoDownloadManager.downloadDeleteEvent -= it }
        super.onDestroy()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_child_downloads, container, false)
    }

    private fun updateList(folder: String) = main {
        val data = withContext(Dispatchers.IO) { context?.getKeys(folder) }
        if (data == null) {
            activity?.onBackPressed() // TODO FIX
            return@main
        }
        val eps = withContext(Dispatchers.IO) {
            data.mapNotNull { key ->
                context?.getKey<VideoDownloadHelper.DownloadEpisodeCached>(key)
            }.mapNotNull {
                val info = VideoDownloadManager.getDownloadFileInfoAndUpdateSettings(requireContext(), it.id)
                    ?: return@mapNotNull null
                VisualDownloadChildCached(info.fileLength, info.totalBytes, it)
            }
        }
        if (eps.isEmpty()) {
            activity?.onBackPressed()
            return@main
        }

        (download_child_list?.adapter as DownloadChildAdapter? ?: return@main).cardList = eps
        download_child_list?.adapter?.notifyDataSetChanged()
    }

    var downloadDeleteEventListener: ((Int) -> Unit)? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val folder = arguments?.getString("folder")
        val name = arguments?.getString("name")
        if (folder == null) {
            activity?.onBackPressed() // TODO FIX
            return
        }
        context?.fixPaddingStatusbar(download_child_root)

        download_child_toolbar.title = name
        download_child_toolbar.setNavigationIcon(R.drawable.ic_baseline_arrow_back_24)
        download_child_toolbar.setNavigationOnClickListener {
            activity?.onBackPressed()
        }

        val adapter: RecyclerView.Adapter<RecyclerView.ViewHolder> =
            DownloadChildAdapter(
                ArrayList(),
            ) { click ->
                handleDownloadClick(activity, name, click)
            }

        downloadDeleteEventListener = { id: Int ->
            val list = (download_child_list?.adapter as DownloadChildAdapter?)?.cardList
            if (list != null) {
                if (list.any { it.data.id == id }) {
                    updateList(folder)
                }
            }
        }

        downloadDeleteEventListener?.let { VideoDownloadManager.downloadDeleteEvent += it }

        download_child_list.adapter = adapter
        download_child_list.layoutManager = GridLayoutManager(context, 1)

        updateList(folder)
    }
}