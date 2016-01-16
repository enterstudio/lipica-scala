package org.lipicalabs.lipica.core.trie

import java.util.concurrent.atomic.AtomicBoolean
import org.lipicalabs.lipica.core.utils.Value

class CachedNode(val nodeValue: Value, _dirty: Boolean) {
	/**
	 * 永続化されていない更新があるか否か。
	 */
	private val isDirtyRef = new AtomicBoolean(_dirty)
	def isDirty = this.isDirtyRef.get
	def isDirty(value: Boolean): CachedNode = {
		this.isDirtyRef.set(value)
		this
	}
	def isEmpty: Boolean = this.nodeValue.isNull || (this.nodeValue.length == 0)
}
