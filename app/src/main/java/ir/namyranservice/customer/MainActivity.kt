package ir.namyranservice.customer

import android.Manifest
import android.app.DatePickerDialog
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import ir.namyranservice.customer.data.*
import ir.namyranservice.customer.domain.NativeRules
import ir.namyranservice.customer.design.*
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
    Scaffold(containerColor=MaterialTheme.colorScheme.background,bottomBar={
        if(!vm.creating)NavigationBar {
            listOf(Triple("home","خانه",Icons.Outlined.Home),Triple("requests","درخواست‌ها",Icons.Outlined.ReceiptLong),Triple("profile","حساب من",Icons.Outlined.PersonOutline)).forEach{(id,title,icon)->
                NavigationBarItem(selected=vm.tab==id,onClick={if(!vm.busy){vm.tab=id;if(id=="requests")vm.loadRequests()}},icon={Icon(icon,null)},label={Text(title)})
            }
        }
    }){padding->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal=20.dp,vertical=16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
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
                IconButton(onClick={vm.dark=!vm.dark}){Icon(if(vm.dark)Icons.Outlined.LightMode else Icons.Outlined.DarkMode,"تغییر حالت نمایش")}
            }
            if(vm.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
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
            if(vm.config==null && (vm.tab=="home" || vm.creating)){
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

@Composable private fun Home(vm:CustomerViewModel){
    var query by rememberSaveable { mutableStateOf("") }
    val config = vm.config ?: return
    val services=config.array("services").objects()
    val results=remember(query,config) {
        val term=query.trim().replace('ي','ی').replace('ك','ک')
        if(term.isEmpty()) emptyList()
        else services.filter { it.optString("title").contains(term,ignoreCase=true) }
    }
    OutlinedTextField(query,{query=it},modifier=Modifier.fillMaxWidth(),singleLine=true,
        label={Text("جستجو در نامیران سرویس")},leadingIcon={Icon(Icons.Outlined.Search,null)},
        trailingIcon={if(query.isNotEmpty())IconButton(onClick={query=""}){Icon(Icons.Outlined.Close,"پاک کردن جستجو")}},
        shape=RoundedCornerShape(24.dp))
    if(query.isNotBlank()) {
        if(results.isEmpty()) Panel("خدمتی پیدا نشد","نام خدمت را جستجو کنید، مثلاً تعمیر یا نصب.")
        results.forEach { result ->
            OutlinedButton(onClick={vm.startRequest(result.optString("id"))},enabled=!vm.busy,modifier=Modifier.fillMaxWidth()) {
                Text(result.optString("title"))
            }
        }
    }
    Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(NamyranColors.blue,NamyranColors.cyan)),RoundedCornerShape(28.dp)).padding(24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        Text(if(vm.loggedIn)"سلام ${vm.accountName.takeIf{it.isNotBlank()}?:"همراه نامیران"}" else "خدمات مطمئن، خیال آسوده",color=Color.White,fontWeight=FontWeight.Bold,fontSize=24.sp)
        Text("نصب، تعمیر و نگهداری لوازم خانگی",color=Color.White)
        Button(onClick={vm.startRequest()},enabled=!vm.busy&&vm.pending==null,modifier=Modifier.fillMaxWidth().heightIn(min=54.dp),colors=ButtonDefaults.buttonColors(containerColor=Color.White,contentColor=NamyranColors.blue)){Text("ثبت درخواست جدید")}
    }
    if(vm.loggedIn){
        if(vm.accountName.isBlank()) {
            Panel("پروفایلتان را کامل کنید","نام و نام خانوادگی را هنگام ثبت درخواست وارد کنید. نشانی را می‌توانید برای درخواست‌های بعدی ذخیره کنید.")
        }
        val active=vm.requests.filter{it.optString("status") !in listOf("completed","cancelled")}
        SectionTitle("درخواست‌های فعال",active.size.toString())
        if(vm.requestsLoaded&&active.isEmpty())Panel("درخواست فعالی ندارید","برای شروع، خدمت و دستگاه موردنظر را انتخاب کنید.")
        active.take(2).forEach{RequestCard(it,vm)}
    }else {
        OutlinedButton(onClick={vm.tab="profile"},modifier=Modifier.fillMaxWidth().heightIn(min=52.dp),shape=RoundedCornerShape(18.dp)){
            Icon(Icons.Outlined.PersonOutline,null);Spacer(Modifier.width(8.dp));Text("ورود به ناحیه کاربری")
        }
    }
    SectionTitle("خدمات ما")
    services.chunked(2).forEach{row->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){
        row.forEach{s->Card(onClick={vm.startRequest(s.getString("id"))},modifier=Modifier.weight(1f),shape=RoundedCornerShape(22.dp)){
            Column(Modifier.padding(18.dp).heightIn(min=80.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                Icon(when(s.optString("icon")){"install"->Icons.Outlined.Handyman;"warranty"->Icons.Outlined.VerifiedUser;"periodic"->Icons.Outlined.Update;else->Icons.Outlined.Build},null,tint=MaterialTheme.colorScheme.primary)
                Text(s.optString("title"),fontWeight=FontWeight.Bold)
            }
        }};if(row.size==1)Spacer(Modifier.weight(1f))
    }}
    TextButton(onClick={vm.refresh()},enabled=!vm.busy){Icon(Icons.Outlined.Refresh,null);Spacer(Modifier.width(6.dp));Text("به‌روزرسانی اطلاعات خدمات")}
}

