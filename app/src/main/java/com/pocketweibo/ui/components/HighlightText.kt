package com.pocketweibo.ui.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import com.pocketweibo.ui.theme.WeiboOrange
import com.pocketweibo.ui.util.findKeywordRanges

/** Plain text with every occurrence of [keywords] drawn on a soft highlight. */
@Composable
fun HighlightText(
    text: String,
    keywords: List<String>,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    highlightColor: Color = WeiboOrange.copy(alpha = 0.28f)
) {
    val annotated = remember(text, keywords, highlightColor) {
        buildAnnotatedString {
            append(text)
            findKeywordRanges(text, keywords).forEach { range ->
                addStyle(
                    style = SpanStyle(background = highlightColor),
                    start = range.first,
                    end = range.last + 1
                )
            }
        }
    }
    BasicText(
        text = annotated,
        modifier = modifier,
        style = style,
        maxLines = maxLines,
        overflow = overflow
    )
}
