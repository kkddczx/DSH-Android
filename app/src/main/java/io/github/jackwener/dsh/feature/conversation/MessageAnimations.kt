package io.github.jackwener.dsh.feature.conversation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import io.github.jackwener.dsh.core.designsystem.DshMarkdown

/**
 * 消息进场动画：新消息第一次出现时，从 94% 缩放、下方 10dp 的位置弹簧式弹入，同时 180ms 淡入。
 * 只在消息首次进入列表时播放，调用方用 remember(turn.key) 去重（见 ChatStream 的 freshKeys）。
 */
@Composable
fun MessageEntrance(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val alpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "entranceAlpha",
    )
    val scale by animateFloatAsState(
        targetValue = if (entered) 1f else 0.94f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "entranceScale",
    )
    val lift by animateDpAsState(
        targetValue = if (entered) 0.dp else 10.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "entranceLift",
    )
    Box(modifier.graphicsLayer {
        this.alpha = alpha
        scaleX = scale
        scaleY = scale
        translationY = lift.toPx()
    }) { content() }
}

/** 超过这个字数的回复直接整段渲染，避免逐帧重排长文本。 */
private const val TYPEWRITER_MAX_CHARS = 4000

/** 每 4 个字提交一次重排：动画逐帧推进，但 markdown 解析只在跨桶时发生。 */
private const val TYPEWRITER_BUCKET = 4

/**
 * 流式回复的打字机渲染：字数增长时，可见字数用 140ms 线性动画追上去，
 * 让回复看起来是一个字一个字敲出来的；回复结束（running=false）时立即显示全文。
 * 长文本（> TYPEWRITER_MAX_CHARS）不启用。
 */
@Composable
fun StreamingReplyText(text: String, running: Boolean, modifier: Modifier = Modifier) {
    if (!running || text.length > TYPEWRITER_MAX_CHARS) {
        DshMarkdown(text, modifier)
        return
    }
    val target = text.length
    val revealed by animateIntAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 140, easing = LinearEasing),
        label = "replyReveal",
    )
    val bucketed = (revealed + TYPEWRITER_BUCKET - 1) / TYPEWRITER_BUCKET * TYPEWRITER_BUCKET
    var cut = bucketed.coerceIn(0, target)
    // 不要切开代理对（emoji），否则会闪过一个乱码方块。
    if (cut in 1 until target && text[cut - 1].isHighSurrogate()) cut -= 1
    DshMarkdown(text.substring(0, cut), modifier)
}
