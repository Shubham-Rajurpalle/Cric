package com.cricketApp.cric.home.Shots

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager2.widget.ViewPager2
import com.cricketApp.cric.R
import com.cricketApp.cric.databinding.FragmentCricShotsBinding
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class Cric_shots : Fragment() {

    private var _binding: FragmentCricShotsBinding? = null
    private val binding get() = _binding!!

    private lateinit var videoAdapter: VideoAdapter
    private lateinit var newsAdapter: NewsAdapter
    private val videoList = mutableListOf<Video>()
    private val newsList = mutableListOf<News>()
    private var firestore: FirebaseFirestore? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        firestore = FirebaseFirestore.getInstance()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCricShotsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupSwipeRefreshLayout()
        setupCricShotsRecyclerView()
        setupNewsRecyclerView()
        fetchVideos()
        fetchNews()
    }

    private fun setupSwipeRefreshLayout() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            refreshData()
        }
    }

    private fun refreshData() {
        val b = _binding ?: return
        b.llAnime.visibility = View.VISIBLE
        b.llAnime2.visibility = View.VISIBLE
        videoList.clear()
        newsList.clear()
        videoAdapter.notifyDataSetChanged()
        newsAdapter.notifyDataSetChanged()
        fetchVideos()
        fetchNews()
    }

    private fun setupNewsRecyclerView() {
        binding.newsRecycleView.layoutManager = LinearLayoutManager(
            requireContext(),
            LinearLayoutManager.VERTICAL,
            false
        )
        newsAdapter = NewsAdapter(newsList, requireContext())
        binding.newsRecycleView.adapter = newsAdapter
    }

    private fun setupCricShotsRecyclerView() {
        val viewPager2 = requireActivity().findViewById<ViewPager2>(R.id.viewPager)
        binding.shotsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            (this as? InterceptableRecyclerView)?.viewPager2 = viewPager2
        }
        videoAdapter = VideoAdapter(videoList) { video -> openVideoPlayer(video) }
        binding.shotsRecyclerView.adapter = videoAdapter
    }

    private fun fetchVideos() {
        val firestoreInstance = firestore ?: return
        if (!isAdded) return

        firestoreInstance.collection("videos")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snapshot ->
                // Guard: fragment may have been destroyed by the time this fires
                if (!isAdded || _binding == null) return@addOnSuccessListener

                _binding?.llAnime?.visibility = View.GONE
                snapshot?.let {
                    if (!it.isEmpty) {
                        videoList.clear()
                        videoList.addAll(it.toObjects(Video::class.java))
                        videoAdapter.notifyDataSetChanged()
                        checkRefreshComplete()
                    }
                }
            }
            .addOnFailureListener {
                if (!isAdded || _binding == null) return@addOnFailureListener
                checkRefreshComplete()
            }
    }

    private fun fetchNews() {
        firestore?.collection("NewsPosts")
            ?.orderBy("timestamp", Query.Direction.DESCENDING)
            ?.get()
            ?.addOnSuccessListener { snapshot ->
                // Guard: fragment may have been destroyed by the time this fires
                if (!isAdded || _binding == null) return@addOnSuccessListener

                _binding?.llAnime2?.visibility = View.GONE
                snapshot?.let {
                    if (!it.isEmpty) {
                        newsList.clear()
                        newsList.addAll(it.toObjects(News::class.java))
                        newsAdapter.notifyDataSetChanged()
                        checkRefreshComplete()
                    }
                }
            }
            ?.addOnFailureListener {
                if (!isAdded || _binding == null) return@addOnFailureListener
                checkRefreshComplete()
            }
    }

    private fun checkRefreshComplete() {
        val b = _binding ?: return
        if (b.llAnime.visibility == View.GONE && b.llAnime2.visibility == View.GONE) {
            b.swipeRefreshLayout.isRefreshing = false
        }
    }

    private fun openVideoPlayer(video: Video) {
        val intent = Intent(requireContext(), VideoPlayerActivity::class.java).apply {
            putExtra("videoUrl", video.videoUrl)
            putExtra("id", video.id)
        }
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Use _binding (nullable) not binding here — view is already being destroyed
        _binding?.shotsRecyclerView?.adapter = null
        _binding?.newsRecycleView?.adapter = null
        _binding = null
    }

    override fun onDestroy() {
        super.onDestroy()
        firestore = null
    }
}