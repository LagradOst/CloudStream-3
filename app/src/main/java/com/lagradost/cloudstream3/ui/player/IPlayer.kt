package com.lagradost.cloudstream3.ui.player

import android.content.Context
import com.lagradost.cloudstream3.ui.subtitles.SaveCaptionStyle
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorUri

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

/** Abstract Exoplayer logic, can be expanded to other players */
interface IPlayer {
    fun getPlaybackSpeed() : Float
    fun setPlaybackSpeed(speed: Float)

    fun getIsPlaying() : Boolean
    fun getDuration() : Long?
    fun getPosition() : Long?

    fun seekTime(time: Long)
    fun seekTo(time: Long)

    fun initCallbacks(
        playerUpdated: (Any?) -> Unit,                              // attach player to view
        updateIsPlaying: ((Pair<Boolean,Boolean>) -> Unit)? = null, // (wasPlaying, isPlaying)
        requestAutoFocus: (() -> Unit)? = null,                     // current player starts, asking for all other programs to shut the fuck up
        playerError: ((Exception) -> Unit)? = null,                 // player error when rendering or misc, used to display toast or log
        playerDimensionsLoaded: ((Pair<Int, Int>) -> Unit)? = null, // (with, height), for UI
        requestedListeningPercentages: List<Int>? = null,           // this is used to request when the player should report back view percentage
        playerPositionChanged: ((Pair<Long, Long>) -> Unit)? = null,// (position, duration) this is used to update UI based of the current time
        nextEpisode: (() -> Unit)? = null,                          // this is used by the player to load the next episode
        prevEpisode: (() -> Unit)? = null,                          // this is used by the player to load the previous episode
    )

    fun updateSubtitleStyle(style: SaveCaptionStyle)
    fun loadPlayer(context: Context, sameEpisode : Boolean, link: ExtractorLink? = null, data: ExtractorUri? = null, startPosition : Long? = null)
    fun handleEvent(event : CSPlayerEvent)

    fun onStop()
    fun onPause()
    fun onResume(context: Context)

    fun release()
}