package com.frodo.fighttracker

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import com.frodo.fighttracker.databinding.FragmentWebBinding
import kotlin.math.abs

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

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // 1. INFLATE BINDING FIRST before touching any view references
        _binding = FragmentWebBinding.inflate(inflater, container, false)

        // 2. Setup Back Press Handling
        requireActivity()
            .onBackPressedDispatcher
            .addCallback(
                viewLifecycleOwner,
                object : OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() {
                        if (_binding != null && binding.webView.canGoBack()) {
                            binding.webView.goBack()
                        } else {
                            isEnabled = false
                            requireActivity().onBackPressedDispatcher.onBackPressed()
                        }
                    }
                }
            )

        // 3. Configure WebView
        binding.webView.webViewClient = WebViewClient()
        binding.webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
        }

        // 4. Setup Fling & Drag Gesture Detection
        val gestureDetector = GestureDetector(
            requireContext(),
            object : GestureDetector.SimpleOnGestureListener() {
                private val SWIPE_THRESHOLD_VELOCITY = 1200 // Velocity in px/s (lower = easier to trigger)
                private val SWIPE_MIN_DISTANCE = 80         // Min distance in px

                override fun onFling(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    velocityX: Float,
                    velocityY: Float
                ): Boolean {
                    if (e1 == null) return false

                    val diffX = e2.x - e1.x
                    val diffY = e2.y - e1.y

                    // Ensure the swipe is primarily horizontal and fast enough
                    if (abs(diffX) > abs(diffY) &&
                        abs(diffX) > SWIPE_MIN_DISTANCE &&
                        abs(velocityX) > SWIPE_THRESHOLD_VELOCITY
                    ) {
                        val viewPager = activity?.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.viewPager)
                        if (viewPager != null) {
                            if (diffX < 0) {
                                // Finger moved Right -> Left (Swipe Next Tab)
                                if (viewPager.currentItem < (viewPager.adapter?.itemCount ?: 0) - 1) {
                                    viewPager.setCurrentItem(viewPager.currentItem + 1, true)
                                    return true
                                }
                            } else {
                                // Finger moved Left -> Right (Swipe Previous Tab)
                                if (viewPager.currentItem > 0) {
                                    viewPager.setCurrentItem(viewPager.currentItem - 1, true)
                                    return true
                                }
                            }
                        }
                    }
                    return false
                }
            }
        )

        binding.webView.setOnTouchListener { v, event ->
            gestureDetector.onTouchEvent(event)

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
            false
        }

        // 5. Configure Cookies & Load URL
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