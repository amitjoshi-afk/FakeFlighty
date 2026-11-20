package app.contrail.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun Panel(label: String?, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
        if (label != null) {
            SectionLabel(label)
            Spacer(Modifier.height(8.dp))
        }
        content()
    }
}

/** A static track between two airports: solid line with an ember dot at each end. */
@Composable
fun RouteLine() {
    Box(Modifier.fillMaxWidth().height(10.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(MaterialTheme.colorScheme.outline))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(Ember))
            Box(Modifier.size(8.dp).clip(CircleShape).background(Ember))
        }
    }
}

@Composable
fun Fact(label: String, value: String?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value ?: "–", fontWeight = FontWeight.SemiBold)
    }
}

/** Horizontal bar used in stats rankings. */
@Composable
fun RankBar(label: String, sub: String?, count: Int, max: Int) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row {
            Text(label, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            Text("$count", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        sub?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            Box(Modifier.fillMaxWidth(count.toFloat() / max.coerceAtLeast(1)).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(Ember))
        }
    }
}
