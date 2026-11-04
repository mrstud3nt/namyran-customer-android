package ir.namyranservice.customer.domain
import org.junit.Assert.*
import org.junit.Test
class ImageRulesTest {
 @Test fun acceptsOnlySupportedSignatures(){
  assertEquals("image/jpeg",ImageRules.kind(byteArrayOf(-1,-40,-1,0))?.first)
  assertEquals("image/png",ImageRules.kind(byteArrayOf(-119,80,78,71,13,10,26,10))?.first)
  assertEquals("image/webp",ImageRules.kind("RIFF0000WEBP".toByteArray())?.first)
  assertNull(ImageRules.kind("<svg>pretend.jpg</svg>".toByteArray()))
  assertNull(ImageRules.kind(byteArrayOf()))
  assertNull(ImageRules.kind("GIF89a".toByteArray()))
 }
 @Test fun mirrorsServerLimits(){assertEquals(1,ImageRules.maxFiles(0));assertEquals(3,ImageRules.maxFiles(99));assertEquals(1048576L,ImageRules.maxBytes(0));assertEquals(20971520L,ImageRules.maxBytes(999))}
}
