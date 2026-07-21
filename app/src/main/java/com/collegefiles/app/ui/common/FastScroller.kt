package com.collegefiles.app.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun <T> AlphabeticalFastScroller(
    items: List<T>,
    getItemName: (T) -> String,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    
    // Map of letter ('A'..'Z', '#') to first item index in `items`
    val letterIndexMap = remember(items) {
        val map = mutableMapOf<Char, Int>()
        items.forEachIndexed { index, item ->
            val name = getItemName(item).trim()
            val firstChar = name.firstOrNull()?.uppercaseChar() ?: '#'
            val key = if (firstChar in 'A'..'Z') firstChar else '#'
            if (!map.containsKey(key)) {
                map[key] = index
            }
        }
        map
    }

    val fullAlphabet = remember {
        listOf('#') + ('A'..'Z').toList()
    }

    // Filter to letters actually present in the current items list
    val activeAlphabet = remember(letterIndexMap) {
        val present = fullAlphabet.filter { letterIndexMap.containsKey(it) }
        if (present.isEmpty()) fullAlphabet else present
    }

    var isDragging by remember { mutableStateOf(false) }
    var activeLetter by remember { mutableStateOf<Char?>(null) }
    var columnHeightPx by remember { mutableStateOf(0f) }
    var pageStartIndex by remember { mutableIntStateOf(0) }

    val density = LocalDensity.current
    val maxFitCount = remember(columnHeightPx) {
        if (columnHeightPx <= 0f) 10 else {
            val itemHeightPx = with(density) { 22.dp.toPx() }
            (columnHeightPx / itemHeightPx).toInt().coerceAtLeast(4)
        }
    }

    // Slice active letters based on pagination pageStartIndex
    val visibleAlphabet = remember(activeAlphabet, pageStartIndex, maxFitCount) {
        if (activeAlphabet.size <= maxFitCount) {
            activeAlphabet
        } else {
            val safeStart = pageStartIndex.coerceIn(0, (activeAlphabet.size - maxFitCount).coerceAtLeast(0))
            activeAlphabet.subList(safeStart, (safeStart + maxFitCount).coerceAtMost(activeAlphabet.size))
        }
    }

    val canPageUp = pageStartIndex > 0
    val canPageDown = (pageStartIndex + maxFitCount) < activeAlphabet.size

    fun scrollToLetter(yOffset: Float) {
        val height = columnHeightPx
        if (height <= 0f || visibleAlphabet.isEmpty()) return
        val clampedY = yOffset.coerceIn(0f, height - 1f)
        val fraction = clampedY / height
        val index = (fraction * visibleAlphabet.size).toInt().coerceIn(0, visibleAlphabet.size - 1)
        val letter = visibleAlphabet[index]
        activeLetter = letter

        val targetIndex = letterIndexMap[letter] ?: run {
            val charCode = letter.code
            letterIndexMap.keys
                .minByOrNull { kotlin.math.abs(it.code - charCode) }
                ?.let { letterIndexMap[it] }
        }

        targetIndex?.let { idx ->
            coroutineScope.launch {
                listState.scrollToItem(idx)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        content(Modifier.fillMaxSize())

        if (items.size > 5) { // Show side index bar when there are more than 5 items
            Row(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(vertical = 8.dp, horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Floating Letter Preview Bubble
                AnimatedVisibility(
                    visible = isDragging && activeLetter != null,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.padding(end = 12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        shadowElevation = 8.dp,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = activeLetter?.toString() ?: "",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                        }
                    }
                }

                // Vertical Alphabet Strip Container with Up/Down Arrow Buttons
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxHeight()
                ) {
                    // Up Arrow Button (▲)
                    IconButton(
                        onClick = {
                            pageStartIndex = (pageStartIndex - (maxFitCount / 2)).coerceAtLeast(0)
                        },
                        enabled = canPageUp,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = "Page Up Letters",
                            tint = if (canPageUp) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // Letter Strip
                    Column(
                        modifier = Modifier
                            .width(26.dp)
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
                            .onGloballyPositioned { layoutCoordinates ->
                                columnHeightPx = layoutCoordinates.size.height.toFloat()
                            }
                            .pointerInput(visibleAlphabet, columnHeightPx) {
                                detectTapGestures(
                                    onPress = { offset ->
                                        isDragging = true
                                        scrollToLetter(offset.y)
                                        tryAwaitRelease()
                                        isDragging = false
                                    }
                                )
                            }
                            .pointerInput(visibleAlphabet, columnHeightPx) {
                                detectVerticalDragGestures(
                                    onDragStart = { offset ->
                                        isDragging = true
                                        scrollToLetter(offset.y)
                                    },
                                    onDragEnd = { isDragging = false },
                                    onDragCancel = { isDragging = false },
                                    onVerticalDrag = { change, _ ->
                                        scrollToLetter(change.position.y)
                                    }
                                )
                            },
                        verticalArrangement = Arrangement.SpaceEvenly,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        visibleAlphabet.forEach { char ->
                            Text(
                                text = char.toString(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // Down Arrow Button (▼)
                    IconButton(
                        onClick = {
                            pageStartIndex = (pageStartIndex + (maxFitCount / 2)).coerceAtMost((activeAlphabet.size - maxFitCount).coerceAtLeast(0))
                        },
                        enabled = canPageDown,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Page Down Letters",
                            tint = if (canPageDown) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                        )
                    }
                }
            }
        }
    }
}
