package ir.namyranservice.customer.data

import android.content.Context
import android.net.Uri
import android.graphics.BitmapFactory
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Private encrypted copies: no persistent shared URI permission or public-storage copy. */
class AttachmentStore(private val context: Context) {
    private val directory = File(context.noBackupFilesDir, "request_images").apply { mkdirs() }
    private fun key(): SecretKey {
        val ks=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey("namyran_customer_images",null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("namyran_customer_images",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private fun file(id:String):File { require(id.matches(Regex("[a-f0-9-]{36}")));return File(directory,id) }
    fun import(uri:Uri,maxBytes:Long):JSONObject {
        val bytes=context.contentResolver.openInputStream(uri)?.use { input ->
            val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
            while(true){val n=input.read(buffer);if(n<0)break;if(out.size().toLong()+n>maxBytes)throw CoreFailure("image_size","حجم تصویر بیشتر از حد مجاز Core است.");out.write(buffer,0,n)}
            out.toByteArray()
        } ?: throw CoreFailure("image_read","تصویر قابل خواندن نیست؛ دوباره انتخاب کنید.")
        val kind=ir.namyranservice.customer.domain.ImageRules.kind(bytes)
            ?: throw CoreFailure("image_type","فقط تصویر JPEG، PNG یا WebP انتخاب کنید.")
        val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
        if(bounds.outWidth<=0||bounds.outHeight<=0)throw CoreFailure("image_invalid","فایل تصویر معتبر نیست.")
        val id=UUID.randomUUID().toString();val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply{init(Cipher.ENCRYPT_MODE,key())}
        val temp=File(directory,"$id.tmp")
        try { java.io.FileOutputStream(temp).use { it.write(cipher.iv);it.write(cipher.doFinal(bytes));it.fd.sync() };check(temp.renameTo(file(id))) }
        finally {temp.delete()}
        return JSONObject().put("id",id).put("mime",kind.first).put("extension",kind.second).put("size",bytes.size).put("sha256",hash(bytes))
    }
    fun read(meta:JSONObject):ByteArray {
        val encrypted=file(meta.getString("id")).readBytes()
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply{init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,encrypted.copyOfRange(0,12)))}
        val bytes=cipher.doFinal(encrypted.copyOfRange(12,encrypted.size))
        check(bytes.size==meta.getInt("size")&&hash(bytes)==meta.getString("sha256"))
        return bytes
    }
    fun preview(meta:JSONObject):android.graphics.Bitmap? {
        val bytes=read(meta);val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
        BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
        var sample=1;while(bounds.outWidth/sample>512||bounds.outHeight/sample>512)sample*=2
        return BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply{inSampleSize=sample})
    }
    fun remove(meta:JSONObject){file(meta.getString("id")).delete()}
    fun clear(){directory.listFiles()?.forEach { it.delete() }}
    private fun hash(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
}
