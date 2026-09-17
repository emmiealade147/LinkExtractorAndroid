package com.linkextractor.app

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

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


class MainActivity : ComponentActivity() {

    private var webView: WebView? = null

    private val links = mutableStateListOf<String>()

    private var sourceUrl by mutableStateOf("")

    private var status by mutableStateOf(
        "Enter a webpage URL."
    )

    private var filter by mutableStateOf("")


    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        sourceUrl = getSharedUrl(intent) ?: ""

        setContent {
            AppUi()
        }
    }


    override fun onNewIntent(intent: Intent) {

        super.onNewIntent(intent)

        val url = getSharedUrl(intent)

        if (!url.isNullOrBlank()) {

            sourceUrl = url

            loadPage(url)
        }
    }


    private fun getSharedUrl(intent: Intent?): String? {

        if (intent == null) return null

        val sharedText =
            intent.getStringExtra(Intent.EXTRA_TEXT)

        val sharedUrl =
            intent.data?.toString()

        val candidate =
            sharedUrl ?: sharedText

        if (candidate.isNullOrBlank()) {
            return null
        }

        return Regex(
            """https?://[^\s]+"""
        )
            .find(candidate)
            ?.value
            ?.trimEnd('.', ',', ')', ']', ';')
            ?: if (
                candidate.startsWith("http://") ||
                candidate.startsWith("https://")
            ) {
                candidate
            } else {
                null
            }
    }


    @SuppressLint("SetJavaScriptEnabled")
    private fun loadPage(url: String) {

        status = "Loading webpage..."

        links.clear()

        webView?.let { browser ->

            browser.loadUrl(url)

            return
        }


        val browser = WebView(this)

        webView = browser

        browser.settings.apply {

            javaScriptEnabled = true

            domStorageEnabled = true

            databaseEnabled = true

            loadsImagesAutomatically = true

            mixedContentMode =
                WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

            userAgentString =
                "Mozilla/5.0 (Linux; Android 12) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/140.0.0.0 Mobile Safari/537.36"
        }


        browser.webViewClient =
            object : WebViewClient() {

                override fun onPageFinished(
                    view: WebView?,
                    url: String?
                ) {

                    super.onPageFinished(
                        view,
                        url
                    )

                    status =
                        "Page loaded. Scanning links..."

                    extractRenderedLinks()
                }
            }


        browser.webChromeClient =
            WebChromeClient()


        browser.addJavascriptInterface(
            LinkBridge(),
            "AndroidLinkExtractor"
        )


        browser.loadUrl(url)
    }


    private fun extractRenderedLinks() {

        val script = """
            (function() {

                const links = [];

                const anchors =
                    document.querySelectorAll('a[href]');

                anchors.forEach(function(a) {

                    try {

                        const url =
                            new URL(
                                a.href,
                                document.baseURI
                            ).href;

                        links.push(url);

                    } catch (e) {
                    }
                });

                return JSON.stringify(links);

            })();
        """.trimIndent()


        webView?.evaluateJavascript(
            script
        ) { result ->

            val decoded =
                result
                    .removePrefix("\"")
                    .removeSuffix("\"")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")

            processJavascriptLinks(decoded)
        }
    }


    private fun processJavascriptLinks(json: String) {

        try {

            val cleaned =
                json
                    .removePrefix("[")
                    .removeSuffix("]")


            val found =
                cleaned
                    .split("\",\"")
                    .map {

                        it
                            .trim()
                            .trim('"')
                            .replace("\\/", "/")
                    }
                    .filter {

                        isValidHttpUrl(it)
                    }
                    .distinct()


            links.clear()

            links.addAll(found)


            status =
                "${links.size} unique HTTP(S) links found."

        } catch (e: Exception) {

            status =
                "Could not process page links."
        }
    }


    private fun isValidHttpUrl(
        value: String
    ): Boolean {

        return try {

            val uri =
                java.net.URI(value)

            (
                uri.scheme.equals(
                    "http",
                    true
                ) ||
                uri.scheme.equals(
                    "https",
                    true
                )
            ) &&
                !uri.host.isNullOrBlank()

        } catch (_: Exception) {

            false
        }
    }


    private fun copyAll() {

        val visible =
            links.filter {

                filter.isBlank() ||
                    it.contains(
                        filter,
                        true
                    )
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
            "${visible.size} links copied."
    }


    private inner class LinkBridge {

        @JavascriptInterface
        fun receiveLinks(
            value: String
        ) {
            runOnUiThread {

                processJavascriptLinks(
                    value
                )
            }
        }
    }


    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun AppUi() {

        MaterialTheme {

            Scaffold(

                topBar = {

                    TopAppBar(

                        title = {
                            Text(
                                "Link Extractor"
                            )
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

                        value =
                            sourceUrl,

                        onValueChange = {
                            sourceUrl = it
                        },

                        label = {
                            Text(
                                "Webpage URL"
                            )
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

                                    loadPage(
                                        sourceUrl
                                    )
                                }
                            }

                        ) {

                            Text(
                                "Open & Extract"
                            )
                        }


                        OutlinedButton(

                            onClick = {
                                extractRenderedLinks()
                            },

                            enabled =
                                webView != null

                        ) {

                            Text(
                                "Scan Again"
                            )
                        }


                        OutlinedButton(

                            onClick = {
                                copyAll()
                            },

                            enabled =
                                links.isNotEmpty()

                        ) {

                            Text(
                                "Copy All"
                            )
                        }
                    }


                    Spacer(
                        Modifier.height(8.dp)
                    )


                    OutlinedTextField(

                        value =
                            filter,

                        onValueChange = {
                            filter = it
                        },

                        label = {
                            Text(
                                "Filter links"
                            )
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

                        items(
                            visible
                        ) { link ->

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
