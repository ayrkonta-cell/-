package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CompareRepository(private val compareDao: CompareDao) {

    val allCities: Flow<List<City>> = compareDao.getCitiesFlow()
    val allChains: Flow<List<Chain>> = compareDao.getChainsFlow()
    val allStores: Flow<List<Store>> = compareDao.getStoresFlow()
    val allItems: Flow<List<Item>> = compareDao.getItemsFlow()
    val allPriceDetails: Flow<List<StorePriceDetail>> = compareDao.getAllPriceDetailsFlow()

    suspend fun insertCity(city: City): Long = withContext(Dispatchers.IO) {
        compareDao.insertCity(city)
    }

    suspend fun insertChain(chain: Chain): Long = withContext(Dispatchers.IO) {
        compareDao.insertChain(chain)
    }

    suspend fun insertStore(store: Store): Long = withContext(Dispatchers.IO) {
        compareDao.insertStore(store)
    }

    suspend fun getStores(): List<Store> = withContext(Dispatchers.IO) {
        compareDao.getStores()
    }

    suspend fun getItems(): List<Item> = withContext(Dispatchers.IO) {
        compareDao.getItems()
    }

    suspend fun insertItem(item: Item) = withContext(Dispatchers.IO) {
        compareDao.insertItem(item)
    }

    suspend fun getItemByBarcode(barcode: String): Item? = withContext(Dispatchers.IO) {
        compareDao.getItemByBarcode(barcode)
    }

    suspend fun updatePrice(barcode: String, storeId: Int, price: Double) = withContext(Dispatchers.IO) {
        val updatedRows = compareDao.updatePrice(barcode, storeId, price, System.currentTimeMillis())
        if (updatedRows == 0) {
            compareDao.insertPrice(
                StorePrice(
                    itemBarcode = barcode,
                    storeId = storeId,
                    price = price,
                    lastUpdated = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun getPricesForBarcode(barcode: String): List<StorePrice> = withContext(Dispatchers.IO) {
        compareDao.getPricesForBarcode(barcode)
    }

    suspend fun seedDatabaseIfEmpty() = withContext(Dispatchers.IO) {
        val currentCities = compareDao.getCities()
        val currentChains = compareDao.getChains()

        val targetCities = setOf("תל אביב", "חולון", "ראשון לציון")
        val currentCityNames = currentCities.map { it.name }.toSet()
        val hasHaziHinam = currentChains.any { it.name == "חצי חינם" }

        val needsSeed = currentCities.isEmpty() || currentCityNames != targetCities || !hasHaziHinam

        if (!needsSeed) return@withContext

        // Clear existing tables to reset with user's specific selection
        compareDao.clearPrices()
        compareDao.clearStores()
        compareDao.clearCities()
        compareDao.clearChains()
        compareDao.clearItems()

        // Seed Cities
        val cities = listOf("תל אביב", "חולון", "ראשון לציון")
        val cityIds = cities.associateWith { name ->
            val existing = compareDao.getCityByName(name)
            existing?.id ?: compareDao.insertCity(City(name = name)).toInt()
        }

        // Seed Chains
        val chains = listOf("רמי לוי", "שופרסל שלי", "יוחננוף", "ויקטורי", "אושר עד", "חצי חינם")
        val chainIds = chains.associateWith { name ->
            val existing = compareDao.getChainByName(name)
            existing?.id ?: compareDao.insertChain(Chain(name = name)).toInt()
        }

        // Seed Stores (1 store per chain-city combination)
        val seededStores = mutableListOf<Store>()
        for ((cityName, cityId) in cityIds) {
            for ((chainName, chainId) in chainIds) {
                val storeName = "$chainName - סניף $cityName"
                val existing = compareDao.getStoreByDetails(storeName, cityId, chainId)
                val id = existing?.id ?: compareDao.insertStore(
                    Store(
                        name = storeName,
                        cityId = cityId,
                        chainId = chainId
                    )
                ).toInt()
                seededStores.add(Store(id = id, name = storeName, cityId = cityId, chainId = chainId))
            }
        }

        // Seed Items
        val itemsToSeed = listOf(
            Item("7290000123456", "שמן זית כתית מעולה 750 מ״ל", "שמנים ורטבים"),
            Item("7290000000014", "חלב תנובה 3% בקרטון 1 ליטר", "מוצרי חלב"),
            Item("7290014567895", "קפה נמס עלית מגורען 200 גרם", "משקאות חמים"),
            Item("7290123412342", "קוטג׳ תנובה 5% באריזת 250 גרם", "מוצרי חלב"),
            Item("7290000063217", "פסטה אסם פני (נוצה) 500 גרם", "פסטה ודגנים"),
            Item("7290125478964", "שוקולד פרה חלב עלית 100 גרם", "חטיפים ומתוקים"),
            Item("5449000000439", "קוקה קולה קלאסי בקבוק 1.5 ליטר", "משקאות קלים"),
            Item("7290000147258", "קורנפלקס תלמה קלאסי 750 גרם", "דגני בוקר"),
            Item("7290000021651", "גבינה צהובה עמק 28% נועם/תנובה", "מוצרי חלב"),
            Item("7290100850259", "חומוס צבר ביתי קלאסי 400 גרם", "סלטים מוכנים")
        )

        for (item in itemsToSeed) {
            compareDao.insertItem(item)
        }

        // Seed Prices featuring "חצי חינם"
        val priceRanges = mapOf(
            "7290000123456" to mapOf("רמי לוי" to 31.90, "שופרסל שלי" to 39.90, "יוחננוף" to 34.90, "ויקטורי" to 36.90, "אושר עד" to 29.90, "חצי חינם" to 32.50),
            "7290000000014" to mapOf("רמי לוי" to 6.20, "שופרסל שלי" to 6.40, "יוחננוף" to 6.30, "ויקטורי" to 6.40, "אושר עד" to 6.10, "חצי חינם" to 5.90),
            "729014567895" to mapOf("רמי לוי" to 15.90, "שופרסל שלי" to 19.90, "יוחננוף" to 17.90, "ויקטורי" to 18.50, "אושר עד" to 15.00, "חצי חינם" to 16.50),
            "7290123412342" to mapOf("רמי לוי" to 5.80, "שופרסל שלי" to 6.30, "יוחננוף" to 5.95, "ויקטורי" to 6.10, "אושר עד" to 5.50, "חצי חינם" to 5.75),
            "7290000063217" to mapOf("רמי לוי" to 4.50, "שופרסל שלי" to 5.90, "יוחננוף" to 4.90, "ויקטורי" to 5.20, "אושר עד" to 3.90, "חצי חינם" to 4.30),
            "7290125478964" to mapOf("רמי לוי" to 4.90, "שופרסל שלי" to 6.20, "יוחננוף" to 5.30, "ויקטורי" to 5.50, "אושר עד" to 4.50, "חצי חינם" to 4.80),
            "5449000000439" to mapOf("רמי לוי" to 6.50, "שופרסל שלי" to 8.20, "יוחננוף" to 7.20, "ויקטורי" to 7.90, "אושר עד" to 5.90, "חצי חינם" to 6.20),
            "7290000147258" to mapOf("רמי לוי" to 14.90, "שופרסל שלי" to 19.90, "יוחננוף" to 16.90, "ויקטורי" to 17.90, "אושר עד" to 13.90, "חצי חינם" to 14.50),
            "7290000021651" to mapOf("רמי לוי" to 13.50, "שופרסל שלי" to 15.90, "יוחננוף" to 14.20, "ויקטורי" to 14.50, "אושר עד" to 12.90, "חצי חינם" to 13.20),
            "7290100850259" to mapOf("רמי לוי" to 7.50, "שופרסל שלי" to 9.90, "יוחננוף" to 8.20, "ויקטורי" to 8.90, "אושר עד" to 6.90, "חצי חינם" to 7.20)
        )

        for (store in seededStores) {
            val chainNameToken = store.name.split(" - ").first()
            for (item in itemsToSeed) {
                val basePriceMap = priceRanges[item.barcode] ?: continue
                val basePrice = basePriceMap[chainNameToken] ?: 9.90
                val cityOffset = when (store.cityId % 3) {
                    0 -> 0.98
                    1 -> 1.02
                    else -> 1.00
                }
                val finalPrice = Math.round(basePrice * cityOffset * 100.0) / 100.0

                compareDao.insertPrice(
                    StorePrice(
                        itemBarcode = item.barcode,
                        storeId = store.id,
                        price = finalPrice,
                        lastUpdated = System.currentTimeMillis() - (store.id * 3600000)
                    )
                )
            }
        }
    }
}
