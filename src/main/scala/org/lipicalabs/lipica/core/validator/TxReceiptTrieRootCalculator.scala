package org.lipicalabs.lipica.core.validator

import org.lipicalabs.lipica.core.base.TransactionReceipt
import org.lipicalabs.lipica.core.crypto.digest.DigestUtils
import org.lipicalabs.lipica.core.trie.TrieImpl
import org.lipicalabs.lipica.core.utils.{RBACCodec, ImmutableBytes}

/**
 *
 * @since 2015/12/28
 * @author YANAGISAWA, Kentaro
 */
object TxReceiptTrieRootCalculator {
	def calculateReceiptsTrieRoot(receipts: Seq[TransactionReceipt]): ImmutableBytes = {
		if (receipts.isEmpty) {
			return DigestUtils.EmptyTrieHash
		}
		val trie = new TrieImpl(null)
		for (i <- receipts.indices) {
			val key = RBACCodec.Encoder.encode(i)
			val value = receipts(i).encode
			trie.update(key, value)
		}
		trie.rootHash

	}
}
