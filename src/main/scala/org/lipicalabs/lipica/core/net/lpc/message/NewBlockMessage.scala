package org.lipicalabs.lipica.core.net.lpc.message

import org.lipicalabs.lipica.core.kernel.Block
import org.lipicalabs.lipica.core.net.lpc.LpcMessageCode
import org.lipicalabs.lipica.core.bytes_codec.RBACCodec
import org.lipicalabs.lipica.core.utils.{BigIntBytes, ImmutableBytes}

/**
 * Created by IntelliJ IDEA.
 * 2015/12/09 21:04
 * YANAGISAWA, Kentaro
 */
case class NewBlockMessage(block: Block, difficulty: BigIntBytes) extends LpcMessage {

	override def toEncodedBytes = {
		val encodedBlock = this.block.encode
		val encodedDiff = RBACCodec.Encoder.encode(this.difficulty)
		RBACCodec.Encoder.encodeSeqOfByteArrays(Seq(encodedBlock, encodedDiff))
	}

	override def code = LpcMessageCode.NewBlock.asByte

	override def toString: String = "NewBlockMessage(difficulty=%s)".format(this.difficulty)

}

object NewBlockMessage {
	def decode(encodedBytes: ImmutableBytes): NewBlockMessage = {
		val items = RBACCodec.Decoder.decode(encodedBytes).right.get.items
		val block = Block.decode(items.head.items)
		val difficulty = BigIntBytes(items(1).bytes)
		new NewBlockMessage(block, difficulty)
	}
}
