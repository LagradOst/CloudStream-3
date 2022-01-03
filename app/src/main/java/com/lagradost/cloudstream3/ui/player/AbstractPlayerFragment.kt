package com.lagradost.cloudstream3.ui.player

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.AnimatedVectorDrawable
import android.media.metrics.PlaybackErrorEvent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.SubtitleView
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.MainActivity.Companion.showToast
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.ui.subtitles.SaveCaptionStyle
import com.lagradost.cloudstream3.ui.subtitles.SubtitlesFragment
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.AppUtils.requestLocalAudioFocus
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.UIHelper
import com.lagradost.cloudstream3.utils.UIHelper.hideSystemUI
import kotlinx.android.synthetic.main.fragment_player.*
import kotlinx.android.synthetic.main.player_custom_layout.*

enum class PlayerResize(@StringRes val nameRes: Int) {
    Fit(R.string.resize_fit),
    Fill(R.string.resize_fill),
    Zoom(R.string.resize_zoom),
}

abstract class AbstractPlayerFragment(
    @LayoutRes val layout: Int,
    val player: IPlayer = CS3IPlayer()
) : Fragment() {
    var resizeMode: Int = 0
    var subStyle: SaveCaptionStyle? = null
    var subView: SubtitleView? = null

    private fun updateIsPlaying(playing: Pair<Boolean, Boolean>) {
        val (wasPlaying, isPlaying) = playing


        if (wasPlaying != isPlaying) {
            player_pause_play.setImageResource(if (isPlaying) R.drawable.play_to_pause else R.drawable.pause_to_play)
            val drawable = player_pause_play?.drawable

            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                if (drawable is AnimatedImageDrawable) {
                    drawable.start()
                }
            }
            if (drawable is AnimatedVectorDrawable) {
                drawable.start()
            }
        } else {
            player_pause_play?.setImageResource(if (isPlaying) R.drawable.netflix_pause else R.drawable.netflix_play)
        }

        MainActivity.canEnterPipMode = isPlaying
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity?.let { act ->
                PlayerPipHelper.updatePIPModeActions(act, isPlaying)
            }
        }
    }

    private var pipReceiver: BroadcastReceiver? = null
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        try {
            MainActivity.isInPIPMode = isInPictureInPictureMode
            if (isInPictureInPictureMode) {
                // Hide the full-screen UI (controls, etc.) while in picture-in-picture mode.
                player_holder.alpha = 0f
                pipReceiver = object : BroadcastReceiver() {
                    override fun onReceive(
                        context: Context,
                        intent: Intent,
                    ) {
                        if (ACTION_MEDIA_CONTROL != intent.action) {
                            return
                        }
                        player.handleEvent(
                            CSPlayerEvent.values()[intent.getIntExtra(
                                EXTRA_CONTROL_TYPE,
                                0
                            )]
                        )
                    }
                }
                val filter = IntentFilter()
                filter.addAction(
                    ACTION_MEDIA_CONTROL
                )
                activity?.registerReceiver(pipReceiver, filter)
                val isPlaying = player.getIsPlaying()
                updateIsPlaying(Pair(isPlaying, isPlaying))
            } else {
                // Restore the full-screen UI.
                player_holder.alpha = 1f
                pipReceiver?.let {
                    activity?.unregisterReceiver(it)
                }
                activity?.hideSystemUI()
                this.view?.let { UIHelper.hideKeyboard(it) }
            }
        } catch (e: Exception) {
            logError(e)
        }
    }

    private fun playerError(exception: Exception) {
        when (exception) {
            is PlaybackException -> {
                val msg = exception.message ?: ""
                val errorName = exception.errorCodeName
                when (val code = exception.errorCode) {
                    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND, PlaybackException.ERROR_CODE_IO_NO_PERMISSION, PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> {
                        // if (currentUrl?.url != "") {
                        showToast(
                            activity,
                            "${getString(R.string.source_error)}\n$errorName ($code)\n$msg",
                            Toast.LENGTH_SHORT
                        )
                        // tryNextMirror() //TODO
                        //}
                    }
                    PlaybackException.ERROR_CODE_REMOTE_ERROR, PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, PlaybackException.ERROR_CODE_TIMEOUT, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE -> {
                        showToast(
                            activity,
                            "${getString(R.string.remote_error)}\n$errorName ($code)\n$msg",
                            Toast.LENGTH_SHORT
                        )
                    }
                    PlaybackException.ERROR_CODE_DECODING_FAILED, PlaybackErrorEvent.ERROR_AUDIO_TRACK_INIT_FAILED, PlaybackErrorEvent.ERROR_AUDIO_TRACK_OTHER, PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED, PlaybackException.ERROR_CODE_DECODER_INIT_FAILED, PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED -> {
                        showToast(
                            activity,
                            "${getString(R.string.render_error)}\n$errorName ($code)\n$msg",
                            Toast.LENGTH_SHORT
                        )
                    }
                    else -> {
                        showToast(
                            activity,
                            "${getString(R.string.unexpected_error)}\n$errorName ($code)\n$msg",
                            Toast.LENGTH_SHORT
                        )
                    }
                }
            }
            else -> {
                showToast(activity, exception.message, Toast.LENGTH_SHORT)
            }
        }
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity?.requestLocalAudioFocus(AppUtils.getFocusRequest())
        }
    }

    private fun onSubStyleChanged(style: SaveCaptionStyle) {
        if (player is CS3IPlayer) {
            player.updateSubtitleStyle(style)
        }
    }

    private fun playerUpdated(player: Any?) {
        if (player is ExoPlayer) {
            player_view?.player = player
            player_view?.performClick()
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        resizeMode = getKey(RESIZE_MODE_KEY) ?: 0
        resize(resizeMode, false)

        player.initCallbacks(
            playerUpdated = ::playerUpdated,
            updatePIPModeActions = ::updateIsPlaying,
            playerError = ::playerError,
            requestAutoFocus = ::requestAudioFocus,
        )
        if (player is CS3IPlayer) {
            subView = player_view?.findViewById(R.id.exo_subtitles)
            subStyle = SubtitlesFragment.getCurrentSavedStyle()
            player.initSubtitles(subView, subtitle_holder, subStyle)
            SubtitlesFragment.applyStyleEvent += ::onSubStyleChanged
        }

        context?.let { ctx ->
            player.loadPlayer(
                ctx,
                false,
                ExtractorLink(
                    "idk",
                    "bunny",
                    "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                    "",
                    Qualities.P720.value,
                    false
                ),
            )
        }
    }

    override fun onDestroy() {
        MainActivity.playerEventListener = null
        MainActivity.keyEventListener = null
        SubtitlesFragment.applyStyleEvent -= ::onSubStyleChanged

        // simply resets brightness and notch settings that might have been overridden
        val lp = activity?.window?.attributes
        lp?.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lp?.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
        }
        activity?.window?.attributes = lp

        super.onDestroy()
    }

    fun nextResize() {
        resizeMode = (resizeMode + 1) % PlayerResize.values().size
        resize(resizeMode, true)
    }

    fun resize(resize: Int, showToast: Boolean) {
        resize(PlayerResize.values()[resize], showToast)
    }

    fun resize(resize: PlayerResize, showToast: Boolean) {
        setKey(RESIZE_MODE_KEY, resize.ordinal)
        val type = when (resize) {
            PlayerResize.Fill -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            PlayerResize.Fit -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            PlayerResize.Zoom -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        }
        player_view?.resizeMode = type

        exo_play?.setOnClickListener {
            player.handleEvent(CSPlayerEvent.Play)
        }

        exo_pause?.setOnClickListener {
            player.handleEvent(CSPlayerEvent.Pause)
        }

        if (showToast)
            showToast(activity, resize.nameRes, Toast.LENGTH_SHORT)
    }

    override fun onStart() {
        super.onStart()
    }

    override fun onStop() {
        player.onStop()
        super.onStop()
    }

    override fun onResume() {
        context?.let { ctx ->
            player.onResume(ctx)
        }

        super.onResume()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(layout, container, false)
    }
}