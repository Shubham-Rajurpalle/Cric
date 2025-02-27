package com.cricketApp.cric.home.Shots

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.cricketApp.cric.databinding.FragmentCricShotsBinding
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class Cric_shots : Fragment() {

    private var _binding: FragmentCricShotsBinding? = null
    private val binding get() = _binding!!

    private lateinit var videoAdapter: VideoAdapter
    private val videoList = mutableListOf<Video>()
    private val firestore = FirebaseFirestore.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCricShotsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        fetchVideos()
    }

    private fun setupRecyclerView() {
        binding.shotsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        videoAdapter = VideoAdapter(childFragmentManager, videoList)
        binding.shotsRecyclerView.adapter = videoAdapter
    }

    private fun fetchVideos() {
        firestore.collection("videos")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("Firestore", "Error fetching videos", error)
                    return@addSnapshotListener
                }

                snapshot?.let {
                    videoList.clear()
                    videoList.addAll(it.toObjects(Video::class.java))
                    videoAdapter.notifyDataSetChanged()
                }
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