@Composable private fun Requests(vm:CustomerViewModel){
    SectionTitle("درخواست‌های من",vm.requests.size.toString())
    if(!vm.loggedIn){Panel("ابتدا وارد حساب شوید","فقط درخواست‌های متعلق به شماره تأییدشده شما نمایش داده می‌شوند.");Action("ورود به حساب",!vm.busy){vm.tab="profile"};return}
    OutlinedButton(onClick=vm::loadRequests,enabled=!vm.busy,modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.Refresh,null);Text("به‌روزرسانی وضعیت")}
    if(!vm.requestsLoaded)Panel("فهرست هنوز دریافت نشده است","برای دریافت اطلاعات واقعی حساب، وضعیت را به‌روزرسانی کنید.")
    else if(vm.requests.isEmpty())Panel("هنوز درخواستی ندارید","در این حساب درخواستی ثبت نشده است.")
    vm.requests.forEach{RequestCard(it,vm)}
}

@Composable private fun RequestCard(row:JSONObject,vm:CustomerViewModel){
    Card(onClick={if(!vm.busy)vm.track(row.optString("tracking_code"))},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(22.dp)){
        Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(row.optString("service_label"),fontWeight=FontWeight.Bold);Text(row.optString("status_label"),color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelMedium)}
            Text(listOf(row.optString("device"),row.optString("brand")).filter{it.isNotBlank()}.joinToString(" · "))
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("پیگیری ${row.optString("tracking_code")}",style=MaterialTheme.typography.labelMedium);Text(row.optString("visit_date_jalali"),style=MaterialTheme.typography.labelMedium)}
            if(row.optBoolean("needs_customer_action"))Text("این درخواست نیاز به اقدام شما دارد.",color=MaterialTheme.colorScheme.error)
        }
    }
}

