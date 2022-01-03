package com.lagradost.cloudstream3.ui.player

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Color
import android.media.AudioManager
import android.os.Bundle
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.MotionEvent
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showDialog
import com.lagradost.cloudstream3.utils.UIHelper.colorFromAttribute
import com.lagradost.cloudstream3.utils.UIHelper.getNavigationBarHeight
import com.lagradost.cloudstream3.utils.UIHelper.getStatusBarHeight
import com.lagradost.cloudstream3.utils.UIHelper.hideSystemUI
import com.lagradost.cloudstream3.utils.UIHelper.showSystemUI
import com.lagradost.cloudstream3.utils.UIHelper.toPx
import com.lagradost.cloudstream3.utils.Vector2
import kotlinx.android.synthetic.main.player_custom_layout.*
import kotlin.math.*

const val MINIMUM_SEEK_TIME = 7000
const val MINIMUM_VERTICAL_SWIPE = 2.0f     // in percentage
const val MINIMUM_HORIZONTAL_SWIPE = 2.0f   // in percentage
const val VERTICAL_MULTIPLIER = 2.0f
const val HORIZONTAL_MULTIPLIER = 2.0f
const val DOUBLE_TAB_MAXIMUM_HOLD_TIME = 200
const val DOUBLE_TAB_MINIMUM_TIME_BETWEEN = 200
const val DOUBLE_TAB_PAUSE_PERCENTAGE = 0.15 // in both directions

// All the UI Logic for the player
class FullScreenPlayer : AbstractPlayerFragment(R.layout.fragment_player_v2) {
    // state of player UI
    private var isShowing = false
    private var isLocked = false

    // options for player
    private var fastForwardTime = 10000L
    private var swipeHorizontalEnabled = false
    private var swipeVerticalEnabled = false
    private var playBackSpeedEnabled = false
    private var playerResizeEnabled = false
    private var doubleTapEnabled = false
    private var doubleTapPauseEnabled = true

    //private var useSystemBrightness = false
    private var useTrueSystemBrightness = true

    private val displayMetrics: DisplayMetrics = Resources.getSystem().displayMetrics

    // screenWidth and screenHeight does always
    // refer to the screen while in landscape mode
    private val screenWidth: Int
        get() {
            return max(displayMetrics.widthPixels, displayMetrics.heightPixels)
        }
    private val screenHeight: Int
        get() {
            return min(displayMetrics.widthPixels, displayMetrics.heightPixels)
        }

    private var statusBarHeight: Int? = null
    private var navigationBarHeight: Int? = null

    private val brightnessIcons = listOf(
        R.drawable.sun_1,
        R.drawable.sun_2,
        R.drawable.sun_3,
        R.drawable.sun_4,
        R.drawable.sun_5,
        R.drawable.sun_6,
        //R.drawable.sun_7,
        // R.drawable.ic_baseline_brightness_1_24,
        // R.drawable.ic_baseline_brightness_2_24,
        // R.drawable.ic_baseline_brightness_3_24,
        // R.drawable.ic_baseline_brightness_4_24,
        // R.drawable.ic_baseline_brightness_5_24,
        // R.drawable.ic_baseline_brightness_6_24,
        // R.drawable.ic_baseline_brightness_7_24,
    )

    private val volumeIcons = listOf(
        R.drawable.ic_baseline_volume_mute_24,
        R.drawable.ic_baseline_volume_down_24,
        R.drawable.ic_baseline_volume_up_24,
    )

    /** Returns false if the touch is on the status bar or navigation bar*/
    private fun isValidTouch(rawX: Float, rawY: Float): Boolean {
        val statusHeight = statusBarHeight ?: 0
        val navHeight = navigationBarHeight ?: 0
        return rawY > statusHeight && rawX < screenWidth - navHeight
    }

