package com.pocketweibo.ui.util

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.pocketweibo.R

fun Context.identityDisplayName(name: String?): String =
    if (name.isNullOrBlank()) getString(R.string.deleted_identity_label) else name

@Composable
fun identityDisplayName(name: String?): String {
    if (name.isNullOrBlank()) return stringResource(R.string.deleted_identity_label)
    return name
}
