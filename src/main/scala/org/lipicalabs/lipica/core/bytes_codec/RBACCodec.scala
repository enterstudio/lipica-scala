package org.lipicalabs.lipica.core.bytes_codec

import java.io.PrintStream
import java.nio.charset.StandardCharsets

import org.lipicalabs.lipica.core.crypto.digest.DigestValue
import org.lipicalabs.lipica.core.kernel.Address
import org.lipicalabs.lipica.core.net.peer_discovery.NodeId
import org.lipicalabs.lipica.core.utils._

import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer

/**
 * 入れ子になったバイト配列コンテナ（Recursive Byte Array Container）のCodecです。
 *
 * @author YANAGISAWA, Kentaro
 */
object RBACCodec {

	/**
	 * この値以上の長さのデータをエンコードすることはできない。
	 * 2の64乗。
	 * ただし、現状実装においては符号付き32ビット整数分のデータしか扱っていないので、
	 * この値に達することはない。
	 */
	//private val ItemLengthCeil: Double = Math.pow(256, 8)

	/**
	 * 短いリストと長いリストとの境界値。
	 * この長さ以上のリストは長いリスト扱いになる。
	 */
	private val SIZE_THRESHOLD = 56

	/**
	 * [0x00, 0x7f] （すなわち127以下）の範囲の１バイトについては、
	 * そのバイト自身がエンコードされた表現となる。
	 */
	/**
	 * [0x80]
	 * バイト列が0バイト以上55バイト以下の場合、
	 * エンコードされた表現は、
	 * 0x80（すなわち128）にバイト列の長さを足した１バイトで開始され、
	 * その後にバイト列が続く。
	 * ゆえに最初のバイトは、[0x80, 0xb7]（すなわち128以上183以下）となる。
	 */
	private val OFFSET_SHORT_ITEM = 0x80

	/**
	 * [0xb7]
	 * バイト列が55バイトよりも長い場合、
	 * エンコードされた表現は、
	 * 0xb7（すなわち183）にバイト列の長さの長さを足した１バイトで開始され、
	 * 次にバイト列の長さ、そしてその後にバイト列が続く。
	 * たとえば1024バイトのバイト列は、0xb9, 0x04, 0x00 の３バイトで開始され、
	 * その後にバイト列本体が続く。
	 * ゆえに最初のバイトは、[0xb8, 0xbf]（すなわち184以上191以下）となる。
	 */
	private val OFFSET_LONG_ITEM = 0xb7

	/**
	 * [0xc0]
	 * リストのペイロード全体（すなわち、リストの個々の要素の長さをすべて合計した値）が
	 * 0バイト以上55バイト以下である場合、
	 * エンコードされた表現は、0xc0（すなわち192）にリストの容量を加えた１バイトに、
	 * エンコードされたリスト要素を結合したものをつなげたものとなる。
	 * したがって、最初のバイトは[0xc0, 0xf7] （すなわち192以上247以下）となる。
	 */
	private val OFFSET_SHORT_LIST = 0xc0

	/**
	 * [0xf7]
	 * リストのペイロード全体（すなわち、リストの個々の要素の長さをすべて合計した値）が
	 * 55バイトよりも大きい場合、
	 * エンコードされた表現は、0xf7（すなわち247）にリストの容量の長さを加えた１バイトに、
	 * リストの容量が続き、その後にリスト本体のエンコードされた表現が続くものとなる。
	 * したがって、最初のバイトは[0xf8, 0xf] （すなわち248以上255以下）となる。
	 */
	private val OFFSET_LONG_LIST = 0xf7

	@tailrec
	private def countBytesRecursively(v: Long, accum: Int): Int = {
		if (v == 0L) return accum
		countBytesRecursively(v >>> 8, accum + 1)
	}

	private def countBytesOfNumber(v: Long): Int = {
		countBytesRecursively(v, 0)
	}

	private def encodeNumberInBigEndian(value: Long, length: Int): ImmutableBytes = {
		val result = new Array[Byte](length)
		(0 until length).foreach {
			i => {
				result(length - 1 - i) = ((value >> (8 * i)) & 0xff).asInstanceOf[Byte]
			}
		}
		ImmutableBytes(result)
	}

