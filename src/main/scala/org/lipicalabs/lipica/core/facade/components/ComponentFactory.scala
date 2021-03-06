package org.lipicalabs.lipica.core.facade.components

import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap

import com.sleepycat.je.Environment
import org.lipicalabs.lipica.core.config.NodeProperties
import org.lipicalabs.lipica.core.datastore._
import org.lipicalabs.lipica.core.datastore.datasource._
import org.lipicalabs.lipica.core.facade.listener.CompositeLipicaListener
import org.lipicalabs.lipica.core.kernel.Wallet
import org.lipicalabs.lipica.core.net.channel.ChannelManager
import org.lipicalabs.lipica.core.net.peer_discovery.udp.UDPListener
import org.lipicalabs.lipica.core.net.peer_discovery.{Node, NodeManager}
import org.lipicalabs.lipica.core.sync.{PeersPool, SyncManager, SyncQueue}
import org.lipicalabs.lipica.core.validator.block_header_rules.{BlockHeaderValidator, ExtraDataRule, ManaValueRule, ProofOfWorkRule}
import org.lipicalabs.lipica.core.validator.block_rules.{BlockValidator, TxTrieRootRule, UnclesRule}
import org.lipicalabs.lipica.core.validator.parent_rules.{DifficultyRule, ParentBlockHeaderValidator, ParentNumberRule}
import org.lipicalabs.lipica.core.vm.program.context.{ProgramContextFactory, ProgramContextFactoryImpl}

import scala.collection.JavaConversions

/**
 * ノードの動作において重要なコンポーネントであるクラスのインスタンスを、
 * 生成し初期化して返すためのオブジェクトです。
 *
 * Created by IntelliJ IDEA.
 * 2015/12/25 15:08
 * YANAGISAWA, Kentaro
 */
object ComponentFactory {

	val os = System.getProperty("os.name").toLowerCase.trim
	private val isWindows = os.contains("windows")


	private val bdbEnvOrNone: Option[Environment] =
		if (isWindows) {
			//Windowsでは、JNI経由でLevelDBを使用するとバグを踏むので、回避のためにBDB JEを使う。
			Some(BdbJeDataSource.createDefaultEnvironment(NodeProperties.instance.dataStoreDir))
		} else {
			None
		}

	def dataStoreResourceOrNone: Option[Closeable] = this.bdbEnvOrNone

	val dataSources = JavaConversions.mapAsScalaConcurrentMap(new ConcurrentHashMap[String, KeyValueDataSource])
	private def put(dataSource: KeyValueDataSource): Unit = {
		this.dataSources.put(dataSource.name, dataSource)
	}

	def createBlockStore: BlockStore = {
		val hashToBlockDB = openKeyValueDataSource("hash2block_db")
		put(hashToBlockDB)
		val numberToBlocksDB = openKeyValueDataSource("number2blocks_db")
		put(numberToBlocksDB)
		IndexedBlockStore.newInstance(hashToBlockDB, numberToBlocksDB)
	}

	def createRepository: Repository = {
		val contractDS = openKeyValueDataSource("contract_dtl_db")
		put(contractDS)
		val stateDS = openKeyValueDataSource("state_db")
		put(stateDS)

		if (isWindows) {
			//Windowsでは、JNI経由でLevelDBを使用するとバグを踏むので、回避のためにBDB JEを使う。
			new RepositoryImpl(contractDS, stateDS, new BdbJeDataSourceFactory("contract_dtl_storage", this.bdbEnvOrNone.get))
		} else {
			new RepositoryImpl(contractDS, stateDS, new LevelDBDataSourceFactory("contract_dtl_storage"))
		}
	}

	def createWallet: Wallet = new Wallet

	def createListener: CompositeLipicaListener = new CompositeLipicaListener

	def createBlockValidator: BlockValidator = {
		val rules = Seq(new TxTrieRootRule, new UnclesRule)
		new BlockValidator(rules)
	}

	def createBlockHeaderValidator: BlockHeaderValidator = {
		val rules = Seq(new ManaValueRule, new ExtraDataRule, new ProofOfWorkRule)
		new BlockHeaderValidator(rules)
	}

	def createParentHeaderValidator: ParentBlockHeaderValidator = {
		val rules = Seq(new ParentNumberRule, new DifficultyRule)
		new ParentBlockHeaderValidator(rules)
	}

	def createChannelManager: ChannelManager = {
		val result = new ChannelManager
		result.init()
		result
	}

	def createNodeManager: NodeManager = {
		val dataSource = openKeyValueDataSource("nodestats_db")
		put(dataSource)
		val result = NodeManager.create(dataSource)
		result.seedNodes = NodeProperties.instance.seedNodes.map(uri => Node(uri))
		result
	}

	def createSyncManager: SyncManager = new SyncManager

	def createSyncQueue: SyncQueue = {
		val hashStoreDB = openKeyValueDataSource("hashstore_db")
		put(hashStoreDB)
		val queuedBlocksDB = openKeyValueDataSource("queued_blocks_db")
		put(queuedBlocksDB)
		val queuedHashesDB = openKeyValueDataSource("queued_hashes_db")
		put(queuedHashesDB)
		new SyncQueue(hashStoreDataSource = hashStoreDB, queuedBlocksDataSource = queuedBlocksDB, queuedHashesDataSource = queuedHashesDB)
	}

	def createPeersPool: PeersPool = {
		val result = new PeersPool
		result.init()
		result
	}

	def createUDPListener: UDPListener = new UDPListener

	def createProgramContextFactory: ProgramContextFactory = new ProgramContextFactoryImpl

	private def openKeyValueDataSource(name: String): KeyValueDataSource = {
		val result =
			if (isWindows) {
				//Windowsでは、JNI経由でLevelDBを使用するとバグを踏むので、回避のためにBDB JEを使う。
				val configs = BdbJeDataSource.createDefaultConfig
				new BdbJeDataSource(name, this.bdbEnvOrNone.get, configs)
			} else {
				val options = LevelDbDataSource.createDefaultOptions
				new LevelDbDataSource(name, options)
			}
		result.init()
		result
	}
}
