package com.example.socialstasts.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowColumn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.max
import kotlin.math.min

data class Group(
    val series: Array<Series>,
    val axis: Int = 0,
    val stack: Boolean = false
)

data class Series(
    val name: AnnotatedString,
    var buckets: Array<Bucket>,
    val color: Color,
    val unit: String = "",
    val fmt: String = "%.1f"
)

data class Bucket(
    var sum: Float = 0f,
    var count: Int = 0,
) {
    fun avg(): Float {
        if (count <= 0) return Float.NaN
        return sum / count.toFloat()
    }

    fun observe(x: Float) {
        sum += x
        count++
    }
}

data class AxisHints(
    var min: Float = Float.NaN,
    var max: Float = Float.NaN,
    var color: Color? = null,
    var fmt: String? = null,
)

@Composable
private fun RoundedBox(content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) { content() }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BarChart(
    groups: Array<Group>,
    totalHeight: Dp = 120.dp,
    heightOfMin: Dp = totalHeight / 10,
    heightOfNaN: Dp = 5.dp,
    groupWidthFraction: Float = .9f,
    barWidthFraction: Float = 1f,
    legendStride: Int = 3,
    selectedBucket: MutableState<Int?> = remember { mutableStateOf<Int?>(null) },
    trendMessage: String? = null,
    onTrendDetails: () -> Unit = {},
    criticalLineValue: Float? = null,
    criticalLineAxis: Int = 0,
) {
    val chartHeight = totalHeight - heightOfMin
    val hints = mutableMapOf<Int, AxisHints>()

    fun aggrNaN(x: Float, y: Float, f: (Float, Float) -> Float): Float {
        if (x.isNaN()) return y
        if (y.isNaN()) return x
        return f(x, y)
    }

    var numBuckets = 0
    var haveAnyValidDataPoints = false

    groups.forEach { g ->
        val h = hints.getOrDefault(g.axis, AxisHints())
        g.series.forEach { s ->
            numBuckets = max(numBuckets, s.buckets.size)
            s.buckets.forEach { bucket ->
                val x = bucket.avg()
                if (!x.isNaN()) haveAnyValidDataPoints = true
                h.min = aggrNaN(h.min, x) { a, b -> min(a, b) }
                h.max = aggrNaN(h.max, x) { a, b -> max(a, b) }
            }
            h.color = if (h.color == null) s.color else Color.Gray
            if (h.fmt == null) h.fmt = s.fmt
        }
        hints[g.axis] = h
    }

    val density = LocalDensity.current
    var legendHeight by remember { mutableStateOf(0.dp) }

    val background = MaterialTheme.colorScheme.surface
    val lightGray = MaterialTheme.colorScheme.onSurfaceVariant
    val darkGray = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
    val orange = MaterialTheme.colorScheme.tertiary
    val onOrange = MaterialTheme.colorScheme.onTertiary
    val darkRed = MaterialTheme.colorScheme.error

    Column(
        modifier = Modifier.clickable { selectedBucket.value = null },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // ===== Series legend (top) =====
        Row(
            horizontalArrangement = Arrangement.spacedBy(15.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            groups.forEach { g ->
                g.series.forEach { s ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .onSizeChanged {
                                density.run {
                                    val dp = it.height.toDp()
                                    if (dp > legendHeight) legendHeight = dp
                                }
                            }
                            .defaultMinSize(minHeight = legendHeight)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(s.color)
                        )

                        var text = s.name
                        if (selectedBucket.value != null) {
                            val idx = selectedBucket.value!!
                            val x = if (idx < s.buckets.size) s.buckets[idx].avg() else Float.NaN
                            text = AnnotatedString(if (x.isNaN()) "N/A" else s.fmt.format(x))
                        }

                        if (s.unit.isNotBlank()) {
                            text = buildAnnotatedString {
                                append(text)
                                append(" ${s.unit}")
                            }
                        }

                        Text(
                            text = text,
                            style = MaterialTheme.typography.labelSmall,
                            color = s.color,
                        )
                    }
                }
            }
        }

        // ===== Body =====
        Box {
            // Axis lines under bars
            FlowColumn(
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.height(chartHeight)
            ) {
                repeat(3) {
                    HorizontalDivider(color = Color(0xffdddddd), thickness = .5.dp)
                }
            }

            Row {
                // Chart data
                Column(Modifier.weight(1f)) {
                    if (!haveAnyValidDataPoints) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(totalHeight),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                "No data in range.",
                                style = MaterialTheme.typography.titleMedium,
                                color = lightGray
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(totalHeight)
                        ) {
                            // Buckets (NO horizontalScroll)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(totalHeight),
                                verticalAlignment = Alignment.Bottom,
                            ) {
                                for (i in 0 until numBuckets) {
                                    Row(
                                        horizontalArrangement = Arrangement.Center,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                selectedBucket.value =
                                                    if (selectedBucket.value == i) null else i
                                            }
                                            .fillMaxHeight(),
                                        verticalAlignment = Alignment.Bottom,
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(groupWidthFraction),
                                            verticalAlignment = Alignment.Bottom,
                                        ) {
                                            groups.forEach { g ->
                                                Column(
                                                    modifier = Modifier.weight(1f),
                                                    verticalArrangement = Arrangement.Bottom,
                                                ) {
                                                    Box(contentAlignment = Alignment.BottomCenter) {
                                                        g.series.forEachIndexed { j, s ->
                                                            val ah = hints.getOrDefault(g.axis, AxisHints())
                                                            val x =
                                                                if (i >= s.buckets.size) Float.NaN
                                                                else s.buckets[i].avg()

                                                            val h = if (x.isNaN()) heightOfNaN
                                                            else if (ah.min == ah.max) heightOfMin
                                                            else heightOfMin + (((x - ah.min) / (ah.max - ah.min)) * chartHeight.value).dp

                                                            var c = if (x.isNaN()) Color.LightGray else s.color
                                                            if (selectedBucket.value != null && i != selectedBucket.value) {
                                                                val gray =
                                                                    (1 + 0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue) / 2
                                                                c = Color(gray, gray, gray, 1f)
                                                            }

                                                            Box(
                                                                modifier = Modifier
                                                                    .fillMaxWidth(barWidthFraction)
                                                                    .clip(RoundedCornerShape(10.dp))
                                                                    .height(h)
                                                                    .background(c)
                                                                    .zIndex(-j.toFloat())
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Critical line overlay
                            if (criticalLineValue != null) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val ah = hints.getOrDefault(criticalLineAxis, AxisHints())
                                    if (ah.max != ah.min) {
                                        val heightOfMinPx = heightOfMin.toPx()
                                        val chartHeightPx = size.height - heightOfMinPx
                                        val frac =
                                            ((criticalLineValue - ah.min) / (ah.max - ah.min))
                                                .coerceIn(0f, 1f)
                                        val hPx = heightOfMinPx + frac * chartHeightPx
                                        val y = size.height - hPx
                                        drawLine(
                                            brush = SolidColor(darkRed),
                                            start = Offset(0f, y),
                                            end = Offset(size.width, y),
                                            strokeWidth = 2.dp.toPx(),
                                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // X-axis legend (NO scroll)
                    Row(modifier = Modifier.fillMaxWidth()) {
                        val stride = max(1, legendStride)
                        var i = 0
                        while (i < numBuckets) {
                            val labelIndex = min(numBuckets - 1, (stride / 2) + i)
                            Column(
                                verticalArrangement = Arrangement.Bottom,
                                modifier = Modifier.weight(stride.toFloat())
                            ) {
                                Text(
                                    text = "$labelIndex",
                                    style = MaterialTheme.typography.labelSmall,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                    color = Color.Gray,
                                )
                            }
                            i += stride
                        }
                        // Fill remaining space if stride doesn't divide nicely
                        val remainder = numBuckets % stride
                        if (remainder != 0) {
                            Spacer(Modifier.weight((stride - remainder).toFloat()))
                        }
                    }
                }

                // Y-axis legend (right)
                Column(
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier
                        .height(chartHeight)
                        .padding(start = 10.dp)
                ) {
                    Column {
                        hints.forEach { ah ->
                            if (!ah.value.max.isNaN() && ah.value.min != ah.value.max) {
                                Text(
                                    (ah.value.fmt ?: "%.1f").format(ah.value.max),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ah.value.color ?: Color.Gray,
                                )
                            }
                        }
                    }
                    Column {
                        hints.forEach { ah ->
                            if (!ah.value.min.isNaN()) {
                                Text(
                                    (ah.value.fmt ?: "%.1f").format(ah.value.min),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ah.value.color ?: Color.Gray,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Trend box (optional)
        var dismissed by remember(trendMessage) { mutableStateOf(false) }
        if (trendMessage != null && !dismissed) {
            Spacer(Modifier.height(4.dp))
            RoundedBox {
                Row(
                    modifier = Modifier
                        .background(background)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 4.dp, horizontal = 8.dp)
                            .clickable { onTrendDetails() }
                            .wrapContentHeight(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(32.dp))
                                .background(orange)
                                .padding(6.dp),
                        ) {
                            Text(
                                text = trendMessage,
                                color = onOrange,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Spacer(Modifier.size(8.dp))
                        Text(
                            text = "Recommended action...",
                            style = MaterialTheme.typography.titleMedium,
                            color = lightGray,
                        )
                    }

                    IconButton(onClick = { dismissed = true }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = darkGray
                        )
                    }
                }
            }
        }
    }
}