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

package com.uniqtech.musicplayer.extensions.resources

import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.TypedValue
import android.view.KeyEvent
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.AnimationUtils
import android.view.animation.DecelerateInterpolator
import android.widget.CompoundButton
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.MenuRes
import androidx.appcompat.widget.Toolbar
import androidx.core.animation.addListener
import androidx.core.animation.doOnEnd
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.toColorInt
import androidx.core.view.drawToBitmap
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.updatePaddingRelative
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.navigation.NavigationBarView
import com.google.android.material.navigationrail.NavigationRailView
import com.google.android.material.shape.MaterialShapeDrawable
import com.uniqtech.musicplayer.R
import com.uniqtech.musicplayer.extensions.dip
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.glide.GlideImagesPlugin
import me.zhanghai.android.fastscroll.FastScroller
import me.zhanghai.android.fastscroll.FastScrollerBuilder
import me.zhanghai.android.fastscroll.PopupStyles

const val BOOMING_ANIM_TIME = 350L

val View.backgroundColor: Int
    get() = (background as? ColorDrawable)?.color
        ?: (background as? MaterialShapeDrawable)?.fillColor?.defaultColor
        ?: Color.TRANSPARENT

fun View.shake() {
    ObjectAnimator.ofFloat(this, View.TRANSLATION_X, 0f, 30f, -30f, 20f, -20f, 10f, -10f, 5f, -5f, 0f).apply {
        duration = BOOMING_ANIM_TIME / 2
        interpolator = AccelerateInterpolator()
        addListener(
            onStart = {
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
            },
            onEnd = {
                setLayerType(View.LAYER_TYPE_NONE, null)
            },
            onCancel = {
                setLayerType(View.LAYER_TYPE_NONE, null)
            }
        )
    }.also { shakeAnimator ->
        shakeAnimator.start()
    }
}

fun View.showBounceAnimation() {
    clearAnimation()
    scaleX = 0.9f
    scaleY = 0.9f
    isVisible = true
    pivotX = (width / 2).toFloat()
    pivotY = (height / 2).toFloat()

    animate().setDuration(200)
        .setInterpolator(DecelerateInterpolator())
        .scaleX(1.1f)
        .scaleY(1.1f)
        .withEndAction {
            animate().setDuration(200)
                .setInterpolator(AccelerateInterpolator())
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .start()
        }
        .start()
}

typealias AnimationCompleted = () -> Unit

fun View.show(animate: Boolean = false, onCompleted: AnimationCompleted? = null) {
    if (!animate) {
        alpha = 1f
        isVisible = true
        onCompleted?.invoke()
    } else {
        if (isVisible && alpha == 1f) {
            onCompleted?.invoke()
            return
        }
        this.animate()
            .alpha(1f)
            .setDuration(BOOMING_ANIM_TIME)
            .withStartAction {
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
                isVisible = true
            }
            .withEndAction {
                setLayerType(View.LAYER_TYPE_NONE, null)
                onCompleted?.invoke()
            }
            .start()
    }
}

fun View.hide(animate: Boolean = false, onCompleted: AnimationCompleted? = null) {
    if (!animate) {
        isVisible = false
        onCompleted?.invoke()
    } else {
        if (!isVisible) {
            onCompleted?.invoke()
            return
        }
        this.animate()
            .alpha(0f)
            .setDuration(BOOMING_ANIM_TIME)
            .withStartAction {
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
            }
            .withEndAction {
                setLayerType(View.LAYER_TYPE_NONE, null)
                isVisible = false
                onCompleted?.invoke()
            }
            .start()
    }
}

fun View.reactionToKey(targetKeyCode: Int, action: (KeyEvent) -> Unit) {
    setOnKeyListener { view, keyCode, keyEvent ->
        if (keyEvent.hasNoModifiers()) {
            if (keyEvent.action == KeyEvent.ACTION_UP) {
                if (keyCode == targetKeyCode) {
                    view.cancelLongPress()
                    action(keyEvent)
                    return@setOnKeyListener true
                }
            }
        }
        false
    }
}

fun View.addPaddingRelative(
    start: Int = 0,
    top: Int = 0,
    end: Int = 0,
    bottom: Int = 0
) {
    updatePaddingRelative(paddingStart + start, paddingTop + top, paddingEnd + end, paddingBottom + bottom)
}

fun View.centerPivot() {
    post {
        pivotX = (width / 2).toFloat()
        pivotY = (height / 2).toFloat()
    }
}

fun View.hitTest(x: Int, y: Int): Boolean {
    val tx = (translationX + 0.5f)
    val ty = (translationY + 0.5f)
    val left = left + tx
    val right = right + tx
    val top = top + ty
    val bottom = bottom + ty

    return x >= left && x <= right && y >= top && y <= bottom
}

