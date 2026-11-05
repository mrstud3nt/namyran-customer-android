package ir.namyranservice.customer

import android.Manifest
import android.icu.text.SimpleDateFormat
import android.icu.util.ULocale
import android.icu.util.TimeZone
import java.util.Date
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.os.CancellationSignal
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import ir.namyranservice.customer.data.*
import ir.namyranservice.customer.domain.NativeRules
import ir.namyranservice.customer.design.*
import ir.namyranservice.customer.ui.ExpressiveRequestHeader
import ir.namyranservice.customer.ui.ExpressiveServiceTile
import ir.namyranservice.customer.ui.ExpressivePicker
import ir.namyranservice.customer.ui.GuidedField
import ir.namyranservice.customer.ui.InstallationPackages
import ir.namyranservice.customer.ui.CrystalOptions
import ir.namyranservice.customer.ui.ExpressiveModelField
import ir.namyranservice.customer.ui.ExpressiveAddDeviceAction
import ir.namyranservice.customer.ui.ExpressiveRequestNavigation
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    private lateinit var vm: CustomerViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vm=ViewModelProvider(this)[CustomerViewModel::class.java]
        setContent { CustomerApp(vm) }
    }
    override fun onResume(){super.onResume();if(::vm.isInitialized)vm.refresh()}
}

@Composable private fun CustomerApp(vm:CustomerViewModel) {
    BackHandler(vm.loggedIn && (vm.creating || vm.tab in listOf("tracking","receipt"))) { if(!vm.busy){if(vm.creating&&vm.step>0)vm.step-- else {vm.creating=false;vm.tab="home"};vm.saveDraft()} }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        NamyranTheme(vm.dark) {
            if (vm.loggedIn) CustomerWorkspace(vm)
            else LoginScreen(vm)
        }
    }
}

