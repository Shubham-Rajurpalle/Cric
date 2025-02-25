package com.cricketApp.cric

import android.net.Uri
import android.util.Log
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.UploadTask


class FirebaseVideoManager {
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val videosRef: CollectionReference = db.collection("videos")

    fun uploadVideo(video: Cric_shot_video, videoUri: Uri?, listener: OnCompleteListener<Void?>?) {
        val storageRef = FirebaseStorage.getInstance().reference
            .child(("videos/" + video.id).toString() + ".mp4")

        storageRef.putFile(videoUri!!)
            .addOnSuccessListener { taskSnapshot: UploadTask.TaskSnapshot? ->
                storageRef.downloadUrl
                    .addOnSuccessListener { uri: Uri? ->
                        video.uploadTimestamp=System.currentTimeMillis()
                        video.viewCount=0

                        video.id?.let {
                            if (listener != null) {
                                videosRef.document(it).set(video)
                                    .addOnCompleteListener(listener)
                            }
                        }
                    }
            }
    }

    fun getVideos(callback: (List<Cric_shot_video>?) -> Unit) {
        val videoList = mutableListOf<Cric_shot_video>()

        FirebaseFirestore.getInstance().collection("videos")
            .orderBy("uploadTimestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                for (document in documents) {
                    val video = document.toObject(Cric_shot_video::class.java)
                    videoList.add(video)
                }
                Log.d("Firebase", "Videos Retrieved: ${videoList.size}")
                callback(videoList)
            }
            .addOnFailureListener { e ->
                Log.e("Firebase", "Error getting videos", e)
                callback(null)
            }
    }
    fun incrementViewCount(videoId: String?) {
        val videoRef: DocumentReference? = videoId?.let { videosRef.document(it) }
        db.runTransaction { transaction ->
            val snapshot: DocumentSnapshot? = videoRef?.let { transaction.get(it) }
            val newViewCount: Long = snapshot?.getLong("viewCount")?.plus(1) ?: 0
            if (videoRef != null) {
                transaction.update(videoRef, "viewCount", newViewCount)
            }
            null
        }
    }
}