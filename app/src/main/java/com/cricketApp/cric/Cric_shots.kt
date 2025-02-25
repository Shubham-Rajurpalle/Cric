package com.cricketApp.cric

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import com.cricketApp.cric.databinding.FragmentCricShotsBinding
import java.util.UUID


class Cric_shots : Fragment() {

    private var _binding: FragmentCricShotsBinding? = null
    private val binding get() = _binding!!

    private lateinit var videoAdapter: VideoAdapter
    private val firebaseVideoManager = FirebaseVideoManager()

    private val PICK_VIDEO_REQUEST = 101
    private var videoUri: Uri? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding=FragmentCricShotsBinding.inflate(inflater,container,false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setUpRecyclerView()
        loadVideos()
        binding.fabToVideo.setOnClickListener {
            pickVideoFromGallery()
        }
    }

    private fun setUpRecyclerView(){
        videoAdapter=VideoAdapter(requireContext(), mutableListOf())
        var layoutManager=LinearLayoutManager(requireContext())
        binding.shotsRecyclerView.layoutManager=layoutManager
        binding.shotsRecyclerView.adapter=videoAdapter
    }

    private fun loadVideos() {
        firebaseVideoManager.getVideos { videos ->
            if (videos != null) {
                if (videos.isNotEmpty()) {
                    videoAdapter.updateVideos(videos)
                    videoAdapter.notifyDataSetChanged()
                    Toast.makeText(requireContext(), "Videos Loaded: ${videos.size}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "No Videos Found", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(requireContext(), "Failed to Load Videos", Toast.LENGTH_SHORT).show()
            }
        }
    }


    private fun pickVideoFromGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, PICK_VIDEO_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_VIDEO_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            videoUri = data.data // Get video URI
            uploadVideoToFirebase()
        }
    }

    private fun uploadVideoToFirebase() {
        if (videoUri == null) {
            Toast.makeText(requireContext(), "No video selected", Toast.LENGTH_SHORT).show()
            return
        }

        val videoId = UUID.randomUUID().toString() // Unique ID
        val videoTitle = "Video ${System.currentTimeMillis()}"
        val videoContent = Cric_shot_video(videoId, videoTitle, "", 0, System.currentTimeMillis())

        firebaseVideoManager.uploadVideo(videoContent, videoUri!!) { task ->
            if (task.isSuccessful) {
                Toast.makeText(requireContext(), "Video Uploaded", Toast.LENGTH_SHORT).show()
                loadVideos()
            } else {
                Toast.makeText(requireContext(), "Upload Failed", Toast.LENGTH_SHORT).show()
            }
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        _binding=null
    }

}