@Composable private fun Profile(vm: CustomerViewModel) {
    SectionTitle("حساب من")
        Card(modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(24.dp)) {
            Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                CustomerAvatar(vm.accountName)
                Column {
                    Text(vm.accountName.ifBlank { "حساب مشتری" },style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                    Text("شماره تأییدشده · "+vm.mobile,style=MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Action("مشاهده درخواست‌های من",!vm.busy,vm::loadRequests)
        OutlinedButton(onClick=vm::loadAddresses,enabled=!vm.busy,modifier=Modifier.fillMaxWidth()){Text("دریافت نشانی‌های ذخیره‌شده")}
        vm.addresses.forEach{row->val a=try{JSONObject(row.optString("data_json"))}catch(_:Exception){JSONObject()};Panel(when(row.optString("label")){"home"->"خانه";"work"->"محل کار";else->"نشانی دیگر"},a.optString("address"))}
        OutlinedButton(onClick=vm::logout,enabled=!vm.busy,modifier=Modifier.fillMaxWidth()){Text("خروج از حساب")}
}

@Composable private fun LoginScreen(vm: CustomerViewModel) {
    Scaffold(containerColor=MaterialTheme.colorScheme.background) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal=24.dp,vertical=24.dp),
            verticalArrangement=Arrangement.spacedBy(18.dp)
        ) {
            Surface(shape=RoundedCornerShape(28.dp),color=MaterialTheme.colorScheme.primaryContainer,modifier=Modifier.fillMaxWidth()) {
                Column(Modifier.padding(22.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Surface(shape=RoundedCornerShape(16.dp),color=Color.White) {
                        androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.namyran_symbol),
                            "نشان نامیران سرویس",Modifier.size(64.dp).padding(6.dp))
                    }
                    Text("نامیران سرویس",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
                    Text("به حساب مشتری خوش آمدید",style=MaterialTheme.typography.bodyLarge)
                }
            }
            if(vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if(vm.error.isNotBlank()) Text(vm.error,color=MaterialTheme.colorScheme.error)
            if(vm.message.isNotBlank()) Text(vm.message,color=MaterialTheme.colorScheme.primary)
            if(vm.hasSavedSession) {
                Panel("بازیابی حساب","در حال بررسی نشست قبلی شما هستیم.")
                if(!vm.busy) Action("بررسی دوباره نشست",onClick=vm::refresh)
            } else LoginForm(vm)
        }
    }
}

@Composable private fun LoginForm(vm: CustomerViewModel) {
    var phone by rememberSaveable{mutableStateOf(vm.rememberedMobile)}
    var rememberNumber by rememberSaveable{mutableStateOf(vm.rememberedMobile.isNotBlank())}
    var code by remember{mutableStateOf("")}
    var now by remember{mutableLongStateOf(System.currentTimeMillis())}
    LaunchedEffect(vm.resendAt){while(System.currentTimeMillis()<vm.resendAt){now=System.currentTimeMillis();kotlinx.coroutines.delay(1000)};now=System.currentTimeMillis()}
        Panel("ورود با شماره موبایل","کد ورود به شماره شما پیامک می‌شود.")
        Field("شماره موبایل",phone,{phone=it},!vm.busy&&!vm.otpSent,KeyboardType.Phone)
        Row(Modifier.fillMaxWidth().clickable(enabled=!vm.busy){rememberNumber=!rememberNumber;if(!rememberNumber)vm.rememberMobile(false)},verticalAlignment=Alignment.CenterVertically) {
            Checkbox(rememberNumber,null,enabled=!vm.busy)
            Text("شماره‌ام را به خاطر بسپار",Modifier.padding(12.dp))
        }
        if(vm.otpSent){
            Text("کد ارسال‌شده به ${vm.mobile} را وارد کنید.")
            Field("کد چهاررقمی",code,{code=normalizedDigits(it).filter(Char::isDigit).take(4)},!vm.busy,KeyboardType.NumberPassword)
            Action("تأیید و ورود",!vm.busy&&code.length==4){vm.verifyOtp(code)}
        }
        val remaining=((vm.resendAt-now+999)/1000).coerceAtLeast(0)
        OutlinedButton(onClick={vm.sendOtp(phone,rememberNumber)},enabled=!vm.busy&&remaining==0L,modifier=Modifier.fillMaxWidth().heightIn(min=54.dp)){
            Text(if(remaining>0)"ارسال دوباره در $remaining ثانیه" else if(vm.otpSent)"ارسال دوباره کد" else "دریافت کد ورود")
        }
}

@Composable private fun RequestFlow(vm:CustomerViewModel){
    val config=vm.config!!;val item=vm.item;val context=LocalContext.current
    val headings=listOf("انتخاب خدمت و دستگاه","نشانی و شرح درخواست","زمان پیشنهادی مراجعه","بازبینی و ثبت")
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Text("درخواست جدید",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);TextButton(onClick={vm.saveDraft();vm.creating=false},enabled=!vm.busy){Text("ذخیره و بستن")}}
    Text("مرحله ${vm.step+1} از ۴ · ${headings[vm.step]}",fontWeight=FontWeight.Bold)
    LinearProgressIndicator(progress={ (vm.step+1)/4f },modifier=Modifier.fillMaxWidth())
    if(item.optString("service_type")=="warranty"){
        Panel("خدمات گارانتی","این خدمت در مرکز گارانتی نامیران پیگیری می‌شود.")
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
            Picker("نوع خدمت",config.array("services").objects().map{it.optString("id") to it.optString("title")},item.optString("service_type"),!vm.busy){vm.changeItem("service_type",it)}
            val categories=config.optJSONObject("categories")?:JSONObject()
            val keys=categories.keys().asSequence().toList()
            Picker("گروه دستگاه",keys.map{it to it},item.optString("category"),!vm.busy){vm.changeItem("category",it)}
            val mode=item.optString("service_type")
            val devices=categories.array(item.optString("category")).objects().filter{d->mode in d.array("service_modes").strings()||(mode=="periodic"&&(d.optBoolean("periodic_enabled")||d.optBoolean("settings_enabled")||d.optBoolean("maintenance_enabled")))}
            Picker("دستگاه",devices.map{it.optString("id") to it.optString("title")},item.optString("device"),!vm.busy){id->devices.find{it.optString("id")==id}?.let(vm::chooseDevice)}
            val device=vm.device()
            if(device!=null&&device.array("subtypes").length()>0)Picker("نوع دستگاه",device.array("subtypes").objects().map{it.optString("id") to it.optString("title")},item.optString("catalog_subtype_id"),!vm.busy){vm.changeItem("catalog_subtype_id",it)}
            val brands=config.array(if(mode=="install")"installBrands" else "brands").strings()
            Picker("برند",brands.map{it to it},item.optString("brand"),!vm.busy){vm.changeItem("brand",it)}
            if(NativeRules.isCustomBrand(item.optString("brand")))Field("نام برند دستگاه",item.optString("custom_brand"),{vm.changeItem("custom_brand",it)},!vm.busy)
            Field("مدل دستگاه، در صورت اطلاع",vm.model,{vm.model=it;vm.saveDraft()},!vm.busy)
            if(vm.isTv()){
                val tv=config.optJSONObject("tvInstall")?:JSONObject()
                Picker("پکیج نصب",tv.array("packages").objects().map{it.optString("id") to it.optString("title")},item.optString("installation_package"),!vm.busy){vm.changeItem("installation_package",it)}
                tv.array("packages").objects().find{it.optString("id")==item.optString("installation_package")}?.let{p->Panel(p.optString("title"),p.array("included").strings().joinToString("\n"));if(p.array("excluded").length()>0)Text(p.array("excluded").strings().joinToString("\n"),style=MaterialTheme.typography.bodySmall)}
                Picker("سایز تلویزیون",tv.array("sizes").strings().map{it to it},item.optString("tv_size"),!vm.busy){vm.changeItem("tv_size",it)}
                Picker("نوع نصب و براکت",tv.array("brackets").objects().map{it.optString("id") to (it.optString("title")+" · "+it.optString("price")+" تومان")},item.optString("tv_bracket"),!vm.busy){vm.changeItem("tv_bracket",it);if(it=="tabletop")vm.changeItem("tv_wall","")}
                if(item.optString("tv_bracket")!="tabletop")Picker("نوع دیوار",tv.array("walls").strings().map{it to it},item.optString("tv_wall"),!vm.busy){vm.changeItem("tv_wall",it)}
                CheckOptions("خدمات تکمیلی",tv.array("services").objects(),item.array("tv_services").strings(),!vm.busy){selected->vm.item=JSONObject(vm.item.toString()).put("tv_services",JSONArray(selected));vm.saveDraft()}
            }
            if(device!=null){
                val extras=device.array("services").objects().filter{mode in it.optString("mode").split(',')}
                CheckOptions("خدمات دستگاه",extras,item.optString("catalog_service_ids").split(',').filter{it.isNotBlank()},!vm.busy){ids->
                    val versions=JSONObject();extras.filter{it.optString("id") in ids}.forEach{versions.put(it.optString("id"),it.optInt("row_version"))}
                    vm.item=JSONObject(vm.item.toString()).put("catalog_service_ids",ids.joinToString(",")).put("catalog_service_versions",versions);vm.saveDraft()
                }
            }
            vm.items.forEachIndexed{index,saved->Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("${index+1}. ${saved.optString("device")} · ${saved.optString("brand")}",Modifier.weight(1f));IconButton(onClick={vm.removeItem(index)}){Icon(Icons.Outlined.DeleteOutline,"حذف دستگاه")}}}
            OutlinedButton(onClick=vm::addItem,enabled=!vm.busy,modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.Add,null);Text("افزودن دستگاه دیگر به این درخواست")}
        }
        1->{
            Field("نام و نام خانوادگی",vm.name,{vm.name=it;vm.saveDraft()},!vm.busy)
            val cities=config.optJSONObject("customerMobile")?.array("cities")?.objects().orEmpty()
            if(cities.isEmpty())Panel("شهر فعالی دریافت نشد","محدوده خدمات باید توسط مدیریت Core فعال شود.")
            Picker("شهر محل خدمت",cities.map{it.optString("id") to it.optString("title")},vm.city,!vm.busy){vm.city=it;vm.area="";vm.saveDraft()}
            val groups=cities.find{it.optString("id")==vm.city}?.array("groups")?.objects().orEmpty()
            val areas=groups.flatMap{g->g.array("areas").objects().map{a->a.optString("title") to (g.optString("title")+" · "+a.optString("title"))}}
            if(areas.isNotEmpty())Picker("محله / محدوده",areas,vm.area,!vm.busy){vm.area=it;vm.saveDraft()}
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
            Action(if(vm.date.isBlank())"انتخاب تاریخ مراجعه" else vm.date,!vm.busy){
                val start=runCatching{LocalDate.parse(config.optString("serverNow").take(10))}.getOrDefault(LocalDate.now())
                val chosen=runCatching{LocalDate.parse(vm.date)}.getOrDefault(start)
                DatePickerDialog(context,{_,year,month,day->vm.getSchedule(LocalDate.of(year,month+1,day).toString())},chosen.year,chosen.monthValue-1,chosen.dayOfMonth).show()
            }
            vm.schedule?.let{s->Text(s.optString("dateJalali"),fontWeight=FontWeight.Bold);s.array("slots").objects().forEach{slot->
                val value=slot.optString("value");OutlinedButton(onClick={vm.slot=value;vm.saveDraft()},enabled=slot.optBoolean("available")&&!vm.busy,modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.outlinedButtonColors(containerColor=if(vm.slot==value)MaterialTheme.colorScheme.primaryContainer else Color.Transparent)){
                    Text(value+if(slot.optBoolean("available"))"" else " · قابل انتخاب نیست")
                }
            }}
        }
        3->{
            SectionTitle("خلاصه درخواست")
            (vm.items+vm.currentItemForReview()).forEach{d->Panel(listOf(d.optString("device"),d.optString("brand")).joinToString(" · "),listOf(config.array("services").objects().find{it.optString("id")==d.optString("service_type")}?.optString("title"),d.optString("device_model")).filterNotNull().filter{it.isNotBlank()}.joinToString("\n"))}
            Panel(vm.name,vm.address)
            if(vm.description.isNotBlank())Panel("شرح درخواست",vm.description)
            ImageAttachments(vm,review=true)
            Panel("زمان پیشنهادی",(vm.schedule?.optString("dateJalali")?:vm.date)+" · "+vm.slot)
            Row(verticalAlignment=Alignment.CenterVertically){Checkbox(vm.terms,{vm.terms=it},enabled=!vm.busy);Text("صحت اطلاعات و ثبت زمان پیشنهادی برای بررسی و هماهنگی را می‌پذیرم.",style=MaterialTheme.typography.bodyMedium)}
            Action("ثبت نهایی درخواست",!vm.busy&&vm.terms,vm::submit)
        }
    }
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){
        OutlinedButton(onClick={if(vm.step>0)vm.step-- else vm.creating=false;vm.saveDraft()},enabled=!vm.busy,modifier=Modifier.weight(1f).heightIn(min=54.dp)){Text("بازگشت")}
        if(vm.step<3)Button(onClick=vm::next,enabled=!vm.busy,modifier=Modifier.weight(1f).heightIn(min=54.dp)){Text("ادامه")}
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
        Column(Modifier.fillMaxWidth().padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp),horizontalAlignment=Alignment.CenterHorizontally){Icon(Icons.Outlined.CheckCircle,null,Modifier.size(56.dp));Text("درخواست ثبت شد",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text(r.optString("tracking_code"),fontSize=28.sp,fontWeight=FontWeight.Bold);Text(r.optString("status_label"))}
    }
    Text(if(vm.receiptInAccount)"همین کد پیگیری در حساب شما بازخوانی و تأیید شد." else "ثبت توسط Core تأیید شد؛ برای بررسی نمایش در حساب، درخواست‌ها را به‌روزرسانی کنید.")
    if(r.optInt("local_image_count")>0)Panel("وضعیت تصاویر",if(r.optBoolean("local_images_confirmed"))"Core ذخیره ${r.optInt("local_image_count")} تصویر را تأیید کرد." else "درخواست ثبت شده، اما ذخیره همه تصاویر تأیید نشد. با همین کد پیگیری از پشتیبانی پیگیری کنید؛ درخواست تازه ثبت نکنید.")
    Action("مشاهده درخواست در حساب",!vm.busy,vm::loadRequests)
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
@Composable private fun CheckOptions(label:String,options:List<JSONObject>,selected:List<String>,enabled:Boolean,onChange:(List<String>)->Unit){
    if(options.isEmpty())return
    Text(label,fontWeight=FontWeight.Bold)
    options.forEach{row->val id=row.optString("id");Row(verticalAlignment=Alignment.CenterVertically){Checkbox(id in selected,{checked->onChange(if(checked)selected+id else selected-id)},enabled=enabled);Column{Text(row.optString("title"));if(row.has("price"))Text(row.optString("price")+" تومان",style=MaterialTheme.typography.labelSmall)}}}
}
