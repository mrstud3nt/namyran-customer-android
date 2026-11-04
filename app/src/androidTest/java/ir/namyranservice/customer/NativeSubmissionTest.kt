package ir.namyranservice.customer

import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import ir.namyranservice.customer.data.SecureStore
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

/** Opt-in real Stage mutation. Credentials only via local instrumentation arguments, never source/artifacts. */
@RunWith(AndroidJUnit4::class)
class NativeSubmissionTest {
 @get:Rule val ui=createEmptyComposeRule()
 @Test fun submitAuthorizedStageRequestWithImage(){
  val instrumentation=InstrumentationRegistry.getInstrumentation();val args=InstrumentationRegistry.getArguments()
  val phone=args.getString("authorized_phone");val token=args.getString("authorized_token")
  Assume.assumeTrue("Requires locally supplied authorized Stage session",!phone.isNullOrBlank()&&!token.isNullOrBlank())
  val context=instrumentation.targetContext
  // Optional explicit CA for a local intercepted test network; never packaged in the app.
  // Hostname verification stays at the Android default. Stock CI smoke uses platform trust.
  val originalTls=javax.net.ssl.HttpsURLConnection.getDefaultSSLSocketFactory()
  val testCa=File(context.filesDir,"native-test-ca.pem")
  if(testCa.exists()){
   val ks=java.security.KeyStore.getInstance(java.security.KeyStore.getDefaultType()).apply{load(null)}
   testCa.inputStream().use{input->java.security.cert.CertificateFactory.getInstance("X.509").generateCertificates(input).forEachIndexed{i,cert->ks.setCertificateEntry("local-test-$i",cert)}}
   val tm=javax.net.ssl.TrustManagerFactory.getInstance(javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm()).apply{init(ks)}
   val ssl=javax.net.ssl.SSLContext.getInstance("TLS").apply{init(null,tm.trustManagers,null)}
   javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(ssl.socketFactory)
  }
  SecureStore(context).write("session",JSONObject().put("mobile",phone).put("token",token).put("expires",System.currentTimeMillis()+3600000))
  val source=File(context.cacheDir,"native-stage-test.png");val bitmap=Bitmap.createBitmap(16,16,Bitmap.Config.ARGB_8888)
  bitmap.eraseColor(android.graphics.Color.BLUE);source.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
  val scenario=ActivityScenario.launch(MainActivity::class.java)
  lateinit var vm:CustomerViewModel
  scenario.onActivity{vm=ViewModelProvider(it)[CustomerViewModel::class.java]}
  fun ready(){ui.waitUntil(120000){!vm.busy};ui.waitForIdle();assertTrue("Stage operation failed (see UI)",vm.error.isBlank())}
  fun click(label:String){ui.onNodeWithText(label,useUnmergedTree=false).performScrollTo().performClick()}
  fun choose(label:String,value:String){click(label);ui.onNodeWithText(value).performClick()}
  try{
   ready();assertTrue("Authorized session refused",vm.loggedIn)
   click("ثبت درخواست جدید");ready()
   choose("نوع خدمت","تعمیر تخصصی");choose("گروه دستگاه","صوتی و تصویری");choose("دستگاه","تلویزیون");choose("برند","LG")
   click("ادامه")
   ui.onNodeWithText("نام و نام خانوادگی").performScrollTo().performTextReplacement("سیروس قبادی")
   choose("شهر محل خدمت","شیراز")
   ui.onNodeWithText("نشانی دقیق، پلاک و واحد").performScrollTo().performTextReplacement("سرداران، خیابان مهر؛ تست Stage، بدون اعزام")
   ui.onNodeWithText("شرح مشکل یا نیاز به خدمت").performScrollTo().performTextReplacement("تست خودکار Native با تصویر؛ بدون خرابی واقعی و بدون اعزام. برند پیش‌فرض آزمایشی.")
   // Exercises the same callback as the document picker; system document UI is a separate manual check.
   scenario.onActivity{vm.addImages(listOf(Uri.fromFile(source)))};ready()
   ui.onNodeWithText("تصویر 1 ·",substring=true).performScrollTo().assertExists()
   scenario.onActivity{vm.latitude="29.5918";vm.longitude="52.5837";vm.saveDraft()}
   click("ادامه")
   // Native schedule request uses a future date; date dialog interaction is separately smoke-tested manually.
   scenario.onActivity{vm.getSchedule(LocalDate.now().plusDays(2).toString())};ready()
   val slot=vm.schedule!!.getJSONArray("slots");val available=(0 until slot.length()).map{slot.getJSONObject(it)}.first{it.getBoolean("available")}.getString("value")
   click(available);click("ادامه")
   ui.onNodeWithText("تصویر 1 ·",substring=true).performScrollTo().assertExists()
   ui.onNode(isToggleable()).performScrollTo().performClick();click("ثبت نهایی درخواست");ready()
   assertNotNull("No real receipt",vm.receipt);assertTrue("Image storage unconfirmed",vm.receipt!!.getBoolean("local_images_confirmed"))
   assertEquals(1,vm.receipt!!.getInt("local_image_count"));assertTrue("Request missing from account",vm.receiptInAccount)
   ui.onNodeWithText("درخواست ثبت شد").performScrollTo().assertExists()
   click("مشاهده درخواست در حساب");ready()
   assertTrue(vm.requests.any{it.optString("tracking_code")==vm.receipt!!.getString("tracking_code")})
   // Persist sanitized evidence privately; the runner never prints phone, token, OTP or tracking code.
   File(context.filesDir,"native-submission-evidence.json").writeText(JSONObject().put("success",true).put("request_id",vm.receipt!!.getInt("request_id")).put("image_count",1).put("account_readback",true).put("picker_callback",true).put("system_picker_ui_tested",false).put("otp_ui_tested",false).toString())
  }finally{scenario.close();source.delete();testCa.delete();javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(originalTls)}
 }
}
