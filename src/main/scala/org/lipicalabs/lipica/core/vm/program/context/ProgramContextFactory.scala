package org.lipicalabs.lipica.core.vm.program.context

import org.lipicalabs.lipica.core.kernel.{Block, TransactionLike}
import org.lipicalabs.lipica.core.datastore.{RepositoryLike, BlockStore}
import org.lipicalabs.lipica.core.utils.ImmutableBytes
import org.lipicalabs.lipica.core.vm.VMWord
import org.lipicalabs.lipica.core.vm.program.Program

/**
 * ProgramContext を生成するクラスが実装すべき trait です。
 *
 * Created by IntelliJ IDEA.
 * 2015/10/31 10:45
 * YANAGISAWA, Kentaro
 */
trait ProgramContextFactory {

	def createProgramContext(tx: TransactionLike, block: Block, repository: RepositoryLike, blockStore: BlockStore): ProgramContext

	def createProgramContext(program: Program, toAddress: VMWord, inValue: VMWord, inMana: VMWord, balanceInt: BigInt, dataIn: ImmutableBytes, repository: RepositoryLike, blockStore: BlockStore): ProgramContext

}