@Composable private fun CustomerWorkspace(vm: CustomerViewModel) {
    var showMenu by rememberSaveable { mutableStateOf(false) }
    var showHelp by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val pageScroll=rememberScrollState()
    LaunchedEffect(vm.tab,vm.creating,vm.step){pageScroll.scrollTo(0)}
    Scaffold(containerColor=MaterialTheme.colorScheme.background,bottomBar={
        if(!vm.creating)NavigationBar {
            listOf(Triple("home","خانه",Icons.Outlined.Home),Triple("requests","درخواست‌ها",Icons.Outlined.ReceiptLong),Triple("profile","حساب من",Icons.Outlined.PersonOutline)).forEach{(id,title,icon)->
                NavigationBarItem(selected=vm.tab==id,onClick={if(!vm.busy){vm.tab=id;if(id=="requests")vm.loadRequests()}},icon={Icon(icon,null)},label={Text(title)})
            }
        }
    }){padding->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(pageScroll).padding(horizontal=20.dp,vertical=16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    Box {
                        IconButton(onClick={showMenu=true},enabled=!vm.busy&&!vm.creating) {
                            Icon(Icons.Outlined.Menu,"منوی حساب")
                        }
                        DropdownMenu(showMenu,{showMenu=false}) {
                            Row(Modifier.widthIn(min=220.dp).padding(horizontal=16.dp,vertical=8.dp), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                CustomerAvatar(vm.accountName)
                                Column(Modifier.weight(1f,false)) {
                                    Text(vm.accountName.ifBlank { if(vm.loggedIn) "حساب مشتری" else "نامیران سرویس" },fontWeight=FontWeight.Bold)
                                    if(vm.loggedIn) Text(vm.mobile,style=MaterialTheme.typography.labelMedium)
                                }
                            }
                            DropdownMenuItem(text={Text(if(vm.loggedIn)"حساب و نشانی‌ها" else "ورود به ناحیه کاربری")},leadingIcon={Icon(Icons.Outlined.PersonOutline,null)},onClick={showMenu=false;vm.tab="profile"})
                            DropdownMenuItem(text={Text("راهنمای نامیران سرویس")},leadingIcon={Icon(Icons.Outlined.HelpOutline,null)},onClick={showMenu=false;showHelp=true})
                            DropdownMenuItem(text={Text("ارتباط با پشتیبانی")},leadingIcon={Icon(Icons.Outlined.SupportAgent,null)},onClick={showMenu=false;openSupport(context)})
                        }
                    }
                    Surface(shape=RoundedCornerShape(16.dp),color=Color.White){androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.namyran_symbol),"نشان نامیران سرویس",Modifier.size(52.dp).padding(4.dp))}
                    Column{Text("نامیران سرویس",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold);Text("محیط آزمایش · Stage",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                }
                IconButton(onClick={vm.setDarkMode(!vm.dark)}){Icon(if(vm.dark)Icons.Outlined.LightMode else Icons.Outlined.DarkMode,"تغییر حالت نمایش")}
            }
            if(vm.busy && vm.tab!="home")LinearProgressIndicator(Modifier.fillMaxWidth())
            if(vm.error.isNotBlank())Surface(color=MaterialTheme.colorScheme.errorContainer,shape=RoundedCornerShape(16.dp)){
                Column(Modifier.padding(16.dp)){Text(vm.error,color=MaterialTheme.colorScheme.onErrorContainer);TextButton(onClick=vm::dismissError){Text("بستن پیام")}}
            }
            if(vm.message.isNotBlank())Text(vm.message,color=MaterialTheme.colorScheme.primary)
            if(vm.pending!=null){
                Panel("ثبت قبلی نیاز به بررسی دارد","برای جلوگیری از ثبت تکراری، همان درخواست با همان شناسه دوباره بررسی می‌شود.")
                Action("بررسی نتیجه ثبت قبلی",!vm.busy&&vm.loggedIn){vm.retryPending()}
            }
            if(showHelp) AlertDialog(
                onDismissRequest={showHelp=false}, title={Text("راهنمای نامیران سرویس")},
                text={Text("از خانه خدمت و دستگاه را انتخاب کنید. پس از ورود، نشانی و زمان پیشنهادی را وارد کنید. کد پیگیری و آخرین وضعیت را در درخواست‌ها ببینید.")},
                confirmButton={TextButton(onClick={showHelp=false}){Text("بستن")}}
            )
            if(vm.config==null && vm.creating){
                Panel("دریافت اطلاعات خدمات","فهرست خدمات باید از Core محیط آزمایش دریافت شود.")
                Action("تلاش دوباره",!vm.busy){vm.refresh()}
            }else if(vm.creating&&vm.pending==null)RequestFlow(vm)
            else when(vm.tab){
                "home"->Home(vm)
                "requests"->Requests(vm)
                "profile"->Profile(vm)
                "receipt"->Receipt(vm)
                "tracking"->Tracking(vm)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable private fun CustomerAvatar(name: String) {
    Surface(shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.primaryContainer) {
        Box(Modifier.size(48.dp),contentAlignment=Alignment.Center) {
            if(name.isBlank()) Icon(Icons.Outlined.PersonOutline,null)
            else Text(name.trim().take(1),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
        }
    }
}

private fun openSupport(context: Context) {
    val intent=android.content.Intent(android.content.Intent.ACTION_DIAL,android.net.Uri.parse("tel:09375846363"))
    if(runCatching { context.startActivity(intent) }.isFailure) {
        android.widget.Toast.makeText(context,"پشتیبانی نامیران سرویس ۰۹۳۷۵۸۴۶۳۶۳",android.widget.Toast.LENGTH_LONG).show()
    }
}

@Composable private fun Home(vm: CustomerViewModel, demo: Boolean = false) {
    var query by rememberSaveable { mutableStateOf("") }
    var demoService by rememberSaveable { mutableStateOf<String?>(null) }
    val config = vm.config.takeUnless { demo }
    val catalog = config ?: vm.bundledCatalog
    val services = catalog.array("services").objects()
    val term = query.trim().replace('ي','ی').replace('ك','ک')
    val visibleServices = services.filter { service ->
        service.optString("title").replace('ي','ی').replace('ك','ک').contains(term,ignoreCase=true)
    }
    val canStart = demo || (!vm.busy && vm.pending == null)
    val deviceResults = if(term.isBlank()) emptyList() else findDeviceServices(catalog,services,term)
    if(demoService != null) AlertDialog(onDismissRequest={demoService=null},
        title={Text(demoService.orEmpty())},
        text={Text("در حساب مشتری، پس از انتخاب خدمت، دستگاه و نشانی را وارد می‌کنید و زمان مراجعه را انتخاب می‌کنید.")},
        confirmButton={TextButton(onClick={demoService=null}){Text("بازگشت")}})
    if(!demo && config == null && vm.busy) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
    }
    BrandBanner {
        Text("خدمات لوازم خانگی",color=Color(0xFF9AEAF4),style=MaterialTheme.typography.labelLarge)
        Text(if(demo) "خانه آزمایشی" else "سلام، ${vm.accountName.ifBlank { "همراه نامیران" }}",color=Color.White,
            style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
        Text("خدمت موردنیاز را انتخاب کنید و وضعیت آن را همین‌جا پیگیری کنید.",color=Color(0xFFD9E9FF))
        Button(onClick={if(demo) demoService="ثبت درخواست جدید" else vm.startRequest()},enabled=canStart,
            colors=ButtonDefaults.buttonColors(containerColor=Color.White,contentColor=NamyranColors.navy)) {
            Icon(Icons.Outlined.Add,null)
            Spacer(Modifier.width(6.dp))
            Text("ثبت درخواست جدید")
        }
    }
    OutlinedTextField(query,{query=it},modifier=Modifier.fillMaxWidth(),singleLine=true,
        label={Text("جستجو در نامیران سرویس")},leadingIcon={Icon(Icons.Outlined.Search,null)},
        trailingIcon={if(query.isNotEmpty())IconButton(onClick={query=""}){Icon(Icons.Outlined.Close,"پاک کردن جستجو")}},
        shape=RoundedCornerShape(20.dp))
    Text(if(term.isEmpty()) "همه خدمات" else "نتایج جستجو",Modifier.fillMaxWidth(),
        textAlign=TextAlign.Center,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
    deviceResults.forEach { result ->
        OutlinedCard(onClick={if(demo) demoService=result.title else vm.startRequest(result.serviceId,result.category,result.deviceId)},
            enabled=canStart,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp)) {
            Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.Search,null,tint=MaterialTheme.colorScheme.primary)
                Text(result.title,style=MaterialTheme.typography.titleSmall)
            }
        }
    }
    if(visibleServices.isEmpty() && deviceResults.isEmpty()) {
        if(term.isEmpty()) Panel("فعلاً خدمتی فعال نیست","برای بررسی دوباره، فهرست خدمات را به‌روزرسانی کنید.")
        else Panel("خدمتی پیدا نشد","نام دیگری را امتحان کنید.")
    }
    visibleServices.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            row.forEach { service ->
                ServiceTile(service,canStart,Modifier.weight(1f)) {
                    if(demo) demoService=service.optString("title") else vm.startRequest(service.getString("id"))
                }
            }
            if(row.size==1) Spacer(Modifier.weight(1f))
        }
    }
    if(term.isEmpty() && demo) {
        Panel("درخواست‌های فعال","وضعیت درخواست‌های شما پس از ثبت در این بخش نمایش داده می‌شود.")
    }
    if(term.isEmpty() && !demo) {
        if(vm.accountName.isBlank()) {
            Panel("تکمیل اطلاعات حساب","نام و نشانی را می‌توانید هنگام ثبت درخواست وارد کنید.")
        }
        val active = vm.requests.filter { it.optString("status") !in listOf("completed","cancelled") }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            Text("درخواست‌های فعال",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,
                color=MaterialTheme.colorScheme.primary)
            TextButton(onClick=vm::loadRequests,enabled=!vm.busy){Text("همه درخواست‌ها")}
        }
        if(vm.requestsLoaded && active.isEmpty()) Panel("درخواست فعالی ندارید","وضعیت درخواست جدید از همین بخش قابل پیگیری است.")
        active.take(2).forEach { RequestCard(it,vm) }
        TextButton(onClick=vm::refresh,enabled=!vm.busy){
            Icon(Icons.Outlined.Refresh,null)
            Spacer(Modifier.width(6.dp))
            Text("به‌روزرسانی خدمات")
        }
    }
}

