package com.pocketweibo.ui.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import com.pocketweibo.R

fun Context.copyPlainToClipboard(
    label: String,
    text: String,
    toast: String? = null
) {
    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(this, toast ?: getString(R.string.toast_clipboard_copied), Toast.LENGTH_SHORT).show()
}
