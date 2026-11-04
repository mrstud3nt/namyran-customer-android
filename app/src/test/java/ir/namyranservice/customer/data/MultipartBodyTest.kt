package ir.namyranservice.customer.data
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
class MultipartBodyTest {
 @Test fun lengthCountsUtf8AndBinaryExactly(){
  val image=byteArrayOf(0,13,10,-1,0,7)
  val body=MultipartBody("stage-test",listOf("customer_name" to "سیروس آزمایشی","order_items" to "[{\"device\":\"تلویزیون\"}]"),listOf(MultipartBody.Part("image-1.png","image/png",image.size.toLong()){image}))
  val out=ByteArrayOutputStream();body.writeTo(out);val bytes=out.toByteArray()
  assertEquals(bytes.size.toLong(),body.contentLength)
  val text=String(bytes,Charsets.UTF_8);assertTrue(text.contains("name=\"customer_name\"\r\n\r\nسیروس آزمایشی\r\n"));assertTrue(text.contains("name=\"attachments[]\"; filename=\"image-1.png\""));assertTrue(text.endsWith("\r\n--stage-test--\r\n"))
 }
 @Test fun framingSupportsMultipleFilesAndDetectsChangedCopies(){
  val body=MultipartBody("boundary",listOf("verification_token" to "test-only"),listOf(MultipartBody.Part("image-1.jpg","image/jpeg",2){byteArrayOf(1,2)},MultipartBody.Part("image-2.webp","image/webp",3){byteArrayOf(3,4,5)}))
  val out=ByteArrayOutputStream();body.writeTo(out);assertEquals(out.size().toLong(),body.contentLength)
  try{MultipartBody("b",emptyList(),listOf(MultipartBody.Part("image-1.png","image/png",2){byteArrayOf(1)})).writeTo(ByteArrayOutputStream());fail("Changed private file accepted")}catch(_:IllegalStateException){}
 }
}