@Composable private fun ServiceTile(service: JSONObject, enabled: Boolean, modifier: Modifier, selected: Boolean = false, onClick: () -> Unit) {
    val icon = when(service.optString("icon")) {
        "install" -> Icons.Outlined.Handyman
        "warranty" -> Icons.Outlined.VerifiedUser
        "periodic" -> Icons.Outlined.Update
        else -> Icons.Outlined.Build
    }
    val badge = when(service.optString("icon")) {
        "install" -> Color(0xFF136483) to Color(0xFF36ABC0)
        "warranty" -> Color(0xFF494397) to Color(0xFF7968CE)
        "periodic" -> Color(0xFF146556) to Color(0xFF319879)
        else -> Color(0xFF17489C) to Color(0xFF487FE3)
    }
    OutlinedCard(onClick=onClick,enabled=enabled,modifier=modifier,shape=RoundedCornerShape(24.dp),
        colors=CardDefaults.outlinedCardColors(containerColor=if(selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().heightIn(min=116.dp).padding(12.dp),
            horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(9.dp)) {
            Box(Modifier.size(54.dp).background(Brush.linearGradient(listOf(badge.first,badge.second)),RoundedCornerShape(18.dp)),
                contentAlignment=Alignment.Center) { Icon(icon,null,Modifier.size(29.dp),tint=Color.White) }
            Text(service.optString("title"),style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.Bold,textAlign=TextAlign.Center)
            if(selected) Icon(Icons.Outlined.CheckCircle,"انتخاب‌شده",Modifier.size(18.dp),tint=MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable private fun BrandBanner(centered: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Box(Modifier.fillMaxWidth().clip(shape).background(Brush.linearGradient(
        listOf(Color(0xFF102654),Color(0xFF1359BA),Color(0xFF087B91))))) {
        Canvas(Modifier.matchParentSize()) {
            val center = Offset(size.width*.07f,size.height*.12f)
            listOf(.24f,.36f,.49f).forEach { radius ->
                drawCircle(Color.White.copy(alpha=.12f),size.width*radius,center,style=Stroke(width=1.dp.toPx()))
            }
            drawCircle(Color(0xFF75E8F0).copy(alpha=.16f),size.width*.16f,center)
        }
        Column(Modifier.fillMaxWidth().padding(20.dp),horizontalAlignment=if(centered) Alignment.CenterHorizontally else Alignment.Start,
            verticalArrangement=Arrangement.spacedBy(9.dp),content=content)
    }
}

@Composable private fun Requests(vm: CustomerViewModel) {
    var query by rememberSaveable{mutableStateOf("")}
    var filter by rememberSaveable{mutableIntStateOf(0)}
    SectionTitle("درخواست‌های من",vm.requests.size.toString())
    Field("جستجو با کد پیگیری",query,{query=normalizedDigits(it).take(40)})
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        listOf("همه","فعال","تکمیل‌شده","لغوشده").forEachIndexed{index,title->
            FilterChip(selected=filter==index,onClick={filter=index},label={Text(title)})
        }
    }
    OutlinedButton(onClick=vm::loadRequests,enabled=!vm.busy,modifier=Modifier.fillMaxWidth()){
        Icon(Icons.Outlined.Refresh,null);Spacer(Modifier.width(8.dp));Text("به‌روزرسانی وضعیت")
    }
    val visible=vm.requests.filter {
        val status=it.optString("status")
        val matches=when(filter){1->status !in setOf("completed","cancelled");2->status=="completed";3->status=="cancelled";else->true}
        matches && it.optString("tracking_code").contains(query.trim())
    }
    when {
        vm.busy && !vm.requestsLoaded -> LinearProgressIndicator(Modifier.fillMaxWidth())
        !vm.requestsLoaded -> Panel("درخواست‌ها هنوز دریافت نشده‌اند","برای دریافت اطلاعات حساب، به‌روزرسانی را بزنید.")
        visible.isEmpty() -> Panel("درخواستی پیدا نشد","فیلتر یا کد پیگیری را تغییر دهید.")
        else -> visible.forEach{RequestCard(it,vm)}
    }
}

@Composable private fun RequestCard(row:JSONObject,vm:CustomerViewModel){
    Card(onClick={if(!vm.busy)vm.track(row.optString("tracking_code"))},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(22.dp)){
        Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                Text(row.optString("service_label"),Modifier.weight(1f),fontWeight=FontWeight.Bold)
                val attention=row.optBoolean("needs_customer_action")
                Surface(shape=RoundedCornerShape(20.dp),
                    color=if(attention) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                    contentColor=if(attention) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer) {
                    Text(row.optString("status_label"),Modifier.padding(horizontal=10.dp,vertical=6.dp),style=MaterialTheme.typography.labelMedium)
                }
            }
            Text(listOf(row.optString("device"),row.optString("brand")).filter{it.isNotBlank()}.joinToString(" · "))
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("پیگیری ${row.optString("tracking_code")}",style=MaterialTheme.typography.labelMedium);Text(row.optString("visit_date_jalali"),style=MaterialTheme.typography.labelMedium)}
            if(row.optBoolean("needs_customer_action"))Text("این درخواست نیاز به اقدام شما دارد.",color=MaterialTheme.colorScheme.error)
        }
    }
}

@Composable private fun Profile(vm: CustomerViewModel) {
    val context = LocalContext.current
    var saved by remember(vm.mobile) { mutableStateOf(vm.localProfile()) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var fullName by rememberSaveable { mutableStateOf("") }
    var birthday by rememberSaveable { mutableStateOf("") }
    var photo by rememberSaveable { mutableStateOf("") }
    var feedback by remember { mutableStateOf("") }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null) {
            val retained=runCatching {
                context.contentResolver.takePersistableUriPermission(uri,android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.isSuccess
            if(retained) photo=uri.toString() else feedback="دسترسی به این عکس ممکن نبود."
        }
    }
    SectionTitle("حساب من")
    val displayName=saved.optString("name").ifBlank { vm.accountName }
    Card(modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(26.dp),
        colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally,
            verticalArrangement=Arrangement.spacedBy(10.dp)) {
            ProfilePhoto(displayName,saved.optString("photo"))
            Text(displayName.ifBlank { "حساب مشتری" },style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            Text("شماره تأییدشده · " + vm.mobile,style=MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick={
                fullName=displayName;birthday=saved.optString("birthday");photo=saved.optString("photo");editing=true
            }) { Icon(Icons.Outlined.Edit,null);Spacer(Modifier.width(8.dp));Text("ویرایش پروفایل") }
        }
    }
    if(editing) {
        Panel("اطلاعات پروفایل","این اطلاعات و عکس روی همین گوشی ذخیره می‌شوند.")
        Field("نام و نام خانوادگی",fullName,{fullName=it.take(80)})
        Field("تاریخ تولد · اختیاری",birthday,{birthday=it.take(20)})
        ProfilePhoto(fullName,photo)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick={picker.launch(arrayOf("image/*"))}){Text("انتخاب عکس")}
            if(photo.isNotEmpty()) TextButton(onClick={photo=""}){Text("حذف عکس")}
        }
        Action("ذخیره پروفایل",fullName.trim().isNotBlank()) {
            if(vm.saveLocalProfile(fullName,birthday,photo)){saved=vm.localProfile();editing=false;feedback="پروفایل ذخیره شد."}
            else feedback="ذخیره پروفایل انجام نشد."
        }
        TextButton(onClick={editing=false}){Text("انصراف")}
    }
    if(feedback.isNotBlank()) Text(feedback,color=MaterialTheme.colorScheme.primary)
    SectionTitle("نشانی‌ها و درخواست‌ها")
    Action("مشاهده درخواست‌های من",!vm.busy,vm::loadRequests)
    OutlinedButton(onClick=vm::loadAddresses,enabled=!vm.busy,modifier=Modifier.fillMaxWidth()){Text("دریافت نشانی‌های ذخیره‌شده")}
    vm.addresses.forEach { row ->
        val address=runCatching{JSONObject(row.optString("data_json"))}.getOrElse{JSONObject()}
        Panel(when(row.optString("label")){"home"->"خانه";"work"->"محل کار";else->"نشانی دیگر"},address.optString("address"))
    }
    SectionTitle("تنظیمات")
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
        Text("حالت تاریک");Switch(vm.dark,vm::setDarkMode)
    }
    OutlinedButton(onClick={openSupport(context)},modifier=Modifier.fillMaxWidth()){Text("ارتباط با پشتیبانی")}
    OutlinedButton(onClick=vm::logout,enabled=!vm.busy,modifier=Modifier.fillMaxWidth()){Text("خروج از حساب")}
}

