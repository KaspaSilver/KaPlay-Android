package com.kachat.app.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * SF Symbols that Material has no single icon for, put together from Material's own paths so
 * they tint and size like any other icon.
 */
object IosSymbols {
    /** "lock.shield": a shield outline with a solid lock inside. */
    val LockShield: ImageVector by lazy {
        val builder = ImageVector.Builder(
            name = "LockShield",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
        fun addPaths(group: VectorGroup) {
            for (node in group) {
                when (node) {
                    is VectorPath -> builder.addPath(
                        pathData = node.pathData,
                        pathFillType = node.pathFillType,
                        fill = SolidColor(Color.Black),
                    )
                    is VectorGroup -> addPaths(node)
                }
            }
        }
        addPaths(Icons.Outlined.Shield.root)
        builder.addGroup(scaleX = 0.4f, scaleY = 0.4f, pivotX = 12f, pivotY = 11.5f)
        addPaths(Icons.Filled.Lock.root)
        builder.clearGroup()
        builder.build()
    }

    /**
     * "quote.bubble": the speech bubble as an outline (Material ChatBubbleOutline) with the
     * same two quote marks as [QuoteBubbleFill], solid inside it - one even-odd path, so the
     * ring and the marks fill and the space between them stays clear.
     */
    val QuoteBubble: ImageVector by lazy {
        ImageVector.Builder(
            name = "QuoteBubble",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData = addPathNodes(
                "M20,2H4c-1.1,0 -2,0.9 -2,2v18l4,-4h14c1.1,0 2,-0.9 2,-2V4c0,-1.1 -0.9,-2 -2,-2z" +
                    "M20,16H5.17L4,17.17V4h16v12z" +
                    "M8.4,13h1.8l1.2,-2.4V7H7.8v3.6h1.8z" +
                    "M13.2,13H15l1.2,-2.4V7h-3.6v3.6h1.8z"
            ),
            pathFillType = PathFillType.EvenOdd,
            fill = SolidColor(Color.Black),
        ).build()
    }

    /**
     * "quote.bubble.fill": a solid speech bubble (Material ChatBubble) with two quote marks
     * (Material FormatQuote at 0.6x about the bubble's centre) cut out of it - one even-odd
     * path, so the marks are holes that show what is behind.
     */
    val QuoteBubbleFill: ImageVector by lazy {
        ImageVector.Builder(
            name = "QuoteBubbleFill",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData = addPathNodes(
                "M20,2H4c-1.1,0 -2,0.9 -2,2v18l4,-4h14c1.1,0 2,-0.9 2,-2V4c0,-1.1 -0.9,-2 -2,-2z" +
                    "M8.4,13h1.8l1.2,-2.4V7H7.8v3.6h1.8z" +
                    "M13.2,13H15l1.2,-2.4V7h-3.6v3.6h1.8z"
            ),
            pathFillType = PathFillType.EvenOdd,
            fill = SolidColor(Color.Black),
        ).build()
    }

    /**
     * The dock's Play icon: KaPlay's controller as one even-odd path, with the D-pad, the buttons
     * and the backward K as holes (iOS ControllerTabIcon.svg, drawn by make_chess_icon.py tab). Its
     * viewBox starts at 96,293, hence the group's shift.
     */
    val ControllerTab: ImageVector by lazy {
        ImageVector.Builder(
            name = "ControllerTab",
            defaultWidth = 32.dp,
            defaultHeight = 20.dp,
            viewportWidth = 832f,
            viewportHeight = 520f,
        ).addGroup(translationX = -96f, translationY = -293f).addPath(
            pathData = addPathNodes(
                "M330 300 L694 300 C800 300 858 336 880 412 C910 512 932 640 924 728 C916 806 836 832 786 784 " +
                    "C748 748 722 696 682 672 C604 652 420 652 342 672 C302 696 276 748 238 784 C188 832 108 806 100 728 " +
                    "C92 640 114 512 144 412 C166 336 224 300 330 300 Z " +
                    "M240 448 L240 416 C240 409.4 245.4 404 252 404 L272 404 C278.6 404 284 409.4 284 416 L284 448 L316 448 " +
                    "C322.6 448 328 453.4 328 460 L328 480 C328 486.6 322.6 492 316 492 L284 492 L284 524 C284 530.6 278.6 536 272 536 " +
                    "L252 536 C245.4 536 240 530.6 240 524 L240 492 L208 492 C201.4 492 196 486.6 196 480 L196 460 C196 453.4 201.4 448 208 448 Z " +
                    "M532.7 375.1 L568.7 370.0 L582.5 470.0 L568.7 570.0 L532.7 564.9 L543.5 491.2 L464.4 552.0 L442.5 523.5 L512.0 470.0 " +
                    "L442.5 416.5 L464.4 388.0 L543.5 448.8 Z " +
                    "M762 399 C774.703 399 785 409.297 785 422 C785 434.703 774.703 445 762 445 C749.297 445 739 434.703 739 422 C739 409.297 749.297 399 762 399 Z " +
                    "M810 447 C822.703 447 833 457.297 833 470 C833 482.703 822.703 493 810 493 C797.297 493 787 482.703 787 470 C787 457.297 797.297 447 810 447 Z " +
                    "M762 495 C774.703 495 785 505.297 785 518 C785 530.703 774.703 541 762 541 C749.297 541 739 530.703 739 518 C739 505.297 749.297 495 762 495 Z " +
                    "M714 447 C726.703 447 737 457.297 737 470 C737 482.703 726.703 493 714 493 C701.297 493 691 482.703 691 470 C691 457.297 701.297 447 714 447 Z"
            ),
            pathFillType = PathFillType.EvenOdd,
            fill = SolidColor(Color.Black),
        ).clearGroup().build()
    }
}
