package com.pocketweibo.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pocketweibo.R
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketweibo.ui.theme.GrayDark

/** Full post/detail body: drag handles to select, then use the system copy action. */
@Composable
fun SelectablePostBody(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier
) {
    SelectionContainer {
        BasicText(
            text = AnnotatedString(text),
            style = style,
            modifier = modifier
        )
    }
}

/** Shown after long-press on a compact post row (feed, discover): select portion of the full text. */
@Composable
fun SelectableCopyDialog(
    body: String,
    onDismiss: () -> Unit,
    title: String? = null
) {
    val resolvedTitle = title ?: stringResource(R.string.select_copy_title)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = resolvedTitle, fontSize = 16.sp) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                SelectionContainer {
                    BasicText(
                        text = AnnotatedString(body),
                        style = TextStyle(
                            color = GrayDark,
                            fontSize = 16.sp,
                            lineHeight = 24.sp
                        )
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_done))
            }
        }
    )
}
