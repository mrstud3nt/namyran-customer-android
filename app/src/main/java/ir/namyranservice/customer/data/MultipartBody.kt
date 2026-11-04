package ir.namyranservice.customer.data

import java.io.OutputStream

/** Byte-exact UTF-8 multipart framing. PHP intake needs a known Content-Length. */
class MultipartBody(val boundary:String, fields:List<Pair<String,String>>, private val files:List<Part>) {
    data class Part(val filename:String,val mime:String,val size:Long,val read:()->ByteArray)
    private fun encode(value:String)=value.toByteArray(Charsets.UTF_8)
    init {
        require(boundary.matches(Regex("[A-Za-z0-9-]{1,70}")))
        require(fields.all{it.first.matches(Regex("[a-zA-Z0-9_]+"))})
        require(files.all{it.size>0&&it.filename.matches(Regex("image-[0-9]+\\.(jpg|png|webp)"))&&it.mime in setOf("image/jpeg","image/png","image/webp")})
    }
    private val fieldParts=fields.map{(key,value)->encode("--$boundary\r\nContent-Disposition: form-data; name=\"$key\"\r\n\r\n$value\r\n")}
    private val fileHeaders=files.map{encode("--$boundary\r\nContent-Disposition: form-data; name=\"attachments[]\"; filename=\"${it.filename}\"\r\nContent-Type: ${it.mime}\r\n\r\n")}
    private val end=encode("--$boundary--\r\n")
    val contentLength:Long=fieldParts.sumOf{it.size.toLong()}+files.indices.sumOf{fileHeaders[it].size+files[it].size+2}+end.size
    fun writeTo(out:OutputStream){
        fieldParts.forEach(out::write)
        files.forEachIndexed{index,file->
            val bytes=file.read();check(bytes.size.toLong()==file.size){"Private image size changed"}
            out.write(fileHeaders[index]);out.write(bytes);out.write(byteArrayOf(13,10))
        }
        out.write(end)
    }
}
