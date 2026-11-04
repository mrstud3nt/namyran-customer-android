package ir.namyranservice.customer.domain

object ImageRules {
    fun maxFiles(value:Int)=value.coerceIn(1,3)
    fun maxBytes(value:Int)=value.coerceIn(1,20)*1024L*1024L
    fun kind(bytes:ByteArray):Pair<String,String>? {
        fun prefix(vararg values:Int)=bytes.size>=values.size&&values.indices.all{(bytes[it].toInt() and 255)==values[it]}
        return when {
            prefix(255,216,255)->"image/jpeg" to "jpg"
            prefix(137,80,78,71,13,10,26,10)->"image/png" to "png"
            bytes.size>=12&&String(bytes,0,4,Charsets.US_ASCII)=="RIFF"&&String(bytes,8,4,Charsets.US_ASCII)=="WEBP"->"image/webp" to "webp"
            else->null
        }
    }
}
