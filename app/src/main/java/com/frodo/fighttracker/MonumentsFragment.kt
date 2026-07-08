package com.frodo.fighttracker

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment

class MonumentsFragment : Fragment() {


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        return WebFragment
            .newInstance(
                "https://floorchart.top/"
            )
            .onCreateView(
                inflater,
                container,
                savedInstanceState
            )!!

    }
}