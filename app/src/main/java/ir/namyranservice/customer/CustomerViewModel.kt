package ir.namyranservice.customer

import android.app.Application
import android.net.Uri
import ir.namyranservice.customer.domain.ImageRules
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
    private val imageStore = AttachmentStore(application)
    var images by mutableStateOf<List<JSONObject>>(emptyList()); private set
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    var busy by mutableStateOf(false); private set
    var error by mutableStateOf(""); private set
    var message by mutableStateOf(""); private set
    val bundledCatalog = runCatching {
        application.assets.open("stage-home-services.json").bufferedReader().use {
            JSONObject(it.readText())
        }
    }.getOrElse { JSONObject() }
    var config by mutableStateOf<JSONObject?>(null); private set
    var mobile by mutableStateOf(""); private set
    private var token by mutableStateOf("")
    val hasSavedSession: Boolean get() = token.isNotBlank()
    private var sessionTtlSeconds = 0L
    var rememberedMobile by mutableStateOf(store.read("remembered_mobile")?.optString("mobile").orEmpty())
        private set

    fun rememberMobile(enabled: Boolean, number: String = mobile) {
        rememberedMobile = if (enabled) normalizedDigits(number).filter(Char::isDigit) else ""
        store.write("remembered_mobile", rememberedMobile.takeIf(String::isNotBlank)?.let { JSONObject().put("mobile", it) })
    }

    private fun persistSession(number: String, credential: String, ttl: Long) {
        store.write("session", JSONObject().put("mobile", number).put("token", credential)
            .put("ttl", ttl).put("expires", System.currentTimeMillis() + ttl * 1000L))
    }
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
    var dark by mutableStateOf(store.read("display")?.optBoolean("dark") == true)
        private set
    fun setDarkMode(enabled: Boolean) {
        store.write("display", JSONObject().put("dark",enabled))
        dark = enabled
    }

    fun localProfile(): JSONObject = if (loggedIn) store.read("profile_" + mobile) ?: JSONObject() else JSONObject()
    fun saveLocalProfile(fullName: String, birthday: String, photo: String): Boolean {
        if (!loggedIn) return false
        return runCatching {
            store.write("profile_" + mobile, JSONObject().put("name",fullName.trim())
                .put("birthday",birthday.trim()).put("photo",photo))
        }.isSuccess
    }
    var item by mutableStateOf(JSONObject())
    var items by mutableStateOf<List<JSONObject>>(emptyList())
    var accountName by mutableStateOf("")
        private set
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
            sessionTtlSeconds = saved.optLong("ttl")
        }
        pending = store.read("pending")
        store.read("draft")?.takeIf { mobile.isNotBlank() && it.optString("owner_mobile")==mobile }?.let { d ->
            images=d.array("images").objects()
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
            catch (e: Exception) {
                if(BuildConfig.DEBUG)android.util.Log.w("NamyranStage", "operation_failed:"+e.javaClass.simpleName+":"+(e.cause?.javaClass?.simpleName?:"none")+(if(e is CoreFailure)":"+e.code else ""))
                main.post {
                if(e is CoreFailure&&pending!=null&&NativeRules.rejectedBeforeInsert(e.code)){pending=null;store.write("pending",null);creating=true;step=0}
                if (e is CoreFailure && e.code in setOf("nsc_customer_session_denied","nsc_session_expired","nsc_my_requests_auth")) clearSession()
                error = when(e) { is CoreFailure -> e.message ?: "خطای Core"; else -> "ارتباط با Stage کامل نشد؛ اتصال اینترنت را بررسی و دوباره تلاش کنید." }
                busy=false
            } }
        }
    }
    fun dismissError() { error="" }
    private fun auth() = JSONObject().put("mobile",mobile).put("verification_token",token)
    private fun clearSession() {
        tab="home"; config=null; token=""; sessionTtlSeconds=0; otpSent=false; loggedIn=false; accountName=""; requests=emptyList();addresses=emptyList();tracking=null
        images=emptyList();if(pending==null)imageStore.clear();receiptInAccount=false;requestsLoaded=false;name="";address="";description="";city="";area="";latitude="";longitude="";accuracy="";model="";item=JSONObject();items=emptyList();terms=false;creating=false;store.write("draft",null);store.write("receipt",null);store.write("session",null);store.write("address_operation",null);api.clearCookies()
    }
    fun refresh() {
        if (token.isBlank()) return
        launchTask {
            val account = try {
                api.post("otp/session", auth().put("purpose", "customer_service"))
            } catch (e: CoreFailure) {
                if (e.code == "nsc_session_expired") {
                    return@launchTask {
                        clearSession()
                        error = "نشست حساب پایان یافته است. برای ادامه دوباره وارد شوید."
                    }
                }
                JSONObject()
            } catch (_: java.io.IOException) {
                JSONObject()
            }
            val responseMobile = normalizedDigits(account.optString("mobile")).trim()
                .replace(Regex("^(?:\\+98|0098|98)(?=9[0-9]{9}$)"), "0")
            val responseToken = account.optString("verification_token")
            val matchingAccount = account.optBoolean("success") && responseMobile == mobile &&
                account.optString("purpose", "customer_service") == "customer_service"
            val renewedToken = responseToken.takeIf {
                matchingAccount && (it == token || it.matches(Regex("[A-Za-z0-9]{48}")))
            }
            // A successful account-scoped read also proves the existing credential
            // when Stage cannot return the optional session metadata.
            val credentials = auth().put("verification_token", renewedToken ?: token)
            val rows = try {
                val response = api.post("my-requests", credentials)
                response.optJSONArray("requests")?.takeIf { response.optBoolean("success") }?.objects()
            } catch (e: CoreFailure) {
                if (e.code == "nsc_my_requests_auth" && renewedToken == null) throw e
                null
            } catch (_: java.io.IOException) {
                null
            }
            if (renewedToken == null && rows == null && !loggedIn) {
                throw CoreFailure("session_response_invalid", "بررسی نشست کامل نشد. دوباره تلاش کنید.")
            }
            if (renewedToken != null && sessionTtlSeconds > 0) {
                persistSession(mobile, renewedToken, sessionTtlSeconds)
            }
            val catalog = runCatching { api.config() }
            return@launchTask {
                if (renewedToken != null) token = renewedToken
                catalog.getOrNull()?.let { config = it }
                if (config == null) error = "دریافت فهرست خدمات کامل نشد. دوباره تلاش کنید."
                loggedIn = true
                if (rows != null) { requests = rows; requestsLoaded = true }
                if (matchingAccount) {
                    accountName = account.optString("customer_name")
                    if (name.isBlank()) name = accountName
                }
            }
        }
    }
    fun sendOtp(number: String, remember: Boolean = false) {
        if (loggedIn || hasSavedSession) return
        val numberClean=normalizedDigits(number).filter { it.isDigit() }
        if(!numberClean.matches(Regex("09[0-9]{9}"))) { error="شماره موبایل معتبر وارد کنید.";return }
        if(System.currentTimeMillis()<resendAt) return
        launchTask {
            val r=api.post("otp/send",JSONObject().put("mobile",numberClean).put("purpose","customer_service").put("website",""))
            if(!r.optBoolean("success")) throw CoreFailure("otp_failed","ارسال کد تأیید نشد.")
            if(r.optBoolean("dev_mode")) throw CoreFailure("otp_dev_mode","Core در حالت کد آزمایشی است؛ ارسال واقعی پیامک تأیید نشد. تنظیم ارسال واقعی باید در Stage اصلاح شود.")
            val until=System.currentTimeMillis()+r.optInt("resend_in",90)*1000L
            return@launchTask { mobile=numberClean;rememberMobile(remember,numberClean);otpSent=true;resendAt=until;message=r.optString("message") }
        }
    }
    fun editOtpMobile() {
        if (busy || loggedIn || hasSavedSession) return
        otpSent = false
        error = ""
        message = ""
    }

    fun verifyOtp(code: String) {
        if (loggedIn || hasSavedSession || !otpSent) return
        val normalized=normalizedDigits(code).trim()
        if(!normalized.matches(Regex("[0-9]{4}"))) { error="کد چهاررقمی را کامل وارد کنید.";return }
        launchTask {
            val r=api.post("otp/verify",JSONObject().put("mobile",mobile).put("code",normalized).put("purpose","customer_service"))
            val t=r.optString("verification_token")
            if(!r.optBoolean("success")||t.isBlank())throw CoreFailure("otp_verify_invalid","نشست معتبر دریافت نشد.")
            val ttl = r.getLong("verified_for")
            require(ttl > 0)
            persistSession(mobile, t, ttl)
            return@launchTask {
                tab = "home"
                token = t
                sessionTtlSeconds = ttl
                loggedIn = true
                otpSent = false
                message = "با موفقیت وارد شدید."
                main.post { refresh() }
            }
        }
    }
    fun logout() { launchTask { api.post("otp/logout",auth()); return@launchTask {clearSession();tab="home";message="از حساب خارج شدید."} } }
    fun loadRequests() { if(!loggedIn){tab="profile";return};launchTask {val r=api.post("my-requests",auth()); return@launchTask {requests=r.array("requests").objects();requestsLoaded=true;tab="requests";receipt?.let { rc->receiptInAccount=requests.any { it.optString("tracking_code")==rc.optString("tracking_code") }}} } }
    fun loadAddresses() { if(!loggedIn)return;launchTask {val r=api.post("customer-addresses",auth().put("action","list"));return@launchTask {addresses=r.array("rows").objects()}} }
    fun saveRequestAddress(label: String) {
        if (!loggedIn || address.trim().length < 8 || latitude.toDoubleOrNull() == null || longitude.toDoubleOrNull() == null) {
            error = "برای ذخیره نشانی، آدرس دقیق و موقعیت آن را وارد کنید."
            return
        }
        val payload = auth().put("action", "save").put("label", label)
            .put("address", address.trim()).put("latitude", latitude).put("longitude", longitude)
        val existing = store.read("address_operation")
        val signature = JSONObject(payload.toString()).apply { remove("verification_token") }.toString()
        val operation = existing?.takeIf { it.optString("signature") == signature }?.optString("id")
            ?: UUID.randomUUID().toString()
        store.write("address_operation", JSONObject().put("signature", signature).put("id", operation))
        launchTask {
            api.post("customer-addresses", payload.put("operation_key", operation))
            val rows = api.post("customer-addresses", auth().put("action", "list")).array("rows").objects()
            return@launchTask {
                store.write("address_operation", null)
                addresses = rows
                message = "نشانی در حساب شما ذخیره شد."
            }
        }
    }

    fun track(code: String) { launchTask {
        val client=store.read("client")?.optString("id") ?: UUID.randomUUID().toString().also{store.write("client",JSONObject().put("id",it))}
        val r=api.post("track",JSONObject().put("tracking_code",code).put("mobile",mobile).put("client_id",client))
        return@launchTask { tracking=r;tab="tracking" }
    } }
    fun startRequest(serviceId: String? = null, category: String? = null, deviceId: String? = null) {
        if(!loggedIn){tab="profile";message="برای ثبت درخواست وارد حساب شوید.";return}
        if(pending!=null)return
        val current = config
        if(current == null) {
            launchTask {
                val fresh = api.config()
                return@launchTask { config = fresh; openRequest(serviceId, category, deviceId, fresh) }
            }
        } else openRequest(serviceId, category, deviceId, current)
    }

    private fun openRequest(serviceId: String?, category: String?, deviceId: String?, catalog: JSONObject) {
        if (serviceId != null && catalog.array("services").objects().none { it.optString("id") == serviceId }) {
            error = "این خدمت در فهرست فعلی فعال نیست."
            return
        }
        val selectedDevice = if (deviceId != null && category != null && serviceId != null) {
            catalog.optJSONObject("categories")?.array(category)?.objects()?.firstOrNull {
                it.optString("id") == deviceId && supportsService(it,serviceId)
            } ?: run {
                error = "این دستگاه برای خدمت انتخاب‌شده در فهرست فعلی فعال نیست."
                return
            }
        } else null
        receipt=null;receiptInAccount=false;creating=true;step=0
        if (serviceId != null && item.optString("service_type") != serviceId) changeItem("service_type",serviceId)
        if (category != null && selectedDevice != null) {
            if(item.optString("category") != category) changeItem("category",category)
            if(item.optString("device") != deviceId) chooseDevice(selectedDevice)
        }
    }
    fun changeItem(key:String,value:String) {
        val next=JSONObject(item.toString())
        NativeRules.invalidatedFields(key).forEach(next::remove)
        if(key in setOf("service_type","category","device"))model=""
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
        if(NativeRules.isCustomBrand(item.optString("brand"))&&item.optString("custom_brand").trim().length<2)return "نام برند دستگاه را وارد کنید."
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
    fun currentItemForReview():JSONObject=currentItem()
    private fun currentItem():JSONObject=JSONObject(item.toString()).apply { if(NativeRules.isCustomBrand(optString("brand")))put("brand",optString("custom_brand").trim());remove("custom_brand") }.put("device_model",model).put("service_location",if(item.optString("service_type")=="install")"onsite" else item.optString("service_location"))
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
    fun addImages(uris:List<Uri>) {
        if(!loggedIn||pending!=null||uris.isEmpty())return
        val maxFiles=ImageRules.maxFiles(config?.optInt("maxFiles",3)?:3)
        val maxBytes=ImageRules.maxBytes(config?.optInt("maxUploadMB",5)?:5)
        if(images.size+uris.size>maxFiles){error="حداکثر $maxFiles تصویر طبق تنظیمات Core مجاز است.";return}
        launchTask {
            val imported=mutableListOf<JSONObject>()
            try {uris.forEach { imported+=imageStore.import(it,maxBytes) }}
            catch(e:Exception){imported.forEach(imageStore::remove);throw e}
            return@launchTask {images=images+imported;saveDraft()}
        }
    }
    fun imagePreview(meta:JSONObject)=imageStore.preview(meta)
    fun removeImage(id:String) {
        if(busy||pending!=null)return
        images.find{it.optString("id")==id}?.let(imageStore::remove)
        images=images.filter{it.optString("id")!=id};saveDraft()
    }
    fun saveDraft() {
        store.write("draft",JSONObject().put("images",JSONArray(images)).put("owner_mobile",mobile).put("item",item).put("items",JSONArray(items)).put("name",name).put("model",model).put("description",description).put("city",city).put("area",area).put("address",address).put("date",date).put("slot",slot).put("latitude",latitude).put("longitude",longitude).put("accuracy",accuracy))
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
        payload.put("local_images",JSONArray(images))
        pending=payload;store.write("pending",payload);retryPending()
    }
    fun retryPending() {
        val saved=pending?:return
        if(!loggedIn){tab="profile";return}
        if(saved.optString("owner_mobile")!=mobile){error="ثبت معلق متعلق به حساب دیگری است؛ به همان حساب وارد شوید.";return}
        launchTask {
            val payload=JSONObject(saved.toString()).put("mobile",mobile).put("verification_token",token)
            val selected=saved.array("local_images").objects()
            val maxFiles=ImageRules.maxFiles(config?.optInt("maxFiles",3)?:3)
            val maxBytes=ImageRules.maxBytes(config?.optInt("maxUploadMB",5)?:5)
            if(selected.size>maxFiles||selected.any{it.optLong("size")>maxBytes})throw CoreFailure("image_limits_changed","محدودیت تصاویر در Core تغییر کرده است؛ ثبت قبلی را با پشتیبانی بررسی کنید.")
            payload.remove("local_images");payload.remove("owner_mobile")
            val result=api.post("requests",payload,selected,imageStore)
            val filesConfirmed=selected.isEmpty()||(!result.optBoolean("duplicate")&&result.array("attachments").length()==selected.size)
            result.put("local_image_count",selected.size).put("local_images_confirmed",filesConfirmed)
            if(!result.optBoolean("success")||result.optInt("request_id")<=0||result.optString("tracking_code").isBlank())throw CoreFailure("submission_invalid","پاسخ ثبت درخواست معتبر نیست. همان درخواست را دوباره بررسی کنید.")
            // Persist the receipt before the read-back, so a list/network failure never loses the tracking code.
            if(!filesConfirmed)result.put("local_unconfirmed_images",JSONArray(selected))
            store.write("receipt",result);store.write("pending",null);store.write("draft",null)
            if(filesConfirmed)selected.forEach(imageStore::remove)
            var rows:List<JSONObject>?=null
            try {rows=api.post("my-requests",auth()).array("requests").objects()}catch(_:Exception){}
            return@launchTask {receipt=result;pending=null;images=emptyList();receiptInAccount=rows?.any{it.optString("tracking_code")==result.optString("tracking_code")}==true
                if(rows!=null){requests=rows!!;requestsLoaded=true}
                creating=false;tab="receipt";item=JSONObject();items=emptyList();terms=false;store.write("draft",null)}
        }
    }
    override fun onCleared(){ executor.shutdown();super.onCleared() }
}
