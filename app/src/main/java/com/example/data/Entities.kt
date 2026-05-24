package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(tableName = "cities")
data class City(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String
)

@Entity(tableName = "chains")
data class Chain(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String
)

@Entity(
    tableName = "stores",
    foreignKeys = [
        ForeignKey(entity = City::class, parentColumns = ["id"], childColumns = ["cityId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Chain::class, parentColumns = ["id"], childColumns = ["chainId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("cityId"), Index("chainId")]
)
data class Store(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val cityId: Int,
    val chainId: Int
)

@Entity(tableName = "items")
data class Item(
    @PrimaryKey val barcode: String,
    val name: String,
    val category: String,
    val imageResId: Int = 0
)

@Entity(
    tableName = "store_prices",
    foreignKeys = [
        ForeignKey(entity = Item::class, parentColumns = ["barcode"], childColumns = ["itemBarcode"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Store::class, parentColumns = ["id"], childColumns = ["storeId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("itemBarcode"), Index("storeId")]
)
data class StorePrice(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val itemBarcode: String,
    val storeId: Int,
    val price: Double,
    val lastUpdated: Long = System.currentTimeMillis()
)

data class StorePriceDetail(
    val priceId: Int,
    val itemBarcode: String,
    val itemName: String,
    val itemCategory: String,
    val storeId: Int,
    val storeName: String,
    val cityId: Int,
    val cityName: String,
    val chainId: Int,
    val chainName: String,
    val price: Double,
    val lastUpdated: Long
)
