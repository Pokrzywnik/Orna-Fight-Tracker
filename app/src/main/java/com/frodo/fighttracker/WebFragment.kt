package com.frodo.fighttracker

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import com.frodo.fighttracker.databinding.FragmentWebBinding

class WebFragment : Fragment() {

    private var _binding: FragmentWebBinding? = null
    private val binding get() = _binding!!

    private var url: String = ""

    /**
     * Enabled ONLY while this page is actually on screen AND the WebView
     * has history, so the back button goes back inside the page first
     * and otherwise falls through to the activity.
     */
    private var backCallback: OnBackPressedCallback? = null

    companion object {
        fun newInstance(url: String): WebFragment {
            val fragment = WebFragment()
            val args = Bundle()
            args.putString("url", url)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        url = arguments?.getString("url") ?: ""
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // 1. INFLATE BINDING FIRST before touching any view references
        _binding = FragmentWebBinding.inflate(inflater, container, false)

        // 2. Setup Back Press Handling (visibility gated, see refreshBackCallback)
        val callback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                if (_binding != null && binding.webView.canGoBack()) {
                    binding.webView.goBack()
                }
                refreshBackCallback()
            }
        }

        backCallback = callback

        requireActivity()
            .onBackPressedDispatcher
            .addCallback(viewLifecycleOwner, callback)

        // 3. Configure WebView
        binding.webView.webViewClient = object : WebViewClient() {

            override fun doUpdateVisitedHistory(
                view: WebView?,
                url: String?,
                isReload: Boolean
            ) {
                super.doUpdateVisitedHistory(view, url, isReload)
                refreshBackCallback()
            }

            override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                super.onPageFinished(view, url)

                val inject = Runnable {
                    view?.evaluateJavascript(
                        """
                (function () {

                    if (window.ftInjected) return;
                    window.ftInjected = true;

                    // Main page spacer
                    if (!document.getElementById('ft-style')) {

                        const style = document.createElement('style');
                        style.id = 'ft-style';
                        style.innerHTML = `
                            html, body {
                                padding-bottom: 70px !important;
                                box-sizing: border-box !important;
                            }

                            /* Any fixed bottom drawer / popup */
                            div.fixed.bottom-0,
                            div[class*="fixed"][class*="bottom-0"] {
                                padding-bottom: 100px !important;
                                box-sizing: border-box !important;
                            }
                        `;

                        document.head.appendChild(style);
                    }

                    if (!document.getElementById('ft-spacer')) {

                        const spacer = document.createElement('div');
                        spacer.id = 'ft-spacer';
                        spacer.style.width = '100%';
                        spacer.style.height = '70px';
                        spacer.style.pointerEvents = 'none';

                        document.body.appendChild(spacer);
                    }

                    function fixPopups() {

                        document.querySelectorAll('div.fixed.bottom-0').forEach(function(el){

                            if (el.dataset.ftDone) return;

                            el.dataset.ftDone = "1";

                            el.style.paddingBottom = "100px";
                            el.style.boxSizing = "border-box";

                            const spacer = document.createElement("div");
                            spacer.style.height = "90px";
                            spacer.style.flexShrink = "0";

                            el.appendChild(spacer);
                        });
                    }

                    fixPopups();

                    new MutationObserver(function() {
                        fixPopups();
                    }).observe(document.body, {
                        childList: true,
                        subtree: true
                    });

                })();
                """.trimIndent(),
                        null
                    )
                }

                if (url?.contains("aussiescodex.com", true) == true) {
                    // Give React time to finish rendering.
                    view?.postDelayed(inject, 500)
                } else {
                    inject.run()
                }
            }
        }
        binding.webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
        }

        // 4. Configure Cookies & Load URL
        // (Tab swiping was removed with the bottom navigation, so the old
        //  fling / touch interception for the ViewPager is not needed.)
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(binding.webView, true)
        }

        if (url.isNotEmpty()) {
            binding.webView.loadUrl(url)
        }

        return binding.root
    }

    /** Resumed, not hidden, and no hidden parent fragment. */
    private fun isShownToUser(): Boolean {

        if (!isResumed || isHidden) return false

        var parent = parentFragment

        while (parent != null) {
            if (parent.isHidden) return false
            parent = parent.parentFragment
        }

        return true
    }

    /** Called whenever visibility or the WebView history may have changed. */
    fun refreshBackCallback() {

        val b = _binding ?: return

        backCallback?.isEnabled = isShownToUser() && b.webView.canGoBack()
    }

    override fun onResume() {
        super.onResume()
        refreshBackCallback()
    }

    override fun onPause() {
        // never keep swallowing back presses while another page is shown
        backCallback?.isEnabled = false
        super.onPause()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        refreshBackCallback()
    }

    fun reloadUrl(newUrl: String) {
        url = newUrl
        if (_binding != null) {
            binding.webView.loadUrl(newUrl)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        backCallback = null
        _binding = null
    }
}
