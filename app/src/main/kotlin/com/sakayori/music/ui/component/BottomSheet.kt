/**
 * Sakayori Music Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sakayori.music.ui.component

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.focusable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.sakayori.music.constants.NavigationBarAnimationSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.pow

/**
 * Bottom Sheet
 * Modified from [ViMusic](https://github.com/vfsfitvnm/ViMusic)
 */
@Composable
fun BottomSheet(
    state: BottomSheetState,
    modifier: Modifier = Modifier,
    background: @Composable (BoxScope.() -> Unit) = { },
    onDismiss: (() -> Unit)? = null,
    collapsedContent: @Composable BoxScope.() -> Unit,
    isExpandable: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val density = LocalDensity.current
    
    Box(
        modifier = modifier
            .graphicsLayer {
                // background fades during about 10%-61% progress
                alpha = (1.4f * (state.progress.coerceAtLeast(0.1f) - 0.1f).pow(0.5f)).coerceIn(0f, 1f)
            }
            .fillMaxSize(),
        content = background
    )
    Box(
        modifier = modifier
            .fillMaxSize()
            // Use graphicsLayer for offset to ensure hardware acceleration and 120Hz support
            .graphicsLayer {
                val y = (state.expandedBound - state.value)
                    .toPx()
                    .coerceAtLeast(0f)
                translationY = y
            }
            .pointerInput(state, isExpandable) {
                if (!isExpandable) return@pointerInput
                val velocityTracker = VelocityTracker()

                detectVerticalDragGestures(
                    onDragStart = {
                        state.onDragStarted()
                    },
                    onVerticalDrag = { change, dragAmount ->
                        velocityTracker.addPointerInputChange(change)
                        state.dispatchRawDelta(dragAmount)
                    },
                    onDragCancel = {
                        velocityTracker.resetTracking()
                        state.onDragCancelled()
                    },
                    onDragEnd = {
                        val velocity = -velocityTracker.calculateVelocity().y
                        velocityTracker.resetTracking()
                        state.onDragEnded(velocity, onDismiss)
                    }
                )
            }
            .graphicsLayer {
                val cornerRadius = if (!state.isExpanded) 16.dp.toPx() else 0f
                shape = RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius)
                clip = true
            }
    ) {
        if (!state.isCollapsed && !state.isDismissed) {
            PredictiveBackHandler { progress ->
                state.onDragStarted()
                val initialValue = state.value
                try {
                    val range = initialValue - state.collapsedBound
                    progress.collect { event ->
                        state.snapToAndWait(
                            initialValue - range * event.progress.coerceIn(0f, 1f)
                        )
                    }
                    state.collapseSoft()
                } catch (_: CancellationException) {
                    state.expandSoft()
                } finally {
                    state.onPredictiveBackEnded()
                }
            }
        }

        // main content
        if (!state.isCollapsed) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = ((state.progress - 0.15f) * 4).coerceIn(0f, 1f)
                    },
                content = content
            )
        }

        if (!state.isExpanded && (onDismiss == null || !state.isDismissed)) {
            Box(
                modifier =
                Modifier
                    .graphicsLayer {
                        alpha = 1f - (state.progress * 4).coerceAtMost(1f)
                    }.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { if (isExpandable) state.expandSoft() },
                    )
                    .focusable(false)
                    .fillMaxWidth()
                    .height(state.collapsedBound),
                content = collapsedContent,
            )
        }
    }
}

