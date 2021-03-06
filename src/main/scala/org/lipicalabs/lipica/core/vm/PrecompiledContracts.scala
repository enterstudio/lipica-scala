package org.lipicalabs.lipica.core.vm

import org.lipicalabs.lipica.core.crypto.digest.DigestUtils
import org.lipicalabs.lipica.core.crypto.elliptic_curve.{ECPublicKey, ECDSASignature}
import org.lipicalabs.lipica.core.utils.ImmutableBytes

/**
 * Lipicaシステムにあらかじめ組み込まれている
 * コントラクト（自動エージェント）の実装です。
 *
 * @since 2015/10/24
 * @author YANAGISAWA, Kentaro
 */
object PrecompiledContracts {

	trait PrecompiledContract {
		def manaForData(data:ImmutableBytes): Long
		def execute(data: ImmutableBytes): ImmutableBytes
	}

	private def computeManaByWords(data: ImmutableBytes, param1: Int, param2: Int): Int = {
		if (data eq null) return param1
		param1 + ((data.length + 31) / 32) * param2
	}

	/**
	 * 渡されたデータそれ自身を返すコントラクト。
	 */
	object Identity extends PrecompiledContract {
		override def manaForData(data: ImmutableBytes): Long = {
			computeManaByWords(data, 15, 3)
		}
		override def execute(data: ImmutableBytes): ImmutableBytes = data
	}

	/**
	 * 渡されたデータのSHA256ダイジェスト値を計算して返すコントラクト。
	 */
	object Sha256 extends PrecompiledContract {
		override def manaForData(data: ImmutableBytes): Long = {
			computeManaByWords(data, 60, 12)
		}
		override def execute(data: ImmutableBytes): ImmutableBytes = {
			if (data eq null) {
				ImmutableBytes(DigestUtils.sha2_256(Array.emptyByteArray))
			} else {
				data.sha2_256
			}
		}
	}

	/**
	 * 渡されたデータのRIPEMPD160ダイジェスト値を計算して返すコントラクト。
	 */
	object Ripempd160 extends PrecompiledContract {
		override def manaForData(data: ImmutableBytes): Long = {
			computeManaByWords(data, 600, 120)
		}
		override def execute(data: ImmutableBytes): ImmutableBytes = {
			if (data eq null) {
				val bytes = DigestUtils.ripemd160(Array.emptyByteArray)
				ImmutableBytes.expand(bytes, 0, bytes.length, VMWord.NumberOfBytes)
			} else {
				data.ripemd160(VMWord.NumberOfBytes)
			}
		}
	}

	object ECRecover extends PrecompiledContract {
		override def manaForData(data: ImmutableBytes): Long = 3000
		override def execute(data: ImmutableBytes): ImmutableBytes = {
			val h = new Array[Byte](32)
			val v = new Array[Byte](32)
			val r = new Array[Byte](32)
			val s = new Array[Byte](32)

			try {
				data.copyTo(0, h, 0, 32)
				data.copyTo(32, v, 0, 32)
				data.copyTo(64, r, 0, 32)
				val sLength: Int = if (data.length < 128) data.length - 96 else 32
				data.copyTo(96, s, 0, sLength)
				val signature = ECDSASignature(r, s, v(31))
				val key = ECPublicKey.recoverFromSignature(h, signature.toBase64).get
				val addr = key.toAddress
				ImmutableBytes.expand(addr.toByteArray, 0, addr.length, VMWord.NumberOfBytes)
			} catch {
				case any: Throwable => ImmutableBytes.empty
			}
		}
	}

	def getContractForAddress(address: VMWord): Option[PrecompiledContract] = {
		if (address eq null) return Some(Identity)

		if (address.isHex("0000000000000000000000000000000000000000000000000000000000000001")) return Some(ECRecover)
		if (address.isHex("0000000000000000000000000000000000000000000000000000000000000002")) return Some(Sha256)
		if (address.isHex("0000000000000000000000000000000000000000000000000000000000000003")) return Some(Ripempd160)
		if (address.isHex("0000000000000000000000000000000000000000000000000000000000000004")) return Some(Identity)

		None
	}

}
