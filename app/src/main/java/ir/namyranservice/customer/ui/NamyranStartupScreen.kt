package ir.namyranservice.customer.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.namyranservice.customer.R

/**
 * Native startup presentation. The caller owns readiness and navigation:
 * this composable never uses a fixed timer to dismiss itself.
 * Keep the existing launcher icon as the brand asset rather than inventing a logo.
 */
@Composable
fun NamyranStartupScreen(modifier: Modifier = Modifier) {
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) { entrance.animateTo(1f, tween(650, easing = FastOutSlowInEasing)) }
    val pulse = rememberInfiniteTransition(label = "loading")
    val glow by pulse.animateFloat(
        initialValue = .35f, targetValue = .8f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow"
    )
    Box(
        modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF06172F), Color(0xFF0B3269), Color(0xFF071A35)))
        ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(R.mipmap.ic_launcher),
                contentDescription = "لوگوی نامیران سرویس",
                modifier = Modifier.size(184.dp).scale(.88f + .12f * entrance.value).alpha(entrance.value)
            )
            Spacer(Modifier.height(22.dp))
            Text("نامیران سرویس", color = Color.White, fontWeight = FontWeight.Bold,
                fontSize = 25.sp, modifier = Modifier.alpha(entrance.value))
            Spacer(Modifier.height(38.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp).alpha(glow),
                color = Color(0xFFE5B74F), strokeWidth = 2.5.dp,
                trackColor = Color.White.copy(alpha = .15f)
            )
        }
    }
}
