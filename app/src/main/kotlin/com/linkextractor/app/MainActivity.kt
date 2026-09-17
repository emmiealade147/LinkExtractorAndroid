package com.linkextractor.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items

import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

import androidx.lifecycle.lifecycleScope

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import okhttp3.OkHttpClient
import okhttp3.Request

import org.jsoup.Jsoup

import java.net.URI


class MainActivity : ComponentActivity() {

    private val client = OkHttpClient()

    private val links = mutableStateListOf<String>()

    private var sourceUrl by mutableStateOf("")

    private var status by mutableStateOf(
        "Share a webpage from Chrome or another browser."
    )

    private var loading by mutableStateOf(false)

    private var filter by mutableStateOf("")


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handleIntent(intent)

        setContent {
            AppUi()
        }
    }


    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        handleIntent(intent)
    }


    private fun handleIntent(intent: Intent?) {

        val text = intent?.getStringExtra(Intent.EXTRA_TEXT)

        val uri = intent?.data

        val candidate = uri?.toString() ?: text

        if (!candidate.isNullOrBlank()) {

            val url = Regex(
                """https?://[^\s]+"""
            )
                .find(candidate)
                ?.value
                ?.trimEnd('.', ',', ')', ']', ';')


            if (url != null) {

                sourceUrl = url

                extract(url)
            }
        }
    }


    private fun extract(url: String) {

        loading = true

        status = "Extracting links..."


        lifecycleScope.launch(Dispatchers.IO) {

            try {

                val request = Request.Builder()
                    .url(url)
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Android) LinkExtractor/1.0"
                    )
                    .build()


                client.newCall(request).execute().use { response ->

                    if (!response.isSuccessful) {

                        error("HTTP ${response.code}")
                    }


                    val html = response.body?.string() ?: ""


                    val document = Jsoup.parse(
                        html,
                        url
                    )


                    val found = linkedSetOf<String>()


                    document
                        .select("a[href]")
                        .forEach { element ->

                            val href = element
                                .attr("abs:href")
                                .trim()


                            if (isValidHttpUrl(href)) {

                                found.add(href)
                            }
                        }


                    withContext(Dispatchers.Main) {

                        links.clear()

                        links.addAll(found)

                        status =
                            "${links.size} unique valid HTTP(S) links found."

                        loading = false
                    }
                }

            } catch (e: Exception) {

                withContext(Dispatchers.Main) {

                    loading = false

                    status =
                        "Could not extract links: ${
                            e.message ?: "Unknown error"
                        }"
                }
            }
        }
    }


    private fun isValidHttpUrl(value: String): Boolean {

        return try {

            val uri = URI(value)

            (
                uri.scheme.equals("http", true) ||
                uri.scheme.equals("https", true)
            ) &&
                    !uri.host.isNullOrBlank()

        } catch (_: Exception) {

            false
        }
    }


    private fun copyAll() {

        val visible = links.filter {

            filter.isBlank() ||
                    it.contains(filter, true)
        }


        val clipboard =
            getSystemService(
                Context.CLIPBOARD_SERVICE
            ) as ClipboardManager


        clipboard.setPrimaryClip(

            ClipData.newPlainText(
                "Extracted links",
                visible.joinToString("\n")
            )
        )


        status =
            "${visible.size} links copied to clipboard."
    }


    @Composable
    private fun AppUi() {

        MaterialTheme {

            Scaffold(

                topBar = {

                    TopAppBar(

                        title = {

                            Text("Link Extractor")
                        }
                    )
                }

            ) { padding ->


                Column(

                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp)
                ) {


                    OutlinedTextField(

                        value = sourceUrl,

                        onValueChange = {
                            sourceUrl = it
                        },

                        label = {
                            Text("Webpage URL")
                        },

                        singleLine = true,

                        modifier =
                            Modifier.fillMaxWidth()
                    )


                    Spacer(
                        Modifier.height(8.dp)
                    )


                    Row(

                        Modifier.fillMaxWidth(),

                        horizontalArrangement =
                            Arrangement.spacedBy(8.dp)
                    ) {


                        Button(

                            onClick = {

                                if (
                                    sourceUrl.startsWith(
                                        "http"
                                    )
                                ) {

                                    extract(sourceUrl)
                                }
                            },

                            enabled = !loading

                        ) {

                            Text(

                                if (loading)
                                    "Working..."
                                else
                                    "Extract"
                            )
                        }


                        OutlinedButton(

                            onClick = {
                                copyAll()
                            },

                            enabled = links.isNotEmpty()

                        ) {

                            Text("Copy All")
                        }
                    }


                    Spacer(
                        Modifier.height(8.dp)
                    )


                    OutlinedTextField(

                        value = filter,

                        onValueChange = {
                            filter = it
                        },

                        label = {
                            Text("Filter links")
                        },

                        singleLine = true,

                        modifier =
                            Modifier.fillMaxWidth()
                    )


                    Spacer(
                        Modifier.height(8.dp)
                    )


                    Text(
                        status,
                        style =
                            MaterialTheme
                                .typography
                                .bodyMedium
                    )


                    Spacer(
                        Modifier.height(8.dp)
                    )


                    val visible =
                        links.filter {

                            filter.isBlank() ||
                                    it.contains(
                                        filter,
                                        true
                                    )
                        }


                    LazyColumn(
                        Modifier.fillMaxSize()
                    ) {

                        items(visible) { link ->

                            Text(
                                link,
                                modifier =
                                    Modifier.padding(
                                        vertical = 6.dp
                                    )
                            )

                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}
