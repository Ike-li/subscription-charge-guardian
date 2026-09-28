package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 截图识别出的候选值，点一下填入对应字段；[selectedIndex] 是当前字段值在候选里的位置（不在则为 -1）
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OcrCandidateChips(
    labels: List<String>,
    selectedIndex: Int,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // 唯一的候选已经填在框里时，这一行没有信息量
    if (labels.isEmpty() || (labels.size == 1 && selectedIndex == 0)) return

    Column(modifier = modifier.padding(top = 6.dp)) {
        Text(
            text = "识别到的其他可能，点一下替换：",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            labels.forEachIndexed { index, label ->
                FilterChip(
                    selected = index == selectedIndex,
                    onClick = { onPick(index) },
                    label = { Text(label, style = MaterialTheme.typography.bodyLarge) }
                )
            }
        }
    }
}
