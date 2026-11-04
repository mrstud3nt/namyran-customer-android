package ir.namyranservice.customer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.namyranservice.customer.design.NamyranColors

@Composable
fun GradientAction(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Surface(
        onClick = onClick, enabled = enabled, shape = shape,
        color = if (enabled) NamyranColors.blue else Color.Gray,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
    ) {
        Box(Modifier.background(if (enabled) Brush.horizontalGradient(listOf(NamyranColors.blue, NamyranColors.cyan)) else Brush.linearGradient(listOf(Color.Gray, Color.Gray)))) {
            Box(Modifier.fillMaxSize().padding(14.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text(text, color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun InfoPanel(title: String, detail: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(detail, style = MaterialTheme.typography.bodyMedium)
            if (action != null && onAction != null) TextButton(onClick = onAction) { Text(action) }
        }
    }
}
