package org.lipicalabs.lipica.core.datastore

import org.junit.runner.RunWith
import org.lipicalabs.lipica.core.kernel.{Address160, Address, ContractDetails, ContractDetailsImpl}
import org.lipicalabs.lipica.core.config.NodeProperties
import org.lipicalabs.lipica.core.datastore.datasource.{KeyValueDataSource, InMemoryDataSource}
import org.lipicalabs.lipica.core.utils.ImmutableBytes
import org.lipicalabs.lipica.core.vm.VMWord
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

import scala.util.Random

/**
 * Created by IntelliJ IDEA.
 * 2015/11/21
 * YANAGISAWA, Kentaro
 */
@RunWith(classOf[JUnitRunner])
class ContractDetailsStoreTest extends Specification {
	sequential

	"test (1)" should {
		"be right" in {
			val dds = new ContractDetailsStore(new InMemoryDataSource, new InMemoryDataSourceFactory)
			val contractKey = Address.parseHexString("1a2b1a2b1a2b1a2b1a2b1a2b1a2b1a2b1a2b1a2b")
			val code = ImmutableBytes.parseHexString("60606060")
			val key = ImmutableBytes.parseHexString("11")
			val value = ImmutableBytes.parseHexString("aa")

			val contractDetails = new ContractDetailsImpl(new InMemoryDataSourceFactory)
			contractDetails.address = randomAddress
			contractDetails.code = code
			contractDetails.put(VMWord(key), VMWord(value))

			dds.update(contractKey, contractDetails)
			val loaded = dds.get(contractKey).get

			val encoded1 = contractDetails.encode
			val encoded2 = loaded.encode

			encoded2 mustEqual encoded1

			dds.flush()

			val loaded2 = dds.get(contractKey).get
			val encoded3 = loaded2.encode

			encoded3 mustEqual encoded1
		}
	}

	"test (2)" should {
		"be right" in {
			val dds = new ContractDetailsStore(new InMemoryDataSource, new InMemoryDataSourceFactory)
			val contractKey = Address.parseHexString("1a2b1a2b1a2b1a2b1a2b1a2b1a2b1a2b1a2b1a2b")
			val code = ImmutableBytes.parseHexString("60606060")
			val key = ImmutableBytes.parseHexString("11")
			val value = ImmutableBytes.parseHexString("aa")

			val contractDetails = new ContractDetailsImpl(new InMemoryDataSourceFactory)
			contractDetails.address = randomAddress
			contractDetails.code = code
			contractDetails.put(VMWord(key), VMWord(value))

			dds.update(contractKey, contractDetails)
			val loaded = dds.get(contractKey).get

			val encoded1 = contractDetails.encode
			val encoded2 = loaded.encode

			encoded2 mustEqual encoded1

			dds.remove(contractKey)

			dds.get(contractKey).isEmpty mustEqual true

			dds.flush()

			dds.get(contractKey).isEmpty mustEqual true
		}
	}

	"test (3)" should {
		"be right" in {
			val dds = new ContractDetailsStore(new InMemoryDataSource, new InMemoryDataSourceFactory)
			val contractKey = Address.parseHexString("1a2b1a2b1a2b1a2b1a2b1a2b1a2b1a2b1a2b1a2b")
			val code = ImmutableBytes.parseHexString("60606060")
			val key = ImmutableBytes.parseHexString("11")
			val value = ImmutableBytes.parseHexString("aa")

			val contractDetails = new ContractDetailsImpl(new InMemoryDataSourceFactory)
			contractDetails.address = randomAddress
			contractDetails.code = code
			contractDetails.put(VMWord(key), VMWord(value))

			dds.update(contractKey, contractDetails)
			val loaded = dds.get(contractKey).get

			val encoded1 = contractDetails.encode
			val encoded2 = loaded.encode

			encoded2 mustEqual encoded1

			dds.remove(contractKey)
			dds.get(contractKey).isEmpty mustEqual true

			dds.update(contractKey, contractDetails)

			dds.get(contractKey).get.encode mustEqual encoded1

			dds.flush()

			dds.get(contractKey).get.encode mustEqual encoded1
		}
	}

	"test external storage" should {
		"be right" in {
			val factory = new InMemoryDataSourceFactory
			val dds = new ContractDetailsStore(new InMemoryDataSource, factory)

			val addressWithExternalStorage = randomAddress
			val addressWithInternalStorage = randomAddress

			val limit = NodeProperties.instance.detailsInMemoryStorageLimit

			val externalStorage = factory.openDataSource(addressWithExternalStorage.toHexString).asInstanceOf[InMemoryDataSource]
			val internalStorage = new InMemoryDataSource

			val detailsWithExternalStorage = randomContractDetails(512, limit + 1, externalStorage)
			val detailsWithInternalStorage = randomContractDetails(512, limit - 1, internalStorage)

			detailsWithExternalStorage.storageContent.size mustEqual limit + 1

			dds.update(addressWithExternalStorage, detailsWithExternalStorage)
			dds.update(addressWithInternalStorage, detailsWithInternalStorage)

			dds.flush()

			(0 < externalStorage.getAddedItems) mustEqual true
			(internalStorage.getAddedItems == 0) mustEqual true

			val loaded = dds.get(addressWithExternalStorage).get
			loaded.storageContent.size mustEqual limit + 1
			val encodedBytes = loaded.encode
			val decoded = ContractDetailsImpl.decode(encodedBytes, factory)
			decoded.externalStorageDataSource = externalStorage
			decoded.decode(encodedBytes)

			decoded.storageContent.size mustEqual limit + 1
			(encodedBytes.length < detailsWithInternalStorage.encode.length) mustEqual true

			val loadedInternal = dds.get(addressWithInternalStorage).get
			loadedInternal.storageContent.size mustEqual limit - 1
		}
	}

	private def randomBytes(length: Int): ImmutableBytes = {
		val result: Array[Byte] = new Array[Byte](length)
		new Random().nextBytes(result)
		ImmutableBytes(result)
	}

	private def randomDataWord: VMWord = {
		VMWord(randomBytes(32))
	}

	private def randomAddress: Address = {
		Address160(randomBytes(20))
	}

	private def randomContractDetails(codeSize: Int, storageSize: Int, storageDataSource: KeyValueDataSource): ContractDetails = {
		val result = new ContractDetailsImpl(new InMemoryDataSourceFactory)
		result.code = randomBytes(codeSize)

		result.externalStorageDataSource = storageDataSource
		for (i <- 0 until storageSize) {
			result.put(randomDataWord, randomDataWord)
		}

		result
	}

}
