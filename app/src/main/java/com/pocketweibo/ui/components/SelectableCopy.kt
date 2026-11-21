package com.pocketweibo.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.pocketweibo.R
import com.pocketweibo.ui.util.copyPlainToClipboard
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketweibo.ui.theme.GrayDark
import com.pocketweibo.ui.theme.WeiboOrange
import com.pocketweibo.ui.util.extractTopicLinks

private const val TOPIC_ANNOTATION = "topic"

/** Full post/detail body: drag handles to select, then use the system copy action. */
@Composable
fun SelectablePostBody(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    onTopicClick: ((String) -> Unit)? = null
) {
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val annotated = remember(text, onTopicClick) {
        if (onTopicClick == null) {
            AnnotatedString(text)
        } else {
            buildAnnotatedString {
                append(text)
                extractTopicLinks(text).forEach { link ->
                    addStringAnnotation(TOPIC_ANNOTATION, link.key, link.start, link.end)
                    addStyle(SpanStyle(color = WeiboOrange, fontWeight = FontWeight.Bold), link.start, link.end)
                }
            }
        }
    }
    SelectionContainer {
        BasicText(
            text = annotated,
            style = style,
            modifier = modifier.then(
                if (onTopicClick != null) {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures { position ->
                            val result = layoutResult ?: return@detectTapGestures
                            val offset = result.getOffsetForPosition(position)
                            annotated.getStringAnnotations(TOPIC_ANNOTATION, offset, offset)
                                .firstOrNull()
                                ?.let { onTopicClick(it.item) }
                        }
                    }
                } else {
                    Modifier
                }
            ),
            onTextLayout = { layoutResult = it }
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
    val context = LocalContext.current
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
            TextButton(onClick = {
                context.copyPlainToClipboard(
                    label = context.getString(R.string.post_detail_copy_label),
                    text = body,
                    toast = context.getString(R.string.toast_clipboard_copied)
                )
                onDismiss()
            }) {
                Text(stringResource(R.string.select_copy_copy_all))
            }
        }
    )
}
