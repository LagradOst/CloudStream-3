package com.ArjixWasTaken.cloudstream3.utils

import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.*
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import com.bumptech.glide.Glide
import com.ArjixWasTaken.cloudstream3.MainActivity
import com.ArjixWasTaken.cloudstream3.R
import com.ArjixWasTaken.cloudstream3.mvvm.logError
import com.ArjixWasTaken.cloudstream3.mvvm.normalSafeApiCall
import com.ArjixWasTaken.cloudstream3.services.VideoDownloadService
import com.ArjixWasTaken.cloudstream3.utils.Coroutines.main
import com.ArjixWasTaken.cloudstream3.utils.DataStore.getKey
import com.ArjixWasTaken.cloudstream3.utils.DataStore.removeKey
import com.ArjixWasTaken.cloudstream3.utils.DataStore.setKey
import com.ArjixWasTaken.cloudstream3.utils.UIHelper.colorFromAttribute
import com.ArjixWasTaken.cloudstream3.utils.M3u8Helper
import com.ArjixWasTaken.cloudstream3.utils.VideoDownloadManager.getExistingDownloadUriOrNullQ
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.*
import java.lang.Thread.sleep
import java.net.URL
import java.net.URLConnection
import java.util.*
import kotlin.collections.ArrayList

const val DOWNLOAD_CHANNEL_ID = "cloudstream3.general"
const val DOWNLOAD_CHANNEL_NAME = "Downloads"
const val DOWNLOAD_CHANNEL_DESCRIPT = "The download notification channel"

object VideoDownloadManager {
    var maxConcurrentDownloads = 3
    private var currentDownloads = mutableListOf<Int>()

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"

    @DrawableRes
    const val imgDone = R.drawable.rddone

    @DrawableRes
    const val imgDownloading = R.drawable.rdload

    @DrawableRes
    const val imgPaused = R.drawable.rdpause

    @DrawableRes
    const val imgStopped = R.drawable.rderror

    @DrawableRes
    const val imgError = R.drawable.rderror

    @DrawableRes
    const val pressToPauseIcon = R.drawable.ic_baseline_pause_24

    @DrawableRes
    const val pressToResumeIcon = R.drawable.ic_baseline_play_arrow_24

    @DrawableRes
    const val pressToStopIcon = R.drawable.exo_icon_stop

    enum class DownloadType {
        IsPaused,
        IsDownloading,
        IsDone,
        IsFailed,
        IsStopped,
    }

    enum class DownloadActionType {
        Pause,
        Resume,
        Stop,
    }

    interface IDownloadableMinimum {
        val url: String
        val referer: String
    }

    fun VideoDownloadManager.IDownloadableMinimum.getId(): Int {
        return url.hashCode()
    }

    data class DownloadEpisodeMetadata(
        val id: Int,
        val mainName: String,
        val sourceApiName: String?,
        val poster: String?,
        val name: String?,
        val season: Int?,
        val episode: Int?
    )

    data class DownloadItem(
        val source: String?,
        val folder: String?,
        val ep: DownloadEpisodeMetadata,
        val links: List<ExtractorLink>
    )

    data class DownloadResumePackage(
        val item: DownloadItem,
        val linkIndex: Int?,
    )

    data class DownloadedFileInfo(
        val totalBytes: Long,
        val relativePath: String,
        val displayName: String,
    )

    data class DownloadedFileInfoResult(
        val fileLength: Long,
        val totalBytes: Long,
        val path: Uri,
    )

    data class DownloadQueueResumePackage(
        val index: Int,
        val pkg: DownloadResumePackage,
    )

    private const val SUCCESS_DOWNLOAD_DONE = 1
    private const val SUCCESS_STOPPED = 2
    private const val ERROR_DELETING_FILE = 3 // will not download the next one, but is still classified as an error
    private const val ERROR_CREATE_FILE = -2
    private const val ERROR_OPEN_FILE = -3
    private const val ERROR_TOO_SMALL_CONNECTION = -4
    private const val ERROR_WRONG_CONTENT = -5
    private const val ERROR_CONNECTION_ERROR = -6
    private const val ERROR_MEDIA_STORE_URI_CANT_BE_CREATED = -7
    private const val ERROR_CONTENT_RESOLVER_CANT_OPEN_STREAM = -8
    private const val ERROR_CONTENT_RESOLVER_NOT_FOUND = -9

    const val KEY_RESUME_PACKAGES = "download_resume"
    const val KEY_DOWNLOAD_INFO = "download_info"
    const val KEY_RESUME_QUEUE_PACKAGES = "download_q_resume"

