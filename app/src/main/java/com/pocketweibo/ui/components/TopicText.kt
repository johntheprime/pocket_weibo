package com.pocketweibo.ui.components

import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import com.pocketweibo.ui.theme.WeiboOrange
import com.pocketweibo.ui.util.extractTopicLinks

private const val TOPIC_ANNOTATION = "topic"

/** Post body text whose hashtags open the matching topic detail page. */
@Composable
fun TopicText(
    text: String,
    onTopicClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip
) {
    val annotated = remember(text) {
        buildAnnotatedString {
            append(text)
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
    ClickableText(
        text = annotated,
        modifier = modifier,
        style = style,
        maxLines = maxLines,
        overflow = overflow,
        onClick = { offset ->
            annotated.getStringAnnotations(TOPIC_ANNOTATION, offset, offset)
                .firstOrNull()
                ?.let { onTopicClick(it.item) }
        }
    )
}