@Composable private fun ProfilePhoto(name: String, photo: String) {
    val context=LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(null,photo) {
        value=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                if(photo.isBlank()) return@runCatching null
                val resolver=context.contentResolver
                val uri=android.net.Uri.parse(photo)
                val bounds=android.graphics.BitmapFactory.Options().apply{inJustDecodeBounds=true}
                resolver.openInputStream(uri)?.use{android.graphics.BitmapFactory.decodeStream(it,null,bounds)}
                var sample=1
                while(maxOf(bounds.outWidth,bounds.outHeight)/sample>384) sample*=2
                resolver.openInputStream(uri)?.use {
                    android.graphics.BitmapFactory.decodeStream(it,null,android.graphics.BitmapFactory.Options().apply{inSampleSize=sample})
                }
            }.getOrNull()
        }
    }
    val image=bitmap
    if(image==null) CustomerAvatar(name)
    else androidx.compose.foundation.Image(image.asImageBitmap(),"عکس پروفایل",
        Modifier.size(72.dp).clip(RoundedCornerShape(25.dp)),contentScale=androidx.compose.ui.layout.ContentScale.Crop)
}

private fun isDemoMobile(number: String): Boolean =
    BuildConfig.DEBUG && BuildConfig.API_BASE_URL == "https://stage.namyranservice.ir/wp-json/" && number == "09022896363"

@Composable private fun LoginScreen(vm: CustomerViewModel) {
    var showDemo by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    BackHandler(showDemo) { showDemo = false }
    Scaffold(containerColor=MaterialTheme.colorScheme.background) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).imePadding(),contentAlignment=Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max=540.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal=20.dp,vertical=20.dp),
                verticalArrangement=Arrangement.spacedBy(14.dp)
            ) {
                if(showDemo) {
                    Home(vm,demo=true)
                    Action("بازگشت به ورود"){showDemo=false}
                } else {
                    LoginBrand(false)
                    if(vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if(vm.error.isNotBlank()) Surface(shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.errorContainer) {
                        Text(vm.error,Modifier.fillMaxWidth().padding(14.dp),color=MaterialTheme.colorScheme.onErrorContainer)
                    }
                    if(vm.hasSavedSession) {
                        Panel("بازیابی حساب","در حال بررسی نشست قبلی شما هستیم.")
                        if(!vm.busy) Action("بررسی دوباره نشست",onClick=vm::refresh)
                    } else Surface(
                        modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(26.dp),
                        color=MaterialTheme.colorScheme.surface,tonalElevation=2.dp
                    ) {
                        Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                            LoginForm(vm) { showDemo=true }
                        }
                    }
                    TextButton(onClick={openSupport(context)},modifier=Modifier.align(Alignment.CenterHorizontally)) {
                        Icon(Icons.Outlined.SupportAgent,null,Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("راهنمای ورود و پشتیبانی")
                    }
                }
            }
        }
    }
}

@Composable private fun LoginBrand(demo: Boolean) {
    BrandBanner(centered=true) {
        Surface(shape=RoundedCornerShape(20.dp),color=Color.White) {
            androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.namyran_symbol),
                "نشان نامیران سرویس",Modifier.size(58.dp).padding(7.dp))
        }
        Text("نامیران سرویس",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,color=Color.White,textAlign=TextAlign.Center)
        Text(if(demo) "پیش‌نمایش حساب مشتری" else "خانه، در دست متخصص",
            style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold,color=Color.White,textAlign=TextAlign.Center)
        Text("تعمیرات، نصب و گارانتی لوازم خانگی",style=MaterialTheme.typography.bodyMedium,color=Color(0xFFE2F2FF),textAlign=TextAlign.Center)
    }
}

