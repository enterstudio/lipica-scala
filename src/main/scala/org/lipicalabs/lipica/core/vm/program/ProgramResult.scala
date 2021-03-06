package org.lipicalabs.lipica.core.vm.program

import org.lipicalabs.lipica.core.crypto.digest.DigestValue
import org.lipicalabs.lipica.core.kernel.Address
import org.lipicalabs.lipica.core.utils.{BigIntBytes, ImmutableBytes}
import org.lipicalabs.lipica.core.vm.{CallCreate, LogInfo, VMWord}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

/**
 * コードの実行結果を表現するクラスです。
 *
 * Created by IntelliJ IDEA.
 * 2015/10/31 15:35
 * YANAGISAWA, Kentaro
 */
class ProgramResult {

	private var _manaUsed: Long = 0L
	private var _hReturn = ImmutableBytes.empty
	private var _exception: RuntimeException = null

	private val _deletedAccounts: mutable.Buffer[VMWord] = new ArrayBuffer[VMWord]
	private val _internalTransactions: mutable.Buffer[InternalTransaction] = new ArrayBuffer[InternalTransaction]
	private val _logsBuffer: mutable.Buffer[LogInfo] = new ArrayBuffer[LogInfo]

	private var _futureRefund: Long = 0L

	/** テスト用。（実行せずに貯める。） */
	private val callCreateBuffer: mutable.Buffer[CallCreate] = new ArrayBuffer[CallCreate]

	/** マナを消費します。 */
	def spendMana(mana: Long): Unit = {
		this._manaUsed += mana
	}
	/** マナを払い戻します。 */
	def refundMana(mana: Long): Unit = {
		this._manaUsed -= mana
	}
	/**
	 * 一連のコード実行によって消費されたマナの合計値を返します。
	 */
	def manaUsed: Long = this._manaUsed

	/**
	 * 将来の払い戻しを記録します。
	 */
	def addFutureRefund(v: Long): Unit = {
		this._futureRefund += v
	}
	/**
	 * 将来の払い戻しをリセットします。
	 */
	def resetFutureRefund(): Unit = {
		this._futureRefund = 0
	}
	/**
	 * 将来の払戻額を取得します。
	 */
	def futureRefund: Long = this._futureRefund

	def hReturn_=(v: ImmutableBytes): Unit = {
		this._hReturn = v
	}
	def hReturn: ImmutableBytes = this._hReturn

	def exception_=(e: RuntimeException): Unit = {
		this._exception = e
	}
	def exception: RuntimeException = this._exception

	def deletedAccounts: Seq[VMWord] = this._deletedAccounts.toSeq
	def addDeletedAccount(address: VMWord): Unit = {
		this._deletedAccounts.append(address)
	}
	def addDeletedAccounts(accounts: Iterable[VMWord]): Unit = {
		this._deletedAccounts ++= accounts
	}
	def clearDeletedAccounts(): Unit = this._deletedAccounts.clear()

	def logsAsSeq: Seq[LogInfo] = this._logsBuffer.toSeq
	def addLog(info: LogInfo): Unit = {
		this._logsBuffer.append(info)
	}
	def addLogs(logs: Iterable[LogInfo]): Unit = {
		this._logsBuffer ++= logs
	}
	def clearLogs(): Unit = this._logsBuffer.clear()

	def getCallCreateList: Seq[CallCreate] = this.callCreateBuffer.toSeq
	def addCallCreate(data: ImmutableBytes, destination: ImmutableBytes, manaLimit: ImmutableBytes, value: ImmutableBytes): Unit = {
		this.callCreateBuffer.append(CallCreate(data, destination, manaLimit, value))
	}

	def internalTransactions: Seq[InternalTransaction] = this._internalTransactions.toSeq
	def addInternalTransaction(parentHash: DigestValue, deep: Int, nonce: BigIntBytes, manaPrice: VMWord, manaLimit: VMWord, senderAddress: Address, receiveAddress: Address, value: BigIntBytes, data: ImmutableBytes, note: String): InternalTransaction = {
		val index = this._internalTransactions.size
		val result = new InternalTransaction(parentHash, deep, index, nonce, manaPrice, manaLimit, senderAddress, receiveAddress, value, data, note)
		this._internalTransactions.append(result)
		result
	}
	def addInternalTransactions(transactions: Iterable[InternalTransaction]): Unit = {
		this._internalTransactions ++= transactions
	}
	def rejectInternalTransactions(): Unit = {
		this._internalTransactions.foreach(_.reject())
	}

	def mergeToThis(another: ProgramResult, mergeLogs: Boolean): Unit = {
		addInternalTransactions(another.internalTransactions)
		addDeletedAccounts(another.deletedAccounts)
		//ログは呼び出し元の文脈に足さないのが正解のようだ。
		if (mergeLogs) {
			addLogs(another.logsAsSeq)
		}
		addFutureRefund(another.futureRefund)
	}

}

object ProgramResult {

	def createEmpty: ProgramResult = {
		val result = new ProgramResult
		result.hReturn = ImmutableBytes.empty
		result
	}
}