@Stable
class BottomSheetState(
    draggableState: DraggableState,
    private val coroutineScope: CoroutineScope,
    private val animatable: Animatable<Dp, AnimationVector1D>,
    private val onAnchorChanged: (Int) -> Unit,
    initialCollapsedBound: Dp,
    initialAnchor: Int = dismissedAnchor,
) : DraggableState by draggableState {
    var collapsedBound by mutableStateOf(initialCollapsedBound)
        private set

    var isDragging by mutableStateOf(false)
        private set

    private var hasPendingBoundsUpdate = false
    private var lastRequestedAnchor = initialAnchor
    private var currentTargetValue: Dp? = animatable.value
    private var activeAnimationJob: Job? = null
    private var animationToken = 0L

    val dismissedBound: Dp
        get() = animatable.lowerBound!!

    val expandedBound: Dp
        get() = animatable.upperBound!!

    val value by animatable.asState()

    val isDismissed by derivedStateOf {
        value == animatable.lowerBound!!
    }

    val isCollapsed by derivedStateOf {
        value == collapsedBound
    }

    val isExpanded by derivedStateOf {
        value == animatable.upperBound
    }

    val progress by derivedStateOf {
        1f - (animatable.upperBound!! - animatable.value) / (animatable.upperBound!! - collapsedBound)
    }

    private fun setAnchor(anchor: Int) {
        lastRequestedAnchor = anchor
        onAnchorChanged(anchor)
    }

    private fun animateToInternal(
        target: Dp,
        animationSpec: AnimationSpec<Dp>
    ) {
        if (currentTargetValue == target && activeAnimationJob?.isActive == true) return
        currentTargetValue = target
        val token = ++animationToken
        activeAnimationJob?.cancel()
        val job = coroutineScope.launch {
            try {
                animatable.animateTo(target, animationSpec)
            } finally {
                if (animationToken == token) {
                    activeAnimationJob = null
                }
            }
        }
        activeAnimationJob = job
    }

    fun updateBounds(
        dismissedBound: Dp,
        expandedBound: Dp,
        newCollapsedBound: Dp,
        currentAnchor: Int
    ) {
        collapsedBound = newCollapsedBound
        animatable.updateBounds(dismissedBound.coerceAtMost(expandedBound), expandedBound)
        val activeAnchor = lastRequestedAnchor
        val targetValue = when (activeAnchor) {
            expandedAnchor -> expandedBound
            collapsedAnchor -> newCollapsedBound
            dismissedAnchor -> dismissedBound
            else -> newCollapsedBound
        }
        if (isDragging) {
            hasPendingBoundsUpdate = true
        } else {
            hasPendingBoundsUpdate = false
            if (currentTargetValue != targetValue) {
                animateToInternal(targetValue, NavigationBarAnimationSpec)
            }
        }
    }

    fun onDragStarted() {
        if (isDragging) return
        isDragging = true
        hasPendingBoundsUpdate = false
        ++animationToken
        activeAnimationJob?.cancel()
        activeAnimationJob = null
        currentTargetValue = null
    }

    fun onDragCancelled() {
        isDragging = false
        hasPendingBoundsUpdate = false
        setAnchor(collapsedAnchor)
        snapTo(collapsedBound)
    }

    fun onDragEnded(velocity: Float, onDismiss: (() -> Unit)?) {
        // performFling decides the final anchor and animates to latest bounds
        performFling(velocity, onDismiss)
        isDragging = false
        hasPendingBoundsUpdate = false
    }

    fun onNestedScrollEnded() {
        isDragging = false
        if (hasPendingBoundsUpdate) {
            hasPendingBoundsUpdate = false
            val targetValue = when (lastRequestedAnchor) {
                expandedAnchor -> expandedBound
                collapsedAnchor -> collapsedBound
                dismissedAnchor -> dismissedBound
                else -> collapsedBound
            }
            if (currentTargetValue != targetValue) {
                animateToInternal(targetValue, NavigationBarAnimationSpec)
            }
        }
    }

    fun onPredictiveBackEnded() {
        isDragging = false
        hasPendingBoundsUpdate = false
    }

    fun collapse(animationSpec: AnimationSpec<Dp>) {
        setAnchor(collapsedAnchor)
        animateToInternal(collapsedBound, animationSpec)
    }

    fun expand(animationSpec: AnimationSpec<Dp>) {
        setAnchor(expandedAnchor)
        animateToInternal(animatable.upperBound!!, animationSpec)
    }

    private fun collapse() {
        collapse(SpringSpec())
    }

    private fun expand() {
        expand(SpringSpec())
    }

    fun collapseSoft() {
        collapse(spring(stiffness = Spring.StiffnessMediumLow))
    }

    fun expandSoft() {
        expand(spring(stiffness = Spring.StiffnessMediumLow))
    }

    fun dismiss() {
        setAnchor(dismissedAnchor)
        animateToInternal(animatable.lowerBound!!, NavigationBarAnimationSpec)
    }

    suspend fun dismissAndWait() {
        setAnchor(dismissedAnchor)
        ++animationToken
        currentTargetValue = animatable.lowerBound!!
        activeAnimationJob?.cancel()
        activeAnimationJob = null
        animatable.animateTo(animatable.lowerBound!!)
    }

    fun snapTo(value: Dp) {
        ++animationToken
        currentTargetValue = value
        activeAnimationJob?.cancel()
        activeAnimationJob = null
        coroutineScope.launch {
            animatable.snapTo(value)
        }
    }

    suspend fun snapToAndWait(value: Dp) {
        ++animationToken
        currentTargetValue = value
        activeAnimationJob?.cancel()
        activeAnimationJob = null
        animatable.snapTo(value)
    }

    fun performFling(velocity: Float, onDismiss: (() -> Unit)?) {
        if (velocity > 250) {
            expand()
        } else if (velocity < -250) {
            if (value < collapsedBound && onDismiss != null) {
                dismiss()
                onDismiss.invoke()
            } else {
                collapse()
            }
        } else {
            val l0 = dismissedBound
            val l1 = (collapsedBound - dismissedBound) / 2
            val l2 = (expandedBound - collapsedBound) / 2
            val l3 = expandedBound

            when (value) {
                in l0..l1 -> {
                    if (onDismiss != null) {
                        dismiss()
                        onDismiss.invoke()
                    } else {
                        collapse()
                    }
                }

                in l1..l2 -> collapse()
                in l2..l3 -> expand()
                else -> {
                    if (value >= l3) expand() else collapse()
                }
            }
        }
    }

    val preUpPostDownNestedScrollConnection
        get() = object : NestedScrollConnection {
            var isTopReached = false

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (isExpanded && available.y < 0) {
                    isTopReached = false
                }

                return if (isTopReached && available.y < 0 && source == NestedScrollSource.UserInput) {
                    onDragStarted()
                    dispatchRawDelta(available.y)
                    available
                } else {
                    Offset.Zero
                }
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (!isTopReached) {
                    isTopReached = consumed.y == 0f && available.y > 0
                }

                return if (isTopReached && source == NestedScrollSource.UserInput) {
                    onDragStarted()
                    dispatchRawDelta(available.y)
                    available
                } else {
                    Offset.Zero
                }
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                return if (isTopReached) {
                    val velocity = -available.y
                    onDragEnded(velocity, null)
                    available
                } else {
                    if (isDragging) {
                        onNestedScrollEnded()
                    }
                    Velocity.Zero
                }
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                isTopReached = false
                if (isDragging) {
                    onNestedScrollEnded()
                }
                return Velocity.Zero
            }
        }
}

