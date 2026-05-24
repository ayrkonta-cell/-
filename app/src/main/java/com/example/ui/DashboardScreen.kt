package com.example.ui

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.*
import kotlinx.coroutines.delay
import com.google.android.gms.common.api.ApiException
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: CompareViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Gather states
    val cities by viewModel.cities.collectAsStateWithLifecycle()
    val chains by viewModel.chains.collectAsStateWithLifecycle()
    val items by viewModel.filteredItems.collectAsStateWithLifecycle()
    val stores by viewModel.filteredStores.collectAsStateWithLifecycle()
    val priceDetails by viewModel.priceDetails.collectAsStateWithLifecycle()
    
    val selectedCity by viewModel.selectedCity.collectAsStateWithLifecycle()
    val selectedChain by viewModel.selectedChain.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val apiState by viewModel.apiState.collectAsStateWithLifecycle()
    val highlightedBarcode by viewModel.highlightedBarcode.collectAsStateWithLifecycle()

    // Screen dynamic layout variables
    var showManualBarcodeDialog by remember { mutableStateOf(false) }
    var selectedItemForManualEdit by remember { mutableStateOf<Pair<Item, Store>?>(null) }
    var manualPriceValue by remember { mutableStateOf("") }
    
    // Shopping basket selected items barcodes list
    val basketSelection = remember { mutableStateListOf<String>() }
    var showAddStoreDialog by remember { mutableStateOf(false) }

    // Setup Barcode Scanner options
    val barcodeScannerOptions = remember {
        try {
            GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                .enableAutoZoom()
                .build()
        } catch (t: Throwable) {
            null
        }
    }
    val barcodeScanner = remember {
        try {
            val isEmulator = android.os.Build.FINGERPRINT.startsWith("generic")
                    || android.os.Build.FINGERPRINT.startsWith("unknown")
                    || android.os.Build.HARDWARE.contains("goldfish")
                    || android.os.Build.HARDWARE.contains("ranchu")
                    || android.os.Build.MODEL.contains("google_sdk")
                    || android.os.Build.MODEL.contains("Emulator")
                    || android.os.Build.MODEL.contains("Android SDK built for x86")
                    || android.os.Build.MANUFACTURER.contains("Genymotion")
                    || android.os.Build.PRODUCT.contains("emulator")
                    || android.os.Build.PRODUCT.contains("sdk")

            val gmsAvailable = com.google.android.gms.common.GoogleApiAvailability.getInstance()
                .isGooglePlayServicesAvailable(context) == com.google.android.gms.common.ConnectionResult.SUCCESS
            if (!isEmulator && gmsAvailable && barcodeScannerOptions != null) {
                GmsBarcodeScanning.getClient(context, barcodeScannerOptions)
            } else {
                null
            }
        } catch (t: Throwable) {
            null
        }
    }

    // Direct RTL setting to match Hebrew user experience
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column(horizontalAlignment = Alignment.Start) {
                            Text(
                                "השוואת מחירים 🛒",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = (-0.15).sp
                                ),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(top = 1.dp)
                            ) {
                                Text(
                                    "מיקום נוכחי:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    "${selectedCity?.name ?: "כל הארץ"} • ${selectedChain?.name ?: "כל הרשתות"}",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    actions = {
                        // Online real-time automatic pricing update button
                        IconButton(
                            onClick = { viewModel.triggerRealtimeOnlineUpdate() },
                            modifier = Modifier.testTag("online_sync_button"),
                            enabled = apiState !is ApiState.Loading
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = "עדכן מחירים אונליין",
                                tint = if (apiState is ApiState.Loading) Color.Gray else MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            modifier = modifier
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    // Static / Dynamic Alert Banner for system state
                    AnimatedVisibility(visible = apiState !is ApiState.Idle) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = when (apiState) {
                                is ApiState.Loading -> MaterialTheme.colorScheme.primaryContainer
                                is ApiState.Success -> Color(0xE7E8F5E9)
                                is ApiState.Error -> MaterialTheme.colorScheme.errorContainer
                                else -> MaterialTheme.colorScheme.primaryContainer
                            }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                when (apiState) {
                                    is ApiState.Loading -> {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            "מעדכן מחירים אונליין בזמן אמת...",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    is ApiState.Success -> {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = "הצלחה",
                                            tint = Color(0xFF2E7D32),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            (apiState as ApiState.Success).message,
                                            fontSize = 13.sp,
                                            color = Color(0xFF1B5E20),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    is ApiState.Error -> {
                                        Icon(
                                            Icons.Default.Error,
                                            contentDescription = "שגיאה",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            (apiState as ApiState.Error).message,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    else -> {}
                                }
                            }
                        }
                    }

                    // Barcode scanning main action cards
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(115.dp)
                            .padding(vertical = 4.dp)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(24.dp)
                            ),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable {
                                    if (barcodeScanner == null) {
                                        Toast.makeText(context, "שירות הסריקה במצלמה אינו זמין במכשיר זה. אנא הזן ברקוד ידנית.", Toast.LENGTH_LONG).show()
                                        showManualBarcodeDialog = true
                                        return@clickable
                                    }
                                    try {
                                        // Start native camera barcode scanning sheet
                                        barcodeScanner
                                            .startScan()
                                            .addOnSuccessListener { barcode ->
                                                val value = barcode.rawValue
                                                if (!value.isNullOrBlank()) {
                                                    viewModel.onBarcodeScanned(value)
                                                }
                                            }
                                            .addOnFailureListener { e ->
                                                if (e is ApiException) {
                                                    if (e.statusCode == 16) {
                                                        Toast
                                                            .makeText(
                                                                context,
                                                                 "סריקה בוטלה על ידי המשתמש",
                                                                Toast.LENGTH_SHORT
                                                            )
                                                            .show()
                                                    } else {
                                                        Toast
                                                            .makeText(
                                                                context,
                                                                "שגיאת סורק: ${e.message}",
                                                                Toast.LENGTH_SHORT
                                                            )
                                                            .show()
                                                    }
                                                }
                                            }
                                    } catch (t: Throwable) {
                                        Toast.makeText(context, "שגיאה בהפעלת סורק המצלמה. אנא הזן ברקוד ידנית.", Toast.LENGTH_LONG).show()
                                        showManualBarcodeDialog = true
                                    }
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QrCodeScanner,
                                    contentDescription = "סורק ברקוד",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "פתח סורק ברקוד מוצר 📷",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "פתח מצלמה לסריקה מהירה והשוואת מחירים מיידית",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    // Secondary manual controls
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { showManualBarcodeDialog = true },
                            modifier = Modifier.testTag("manual_entry_toggle")
                        ) {
                            Icon(Icons.Default.Keyboard, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("הזן ברקוד ידנית (לדוגמה: 7290123412342)", fontSize = 12.sp)
                        }

                        IconButton(onClick = { showAddStoreDialog = true }) {
                            Icon(
                                Icons.Default.AddBusiness,
                                contentDescription = "הוסף חנות חדשה",
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }

                    // Filtering Section (City and Chains Selection Rows)
                    Text(
                        "סנן תוצאות לפי חנויות:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )

                    // 1. City selection horizontal list
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            FilterChip(
                                selected = selectedCity == null,
                                onClick = { viewModel.selectedCity.value = null },
                                label = { Text("כל הערים 📍") }
                            )
                        }
                        items(cities) { city ->
                            FilterChip(
                                selected = selectedCity?.id == city.id,
                                onClick = { viewModel.selectedCity.value = city },
                                label = { Text(city.name) }
                            )
                        }
                    }

                    // 2. Chain selection horizontal list
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            FilterChip(
                                selected = selectedChain == null,
                                onClick = { viewModel.selectedChain.value = null },
                                label = { Text("כל הרשתות 🏛️") }
                            )
                        }
                        items(chains) { chain ->
                            FilterChip(
                                selected = selectedChain?.id == chain.id,
                                onClick = { viewModel.selectedChain.value = chain },
                                label = { Text(chain.name) }
                            )
                        }
                    }

                    // Search input
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.searchQuery.value = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .testTag("search_items_bar"),
                        placeholder = { Text("חפש מוצר לפי שם, קטגוריה או ברקוד...", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "נקה")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                            unfocusedIndicatorColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )

                    // Table items header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "מוצרים להשוואה (${items.size}):",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(
                            onClick = {
                                if (basketSelection.size == items.size) {
                                    basketSelection.clear()
                                } else {
                                    basketSelection.clear()
                                    basketSelection.addAll(items.map { it.barcode })
                                }
                            }
                        ) {
                            Text(
                                if (basketSelection.size == items.size) "בטל הכל" else "סמן הכל לסל",
                                fontSize = 12.sp
                            )
                        }
                    }

                    // Product list representing our comparative table
                    if (items.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.LocalMall,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(56.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    "לא נמצאו מוצרים במערכת",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "נסה לסרוק ברקוד או להחליף מסנני חיפוש",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = if (basketSelection.isNotEmpty()) 120.dp else 16.dp)
                        ) {
                            items(items) { item ->
                                val isHighlighted = highlightedBarcode == item.barcode
                                
                                ProductComparisonRow(
                                    item = item,
                                    stores = stores,
                                    priceDetails = priceDetails,
                                    isHighlighted = isHighlighted,
                                    isInBasket = basketSelection.contains(item.barcode),
                                    onBasketToggle = {
                                        if (basketSelection.contains(item.barcode)) {
                                            basketSelection.remove(item.barcode)
                                        } else {
                                            basketSelection.add(item.barcode)
                                        }
                                    },
                                    onPriceEditRequest = { store ->
                                        selectedItemForManualEdit = Pair(item, store)
                                        // Find existing price to prepopulate manualPriceValue
                                        val existingPrice = priceDetails
                                            .find { it.itemBarcode == item.barcode && it.storeId == store.id }?.price
                                        manualPriceValue = existingPrice?.toString() ?: ""
                                    },
                                    onDismissHighlight = { viewModel.clearHighlight() }
                                )
                            }
                        }
                    }
                }

                // Cumulative Shopping Basket Summary overlay (Sticky bottom drawer layout)
                if (basketSelection.isNotEmpty()) {
                    ShoppingBasketSummary(
                        selectedBarcodes = basketSelection,
                        stores = stores,
                        priceDetails = priceDetails,
                        onClearBasket = { basketSelection.clear() },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                    )
                }
            }
        }

        // Dialog for entering a manual simulated barcode lookup
        if (showManualBarcodeDialog) {
            Dialog(onDismissRequest = { showManualBarcodeDialog = false }) {
                var barcodeInput by remember { mutableStateOf("") }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "הזנת ברקוד פריט ידנית 🖋️",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "הזן קוד ברקוד מוצר לבדיקה וזיהוי מיידי אונליין (למשל ברקודים ישראליים נפוצים):",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        val sampleList = listOf(
                            "7290000123456" to "שמן זית",
                            "7290123412342" to "קוטג׳ תנובה",
                            "5449000000439" to "קוקה קולה בקבוק",
                            "7290000147258" to "קורנפלקס תלמה"
                        )

                        Text("רשימה לבחירה מהירה:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            sampleList.forEach { (sampleBarcode, label) ->
                                Text(
                                    label,
                                    fontSize = 10.sp,
                                    modifier = Modifier
                                        .background(
                                            MaterialTheme.colorScheme.secondaryContainer,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable { barcodeInput = sampleBarcode }
                                        .padding(horizontal = 6.dp, vertical = 4.dp),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = barcodeInput,
                            onValueChange = { barcodeInput = it },
                            label = { Text("קוד ברקוד (מספרים)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                if (barcodeInput.isNotBlank()) {
                                    viewModel.onBarcodeScanned(barcodeInput)
                                    showManualBarcodeDialog = false
                                }
                            }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("barcode_text_input")
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TextButton(onClick = { showManualBarcodeDialog = false }) {
                                Text("ביטול")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (barcodeInput.isNotBlank()) {
                                        viewModel.onBarcodeScanned(barcodeInput)
                                        showManualBarcodeDialog = false
                                    }
                                },
                                enabled = barcodeInput.isNotBlank(),
                                modifier = Modifier.testTag("barcode_submit_button")
                            ) {
                                Text("שלח לזיהוי")
                            }
                        }
                    }
                }
            }
        }

        // Dialog for adding a Custom Store
        if (showAddStoreDialog) {
            Dialog(onDismissRequest = { showAddStoreDialog = false }) {
                var storeNameInput by remember { mutableStateOf("") }
                var selectedCityOption by remember { mutableStateOf(cities.firstOrNull()) }
                var selectedChainOption by remember { mutableStateOf(chains.firstOrNull()) }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth()
                    ) {
                        Text(
                            "הוספת סניף חנות חדש 🏪",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = storeNameInput,
                            onValueChange = { storeNameInput = it },
                            label = { Text("שם הסניף (למשל: רמי לוי - פתח תקווה)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text("בחר עיר:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        LazyRow(modifier = Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(cities) { city ->
                                FilterChip(
                                    selected = selectedCityOption?.id == city.id,
                                    onClick = { selectedCityOption = city },
                                    label = { Text(city.name) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text("בחר רשת:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        LazyRow(modifier = Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(chains) { chain ->
                                FilterChip(
                                    selected = selectedChainOption?.id == chain.id,
                                    onClick = { selectedChainOption = chain },
                                    label = { Text(chain.name) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))
                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TextButton(onClick = { showAddStoreDialog = false }) {
                                Text("ביטול")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    val city = selectedCityOption
                                    val chain = selectedChainOption
                                    if (storeNameInput.isNotBlank() && city != null && chain != null) {
                                        viewModel.addNewStore(storeNameInput, city.id, chain.id)
                                        showAddStoreDialog = false
                                        Toast.makeText(context, "הסניף נוסף בהצלחה!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                enabled = storeNameInput.isNotBlank() && selectedCityOption != null && selectedChainOption != null
                            ) {
                                Text("הוסף")
                            }
                        }
                    }
                }
            }
        }

        // Dialog for updating a single product price manually in details
        selectedItemForManualEdit?.let { (item, store) ->
            Dialog(onDismissRequest = { selectedItemForManualEdit = null }) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth()
                    ) {
                        Text(
                            "עדכון מחיר ידני 💰",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "עדכן את מחיר הפריט:",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            item.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "בסניף: ${store.name}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = manualPriceValue,
                            onValueChange = { manualPriceValue = it },
                            label = { Text("מחיר בשקלים (₪)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                val price = manualPriceValue.toDoubleOrNull()
                                if (price != null && price >= 0.0) {
                                    viewModel.updateItemPriceManual(item.barcode, store.id, price)
                                    selectedItemForManualEdit = null
                                }
                            }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("manual_price_input")
                        )

                        Spacer(modifier = Modifier.height(20.dp))
                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TextButton(onClick = { selectedItemForManualEdit = null }) {
                                Text("ביטול")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    val price = manualPriceValue.toDoubleOrNull()
                                    if (price != null && price >= 0.0) {
                                        viewModel.updateItemPriceManual(item.barcode, store.id, price)
                                        selectedItemForManualEdit = null
                                    }
                                },
                                enabled = manualPriceValue.toDoubleOrNull() != null,
                                modifier = Modifier.testTag("manual_price_submit")
                            ) {
                                Text("עדכן מחיר")
                            }
                        }
                    }
                }
            }
        }
    }
}

// Represent an Item row with detailed prices across matching stores
@Composable
fun ProductComparisonRow(
    item: Item,
    stores: List<Store>,
    priceDetails: List<StorePriceDetail>,
    isHighlighted: Boolean,
    isInBasket: Boolean,
    onBasketToggle: () -> Unit,
    onPriceEditRequest: (Store) -> Unit,
    onDismissHighlight: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Determine the prices of this product across all matching storefront columns
    val itemPrices = remember(priceDetails, item.barcode, stores) {
        stores.map { store ->
            val priceRecord = priceDetails.find { it.itemBarcode == item.barcode && it.storeId == store.id }
            store to priceRecord?.price
        }
    }

    // Identify cheapest price for visual crowning
    val minimumPrice = remember(itemPrices) {
        val validPrices = itemPrices.mapNotNull { it.second }
        if (validPrices.isNotEmpty()) validPrices.minOrNull() else null
    }

    var showPricesDashboard by remember { mutableStateOf(false) }

    LaunchedEffect(isHighlighted) {
        if (isHighlighted) {
            delay(5000)
            onDismissHighlight()
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = if (isHighlighted) 2.dp else 1.dp,
                color = if (isHighlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(12.dp)
            )
            .testTag("product_row_${item.barcode}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isHighlighted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Header: Item Title, checkbox, category, barcode
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = isInBasket,
                    onCheckedChange = { onBasketToggle() },
                    modifier = Modifier.testTag("basket_checkbox_${item.barcode}")
                )
                
                Spacer(modifier = Modifier.width(4.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        item.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            item.category,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                        Text(
                            "ברקוד: ${item.barcode}",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = { showPricesDashboard = !showPricesDashboard }
                ) {
                    Icon(
                        imageVector = if (showPricesDashboard) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "פרט מחירים"
                    )
                }
            }

            // Summary of price comparison (Cheapest tag shown immediately)
            if (itemPrices.isNotEmpty()) {
                val cheapestStores = itemPrices.filter { it.second != null && it.second == minimumPrice }
                if (cheapestStores.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, start = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Payments,
                            contentDescription = null,
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "השווה לסל: ",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "הזול ביותר ${minimumPrice?.let { String.format(Locale.US, "%.2f", it) } ?: ""} ₪ ב-${cheapestStores.first().first.name}",
                            fontSize = 11.sp,
                            color = Color(0xFF1B5E20),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Expansion list showing details per store
            AnimatedVisibility(visible = showPricesDashboard || isHighlighted) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .background(
                            MaterialTheme.colorScheme.background,
                            RoundedCornerShape(12.dp)
                        )
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                ) {
                    // Styled Table Header (replicates the HTML comparative grid header)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = Color(0xFFF7F2FA),
                                shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                            )
                            .border(
                                width = (0.5).dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "סניף / רשת",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1.5f)
                        )
                        Text(
                            "מזהה סניף",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            "מחיר בסניף",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Left
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        stores.forEach { store ->
                            val pricePair = itemPrices.find { it.first.id == store.id }
                            val price = pricePair?.second
                            val isCheapest = price != null && price == minimumPrice

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isCheapest) Color(0x33E8DEF8) else Color.Transparent)
                                    .clickable { onPriceEditRequest(store) }
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Chain and branch details
                                Row(
                                    modifier = Modifier.weight(1.5f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (isCheapest) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(Color(0xFF2E7D32), CircleShape)
                                        )
                                    }
                                    Text(
                                        store.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (isCheapest) FontWeight.SemiBold else FontWeight.Normal
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                // Identifier label
                                Text(
                                    text = "#${store.id}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center
                                )

                                // Price details
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.End,
                                        modifier = Modifier.padding(end = 6.dp)
                                    ) {
                                        Text(
                                            text = if (price != null) "${String.format(Locale.US, "%.2f", price)} ₪" else "אין מחיר",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = if (isCheapest) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurface
                                        )
                                        if (isCheapest) {
                                            Text(
                                                "הזול ביותר",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF2E7D32)
                                            )
                                        }
                                    }
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "עדכן",
                                        modifier = Modifier.size(13.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Shopping Basket Calculator bottom sheet
@Composable
fun ShoppingBasketSummary(
    selectedBarcodes: List<String>,
    stores: List<Store>,
    priceDetails: List<StorePriceDetail>,
    onClearBasket: () -> Unit,
    modifier: Modifier = Modifier
) {
    // For each store, calculate sum of prices of selected items in the basket
    val basketSums = remember(selectedBarcodes, stores, priceDetails) {
        stores.map { store ->
            var totalSum = 0.0
            var allItemsHavePrice = true
            
            for (barcode in selectedBarcodes) {
                val priceItem = priceDetails.find { it.itemBarcode == barcode && it.storeId == store.id }
                if (priceItem != null) {
                    totalSum += priceItem.price
                } else {
                    allItemsHavePrice = false
                }
            }
            Triple(store, totalSum, allItemsHavePrice)
        }
    }

    // Get cheapest basket sum
    val cheapestBasket = remember(basketSums) {
        val validSums = basketSums.filter { it.third } // Only stores carrying ALL items in basket
        if (validSums.isNotEmpty()) {
            validSums.minByOrNull { it.second }
        } else {
            // Fallback to any store sum containing at least some parts
            basketSums.minByOrNull { it.second }
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp)
            .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.ShoppingBag,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "סיכום סל קניות משווה 🧺",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "השוואת עלות עבור ${selectedBarcodes.size} מוצרים שסימנת",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onClearBasket) {
                    Text("נקה סל", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            // Result comparisons
            if (cheapestBasket != null) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "המלצת הקנייה הזולה ביותר עבורך:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFE8F5E9), RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "👑 ${cheapestBasket.first.name}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFF1B5E20),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "סה\"כ: ${String.format(Locale.US, "%.2f", cheapestBasket.second)} ₪",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFF1B5E20)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Expand mini comparisons horizontal scroll
            Text("השוואת כל הסניפים הזמינים המותאמים:", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(basketSums) { (store, totalCost, checkAll) ->
                    val isRecommend = cheapestBasket?.first?.id == store.id
                    Box(
                        modifier = Modifier
                            .background(
                                if (isRecommend) Color(0xFFC8E6C9) else MaterialTheme.colorScheme.secondaryContainer,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                store.name.split(" - ").first(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isRecommend) Color(0xFF1B5E20) else MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                "${String.format(Locale.US, "%.2f", totalCost)} ₪",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isRecommend) Color(0xFF1B5E20) else MaterialTheme.colorScheme.onSurface
                            )
                            if (!checkAll) {
                                Text(
                                    "(חסרים מחירים)",
                                    fontSize = 8.sp,
                                    color = Color.Red
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