@Composable private fun LoginForm(vm: CustomerViewModel,onDemo: () -> Unit) {
    var phone by rememberSaveable{mutableStateOf(vm.mobile.takeIf { vm.otpSent } ?: vm.rememberedMobile)}
    var rememberNumber by rememberSaveable{mutableStateOf(vm.rememberedMobile.isNotBlank())}
    var code by remember{mutableStateOf("")}
    var demoChallenge by rememberSaveable{mutableStateOf(false)}
    var wrongDemoCode by remember{mutableStateOf(false)}
    var listening by remember { mutableStateOf(false) }
    var now by remember{mutableLongStateOf(System.currentTimeMillis())}
    val listenForCode=rememberSmsCodeConsent { if(!demoChallenge && code.isBlank()) code=it }
    LaunchedEffect(vm.resendAt){while(System.currentTimeMillis()<vm.resendAt){now=System.currentTimeMillis();kotlinx.coroutines.delay(1000)};now=System.currentTimeMillis()}
    val codeStep=vm.otpSent || demoChallenge
    val enabled=!vm.busy && !listening
    Text(if(codeStep)"تأیید شماره موبایل" else "ورود به حساب مشتری",
        style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
    Text(
        when {
            demoChallenge -> "کد آزمایشی را وارد کنید."
            vm.otpSent -> "کد پیامک‌شده به ${vm.mobile} را وارد کنید."
            else -> "برای شروع، شماره موبایل خود را وارد کنید."
        },
        color=MaterialTheme.colorScheme.onSurfaceVariant
    )
    if(codeStep) {
        Field("کد چهاررقمی",code,{code=normalizedDigits(it).filter { digit -> digit in '0'..'9' }.take(4);wrongDemoCode=false},
            enabled,KeyboardType.NumberPassword)
        if(wrongDemoCode) Text("کد آزمایشی درست نیست.",color=MaterialTheme.colorScheme.error)
        Action("تأیید و ورود",enabled&&code.length==4){
            if(demoChallenge) {
                if(isDemoMobile(phone) && code=="1111") onDemo() else wrongDemoCode=true
            } else vm.verifyOtp(code)
        }
        TextButton(onClick={demoChallenge=false;code="";wrongDemoCode=false;vm.editOtpMobile()},enabled=enabled){
            Text("ویرایش شماره موبایل")
        }
    } else {
        Field("شماره موبایل",phone,{phone=normalizedDigits(it).filter { digit -> digit in '0'..'9' }.take(11)},enabled,KeyboardType.Phone)
        Row(Modifier.fillMaxWidth().clickable(enabled=enabled){rememberNumber=!rememberNumber;if(!rememberNumber)vm.rememberMobile(false)},
            verticalAlignment=Alignment.CenterVertically) {
            Checkbox(rememberNumber,null,enabled=enabled)
            Text("شماره‌ام را به خاطر بسپار",Modifier.padding(10.dp))
        }
    }
    val remaining=((vm.resendAt-now+999)/1000).coerceAtLeast(0)
    if(!demoChallenge) {
        val label=if(remaining>0 && !isDemoMobile(phone)) "ارسال دوباره در $remaining ثانیه"
            else if(vm.otpSent) "ارسال دوباره کد" else "دریافت کد ورود"
        val canSend=enabled && phone.matches(Regex("09[0-9]{9}")) && (remaining==0L || isDemoMobile(phone))
        Action(label,canSend) {
            code=""
            if(isDemoMobile(phone)) demoChallenge=true
            else {
                listening=true
                listenForCode {
                    vm.sendOtp(phone,rememberNumber)
                    listening=false
                }
            }
        }
    }
}

@Composable private fun RequestFlow(vm:CustomerViewModel){
    val config=vm.config!!;val item=vm.item;val context=LocalContext.current
    val headings=listOf("انتخاب خدمت و دستگاه","نشانی و شرح درخواست","زمان پیشنهادی مراجعه","بازبینی و ثبت")
    ExpressiveRequestHeader(vm.step,headings[vm.step]) { vm.saveDraft();vm.creating=false }
    if(item.optString("service_type")=="warranty"){
        BrandBanner(centered=true) {
            Icon(Icons.Outlined.VerifiedUser,null,tint=Color.White,modifier=Modifier.size(40.dp))
            Text("خدمات گارانتی",color=Color.White,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
            Text("پیگیری گارانتی در مرکز گارانتی نامیران",color=Color(0xFFD9E9FF),textAlign=TextAlign.Center)
        }
        Action("ورود به مرکز گارانتی Stage",!vm.busy){
            val url=config.optString("warrantyHubUrl")
            val parsed=runCatching{java.net.URI(url)}.getOrNull()
            if(parsed?.scheme=="https"&&parsed.host=="stage.namyranservice.ir"&&parsed.userInfo==null&&parsed.port==-1)context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse(url)))
        }
        OutlinedButton(onClick={vm.changeItem("service_type","")},modifier=Modifier.fillMaxWidth()){Text("انتخاب خدمت دیگر")}
        return
    }
    when(vm.step){
        0->{
            Text("نوع خدمت",Modifier.fillMaxWidth(),textAlign=TextAlign.Right,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.ExtraBold)
            config.array("services").objects().chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    row.forEach { service ->
                        val id=service.optString("id")
                        ExpressiveServiceTile(service,!vm.busy,selected=item.optString("service_type")==id,modifier=Modifier.weight(1f)) {
                            if(item.optString("service_type")!=id) vm.changeItem("service_type",id)
                        }
                    }
                    if(row.size==1) Spacer(Modifier.weight(1f))
                }
            }
            val mode=item.optString("service_type")
            val category=item.optString("category")
            val brand=item.optString("brand")
            val categories=config.optJSONObject("categories")?:JSONObject()
            if(mode.isNotBlank()){
                val keys=categories.keys().asSequence().filter { key ->
                    categories.array(key).objects().any{supportsService(it,mode)}
                }.toList()
                GuidedField("گروه دستگاه","دسته کلی محصول را انتخاب کنید. برای نمونه، تلویزیون در گروه صوتی و تصویری قرار دارد.",category.isBlank()){
                    ExpressivePicker("گروه دستگاه",keys.map{it to it},category,!vm.busy){
                        if(it!=category) vm.changeItem("category",it)
                    }
                }
                if(category in keys){
                    val devices=categories.array(category).objects().filter{supportsService(it,mode)}
                    GuidedField("دستگاه","نام دقیق دستگاه را از این گروه انتخاب کنید.",item.optString("device").isBlank()){
                        ExpressivePicker("دستگاه",devices.map{it.optString("id") to it.optString("title")},item.optString("device"),!vm.busy){id->
                            if(id!=item.optString("device")) devices.firstOrNull{it.optString("id")==id}?.let(vm::chooseDevice)
                        }
                    }
                    val device=devices.firstOrNull{it.optString("id")==item.optString("device")}
                    if(device!=null){
                        val subtypes=device.array("subtypes").objects()
                        val subtype=item.optString("catalog_subtype_id")
                        if(subtypes.isNotEmpty()) GuidedField("نوع دستگاه","اگر دستگاه چند نوع دارد، نوع درج‌شده روی برچسب یا دفترچه را انتخاب کنید.",subtype.isBlank()){
                            ExpressivePicker("نوع دستگاه",subtypes.map{it.optString("id") to it.optString("title")},subtype,!vm.busy){
                                if(it!=subtype) vm.changeItem("catalog_subtype_id",it)
                            }
                        }
                        if(subtypes.isEmpty() || subtypes.any{it.optString("id")==subtype}){
                            val brands=config.array(if(mode=="install")"installBrands" else "brands").strings()
                            GuidedField("برند","نام سازنده معمولاً روی بدنه، برچسب پشت دستگاه یا جعبه نوشته شده است.",brand.isBlank()){
                                ExpressivePicker("برند",brands.map{it to it},brand,!vm.busy){
                                    if(it!=brand) vm.changeItem("brand",it)
                                }
                            }
                            if(brand in brands){
                                val custom=NativeRules.isCustomBrand(brand)
                                if(custom) Field("نام برند دستگاه",item.optString("custom_brand"),{vm.changeItem("custom_brand",it)},!vm.busy)
                                if(!custom || item.optString("custom_brand").trim().isNotBlank()){
                                    GuidedField("مدل دستگاه","کد مدل را کنار عبارت Model یا Model No روی برچسب دستگاه پیدا کنید. وارد کردن مدل اختیاری است.",vm.model.isBlank()){
                                        ExpressiveModelField(vm.model,!vm.busy){vm.model=it;vm.saveDraft()}
                                    }
                                    if(vm.isTv()) TelevisionInstallation(vm,config.optJSONObject("tvInstall")?:JSONObject())
                                    val extras=device.array("services").objects().filter{mode in it.optString("mode").split(',')}
                                    CrystalOptions("خدمات دستگاه",extras,item.optString("catalog_service_ids").split(',').filter{it.isNotBlank()},!vm.busy){ids->
                                        val versions=JSONObject()
                                        extras.filter{it.optString("id") in ids}.forEach{versions.put(it.optString("id"),it.optInt("row_version"))}
                                        vm.item=JSONObject(vm.item.toString()).put("catalog_service_ids",ids.joinToString(",")).put("catalog_service_versions",versions)
                                        vm.saveDraft()
                                    }
                                }
                            }
                        }
                    }
                }
            }
            vm.items.forEachIndexed{index,saved->Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("${index+1}. ${saved.optString("device")} · ${saved.optString("brand")}",Modifier.weight(1f));IconButton(onClick={vm.removeItem(index)}){Icon(Icons.Outlined.DeleteOutline,"حذف دستگاه")}}}
            if(brand.isNotBlank()) ExpressiveAddDeviceAction(!vm.busy,vm::addItem)
        }
        1->{
            Panel("اطلاعات مشتری","شماره همراه تأییدشده · ${vm.mobile}")
            Field("نام و نام خانوادگی",vm.name,{vm.name=it;vm.saveDraft()},!vm.busy)
            val cities=config.optJSONObject("customerMobile")?.array("cities")?.objects().orEmpty()
            if(cities.isEmpty())Panel("شهر فعالی دریافت نشد","محدوده خدمات باید توسط مدیریت Core فعال شود.")
            ExpressivePicker("شهر محل خدمت",cities.map{it.optString("id") to it.optString("title")},vm.city,!vm.busy){if(vm.city!=it){vm.city=it;vm.area="";vm.saveDraft()}}
            val groups=cities.find{it.optString("id")==vm.city}?.array("groups")?.objects().orEmpty()
            val areas=groups.flatMap{g->g.array("areas").objects().map{a->a.optString("title") to (g.optString("title")+" · "+a.optString("title"))}}
            if(areas.isNotEmpty())ExpressivePicker("محله / محدوده",areas,vm.area,!vm.busy){vm.area=it;vm.saveDraft()}
            Field("نشانی دقیق، پلاک و واحد",vm.address,{vm.address=it;vm.saveDraft()},!vm.busy,lines=3)
            Field("شرح مشکل یا نیاز به خدمت",vm.description,{vm.description=it;vm.saveDraft()},!vm.busy,lines=4)
            ImageAttachments(vm)
            LocationButton(vm)
            if(vm.latitude.isNotBlank()) {
                Text("موقعیت ثبت شد · دقت ${vm.accuracy.ifBlank{"نامشخص"}} متر",style=MaterialTheme.typography.bodySmall)
                var addressLabel by rememberSaveable { mutableStateOf("home") }
                Picker("عنوان نشانی",listOf("home" to "خانه","work" to "محل کار","other" to "دیگر"),addressLabel,!vm.busy){addressLabel=it}
                OutlinedButton(onClick={vm.saveRequestAddress(addressLabel)},enabled=!vm.busy&&vm.address.trim().length>=8,modifier=Modifier.fillMaxWidth()){
                    Text("ذخیره نشانی در حساب من")
                }
            }
            OutlinedButton(onClick=vm::loadAddresses,enabled=!vm.busy,modifier=Modifier.fillMaxWidth()){Text("انتخاب از نشانی‌های من")}
            vm.addresses.forEach{row->val a=try{JSONObject(row.optString("data_json"))}catch(_:Exception){JSONObject()};OutlinedButton(onClick={vm.address=a.optString("address");vm.latitude=a.optString("latitude");vm.longitude=a.optString("longitude");vm.accuracy="";vm.saveDraft()},modifier=Modifier.fillMaxWidth()){Text(a.optString("address"))}}
        }
        2->{
            Panel("زمان پیشنهادی شما","این بازه برای بررسی و هماهنگی مراجعه ثبت می‌شود؛ تأیید نهایی پس از بررسی نامیران انجام می‌شود.")
            PersianVisitDate(config.optString("serverNow"),vm.date,!vm.busy,vm::getSchedule)
            if(vm.busy && vm.schedule==null) LinearProgressIndicator(Modifier.fillMaxWidth())
            if(!vm.busy && vm.date.isNotBlank() && vm.schedule==null)
                OutlinedButton(onClick={vm.getSchedule(vm.date)}){Text("دریافت دوباره زمان‌ها")}
            vm.schedule?.let{s->Text(s.optString("dateJalali"),fontWeight=FontWeight.Bold);s.array("slots").objects().forEach{slot->
                val value=slot.optString("value");OutlinedButton(onClick={vm.slot=value;vm.saveDraft()},enabled=slot.optBoolean("available")&&!vm.busy,modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.outlinedButtonColors(containerColor=if(vm.slot==value)MaterialTheme.colorScheme.primaryContainer else Color.Transparent)){
                    Text(value+if(slot.optBoolean("available"))"" else " · قابل انتخاب نیست")
                }
            }}
        }
        3->{
            SectionTitle("بازبینی درخواست")
            val devices=(vm.items+vm.currentItemForReview()).map { device ->
                val service=config.array("services").objects().firstOrNull{it.optString("id")==device.optString("service_type")}
                val details=config.optJSONObject("categories")?.array(device.optString("category"))?.objects()
                    ?.firstOrNull{it.optString("id")==device.optString("device")}
                val extras=device.optString("catalog_service_ids").split(',')
                val tvExtras=device.array("tv_services").strings()
                val tv=config.optJSONObject("tvInstall")
                val extraTitles=details?.array("services")?.objects().orEmpty().filter{it.optString("id") in extras}.map{it.optString("title")}
                val tvTitles=tv?.array("services")?.objects().orEmpty().filter{it.optString("id") in tvExtras}.map{it.optString("title")}
                val packageName=tv?.array("packages")?.objects()?.firstOrNull{it.optString("id")==device.optString("installation_package")}?.optString("title").orEmpty()
                listOf(service?.optString("title").orEmpty(),device.optString("category"),device.optString("device"),
                    device.optString("brand"),device.optString("device_model"),packageName,device.optString("tv_size"),
                    (extraTitles+tvTitles).filter(String::isNotBlank).joinToString("، ")).filter(String::isNotBlank).joinToString(" · ")
            }
            ReviewSection("خدمت و دستگاه",devices.joinToString("\n"),!vm.busy){vm.step=0}
            val city=config.optJSONObject("customerMobile")?.array("cities")?.objects()
                ?.firstOrNull{it.optString("id")==vm.city}?.optString("title").orEmpty()
            val place=listOf(city,vm.area,vm.address).filter(String::isNotBlank).joinToString("، ")
            val location=if(vm.latitude.isNotBlank() && vm.longitude.isNotBlank()) "موقعیت ثبت شده" else ""
            ReviewSection("اطلاعات مشتری و نشانی",listOf(vm.name,vm.mobile,place,location,vm.description)
                .filter(String::isNotBlank).joinToString("\n"),!vm.busy){vm.step=1}
            ImageAttachments(vm,review=true)
            ReviewSection("زمان پیشنهادی",(vm.schedule?.optString("dateJalali")?:vm.date)+" · "+vm.slot,!vm.busy){vm.step=2}
            Row(verticalAlignment=Alignment.CenterVertically){Checkbox(vm.terms,{vm.terms=it},enabled=!vm.busy);Text("صحت اطلاعات و ثبت زمان پیشنهادی برای بررسی و هماهنگی را می‌پذیرم.",style=MaterialTheme.typography.bodyMedium)}
            Action("ثبت نهایی درخواست",!vm.busy&&vm.terms,vm::submit)
        }
    }
    if(vm.step<3)ExpressiveRequestNavigation(!vm.busy,vm::next) { if(vm.step>0)vm.step-- else vm.creating=false;vm.saveDraft() }
    else OutlinedButton(onClick={if(vm.step>0)vm.step-- else vm.creating=false;vm.saveDraft()},enabled=!vm.busy,modifier=Modifier.fillMaxWidth()){Text("بازگشت")}
}

