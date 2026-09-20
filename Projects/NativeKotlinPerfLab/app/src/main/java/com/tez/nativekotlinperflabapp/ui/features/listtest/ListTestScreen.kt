package com.tez.nativekotlinperflabapp.ui.features.listtest

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.tez.nativekotlinperflabapp.models.Product

/**
 * The presentation layer for evaluating high-frequency list rendering and scrolling performance.
 */
@Composable
fun ListTestScreen(
    viewModel: ListTestViewModel = viewModel()
) {
    val context = LocalContext.current

    // Uses direct state access to prevent unnecessary collection allocations during updates.
    val products = viewModel.products

    // Lifecycle-aware state observations
    val isRunning by viewModel.isRunning.collectAsStateWithLifecycle()
    val isLoadingMore by viewModel.isLoadingMore.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val exportUri by viewModel.exportUri.collectAsStateWithLifecycle()

    val gridState = rememberLazyGridState()

    // Encapsulates automated scrolling commands from the ViewModel to the UI thread,
    // simulating rapid, deterministic user interaction.
    LaunchedEffect(Unit) {
        viewModel.scrollCommand.collect { command ->
            if (command.targetIndex in products.indices) {
                gridState.animateScrollToItem(command.targetIndex)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.stopTest(isFinished = false) }
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // --- Status Bar ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF2F2F7))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = status,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray,
                modifier = Modifier.weight(1f)
            )

            if (isLoadingMore) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            }
        }

        // --- Main Rendering Surface ---
        Box(
            modifier = Modifier
                .weight(1f)
                .background(Color(0xFFF2F2F7))
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                state = gridState,
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(15.dp),
                verticalArrangement = Arrangement.spacedBy(15.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // Using explicit keys bypasses heavy hash-map calculations in Compose,
                // capturing pure rendering performance.
                items(
                    items = products,
                    key = { it.id },
                    contentType = { "product" }
                ) { product ->
                    ProductCardView(product = product)
                }

                if (isRunning && products.size < 300) {
                    item(span = { GridItemSpan(2) }) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }

        // --- Controller Interface ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!isRunning) {
                Button(
                    onClick = { viewModel.startTest() },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Start E-Commerce Simulation", fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = { viewModel.stopTest(isFinished = false) },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Stop Simulation", fontWeight = FontWeight.Bold)
                }
            }

            if (exportUri != null && !isRunning) {
                Button(
                    onClick = { viewModel.exportResults(context) },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34C759)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.SystemUpdateAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export Results (CSV)", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// --- ASSET REGISTRY ---
private val ASSET_REGISTRY = mapOf(
    "laptop"     to "laptop.jpg",
    "shoe"       to "shoe.jpg",
    "smartphone" to "smartphone.jpg",
    "tablet"     to "tablet.jpg",
    "watch"      to "watch.jpg",
    "testphoto"  to "testphoto.jpg"
)

@Composable
fun ProductCardView(product: Product) {

    // Resolves asset path strings through a centralized registry to ensure fast lookups
    // during rapid scrolling cycles.
    val assetPath = remember(product.data.imageName) {
        val cleanName = product.data.imageName.substringBeforeLast(".")
        val fileName = ASSET_REGISTRY[cleanName]
        if (fileName != null) "file:///android_asset/$fileName" else null
    }

    Card(
        modifier = Modifier.fillMaxWidth().height(280.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .background(Color.White)
            ) {
                if (assetPath != null) {
                    // Uses Coil's internal bitmap pooling to prevent UI thread starvation
                    // during high-velocity recycling.
                    AsyncImage(
                        model = assetPath,
                        contentDescription = product.data.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(10.dp)
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(Color.LightGray), contentAlignment = Alignment.Center) {
                        Text("Asset Missing", fontSize = 10.sp, color = Color.Gray)
                    }
                }

                Text(
                    text = product.data.discount,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(Color.Red, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            // --- PRODUCT METADATA ---
            Column(
                modifier = Modifier.fillMaxSize().padding(10.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = product.data.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.heightIn(min = 40.dp)
                    )
                    Text(text = product.data.category, fontSize = 12.sp, color = Color.Gray)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = product.data.oldPrice,
                            fontSize = 10.sp,
                            color = Color.Gray,
                            textDecoration = TextDecoration.LineThrough
                        )
                        Text(
                            text = product.data.price,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF9500)
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.AddCircle,
                        contentDescription = "Cart Trigger",
                        tint = Color(0xFF007AFF),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}