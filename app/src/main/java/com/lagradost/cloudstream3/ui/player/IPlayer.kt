package com.lagradost.cloudstream3.ui.player

import android.content.Context
import android.net.Uri
import com.lagradost.cloudstream3.ui.subtitles.SaveCaptionStyle
import com.lagradost.cloudstream3.utils.ExtractorLink

enum class CSPlayerEvent(val value: Int) {
    Pause(0),
    Play(1),
    SeekForward(2),
    SeekBack(3),

    //SkipCurrentChapter(4),
    NextEpisode(5),
    PrevEpisode(6),
    PlayPauseToggle(7),
    ToggleMute(8),
}

interface IPlayer {
    fun getPlaybackSpeed() : Float
    fun setPlaybackSpeed(speed: Float)

    fun getIsPlaying() : Boolean
    fun getDuration() : Long?
    fun getPosition() : Long?

    fun seekTime(time: Long)
    fun seekTo(time: Long)

    fun initCallbacks(
        playerUpdated: (Any?) -> Unit,
        updatePIPModeActions: ((Pair<Boolean,Boolean>) -> Unit)? = null,
        requestAutoFocus: (() -> Unit)? = null,
        playerError: ((Exception) -> Unit)? = null,
        playerDimensionsLoaded: ((Pair<Int, Int>) -> Unit)? = null,
        requestedListeningPercentages: List<Int>? = null,
        playerPositionChanged: ((Pair<Long, Long>) -> Unit)? = null,
        nextEpisode: (() -> Unit)? = null,
        prevEpisode: (() -> Unit)? = null,
    )

    fun updateSubtitleStyle(style: SaveCaptionStyle)
    fun loadPlayer(context: Context, sameEpisode : Boolean, link: ExtractorLink? = null, uri: Uri? = null)
    fun handleEvent(event : CSPlayerEvent)

    fun onStop()
    fun onPause()
    fun onResume(context: Context)

}