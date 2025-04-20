/*
 * Copyright (c) 2024 Christians Martínez Alvarado
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.uniqtech.musicplayer.views

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.os.Looper
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.format.DateUtils
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.Scroller
import androidx.core.content.ContextCompat
import androidx.core.graphics.withSave
import com.uniqtech.musicplayer.R
import com.uniqtech.musicplayer.extensions.resources.getDrawableCompat
import com.uniqtech.musicplayer.lyrics.LrcEntry
import com.uniqtech.musicplayer.lyrics.LrcLyrics
import com.uniqtech.musicplayer.lyrics.LrcUtils
import kotlin.math.abs

/**
 * 歌词 Created by wcy on 2015/11/9.
 */
@SuppressLint("StaticFieldLeak")
class CoverLrcView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val mLrcEntryList: MutableList<LrcEntry> = ArrayList()
    private val mLrcPaint = TextPaint()
    private val mTimePaint = TextPaint()
    private var mTimeFontMetrics: Paint.FontMetrics? = null
    private var mPlayDrawable: Drawable? = null
    private var mDividerHeight = 0f
    private var mAnimationDuration: Long = 0
    private var mNormalTextColor = 0
    private var mNormalTextSize = 0f
    private var mCurrentTextColor = 0
    private var mCurrentTextSize = 0f
    private var mTimelineTextColor = 0
    private var mTimelineColor = 0
    private var mTimeTextColor = 0
    private var mDrawableWidth = 0
    private var mTimeTextWidth = 0
    private var mDefaultLabel: String = context.getString(R.string.empty_label)
    private var mLrcPadding = 0f
    private var mOnPlayClickListener: OnPlayClickListener? = null
    private var mAnimator: ValueAnimator? = null
    private var mGestureDetector: GestureDetector? = null
    private var mScroller: Scroller? = null
    private var mOffset = 0f
    private var mCurrentLine = 0
    private var isShowTimeline = false
    private var isTouching = false
    private var isFling = false
    private var mTextGravity = 0 // 歌词显示位置，靠左/居中/靠右
    private val hideTimelineRunnable = Runnable {
        if (hasLrc() && isShowTimeline) {
            isShowTimeline = false
            smoothScrollTo(mCurrentLine)
        }
    }

    private val mSimpleOnGestureListener: SimpleOnGestureListener =
        object : SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean {
                if (hasLrc() && mOnPlayClickListener != null) {
                    if (mOffset != getOffset(0)) {
                        parent.requestDisallowInterceptTouchEvent(true)
                    }
                    mScroller!!.forceFinished(true)
                    removeCallbacks(hideTimelineRunnable)
                    isTouching = true
                    isShowTimeline = true
                    invalidate()
                    return true
                }
                return super.onDown(e)
            }

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float,
            ): Boolean {
                if (mOffset == getOffset(0) && distanceY < 0F) {
                    return super.onScroll(e1, e2, distanceX, distanceY)
                }
                if (hasLrc()) {
                    mOffset += -distanceY
                    mOffset = mOffset.coerceAtMost(getOffset(0))
                    mOffset = mOffset.coerceAtLeast(getOffset(mLrcEntryList.size - 1))
                    invalidate()
                    parent.requestDisallowInterceptTouchEvent(true)
                    return true
                }
                return super.onScroll(e1, e2, distanceX, distanceY)
            }

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float,
            ): Boolean {
                if (hasLrc()) {
                    mScroller!!.fling(
                        0,
                        mOffset.toInt(),
                        0,
                        velocityY.toInt(),
                        0,
                        0,
                        getOffset(mLrcEntryList.size - 1).toInt(),
                        getOffset(0).toInt()
                    )
                    isFling = true
                    return true
                }
                return super.onFling(e1, e2, velocityX, velocityY)
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (hasLrc() && isShowTimeline && mPlayDrawable!!.bounds.contains(e.x.toInt(), e.y.toInt())) {
                    val centerLine = centerLine
                    val centerLineTime = mLrcEntryList[centerLine].time
                    // onPlayClick 消费了才更新 UI
                    if (mOnPlayClickListener != null && mOnPlayClickListener!!.onPlayClick(centerLineTime)) {
                        isShowTimeline = false
                        removeCallbacks(hideTimelineRunnable)
                        mCurrentLine = centerLine
                        animateCurrentTextSize()
                        return true
                    }
                } else {
                    callOnClick()
                    return true
                }
                return super.onSingleTapConfirmed(e)
            }
        }

    @SuppressLint("CustomViewStyleable")
    private fun init(attrs: AttributeSet?) {
        val ta = context.obtainStyledAttributes(attrs, R.styleable.LrcView)
        mCurrentTextSize = ta.getDimension(R.styleable.LrcView_lrcTextSize, resources.getDimension(R.dimen.lrc_text_size))
        mNormalTextSize = ta.getDimension(R.styleable.LrcView_lrcNormalTextSize, resources.getDimension(R.dimen.lrc_text_size))
        if (mNormalTextSize == 0f) {
            mNormalTextSize = mCurrentTextSize
        }
        mDividerHeight = ta.getDimension(R.styleable.LrcView_lrcDividerHeight, resources.getDimension(R.dimen.lrc_divider_height))
        val defDuration = resources.getInteger(R.integer.lrc_animation_duration)
        mAnimationDuration = ta.getInt(R.styleable.LrcView_lrcAnimationDuration, defDuration).toLong()
        mAnimationDuration = if (mAnimationDuration < 0) defDuration.toLong() else mAnimationDuration
        mNormalTextColor = ta.getColor(R.styleable.LrcView_lrcNormalTextColor, ContextCompat.getColor(context, R.color.lrc_normal_text_color))
        mCurrentTextColor = ta.getColor(R.styleable.LrcView_lrcCurrentTextColor, ContextCompat.getColor(context, R.color.lrc_current_text_color))
        mTimelineTextColor = ta.getColor(R.styleable.LrcView_lrcTimelineTextColor, ContextCompat.getColor(context, R.color.lrc_timeline_text_color))
        mDefaultLabel = ta.getString(R.styleable.LrcView_lrcLabel) ?: mDefaultLabel
        mLrcPadding = ta.getDimension(R.styleable.LrcView_lrcPadding, 0f)
        mTimelineColor = ta.getColor(R.styleable.LrcView_lrcTimelineColor, ContextCompat.getColor(context, R.color.lrc_timeline_color))
        mPlayDrawable = ta.getDrawable(R.styleable.LrcView_lrcPlayDrawable)
        mPlayDrawable = if (mPlayDrawable == null) context.getDrawableCompat(R.drawable.ic_play_24dp) else mPlayDrawable
        mTimeTextColor = ta.getColor(R.styleable.LrcView_lrcTimeTextColor, ContextCompat.getColor(context, R.color.lrc_time_text_color))
        mTextGravity = ta.getInteger(R.styleable.LrcView_lrcTextGravity, LrcEntry.GRAVITY_CENTER)

        val timelineHeight = ta.getDimension(R.styleable.LrcView_lrcTimelineHeight, resources.getDimension(R.dimen.lrc_timeline_height))
        val timeTextSize = ta.getDimension(R.styleable.LrcView_lrcTimeTextSize, resources.getDimension(R.dimen.lrc_time_text_size))

        ta.recycle()

        mDrawableWidth = resources.getDimension(R.dimen.lrc_drawable_width).toInt()
        mTimeTextWidth = resources.getDimension(R.dimen.lrc_time_width).toInt()

        mLrcPaint.isAntiAlias = true
        mLrcPaint.textSize = mCurrentTextSize
        mLrcPaint.textAlign = Paint.Align.LEFT
        mTimePaint.isAntiAlias = true
        mTimePaint.textSize = timeTextSize
        mTimePaint.textAlign = Paint.Align.CENTER
        mTimePaint.strokeWidth = timelineHeight
        mTimePaint.strokeCap = Paint.Cap.ROUND

        mTimeFontMetrics = mTimePaint.fontMetrics
        mGestureDetector = GestureDetector(context, mSimpleOnGestureListener)
        mGestureDetector!!.setIsLongpressEnabled(false)
        mScroller = Scroller(context)
    }

    fun setNormalColor(normalColor: Int) {
        mNormalTextColor = normalColor
        postInvalidate()
    }

    fun setCurrentColor(currentColor: Int) {
        mCurrentTextColor = currentColor
        postInvalidate()
    }

    fun setTimelineTextColor(timelineTextColor: Int) {
        mTimelineTextColor = timelineTextColor
        postInvalidate()
    }

    fun setTimelineColor(timelineColor: Int) {
        mTimelineColor = timelineColor
        postInvalidate()
    }

    fun setTimeTextColor(timeTextColor: Int) {
        mTimeTextColor = timeTextColor
        postInvalidate()
    }

    fun setDraggable(draggable: Boolean, onPlayClickListener: OnPlayClickListener?) {
        mOnPlayClickListener = if (draggable) {
            requireNotNull(onPlayClickListener) { "if draggable == true, onPlayClickListener must not be null" }
        } else {
            null
        }
    }

    fun setLRCContent(lyrics: LrcLyrics) {
        reset()
        if (lyrics.hasLines) {
            mLrcEntryList.addAll(lyrics.getValidLines())
        }
        mLrcEntryList.sort()
        initEntryList()
        invalidate()
    }

    fun hasLrc(): Boolean {
        return mLrcEntryList.isNotEmpty()
    }

    fun updateTime(time: Long) {
        runOnUi {
            if (!hasLrc()) {
                return@runOnUi
            }
            val line = findShowLine(time + 300L)
            if (line != mCurrentLine) {
                mCurrentLine = line
                if (!isShowTimeline) {
                    smoothScrollTo(line)
                    animateCurrentTextSize()
                } else {
                    invalidate()
                }
            }
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (changed) {
            initPlayDrawable()
            initEntryList()
            if (hasLrc()) {
                smoothScrollTo(mCurrentLine, 0L)
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val centerY = height / 2

        if (!hasLrc()) {
            mLrcPaint.color = mCurrentTextColor

            val staticLayout = StaticLayout.Builder.obtain(mDefaultLabel, 0, mDefaultLabel.length, mLrcPaint, lrcWidth.toInt())
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(0f, 1f)
                .setIncludePad(false)
                .build();

            drawText(canvas, staticLayout, centerY.toFloat())
            return
        }
        val centerLine = centerLine
        if (isShowTimeline) {
            mPlayDrawable?.draw(canvas)
            mTimePaint.color = mTimeTextColor
            val timeText = LrcUtils.formatTime(mLrcEntryList[centerLine].time)
            val timeX = (width - mTimeTextWidth / 2).toFloat()
            val timeY = centerY - (mTimeFontMetrics!!.descent + mTimeFontMetrics!!.ascent) / 2
            canvas.drawText(timeText, timeX, timeY, mTimePaint)
        }
        canvas.translate(0f, mOffset)
        var y = 0f
        for (i in mLrcEntryList.indices) {
            if (i > 0) {
                y += ((mLrcEntryList[i - 1].height + mLrcEntryList[i].height shr 1) + mDividerHeight)
            }
            if (i == mCurrentLine) {
                mLrcPaint.textSize = mCurrentTextSize
                mLrcPaint.color = mCurrentTextColor
            } else if (isShowTimeline && i == centerLine) {
                mLrcPaint.color = mTimelineTextColor
            } else {
                mLrcPaint.textSize = mNormalTextSize
                mLrcPaint.color = mNormalTextColor
            }
            drawText(canvas, mLrcEntryList[i].staticLayout, y)
        }
    }

    private fun drawText(canvas: Canvas, staticLayout: StaticLayout?, y: Float) {
        if (staticLayout == null) return

        canvas.withSave {
            translate(mLrcPadding, y - (staticLayout.height shr 1))
            staticLayout.draw(this)
        }
    }

    fun animateCurrentTextSize() {
        val currentTextSize = mCurrentTextSize
        ValueAnimator.ofFloat(mNormalTextSize, currentTextSize).apply {
            addUpdateListener {
                mCurrentTextSize = it.animatedValue as Float
                invalidate()
            }
            duration = 300L
            start()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
            isTouching = false
            if (hasLrc() && !isFling) {
                adjustCenter()
                postDelayed(hideTimelineRunnable, TIMELINE_KEEP_TIME)
            }
        }
        return mGestureDetector!!.onTouchEvent(event)
    }

    override fun computeScroll() {
        if (mScroller!!.computeScrollOffset()) {
            mOffset = mScroller!!.currY.toFloat()
            invalidate()
        }
        if (isFling && mScroller!!.isFinished) {
            isFling = false
            if (hasLrc() && !isTouching) {
                adjustCenter()
                postDelayed(hideTimelineRunnable, TIMELINE_KEEP_TIME)
            }
        }
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(hideTimelineRunnable)
        super.onDetachedFromWindow()
    }

    private fun initPlayDrawable() {
        val l = (mTimeTextWidth - mDrawableWidth) / 2
        val t = height / 2 - mDrawableWidth / 2
        val r = l + mDrawableWidth
        val b = t + mDrawableWidth
        mPlayDrawable!!.setBounds(l, t, r, b)
    }

    private fun initEntryList() {
        if (!hasLrc() || width == 0) {
            return
        }
        for (lrcEntry in mLrcEntryList) {
            lrcEntry.init(mLrcPaint, lrcWidth.toInt(), mTextGravity)
        }
        mOffset = (height / 2).toFloat()
    }

    fun reset() {
        endAnimation()
        mScroller!!.forceFinished(true)
        isShowTimeline = false
        isTouching = false
        isFling = false
        removeCallbacks(hideTimelineRunnable)
        mLrcEntryList.clear()
        mOffset = 0f
        mCurrentLine = 0
        invalidate()
    }

    private fun adjustCenter() {
        smoothScrollTo(centerLine, ADJUST_DURATION)
    }

    private fun smoothScrollTo(line: Int, duration: Long = mAnimationDuration) {
        val offset = getOffset(line)
        endAnimation()
        mAnimator = ValueAnimator.ofFloat(mOffset, offset).apply {
            this.duration = duration
            interpolator = LinearInterpolator()
            addUpdateListener { animation: ValueAnimator ->
                mOffset = animation.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun endAnimation() {
        if (mAnimator != null && mAnimator!!.isRunning) {
            mAnimator!!.end()
        }
    }

    private fun findShowLine(time: Long): Int {
        var left = 0
        var right = mLrcEntryList.size
        while (left <= right) {
            val middle = (left + right) / 2
            val middleTime = mLrcEntryList[middle].time
            if (time < middleTime) {
                right = middle - 1
            } else {
                if (middle + 1 >= mLrcEntryList.size || time < mLrcEntryList[middle + 1].time) {
                    return middle
                }
                left = middle + 1
            }
        }
        return 0
    }

    private val centerLine: Int
        get() {
            var centerLine = 0
            var minDistance = Float.MAX_VALUE
            for (i in mLrcEntryList.indices) {
                if (abs(mOffset - getOffset(i)) < minDistance) {
                    minDistance = abs(mOffset - getOffset(i))
                    centerLine = i
                }
            }
            return centerLine
        }

    private fun getOffset(line: Int): Float {
        if (mLrcEntryList.isEmpty()) return 0F
        if (mLrcEntryList[line].offset == Float.MIN_VALUE) {
            var offset = (height / 2).toFloat()
            for (i in 1..line) {
                offset -= ((mLrcEntryList[i - 1].height + mLrcEntryList[i].height shr 1)
                        + mDividerHeight)
            }
            mLrcEntryList[line].offset = offset
        }
        return mLrcEntryList[line].offset
    }

    private val lrcWidth: Float
        get() = width - mLrcPadding * 2

    private fun runOnUi(r: Runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            r.run()
        } else {
            post(r)
        }
    }

    fun interface OnPlayClickListener {
        fun onPlayClick(time: Long): Boolean
    }

    companion object {
        private const val ADJUST_DURATION: Long = 100
        private const val TIMELINE_KEEP_TIME = 4 * DateUtils.SECOND_IN_MILLIS
    }

    init {
        init(attrs)
    }
}