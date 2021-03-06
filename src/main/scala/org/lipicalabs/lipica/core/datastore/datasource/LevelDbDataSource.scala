package org.lipicalabs.lipica.core.datastore.datasource

import java.nio.file.{Files, Path}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}

import org.iq80.leveldb.{DBException, CompressionType, DB, Options}
import org.lipicalabs.lipica.core.config.NodeProperties
import org.lipicalabs.lipica.core.utils.{ErrorLogger, ImmutableBytes}
import org.slf4j.LoggerFactory

import scala.annotation.tailrec
import scala.collection.mutable

/**
 * Created by IntelliJ IDEA.
 * 2015/11/18 12:07
 * YANAGISAWA, Kentaro
 */
class LevelDbDataSource(_name: String, private val options: Options) extends KeyValueDataSource {
	import LevelDbDataSource._

	private val nameRef: AtomicReference[String] = new AtomicReference[String](_name)
	override def name_=(v: String): Unit = this.nameRef.set(v)
	override def name: String = this.nameRef.get

	private val dbRef: AtomicReference[DB] = new AtomicReference[DB](null)
	private def db: DB = this.dbRef.get

	private val aliveRef: AtomicBoolean = new AtomicBoolean(false)
	override def isAlive: Boolean = this.aliveRef.get

	override def init(): Unit = {
		if (this.isAlive) {
			return
		}
		try {
			if (logger.isDebugEnabled) {
				logger.debug("<LevelDBDS> Opening database: %s".format(this.name))
			}
			val dbPath = NodeProperties.instance.dataStoreDir.resolve(this.name)
			Files.createDirectories(dbPath.getParent)

			if (logger.isDebugEnabled) {
				logger.debug("<LevelDBDS> Initializing database: %s at %s".format(this.name, dbPath))
			}
			this.dbRef.set(org.fusesource.leveldbjni.JniDBFactory.factory.open(dbPath.toFile, options))
			this.aliveRef.set(true)
		} catch {
			case e: Exception =>
				ErrorLogger.logger.warn("<LevelDBDS>", e)
				logger.warn("<LevelDBDS>", e)
				throw new RuntimeException(e)
		}
	}

	def destroyDB(path: Path): Unit = {
		if (logger.isDebugEnabled) {
			logger.debug("<LevelDBDS> Destroying database at %s".format(path))
		}
		try {
			org.fusesource.leveldbjni.JniDBFactory.factory.destroy(path.toFile, options)
		} catch {
			case e: Exception =>
				ErrorLogger.logger.warn("<LevelDBDS>", e)
				logger.warn("<LevelDBDS>", e)
		}
	}

	override def close(): Unit = {
		if (!isAlive) {
			return
		}
		if (logger.isDebugEnabled) {
			logger.debug("<LevelDBDS> Closing database: %s".format(this.name))
		}
		try {
			this.db.close()
			this.aliveRef.set(false)
		} catch {
			case e: Exception =>
				ErrorLogger.logger.warn("<LevelDBDS>", e)
				logger.warn("<LevelDBDS>", e)
		}
	}

	override def get(key: ImmutableBytes): Option[ImmutableBytes] = {
		privateGet(key, 0)
	}

	@tailrec
	private def privateGet(key: ImmutableBytes, count: Int): Option[ImmutableBytes] = {
		try {
			val result = this.db.get(key.toByteArray)
			if (result eq null) {
				None
			} else {
				Some(ImmutableBytes(result))
			}
		} catch {
			case e: DBException =>
				//Windows版leveldb固有のバグを避けるための方便。
				if (count < 3) {
					//少し待って再試行する。
					Thread.sleep(7L)
					privateGet(key, count + 1)
				} else {
					throw e
				}
		}
	}

	override def put(key: ImmutableBytes, value: ImmutableBytes): Unit = {
		this.db.put(key.toByteArray, value.toByteArray)
	}

	override def delete(key: ImmutableBytes): Unit = {
		this.db.delete(key.toByteArray)
	}

	override def keys: Set[ImmutableBytes] = {
		val result = new mutable.HashSet[ImmutableBytes]
		val it = db.iterator()
		it.seekToFirst()
		while (it.hasNext) {
			result.add(ImmutableBytes(it.peekNext().getKey))
			it.next()
		}
		result.toSet
	}

	override def deleteAll(): Unit = {
		this.keys.foreach(this.delete)
	}

	private def updateBatchInternal(rows: Map[ImmutableBytes, ImmutableBytes]): Unit = {
		val batch = this.db.createWriteBatch()
		try {
			for (entry <- rows) {
				batch.put(entry._1.toByteArray, entry._2.toByteArray)
			}
			this.db.write(batch)
		} finally {
			batch.close()
		}
	}

	override def updateBatch(rows: Map[ImmutableBytes, ImmutableBytes]): Unit = {
		try {
			updateBatchInternal(rows)
		} catch {
			case e: Exception =>
				//もう一度。
				Thread.sleep(50L)
				try {
					updateBatch(rows)
				} catch {
					case e1: Exception =>
						ErrorLogger.logger.warn("<LevelDBDS>", e)
						logger.warn("<LevelDBDS>", e1)
						throw new RuntimeException(e1)
				}
		}
	}
}

object LevelDbDataSource {
	private val logger = LoggerFactory.getLogger("datastore")

	def createDefaultOptions: Options = {
		val options = new Options
		options.createIfMissing(true)
		options.compressionType(CompressionType.NONE)
		options.blockSize(10 * 1024 * 1024)
		options.writeBufferSize(10 * 1024 * 1024)
		options.cacheSize(0)
		options.paranoidChecks(true)
		options.verifyChecksums(true)
		options
	}
}

class LevelDBDataSourceFactory(override val categoryName: String) extends KeyValueDataSourceFactory {

	private def dataSourceName(givenName: String) = "%s/%s".format(this.categoryName, givenName)

	override def openDataSource(name: String) = DataSourcePool.levelDbByName(dataSourceName(name))

	override def closeDataSource(name: String) = DataSourcePool.closeDataSource(dataSourceName(name))

}