    private fun animateLayoutChanges() {
        if (isShowing) {
            updateUIVisibility()
        } else {
            player_holder.postDelayed({ updateUIVisibility() }, 200)
        }

        val titleMove = if (isShowing) 0f else -50.toPx.toFloat()
        video_title?.let {
            ObjectAnimator.ofFloat(it, "translationY", titleMove).apply {
                duration = 200
                start()
            }
        }
        video_title_rez?.let {
            ObjectAnimator.ofFloat(it, "translationY", titleMove).apply {
                duration = 200
                start()
            }
        }
        val playerBarMove = if (isShowing) 0f else 50.toPx.toFloat()
        bottom_player_bar?.let {
            ObjectAnimator.ofFloat(it, "translationY", playerBarMove).apply {
                duration = 200
                start()
            }
        }

        val fadeTo = if (isShowing) 1f else 0f
        val fadeAnimation = AlphaAnimation(1f - fadeTo, fadeTo)

        fadeAnimation.duration = 100
        fadeAnimation.fillAfter = true

        val sView = subView
        val sStyle = subStyle
        if (sView != null && sStyle != null) {
            val move = if (isShowing) -((bottom_player_bar?.height?.toFloat()
                ?: 0f) + 10.toPx) else -sStyle.elevation.toPx.toFloat()
            ObjectAnimator.ofFloat(sView, "translationY", move).apply {
                duration = 200
                start()
            }
        }

        if (!isLocked) {
            player_ffwd_holder?.alpha = 1f
            player_rew_holder?.alpha = 1f
            player_pause_holder?.alpha = 1f

            shadow_overlay?.startAnimation(fadeAnimation)
            player_ffwd_holder?.startAnimation(fadeAnimation)
            player_rew_holder?.startAnimation(fadeAnimation)
            player_pause_holder?.startAnimation(fadeAnimation)
        }

        bottom_player_bar?.startAnimation(fadeAnimation)
        player_top_holder?.startAnimation(fadeAnimation)
    }

    override fun onResume() {
        activity?.hideSystemUI()
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        super.onResume()
    }

    override fun onDestroy() {
        activity?.showSystemUI()
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER
        super.onDestroy()
    }

    private fun setPlayBackSpeed(speed: Float) {
        try {
            setKey(PLAYBACK_SPEED_KEY, speed)
            playback_speed_btt?.text =
                getString(R.string.player_speed_text_format).format(speed)
                    .replace(".0x", "x")
        } catch (e: Exception) {
            // the format string was wrong
            logError(e)
        }

        player.setPlaybackSpeed(speed)
    }

    private fun skipOp() {
        player.seekTime(85000) // skip 85s
    }

    private fun showSpeedDialog() {
        val speedsText =
            listOf(
                "0.5x",
                "0.75x",
                "0.85x",
                "1x",
                "1.15x",
                "1.25x",
                "1.4x",
                "1.5x",
                "1.75x",
                "2x"
            )
        val speedsNumbers =
            listOf(0.5f, 0.75f, 0.85f, 1f, 1.15f, 1.25f, 1.4f, 1.5f, 1.75f, 2f)
        val speedIndex = speedsNumbers.indexOf(player.getPlaybackSpeed())

        activity?.let { act ->
            act.showDialog(
                speedsText,
                speedIndex,
                act.getString(R.string.player_speed),
                false,
                {
                    activity?.hideSystemUI()
                }) { index ->
                activity?.hideSystemUI()
                setPlayBackSpeed(speedsNumbers[index])
            }
        }
    }

    fun resetRewindText() {
        exo_rew_text?.text =
            getString(R.string.rew_text_regular_format).format(fastForwardTime / 1000)
    }

    fun resetFastForwardText() {
        exo_ffwd_text?.text =
            getString(R.string.ffw_text_regular_format).format(fastForwardTime / 1000)
    }

