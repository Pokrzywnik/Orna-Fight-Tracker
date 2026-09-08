package com.frodo.fighttracker

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Manages the Item Assessment database.
 *
 * Source:
 *
 * LOCAL:
 *     assets/items.json
 *
 * REMOTE:
 *     URL entered in Settings.
 *
 * Remote database is cached locally.
 *
 * We use:
 *
 * - ETag
 * - Last-Modified
 *
 * so that checking for an update does NOT download
 * the complete JSON when nothing changed.
 */
object ItemDatabaseManager {

    private const val TAG = "ItemDatabaseManager"

    private const val PREF = "settings"

    private const val KEY_DATABASE_URL =
        "assess_database_url"

    private const val KEY_ETAG =
        "assess_database_etag"

    private const val KEY_LAST_MODIFIED =
        "assess_database_last_modified"

    private const val CACHE_FILE =
        "assess_items_remote.json"

    private const val LOCAL =
        "local"

    private val scope =
        CoroutineScope(
            SupervisorJob() +
                    Dispatchers.IO
        )

    @Volatile
    private var currentJson: String? = null

    @Volatile
    private var initialized = false

    private var appContext: Context? = null

    /**
     * Initialize database manager.
     *
     * This is safe to call multiple times.
     */
    fun initialize(context: Context) {

        if (initialized) {
            return
        }

        synchronized(this) {

            if (initialized) {
                return
            }

            appContext =
                context.applicationContext

            /*
             * Load the currently selected database
             * immediately from local assets or cache.
             *
             * This never requires internet.
             */
            loadCurrentDatabase()

            initialized = true

            /*
             * If remote URL is configured,
             * check for an update in background.
             */
            if (getDatabaseUrl(context) != LOCAL) {

                refreshRemoteDatabaseAsync()
            }
        }
    }

    /**
     * Get currently active database JSON.
     *
     * This is always available after initialize().
     */
    fun getCurrentJson(
        context: Context
    ): String {

        initialize(context)

        return currentJson
            ?: loadLocalDatabase(context)
    }

    /**
     * Get configured database URL.
     *
     * Default:
     *
     * local
     */
    fun getDatabaseUrl(
        context: Context
    ): String {

        return context
            .getSharedPreferences(
                PREF,
                Context.MODE_PRIVATE
            )
            .getString(
                KEY_DATABASE_URL,
                LOCAL
            )
            ?.trim()
            ?.ifBlank {
                LOCAL
            }
            ?: LOCAL
    }

    /**
     * Set database source.
     *
     * LOCAL:
     * immediately switch back to assets/items.json
     *
     * URL:
     * immediately use cached remote file if available,
     * then check server in background.
     */
    fun setDatabaseUrl(
        context: Context,
        value: String
    ) {

        val cleanValue =
            value.trim()
                .ifBlank {
                    LOCAL
                }

        context
            .getSharedPreferences(
                PREF,
                Context.MODE_PRIVATE
            )
            .edit()
            .putString(
                KEY_DATABASE_URL,
                cleanValue
            )
            .apply()

        /*
         * Make sure context is available.
         */
        appContext =
            context.applicationContext

        /*
         * Switch source immediately.
         */
        loadCurrentDatabase()

        /*
         * Remote source needs an update check.
         */
        if (cleanValue != LOCAL) {

            refreshRemoteDatabaseAsync()
        }
    }

