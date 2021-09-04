package com.ArjixWasTaken.cloudstream3.ui.subtitles

import android.app.Activity
import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.FontRes
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.google.android.exoplayer2.text.Cue
import com.google.android.exoplayer2.ui.CaptionStyleCompat
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import com.ArjixWasTaken.cloudstream3.MainActivity
import com.ArjixWasTaken.cloudstream3.MainActivity.Companion.showToast
import com.ArjixWasTaken.cloudstream3.R
import com.ArjixWasTaken.cloudstream3.utils.DataStore.getKey
import com.ArjixWasTaken.cloudstream3.utils.DataStore.setKey
import com.ArjixWasTaken.cloudstream3.utils.Event
import com.ArjixWasTaken.cloudstream3.utils.SingleSelectionHelper.showBottomDialog
import com.ArjixWasTaken.cloudstream3.utils.SingleSelectionHelper.showDialog
import com.ArjixWasTaken.cloudstream3.utils.SingleSelectionHelper.showMultiDialog
import com.ArjixWasTaken.cloudstream3.utils.SubtitleHelper
import com.ArjixWasTaken.cloudstream3.utils.UIHelper.fixPaddingStatusbar
import com.ArjixWasTaken.cloudstream3.utils.UIHelper.hideSystemUI
import com.ArjixWasTaken.cloudstream3.utils.UIHelper.popCurrentPage
import kotlinx.android.synthetic.main.subtitle_settings.*

const val SUBTITLE_KEY = "subtitle_settings"
const val SUBTITLE_AUTO_SELECT_KEY = "subs_auto_select"
const val SUBTITLE_DOWNLOAD_KEY = "subs_auto_download"

data class SaveCaptionStyle(
    var foregroundColor: Int,
    var backgroundColor: Int,
    var windowColor: Int,
    @CaptionStyleCompat.EdgeType
    var edgeType: Int,
    var edgeColor: Int,
    @FontRes
    var typeface: Int?,
    /**in dp**/
    var elevation: Int,
)

class SubtitlesFragment : Fragment() {
    companion object {
        val applyStyleEvent = Event<SaveCaptionStyle>()

        fun Context.fromSaveToStyle(data: SaveCaptionStyle): CaptionStyleCompat {
            val typeface = data.typeface
            return CaptionStyleCompat(
                data.foregroundColor, data.backgroundColor, data.windowColor, data.edgeType, data.edgeColor,
                if (typeface == null) Typeface.SANS_SERIF else ResourcesCompat.getFont(this, typeface)
            )
        }

        fun push(activity: Activity?, hide: Boolean = true) {
            (activity as FragmentActivity?)?.supportFragmentManager?.beginTransaction()
                ?.setCustomAnimations(
                    R.anim.enter_anim,
                    R.anim.exit_anim,
                    R.anim.pop_enter,
                    R.anim.pop_exit
                )
                ?.add(
                    R.id.homeRoot,
                    SubtitlesFragment().apply {
                        arguments = Bundle().apply {
                            putBoolean("hide", hide)
                        }
                    }
                )
                ?.commit()
        }

        private fun getDefColor(id: Int): Int {
            return when (id) {
                0 -> Color.WHITE
                1 -> Color.BLACK
                2 -> Color.TRANSPARENT
                3 -> Color.TRANSPARENT
                else -> Color.TRANSPARENT
            }
        }

        fun Context.saveStyle(style: SaveCaptionStyle) {
            this.setKey(SUBTITLE_KEY, style)
        }

        fun Context.getCurrentSavedStyle(): SaveCaptionStyle {
            return this.getKey(SUBTITLE_KEY) ?: SaveCaptionStyle(
                getDefColor(0),
                getDefColor(2),
                getDefColor(3),
                CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                getDefColor(1),
                null,
                0,
            )
        }

        private fun Context.getCurrentStyle(): CaptionStyleCompat {
            return fromSaveToStyle(getCurrentSavedStyle())
        }

        private fun getPixels(unit: Int, size: Float): Int {
            val metrics: DisplayMetrics = Resources.getSystem().displayMetrics
            return TypedValue.applyDimension(unit, size, metrics).toInt()
        }

        fun Context.getDownloadSubsLanguageISO639_1(): List<String> {
            return getKey(SUBTITLE_DOWNLOAD_KEY) ?: listOf("en")
        }

        fun Context.getAutoSelectLanguageISO639_1(): String {
            return getKey(SUBTITLE_AUTO_SELECT_KEY) ?: "en"
        }
    }