@Composable private fun ReviewSection(title: String, body: String, enabled: Boolean, edit: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text(title,Modifier.weight(1f),fontWeight=FontWeight.Bold)
                TextButton(onClick=edit,enabled=enabled){Text("ویرایش")}
            }
            Text(body,style=MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun PersianVisitDate(serverNow: String, selected: String, enabled: Boolean, onSelect: (String) -> Unit) {
    val today=runCatching{LocalDate.parse(serverNow.take(10))}.getOrNull()
    var open by rememberSaveable{mutableStateOf(false)}
    val selectedDay=runCatching{LocalDate.parse(selected)}.getOrNull()
    val formatter=remember {
        SimpleDateFormat("EEEE، d MMMM y",ULocale("fa_IR@calendar=persian")).apply {
            timeZone=TimeZone.getTimeZone("UTC")
        }
    }
    fun label(day: LocalDate): String = formatter.format(Date(day.toEpochDay()*86_400_000L))
    Action(selectedDay?.let{label(it)} ?: "انتخاب تاریخ مراجعه",enabled && today!=null){open=true}
    if(open && today!=null) {
        var week by rememberSaveable(selected){mutableIntStateOf(
            selectedDay?.let{((it.toEpochDay()-today.toEpochDay())/7).coerceAtLeast(0).toInt()} ?: 0)}
        ModalBottomSheet(onDismissRequest={open=false}) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("تقویم مراجعه",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                Text("پس از انتخاب روز، بازه‌های قابل انتخاب از Core دریافت می‌شوند.")
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    TextButton(onClick={week--},enabled=week>0){Text("هفته قبل")}
                    TextButton(onClick={week++}){Text("هفته بعد")}
                }
                repeat(7){index->
                    val day=today.plusDays(week.toLong()*7+index)
                    OutlinedButton(onClick={open=false;onSelect(day.toString())},modifier=Modifier.fillMaxWidth(),
                        colors=ButtonDefaults.outlinedButtonColors(containerColor=if(day==selectedDay)MaterialTheme.colorScheme.primaryContainer else Color.Transparent)){
                        Text(label(day))
                    }
                }
            }
        }
    }
}

