package ir.namyranservice.customer

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import ir.namyranservice.customer.data.*
import ir.namyranservice.customer.domain.NativeRules
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.Executors

class CustomerViewModel(application: Application) : AndroidViewModel(application) {
    private val api = CoreApi()
    private val store = SecureStore(application)
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    var busy by mutableStateOf(false); private set
    var error by mutableStateOf(""); private set
    var message by mutableStateOf(""); private set
    var config by mutableStateOf<JSONObject?>(null); private set
    var mobile by mutableStateOf(""); private set
    private var token = ""
    var loggedIn by mutableStateOf(false); private set
    var otpSent by mutableStateOf(false); private set
    var resendAt by mutableLongStateOf(0L); private set
    var requestsLoaded by mutableStateOf(false); private set
    var requests by mutableStateOf<List<JSONObject>>(emptyList()); private set
    var addresses by mutableStateOf<List<JSONObject>>(emptyList()); private set
    var schedule by mutableStateOf<JSONObject?>(null); private set
    var tracking by mutableStateOf<JSONObject?>(null); private set
    var receipt by mutableStateOf<JSONObject?>(null); private set
    var receiptInAccount by mutableStateOf(false); private set
    var pending by mutableStateOf<JSONObject?>(null); private set
    var tab by mutableStateOf("home")
    var creating by mutableStateOf(false)
    var step by mutableIntStateOf(0)
    var dark by mutableStateOf(false)
    var item by mutableStateOf(JSONObject())
    var items by mutableStateOf<List<JSONObject>>(emptyList())
    var name by mutableStateOf("")
    var model by mutableStateOf("")
    var description by mutableStateOf("")
    var city by mutableStateOf("")
    var area by mutableStateOf("")
    var address by mutableStateOf("")
    var date by mutableStateOf("")
    var slot by mutableStateOf("")
    var latitude by mutableStateOf("")
    var longitude by mutableStateOf("")
    var accuracy by mutableStateOf("")
    var terms by mutableStateOf(false)

