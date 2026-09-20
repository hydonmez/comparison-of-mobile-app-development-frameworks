package com.tez.perflab.ui.features.listtest

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.tez.perflab.managers.getPlatformImagePath
import com.tez.perflab.managers.rememberPlatformContext
import com.tez.perflab.models.Product

/**
 * Evaluates hardware rendering performance and GPU frame-time consistency
 * during high-frequency programmatic scrolling.
 */
@Composable
fun ListTestScreen(
    // Utilizes a lambda factory for ViewModel instantiation to bypass reflection constraints in KMP iOS.
    viewModel: ListTestViewModel = viewModel { ListTestViewModel() },
    onNavigateBack: () -> Unit
) {
    val context = rememberPlatformContext()

    // Direct state access for O(1) read operations during peak telemetry capture
    val products = viewModel.products

    // Lifecycle-aware collection strictly manages subscriptions to prevent background resource leaks
    val isRunning by viewModel.isRunning.collectAsStateWithLifecycle()
    val isLoadingMore by viewModel.isLoadingMore.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val isReportReady by viewModel.isReportReady.collectAsStateWithLifecycle()

    val gridState = rememberLazyGridState()

    // Scroll event consumer
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            // Ensures the UI strictly respects device safe areas (e.g., hardware notches)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, start = 8.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Expanded hit-box for improved accessibility on mobile targets
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Navigate Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

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
                // Explicit unique keys optimize rapid recompositions.
                items(
                    items = products,
                    key = { it.id },
                    contentType = { "product_grid_item" }
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

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!isRunning) {
                Button(
                    onClick = { viewModel.startTest(context) },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("START E-COMMERCE BENCHMARK", fontWeight = FontWeight.Bold)
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
                    Text("TERMINATE BENCHMARK", fontWeight = FontWeight.Bold)
                }
            }

            if (isReportReady && !isRunning) {
                Button(
                    onClick = { viewModel.shareResults(context) },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34C759)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.SystemUpdateAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("EXPORT RESULTS (CSV)", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private val ASSET_REGISTRY: Map<String, String> = mapOf(
    "laptop"     to "laptop.jpg",
    "shoe"       to "shoe.jpg",
    "smartphone" to "smartphone.jpg",
    "tablet"     to "tablet.jpg",
    "watch"      to "watch.jpg",
    "testphoto"  to "testphoto.jpg"
)

/**
 * An optimized product card designed for virtualization within a LazyGrid.
 */
@Composable
fun ProductCardView(product: Product) {
    val assetPath = remember(product.data.imageName) {
        val cleanName = product.data.imageName.substringBeforeLast(".")
        val rawFileName = ASSET_REGISTRY[cleanName]
        if (rawFileName != null) {
            getPlatformImagePath(rawFileName)
        } else null
    }

    Card(
        modifier = Modifier.fillMaxWidth().height(280.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
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
                    // MEMORY OPTIMIZATION:
                    // Downsamples the asset to 300px before decoding it into an ImageBitmap.
                    // This prevents uncompressed high-resolution bitmaps from overwhelming the memory heap.
                    val imageRequest = ImageRequest.Builder(LocalPlatformContext.current)
                        .data(assetPath)
                        .size(300, 300)
                        .build()

                    AsyncImage(
                        model = imageRequest,
                        contentDescription = product.data.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(10.dp)
                    )
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
                        contentDescription = "Interaction Event",
                        tint = Color(0xFF007AFF),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}