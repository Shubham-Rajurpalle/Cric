package com.cricketApp.cric.home.Shots

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2

class InterceptableRecyclerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    var viewPager2: ViewPager2? = null

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        when (ev?.action) {

            MotionEvent.ACTION_DOWN -> {
                viewPager2?.isUserInputEnabled = false
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                viewPager2?.postDelayed({ viewPager2?.isUserInputEnabled = false }, 200)
            }
        }
        return super.onInterceptTouchEvent(ev)
    }
}
