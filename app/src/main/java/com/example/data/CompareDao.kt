package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CompareDao {
    // Cities
    @Query("SELECT * FROM cities ORDER BY name ASC")
    fun getCitiesFlow(): Flow<List<City>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCity(city: City): Long

    @Query("SELECT * FROM cities WHERE name = :name LIMIT 1")
    suspend fun getCityByName(name: String): City?

    @Query("SELECT * FROM cities ORDER BY name ASC")
    suspend fun getCities(): List<City>

    // Chains
    @Query("SELECT * FROM chains ORDER BY name ASC")
    fun getChainsFlow(): Flow<List<Chain>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChain(chain: Chain): Long

    @Query("SELECT * FROM chains WHERE name = :name LIMIT 1")
    suspend fun getChainByName(name: String): Chain?

    @Query("SELECT * FROM chains ORDER BY name ASC")
    suspend fun getChains(): List<Chain>

    // Database clearing operations
    @Query("DELETE FROM cities")
    suspend fun clearCities()

    @Query("DELETE FROM chains")
    suspend fun clearChains()

    @Query("DELETE FROM stores")
    suspend fun clearStores()

    @Query("DELETE FROM store_prices")
    suspend fun clearPrices()

    @Query("DELETE FROM items")
    suspend fun clearItems()

    // Stores
    @Query("SELECT * FROM stores ORDER BY name ASC")
    fun getStoresFlow(): Flow<List<Store>>

    @Query("SELECT * FROM stores ORDER BY name ASC")
    suspend fun getStores(): List<Store>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStore(store: Store): Long

    @Query("SELECT * FROM stores WHERE name = :name AND cityId = :cityId AND chainId = :chainId LIMIT 1")
    suspend fun getStoreByDetails(name: String, cityId: Int, chainId: Int): Store?

    // Items
    @Query("SELECT * FROM items ORDER BY name ASC")
    fun getItemsFlow(): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE barcode = :barcode LIMIT 1")
    suspend fun getItemByBarcode(barcode: String): Item?

    @Query("SELECT * FROM items ORDER BY name ASC")
    suspend fun getItems(): List<Item>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: Item)

    // Store Prices
    @Query("SELECT * FROM store_prices WHERE itemBarcode = :barcode")
    suspend fun getPricesForBarcode(barcode: String): List<StorePrice>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrice(storePrice: StorePrice): Long

    @Query("UPDATE store_prices SET price = :price, lastUpdated = :timestamp WHERE itemBarcode = :barcode AND storeId = :storeId")
    suspend fun updatePrice(barcode: String, storeId: Int, price: Double, timestamp: Long): Int

    // Details flow
    @Query("""
        SELECT 
            p.id as priceId,
            p.itemBarcode as itemBarcode,
            i.name as itemName,
            i.category as itemCategory,
            p.storeId as storeId,
            s.name as storeName,
            ct.id as cityId,
            ct.name as cityName,
            ch.id as chainId,
            ch.name as chainName,
            p.price as price,
            p.lastUpdated as lastUpdated
        FROM store_prices p
        INNER JOIN items i ON p.itemBarcode = i.barcode
        INNER JOIN stores s ON p.storeId = s.id
        INNER JOIN cities ct ON s.cityId = ct.id
        INNER JOIN chains ch ON s.chainId = ch.id
    """)
    fun getAllPriceDetailsFlow(): Flow<List<StorePriceDetail>>
}
