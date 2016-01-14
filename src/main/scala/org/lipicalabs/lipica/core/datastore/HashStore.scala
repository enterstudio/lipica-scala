package org.lipicalabs.lipica.core.datastore

import org.lipicalabs.lipica.core.utils.ImmutableBytes

/**
 * ブロックのダイジェスト値を永続化して記録するクラスが実装すべき trait です。
 *
 * Created by IntelliJ IDEA.
 * 2015/11/25 18:20
 * YANAGISAWA, Kentaro
 */
trait HashStore extends DiskStore {

	def add(hash: ImmutableBytes): Unit

	def addFirst(hash: ImmutableBytes): Unit

	def addBatch(hashes: Seq[ImmutableBytes]): Unit

	def addBatchFirst(hashes: Seq[ImmutableBytes]): Unit

	def peek: Option[ImmutableBytes]

	def poll: Option[ImmutableBytes]

	def pollBatch(count: Int): Seq[ImmutableBytes]

	def size: Int

	def isEmpty: Boolean

	def nonEmpty: Boolean

	def keys: Set[Long]

	def clear(): Unit

}