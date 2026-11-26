package io.writeopia.claudecode.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.writeopia.claudecode.ClaudeCodeBridge
import io.writeopia.claudecode.ClaudeCodeBridge.AiMode
import io.writeopia.resources.WrStrings
import io.writeopia.theme.WriteopiaTheme

/**
 * The banner of AI mode: tells that Claude Code is at work and lets the person stop it. Shows
 * nothing while AI mode is off.
 */
@Composable
fun ClaudeCodeAiModeBanner(modifier: Modifier = Modifier) {
    val mode by ClaudeCodeBridge.aiMode.collectAsState()

    AnimatedVisibility(
        visible = mode.isOn,
        enter = fadeIn() + slideInVertically { height -> -height },
        exit = fadeOut() + slideOutVertically { height -> -height },
        modifier = modifier,
    ) {
        val shape = RoundedCornerShape(24.dp)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(WriteopiaTheme.colorScheme.cardBg, shape)
                .border(1.dp, MaterialTheme.colorScheme.primary, shape)
                .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                .semantics { contentDescription = "Claude Code AI mode" }
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                if (mode == AiMode.WRITING) WrStrings.claudeCodeWriting() else WrStrings.claudeCodeWorking(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(onClick = ClaudeCodeBridge::stop) {
                Text(WrStrings.claudeCodeStop())
            }
        }
    }
}

/**
 * While AI mode is on, the content takes no clicks, drags nor typing (the focus is taken away
 * from its text fields); it still scrolls, so the person can follow the writing.
 */
@Composable
fun Modifier.blockedInAiMode(): Modifier {
    val mode by ClaudeCodeBridge.aiMode.collectAsState()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(mode.isOn) {
        if (mode.isOn) focusManager.clearFocus(force = true)
    }

    if (!mode.isOn) return this

    return this.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                // The parent sees the events first: consumed here, no child acts on them.
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type != PointerEventType.Scroll) {
                    event.changes.forEach { change -> change.consume() }
                }
            }
        }
    }
}
