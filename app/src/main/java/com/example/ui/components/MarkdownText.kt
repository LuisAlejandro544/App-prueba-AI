package com.example.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

/**
 * Parser liviano para textos con formato:
 * - ***texto*** -> Negrita y Cursiva
 * - **texto** -> Negrita
 * - *texto* -> Cursiva
 * - `codigo` -> Monoespaciado
 */
fun parseMarkdownAnnotations(
    text: String,
    codeBgColor: Color = Color.Unspecified
): AnnotatedString {
    val builder = AnnotatedString.Builder()
    if (text.isEmpty()) return builder.toAnnotatedString()

    // Expresión regular que detecta tokens en orden de precedencia
    val regex = Regex("""(\*\*\*(.+?)\*\*\*|\*\*(.+?)\*\*|\*(.+?)\*|`([^`\n]+)`)""", RegexOption.DOT_MATCHES_ALL)

    var lastIndex = 0
    for (match in regex.findAll(text)) {
        val range = match.range
        if (range.first > lastIndex) {
            builder.append(text.substring(lastIndex, range.first))
        }

        val fullMatch = match.value
        when {
            fullMatch.startsWith("***") && fullMatch.endsWith("***") && fullMatch.length >= 6 -> {
                val inner = fullMatch.substring(3, fullMatch.length - 3)
                builder.pushStyle(
                    SpanStyle(
                        fontWeight = FontWeight.Bold,
                        fontStyle = FontStyle.Italic
                    )
                )
                builder.append(inner)
                builder.pop()
            }
            fullMatch.startsWith("**") && fullMatch.endsWith("**") && fullMatch.length >= 4 -> {
                val inner = fullMatch.substring(2, fullMatch.length - 2)
                builder.pushStyle(
                    SpanStyle(
                        fontWeight = FontWeight.Bold
                    )
                )
                builder.append(inner)
                builder.pop()
            }
            fullMatch.startsWith("*") && fullMatch.endsWith("*") && fullMatch.length >= 2 -> {
                val inner = fullMatch.substring(1, fullMatch.length - 1)
                builder.pushStyle(
                    SpanStyle(
                        fontStyle = FontStyle.Italic
                    )
                )
                builder.append(inner)
                builder.pop()
            }
            fullMatch.startsWith("`") && fullMatch.endsWith("`") && fullMatch.length >= 2 -> {
                val inner = fullMatch.substring(1, fullMatch.length - 1)
                builder.pushStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = codeBgColor
                    )
                )
                builder.append(inner)
                builder.pop()
            }
            else -> {
                builder.append(fullMatch)
            }
        }
        lastIndex = range.last + 1
    }

    if (lastIndex < text.length) {
        builder.append(text.substring(lastIndex))
    }

    return builder.toAnnotatedString()
}

@Composable
fun MarkdownFormattedText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: TextStyle = MaterialTheme.typography.bodyMedium
) {
    val codeBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val annotatedString = remember(text, codeBg) {
        parseMarkdownAnnotations(text, codeBg)
    }

    Text(
        text = annotatedString,
        modifier = modifier,
        color = color,
        style = style
    )
}