    private fun onColorSelected(stuff: Pair<Int, Int>) {
        context?.setColor(stuff.first, stuff.second)
        if (hide)
            activity?.hideSystemUI()
    }

    private fun onDialogDismissed(id: Int) {
        if (hide)
            activity?.hideSystemUI()
    }

    private fun Context.setColor(id: Int, color: Int?) {
        val realColor = color ?: getDefColor(id)
        when (id) {
            0 -> state.foregroundColor = realColor
            1 -> state.edgeColor = realColor
            2 -> state.backgroundColor = realColor
            3 -> state.windowColor = realColor

            else -> {
            }
        }
        updateState()
    }

    private fun Context.updateState() {
        subtitle_text?.setStyle(fromSaveToStyle(state))
    }

    private fun getColor(id: Int): Int {
        val color = when (id) {
            0 -> state.foregroundColor
            1 -> state.edgeColor
            2 -> state.backgroundColor
            3 -> state.windowColor

            else -> Color.TRANSPARENT
        }

        return if (color == Color.TRANSPARENT) Color.BLACK else color
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.subtitle_settings, container, false)
    }

    private lateinit var state: SaveCaptionStyle
    private var hide: Boolean = true

    override fun onDestroy() {
        super.onDestroy()
        MainActivity.onColorSelectedEvent -= ::onColorSelected
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        hide = arguments?.getBoolean("hide") ?: true
        MainActivity.onColorSelectedEvent += ::onColorSelected
        MainActivity.onDialogDismissedEvent += ::onDialogDismissed

        context?.fixPaddingStatusbar(subs_root)

        state = requireContext().getCurrentSavedStyle()
        context?.updateState()

        fun View.setup(id: Int) {
            this.setOnClickListener {
                activity?.let {
                    ColorPickerDialog.newBuilder()
                        .setDialogId(id)
                        .setColor(getColor(id))
                        .show(it)
                }
            }

            this.setOnLongClickListener {
                it.context.setColor(id, null)
                showToast(activity, R.string.subs_default_reset_toast, Toast.LENGTH_SHORT)
                return@setOnLongClickListener true
            }
        }

        subs_text_color.setup(0)
        subs_outline_color.setup(1)
        subs_background_color.setup(2)
        subs_window_color.setup(3)

        val dismissCallback = {
            if (hide)
                activity?.hideSystemUI()
        }

        subs_subtitle_elevation.setOnClickListener { textView ->
            val elevationTypes = listOf(
                Pair(0, "None"),
                Pair(10, "10"),
                Pair(20, "20"),
                Pair(30, "30"),
                Pair(40, "40"),
            )

            textView.context.showBottomDialog(
                elevationTypes.map { it.second },
                elevationTypes.map { it.first }.indexOf(state.elevation),
                (textView as TextView).text.toString(),
                false,
                dismissCallback
            ) { index ->
                state.elevation = elevationTypes.map { it.first }[index]
                textView.context.updateState()
                if (hide)
                    activity?.hideSystemUI()
            }
        }

        subs_subtitle_elevation.setOnLongClickListener {
            state.elevation = 0
            it.context.updateState()
            showToast(activity, R.string.subs_default_reset_toast, Toast.LENGTH_SHORT)
            return@setOnLongClickListener true
        }

        subs_edge_type.setOnClickListener { textView ->
            val edgeTypes = listOf(
                Pair(CaptionStyleCompat.EDGE_TYPE_NONE, "None"),
                Pair(CaptionStyleCompat.EDGE_TYPE_OUTLINE, "Outline"),
                Pair(CaptionStyleCompat.EDGE_TYPE_DEPRESSED, "Depressed"),
                Pair(CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW, "Shadow"),
                Pair(CaptionStyleCompat.EDGE_TYPE_RAISED, "Raised"),
            )

            textView.context.showBottomDialog(
                edgeTypes.map { it.second },
                edgeTypes.map { it.first }.indexOf(state.edgeType),
                (textView as TextView).text.toString(),
                false,
                dismissCallback
            ) { index ->
                state.edgeType = edgeTypes.map { it.first }[index]
                textView.context.updateState()
            }
        }

        subs_edge_type.setOnLongClickListener {
            state.edgeType = CaptionStyleCompat.EDGE_TYPE_OUTLINE
            it.context.updateState()
            showToast(activity, R.string.subs_default_reset_toast, Toast.LENGTH_SHORT)
            return@setOnLongClickListener true
        }

        subs_font.setOnClickListener { textView ->
            val fontTypes = listOf(
                Pair(null, "Normal"),
                Pair(R.font.trebuchet_ms, "Trebuchet MS"),
                Pair(R.font.google_sans, "Google Sans"),
                Pair(R.font.open_sans, "Open Sans"),
                Pair(R.font.futura, "Futura"),
                Pair(R.font.consola, "Consola"),
                Pair(R.font.gotham, "Gotham"),
                Pair(R.font.lucida_grande, "Lucida Grande"),
                Pair(R.font.stix_general, "STIX General"),
                Pair(R.font.times_new_roman, "Times New Roman"),
                Pair(R.font.verdana, "Verdana"),
                Pair(R.font.ubuntu_regular, "Ubuntu"),
                Pair(R.font.poppins_regular, "Poppins"),
            )

            textView.context.showBottomDialog(
                fontTypes.map { it.second },
                fontTypes.map { it.first }.indexOf(state.typeface),
                (textView as TextView).text.toString(),
                false,
                dismissCallback
            ) { index ->
                state.typeface = fontTypes.map { it.first }[index]
                textView.context.updateState()
            }
        }

        subs_font.setOnLongClickListener { textView ->
            state.typeface = null
            textView.context.updateState()
            showToast(activity, R.string.subs_default_reset_toast, Toast.LENGTH_SHORT)
            return@setOnLongClickListener true
        }

        subs_auto_select_language.setOnClickListener { textView ->
            val langMap = arrayListOf(
                SubtitleHelper.Language639("None", "None", "", "", "", "", ""),
            )
            langMap.addAll(SubtitleHelper.languages)

            val lang639_1 = langMap.map { it.ISO_639_1 }
            textView.context.showDialog(
                langMap.map { it.languageName },
                lang639_1.indexOf(textView.context.getAutoSelectLanguageISO639_1()),
                (textView as TextView).text.toString(),
                true,
                dismissCallback
            ) { index ->
                textView.context.setKey(SUBTITLE_AUTO_SELECT_KEY, lang639_1[index])
            }
        }

        subs_auto_select_language.setOnLongClickListener { textView ->
            textView.context.setKey(SUBTITLE_AUTO_SELECT_KEY, "en")
            showToast(activity, R.string.subs_default_reset_toast, Toast.LENGTH_SHORT)
            return@setOnLongClickListener true
        }

        subs_download_languages.setOnClickListener { textView ->
            val langMap = SubtitleHelper.languages
            val lang639_1 = langMap.map { it.ISO_639_1 }
            val keys = textView.context.getDownloadSubsLanguageISO639_1()
            val keyMap = keys.map { lang639_1.indexOf(it) }.filter { it >= 0 }

            textView.context.showMultiDialog(
                langMap.map { it.languageName },
                keyMap,
                (textView as TextView).text.toString(),
                dismissCallback
            ) { indexList ->
                textView.context.setKey(SUBTITLE_DOWNLOAD_KEY, indexList.map { lang639_1[it] }.toList())
            }
        }

        subs_download_languages.setOnLongClickListener { textView ->
            textView.context.setKey(SUBTITLE_DOWNLOAD_KEY, listOf("en"))

            showToast(activity, R.string.subs_default_reset_toast, Toast.LENGTH_SHORT)
            return@setOnLongClickListener true
        }

        cancel_btt.setOnClickListener {
            activity?.popCurrentPage()
        }

        apply_btt.setOnClickListener {
            it.context.saveStyle(state)
            applyStyleEvent.invoke(state)
            it.context.fromSaveToStyle(state)
            activity?.popCurrentPage()
        }

        subtitle_text.setCues(
            listOf(
                Cue.Builder()
                    .setTextSize(
                        getPixels(TypedValue.COMPLEX_UNIT_SP, 25.0f).toFloat(),
                        Cue.TEXT_SIZE_TYPE_ABSOLUTE
                    )
                    .setText("The quick brown fox jumps over the lazy dog").build()
            )
        )
    }
}