	/**
	 * バイト数を節約して、整数をビッグエンディアンのバイト列に変換する。
	 */
	private def bytesFromInt(value: Int): ImmutableBytes = {
		if (value == 0) return ImmutableBytes.empty
		val len = countBytesOfNumber(value)
		encodeNumberInBigEndian(value, len)
	}


	object Encoder {

		/**
		 * 渡された値をバイト列にエンコードします。
		 *
		 * 静的な型付けの観点からは非常に「甘い」が、
		 * ここに妙な型のインスタンスを渡した結果
		 * 実行時例外を投げられてしまう、というバグは、
		 * テストを１回実行すれば容易に発見できるものだ。
		 */
		def encode(v: Any): ImmutableBytes = {
			v match {
				case seq: Seq[_] => encodeSeq(seq)
				case any => encodeItem(any)
			}
		}

		/**
		 * バイト配列の並びをエンコードします。
		 */
		def encodeSeqOfByteArrays(seq: Seq[ImmutableBytes]): ImmutableBytes = {
			if (seq eq null) {
				//要素なしのリストとする。
				return ImmutableBytes.fromOneByte(OFFSET_SHORT_LIST.asInstanceOf[Byte])
			}
			//リスト要素の全ペイロードを合計する。
			val totalLength = seq.foldLeft(0)((accum, each) => accum + each.length)
			//リストのヘッダ部分を構築する。
			val (data: Array[Byte], initialPos: Int) =
				if (totalLength < SIZE_THRESHOLD) {
					//これは短いリストである。
					val d = new Array[Byte](1 + totalLength)
					d(0) = (OFFSET_SHORT_LIST + totalLength).asInstanceOf[Byte]
					(d, 1)
				} else {
					//これは長いリストである。
					//まずは、リストの容量を表現するのに何バイト必要かを数える。
					val byteNum = countBytesOfNumber(totalLength)
					//その結果を利用して、リストの容量をビッグエンディアンでエンコードする。
					val lengthBytes = encodeNumberInBigEndian(totalLength, byteNum)

					//ヘッダ部分を組み立てる。
					val d = new Array[Byte](1 + lengthBytes.length + totalLength)
					d(0) = (OFFSET_LONG_LIST + byteNum).asInstanceOf[Byte]
					lengthBytes.copyTo(0, d, 1, lengthBytes.length)
					(d, lengthBytes.length + 1)
				}
			//リストの要素を、結果配列の中の本体部分にコピーする。
			var copyPos = initialPos
			seq.foreach {
				element => {
					element.copyTo(0, data, copyPos, element.length)
					copyPos += element.length
				}
			}
			ImmutableBytes(data)
		}

		private def encodeSeq(list: Seq[Any]): ImmutableBytes = {
			val listOfBytes = list.map(each => encodeElement(each))
			encodeSeqOfByteArrays(listOfBytes)
		}

		@tailrec
		private def encodeElement(elem: Any): ImmutableBytes = {
			elem match {
				case bytes: ImmutableBytes => encodeItem(bytes)
				case bytes: Array[Byte] => encodeItem(bytes)
				case seq: Seq[_] => encodeSeq(seq)
				case Right(v) => encodeElement(v)
				case Left(v) => encodeElement(v)
				case Some(v) => encodeElement(v)
				case _ => encodeItem(elem)
			}
		}

		/**
		 * １個の値をエンコードします。
		 */
		private def encodeItem(value: Any): ImmutableBytes = {
			value match {
				case null =>
					//空のアイテムとする。
					ImmutableBytes.fromOneByte(OFFSET_SHORT_ITEM.asInstanceOf[Byte])
				case _ =>
					//値をバイト列に変換する。
					val bytes = toBytes(value)
					if (bytes.isEmpty) {
						ImmutableBytes.fromOneByte(OFFSET_SHORT_ITEM.asInstanceOf[Byte])
					} else if ((bytes.length == 1) && ((bytes(0) & 0xff) < OFFSET_SHORT_ITEM)) {
						bytes
					} else {
						val prefix = encodeLength(bytes.length, OFFSET_SHORT_ITEM)
						prefix ++ bytes
					}
			}
		}