    val downloadStatus = HashMap<Int, DownloadType>()
    val downloadStatusEvent = Event<Pair<Int, DownloadType>>()
    val downloadDeleteEvent = Event<Int>()
    val downloadEvent = Event<Pair<Int, DownloadActionType>>()
    val downloadProgressEvent = Event<Triple<Int, Long, Long>>()
    val downloadQueue = LinkedList<DownloadResumePackage>()

    private var hasCreatedNotChanel = false
    private fun Context.createNotificationChannel() {
        hasCreatedNotChanel = true
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = DOWNLOAD_CHANNEL_NAME //getString(R.string.channel_name)
            val descriptionText = DOWNLOAD_CHANNEL_DESCRIPT//getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(DOWNLOAD_CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                this.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /** Will return IsDone if not found or error */
    fun getDownloadState(id: Int): DownloadType {
        return try {
            downloadStatus[id] ?: DownloadType.IsDone
        } catch (e: Exception) {
            e.printStackTrace()
            DownloadType.IsDone
        }
    }

    private val cachedBitmaps = hashMapOf<String, Bitmap>()
    private fun Context.getImageBitmapFromUrl(url: String): Bitmap? {
        try {
            if (cachedBitmaps.containsKey(url)) {
                return cachedBitmaps[url]
            }

            val bitmap = Glide.with(this)
                .asBitmap()
                .load(url).into(720, 720)
                .get()
            if (bitmap != null) {
                cachedBitmaps[url] = bitmap
            }
            return null
        } catch (e: Exception) {
            return null
        }
    }

    private fun createNotification(
        context: Context,
        source: String?,
        linkName: String?,
        ep: DownloadEpisodeMetadata,
        state: DownloadType,
        progress: Long,
        total: Long,
    ) {
        main { // DON'T WANT TO SLOW IT DOWN
            val builder = NotificationCompat.Builder(context, DOWNLOAD_CHANNEL_ID)
                .setAutoCancel(true)
                .setColorized(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setColor(context.colorFromAttribute(R.attr.colorPrimary))
                .setContentTitle(ep.mainName)
                .setSmallIcon(
                    when (state) {
                        DownloadType.IsDone -> imgDone
                        DownloadType.IsDownloading -> imgDownloading
                        DownloadType.IsPaused -> imgPaused
                        DownloadType.IsFailed -> imgError
                        DownloadType.IsStopped -> imgStopped
                    }
                )

            if (ep.sourceApiName != null) {
                builder.setSubText(ep.sourceApiName)
            }

            if (source != null) {
                val intent = Intent(context, MainActivity::class.java).apply {
                    data = source.toUri()
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                val pendingIntent: PendingIntent = PendingIntent.getActivity(context, 0, intent, 0)
                builder.setContentIntent(pendingIntent)
            }

            if (state == DownloadType.IsDownloading || state == DownloadType.IsPaused) {
                builder.setProgress(total.toInt(), progress.toInt(), false)
            }

            val rowTwoExtra = if (ep.name != null) " - ${ep.name}\n" else ""
            val rowTwo = if (ep.season != null && ep.episode != null) {
                "S${ep.season}:E${ep.episode}" + rowTwoExtra
            } else if (ep.episode != null) {
                "Episode ${ep.episode}" + rowTwoExtra
            } else {
                (ep.name ?: "") + ""
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (ep.poster != null) {
                    val poster = withContext(Dispatchers.IO) {
                        context.getImageBitmapFromUrl(ep.poster)
                    }
                    if (poster != null)
                        builder.setLargeIcon(poster)
                }

                val progressPercentage = progress * 100 / total
                val progressMbString = "%.1f".format(progress / 1000000f)
                val totalMbString = "%.1f".format(total / 1000000f)

                val bigText =
                    if (state == DownloadType.IsDownloading || state == DownloadType.IsPaused) {
                        (if (linkName == null) "" else "$linkName\n") + "$rowTwo\n$progressPercentage % ($progressMbString MB/$totalMbString MB)"
                    } else if (state == DownloadType.IsFailed) {
                        "Download Failed - $rowTwo"
                    } else if (state == DownloadType.IsDone) {
                        "Download Done - $rowTwo"
                    } else {
                        "Download Canceled - $rowTwo"
                    }

                val bodyStyle = NotificationCompat.BigTextStyle()
                bodyStyle.bigText(bigText)
                builder.setStyle(bodyStyle)
            } else {
                val txt = if (state == DownloadType.IsDownloading || state == DownloadType.IsPaused) {
                    rowTwo
                } else if (state == DownloadType.IsFailed) {
                    "Download Failed - $rowTwo"
                } else if (state == DownloadType.IsDone) {
                    "Download Done - $rowTwo"
                } else {
                    "Download Canceled - $rowTwo"
                }

                builder.setContentText(txt)
            }

            if ((state == DownloadType.IsDownloading || state == DownloadType.IsPaused) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val actionTypes: MutableList<DownloadActionType> = ArrayList()
                // INIT
                if (state == DownloadType.IsDownloading) {
                    actionTypes.add(DownloadActionType.Pause)
                    actionTypes.add(DownloadActionType.Stop)
                }

                if (state == DownloadType.IsPaused) {
                    actionTypes.add(DownloadActionType.Resume)
                    actionTypes.add(DownloadActionType.Stop)
                }

                // ADD ACTIONS
                for ((index, i) in actionTypes.withIndex()) {
                    val actionResultIntent = Intent(context, VideoDownloadService::class.java)

                    actionResultIntent.putExtra(
                        "type", when (i) {
                            DownloadActionType.Resume -> "resume"
                            DownloadActionType.Pause -> "pause"
                            DownloadActionType.Stop -> "stop"
                        }
                    )

                    actionResultIntent.putExtra("id", ep.id)

                    val pending: PendingIntent = PendingIntent.getService(
                        // BECAUSE episodes lying near will have the same id +1, index will give the same requested as the previous episode, *100000 fixes this
                        context, (4337 + index * 100000 + ep.id),
                        actionResultIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT
                    )

                    builder.addAction(
                        NotificationCompat.Action(
                            when (i) {
                                DownloadActionType.Resume -> pressToResumeIcon
                                DownloadActionType.Pause -> pressToPauseIcon
                                DownloadActionType.Stop -> pressToStopIcon
                            }, when (i) {
                                DownloadActionType.Resume -> "Resume"
                                DownloadActionType.Pause -> "Pause"
                                DownloadActionType.Stop -> "Cancel"
                            }, pending
                        )
                    )
                }
            }

            if (!hasCreatedNotChanel) {
                context.createNotificationChannel()
            }

            with(NotificationManagerCompat.from(context)) {
                // notificationId is a unique int for each notification that you must define
                notify(ep.id, builder.build())
            }
        }
    }

    private const val reservedChars = "|\\?*<\":>+[]/\'"
    fun sanitizeFilename(name: String): String {
        var tempName = name
        for (c in reservedChars) {
            tempName = tempName.replace(c, ' ')
        }
        return tempName.replace("  ", " ").trim(' ')
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun ContentResolver.getExistingFolderStartName(relativePath: String): List<Pair<String, Uri>>? {
        try {
            val projection = arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,   // unused (for verification use only)
                //MediaStore.MediaColumns.RELATIVE_PATH,  // unused (for verification use only)
            )

            val selection =
                "${MediaStore.MediaColumns.RELATIVE_PATH}='$relativePath'"

            val result = this.query(
                MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                projection, selection, null, null
            )
            val list = ArrayList<Pair<String, Uri>>()

            result.use { c ->
                if (c != null && c.count >= 1) {
                    c.moveToFirst()
                    while (true) {
                        val id = c.getLong(c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                        val name = c.getString(c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME))
                        val uri = ContentUris.withAppendedId(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI, id
                        )
                        list.add(Pair(name, uri))
                        if (c.isLast) {
                            break
                        }
                        c.moveToNext()
                    }

                    /*
                    val cDisplayName = c.getString(c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME))
                    val cRelativePath = c.getString(c.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH))*/

                }
            }
            return list
        } catch (e: Exception) {
            return null
        }
    }

    fun getFolder(context: Context, relativePath: String): List<Pair<String, Uri>>? {
        if (isScopedStorage()) {
            return context.contentResolver?.getExistingFolderStartName(relativePath)
        } else {
            val normalPath =
                "${Environment.getExternalStorageDirectory()}${File.separatorChar}${relativePath}".replace(
                    '/',
                    File.separatorChar
                )
            val folder = File(normalPath)
            if (folder.isDirectory) {
                return folder.listFiles().map { Pair(it.name, it.toUri()) }
            }
            return null
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun ContentResolver.getExistingDownloadUriOrNullQ(relativePath: String, displayName: String): Uri? {
        try {
            val projection = arrayOf(
                MediaStore.MediaColumns._ID,
                //MediaStore.MediaColumns.DISPLAY_NAME,   // unused (for verification use only)
                //MediaStore.MediaColumns.RELATIVE_PATH,  // unused (for verification use only)
            )

            val selection =
                "${MediaStore.MediaColumns.RELATIVE_PATH}='$relativePath' AND " + "${MediaStore.MediaColumns.DISPLAY_NAME}='$displayName'"

            val result = this.query(
                MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                projection, selection, null, null
            )

            result.use { c ->
                if (c != null && c.count >= 1) {
                    c.moveToFirst().let {
                        val id = c.getLong(c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                        /*
                        val cDisplayName = c.getString(c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME))
                        val cRelativePath = c.getString(c.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH))*/

                        return ContentUris.withAppendedId(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI, id
                        )
                    }
                }
            }
            return null
        } catch (e: Exception) {
            return null
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    fun ContentResolver.getFileLength(fileUri: Uri): Long? {
        return try {
            this.openFileDescriptor(fileUri, "r")
                .use { it?.statSize ?: 0 }
        } catch (e: Exception) {
            null
        }
    }

    private fun isScopedStorage(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    }

    data class CreateNotificationMetadata(
        val type: DownloadType,
        val bytesDownloaded: Long,
        val bytesTotal: Long,
    )

    fun downloadThing(
        context: Context,
        link: IDownloadableMinimum,
        name: String,
        folder: String?,
        extension: String,
        tryResume: Boolean,
        parentId: Int?,
        createNotificationCallback: (CreateNotificationMetadata) -> Unit
    ): Int {
        val relativePath = (Environment.DIRECTORY_DOWNLOADS + '/' + folder + '/').replace('/', File.separatorChar)
        val displayName = "$name.$extension"

        val normalPath = "${Environment.getExternalStorageDirectory()}${File.separatorChar}$relativePath$displayName"
        var resume = tryResume

        val fileStream: OutputStream
        val fileLength: Long

        fun deleteFile(): Int {
            if (isScopedStorage()) {
                val lastContent = context.contentResolver.getExistingDownloadUriOrNullQ(relativePath, displayName)
                if (lastContent != null) {
                    context.contentResolver.delete(lastContent, null, null)
                }
            } else {
                if (!File(normalPath).delete()) return ERROR_DELETING_FILE
            }
            parentId?.let {
                downloadDeleteEvent.invoke(parentId)
            }
            return SUCCESS_STOPPED
        }

        if (isScopedStorage()) {
            val cr = context.contentResolver ?: return ERROR_CONTENT_RESOLVER_NOT_FOUND

            val currentExistingFile =
                cr.getExistingDownloadUriOrNullQ(relativePath, displayName) // CURRENT FILE WITH THE SAME PATH

            fileLength =
                if (currentExistingFile == null || !resume) 0 else (cr.getFileLength(currentExistingFile)
                    ?: 0)// IF NOT RESUME THEN 0, OTHERWISE THE CURRENT FILE SIZE

            if (!resume && currentExistingFile != null) { // DELETE FILE IF FILE EXITS AND NOT RESUME
                val rowsDeleted = context.contentResolver.delete(currentExistingFile, null, null)
                if (rowsDeleted < 1) {
                    println("ERROR DELETING FILE!!!")
                }
            }

            var appendFile = false
            val newFileUri = if (resume && currentExistingFile != null) {
                appendFile = true
                currentExistingFile
            } else {
                val contentUri =
                    MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) // USE INSTEAD OF MediaStore.Downloads.EXTERNAL_CONTENT_URI
                //val currentMimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                val currentMimeType = when (extension) {
                    "vtt" -> "text/vtt"
                    "mp4" -> "video/mp4"
                    "srt" -> "text/plain"
                    else -> null
                }
                val newFile = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(MediaStore.MediaColumns.TITLE, name)
                    if (currentMimeType != null)
                        put(MediaStore.MediaColumns.MIME_TYPE, currentMimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                }

                cr.insert(
                    contentUri,
                    newFile
                ) ?: return ERROR_MEDIA_STORE_URI_CANT_BE_CREATED
            }

            fileStream = cr.openOutputStream(newFileUri, "w" + (if (appendFile) "a" else ""))
                ?: return ERROR_CONTENT_RESOLVER_CANT_OPEN_STREAM
        } else {
            // NORMAL NON SCOPED STORAGE FILE CREATION
            val rFile = File(normalPath)
            if (!rFile.exists()) {
                fileLength = 0
                rFile.parentFile?.mkdirs()
                if (!rFile.createNewFile()) return ERROR_CREATE_FILE
            } else {
                if (resume) {
                    fileLength = rFile.length()
                } else {
                    fileLength = 0
                    rFile.parentFile?.mkdirs()
                    if (!rFile.delete()) return ERROR_DELETING_FILE
                    if (!rFile.createNewFile()) return ERROR_CREATE_FILE
                }
            }
            fileStream = FileOutputStream(rFile, false)
        }
        if (fileLength == 0L) resume = false

        // CONNECT
        val connection: URLConnection = URL(link.url.replace(" ", "%20")).openConnection() // IDK OLD PHONES BE WACK

        // SET CONNECTION SETTINGS
        connection.connectTimeout = 10000
        connection.setRequestProperty("Accept-Encoding", "identity")
        connection.setRequestProperty("User-Agent", USER_AGENT)
        if (link.referer.isNotEmpty()) connection.setRequestProperty("Referer", link.referer)

        // extra stuff
        connection.setRequestProperty(
            "sec-ch-ua",
            "\"Chromium\";v=\"91\", \" Not;A Brand\";v=\"99\""
        )
        connection.setRequestProperty("sec-ch-ua-mobile", "?0")
        //   dataSource.setRequestProperty("Sec-Fetch-Site", "none") //same-site
        connection.setRequestProperty("Sec-Fetch-User", "?1")
        connection.setRequestProperty("Sec-Fetch-Mode", "navigate")
        connection.setRequestProperty("Sec-Fetch-Dest", "document")

        if (resume)
            connection.setRequestProperty("Range", "bytes=${fileLength}-")
        val resumeLength = (if (resume) fileLength else 0)

        // ON CONNECTION
        connection.connect()

        val contentLength = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) { // fuck android
            connection.contentLengthLong
        } else {
            connection.getHeaderField("content-length").toLongOrNull() ?: connection.contentLength.toLong()
        }
        val bytesTotal = contentLength + resumeLength

        if (extension == "mp4" && bytesTotal < 5000000) return ERROR_TOO_SMALL_CONNECTION // DATA IS LESS THAN 5MB, SOMETHING IS WRONG

        parentId?.let {
            context.setKey(KEY_DOWNLOAD_INFO, it.toString(), DownloadedFileInfo(bytesTotal, relativePath, displayName))
        }

        // Could use connection.contentType for mime types when creating the file,
        // however file is already created and players don't go of file type

        // https://stackoverflow.com/questions/23714383/what-are-all-the-possible-values-for-http-content-type-header
        // might receive application/octet-stream
        /*if (!connection.contentType.isNullOrEmpty() && !connection.contentType.startsWith("video")) {
            return ERROR_WRONG_CONTENT // CONTENT IS NOT VIDEO, SHOULD NEVER HAPPENED, BUT JUST IN CASE
        }*/

        // READ DATA FROM CONNECTION
        val connectionInputStream: InputStream = BufferedInputStream(connection.inputStream)
        val buffer = ByteArray(1024)
        var count: Int
        var bytesDownloaded = resumeLength

        var isPaused = false
        var isStopped = false
        var isDone = false
        var isFailed = false

        // TO NOT REUSE CODE
        fun updateNotification() {
            val type = when {
                isDone -> DownloadType.IsDone
                isStopped -> DownloadType.IsStopped
                isFailed -> DownloadType.IsFailed
                isPaused -> DownloadType.IsPaused
                else -> DownloadType.IsDownloading
            }

            parentId?.let { id ->
                try {
                    downloadStatus[id] = type
                    downloadStatusEvent.invoke(Pair(id, type))
                    downloadProgressEvent.invoke(Triple(id, bytesDownloaded, bytesTotal))
                } catch (e: Exception) {
                    // IDK MIGHT ERROR
                }
            }

            createNotificationCallback.invoke(CreateNotificationMetadata(type, bytesDownloaded, bytesTotal))
            /*createNotification(
                context,
                source,
                link.name,
                ep,
                type,
                bytesDownloaded,
                bytesTotal
            )*/
        }

        val downloadEventListener = { event: Pair<Int, DownloadActionType> ->
            if (event.first == parentId) {
                when (event.second) {
                    DownloadActionType.Pause -> {
                        isPaused = true; updateNotification()
                    }
                    DownloadActionType.Stop -> {
                        isStopped = true; updateNotification()
                        context.removeKey(KEY_RESUME_PACKAGES, event.first.toString())
                        saveQueue(context)
                    }
                    DownloadActionType.Resume -> {
                        isPaused = false; updateNotification()
                    }
                }
            }
        }

        if (parentId != null)
            downloadEvent += downloadEventListener

        // UPDATE DOWNLOAD NOTIFICATION
        val notificationCoroutine = main {
            while (true) {
                if (!isPaused) {
                    updateNotification()
                }
                for (i in 1..10) {
                    delay(100)
                }
            }
        }

        // THE REAL READ
        try {
            while (true) {
                count = connectionInputStream.read(buffer)
                if (count < 0) break
                bytesDownloaded += count
                // downloadProgressEvent.invoke(Pair(id, bytesDownloaded)) // Updates too much for any UI to keep up with
                while (isPaused) {
                    sleep(100)
                    if (isStopped) {
                        break
                    }
                }
                if (isStopped) {
                    break
                }
                fileStream.write(buffer, 0, count)
            }
        } catch (e: Exception) {
            isFailed = true
            updateNotification()
        }

        // REMOVE AND EXIT ALL
        fileStream.close()
        connectionInputStream.close()
        notificationCoroutine.cancel()

        try {
            if (parentId != null)
                downloadEvent -= downloadEventListener
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            parentId?.let {
                downloadStatus.remove(it)
            }
        } catch (e: Exception) {
            // IDK MIGHT ERROR
        }

        // RETURN MESSAGE
        return when {
            isFailed -> {
                parentId?.let { id -> downloadProgressEvent.invoke(Triple(id, 0, 0)) }
                ERROR_CONNECTION_ERROR
            }
            isStopped -> {
                parentId?.let { id -> downloadProgressEvent.invoke(Triple(id, 0, 0)) }
                deleteFile()
            }
            else -> {
                parentId?.let { id -> downloadProgressEvent.invoke(Triple(id, bytesDownloaded, bytesTotal)) }
                isDone = true
                updateNotification()
                SUCCESS_DOWNLOAD_DONE
            }
        }
    }

    private fun downloadHLS(
        context: Context,
        link: ExtractorLink,
        name: String,
        folder: String?,
        parentId: Int?,
        createNotificationCallback: (CreateNotificationMetadata) -> Unit
    ): Int {
        fun logcatPrint(vararg items: Any?) {
            items.forEach {
                println("[HLS]: $it")
            }
        }

        val m3u8Helper = M3u8Helper()

        val m3u8 = M3u8Helper.M3u8Stream(link.url, when (link.quality) {
            -2 -> 360
            -1 -> 480
            1 -> 720
            2 -> 1080
            else -> null
        }, mapOf("referer" to link.referer))
        val tsIterator = m3u8Helper.hlsYield(listOf(m3u8))
        logcatPrint("initialised the HLS downloader.")

        val relativePath = (Environment.DIRECTORY_DOWNLOADS + '/' + folder + '/').replace('/', File.separatorChar)
        val displayName = "$name.ts"

        val normalPath = "${Environment.getExternalStorageDirectory()}${File.separatorChar}$relativePath$displayName"

        val fileStream: OutputStream
        val fileLength: Long

        fun deleteFile(): Int {
            if (isScopedStorage()) {
                val lastContent = context.contentResolver.getExistingDownloadUriOrNullQ(relativePath, displayName)
                if (lastContent != null) {
                    context.contentResolver.delete(lastContent, null, null)
                }
            } else {
                if (!File(normalPath).delete()) return ERROR_DELETING_FILE
            }
            parentId?.let {
                downloadDeleteEvent.invoke(parentId)
            }
            return SUCCESS_STOPPED
        }

        if (isScopedStorage()) {
            val cr = context.contentResolver ?: return ERROR_CONTENT_RESOLVER_NOT_FOUND

            val currentExistingFile =
                cr.getExistingDownloadUriOrNullQ(relativePath, displayName) // CURRENT FILE WITH THE SAME PATH

            if (currentExistingFile != null) { // DELETE FILE IF FILE EXITS
                val rowsDeleted = context.contentResolver.delete(currentExistingFile, null, null)
                if (rowsDeleted < 1) {
                    println("ERROR DELETING FILE!!!")
                }
            }

            val newFileUri = if (currentExistingFile != null) {
                currentExistingFile
            } else {
                val contentUri =
                    MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) // USE INSTEAD OF MediaStore.Downloads.EXTERNAL_CONTENT_URI
                //val currentMimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                val currentMimeType = "video/mp2t"
                val newFile = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(MediaStore.MediaColumns.TITLE, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, currentMimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                }

                cr.insert(
                    contentUri,
                    newFile
                ) ?: return ERROR_MEDIA_STORE_URI_CANT_BE_CREATED
            }

            fileStream = cr.openOutputStream(newFileUri, "a")
                ?: return ERROR_CONTENT_RESOLVER_CANT_OPEN_STREAM
        } else {
            // NORMAL NON SCOPED STORAGE FILE CREATION
            val rFile = File(normalPath)
            if (!rFile.exists()) {
                rFile.parentFile?.mkdirs()
                if (!rFile.createNewFile()) return ERROR_CREATE_FILE
            } else {
                rFile.parentFile?.mkdirs()
                if (!rFile.delete()) return ERROR_DELETING_FILE
                if (!rFile.createNewFile()) return ERROR_CREATE_FILE
            }
            fileStream = FileOutputStream(rFile, false)
        }
        val firstTs = tsIterator.next()

        var isDone = false
        var isFailed = false
        var bytesDownloaded = firstTs.bytes.size.toLong()
        var tsProgress = 1L
        val totalTs = firstTs.totalTs.toLong()
        /*
            Most of the auto generated m3u8 out there have TS of the same size.
            And only the last TS might have a different size.
            But oh well, in cases of handmade m3u8 streams this will go all over the place ¯\_(ツ)_/¯
            So ya, this calculates an estimate of how many bytes the file is going to be.
            > (bytesDownloaded/tsProgress)*totalTs
         */


        parentId?.let {
            context.setKey(KEY_DOWNLOAD_INFO, it.toString(), DownloadedFileInfo((bytesDownloaded/tsProgress)*totalTs, relativePath, displayName))
        }

        fun updateNotification() {
            val type = when {
                isDone -> DownloadType.IsDone
                isFailed -> DownloadType.IsFailed
                else -> DownloadType.IsDownloading
            }

            parentId?.let { id ->
                try {
                    downloadStatus[id] = type
                    downloadStatusEvent.invoke(Pair(id, type))
                    downloadProgressEvent.invoke(Triple(id, bytesDownloaded, (bytesDownloaded/tsProgress)*totalTs))
                } catch (e: Exception) {
                    // IDK MIGHT ERROR
                }
            }

            createNotificationCallback.invoke(CreateNotificationMetadata(type, bytesDownloaded, (bytesDownloaded/tsProgress)*totalTs))
        }

        if (firstTs.errored) {
            isFailed = true
            fileStream.close()
            deleteFile()
            updateNotification()
            return ERROR_CONNECTION_ERROR
        }

        val notificationCoroutine = main {
            while (true) {
                if (!isDone) {
                    updateNotification()
                }
                for (i in 1..10) {
                    delay(100)
                }
            }
        }

        val downloadEventListener = { event: Pair<Int, DownloadActionType> ->
            if (event.first == parentId) {
                when (event.second) {
                    DownloadActionType.Stop -> {
                        isFailed = true
                    }
                    DownloadActionType.Pause -> {
                        isFailed = true  // Pausing is not supported since well...I need to know the index of the ts it was paused at
                        // it may be possible to store it in a variable, but when the app restarts it will be lost
                    }
                    else -> updateNotification()  // do nothing, since well...I don't support anything else
                }
            }
        }

        fun closeAll() {
            try {
                if (parentId != null)
                    downloadEvent -= downloadEventListener
            } catch (e: Exception) {
                logError(e)
            }
            try {
                parentId?.let {
                    downloadStatus.remove(it)
                }
            } catch (e: Exception) {
                logError(e)
                // IDK MIGHT ERROR
            }
            notificationCoroutine.cancel()
        }

        if (parentId != null)
            downloadEvent += downloadEventListener

        fileStream.write(firstTs.bytes)

        for (ts in tsIterator) {
            if (isFailed) {
                fileStream.close()
                deleteFile()
                updateNotification()
                closeAll()
                return SUCCESS_STOPPED
            }
            if (ts.errored) {
                isFailed = true
                fileStream.close()
                deleteFile()
                updateNotification()

                closeAll()
                return ERROR_CONNECTION_ERROR
            }
            fileStream.write(ts.bytes)
            ++tsProgress
            bytesDownloaded += ts.bytes.size.toLong()
            logcatPrint("Download progress $tsProgress/$totalTs")
        }
        isDone = true
        fileStream.close()
        updateNotification()

        closeAll()
        parentId?.let {
            context.setKey(KEY_DOWNLOAD_INFO, it.toString(), DownloadedFileInfo(bytesDownloaded, relativePath, displayName))
        }

        return SUCCESS_DOWNLOAD_DONE
    }

    private fun downloadSingleEpisode(
        context: Context,
        source: String?,
        folder: String?,
        ep: DownloadEpisodeMetadata,
        link: ExtractorLink,
        tryResume: Boolean = false,
    ): Int {
        val name = sanitizeFilename(ep.name ?: "Episode ${ep.episode}")

        if (link.isM3u8) {
            return downloadHLS(context, link, name, folder, ep.id) { meta ->
                createNotification(
                    context,
                    source,
                    link.name,
                    ep,
                    meta.type,
                    meta.bytesDownloaded,
                    meta.bytesTotal
                )
            }
        }

        return downloadThing(context, link, name, folder, "mp4", tryResume, ep.id) { meta ->
            createNotification(
                context,
                source,
                link.name,
                ep,
                meta.type,
                meta.bytesDownloaded,
                meta.bytesTotal
            )
        }
    }

    private fun downloadCheck(context: Context) {
        if (currentDownloads.size < maxConcurrentDownloads && downloadQueue.size > 0) {
            val pkg = downloadQueue.removeFirst()
            val item = pkg.item
            val id = item.ep.id
            if (currentDownloads.contains(id)) { // IF IT IS ALREADY DOWNLOADING, RESUME IT
                downloadEvent.invoke(Pair(id, DownloadActionType.Resume))
                return
            }

            currentDownloads.add(id)

            main {
                try {
                    for (index in (pkg.linkIndex ?: 0) until item.links.size) {
                        val link = item.links[index]
                        val resume = pkg.linkIndex == index

                        context.setKey(KEY_RESUME_PACKAGES, id.toString(), DownloadResumePackage(item, index))
                        val connectionResult = withContext(Dispatchers.IO) {
                            normalSafeApiCall {
                                downloadSingleEpisode(context, item.source, item.folder, item.ep, link, resume)
                            }
                        }
                        if (connectionResult != null && connectionResult > 0) { // SUCCESS
                            context.removeKey(KEY_RESUME_PACKAGES, id.toString())
                            break
                        }
                    }
                } catch (e: Exception) {
                    logError(e)
                } finally {
                    currentDownloads.remove(id)
                    downloadCheck(context)
                }
            }
        }
    }

    fun getDownloadFileInfoAndUpdateSettings(context: Context, id: Int): DownloadedFileInfoResult? {
        val res = getDownloadFileInfo(context, id)
        if (res == null) context.removeKey(KEY_DOWNLOAD_INFO, id.toString())
        return res
    }

    private fun getDownloadFileInfo(context: Context, id: Int): DownloadedFileInfoResult? {
        val info = context.getKey<DownloadedFileInfo>(KEY_DOWNLOAD_INFO, id.toString()) ?: return null

        if (isScopedStorage()) {
            val cr = context.contentResolver ?: return null
            val fileUri =
                cr.getExistingDownloadUriOrNullQ(info.relativePath, info.displayName) ?: return null
            val fileLength = cr.getFileLength(fileUri) ?: return null
            if (fileLength == 0L) return null
            return DownloadedFileInfoResult(fileLength, info.totalBytes, fileUri)
        } else {
            val normalPath =
                "${Environment.getExternalStorageDirectory()}${File.separatorChar}${info.relativePath}${info.displayName}".replace(
                    '/',
                    File.separatorChar
                )
            val dFile = File(normalPath)
            if (!dFile.exists()) return null
            return DownloadedFileInfoResult(dFile.length(), info.totalBytes, dFile.toUri())
        }
    }

    fun deleteFileAndUpdateSettings(context: Context, id: Int): Boolean {
        val success = deleteFile(context, id)
        if (success) context.removeKey(KEY_DOWNLOAD_INFO, id.toString())
        return success
    }

    private fun deleteFile(context: Context, id: Int): Boolean {
        val info = context.getKey<DownloadedFileInfo>(KEY_DOWNLOAD_INFO, id.toString()) ?: return false
        downloadEvent.invoke(Pair(id, DownloadActionType.Stop))
        downloadProgressEvent.invoke(Triple(id, 0, 0))
        downloadStatusEvent.invoke(Pair(id, DownloadType.IsStopped))
        downloadDeleteEvent.invoke(id)

        if (isScopedStorage()) {
            val cr = context.contentResolver ?: return false
            val fileUri =
                cr.getExistingDownloadUriOrNullQ(info.relativePath, info.displayName)
                    ?: return true // FILE NOT FOUND, ALREADY DELETED

            return cr.delete(fileUri, null, null) > 0 // IF DELETED ROWS IS OVER 0
        } else {
            val normalPath =
                "${Environment.getExternalStorageDirectory()}${File.separatorChar}${info.relativePath}${info.displayName}".replace(
                    '/',
                    File.separatorChar
                )
            val dFile = File(normalPath)
            if (!dFile.exists()) return true
            return dFile.delete()
        }
    }

    fun getDownloadResumePackage(context: Context, id: Int): DownloadResumePackage? {
        return context.getKey(KEY_RESUME_PACKAGES, id.toString())
    }

    fun downloadFromResume(context: Context, pkg: DownloadResumePackage, setKey: Boolean = true) {
        if (!currentDownloads.any { it == pkg.item.ep.id }) {
            if (currentDownloads.size == maxConcurrentDownloads) {
                main {
                    Toast.makeText(
                        context,
                        "${pkg.item.ep.mainName}${pkg.item.ep.episode?.let { " Episode $it " } ?: " "}queued",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            downloadQueue.addLast(pkg)
            downloadCheck(context)
            if (setKey) saveQueue(context)
        } else {
            downloadEvent.invoke(
                Pair(pkg.item.ep.id, DownloadActionType.Resume)
            )
        }
    }

    private fun saveQueue(context: Context) {
        val dQueue =
            downloadQueue.toList().mapIndexed { index, any -> DownloadQueueResumePackage(index, any) }
                .toTypedArray()
        context.setKey(KEY_RESUME_QUEUE_PACKAGES, dQueue)
    }

    fun isMyServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager?
        for (service in manager!!.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }

    fun downloadEpisode(
        context: Context,
        source: String?,
        folder: String?,
        ep: DownloadEpisodeMetadata,
        links: List<ExtractorLink>
    ) {
        if (links.isNotEmpty()) {
            downloadFromResume(context, DownloadResumePackage(DownloadItem(source, folder, ep, links), null))
        }
    }
}