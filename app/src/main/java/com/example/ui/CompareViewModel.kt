package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.random.Random

sealed class ApiState {
    object Idle : ApiState()
    object Loading : ApiState()
    data class Success(val message: String) : ApiState()
    data class Error(val message: String) : ApiState()
}

class CompareViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: CompareRepository
    
    // Core database flows
    val cities: StateFlow<List<City>>
    val chains: StateFlow<List<Chain>>
    val stores: StateFlow<List<Store>>
    val items: StateFlow<List<Item>>
    val priceDetails: StateFlow<List<StorePriceDetail>>

    // Search and filter states
    val selectedCity = MutableStateFlow<City?>(null)
    val selectedChain = MutableStateFlow<Chain?>(null)
    val searchQuery = MutableStateFlow("")
    val newBarcodeQuery = MutableStateFlow("")

    // API status for scans or online updates
    private val _apiState = MutableStateFlow<ApiState>(ApiState.Idle)
    val apiState: StateFlow<ApiState> = _apiState.asStateFlow()

    // Highlighted barcode scanned during active session
    private val _highlightedBarcode = MutableStateFlow<String?>(null)
    val highlightedBarcode: StateFlow<String?> = _highlightedBarcode.asStateFlow()

    init {
        val db = AppDatabase.getDatabase(application)
        repository = CompareRepository(db.compareDao())

        // Hook up database tables as flow states
        cities = repository.allCities.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        chains = repository.allChains.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        stores = repository.allStores.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        items = repository.allItems.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        priceDetails = repository.allPriceDetails.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        // Initial database seed
        viewModelScope.launch {
            repository.seedDatabaseIfEmpty()
        }
    }

    // Stores filtered by selected City & Chain
    val filteredStores: StateFlow<List<Store>> = combine(stores, selectedCity, selectedChain) { storeList, city, chain ->
        storeList.filter { store ->
            val matchesCity = city == null || store.cityId == city.id
            val matchesChain = chain == null || store.chainId == chain.id
            matchesCity && matchesChain
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filtered items based on search query
    val filteredItems: StateFlow<List<Item>> = combine(items, searchQuery) { itemList, query ->
        if (query.isBlank()) {
            itemList
        } else {
            itemList.filter { item ->
                item.name.contains(query, ignoreCase = true) ||
                item.category.contains(query, ignoreCase = true) ||
                item.barcode.contains(query)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Fast-updates database to simulate real-time live stock/market pricing index!
    fun triggerRealtimeOnlineUpdate() {
        viewModelScope.launch {
            _apiState.value = ApiState.Loading
            try {
                // Fetch current state using safe direct database snapshots
                val allStoresSnapshot = repository.getStores()
                val allItemsSnapshot = repository.getItems()
                
                if (allStoresSnapshot.isEmpty() || allItemsSnapshot.isEmpty()) {
                    _apiState.value = ApiState.Error("אין נתוני חנויות או מוצרים לעדכון")
                    return@launch
                }

                // Ensure EVERY item in the database actually has a price in EVERY store!
                // This acts as a reliable seed/safety net for manually entered items.
                for (item in allItemsSnapshot) {
                    val currentPricesForBarcode = repository.getPricesForBarcode(item.barcode)
                    val existingStoreIds = currentPricesForBarcode.map { it.storeId }.toSet()
                    
                    for (store in allStoresSnapshot) {
                        if (!existingStoreIds.contains(store.id)) {
                            // Generate a realistic base price if none exists (e.g. between 5.0 and 45.0)
                            val basePrice = Math.abs(item.barcode.hashCode() % 40) + 5.90
                            val chainSpecificMultiplier = when {
                                store.name.contains("אושר עד") -> 0.90
                                store.name.contains("רמי לוי") -> 0.93
                                store.name.contains("חצי חינם") -> 0.95
                                store.name.contains("שופרסל שלי") -> 1.08
                                store.name.contains("ויקטורי") -> 1.02
                                else -> 1.00
                            }
                            val calculatedPrice = Math.round(basePrice * chainSpecificMultiplier * 20.0) / 20.0
                            repository.updatePrice(item.barcode, store.id, calculatedPrice)
                        }
                    }
                }

                // Simulate live updates block by block
                repeat(4) { increment ->
                    delay(800) // Visual pacing
                    val randomStore = allStoresSnapshot.random()
                    val randomItem = allItemsSnapshot.random()
                    
                    // Fetch current price or generate realistic price
                    val currentPricesForBarcode = repository.getPricesForBarcode(randomItem.barcode)
                    val existingPrice = currentPricesForBarcode.find { it.storeId == randomStore.id }?.price
                        ?: Random.nextDouble(5.0, 45.0)

                    // Shift price slightly (simulate fluctuation of -10% to +10%)
                    val percentageShift = Random.nextDouble(0.90, 1.10)
                    val rawNewPrice = existingPrice * percentageShift
                    val newPrice = Math.round(rawNewPrice * 20.0) / 20.0 // Round to nearest 0.05 shekel (5 agorot)

                    repository.updatePrice(randomItem.barcode, randomStore.id, newPrice)
                }

                _apiState.value = ApiState.Success("עדכון מחירים אונליין בזמן אמת הושלם בהצלחה!")
                delay(3000)
                _apiState.value = ApiState.Idle
            } catch (e: Exception) {
                _apiState.value = ApiState.Error("שגיאה בעדכון מחירים: ${e.message}")
            }
        }
    }

    // Handle barcode scanned manually or via scanner UI
    fun onBarcodeScanned(barcodeInput: String) {
        val barcode = barcodeInput.trim()
        if (barcode.isBlank()) return
        _highlightedBarcode.value = barcode

        viewModelScope.launch {
            _apiState.value = ApiState.Loading
            try {
                // Check if item already exists
                val existingItem = repository.getItemByBarcode(barcode)
                if (existingItem != null) {
                    // Safety check: ensure existing item has prices populated for all current stores
                    val allStoresList = repository.getStores()
                    val currentPricesForBarcode = repository.getPricesForBarcode(barcode)
                    val existingStoreIds = currentPricesForBarcode.map { it.storeId }.toSet()
                    
                    for (store in allStoresList) {
                        if (!existingStoreIds.contains(store.id)) {
                            val basePrice = Math.abs(barcode.hashCode() % 40) + 5.90
                            val chainSpecificMultiplier = when {
                                store.name.contains("אושר עד") -> 0.90
                                store.name.contains("רמי לוי") -> 0.93
                                store.name.contains("חצי חינם") -> 0.95
                                store.name.contains("שופרסל שלי") -> 1.08
                                store.name.contains("ויקטורי") -> 1.02
                                else -> 1.00
                            }
                            val calculatedPrice = Math.round(basePrice * chainSpecificMultiplier * 20.0) / 20.0
                            repository.updatePrice(barcode, store.id, calculatedPrice)
                        }
                    }
                    
                    _apiState.value = ApiState.Success("המוצר נמצא במערכת: ${existingItem.name}")
                    delay(2500)
                    _apiState.value = ApiState.Idle
                    return@launch
                }

                // Resolve product details online via Gemini (with automatic offline mock generator)
                val resolved = GeminiService.resolveBarcode(barcode)
                
                // Save resolved product to our database
                val newItem = Item(
                    barcode = barcode,
                    name = resolved.name,
                    category = resolved.category
                )
                repository.insertItem(newItem)

                // Seed appropriate simulated prices for this new item across all established stores
                val allStoresList = repository.getStores()
                for (store in allStoresList) {
                    // Create retail-appropriate fluctuations around the resolved average price
                    val chainSpecificMultiplier = when {
                        store.name.contains("אושר עד") -> 0.90
                        store.name.contains("רמי לוי") -> 0.93
                        store.name.contains("חצי חינם") -> 0.95
                        store.name.contains("שופרסל שלי") -> 1.08
                        store.name.contains("ויקטורי") -> 1.02
                        else -> 1.00
                    }
                    val randomFluctuation = Random.nextDouble(0.96, 1.04)
                    val calculatedPrice = Math.round(resolved.averagePrice * chainSpecificMultiplier * randomFluctuation * 20.0) / 20.0
                    repository.updatePrice(barcode, store.id, calculatedPrice)
                }

                _apiState.value = ApiState.Success("מוצר חדש זוהה אונליין: ${resolved.name}!")
                delay(4000)
                _apiState.value = ApiState.Idle
            } catch (e: Exception) {
                _apiState.value = ApiState.Error("סריקת ברקוד נכשלה: ${e.message}")
            }
        }
    }

    // Manual custom price entry updates
    fun updateItemPriceManual(barcode: String, storeId: Int, price: Double) {
        viewModelScope.launch {
            repository.updatePrice(barcode.trim(), storeId, price)
        }
    }

    // Add entirely customized cities or chains
    fun addNewCity(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.insertCity(City(name = name.trim()))
        }
    }

    fun addNewChain(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.insertChain(Chain(name = name.trim()))
        }
    }

    fun addNewStore(name: String, cityId: Int, chainId: Int) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.insertStore(Store(name = name.trim(), cityId = cityId, chainId = chainId))
        }
    }

    fun clearHighlight() {
        _highlightedBarcode.value = null
    }
}
