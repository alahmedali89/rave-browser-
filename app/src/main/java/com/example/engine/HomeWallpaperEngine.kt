package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.LruCache
import java.io.File
import java.io.FileOutputStream
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

data class WallpaperCatalogItem(
    val id: String,
    val author: String,
    val imageUrl: String
)

data class HomeWallpaperData(
    val seed: String,
    val bitmap: Bitmap,
    val author: String,
    val imageUrl: String,
    val isFromApi: Boolean = true
)

class HomeWallpaperEngine(
    private val cacheRootDir: File? = null,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) {
    private val catalogMutex = Mutex()
    private val catalogPool = Collections.synchronizedList(mutableListOf<WallpaperCatalogItem>())
    private var nextCatalogPage = 1
    private val usedCatalogIds = Collections.synchronizedSet(mutableSetOf<String>())
    private val seedAssignments = ConcurrentHashMap<String, WallpaperCatalogItem>()

    private val memoryBitmapCache = object : LruCache<String, Bitmap>(12) {
        override fun sizeOf(key: String, value: Bitmap): Int = 1
    }

    suspend fun loadWallpaperForSeed(
        seed: String,
        forceRefresh: Boolean = false
    ): HomeWallpaperData? = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            memoryBitmapCache.get(seed)?.let { cachedBitmap ->
                val assigned = seedAssignments[seed]
                return@withContext HomeWallpaperData(
                    seed = seed,
                    bitmap = cachedBitmap,
                    author = assigned?.author ?: "Picsum Photos",
                    imageUrl = assigned?.imageUrl ?: buildSeedFallbackUrl(seed),
                    isFromApi = true
                )
            }
            readDiskCachedBitmap(seed)?.let { diskBitmap ->
                memoryBitmapCache.put(seed, diskBitmap)
                val assigned = seedAssignments[seed]
                return@withContext HomeWallpaperData(
                    seed = seed,
                    bitmap = diskBitmap,
                    author = assigned?.author ?: "Picsum Photos",
                    imageUrl = assigned?.imageUrl ?: buildSeedFallbackUrl(seed),
                    isFromApi = true
                )
            }
        } else {
            seedAssignments.remove(seed)
            memoryBitmapCache.remove(seed)
            diskCacheFileForSeed(seed)?.delete()
        }

        val item = assignCatalogItemForSeed(seed)
        val primaryBytes = fetchBytes(item.imageUrl)
        val decoded = primaryBytes?.let { decodeSampledBitmap(it, maxDimensionPx = 1600) }
            ?: fetchBytes(buildSeedFallbackUrl(seed))?.let { decodeSampledBitmap(it, maxDimensionPx = 1600) }

        if (decoded != null) {
            memoryBitmapCache.put(seed, decoded)
            writeDiskCachedBitmap(seed, decoded)
            HomeWallpaperData(
                seed = seed,
                bitmap = decoded,
                author = item.author,
                imageUrl = item.imageUrl,
                isFromApi = true
            )
        } else {
            null
        }
    }

    suspend fun loadCustomWallpaperBitmap(filePath: String): Bitmap? = withContext(Dispatchers.IO) {
        if (filePath.isBlank()) return@withContext null
        val cacheKey = "custom::$filePath"
        memoryBitmapCache.get(cacheKey)?.let { return@withContext it }

        val file = File(filePath)
        if (!file.exists() || !file.isFile) return@withContext null
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return@withContext null
        val decoded = decodeSampledBitmap(bytes, maxDimensionPx = 1920)
        if (decoded != null) {
            memoryBitmapCache.put(cacheKey, decoded)
        }
        decoded
    }

    suspend fun saveCustomWallpaperFromUri(
        context: Context,
        sourceUri: Uri
    ): String? = withContext(Dispatchers.IO) {
        val resolver = context.applicationContext.contentResolver
        val rawBytes = runCatching {
            resolver.openInputStream(sourceUri)?.use { it.readBytes() }
        }.getOrNull() ?: return@withContext null

        val orientation = runCatching {
            resolver.openInputStream(sourceUri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val sampled = decodeSampledBitmap(rawBytes, maxDimensionPx = 1920)
            ?: return@withContext null
        val oriented = applyExifOrientation(sampled, orientation)

        val filesDir = context.applicationContext.filesDir
        filesDir.listFiles()?.forEach { existing ->
            if (existing.name.startsWith("home_wallpaper_") && existing.name.endsWith(".jpg")) {
                runCatching { existing.delete() }
            }
        }

        val outFile = File(filesDir, "home_wallpaper_${System.currentTimeMillis()}.jpg")
        val saved = runCatching {
            FileOutputStream(outFile).use { fos ->
                oriented.compress(Bitmap.CompressFormat.JPEG, 90, fos)
            }
            true
        }.getOrDefault(false)

        if (oriented !== sampled) {
            sampled.recycle()
        }

        if (saved) {
            memoryBitmapCache.put("custom::${outFile.absolutePath}", oriented)
            outFile.absolutePath
        } else {
            null
        }
    }

    private suspend fun assignCatalogItemForSeed(seed: String): WallpaperCatalogItem {
        seedAssignments[seed]?.let { return it }
        return catalogMutex.withLock {
            seedAssignments[seed]?.let { return@withLock it }
            if (catalogPool.isEmpty()) {
                val fetched = fetchCatalogPage(nextCatalogPage)
                if (fetched.isNotEmpty()) {
                    val freshItems = fetched.filterNot { usedCatalogIds.contains(it.id) }.shuffled()
                    if (freshItems.isNotEmpty()) {
                        catalogPool.addAll(freshItems)
                    } else {
                        usedCatalogIds.clear()
                        catalogPool.addAll(fetched.shuffled())
                    }
                    nextCatalogPage = if (nextCatalogPage >= 10) 1 else nextCatalogPage + 1
                }
            }
            val selected = if (catalogPool.isNotEmpty()) {
                val item = catalogPool.removeAt(0)
                usedCatalogIds.add(item.id)
                item
            } else {
                WallpaperCatalogItem(
                    id = "seed_${seed.hashCode()}",
                    author = "Picsum Photos",
                    imageUrl = buildSeedFallbackUrl(seed)
                )
            }
            seedAssignments[seed] = selected
            selected
        }
    }

    private fun fetchCatalogPage(page: Int): List<WallpaperCatalogItem> {
        val url = "https://picsum.photos/v2/list?page=$page&limit=30"
        val jsonBody = fetchString(url) ?: return emptyList()
        return parseCatalogJson(jsonBody)
    }

    private fun fetchString(url: String): String? {
        return runCatching {
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                response.body?.string()
            }
        }.getOrNull()
    }

    private fun fetchBytes(url: String): ByteArray? {
        return runCatching {
            val request = Request.Builder()
                .url(url)
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                response.body?.bytes()
            }
        }.getOrNull()
    }

    private fun diskCacheFileForSeed(seed: String): File? {
        val root = cacheRootDir ?: return null
        val dir = File(root, "home_wallpapers").apply { mkdirs() }
        val safeName = seed.replace(Regex("[^A-Za-z0-9_-]"), "_").take(48)
        return File(dir, "wp_${safeName}_${seed.hashCode()}.jpg")
    }

    private fun readDiskCachedBitmap(seed: String): Bitmap? {
        val file = diskCacheFileForSeed(seed) ?: return null
        if (!file.exists() || !file.isFile) return null
        return runCatching {
            val bytes = file.readBytes()
            decodeSampledBitmap(bytes, maxDimensionPx = 1600)
        }.getOrNull()
    }

    private fun writeDiskCachedBitmap(seed: String, bitmap: Bitmap) {
        val file = diskCacheFileForSeed(seed) ?: return
        runCatching {
            FileOutputStream(file).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 88, fos)
            }
        }
    }

    private fun decodeSampledBitmap(bytes: ByteArray, maxDimensionPx: Int): Bitmap? {
        if (bytes.isEmpty()) return null
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val width = bounds.outWidth
            val height = bounds.outHeight
            if (width <= 0 || height <= 0) return@runCatching null

            var sampleSize = 1
            val longest = maxOf(width, height)
            while (longest / sampleSize > maxDimensionPx) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
        }.getOrNull()
    }

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            else -> return bitmap
        }
        return runCatching {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }.getOrDefault(bitmap)
    }

    companion object {
        @Volatile
        private var instance: HomeWallpaperEngine? = null

        fun getInstance(context: Context): HomeWallpaperEngine {
            return instance ?: synchronized(this) {
                instance ?: HomeWallpaperEngine(
                    cacheRootDir = context.applicationContext.cacheDir
                ).also { instance = it }
            }
        }

        fun buildSeedFallbackUrl(seed: String): String {
            val safeSeed = Uri.encode(seed.ifBlank { "apex" })
            return "https://picsum.photos/seed/$safeSeed/1080/1920"
        }

        fun parseCatalogJson(rawJson: String): List<WallpaperCatalogItem> {
            if (rawJson.isBlank()) return emptyList()
            return runCatching {
                val arr = JSONArray(rawJson)
                buildList {
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val id = obj.optString("id").trim()
                        if (id.isEmpty()) continue
                        val author = obj.optString("author").trim().ifBlank { "Picsum Photos" }
                        add(
                            WallpaperCatalogItem(
                                id = id,
                                author = author,
                                imageUrl = "https://picsum.photos/id/$id/1080/1920"
                            )
                        )
                    }
                }
            }.getOrDefault(emptyList())
        }
    }
}