    /**
     * Force a remote update check.
     *
     * Does nothing for LOCAL.
     */
    fun refreshRemoteDatabaseAsync(
        onFinished: (() -> Unit)? = null
    ) {

        val context =
            appContext
                ?: return

        val url =
            getDatabaseUrl(context)

        if (url == LOCAL) {

            onFinished?.invoke()

            return
        }

        scope.launch {

            try {

                val changed =
                    refreshRemoteDatabase(
                        context,
                        url
                    )

                if (changed) {

                    Log.d(
                        TAG,
                        "Remote database changed"
                    )

                    /*
                     * Reload current JSON so new
                     * items are used immediately.
                     */
                    loadCurrentDatabase()

                    /*
                     * Existing ItemMatcher instance
                     * must reload its index.
                     */
                    ItemAssessmentPipeline
                        .ItemAssessmentManager
                        .getPipeline(context)
                        .reloadItemDatabase()
                }

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "Remote database update failed",
                    e
                )

            } finally {

                onFinished?.invoke()
            }
        }
    }

    /**
     * Perform conditional HTTP request.
     *
     * Returns true when the actual JSON changed.
     */
    private fun refreshRemoteDatabase(
        context: Context,
        urlString: String
    ): Boolean {

        Log.d(
            TAG,
            "Checking remote database: $urlString"
        )

        val prefs =
            context.getSharedPreferences(
                PREF,
                Context.MODE_PRIVATE
            )

        val etag =
            prefs.getString(
                KEY_ETAG,
                null
            )

        val lastModified =
            prefs.getLong(
                KEY_LAST_MODIFIED,
                0L
            )

        val connection =
            (URL(urlString)
                .openConnection() as HttpURLConnection)
                .apply {

                    requestMethod = "GET"

                    connectTimeout =
                        10_000

                    readTimeout =
                        20_000

                    useCaches = false

                    setRequestProperty(
                        "Accept",
                        "application/json"
                    )

                    /*
                     * Conditional request.
                     *
                     * If server supports ETag,
                     * it can answer 304.
                     */
                    if (!etag.isNullOrBlank()) {

                        setRequestProperty(
                            "If-None-Match",
                            etag
                        )
                    }

                    /*
                     * Conditional request using
                     * Last-Modified.
                     */
                    if (lastModified > 0L) {

                        setIfModifiedSince(
                            lastModified
                        )
                    }

                    setRequestProperty(
                        "User-Agent",
                        "Orna-Fight-Tracker"
                    )
                }

        try {

            val responseCode =
                connection.responseCode

            Log.d(
                TAG,
                "HTTP response: $responseCode"
            )

            /*
             * Nothing changed.
             *
             * This is the important case:
             *
             * NO JSON DOWNLOAD.
             */
            if (
                responseCode ==
                HttpURLConnection.HTTP_NOT_MODIFIED
            ) {

                Log.d(
                    TAG,
                    "Remote database unchanged (304)"
                )

                return false
            }

            if (
                responseCode !in 200..299
            ) {

                throw Exception(
                    "HTTP $responseCode"
                )
            }

            /*
             * Read new JSON.
             */
            val json =
                connection
                    .inputStream
                    .bufferedReader(
                        Charsets.UTF_8
                    )
                    .use {
                        it.readText()
                    }

            if (json.isBlank()) {

                throw Exception(
                    "Remote items.json is empty"
                )
            }

            /*
             * Validate JSON before replacing cache.
             *
             * ItemMatcher will do the real parsing,
             * but basic validation prevents a broken
             * response from replacing a good database.
             */
            val trimmed =
                json.trim()

            if (
                !trimmed.startsWith("[") ||
                !trimmed.endsWith("]")
            ) {

                throw Exception(
                    "Remote items.json does not look like a JSON array"
                )
            }

            val cacheFile =
                File(
                    context.filesDir,
                    CACHE_FILE
                )

            /*
             * Write atomically.
             *
             * This prevents a partially downloaded
             * file from becoming the active cache.
             */
            val tempFile =
                File(
                    context.filesDir,
                    "$CACHE_FILE.tmp"
                )

            tempFile.writeText(
                json,
                Charsets.UTF_8
            )

            if (
                !tempFile.renameTo(
                    cacheFile
                )
            ) {

                /*
                 * renameTo can fail if destination
                 * exists on some Android versions.
                 */
                cacheFile.delete()

                if (
                    !tempFile.renameTo(
                        cacheFile
                    )
                ) {

                    throw Exception(
                        "Could not replace cache file"
                    )
                }
            }

            /*
             * Store HTTP validators.
             */
            val newEtag =
                connection
                    .getHeaderField("ETag")

            val newLastModified =
                connection.lastModified

            prefs.edit()
                .apply {

                    if (
                        !newEtag.isNullOrBlank()
                    ) {

                        putString(
                            KEY_ETAG,
                            newEtag
                        )
                    }

                    if (
                        newLastModified > 0L
                    ) {

                        putLong(
                            KEY_LAST_MODIFIED,
                            newLastModified
                        )
                    }

                }
                .apply()

            Log.d(
                TAG,
                "Remote database downloaded: ${json.length} chars"
            )

            return true

        } finally {

            connection.disconnect()
        }
    }

    /**
     * Load whichever database is currently selected.
     */
    private fun loadCurrentDatabase() {

        val context =
            appContext
                ?: return

        val url =
            getDatabaseUrl(context)

        if (url == LOCAL) {

            Log.d(
                TAG,
                "Using LOCAL items.json"
            )

            currentJson =
                loadLocalDatabase(context)

            return
        }

        val cacheFile =
            File(
                context.filesDir,
                CACHE_FILE
            )

        if (cacheFile.exists()) {

            try {

                val json =
                    cacheFile.readText(
                        Charsets.UTF_8
                    )

                if (json.isNotBlank()) {

                    currentJson =
                        json

                    Log.d(
                        TAG,
                        "Using cached remote items.json"
                    )

                    return
                }

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "Failed reading remote cache",
                    e
                )
            }
        }

        /*
         * No remote cache yet.
         *
         * Fall back to local database until
         * the remote download completes.
         */
        Log.d(
            TAG,
            "No remote cache - using local items.json temporarily"
        )

        currentJson =
            loadLocalDatabase(context)
    }

    /**
     * Read bundled assets/items.json.
     */
    private fun loadLocalDatabase(
        context: Context
    ): String {

        return try {

            context.assets
                .open("items.json")
                .bufferedReader(
                    Charsets.UTF_8
                )
                .use {
                    it.readText()
                }

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Failed to load local items.json",
                e
            )

            "[]"
        }
    }
}