const val expandedAnchor = 2
const val collapsedAnchor = 1
const val dismissedAnchor = 0

@Composable
fun rememberBottomSheetState(
    dismissedBound: Dp,
    expandedBound: Dp,
    collapsedBound: Dp = dismissedBound,
    initialAnchor: Int = dismissedAnchor,
): BottomSheetState {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    var previousAnchor by rememberSaveable {
        mutableIntStateOf(initialAnchor)
    }
    val initialValue = when (previousAnchor) {
        expandedAnchor -> expandedBound
        collapsedAnchor -> collapsedBound
        dismissedAnchor -> dismissedBound
        else -> dismissedBound
    }
    val animatable = remember {
        Animatable(initialValue, Dp.VectorConverter).apply {
            updateBounds(dismissedBound.coerceAtMost(expandedBound), expandedBound)
        }
    }

    val state = remember(coroutineScope) {
        BottomSheetState(
            draggableState = DraggableState { delta ->
                coroutineScope.launch {
                    animatable.snapTo(animatable.value - with(density) { delta.toDp() })
                }
            },
            onAnchorChanged = { previousAnchor = it },
            coroutineScope = coroutineScope,
            animatable = animatable,
            initialCollapsedBound = collapsedBound,
            initialAnchor = initialAnchor,
        )
    }

    LaunchedEffect(dismissedBound, expandedBound, collapsedBound) {
        state.updateBounds(dismissedBound, expandedBound, collapsedBound, previousAnchor)
    }

    return state
}
