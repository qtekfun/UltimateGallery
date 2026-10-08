package com.qtekfun.ultimategallery.navigation

import android.net.Uri

/** Navigation destinations. Arguments stay small: bulk data travels through `BatchSelection`. */
object Routes {
    const val HOME = "home"
    const val PICKER = "picker"
    const val FOLDER = "folder/{bucketId}?name={name}&pick={pick}"
    const val WATERMARK = "watermark"
    const val SETTINGS = "settings"

    fun folder(bucketId: Long, name: String, pick: Boolean = false) = "folder/$bucketId?name=${Uri.encode(name)}&pick=$pick"
}
