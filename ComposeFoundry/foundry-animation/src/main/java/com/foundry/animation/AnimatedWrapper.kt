package com.foundry.animation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 动画包装 Composable。
 * 根据 FoundryAnimationSpec.type 将子内容包装在对应的动画容器中。
 *
 * 用法：
 * ```
 * AnimatedWrapper(spec = animationSpec) {
 *     // 被包装的 UI 内容
 * }
 * ```
 */
@Composable
fun AnimatedWrapper(
    spec: FoundryAnimationSpec,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    if (spec.type == FoundryAnimationType.NONE) {
        content()
        return
    }

    var isVisible by remember { mutableStateOf(!spec.triggerOnAppear) }

    LaunchedEffect(Unit) {
        if (spec.triggerOnAppear) {
            delay(spec.delayMs.toLong())
            isVisible = true
        }
    }

    val animSpec = AnimationParser.buildAnimationSpec(spec)

    when (spec.type) {
        FoundryAnimationType.FADE_IN -> {
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(animationSpec = animSpec),
                exit = fadeOut(),
                modifier = modifier
            ) { content() }
        }

        FoundryAnimationType.SLIDE_IN_LEFT -> {
            AnimatedVisibility(
                visible = isVisible,
                enter = slideInHorizontally(animationSpec = animSpec, initialOffsetX = { -it }),
                exit = slideOutHorizontally(targetOffsetX = { -it }),
                modifier = modifier
            ) { content() }
        }

        FoundryAnimationType.SLIDE_IN_RIGHT -> {
            AnimatedVisibility(
                visible = isVisible,
                enter = slideInHorizontally(animationSpec = animSpec, initialOffsetX = { it }),
                exit = slideOutHorizontally(targetOffsetX = { it }),
                modifier = modifier
            ) { content() }
        }

        FoundryAnimationType.SLIDE_IN_TOP -> {
            AnimatedVisibility(
                visible = isVisible,
                enter = slideInVertically(animationSpec = animSpec, initialOffsetY = { -it }),
                exit = slideOutVertically(targetOffsetY = { -it }),
                modifier = modifier
            ) { content() }
        }

        FoundryAnimationType.SLIDE_IN_BOTTOM -> {
            AnimatedVisibility(
                visible = isVisible,
                enter = slideInVertically(animationSpec = animSpec, initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
                modifier = modifier
            ) { content() }
        }

        FoundryAnimationType.SCALE_IN -> {
            AnimatedVisibility(
                visible = isVisible,
                enter = scaleIn(animationSpec = animSpec),
                exit = scaleOut(),
                modifier = modifier
            ) { content() }
        }

        FoundryAnimationType.EXPAND_VERTICAL -> {
            AnimatedVisibility(
                visible = isVisible,
                enter = expandVertically(animationSpec = animSpec),
                exit = shrinkVertically(),
                modifier = modifier
            ) { content() }
        }

        FoundryAnimationType.PULSE -> {
            val scale by animateFloatAsState(
                targetValue = if (isVisible) 1f else 0.8f,
                animationSpec = animSpec,
                label = "pulse"
            )
            Box(modifier = modifier.scale(scale)) { content() }
        }

        FoundryAnimationType.BOUNCE -> {
            val animatable = remember { Animatable(0f) }
            LaunchedEffect(isVisible) {
                if (isVisible) {
                    animatable.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = spec.springStiffness ?: Spring.StiffnessMedium
                        )
                    )
                }
            }
            Box(
                modifier = modifier.graphicsLayer {
                    translationY = (1f - animatable.value) * -20f
                    alpha = animatable.value
                }
            ) { content() }
        }

        FoundryAnimationType.SHAKE -> {
            val offsetX = remember { Animatable(0f) }
            LaunchedEffect(isVisible) {
                if (isVisible) {
                    for (i in 1..5) {
                        offsetX.animateTo(
                            targetValue = if (i % 2 == 0) 0f else 8f,
                            animationSpec = tween(50)
                        )
                    }
                }
            }
            Box(
                modifier = modifier.graphicsLayer { translationX = offsetX.value }
            ) { content() }
        }

        FoundryAnimationType.ROTATE_IN -> {
            val rotation = remember { Animatable(90f) }
            val alphaVal = remember { Animatable(0f) }
            LaunchedEffect(isVisible) {
                if (isVisible) {
                    launch { rotation.animateTo(0f, animationSpec = tween(spec.durationMs)) }
                    alphaVal.animateTo(1f, animationSpec = tween(spec.durationMs))
                }
            }
            Box(
                modifier = modifier.graphicsLayer {
                    rotationZ = rotation.value
                    alpha = alphaVal.value
                }
            ) { content() }
        }

        FoundryAnimationType.NONE -> content()
    }
}
