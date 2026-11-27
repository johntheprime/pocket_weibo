package com.pocketweibo.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.onClick as semanticsOnClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.pocketweibo.ui.theme.WeiboOrange
import com.pocketweibo.ui.theme.postTextStyle
import com.pocketweibo.ui.util.extractTopicLinks
import com.pocketweibo.ui.util.findKeywordRanges

private const val TOPIC_ANNOTATION = "topic"

/**
 * Post body text whose hashtags open the matching topic detail page.
 *
 * [onClick] receives taps that are not on a hashtag, so tapping the body behaves like
 * tapping the rest of a list row. [onLongClick] keeps the row's copy action working even
 * though this text owns the tap gesture.
 */
@Composable
fun TopicText(
    text: String,
    onTopicClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    /** Search terms drawn with a soft highlight behind them. */
    highlightKeywords: List<String> = emptyList(),
    highlightColor: Color = WeiboOrange.copy(alpha = 0.28f)
) {
    val annotated = remember(text, highlightKeywords, highlightColor) {
        buildAnnotatedString {
            append(text)
            findKeywordRanges(text, highlightKeywords).forEach { range ->
                addStyle(
                    style = SpanStyle(background = highlightColor),
                    start = range.first,
                    end = range.last + 1
                )
            }
            extractTopicLinks(text).forEach { link ->
                addStringAnnotation(
                    tag = TOPIC_ANNOTATION,
                    annotation = link.key,
                    start = link.start,
                    end = link.end
                )
                addStyle(
                    style = SpanStyle(color = WeiboOrange, fontWeight = FontWeight.Medium),
                    start = link.start,
                    end = link.end
                )
            }
        }
    }
    var layoutResult by remember(text, style) { mutableStateOf<TextLayoutResult?>(null) }
    val clickModifier = modifier
        .then(
            if (onClick != null) {
                Modifier.semantics {
                    semanticsOnClick {
                        onClick()
                        true
                    }
                }
            } else {
                Modifier
            }
        )
        .pointerInput(annotated, onClick, onLongClick) {
            detectTapGestures(
                onTap = { position ->
                    val result = layoutResult
                    val topic = result?.let {
                        val offset = it.getOffsetForPosition(position)
                        annotated.getStringAnnotations(TOPIC_ANNOTATION, offset, offset).firstOrNull()
                    }
                    if (topic != null) {
                        onTopicClick(topic.item)
                    } else {
                        onClick?.invoke()
                    }
                },
                onLongPress = { onLongClick?.invoke() }
            )
        }
    BasicText(
        text = annotated,
        modifier = clickModifier,
        style = postTextStyle(style),
        maxLines = maxLines,
        overflow = overflow,
        onTextLayout = { layoutResult = it }
    )
}
