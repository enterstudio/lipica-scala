package org.lipicalabs.lipica.core.datastore

import java.nio.charset.StandardCharsets
import java.nio.file.Paths
import java.util.Random

import org.junit.runner.RunWith
import org.lipicalabs.lipica.core.crypto.digest.DigestValue
import org.lipicalabs.lipica.core.kernel.{BlockWrapper, Block, Genesis}
import org.lipicalabs.lipica.core.datastore.datasource.InMemoryDataSource
import org.lipicalabs.lipica.core.net.peer_discovery.NodeId
import org.lipicalabs.lipica.core.utils.{ImmutableBytes, UtilConsts}
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

import scala.collection.mutable.ArrayBuffer

/**
 * Created by IntelliJ IDEA.
 * 2015/11/15
 * YANAGISAWA, Kentaro
 */

@RunWith(classOf[JUnitRunner])
class BlockQueueTest extends Specification {
	sequential

	private val blocks = new ArrayBuffer[Block]
	private val hashes = new ArrayBuffer[DigestValue]
	private var cumulativeDifficulty: BigInt = UtilConsts.Zero
	private val nodeId = new Array[Byte](64)

	private var blockQueue: BlockQueueImpl = null

	private def init(): Unit = {
		val uri = getClass.getResource("light-load.dmp")
		val lines = java.nio.file.Files.readAllLines(Paths.get(uri.toURI), StandardCharsets.UTF_8)

		val genesis = Genesis.getInstance
		append(genesis, genesis.cumulativeDifficulty)

		import scala.collection.JavaConversions._
		var i = 0
		for (line <- lines) {
			val block = Block.decode(ImmutableBytes.parseHexString(line))
			append(block, block.cumulativeDifficulty)
			i += 1
			if ((i % 10) == 0) {
				println("Adding the %,dth block.".format(i))
			}
		}

		val r = BigInt(32, new Random)
//		this.testDBName = "./work/database/test_db_" + r
//		SystemProperties.CONFIG.databaseDir = this.testDBName
//		NodeProperties.CONFIG.shouldResetDataStore = false

		this.blockQueue = new BlockQueueImpl(new InMemoryDataSource, new InMemoryDataSource)
		this.blockQueue.open()

		val rnd = new Random
		rnd.nextBytes(this.nodeId)
	}

	private def append(block: Block, difficulty: BigInt): Unit = {
		this.blocks.append(block)
		this.hashes.append(block.hash)
		this.cumulativeDifficulty += difficulty
	}

	private def cleanUp(): Unit = {
		this.blockQueue.close()
//		try {
//			Thread.sleep(100L)
//			FileUtils.forceDelete(new java.io.File(this.testDBName))
//		} catch {
//			case e: Throwable => e.printStackTrace()
//		}

	}

	"test (1)" should {
		"be right" in {
			try {
				init()

				val receivedAt = System.currentTimeMillis
				val importFailedAt = receivedAt + receivedAt / 2L
				val wrapper = BlockWrapper(this.blocks.head, newBlock = true, NodeId(nodeId))
				wrapper.receivedAt = receivedAt
				wrapper.importFailedAt = importFailedAt

				wrapper.receivedAt mustEqual receivedAt
				wrapper.importFailedAt mustEqual importFailedAt
				wrapper.nodeId mustEqual NodeId(this.nodeId)

				this.blockQueue.add(BlockWrapper(this.blocks.head, NodeId(this.nodeId)))

				val block = this.blockQueue.peek.get
				block.encode mustEqual this.blocks.head.encode

				this.blockQueue.take
				this.blockQueue.addAll(
					this.blocks.map {
						each => {
							val result = BlockWrapper(each, NodeId(this.nodeId))
							result.receivedAt = System.currentTimeMillis
							result
						}
					}
				)
				this.blockQueue.close()
				this.blockQueue.open()

				this.blockQueue.size mustEqual this.blocks.size

				val filtered = this.blockQueue.excludeExisting(this.hashes)
				filtered.isEmpty mustEqual true

				var prevBlockNumber = -1L
				for (i <- this.blocks.indices) {
					val each = this.blockQueue.poll.get
					(prevBlockNumber < each.blockNumber) mustEqual true
					prevBlockNumber = each.blockNumber
				}

				blockQueue.peek.isEmpty mustEqual true
				blockQueue.poll.isEmpty mustEqual true
				blockQueue.isEmpty mustEqual true

				for (b <- this.blocks) {
					this.blockQueue.add(BlockWrapper(b, NodeId(this.nodeId)))
				}

				prevBlockNumber = -1L
				for (i <- 0 until 20) {
					val each = this.blockQueue.poll.get
					(prevBlockNumber < each.blockNumber) mustEqual true
					prevBlockNumber = each.blockNumber
				}
				ok
			} finally {
				cleanUp()
			}
		}
	}

	"test (2)" should {
		"be right" in {
			try {
				init()
				new Thread(new Writer(1)).start()
				new Thread(new Reader(1)).start()
				val r2Thread = new Thread(new Reader(2))
				r2Thread.start()
				r2Thread.join()
				ok
			} finally {
				cleanUp()
			}
		}
	}

	class Reader(val index: Int) extends Runnable {
		override def run(): Unit = {
			var noneCount = 0
			while (noneCount < 10) {
				val polled = blockQueue.poll
				if (polled.isEmpty) {
					noneCount += 1
				} else {
					noneCount = 0
				}
				Thread.sleep(50L)
			}
		}
	}

	class Writer(val index: Int) extends Runnable {
		override def run(): Unit = {
			for (i <- 0 until 50) {
				val b = blocks(i)
				blockQueue.add(BlockWrapper(b, NodeId(nodeId)))
				Thread.sleep(50L)
			}
		}
	}

}