@Composable private fun ImageAttachments(vm:CustomerViewModel,review:Boolean=false){
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments(),vm::addImages)
    val maxFiles=ir.namyranservice.customer.domain.ImageRules.maxFiles(vm.config?.optInt("maxFiles",3)?:3)
    val maxMB=(vm.config?.optInt("maxUploadMB",5)?:5).coerceIn(1,20)
    SectionTitle("تصاویر درخواست", "${vm.images.size} از $maxFiles")
    if(!review){
        Text("JPEG، PNG یا WebP · هر تصویر حداکثر $maxMB مگابایت",style=MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick={picker.launch(arrayOf("image/jpeg","image/png","image/webp"))},enabled=!vm.busy&&vm.images.size<maxFiles,modifier=Modifier.fillMaxWidth()){
            Icon(Icons.Outlined.AddPhotoAlternate,null);Spacer(Modifier.width(8.dp));Text("انتخاب تصویر")
        }
    }
    vm.images.forEachIndexed { index,meta ->
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            val bitmap by produceState<android.graphics.Bitmap?>(null,meta.optString("id")){
                value=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){runCatching{vm.imagePreview(meta)}.getOrNull()}
            }
            if(bitmap!=null)androidx.compose.foundation.Image(bitmap!!.asImageBitmap(),"پیش‌نمایش تصویر ${index+1}",Modifier.size(64.dp)) else Icon(Icons.Outlined.Image,null)
            Text("تصویر ${index+1} · ${meta.optLong("size")/1024} کیلوبایت",Modifier.weight(1f).padding(8.dp))
            if(!review)IconButton(onClick={vm.removeImage(meta.getString("id"))},enabled=!vm.busy){Icon(Icons.Outlined.DeleteOutline,"حذف تصویر ${index+1}")}
        }
    }
}

@Composable private fun LocationButton(vm:CustomerViewModel){
    val context=LocalContext.current
    var status by remember{mutableStateOf("")}
    val locate={
        val manager=context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val provider=listOf(LocationManager.GPS_PROVIDER,LocationManager.NETWORK_PROVIDER).firstOrNull{manager.isProviderEnabled(it)}
        if(provider==null)status="موقعیت‌یابی دستگاه را روشن کنید."
        else if(android.os.Build.VERSION.SDK_INT>=30&&context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED){
            status="در حال دریافت موقعیت…"
            manager.getCurrentLocation(provider,CancellationSignal(),context.mainExecutor){point->
                if(point==null)status="موقعیت دریافت نشد؛ دوباره تلاش کنید."
                else{vm.latitude=point.latitude.toString();vm.longitude=point.longitude.toString();vm.accuracy=point.accuracy.toString();vm.saveDraft();status="موقعیت محل ثبت شد."}
            }
        }else if(context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED){
            status="در حال دریافت موقعیت…"
            val listener=object:android.location.LocationListener{
                override fun onLocationChanged(point:android.location.Location){manager.removeUpdates(this);vm.latitude=point.latitude.toString();vm.longitude=point.longitude.toString();vm.accuracy=point.accuracy.toString();vm.saveDraft();status="موقعیت محل ثبت شد."}
                @Deprecated("Legacy callback") override fun onStatusChanged(provider:String?,state:Int,extras:Bundle?){}
                override fun onProviderEnabled(provider:String){}
                override fun onProviderDisabled(provider:String){manager.removeUpdates(this);status="موقعیت‌یابی خاموش است."}
            }
            @Suppress("DEPRECATION")
            manager.requestSingleUpdate(provider,listener,android.os.Looper.getMainLooper())
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({manager.removeUpdates(listener);if(status=="در حال دریافت موقعیت…")status="موقعیت دریافت نشد؛ دوباره تلاش کنید."},30000)
        }else status="مجوز موقعیت دقیق لازم است."
    }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){grants->if(grants[Manifest.permission.ACCESS_FINE_LOCATION]==true)locate() else status="مجوز موقعیت دقیق داده نشد؛ برای نشانی الزامی، از نشانی ذخیره‌شده استفاده کنید."}
    OutlinedButton(onClick={if(context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED)locate() else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))},enabled=!vm.busy,modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.MyLocation,null);Spacer(Modifier.width(8.dp));Text("ثبت موقعیت فعلی محل خدمت")}
    if(status.isNotBlank())Text(status,style=MaterialTheme.typography.bodySmall)
}

