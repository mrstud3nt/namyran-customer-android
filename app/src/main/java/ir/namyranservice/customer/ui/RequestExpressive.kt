package ir.namyranservice.customer.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.namyranservice.customer.design.NamyranColors
import org.json.JSONObject

private val ink = Color(0xFF101C51)
private val lilac = Color(0xFFEEE7FF)
private val outline = Color(0xFFD9DAF3)
private val electric = Color(0xFF115CFA)

@Composable
fun ExpressiveRequestHeader(step: Int, heading: String, onClose: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                color = Color(0xFFE5E9FF), shape = RoundedCornerShape(19.dp),
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Handyman, null, tint = electric, modifier = Modifier.size(30.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("درخواست خدمت", color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                Text("مرحله ${step+1} از ۴ · ${heading}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium)
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Outlined.Close, "ذخیره و بستن")
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (0..3).forEach { index ->
                Box(
                    Modifier.weight(1f).height(7.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if(index <= step) electric else outline)
                )
            }
        }
    }
}

@Composable
fun ExpressiveServiceTile(
    service: JSONObject,
    enabled: Boolean,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val type=service.optString("icon")
    val icon=when(type) {
        "install" -> Icons.Outlined.Handyman
        "warranty" -> Icons.Outlined.VerifiedUser
        "periodic" -> Icons.Outlined.Update
        else -> Icons.Outlined.Build
    }
    val accent=when(type) {
        "install" -> listOf(Color(0xFF12728E), Color(0xFF31A9BB))
        "warranty" -> listOf(Color(0xFF5742AC), Color(0xFF8063CF))
        "periodic" -> listOf(Color(0xFF11755C), Color(0xFF2EB78A))
        else -> listOf(Color(0xFF1C55A3), Color(0xFF4484E4))
    }
    val shape=RoundedCornerShape(25.dp)
    Surface(
        onClick=onClick, enabled=enabled, modifier=modifier.heightIn(min=146.dp),
        shape=shape, color=if(selected) lilac else MaterialTheme.colorScheme.surface,
        border=BorderStroke(if(selected) 2.dp else 1.dp, if(selected) electric else outline),
        shadowElevation=if(selected) 5.dp else 2.dp
    ) {
        Box {
            Column(
                Modifier.fillMaxWidth().padding(horizontal=10.dp,vertical=17.dp),
                verticalArrangement=Arrangement.spacedBy(13.dp),
                horizontalAlignment=Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier.size(65.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Brush.linearGradient(accent)),
                    contentAlignment=Alignment.Center
                ) {
                    Icon(icon,null,tint=Color.White,modifier=Modifier.size(34.dp))
                }
                Text(service.optString("title"),fontWeight=FontWeight.Bold,
                    style=MaterialTheme.typography.titleMedium,
                    textAlign=TextAlign.Center,color=if(selected) ink else MaterialTheme.colorScheme.onSurface)
                if(selected) Icon(Icons.Outlined.CheckCircle,"انتخاب‌شده",tint=electric,
                    modifier=Modifier.size(20.dp))
            }
        }
    }
}