    init {
        val saved = store.read("session")
        if (saved != null && saved.optLong("expires") > System.currentTimeMillis()) {
            mobile = saved.optString("mobile"); token = saved.optString("token")
        }
        pending = store.read("pending")
        store.read("draft")?.let { d ->
            item = d.optJSONObject("item") ?: JSONObject(); items = d.array("items").objects()
            name=d.optString("name"); model=d.optString("model"); description=d.optString("description")
            city=d.optString("city"); area=d.optString("area"); address=d.optString("address")
            date=d.optString("date"); slot=d.optString("slot"); latitude=d.optString("latitude");longitude=d.optString("longitude");accuracy=d.optString("accuracy")
        }
        refresh()
    }
    private fun launchTask(task: () -> (() -> Unit)) {
        if (busy) return
        busy = true; error = ""; message = ""
        executor.execute {
            try { val apply = task(); main.post { apply(); busy=false } }
            catch (e: Exception) { main.post {
                if(e is CoreFailure&&pending!=null&&NativeRules.rejectedBeforeInsert(e.code)){pending=null;store.write("pending",null);creating=true;step=0}
                if (e is CoreFailure && e.code in setOf("nsc_customer_session_denied","nsc_session_expired","nsc_not_verified","nsc_my_requests_auth")) clearSession()
                error = when(e) { is CoreFailure -> e.message ?: "خطای Core"; else -> "ارتباط با Stage کامل نشد؛ اتصال اینترنت را بررسی و دوباره تلاش کنید." }
                busy=false
            } }
        }
    }
    fun dismissError() { error="" }
    private fun auth() = JSONObject().put("mobile",mobile).put("verification_token",token)
    private fun clearSession() {
        token=""; loggedIn=false; requests=emptyList();addresses=emptyList();tracking=null
        receiptInAccount=false;requestsLoaded=false;name="";address="";description="";city="";area="";latitude="";longitude="";accuracy="";model="";item=JSONObject();items=emptyList();terms=false;creating=false;store.write("draft",null);store.write("receipt",null);store.write("session",null);api.clearCookies()
    }
    fun refresh() {
        launchTask {
            val c=api.config()
            val account = if(token.isNotBlank()) api.post("otp/session",auth()) else null
            val rows = if(account != null) api.post("my-requests",auth()).array("requests").objects() else emptyList()
            return@launchTask { config=c; loggedIn=account != null; requests=rows;requestsLoaded=account!=null; if(account != null && name.isBlank()) name=account.optString("customer_name") }
        }
    }
    fun sendOtp(number: String) {
        val numberClean=normalizedDigits(number).filter { it.isDigit() }
        if(!numberClean.matches(Regex("09[0-9]{9}"))) { error="شماره موبایل معتبر وارد کنید.";return }
        if(System.currentTimeMillis()<resendAt) return
        launchTask {
            val r=api.post("otp/send",JSONObject().put("mobile",numberClean).put("purpose","customer_service").put("website",""))
            if(!r.optBoolean("success")) throw CoreFailure("otp_failed","ارسال کد تأیید نشد.")
            if(r.optBoolean("dev_mode")) throw CoreFailure("otp_dev_mode","Core در حالت کد آزمایشی است؛ ارسال واقعی پیامک تأیید نشد. تنظیم ارسال واقعی باید در Stage اصلاح شود.")
            val until=System.currentTimeMillis()+r.optInt("resend_in",90)*1000L
            return@launchTask { mobile=numberClean;otpSent=true;resendAt=until;message=r.optString("message") }
        }
    }
    fun verifyOtp(code: String) {
        val normalized=normalizedDigits(code).trim()
        if(!normalized.matches(Regex("[0-9]{4}"))) { error="کد چهاررقمی را کامل وارد کنید.";return }
        launchTask {
            val r=api.post("otp/verify",JSONObject().put("mobile",mobile).put("code",normalized).put("purpose","customer_service"))
            val t=r.optString("verification_token")
            if(!r.optBoolean("success")||t.isBlank())throw CoreFailure("otp_verify_invalid","نشست معتبر دریافت نشد.")
            val session=JSONObject().put("mobile",mobile).put("token",t).put("expires",System.currentTimeMillis()+r.getLong("verified_for")*1000)
            store.write("session",session)
            val rows=try { api.post("my-requests",JSONObject().put("mobile",mobile).put("verification_token",t)).array("requests").objects() } catch (_: Exception) { null }
            return@launchTask { token=t;loggedIn=true;otpSent=false;requests=rows.orEmpty();requestsLoaded=rows!=null;message="با موفقیت وارد شدید." }
        }
    }
    fun logout() { launchTask { api.post("otp/logout",auth()); return@launchTask {clearSession();tab="home";message="از حساب خارج شدید."} } }
    fun loadRequests() { if(!loggedIn){tab="profile";return};launchTask {val r=api.post("my-requests",auth()); return@launchTask {requests=r.array("requests").objects();requestsLoaded=true;tab="requests";receipt?.let { rc->receiptInAccount=requests.any { it.optString("tracking_code")==rc.optString("tracking_code") }}} } }
    fun loadAddresses() { if(!loggedIn)return;launchTask {val r=api.post("customer-addresses",auth().put("action","list"));return@launchTask {addresses=r.array("rows").objects()}} }
    fun track(code: String) { launchTask {
        val client=store.read("client")?.optString("id") ?: UUID.randomUUID().toString().also{store.write("client",JSONObject().put("id",it))}
        val r=api.post("track",JSONObject().put("tracking_code",code).put("mobile",mobile).put("client_id",client))
        return@launchTask { tracking=r;tab="tracking" }
    } }
    fun startRequest() {
        if(!loggedIn){tab="profile";message="برای ثبت درخواست وارد حساب شوید.";return}
        if(config==null){refresh();return}
        launchTask { val current=api.config();return@launchTask {config=current;receipt=null;receiptInAccount=false;creating=true;step=0} }
    }
    fun changeItem(key:String,value:String) {
        val next=JSONObject(item.toString())
        when(key){
            "service_type" -> { listOf("category","device","catalog_device_id","catalog_device_version","catalog_subtype_id","brand","installation_package","tv_size","tv_bracket","tv_wall","catalog_service_ids").forEach(next::remove); model="" }
            "category" -> { listOf("device","catalog_device_id","catalog_device_version","catalog_subtype_id","catalog_service_ids").forEach(next::remove); model="" }
            "device" -> {listOf("catalog_subtype_id","catalog_service_ids").forEach(next::remove);model=""}
        }
        next.put(key,value);item=next;saveDraft()
    }
    fun chooseDevice(device:JSONObject) {
        changeItem("device",device.getString("id"))
        item=JSONObject(item.toString()).put("catalog_device_id",device.getInt("catalog_id")).put("catalog_device_version",device.getInt("row_version"))
        saveDraft()
    }
    fun device():JSONObject? = config?.optJSONObject("categories")?.array(item.optString("category"))?.objects()?.find{it.optString("id")==item.optString("device")}
    fun checkItem():String? {
        if(listOf("service_type","category","device","brand").any{item.optString(it).isBlank()})return "خدمت، گروه، دستگاه و برند را انتخاب کنید."
        val brandOptions=config!!.array(if(item.optString("service_type")=="install")"installBrands" else "brands").strings()
        if(item.optString("brand") !in brandOptions)return "برند در Core تغییر کرده است؛ دوباره انتخاب کنید."
        val d=device()?:return "دستگاه در کاتالوگ فعلی Core پیدا نشد؛ دوباره انتخاب کنید."
        if(d.array("subtypes").length()>0&&d.array("subtypes").objects().none{it.optString("id")==item.optString("catalog_subtype_id")})return "زیرنوع دستگاه را انتخاب کنید."
        if(isTv()){
            if(listOf("installation_package","tv_size","tv_bracket").any{item.optString(it).isBlank()})return "جزئیات نصب تلویزیون را کامل کنید."
            if(item.optString("tv_bracket")!="tabletop"&&item.optString("tv_wall").isBlank())return "نوع دیوار را انتخاب کنید."
            val vip=config?.optJSONObject("customerMobile")?.array("tvVipSizes")?.strings().orEmpty()
            if(item.optString("tv_size") in vip&&item.optString("installation_package")!="vip")return "این سایز طبق قوانین Core به پکیج VIP نیاز دارد."
        }
        return null
    }
    fun isTv()=item.optString("service_type")=="install"&&device()?.optString("icon")=="television"
    private fun currentItem():JSONObject=JSONObject(item.toString()).put("device_model",model).put("service_location",if(item.optString("service_type")=="install")"onsite" else item.optString("service_location"))
    fun addItem() { val problem=checkItem();if(problem!=null){error=problem;return};if(items.size>=(config!!.getJSONObject("customerMobile").getInt("maxOrderItems")-1)){error="حداکثر ۲۰ دستگاه در هر درخواست مجاز است.";return};items=items+currentItem();item=JSONObject();model="";step=0;saveDraft() }
    fun removeItem(index:Int){items=items.filterIndexed{i,_->i!=index};saveDraft()}
    fun next() {
        error=""
        when(step){
            0 -> checkItem()?.let{error=it;return}
            1 -> {
                val cities=config?.optJSONObject("customerMobile")?.array("cities")?.objects().orEmpty()
                if(cities.none{it.optString("id")==city}){error="شهر فعال محل خدمت را انتخاب کنید.";return}
                if(name.trim().length<3||address.trim().length<8){error="نام کامل و نشانی دقیق را وارد کنید.";return}
                if(config?.optBoolean("locationRequired")==true&&(latitude.isBlank()||longitude.isBlank())){error="طبق تنظیم Core، موقعیت محل خدمت الزامی است.";return}
            }
            2 -> {if(schedule?.optString("date")!=date||schedule?.array("slots")?.objects()?.none{it.optString("value")==slot&&it.optBoolean("available")}!=false){error="زمان پیشنهادی معتبر را از سرور دریافت و انتخاب کنید.";return}}
        }
        saveDraft();step++
    }
    fun getSchedule(value:String) {val d=normalizedDigits(value).trim();date=d;slot="";schedule=null;saveDraft();launchTask {val r=api.schedule(d);return@launchTask {schedule=r}}}
    fun saveDraft() {
        store.write("draft",JSONObject().put("item",item).put("items",JSONArray(items)).put("name",name).put("model",model).put("description",description).put("city",city).put("area",area).put("address",address).put("date",date).put("slot",slot).put("latitude",latitude).put("longitude",longitude).put("accuracy",accuracy))
    }
    fun submit() {
        if(!loggedIn||!terms||busy||pending!=null){error="ورود به حساب و پذیرش شرایط لازم است.";return}
        checkItem()?.let{error=it;return}
        val cities=config?.optJSONObject("customerMobile")?.array("cities")?.objects().orEmpty()
        val cityTitle=cities.find{it.optString("id")==city}?.optString("title")?:kotlin.run {error="شهر را دوباره انتخاب کنید.";return}
        val allItems=items+currentItem();val primary=allItems.first()
        val payload=JSONObject(primary.toString()).put("order_items",JSONArray(allItems).toString()).put("customer_name",name.trim()).put("problem_description",description)
            .put("address",listOf(cityTitle,area,address.trim()).filter{it.isNotBlank()}.joinToString("، "))
            .put("visit_date",date).put("visit_time",slot).put("latitude",latitude).put("longitude",longitude).put("location_accuracy",accuracy)
            .put("terms_accepted","1").put("request_origin","android_stage").put("submission_token",UUID.randomUUID().toString()).put("owner_mobile",mobile)
        payload.put("vip_required",if(allItems.any{it.optString("installation_package")=="vip"})"1" else "0")
        payload.put("tv_services",primary.optJSONArray("tv_services")?.toString()?:"[]")
        pending=payload;store.write("pending",payload);retryPending()
    }
    fun retryPending() {
        val saved=pending?:return
        if(!loggedIn){tab="profile";return}
        if(saved.optString("owner_mobile")!=mobile){error="ثبت معلق متعلق به حساب دیگری است؛ به همان حساب وارد شوید.";return}
        launchTask {
            val payload=JSONObject(saved.toString()).put("mobile",mobile).put("verification_token",token)
            val result=api.post("requests",payload)
            if(!result.optBoolean("success")||result.optInt("request_id")<=0||result.optString("tracking_code").isBlank())throw CoreFailure("submission_invalid","پاسخ ثبت درخواست معتبر نیست. همان درخواست را دوباره بررسی کنید.")
            // Persist the receipt before the read-back, so a list/network failure never loses the tracking code.
            store.write("receipt",result);store.write("pending",null)
            var rows:List<JSONObject>?=null
            try {rows=api.post("my-requests",auth()).array("requests").objects()}catch(_:Exception){}
            return@launchTask {receipt=result;pending=null;receiptInAccount=rows?.any{it.optString("tracking_code")==result.optString("tracking_code")}==true
                if(rows!=null){requests=rows!!;requestsLoaded=true}
                creating=false;tab="receipt";item=JSONObject();items=emptyList();terms=false;store.write("draft",null)}
        }
    }
    override fun onCleared(){ executor.shutdown();super.onCleared() }
}