@Composable private fun Receipt(vm:CustomerViewModel){
    val r=vm.receipt?:return
    Surface(color=MaterialTheme.colorScheme.primaryContainer,shape=RoundedCornerShape(28.dp)){
        Column(Modifier.fillMaxWidth().padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp),horizontalAlignment=Alignment.CenterHorizontally){Icon(Icons.Outlined.CheckCircle,null,Modifier.size(56.dp));Text("درخواست شما ثبت شد",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text(r.optString("tracking_code"),fontSize=28.sp,fontWeight=FontWeight.Bold);Text(r.optString("status_label"))}
    }
    Text(if(vm.receiptInAccount)"همین کد پیگیری در حساب شما بازخوانی و تأیید شد." else "ثبت توسط Core تأیید شد؛ برای بررسی نمایش در حساب، درخواست‌ها را به‌روزرسانی کنید.")
    if(r.optInt("local_image_count")>0)Panel("وضعیت تصاویر",if(r.optBoolean("local_images_confirmed"))"Core ذخیره ${r.optInt("local_image_count")} تصویر را تأیید کرد." else "درخواست ثبت شده، اما ذخیره همه تصاویر تأیید نشد. با همین کد پیگیری از پشتیبانی پیگیری کنید؛ درخواست تازه ثبت نکنید.")
    val service=vm.config?.array("services")?.objects()?.firstOrNull{it.optString("id")==r.optString("local_service_type")}?.optString("title").orEmpty()
    val device=r.optString("local_device")
    val date=r.optString("local_proposed_date")
    val slot=r.optString("local_proposed_slot")
    if(service.isNotBlank() || device.isNotBlank()) Panel("خدمت و دستگاه",listOf(service,device).filter(String::isNotBlank).joinToString(" · "))
    if(date.isNotBlank()) Panel("زمان پیشنهادی",listOf(date,slot).filter(String::isNotBlank).joinToString(" · "))
    Action("پیگیری درخواست",!vm.busy){vm.track(r.optString("tracking_code"))}
    OutlinedButton(onClick={vm.tab="home"},enabled=!vm.busy,modifier=Modifier.fillMaxWidth()){Text("بازگشت به خانه")}
}

@Composable private fun Tracking(vm:CustomerViewModel){
    SectionTitle("جزئیات درخواست")
    val data=vm.tracking?:return
    val r=data.optJSONObject("request")?:data
    Panel("کد پیگیری",r.optString("tracking_code"))
    listOf("status_label" to "وضعیت","device" to "دستگاه","brand" to "برند","visit_date_jalali" to "تاریخ مراجعه","address" to "نشانی").forEach{(key,label)->if(r.optString(key).isNotBlank())Panel(label,r.optString(key))}
    val steps=r.array("workflow").objects()
    steps.forEach{s->Text(listOf(s.optString("title"),s.optString("label"),s.optString("status")).filter{it.isNotBlank()}.joinToString(" · "))}
    OutlinedButton(onClick={vm.tab="requests"},modifier=Modifier.fillMaxWidth()){Text("بازگشت به درخواست‌ها")}
}

@Composable private fun SectionTitle(title:String,count:String=""){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Text(title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);if(count.isNotBlank())Text(count,color=MaterialTheme.colorScheme.primary)}}
@Composable private fun Panel(title:String,body:String){Surface(shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surface,modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text(title,fontWeight=FontWeight.Bold);if(body.isNotBlank())Text(body,color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodyMedium)}}}
@Composable private fun Action(title:String,enabled:Boolean=true,onClick:()->Unit){Button(onClick=onClick,enabled=enabled,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp),shape=RoundedCornerShape(18.dp)){Text(title,fontWeight=FontWeight.Bold)}}
@Composable private fun Field(label:String,value:String,onChange:(String)->Unit,enabled:Boolean=true,type:KeyboardType=KeyboardType.Text,lines:Int=1){OutlinedTextField(value,onChange,label={Text(label)},enabled=enabled,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),keyboardOptions=KeyboardOptions(keyboardType=type),singleLine=lines==1,minLines=lines)}
@Composable private fun Picker(label:String,options:List<Pair<String,String>>,value:String,enabled:Boolean=true,onChoose:(String)->Unit){
    var open by remember{mutableStateOf(false)};var search by remember{mutableStateOf("")}
    OutlinedButton(onClick={search="";open=true},enabled=enabled&&options.isNotEmpty(),shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth().heightIn(min=58.dp)){
        Column(Modifier.weight(1f)){Text(label,style=MaterialTheme.typography.labelSmall);Text(options.find{it.first==value}?.second?:"انتخاب کنید",fontWeight=FontWeight.SemiBold)};Icon(Icons.Outlined.ExpandMore,null)
    }
    if(open)AlertDialog(onDismissRequest={open=false},title={Text(label)},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
        if(options.size>8)Field("جست‌وجو",search,{search=it})
        Column(Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState())){options.filter{it.second.contains(search,true)}.forEach{(id,title)->TextButton(onClick={onChoose(id);open=false},modifier=Modifier.fillMaxWidth()){Text(title,Modifier.weight(1f));if(id==value)Icon(Icons.Outlined.Check,null)}}}
    }},confirmButton={TextButton(onClick={open=false}){Text("بستن")}})
}
@Composable private fun TelevisionInstallation(vm: CustomerViewModel,tv: JSONObject){
    val item=vm.item
    val packages=tv.array("packages").objects()
    val chosen=item.optString("installation_package")
    val enabled=!vm.busy
    GuidedField("پکیج نصب","محتویات هر پکیج و موارد خارج از آن را مقایسه کنید.",false){
        InstallationPackages(packages,chosen,enabled){
            if(it!=chosen) vm.changeItem("installation_package",it)
        }
    }
    if(packages.none{it.optString("id")==chosen}) return
    val size=item.optString("tv_size")
    GuidedField("سایز تلویزیون","اندازه صفحه به اینچ روی جعبه یا مشخصات مدل درج شده است.",size.isBlank()){
        ExpressivePicker("سایز تلویزیون",tv.array("sizes").strings().map{it to it},size,enabled){vm.changeItem("tv_size",it)}
    }
    if(size.isBlank()) return
    val bracket=item.optString("tv_bracket")
    GuidedField("نوع نصب و براکت","برای نصب روی دیوار، براکت موردنیاز را انتخاب کنید. برای نصب رومیزی گزینه پایه را بزنید.",bracket.isBlank()){
        ExpressivePicker("نوع نصب و براکت",tv.array("brackets").objects().map{
            it.optString("id") to listOf(it.optString("title"),it.optString("price").takeIf(String::isNotBlank)?.let{price->"$price تومان"}.orEmpty())
                .filter(String::isNotBlank).joinToString(" · ")
        },bracket,enabled){
            if(it!=bracket){vm.changeItem("tv_bracket",it);vm.changeItem("tv_wall","")}
        }
    }
    if(bracket.isBlank()) return
    val wall=item.optString("tv_wall")
    if(bracket!="tabletop") GuidedField("نوع دیوار","جنس دیوار محل نصب را انتخاب کنید.",wall.isBlank()){
        ExpressivePicker("نوع دیوار",tv.array("walls").strings().map{it to it},wall,enabled){vm.changeItem("tv_wall",it)}
    }
    if(bracket=="tabletop" || wall.isNotBlank()){
        CrystalOptions("خدمات تکمیلی",tv.array("services").objects(),item.array("tv_services").strings(),enabled){selected->
            vm.item=JSONObject(vm.item.toString()).put("tv_services",JSONArray(selected))
            vm.saveDraft()
        }
    }
}
