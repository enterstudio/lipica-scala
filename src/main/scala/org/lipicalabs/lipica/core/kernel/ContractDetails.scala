package org.lipicalabs.lipica.core.kernel

import org.lipicalabs.lipica.core.crypto.digest.DigestValue
import org.lipicalabs.lipica.core.utils.ImmutableBytes
import org.lipicalabs.lipica.core.vm.VMWord

/**
 * コントラクトの内容である、コードやストレージデータを
 * 保持するクラスが実装すべき trait です。
 *
 * Created by IntelliJ IDEA.
 * 2015/10/25 14:03
 * YANAGISAWA, Kentaro
 */
trait ContractDetails {

	/**
	 * アドレスを取得します。
	 */
	def address: Address

	/**
	 * アドレスを設定します。
	 */
	def address_=(v: Address): Unit

	/**
	 * コードを取得します。
	 */
	def code: ImmutableBytes

	/**
	 * コードをセットします。
	 */
	def code_=(v: ImmutableBytes): Unit

	/**
	 * データをストレージに保存します。
	 */
	def put(key: VMWord, value: VMWord): Unit

	/**
	 * 渡されたデータをストレージに登録します。
	 */
	def put(data: Map[VMWord, VMWord]): Unit

	/**
	 * データをストレージから読み取ります。
	 */
	def get(key: VMWord): Option[VMWord]

	/**
	 * ストレージデータ全体のトップダイジェスト値を取得します。
	 */
	def storageRoot: DigestValue

	/**
	 * ストレージに格納されたデータ数を取得します。
	 */
	def storageSize: Int

	/**
	 * ストレージに保存されたデータを返します。
	 */
	def storageContent: Map[VMWord, VMWord]

	/**
	 * ストレージに保存されたデータのうち、条件に合致するものを返します。
	 */
	def storageContent(keys: Iterable[VMWord]): Map[VMWord, VMWord]

	/**
	 * ストレージに保存されているデータのキーの集合を返します。
	 */
	def storageKeys: Set[VMWord]

	def syncStorage(): Unit

	def getSnapshotTo(v: DigestValue): ContractDetails

	/**
	 * このオブジェクトを、RBAC形式にエンコードします。
	 */
	def encode: ImmutableBytes

	/**
	 * RBAC形式にエンコードされたバイト列を解析して、このオブジェクトに属性をセットします。
	 */
	def decode(data: ImmutableBytes): Unit

	def createClone: ContractDetails

	def isDirty: Boolean

	def isDirty_=(v: Boolean): Unit

	def isDeleted: Boolean

	def isDeleted_=(v: Boolean): Unit

}
