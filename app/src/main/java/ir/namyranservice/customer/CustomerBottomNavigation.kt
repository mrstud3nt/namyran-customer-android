package ir.namyranservice.customer

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
internal fun CustomerBottomNavigation(active: String, enabled: Boolean, onSelect: (String) -> Unit) {
    val tracking = active == "track" || active == "tracking"
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().background(colors.background).navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 6.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        NavigationWing(Modifier.weight(1f)) {
            Destination("خانه", Icons.Outlined.Home, active == "home", enabled) { onSelect("home") }
            Destination("درخواست‌ها", Icons.Outlined.ReceiptLong, active == "requests", enabled) { onSelect("requests") }
        }
        val shape = RoundedCornerShape(20.dp)
        val top by animateColorAsState(if (tracking) Color(0xFF007F83) else Color(0xFF08767A), label = "Tracking fill")
        Surface(shape = shape, shadowElevation = if (tracking) 6.dp else 3.dp, modifier = Modifier.width(66.dp)) {
            Column(
                Modifier.fillMaxWidth().heightIn(min = 77.dp).clip(shape)
                    .background(Brush.verticalGradient(listOf(top, Color(0xFF004E58))))
                    .selectable(tracking, enabled = enabled, role = Role.Tab) { onSelect("track") }
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                TrackingRoute()
                Spacer(Modifier.height(3.dp))
                Text("پیگیری", color = Color.White, style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            }
        }
        NavigationWing(Modifier.weight(1f)) {
            Destination("حساب من", Icons.Outlined.Badge, active == "profile", enabled) { onSelect("profile") }
            Destination("پشتیبانی", Icons.Outlined.SupportAgent, active == "support", enabled) { onSelect("support") }
        }
    }
}

@Composable
private fun NavigationWing(modifier: Modifier, content: @Composable RowScope.() -> Unit) {
    Surface(modifier = modifier, shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface, shadowElevation = 3.dp, tonalElevation = 1.dp) {
        Row(Modifier.padding(2.dp), verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

@Composable
private fun RowScope.Destination(title: String, icon: ImageVector, active: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val tint by animateColorAsState(if (active) colors.onSecondaryContainer else colors.onSurface, label = "Tab ink")
    val fill by animateColorAsState(if (active) colors.secondaryContainer else Color.Transparent, label = "Tab fill")
    Column(
        Modifier.weight(1f).heightIn(min = 59.dp).clip(RoundedCornerShape(18.dp))
            .background(fill).selectable(active, enabled = enabled, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(23.dp))
        Spacer(Modifier.height(3.dp))
        Text(title, color = tint, style = MaterialTheme.typography.labelSmall,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium, textAlign = TextAlign.Center, maxLines = 2)
    }
}

@Composable
private fun TrackingRoute() {
    Canvas(Modifier.size(29.dp)) {
        val unit = size.width / 28f
        val path = Path().apply {
            moveTo(7f * unit, 22f * unit)
            cubicTo(15f * unit, 22f * unit, 11f * unit, 13f * unit, 19f * unit, 13f * unit)
        }
        drawPath(path, Color.White, style = Stroke(2.5f * unit, cap = StrokeCap.Round))
        drawCircle(Color.White, 3f * unit, Offset(6f * unit, 22f * unit), style = Stroke(2f * unit))
        val pin = Path().apply {
            moveTo(20f * unit, 14f * unit)
            cubicTo(18f * unit, 11f * unit, 14.5f * unit, 8.5f * unit, 14.5f * unit, 6f * unit)
            cubicTo(14.5f * unit, -0.5f * unit, 25.5f * unit, -0.5f * unit, 25.5f * unit, 6f * unit)
            cubicTo(25.5f * unit, 8.5f * unit, 22f * unit, 11f * unit, 20f * unit, 14f * unit)
            close()
        }
        drawPath(pin, Color(0xFFA0F4E9))
        drawCircle(Color(0xFF005962), 1.8f * unit, Offset(20f * unit, 6f * unit))
    }
}
