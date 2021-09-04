package com.ArjixWasTaken.cloudstream3.ui.download

import android.app.Activity
import android.content.DialogInterface
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import com.ArjixWasTaken.cloudstream3.MainActivity
import com.ArjixWasTaken.cloudstream3.R
import com.ArjixWasTaken.cloudstream3.ui.player.PlayerFragment
import com.ArjixWasTaken.cloudstream3.ui.player.UriData
import com.ArjixWasTaken.cloudstream3.utils.AppUtils.getNameFull
import com.ArjixWasTaken.cloudstream3.utils.DataStore.getKey
import com.ArjixWasTaken.cloudstream3.utils.DataStoreHelper.getViewPos
import com.ArjixWasTaken.cloudstream3.utils.VideoDownloadHelper
import com.ArjixWasTaken.cloudstream3.utils.VideoDownloadManager

object DownloadButtonSetup {
    fun handleDownloadClick(activity: Activity?, headerName: String?, click: DownloadClickEvent) {
        val id = click.data.id
        if (click.data !is VideoDownloadHelper.DownloadEpisodeCached) return
        when (click.action) {
            DOWNLOAD_ACTION_DELETE_FILE -> {
                activity?.let { ctx ->
                    val builder: AlertDialog.Builder = AlertDialog.Builder(ctx)
                    val dialogClickListener =
                        DialogInterface.OnClickListener { _, which ->
                            when (which) {
                                DialogInterface.BUTTON_POSITIVE -> {
                                    VideoDownloadManager.deleteFileAndUpdateSettings(ctx, id)
                                }
                                DialogInterface.BUTTON_NEGATIVE -> {
                                }
                            }
                        }

                    builder.setTitle(R.string.delete_file)
                        .setMessage(
                            ctx.getString(R.string.delete_message).format(ctx.getNameFull(
                                click.data.name,
                                click.data.episode,
                                click.data.season
                            ))
                        )
                        .setPositiveButton(R.string.delete, dialogClickListener)
                        .setNegativeButton(R.string.cancel, dialogClickListener)
                        .show()
                }
            }
            DOWNLOAD_ACTION_PAUSE_DOWNLOAD -> {
                VideoDownloadManager.downloadEvent.invoke(
                    Pair(click.data.id, VideoDownloadManager.DownloadActionType.Pause)
                )
            }
            DOWNLOAD_ACTION_RESUME_DOWNLOAD -> {
                activity?.let { ctx ->
                    val pkg = VideoDownloadManager.getDownloadResumePackage(ctx, id)
                    if (pkg != null) {
                        VideoDownloadManager.downloadFromResume(ctx, pkg)
                    } else {
                        VideoDownloadManager.downloadEvent.invoke(
                            Pair(click.data.id, VideoDownloadManager.DownloadActionType.Resume)
                        )
                    }
                }
            }
            DOWNLOAD_ACTION_LONG_CLICK -> {
                activity?.let { act ->
                    val length =
                        VideoDownloadManager.getDownloadFileInfoAndUpdateSettings(act, click.data.id)?.fileLength
                            ?: 0
                    if(length > 0) {
                        MainActivity.showToast(act, R.string.delete, Toast.LENGTH_LONG)
                    } else {
                        MainActivity.showToast(act, R.string.download, Toast.LENGTH_LONG)
                    }
                }
            }
            DOWNLOAD_ACTION_PLAY_FILE -> {
                activity?.let { act ->
                    val info =
                        VideoDownloadManager.getDownloadFileInfoAndUpdateSettings(act, click.data.id)
                            ?: return
                    val keyInfo = act.getKey<VideoDownloadManager.DownloadedFileInfo>(
                        VideoDownloadManager.KEY_DOWNLOAD_INFO,
                        click.data.id.toString()
                    ) ?: return
                    (act as FragmentActivity).supportFragmentManager.beginTransaction()
                        .setCustomAnimations(
                            R.anim.enter_anim,
                            R.anim.exit_anim,
                            R.anim.pop_enter,
                            R.anim.pop_exit
                        )
                        .add(
                            R.id.homeRoot,
                            PlayerFragment.newInstance(
                                UriData(
                                    info.path.toString(),
                                    keyInfo.relativePath,
                                    keyInfo.displayName,
                                    click.data.parentId,
                                    click.data.id,
                                    headerName ?: "null",
                                    if (click.data.episode <= 0) null else click.data.episode,
                                    click.data.season
                                ),
                                act.getViewPos(click.data.id)?.position ?: 0
                            )
                        )
                        .commit()
                }
            }
        }
    }
}