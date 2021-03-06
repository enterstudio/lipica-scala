package org.lipicalabs.lipica.core.vm.program

import org.lipicalabs.lipica.core.vm.VMWord
import org.lipicalabs.lipica.core.vm.program.listener.{ProgramListener, ProgramListenerAware}


/**
 * Lipica VMのスタックを表すクラスです。
 *
 * Created by IntelliJ IDEA.
 * 2015/10/25 13:27
 * YANAGISAWA, Kentaro
 */
class Stack extends ProgramListenerAware {

	private var traceListener: ProgramListener = null

	private val stack = new java.util.Stack[VMWord]

	override def setTraceListener(traceListener: ProgramListener): Unit = {
		this.traceListener = traceListener
	}

	def pop: VMWord = {
		this.synchronized {
			if (this.traceListener ne null) {
				this.traceListener.onStackPop()
			}
			this.stack.pop()
		}
	}

	def push(item: VMWord): VMWord = {
		this.synchronized {
			if (this.traceListener ne null) {
				this.traceListener.onStackPush(item)
			}
			this.stack.push(item)
		}
	}

	def swap(from: Int, to: Int): Either[Exception, Unit] = {
		this.synchronized {
			if ((from != to) && isAccessible(from) && isAccessible(to)) {
				if (this.traceListener ne null) {
					this.traceListener.onStackSwap(from, to)
				}
				val temp = this.stack.get(from)
				val replaced = this.stack.set(to, temp)
				this.stack.set(from, replaced)
				Right(())
			} else {
				Left(new IllegalArgumentException)
			}
		}
	}

	def peek: VMWord = this.stack.peek

	def get(index: Int): VMWord = {
		if (0 <= index) {
			this.stack.get(index)
		} else {
			//添字が負の場合、末尾から数える。
			this.stack.get(this.stack.size + index)
		}
	}

	def size: Int = {
		this.synchronized {
			this.stack.size
		}
	}

	def isEmpty: Boolean = this.size == 0
	def nonEmpty: Boolean = !this.isEmpty

	def asIterable: Iterable[VMWord] = {
		this.synchronized {
			import scala.collection.JavaConversions._
			iterableAsScalaIterable(this.stack)
		}
	}

	private def isAccessible(idx: Int): Boolean = {
		(0 <= idx) && (idx < this.stack.size)
	}

}
