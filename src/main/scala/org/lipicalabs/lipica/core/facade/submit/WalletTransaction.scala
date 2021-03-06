package org.lipicalabs.lipica.core.facade.submit

import org.lipicalabs.lipica.core.kernel.TransactionLike

/**
 * Created by IntelliJ IDEA.
 * 2015/11/30 20:50
 * YANAGISAWA, Kentaro
 */
class WalletTransaction(private val tx: TransactionLike) {

	private var approved = 0

	def incrementApproved(): Unit = this.approved += 1

	def approvedCount: Int = this.approved

}
