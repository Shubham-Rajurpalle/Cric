package com.cricketApp.cric.Chat

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.TextView
import com.cricketApp.cric.R

/**
 * Custom view for displaying a poll option with a progress bar and vote count
 */
class PollOptionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val radioButton: RadioButton
    private val textViewOption: TextView
    private val textViewPercentage: TextView
    private val progressBar: ProgressBar

    init {
        LayoutInflater.from(context).inflate(R.layout.item_poll_option, this, true)

        radioButton = findViewById(R.id.radioButtonOption)
        textViewOption = findViewById(R.id.textViewOption)
        textViewPercentage = findViewById(R.id.textViewPercentage)
        progressBar = findViewById(R.id.progressBarOption)

        // Default state
        radioButton.isEnabled = false
    }

    /**
     * Set the text of the poll option
     */
    fun setOptionText(text: String) {
        textViewOption.text = text
    }

    /**
     * Set the percentage of votes for this option and animate the progress bar
     */
    fun setVotePercentage(percentage: Int) {
        textViewPercentage.text = "$percentage%"

        // Animate the progress bar
        val animator = ObjectAnimator.ofInt(progressBar, "progress", 0, percentage)
        animator.duration = 500
        animator.interpolator = DecelerateInterpolator()
        animator.start()
    }

    /**
     * Set the vote count display
     */
    fun setVoteCount(count: Int) {
        // Optional: Display vote count somewhere if needed
    }

    /**
     * Mark this option as selected
     */
    override fun setSelected(selected: Boolean) {
        radioButton.isChecked = selected

        if (selected) {
            textViewOption.setTypeface(null, Typeface.BOLD)
        } else {
            textViewOption.setTypeface(null, Typeface.NORMAL)
        }
    }
}