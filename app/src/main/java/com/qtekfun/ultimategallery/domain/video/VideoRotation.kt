package com.qtekfun.ultimategallery.domain.video

import android.net.Uri
import com.qtekfun.ultimategallery.data.prefs.SaveBehavior
import com.qtekfun.ultimategallery.domain.MediaItem

/** Outcome of rotating a video. */
sealed interface VideoRotateResult {
    /** The rotation was stored. [uri] is the rotated video: the original after an overwrite, a new item after a copy. */
    data class Rotated(val uri: Uri, val overwritten: Boolean) : VideoRotateResult

    /** The user refused the system permission dialog. */
    data object Denied : VideoRotateResult

    /** The container cannot be rotated losslessly (only MP4/MOV/3GP-style files keep a rotation flag). */
    data object Unsupported : VideoRotateResult

    data class Failed(val message: String) : VideoRotateResult
}

/**
 * Fixes videos that were saved turned the wrong way without re-encoding them: the rotation flag in
 * the MP4 header (the `tkhd` matrix of the video track) is rewritten, so the change is instant and
 * lossless, and every player that honors the flag shows the video upright.
 */
interface VideoRotation {
    /** The rotation flag now stored in the file, in degrees clockwise (0, 90, 180 or 270), or null when unsupported. */
    suspend fun currentRotation(item: MediaItem): Int?

    /**
     * Adds [quarterTurnsClockwise] quarter turns (may be negative or larger than 3) to the stored rotation.
     * [behavior] decides between overwriting the original (needs the system write consent) and saving a
     * rotated copy next to it; [SaveBehavior.ASK] is treated as a copy (the screen asks beforehand).
     */
    suspend fun rotate(item: MediaItem, quarterTurnsClockwise: Int, behavior: SaveBehavior): VideoRotateResult
}
