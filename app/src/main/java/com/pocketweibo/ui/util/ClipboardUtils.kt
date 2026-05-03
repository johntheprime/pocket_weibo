package com.pocketweibo.ui.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast

fun Context.copyPlainToClipboard(
    label: String,
    text: String,
    toast: String = "已复制到剪贴板"
) {
    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(this, toast, Toast.LENGTH_SHORT).show()
}
