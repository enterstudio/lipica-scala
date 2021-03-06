package org.lipicalabs.lipica.core.net.lpc.message

import org.lipicalabs.lipica.core.crypto.digest.{Digest256, DigestValue}
import org.lipicalabs.lipica.core.net.lpc.LpcMessageCode
import org.lipicalabs.lipica.core.net.message.Message
import org.lipicalabs.lipica.core.bytes_codec.RBACCodec
import org.lipicalabs.lipica.core.utils.ImmutableBytes

/**
 * 指定するハッシュ値の直前N個のハッシュ値を送るよう要求するメッセージです。
 * 返信として BlockHashes メッセージを期待します。
 *
 * Created by IntelliJ IDEA.
 * 2015/12/09 20:57
 * YANAGISAWA, Kentaro
 */
class GetBlockHashesMessage(val bestHash: DigestValue, val maxBlocks: Int) extends LpcMessage {

	override def toEncodedBytes = {
		val encodedHash = RBACCodec.Encoder.encode(this.bestHash)
		val encodedMaxBlocks = RBACCodec.Encoder.encode(this.maxBlocks)
		RBACCodec.Encoder.encodeSeqOfByteArrays(Seq(encodedHash, encodedMaxBlocks))
	}

	override def code = LpcMessageCode.GetBlockHashes.asByte

	override def answerMessage: Option[Class[_ <: Message]] = Option(classOf[BlockHashesMessage])

}

object GetBlockHashesMessage {

	def decode(encodedBytes: ImmutableBytes): GetBlockHashesMessage = {
		val items = RBACCodec.Decoder.decode(encodedBytes).right.get.items
		new GetBlockHashesMessage(Digest256(items.head.bytes), items(1).asInt)
	}
}