/** The options always come from the live Core catalog; no local category/brand lists. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpressivePicker(
    label: String,
    options: List<Pair<String,String>>,
    value: String,
    enabled: Boolean = true,
    onChoose: (String)->Unit
) {
    var open by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    val selected=options.firstOrNull { it.first == value }?.second
    val leadingIcon: ImageVector = when(label) {
        "گروه دستگاه" -> Icons.Outlined.Category
        "دستگاه" -> Icons.Outlined.Tv
        "برند" -> Icons.Outlined.LocalOffer
        else -> Icons.Outlined.Tune
    }
    Surface(
        onClick={search="";open=true}, enabled=enabled && options.isNotEmpty(),
        shape=RoundedCornerShape(22.dp), modifier=Modifier.fillMaxWidth().heightIn(min=73.dp),
        color=MaterialTheme.colorScheme.surface,
        border=BorderStroke(1.dp,outline),
        shadowElevation=2.dp
    ) {
        Row(Modifier.padding(horizontal=16.dp,vertical=12.dp),
            verticalAlignment=Alignment.CenterVertically) {
            Surface(shape=RoundedCornerShape(14.dp), color=Color(0xFFECEFFF)) {
                Box(Modifier.size(43.dp), contentAlignment=Alignment.Center) {
                    Icon(leadingIcon,null,tint=electric,modifier=Modifier.size(24.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                Text(label,color=MaterialTheme.colorScheme.onSurfaceVariant,
                    style=MaterialTheme.typography.labelMedium)
                Text(selected ?: "انتخاب کنید",
                    fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.onSurface,
                    style=MaterialTheme.typography.bodyLarge)
            }
            Icon(Icons.Outlined.KeyboardArrowDown,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if(open) {
        ModalBottomSheet(
            onDismissRequest={open=false},
            containerColor=MaterialTheme.colorScheme.surface,
            shape=RoundedCornerShape(topStart=32.dp,topEnd=32.dp),
            dragHandle={BottomSheetDefaults.DragHandle(color=outline)}
        ) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding()
                .padding(start=20.dp,end=20.dp,bottom=16.dp),
                verticalArrangement=Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Surface(shape=RoundedCornerShape(16.dp),color=Color(0xFFECEFFF)) {
                        Box(Modifier.size(52.dp),contentAlignment=Alignment.Center) {
                            Icon(leadingIcon,null,tint=electric)
                        }
                    }
                    Text(label,style=MaterialTheme.typography.headlineSmall,
                        fontWeight=FontWeight.ExtraBold)
                }
                if(options.size>8) OutlinedTextField(
                    search,{search=it},modifier=Modifier.fillMaxWidth(),singleLine=true,
                    placeholder={Text("جست‌وجو در ${label}")},
                    leadingIcon={Icon(Icons.Outlined.Search,null)},
                    shape=RoundedCornerShape(18.dp)
                )
                val filtered=options.filter { it.second.contains(search,ignoreCase=true) }
                Column(Modifier.fillMaxWidth().heightIn(max=440.dp)
                    .verticalScroll(rememberScrollState()),
                    verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    if(filtered.isEmpty()) Text("موردی پیدا نشد",
                        Modifier.padding(16.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)
                    filtered.forEach { (id,title) ->
                        val active=id==value
                        Surface(
                            onClick={onChoose(id);open=false},
                            color=if(active) Color(0xFFF0EBFF) else MaterialTheme.colorScheme.surface,
                            shape=RoundedCornerShape(18.dp),
                            border=BorderStroke(if(active) 1.5.dp else 1.dp,
                                if(active) electric else outline),
                            modifier=Modifier.fillMaxWidth().heightIn(min=60.dp)
                        ) {
                            Row(Modifier.padding(13.dp),
                                verticalAlignment=Alignment.CenterVertically) {
                                Surface(shape=RoundedCornerShape(12.dp),color=Color(0xFFE9EEFF)) {
                                    Box(Modifier.size(37.dp),contentAlignment=Alignment.Center) {
                                        Icon(leadingIcon,null,tint=electric,
                                            modifier=Modifier.size(20.dp))
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(title,Modifier.weight(1f),style=MaterialTheme.typography.bodyLarge,
                                    fontWeight=if(active) FontWeight.Bold else FontWeight.Medium)
                                Icon(
                                    if(active) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                                    null,tint=if(active) electric else Color(0xFFA4AAC3)
                                )
                            }
                        }
                    }
                }
                Button(
                    onClick={open=false},modifier=Modifier.fillMaxWidth().height(54.dp),
                    colors=ButtonDefaults.buttonColors(containerColor=electric),
                    shape=RoundedCornerShape(20.dp)
                ) { Text("بستن",fontWeight=FontWeight.Bold) }
            }
        }
    }
}

@Composable
fun ExpressiveModelField(value: String, enabled:Boolean,onChange:(String)->Unit) {
    OutlinedTextField(
        value,onChange,enabled=enabled,modifier=Modifier.fillMaxWidth(),
        label={Text("مدل دستگاه، در صورت اطلاع")},singleLine=true,
        leadingIcon={Icon(Icons.Outlined.Keyboard,null)},
        shape=RoundedCornerShape(23.dp),
        colors=OutlinedTextFieldDefaults.colors(
            focusedBorderColor=electric,focusedLabelColor=electric,
            unfocusedBorderColor=outline
        )
    )
}

@Composable
fun ExpressiveAddDeviceAction(enabled:Boolean,onClick:()->Unit) {
    Surface(
        onClick=onClick,enabled=enabled,shape=RoundedCornerShape(22.dp),
        color=Color(0xFFF0EDFF),modifier=Modifier.fillMaxWidth().heightIn(min=57.dp)
    ) {
        Row(Modifier.padding(12.dp),horizontalArrangement=Arrangement.Center,
            verticalAlignment=Alignment.CenterVertically) {
            Icon(Icons.Outlined.AddCircle,null,tint=electric)
            Spacer(Modifier.width(10.dp))
            Text("افزودن دستگاه دیگر به این درخواست",
                fontWeight=FontWeight.SemiBold,color=ink)
        }
    }
}

@Composable
fun ExpressiveRequestNavigation(
    canContinue:Boolean,
    onContinue:()->Unit,
    onBack:()->Unit
) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Button(
            onClick=onContinue,enabled=canContinue,modifier=Modifier.weight(1f).height(58.dp),
            shape=RoundedCornerShape(24.dp),
            colors=ButtonDefaults.buttonColors(containerColor=electric)
        ) { Text("ادامه",fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium) }
        OutlinedButton(
            onClick=onBack,modifier=Modifier.weight(1f).height(58.dp),
            shape=RoundedCornerShape(24.dp),
            border=BorderStroke(1.dp,outline)
        ) { Text("بازگشت",fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium) }
    }
}
