package org.lipicalabs.lipica.core.vm.program

import org.slf4j.LoggerFactory

/**
 *
 * @since 2015/10/24
 * @author YANAGISAWA, Kentaro
 */
class Program {

	import Program._

}

object Program {
	private val logger = LoggerFactory.getLogger("VM")
	private val manaLogger = LoggerFactory.getLogger("mana")

	/**
	 * LVM�̊֐��Ăяo���̌��E�ƂȂ�[���B
	 * JVM�ɂ� -Xss10M ���x���K�v�ɂȂ�B
	 */
	private val MaxDepth = 1024

	private val MaxStackSize = 1024

}