    private fun rewind() {
        try {
            player_rew_holder?.alpha = 1f

            val rotateLeft = AnimationUtils.loadAnimation(context, R.anim.rotate_left)
            exo_rew?.startAnimation(rotateLeft)

            val goLeft = AnimationUtils.loadAnimation(context, R.anim.go_left)
            goLeft.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation?) {}

                override fun onAnimationRepeat(animation: Animation?) {}

                override fun onAnimationEnd(animation: Animation?) {
                    exo_rew_text?.post {
                        resetRewindText()
                        player_rew_holder?.alpha = if (isShowing) 1f else 0f
                    }
                }
            })
            exo_rew_text?.startAnimation(goLeft)
            exo_rew_text?.text = getString(R.string.rew_text_format).format(fastForwardTime / 1000)
            player.seekTime(-fastForwardTime)
        } catch (e: Exception) {
            logError(e)
        }
    }

    private fun fastForward() {
        try {
            player_ffwd_holder?.alpha = 1f
            val rotateRight = AnimationUtils.loadAnimation(context, R.anim.rotate_right)
            exo_ffwd?.startAnimation(rotateRight)

            val goRight = AnimationUtils.loadAnimation(context, R.anim.go_right)
            goRight.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation?) {}

                override fun onAnimationRepeat(animation: Animation?) {}

                override fun onAnimationEnd(animation: Animation?) {
                    exo_ffwd_text?.post {
                        resetFastForwardText()
                        player_ffwd_holder?.alpha = if (isShowing) 1f else 0f
                    }
                }
            })
            exo_ffwd_text?.startAnimation(goRight)
            exo_ffwd_text?.text = getString(R.string.ffw_text_format).format(fastForwardTime / 1000)
            player.seekTime(fastForwardTime)
        } catch (e: Exception) {
            logError(e)
        }
    }

    private fun onClickChange() {
        isShowing = !isShowing
        if (isShowing) {
            autoHide()
        }
        activity?.hideSystemUI()
        animateLayoutChanges()
    }

    private fun toggleLock() {
        if (!isShowing) {
            onClickChange()
        }

        isLocked = !isLocked
        if (isLocked && isShowing) {
            player_holder?.postDelayed({
                if (isLocked && isShowing) {
                    onClickChange()
                }
            }, 200)
        }

        val fadeTo = if (isLocked) 0f else 1f

        val fadeAnimation = AlphaAnimation(video_title.alpha, fadeTo).apply {
            duration = 100
            fillAfter = true
        }

        updateUIVisibility()
        // MENUS
        //centerMenu.startAnimation(fadeAnimation)
        player_pause_holder?.startAnimation(fadeAnimation)
        player_ffwd_holder?.startAnimation(fadeAnimation)
        player_rew_holder?.startAnimation(fadeAnimation)
        player_media_route_button?.startAnimation(fadeAnimation)
        //video_bar.startAnimation(fadeAnimation)

        //TITLE
        video_title_rez?.startAnimation(fadeAnimation)
        video_title?.startAnimation(fadeAnimation)

        // BOTTOM
        player_lock_holder?.startAnimation(fadeAnimation)
        video_go_back_holder2?.startAnimation(fadeAnimation)

        shadow_overlay?.startAnimation(fadeAnimation)

        updateLockUI()
    }

    private fun updateUIVisibility() {
        val isGone = isLocked || !isShowing
        player_lock_holder?.isGone = isGone
        player_video_bar?.isGone = isGone
        player_pause_holder?.isGone = isGone
        player_pause_holder?.isGone = isGone
        player_top_holder?.isGone = isGone
    }

    private fun updateLockUI() {
        lock_player?.setIconResource(if (isLocked) R.drawable.video_locked else R.drawable.video_unlocked)
        val color = if (isLocked) context?.colorFromAttribute(R.attr.colorPrimary)
        else Color.WHITE
        if (color != null) {
            lock_player?.setTextColor(color)
            lock_player?.iconTint = ColorStateList.valueOf(color)
            lock_player?.rippleColor = ColorStateList.valueOf(Color.argb(50, color.red, color.green, color.blue))
        }
    }

    private var currentTapIndex = 0
    private fun autoHide() {
        currentTapIndex++
        val index = currentTapIndex
        player_holder?.postDelayed({
            if (isShowing && index == currentTapIndex && player.getIsPlaying()) {
                onClickChange()
            }
        }, 2000)
    }

    private var isCurrentTouchValid = false
    private var currentTouchStart: Vector2? = null
    private var currentTouchLast: Vector2? = null
    private var currentTouchAction: TouchAction? = null
    private var currentTouchStartPlayerTime: Long? = null // the time in the player when you first click
    private var currentTouchStartTime: Long? = null // the system time when you first click
    private var currentLastTouchEndTime: Long = 0 // the system time when you released your finger
    private var currentClickCount: Int =
        0 // amount of times you have double clicked, will reset when other action is taken

    // requested volume and brightness is used to make swiping smoother
    // to make it not jump between values,
    // this value is within the range [0,1]
    private var currentRequestedVolume: Float = 0.0f
    private var currentRequestedBrightness: Float = 1.0f

    enum class TouchAction {
        Brightness,
        Volume,
        Time,
    }

    companion object {
        private fun forceLetters(inp: Long, letters: Int = 2): String {
            val added: Int = letters - inp.toString().length
            return if (added > 0) {
                "0".repeat(added) + inp.toString()
            } else {
                inp.toString()
            }
        }

        private fun convertTimeToString(sec: Long): String {
            val rsec = sec % 60L
            val min = ceil((sec - rsec) / 60.0).toInt()
            val rmin = min % 60L
            val h = ceil((min - rmin) / 60.0).toLong()
            //int rh = h;// h % 24;
            return (if (h > 0) forceLetters(h) + ":" else "") + (if (rmin >= 0 || h >= 0) forceLetters(
                rmin
            ) + ":" else "") + forceLetters(
                rsec
            )
        }
    }

    private fun calculateNewTime(startTime: Long?, touchStart: Vector2?, touchEnd: Vector2?): Long? {
        if (touchStart == null || touchEnd == null || startTime == null) return null
        val diffX = (touchEnd.x - touchStart.x) * HORIZONTAL_MULTIPLIER / screenWidth.toFloat()
        val duration = player.getDuration() ?: return null
        return max(min(startTime + ((duration * (diffX * diffX)) * (if (diffX < 0) -1 else 1)).toLong(), duration), 0)
    }

    private fun getBrightness(): Float? {
        return if (useTrueSystemBrightness) {
            try {
                Settings.System.getInt(
                    context?.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS
                ) / 255f
            } catch (e: Exception) {
                // because true system brightness requires
                // permission, this is a lazy way to check
                // as it will throw an error if we do not have it
                useTrueSystemBrightness = false
                return getBrightness()
            }
        } else {
            try {
                activity?.window?.attributes?.screenBrightness
            } catch (e: Exception) {
                logError(e)
                null
            }
        }
    }

    private fun setBrightness(brightness: Float) {
        if (useTrueSystemBrightness) {
            try {
                Settings.System.putInt(
                    context?.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                )

                Settings.System.putInt(
                    context?.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS, (brightness * 255).toInt()
                )
            } catch (e: Exception) {
                useTrueSystemBrightness = false
                setBrightness(brightness)
            }
        } else {
            try {
                val lp = activity?.window?.attributes
                lp?.screenBrightness = brightness
                activity?.window?.attributes = lp
            } catch (e: Exception) {
                logError(e)
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun handleMotionEvent(view: View?, event: MotionEvent?): Boolean {
        if (event == null || view == null) return false
        val currentTouch = Vector2(event.x, event.y)
        val startTouch = currentTouchStart

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // validates if the touch is inside of the player area
                isCurrentTouchValid = isValidTouch(currentTouch.x, currentTouch.y)
                if (isCurrentTouchValid) {
                    currentTouchStartTime = System.currentTimeMillis()
                    currentTouchStart = currentTouch
                    currentTouchLast = currentTouch
                    currentTouchStartPlayerTime = player.getPosition()

                    getBrightness()?.let {
                        currentRequestedBrightness = it
                    }
                    (activity?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.let { audioManager ->
                        val currentVolume =
                            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                        val maxVolume =
                            audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

                        currentRequestedVolume = currentVolume.toFloat() / maxVolume.toFloat()
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                if (isCurrentTouchValid && !isLocked) {
                    // seek time
                    if (swipeHorizontalEnabled && currentTouchAction == TouchAction.Time) {
                        val startTime = currentTouchStartPlayerTime
                        if (startTime != null) {
                            calculateNewTime(startTime, startTouch, currentTouch)?.let { seekTo ->
                                if (abs(seekTo - startTime) > MINIMUM_SEEK_TIME) {
                                    player.seekTo(seekTo)
                                }
                            }
                        }
                    }
                }

                // see if click is eligible for seek 10s
                val holdTime = currentTouchStartTime?.minus(System.currentTimeMillis())
                if (isCurrentTouchValid // is valid
                    && currentTouchAction == null // no other action like swiping is taking place

                    && holdTime != null
                    && holdTime < DOUBLE_TAB_MAXIMUM_HOLD_TIME // it is a click not a long hold
                ) {
                    if (doubleTapEnabled
                        && !isLocked
                        && (System.currentTimeMillis() - currentLastTouchEndTime) < DOUBLE_TAB_MINIMUM_TIME_BETWEEN // the time since the last action is short
                    ) {
                        currentClickCount++

                        if (currentClickCount >= 1) { // have double clicked
                            if (doubleTapPauseEnabled) { // you can pause if your tap is in the middle of the screen
                                when {
                                    currentTouch.x < screenWidth / 2 - (DOUBLE_TAB_PAUSE_PERCENTAGE * screenWidth) -> {
                                        rewind()
                                    }
                                    currentTouch.x > screenWidth / 2 + (DOUBLE_TAB_PAUSE_PERCENTAGE * screenWidth) -> {
                                        fastForward()
                                    }
                                    else -> {
                                        player.handleEvent(CSPlayerEvent.PlayPauseToggle)
                                    }
                                }
                            } else {
                                if (currentTouch.x < screenWidth / 2) {
                                    rewind()
                                } else {
                                    fastForward()
                                }
                            }
                        }
                    } else {
                        // is a valid click but not fast enough for seek
                        currentClickCount = 0

                        onClickChange()
                    }
                } else {
                    currentClickCount = 0
                }

                // reset variables
                isCurrentTouchValid = false
                currentTouchStart = null
                currentTouchAction = null
                currentTouchStartPlayerTime = null
                currentTouchLast = null
                currentTouchStartTime = null

                // resets UI
                timeText?.isVisible = false
                progressBarLeftHolder?.isVisible = false
                progressBarRightHolder?.isVisible = false
                currentLastTouchEndTime = System.currentTimeMillis()
            }
            MotionEvent.ACTION_MOVE -> {
                // if current touch is valid
                if (startTouch != null && isCurrentTouchValid && !isLocked) {
                    // action is unassigned and can therefore be assigned
                    if (currentTouchAction == null) {
                        val diffFromStart = startTouch - currentTouch

                        if (swipeVerticalEnabled) {
                            if (abs(diffFromStart.y * 100 / screenHeight) > MINIMUM_VERTICAL_SWIPE) {
                                // left = Brightness, right = Volume, but the UI is reversed to show the UI better
                                currentTouchAction = if (startTouch.x < screenWidth / 2) {
                                    TouchAction.Brightness
                                } else {
                                    TouchAction.Volume
                                }
                            }
                        }
                        if (swipeHorizontalEnabled) {
                            if (abs(diffFromStart.x * 100 / screenHeight) > MINIMUM_HORIZONTAL_SWIPE) {
                                currentTouchAction = TouchAction.Time
                            }
                        }
                    }

                    // display action
                    val lastTouch = currentTouchLast
                    if (lastTouch != null) {
                        val diffFromLast = lastTouch - currentTouch
                        val verticalAddition = diffFromLast.y * VERTICAL_MULTIPLIER / screenHeight.toFloat()

                        // update UI
                        timeText?.isVisible = false
                        progressBarLeftHolder?.isVisible = false
                        progressBarRightHolder?.isVisible = false

                        when (currentTouchAction) {
                            TouchAction.Time -> {
                                // this simply updates UI as the seek logic happens on release
                                // startTime is rounded to make the UI sync in a nice way
                                val startTime = currentTouchStartPlayerTime?.div(1000L)?.times(1000L)
                                if (startTime != null) {
                                    calculateNewTime(startTime, startTouch, currentTouch)?.let { newMs ->
                                        val skipMs = newMs - startTime
                                        timeText?.text = "${convertTimeToString(newMs / 1000)} [${
                                            (if (abs(skipMs) < 1000) "" else (if (skipMs > 0) "+" else "-"))
                                        }${convertTimeToString(abs(skipMs / 1000))}]"
                                        timeText?.isVisible = true
                                    }
                                }
                            }
                            TouchAction.Brightness -> {
                                progressBarRightHolder?.isVisible = true
                                val lastRequested = currentRequestedBrightness
                                currentRequestedBrightness =
                                    min(
                                        1.0f,
                                        max(currentRequestedBrightness + verticalAddition, 0.0f)
                                    )

                                // this is to not spam request it, just in case it fucks over someone
                                if (lastRequested != currentRequestedBrightness)
                                    setBrightness(currentRequestedBrightness)

                                // max is set high to make it smooth
                                player_progressbar_right?.max = 100_000
                                player_progressbar_right?.progress =
                                    max(2_000, (currentRequestedBrightness * 100_000f).toInt())

                                player_progressbar_right_icon?.setImageResource(
                                    brightnessIcons[min( // clamp the value just in case
                                        brightnessIcons.size - 1,
                                        max(0, round(currentRequestedBrightness * (brightnessIcons.size - 1)).toInt())
                                    )]
                                )
                            }
                            TouchAction.Volume -> {
                                (activity?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.let { audioManager ->
                                    progressBarLeftHolder?.isVisible = true
                                    val maxVolume =
                                        audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                    val currentVolume =
                                        audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

                                    // clamps volume and adds swipe
                                    currentRequestedVolume =
                                        min(
                                            1.0f,
                                            max(currentRequestedVolume + verticalAddition, 0.0f)
                                        )

                                    // max is set high to make it smooth
                                    player_progressbar_left?.max = 100_000
                                    player_progressbar_left?.progress =
                                        max(2_000, (currentRequestedVolume * 100_000f).toInt())

                                    player_progressbar_left_icon?.setImageResource(
                                        volumeIcons[min( // clamp the value just in case
                                            volumeIcons.size - 1,
                                            max(0, round(currentRequestedVolume * (volumeIcons.size - 1)).toInt())
                                        )]
                                    )

                                    // this is used instead of set volume because old devices does not support it
                                    val desiredVolume = round(currentRequestedVolume * maxVolume).toInt()
                                    if (desiredVolume != currentVolume) {
                                        val newVolumeAdjusted =
                                            if (desiredVolume < currentVolume) AudioManager.ADJUST_LOWER else AudioManager.ADJUST_RAISE

                                        audioManager.adjustStreamVolume(
                                            AudioManager.STREAM_MUSIC,
                                            newVolumeAdjusted,
                                            0
                                        )
                                    }
                                }
                            }
                            else -> Unit
                        }
                    }
                }
            }
        }
        currentTouchLast = currentTouch
        return true
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // init variables
        setPlayBackSpeed(getKey(PLAYBACK_SPEED_KEY) ?: 1.0f)
        fastForwardTime = getKey(PLAYBACK_FASTFORWARD) ?: 10000L

        try {
            val settingsManager = PreferenceManager.getDefaultSharedPreferences(activity)
            context?.let { ctx ->
                navigationBarHeight = ctx.getNavigationBarHeight()
                statusBarHeight = ctx.getStatusBarHeight()

                swipeHorizontalEnabled =
                    settingsManager.getBoolean(ctx.getString(R.string.swipe_enabled_key), true)
                swipeVerticalEnabled =
                    settingsManager.getBoolean(ctx.getString(R.string.swipe_vertical_enabled_key), true)
                playBackSpeedEnabled = settingsManager.getBoolean(
                    ctx.getString(R.string.playback_speed_enabled_key),
                    false
                )
                playerResizeEnabled =
                    settingsManager.getBoolean(ctx.getString(R.string.player_resize_enabled_key), true)
                doubleTapEnabled =
                    settingsManager.getBoolean(ctx.getString(R.string.double_tap_enabled_key), false)
                // useSystemBrightness =
                //    settingsManager.getBoolean(ctx.getString(R.string.use_system_brightness_key), false)
            }
        } catch (e: Exception) {
            logError(e)
        }

        // init clicks
        resize_player?.setOnClickListener {
            nextResize()
        }

        playback_speed_btt?.setOnClickListener {
            showSpeedDialog()
        }

        skip_op?.setOnClickListener {
            skipOp()
        }

        skip_episode?.setOnClickListener {
            player.handleEvent(CSPlayerEvent.NextEpisode)
        }

        lock_player?.setOnClickListener {
            toggleLock()
        }

        exo_rew?.setOnClickListener {
            rewind()
        }

        exo_ffwd?.setOnClickListener {
            fastForward()
        }

        player_holder?.setOnTouchListener { callView, event ->
            return@setOnTouchListener handleMotionEvent(callView, event)
        }

        // init UI
        try {
            updateLockUI()
            updateUIVisibility()
            animateLayoutChanges()
            resetFastForwardText()
            resetRewindText()
        } catch (e: Exception) {
            logError(e)
        }
    }
}