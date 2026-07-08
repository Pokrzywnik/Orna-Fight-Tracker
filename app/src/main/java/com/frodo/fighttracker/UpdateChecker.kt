package com.frodo.fighttracker

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread


object UpdateChecker {


    private const val API_URL =
        "https://api.github.com/repos/Pokrzywnik/Orna-Fight-Tracker/releases/latest"


    private const val RELEASE_URL =
        "https://github.com/Pokrzywnik/Orna-Fight-Tracker/releases/latest"



    fun check(context: Context) {


        thread {


            try {

                val connection =
                    URL(API_URL)
                        .openConnection()
                            as HttpURLConnection


                connection.apply {

                    requestMethod = "GET"
                    connectTimeout = 5000
                    readTimeout = 5000
                    setRequestProperty(
                        "Accept",
                        "application/vnd.github+json"
                    )
                }


                val response =
                    connection.inputStream
                        .bufferedReader()
                        .readText()


                val json =
                    JSONObject(response)


                val tag =
                    json.getString("tag_name")


                /*
                    Examples:

                    Update_2.0 -> 2.0
                    v2.1 -> 2.1
                    2.2 -> 2.2
                */

                val latestVersion =
                    tag
                        .replace("Update_", "")
                        .replace("v", "")
                        .trim()



                val currentVersion =
                    BuildConfig.VERSION_NAME



                Log.d(
                    "UPDATE",
                    "Current=$currentVersion Latest=$latestVersion"
                )



                if (isNewer(
                        currentVersion,
                        latestVersion
                    )
                ) {


                    Handler(
                        Looper.getMainLooper()
                    ).post {


                        showUpdateDialog(
                            context,
                            latestVersion
                        )

                    }
                }



            } catch (e: Exception) {


                Log.e(
                    "UPDATE",
                    "Update check failed",
                    e
                )

            }

        }

    }



    private fun isNewer(
        current:String,
        latest:String
    ):Boolean {


        val c =
            current.split(".")
                .map {
                    it.toIntOrNull() ?: 0
                }


        val l =
            latest.split(".")
                .map {
                    it.toIntOrNull() ?: 0
                }



        for(i in 0 until maxOf(
            c.size,
            l.size
        )) {


            val cv =
                c.getOrElse(i){0}

            val lv =
                l.getOrElse(i){0}



            if(lv > cv)
                return true


            if(lv < cv)
                return false

        }


        return false
    }



    private fun showUpdateDialog(
        context: Context,
        version:String
    ) {


        AlertDialog.Builder(context)

            .setTitle(
                "New update available"
            )

            .setMessage(
                "Version $version is available.\n\nDownload now?"
            )

            .setPositiveButton(
                "Download"
            ) { _,_ ->


                val intent =
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(RELEASE_URL)
                    )


                context.startActivity(intent)

            }


            .setNegativeButton(
                "Later",
                null
            )


            .show()

    }

}