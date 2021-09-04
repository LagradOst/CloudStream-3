package com.ArjixWasTaken.cloudstream3.ui.search

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.ArjixWasTaken.cloudstream3.APIHolder.apis
import com.ArjixWasTaken.cloudstream3.APIHolder.getApiSettings
import com.ArjixWasTaken.cloudstream3.APIHolder.getApiTypeSettings
import com.ArjixWasTaken.cloudstream3.HomePageList
import com.ArjixWasTaken.cloudstream3.R
import com.ArjixWasTaken.cloudstream3.TvType
import com.ArjixWasTaken.cloudstream3.mvvm.Resource
import com.ArjixWasTaken.cloudstream3.mvvm.observe
import com.ArjixWasTaken.cloudstream3.ui.APIRepository.Companion.providersActive
import com.ArjixWasTaken.cloudstream3.ui.APIRepository.Companion.typesActive
import com.ArjixWasTaken.cloudstream3.ui.home.HomeFragment
import com.ArjixWasTaken.cloudstream3.ui.home.HomeFragment.Companion.loadHomepageList
import com.ArjixWasTaken.cloudstream3.ui.home.ParentItemAdapter
import com.ArjixWasTaken.cloudstream3.utils.DataStore.getKey
import com.ArjixWasTaken.cloudstream3.utils.DataStore.setKey
import com.ArjixWasTaken.cloudstream3.utils.SEARCH_PROVIDER_TOGGLE
import com.ArjixWasTaken.cloudstream3.utils.UIHelper.fixPaddingStatusbar
import com.ArjixWasTaken.cloudstream3.utils.UIHelper.getGridIsCompact
import com.ArjixWasTaken.cloudstream3.utils.UIHelper.hideKeyboard
import kotlinx.android.synthetic.main.fragment_search.*