		/**
		 * 長さの表現をバイト列にエンコードします。
		 */
		private def encodeLength(length: Int, offset: Int): ImmutableBytes = {
			if (length < SIZE_THRESHOLD) {
				ImmutableBytes(Array((length + offset).asInstanceOf[Byte]))
			} else {
				val binaryLength =
					if (0xff < length) {
						bytesFromInt(length)
					} else {
						ImmutableBytes(Array(length.asInstanceOf[Byte]))
					}
				//渡ってくる offset は、常に短いリストやアイテムの基準点なので、
				//それを長いリストやアイテムに換算するために、SIZE_THRESHOLDを足す。
				val firstByte = (binaryLength.length + offset + SIZE_THRESHOLD - 1).asInstanceOf[Byte]
				firstByte +: binaryLength
			}
		}

		/**
		 * １個の値をバイト列に変換します。
		 */
		@tailrec
		private def toBytes(input: Any): ImmutableBytes = {
			input match {
				case v: ImmutableBytes => v
				case v: Array[Byte] => ImmutableBytes(v)
				case d: DigestValue => d.bytes
				case a: Address => a.bytes
				case v: BigIntBytes => v.bytes
				case n:	 NodeId => n.bytes
				case s: String => ImmutableBytes(s.getBytes(StandardCharsets.UTF_8))
				case v: Long =>
					if (v == 0L) {
						ImmutableBytes.empty
					} else {
					 	ImmutableBytes(ByteUtils.asUnsignedByteArray(BigInt(v)))
					}
				case v: Int =>
					if (v <= 0xff) {
						//１バイト。
						toBytes(v.asInstanceOf[Byte])
					} else if (v <= 0xffff) {
						//２バイト。
						toBytes(v.asInstanceOf[Short])
					} else if (v <= 0xffffff) {
						//３バイト。
						ImmutableBytes(Array(
							(v >>> 16).asInstanceOf[Byte],
							(v >>> 8).asInstanceOf[Byte],
							v.asInstanceOf[Byte]
						))
					} else {
						//４バイト。
						ImmutableBytes(Array(
							(v >>> 24).asInstanceOf[Byte],
							(v >>> 16).asInstanceOf[Byte],
							(v >>> 8).asInstanceOf[Byte],
							v.asInstanceOf[Byte]
						))
					}
				case v: Short =>
					if ((v & 0xFFFF) <= 0xff) {
						//１バイト。
						toBytes(v.asInstanceOf[Byte])
					} else {
						//２バイト。
						ImmutableBytes(Array(
							(v >>> 8).asInstanceOf[Byte],
							v.asInstanceOf[Byte]
						))
					}
				case v: Byte =>
					if (v == 0) {
						ImmutableBytes.empty
					} else {
						ImmutableBytes.fromOneByte((v & 0xff).asInstanceOf[Byte])
					}
				case v: Boolean =>
					if (v) {
						toBytes(1.asInstanceOf[Byte])
					} else {
						toBytes(0.asInstanceOf[Byte])
					}
				case v: BigInt =>
					if (v == UtilConsts.Zero) {
						ImmutableBytes.empty
					} else {
						ImmutableBytes.asUnsignedByteArray(v)
					}
				case _ =>
					throw new RuntimeException("Unsupported type: %s".format(input.getClass + " " + input))
			}
		}
	}

	object Decoder {

		trait DecodedResult {
			def pos: Int
			def isSeq: Boolean
			def bytes: ImmutableBytes
			def items: Seq[DecodedResult]

			def result: Any = {
				if (this.isSeq) mapElementsToBytes(items) else bytes
			}

			def asPositiveLong: Long = asPositiveBigInt.longValue()

			def asInt: Int = asPositiveLong.toInt

			def asByte: Byte = bytes.head

			def asString: String = bytes.asString(StandardCharsets.UTF_8)

			def asPositiveBigInt: BigInt = {
				if (bytes.length == 0) {
					UtilConsts.Zero
				} else {
					bytes.toPositiveBigInt
				}
			}

			private def mapElementsToBytes(seq: Seq[DecodedResult]): Seq[AnyRef] = {
				seq.map {
					each => {
						if (!each.isSeq) {
							each.bytes
						} else {
							mapElementsToBytes(each.items)
						}
					}
				}
			}

