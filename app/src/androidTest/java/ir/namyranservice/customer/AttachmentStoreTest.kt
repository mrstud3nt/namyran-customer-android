package ir.namyranservice.customer

import android.graphics.Bitmap
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.namyranservice.customer.data.AttachmentStore
import ir.namyranservice.customer.data.CoreFailure
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AttachmentStoreTest {
 @Test fun encryptedCopySurvivesOriginalRemovalAndRejectsTampering(){
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  val original=File(context.cacheDir,"attachment-store-test.png")
  val bitmap=Bitmap.createBitmap(3,3,Bitmap.Config.ARGB_8888)
  original.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
  val expected=original.readBytes();val store=AttachmentStore(context)
  val meta=store.import(Uri.fromFile(original),1024*1024)
  val encrypted=File(context.noBackupFilesDir,"request_images/"+meta.getString("id"))
  try {
   assertFalse(encrypted.readBytes().contentEquals(expected));original.delete()
   assertArrayEquals(expected,AttachmentStore(context).read(meta))
   val damaged=encrypted.readBytes();damaged[damaged.lastIndex]=(damaged.last().toInt() xor 1).toByte();encrypted.writeBytes(damaged)
   try{store.read(meta);fail("Corrupted ciphertext accepted")}catch(_:javax.crypto.AEADBadTagException){}
  } finally {store.remove(meta);original.delete()}
 }
 @Test fun rejectsOversizeAndDisguisedFiles(){
  val context=InstrumentationRegistry.getInstrumentation().targetContext;val source=File(context.cacheDir,"disguised.png");val store=AttachmentStore(context)
  try{
   source.writeText("<svg>not a PNG</svg>")
   try{store.import(Uri.fromFile(source),1024);fail("Non-image accepted")}catch(e:CoreFailure){assertEquals("image_type",e.code)}
   try{store.import(Uri.fromFile(source),2);fail("Oversize accepted")}catch(e:CoreFailure){assertEquals("image_size",e.code)}
  }finally{source.delete()}
 }
}