class SearchFragment : Fragment() {
    private lateinit var searchViewModel: SearchViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        searchViewModel =
            ViewModelProvider(this).get(SearchViewModel::class.java)
        activity?.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        )
        return inflater.inflate(R.layout.fragment_search, container, false)
    }

    private fun fixGrid() {
        val compactView = activity?.getGridIsCompact() ?: false
        val spanCountLandscape = if (compactView) 2 else 6
        val spanCountPortrait = if (compactView) 1 else 3
        val orientation = resources.configuration.orientation

        val currentSpan = if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            spanCountLandscape
        } else {
            spanCountPortrait
        }
        cardSpace.spanCount = currentSpan
        HomeFragment.currentSpan = currentSpan
        HomeFragment.configEvent.invoke(currentSpan)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        fixGrid()
    }

    override fun onDestroyView() {
        hideKeyboard()
        super.onDestroyView()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        context?.fixPaddingStatusbar(searchRoot)
        fixGrid()

        val adapter: RecyclerView.Adapter<RecyclerView.ViewHolder>? = activity?.let {
            SearchAdapter(
                ArrayList(),
                cardSpace,
            ) { callback ->
                SearchHelper.handleSearchClickCallback(activity, callback)
            }
        }

        cardSpace.adapter = adapter
        search_loading_bar.alpha = 0f

        val searchExitIcon = main_search.findViewById<ImageView>(androidx.appcompat.R.id.search_close_btn)
        val searchMagIcon = main_search.findViewById<ImageView>(androidx.appcompat.R.id.search_mag_icon)
        searchMagIcon.scaleX = 0.65f
        searchMagIcon.scaleY = 0.65f
        search_filter.setOnClickListener { searchView ->
            val apiNamesSetting = activity?.getApiSettings()
            if (apiNamesSetting != null) {
                val apiNames = apis.map { it.name }
                val builder =
                    AlertDialog.Builder(searchView.context, R.style.AlertDialogCustom).setView(R.layout.provider_list)

                val dialog = builder.create()
                dialog.show()

                val listView = dialog.findViewById<ListView>(R.id.listview1)!!
                val listView2 = dialog.findViewById<ListView>(R.id.listview2)!!
                val toggle = dialog.findViewById<SwitchMaterial>(R.id.toggle1)!!
                val applyButton = dialog.findViewById<TextView>(R.id.apply_btt)!!
                val cancelButton = dialog.findViewById<TextView>(R.id.cancel_btt)!!
                // val applyHolder = dialog.findViewById<LinearLayout>(R.id.apply_btt_holder)!!

                val arrayAdapter = ArrayAdapter<String>(searchView.context, R.layout.sort_bottom_single_choice)
                arrayAdapter.addAll(apiNames)

                listView.adapter = arrayAdapter
                listView.choiceMode = AbsListView.CHOICE_MODE_MULTIPLE

                val typeChoices = listOf(
                    Pair(R.string.movies, listOf(TvType.Movie)),
                    Pair(R.string.tv_series, listOf(TvType.TvSeries)),
                    Pair(R.string.cartoons, listOf(TvType.Cartoon)),
                    Pair(R.string.anime, listOf(TvType.Anime, TvType.ONA, TvType.AnimeMovie)),
                    Pair(R.string.torrent, listOf(TvType.Torrent)),
                ).filter { item -> apis.any { api -> api.supportedTypes.any { type -> item.second.contains(type) } } }

                val arrayAdapter2 = ArrayAdapter<String>(searchView.context, R.layout.sort_bottom_single_choice)
                arrayAdapter2.addAll(typeChoices.map { getString(it.first) })

                listView2.adapter = arrayAdapter2
                listView2.choiceMode = AbsListView.CHOICE_MODE_MULTIPLE


                /*fun updateMulti() {
                    val set = HashSet<TvType>()

                    for ((index, api) in apis.withIndex()) {
                        if (listView?.checkedItemPositions[index]) {
                            set.addAll(api.supportedTypes)
                        }
                    }

                    if (set.size == 0) {
                        set.addAll(TvType.values())
                    }

                    for ((index, choice) in typeChoices.withIndex()) {
                        listView2?.setItemChecked(index, choice.second.any { set.contains(it) })
                    }
                }*/

                for ((index, item) in apiNames.withIndex()) {
                    listView.setItemChecked(index, apiNamesSetting.contains(item))
                }

                for ((index, item) in typeChoices.withIndex()) {
                    listView2.setItemChecked(index, item.second.any { typesActive.contains(it) })
                }

                fun toggleSearch(isOn: Boolean) {
                    toggle.text =
                        getString(if (isOn) R.string.search_provider_text_types else R.string.search_provider_text_providers)

                    if (isOn) {
                        listView2.visibility = View.VISIBLE
                        listView.visibility = View.GONE
                    } else {
                        listView.visibility = View.VISIBLE
                        listView2.visibility = View.GONE
                    }
                }

                val defVal = context?.getKey(SEARCH_PROVIDER_TOGGLE, true) ?: true
                toggleSearch(defVal)

                toggle.isChecked = defVal
                toggle.setOnCheckedChangeListener { _, isOn ->
                    toggleSearch(isOn)
                }

                listView.setOnItemClickListener { _, _, i, _ ->
                    val types = HashSet<TvType>()
                    for ((index, api) in apis.withIndex()) {
                        if (listView.checkedItemPositions[index]) {
                            types.addAll(api.supportedTypes)
                        }
                    }
                    for ((typeIndex, type) in typeChoices.withIndex()) {
                        listView2.setItemChecked(typeIndex, type.second.any { types.contains(it) })
                    }
                }

                listView2.setOnItemClickListener { _, _, i, _ ->
                    for ((index, api) in apis.withIndex()) {
                        var isSupported = false

                        for ((typeIndex, type) in typeChoices.withIndex()) {
                            if (listView2.checkedItemPositions[typeIndex]) {
                                if (api.supportedTypes.any { type.second.contains(it) }) {
                                    isSupported = true
                                }
                            }
                        }

                        listView.setItemChecked(
                            index,
                            isSupported
                        )
                    }

                    //updateMulti()
                }

                dialog.setOnDismissListener {
                    context?.setKey(SEARCH_PROVIDER_TOGGLE, toggle.isChecked)
                }

                applyButton.setOnClickListener {
                    val settingsManagerLocal = PreferenceManager.getDefaultSharedPreferences(activity)

                    val activeTypes = HashSet<TvType>()
                    for ((index, _) in typeChoices.withIndex()) {
                        if (listView2.checkedItemPositions[index]) {
                            activeTypes.addAll(typeChoices[index].second)
                        }
                    }

                    if (activeTypes.size == 0) {
                        activeTypes.addAll(TvType.values())
                    }


                    val activeApis = HashSet<String>()
                    for ((index, name) in apiNames.withIndex()) {
                        if (listView.checkedItemPositions[index]) {
                            activeApis.add(name)
                        }
                    }

                    if (activeApis.size == 0) {
                        activeApis.addAll(apiNames)
                    }

                    val edit = settingsManagerLocal.edit()
                    edit.putStringSet(
                        getString(R.string.search_providers_list_key),
                        activeApis
                    )
                    edit.putStringSet(
                        getString(R.string.search_types_list_key),
                        activeTypes.map { it.name }.toSet()
                    )
                    edit.apply()
                    providersActive = activeApis
                    typesActive = activeTypes

                    dialog.dismiss()
                }

                cancelButton.setOnClickListener {
                    dialog.dismiss()
                }

                //listView.setSelection(selectedIndex)
                // listView.setItemChecked(selectedIndex, true)
                /*
                val builder: AlertDialog.Builder = AlertDialog.Builder(requireContext())

                builder.setMultiChoiceItems(
                    apiNames.toTypedArray(),
                    apiNames.map { a -> apiNamesSetting.contains(a) }.toBooleanArray()
                ) { _, position: Int, checked: Boolean ->
                    val apiNamesSettingLocal = activity?.getApiSettings()
                    if (apiNamesSettingLocal != null) {
                        val settingsManagerLocal = PreferenceManager.getDefaultSharedPreferences(activity)
                        if (checked) {
                            apiNamesSettingLocal.add(apiNames[position])
                        } else {
                            apiNamesSettingLocal.remove(apiNames[position])
                        }

                        val edit = settingsManagerLocal.edit()
                        edit.putStringSet(
                            getString(R.string.search_providers_list_key),
                            apiNames.filter { a -> apiNamesSettingLocal.contains(a) }.toSet()
                        )
                        edit.apply()
                        providersActive = apiNamesSettingLocal
                    }
                }
                builder.setTitle("Search Providers")
                builder.setNegativeButton("Ok") { _, _ -> }
                builder.show()*/
            }
        }

        main_search.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                searchViewModel.searchAndCancel(query)
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                searchViewModel.quickSearch(newText)
                return true
            }
        })

        observe(searchViewModel.searchResponse) {
            when (it) {
                is Resource.Success -> {
                    it.value.let { data ->
                        if (data.isNotEmpty()) {
                            (cardSpace?.adapter as SearchAdapter?)?.apply {
                                cardList = data.toList()
                                notifyDataSetChanged()
                            }
                        }
                    }
                    searchExitIcon.alpha = 1f
                    search_loading_bar.alpha = 0f
                }
                is Resource.Failure -> {
                    // Toast.makeText(activity, "Server error", Toast.LENGTH_LONG).show()
                    searchExitIcon.alpha = 1f
                    search_loading_bar.alpha = 0f
                }
                is Resource.Loading -> {
                    searchExitIcon.alpha = 0f
                    search_loading_bar.alpha = 1f
                }
            }
        }

        observe(searchViewModel.currentSearch) { list ->
            (search_master_recycler?.adapter as ParentItemAdapter?)?.apply {
                items = list.map {
                    HomePageList(it.apiName, if (it.data is Resource.Success) it.data.value else ArrayList())
                }
                notifyDataSetChanged()
            }
        }

        activity?.let {
            providersActive = it.getApiSettings()
            typesActive = it.getApiTypeSettings()
        }

        main_search.setOnQueryTextFocusChangeListener { searchView, b ->
            if (b) {
                // https://stackoverflow.com/questions/12022715/unable-to-show-keyboard-automatically-in-the-searchview
                searchView?.postDelayed({
                    (activity?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager?)?.showSoftInput(
                        view.findFocus(),
                        0
                    )
                }, 200)
            }
        }
        main_search.onActionViewExpanded()

        val masterAdapter: RecyclerView.Adapter<RecyclerView.ViewHolder> = ParentItemAdapter(listOf(), { callback ->
            SearchHelper.handleSearchClickCallback(activity, callback)
        }, { item ->
            activity?.loadHomepageList(item)
        })

        search_master_recycler.adapter = masterAdapter
        search_master_recycler.layoutManager = GridLayoutManager(context, 1)

        val settingsManager = PreferenceManager.getDefaultSharedPreferences(context)
        val isAdvancedSearch = settingsManager.getBoolean("advanced_search", true)

        search_master_recycler.visibility = if (isAdvancedSearch) View.VISIBLE else View.GONE
        cardSpace.visibility = if (!isAdvancedSearch) View.VISIBLE else View.GONE

        // SubtitlesFragment.push(activity)
        //searchViewModel.search("iron man")
        //(activity as AppCompatActivity).loadResult("https://shiro.is/overlord-dubbed", "overlord-dubbed", "Shiro")
/*
        (activity as AppCompatActivity?)?.supportFragmentManager.beginTransaction()
            .setCustomAnimations(R.anim.enter_anim,
                R.anim.exit_anim,
                R.anim.pop_enter,
                R.anim.pop_exit)
            .add(R.id.homeRoot, PlayerFragment.newInstance(PlayerData(0, null,0)))
            .commit()*/
    }

}