			def printRecursively(out: PrintStream): Unit = {
				if (this.isSeq) {
					out.print("[")
					this.items.foreach { each =>
						each.printRecursively(out)
					}
					out.print("]")
				} else {
					out.print(this.bytes.toHexString)
					out.print(", ")
				}
			}
		}

		case class DecodedBytes(override val pos: Int, override val bytes: ImmutableBytes) extends DecodedResult {
			override val isSeq = false
			override val items = List.empty
		}

		case class DecodedSeq(override val pos: Int, override val items: Seq[DecodedResult], override val bytes: ImmutableBytes) extends DecodedResult {
			override val isSeq = true
		}

		def decode(data: Array[Byte]): Either[Exception, DecodedResult] = {
			decode(ImmutableBytes(data))
		}

		def decode(data: ImmutableBytes): Either[Exception, DecodedResult] = {
			decode(data, 0)
		}

		def decode(data: ImmutableBytes, pos: Int): Either[Exception, DecodedResult] = {
			if (data.length < 1) {
				return Left(new IllegalArgumentException("Prefix is lacking."))
			}
			val prefix = data(pos) & 0xFF
			if (prefix == OFFSET_SHORT_ITEM) {
				//空データであることが確定。
				Right(DecodedBytes(pos + 1, ImmutableBytes.empty))
			} else if (prefix < OFFSET_SHORT_ITEM) {
				//１バイトデータ。
				Right(DecodedBytes(pos + 1, ImmutableBytes.fromOneByte(data(pos))))
			} else if (prefix <= OFFSET_LONG_ITEM) {//この判定条件は、バグではない。
			//長さがprefixに含まれている。
				val len = prefix - OFFSET_SHORT_ITEM
				Right(DecodedBytes(pos + 1 + len, data.copyOfRange(pos + 1, pos + 1 + len)))
			} else if (prefix < OFFSET_SHORT_LIST) {
				//長さが２重にエンコードされている。
				val lenlen = prefix - OFFSET_LONG_ITEM
				val len = intFromBytes(data.copyOfRange(pos + 1, pos + 1 + lenlen))
				Right(DecodedBytes(pos + 1 + lenlen + len, data.copyOfRange(pos + 1 + lenlen, pos + 1 + lenlen + len)))
			} else if (prefix <= OFFSET_LONG_LIST) {//この判定条件は、バグではない。
			//単純なリスト。
				val len = prefix - OFFSET_SHORT_LIST
				decodeSeq(data, pos + 1, len, ImmutableBytes.fromOneByte(data(pos)))
			} else if (prefix < 0xFF) {
				//長さが２重にエンコードされている。
				val lenlen = prefix - OFFSET_LONG_LIST
				val len = intFromBytes(data.copyOfRange(pos + 1, pos + 1 + lenlen))
				decodeSeq(data, pos + lenlen + 1, len, data.copyOfRange(pos, pos + lenlen + 1))
			} else {
				Left(new IllegalArgumentException("Illegal prefix: %d".format(prefix)))
			}
		}

		private def decodeSeq(data: ImmutableBytes, pos: Int, len: Int, initialBytes: ImmutableBytes): Either[Exception, DecodedSeq] = {
			decodeListItemsRecursively(data, pos, len, 0, new ArrayBuffer[DecodedResult], initialBytes)
		}

		@tailrec
		private def decodeListItemsRecursively(data: ImmutableBytes, pos: Int, len: Int, consumed: Int, items: ArrayBuffer[DecodedResult], accumRawBytes: ImmutableBytes): Either[Exception, DecodedSeq] = {
			if (len <= consumed) {
				return Right(DecodedSeq(pos, items.toIndexedSeq, accumRawBytes))
			}
			decode(data, pos) match {
				case Right(item) =>
					items.append(item)
					decodeListItemsRecursively(data, item.pos, len, consumed + (item.pos - pos), items, accumRawBytes ++ data.copyOfRange(pos, item.pos))
				case Left(e) =>
					Left(e)
			}
		}

		private def intFromBytes(b: ImmutableBytes): Int = {
			if (b.length == 0) return 0
			BigInt(1, b.toByteArray).intValue()
		}
	}

}