fun TextView.setMarkdownText(str: String) {
    val markwon = Markwon.builder(context)
        .usePlugin(GlideImagesPlugin.create(context)) // image loader
        .usePlugin(HtmlPlugin.create()) // basic Html tags
        .usePlugin(object : AbstractMarkwonPlugin() {
            override fun configureTheme(builder: MarkwonTheme.Builder) {
                val typedColor = TypedValue()
                context.theme.resolveAttribute(com.google.android.material.R.attr.dividerColor, typedColor, true)

                builder.headingBreakColor("#00ffffff".toColorInt())
                    .thematicBreakColor(typedColor.data)
                    .thematicBreakHeight(2)
                    .bulletWidth(12)
                    .headingTextSizeMultipliers(
                        floatArrayOf(2f, 1.5f, 1f, .83f, .67f, .55f)
                    )
            }
        })
        .build()

    markwon.setMarkdown(this, str)
}

fun ImageView.useAsIcon() {
    val iconPadding = context.dip(R.dimen.list_item_image_icon_padding)
    setPadding(iconPadding, iconPadding, iconPadding, iconPadding)
    clearColorFilter()
}

fun Toolbar.inflateMenu(
    @MenuRes menuId: Int,
    itemClickListener: Toolbar.OnMenuItemClickListener,
    menuConsumer: ((Menu) -> Unit)? = null
) {
    inflateMenu(menuId)
    setOnMenuItemClickListener(itemClickListener)
    menuConsumer?.invoke(menu)
}

fun RecyclerView.useLinearLayout() {
    layoutManager = LinearLayoutManager(context)
}

fun RecyclerView.destroyOnDetach() {
    layoutManager?.let {
        if (it is LinearLayoutManager) {
            it.recycleChildrenOnDetach = true
        }
    }
}

fun ViewGroup.createFastScroller(): FastScroller {
    val fastScrollerBuilder = FastScrollerBuilder(this)
    fastScrollerBuilder.useMd2Style()
    fastScrollerBuilder.setPopupStyle { popupText ->
        PopupStyles.MD2.accept(popupText)
    }
    return fastScrollerBuilder.build()
}

/**
 * Potentially animate showing a [BottomNavigationView].
 *
 * Abruptly changing the visibility leads to a re-layout of main content, animating
 * `translationY` leaves a gap where the view was that content does not fill.
 *
 * Instead, take a snapshot of the view, and animate this in, only changing the visibility (and
 * thus layout) when the animation completes.
 */
fun NavigationBarView.show() {
    if (this is NavigationRailView) return
    if (isVisible) return

    val parent = parent as ViewGroup
    // View needs to be laid out to create a snapshot & know position to animate. If view isn't
    // laid out yet, need to do this manually.
    if (!isLaidOut) {
        measure(
            View.MeasureSpec.makeMeasureSpec(parent.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(parent.height, View.MeasureSpec.AT_MOST)
        )
        layout(parent.left, parent.height - measuredHeight, parent.right, parent.height)
    }

    val drawable = drawToBitmap().toDrawable(context.resources)
    drawable.setBounds(left, parent.height, right, parent.height + height)
    parent.overlay.add(drawable)
    ValueAnimator.ofInt(parent.height, top).apply {
        duration = BOOMING_ANIM_TIME
        interpolator = AnimationUtils.loadInterpolator(context, android.R.interpolator.accelerate_decelerate)
        addUpdateListener {
            val newTop = it.animatedValue as Int
            drawable.setBounds(left, newTop, right, newTop + height)
        }
        doOnEnd {
            parent.overlay.remove(drawable)
            isVisible = true
        }
        start()
    }
}

/**
 * Potentially animate hiding a [BottomNavigationView].
 *
 * Abruptly changing the visibility leads to a re-layout of main content, animating
 * `translationY` leaves a gap where the view was that content does not fill.
 *
 * Instead, take a snapshot, instantly hide the view (so content lays out to fill), then animate
 * out the snapshot.
 */
fun NavigationBarView.hide() {
    if (this is NavigationRailView) return
    if (isGone) return

    if (!isLaidOut) {
        isGone = true
        return
    }

    val drawable = drawToBitmap().toDrawable(context.resources)
    val parent = parent as ViewGroup
    drawable.setBounds(left, top, right, bottom)
    parent.overlay.add(drawable)
    isGone = true
    ValueAnimator.ofInt(top, parent.height).apply {
        duration = BOOMING_ANIM_TIME
        interpolator = AnimationUtils.loadInterpolator(context, android.R.interpolator.accelerate_decelerate)
        addUpdateListener {
            val newTop = it.animatedValue as Int
            drawable.setBounds(left, newTop, right, newTop + height)
        }
        doOnEnd {
            parent.overlay.remove(drawable)
        }
        start()
    }
}

fun BottomSheetBehavior<*>.peekHeightAnimate(value: Int): Animator {
    return ObjectAnimator.ofInt(this, "peekHeight", value).apply {
        duration = BOOMING_ANIM_TIME
        start()
    }
}

fun AppBarLayout.setupStatusBarForeground() {
    statusBarForeground = MaterialShapeDrawable.createWithElevationOverlay(context)
}

fun CompoundButton.animateToggle() = post { isChecked = !isChecked }