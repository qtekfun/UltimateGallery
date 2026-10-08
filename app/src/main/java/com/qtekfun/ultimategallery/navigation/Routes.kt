package com.qtekfun.ultimategallery.navigation

import android.net.Uri

/** Navigation destinations. Arguments stay small: bulk data travels through `BatchSelection`. */
object Routes {
    const val HOME = "home"
    const val PICKER = "picker"
    const val FOLDER = "folder/{bucketId}?name={name}&pick={pick}"
    const val WATERMARK = "watermark"
    const val VIEWER = "viewer/{bucketId}/{mediaId}"
    const val EXPORT = "export/{jobId}"
    const val SETTINGS = "settings"
    const val SETTINGS_FOLDERS = "settings/folders"
    const val SETTINGS_APP_FOLDERS = "settings/app-folders"

    fun viewer(bucketId: Long, mediaId: Long) = "viewer/$bucketId/$mediaId"

    fun export(jobId: String) = "export/$jobId"

    fun folder(bucketId: Long, name: String, pick: Boolean = false) = "folder/$bucketId?name=${Uri.encode(name)}&pick=$pick"
}
