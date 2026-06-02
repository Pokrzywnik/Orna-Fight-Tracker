package com.frodo.fighttracker

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment
import com.frodo.fighttracker.databinding.FragmentWebBinding

class WebFragment : Fragment() {

    private var _binding: FragmentWebBinding? = null
    private val binding get() = _binding!!

    private var url: String = ""

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

        _binding =
            FragmentWebBinding.inflate(
                inflater,
                container,
                false
            )

        binding.webView.webViewClient = WebViewClient()

        binding.webView.settings.apply {
            javaScriptEnabled = true

            domStorageEnabled = true

            databaseEnabled = true

            cacheMode =
                android.webkit.WebSettings.LOAD_DEFAULT
        }

        val cookieManager =
            android.webkit.CookieManager.getInstance()

        cookieManager.setAcceptCookie(true)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(
                binding.webView,
                true
            )
        }

        if (url.isNotEmpty()) {
            binding.webView.loadUrl(url)
        }

        return binding.root
    }

    fun reloadUrl(newUrl: String) {
        url = newUrl

        if (_binding != null) {
            binding.webView.loadUrl(newUrl)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()

        _binding = null
    }
}