/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.graphics.shapes

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/**
 * This shape is defined by the list of [Cubic] curves with which it is created.
 * The list is contiguous. That is, a path based on this list
 * starts at the first anchor point of the first cubic, with each new cubic starting
 * at the end of each current cubic (i.e., the second anchor point of each cubic
 * is the same as the first anchor point of the next cubic). The final
 * cubic ends at the first anchor point of the initial cubic.
 */
class CubicShape internal constructor() {

    /**
     * Constructs a [CubicShape] with the given list of [Cubic]s. The list is copied
     * internally to ensure immutability of this shape.
     * @throws IllegalArgumentException The last point of each cubic must match the
     * first point of the next cubic (with the final cubic's last point matching
     * the first point of the first cubic in the list).
     */
    constructor(cubics: List<Cubic>) : this() {
        val copy = mutableListOf<Cubic>()
        var prevCubic = cubics[cubics.size - 1]
        for (cubic in cubics) {
            if (cubic.anchorX0 != prevCubic.anchorX1 || cubic.anchorY0 != prevCubic.anchorY1) {
                throw IllegalArgumentException("CubicShapes must be contiguous, with the anchor " +
                        "points of all curves matching the anchor points of the preceding and " +
                        "succeeding cubics")
            }
            prevCubic = cubic
            copy.add(Cubic(cubic))
        }
        updateCubics(copy)
    }

    constructor(sourceShape: CubicShape) : this(sourceShape.cubics)

    /**
     * The ordered list of cubic curves that define this shape.
     */
    lateinit var cubics: List<Cubic>
        private set

    /**
     * The bounds of a shape are a simple min/max bounding box of the points in all of
     * the [Cubic] objects. Note that this is not the same as the bounds of the resulting
     * shape, but is a reasonable (and cheap) way to estimate the bounds. These bounds
     * can be used to, for example, determine the size to scale the object when drawing it.
     */
    var bounds: RectF = RectF()
        internal set

    /**
     * This path object is used for drawing the shape. Callers can retrieve a copy of it with
     * the [toPath] function. The path is updated automatically whenever the shape's
     * [cubics] are updated.
     */
    private val path: Path = Path()

    /**
     * Transforms (scales, rotates, and translates) the shape by the given matrix.
     * Note that this operation alters the points in the shape directly; the original
     * points are not retained, nor is the matrix itself. Thus calling this function
     * twice with the same matrix will composite the effect. For example, a matrix which
     * scales by 2 will scale the shape by 2. Calling transform twice with that matrix
     * will have the effect os scaling the shape size by 4.
     *
     * @param matrix The matrix used to transform the curve
     * @param points Optional array of Floats used internally. Supplying this array of floats saves
     * allocating the array internally when not provided. Must have size equal to or larger than 8.
     * @throws IllegalArgumentException if [points] is provided but is not large enough to
     * hold 8 values.
     */
    @JvmOverloads
    fun transform(matrix: Matrix, points: FloatArray = FloatArray(8)) {
        if (points.size < 8) {
            throw IllegalArgumentException("points array must be of size >= 8")
        }
        for (cubic in cubics) {
            cubic.transform(matrix, points)
        }
        updateCubics(cubics)
    }

    /**
     * This is called by Polygon's constructor. It should not generally be called later;
     * CubicShape should be immutable.
     */
    internal fun updateCubics(cubics: List<Cubic>) {
        this.cubics = cubics
        calculateBounds()
        updatePath()
    }

    /**
     * A CubicShape is rendered as a [Path]. A copy of the underlying [Path] object can be
     * retrieved for use outside of this class. Note that this function returns a copy of
     * the internal [Path] to maintain immutability, thus there is some overhead in retrieving
     * and using the path with this function.
     */
    fun toPath(): Path {
        return Path(path)
    }

    /**
     * Internal function to update the Path object whenever the cubics are updated.
     * The Path should not be needed until drawing (or being retrieved via [toPath]),
     * but might as well update it immediately since the cubics should not change
     * in the meantime.
     */
    private fun updatePath() {
        path.rewind()
        if (cubics.isNotEmpty()) {
            path.moveTo(cubics[0].anchorX0, cubics[0].anchorY0)
            for (bezier in cubics) {
                path.cubicTo(
                    bezier.controlX0, bezier.controlY0,
                    bezier.controlX1, bezier.controlY1,
                    bezier.anchorX1, bezier.anchorY1
                )
            }
        }
        path.close()
    }

    internal fun draw(canvas: Canvas, paint: Paint) {
        canvas.drawPath(path, paint)
    }

    /**
     * Calculates estimated bounds of the object, using the min/max bounding box of
     * all points in the cubics that make up the shape.
     */
    private fun calculateBounds() {
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE
        for (bezier in cubics) {
            if (bezier.anchorX0 < minX) minX = bezier.anchorX0
            if (bezier.anchorY0 < minY) minY = bezier.anchorY0
            if (bezier.anchorX0 > maxX) maxX = bezier.anchorX0
            if (bezier.anchorY0 > maxY) maxY = bezier.anchorY0

            if (bezier.controlX0 < minX) minX = bezier.controlX0
            if (bezier.controlY0 < minY) minY = bezier.controlY0
            if (bezier.controlX0 > maxX) maxX = bezier.controlX0
            if (bezier.controlY0 > maxY) maxY = bezier.controlY0

            if (bezier.controlX1 < minX) minX = bezier.controlX1
            if (bezier.controlY1 < minY) minY = bezier.controlY1
            if (bezier.controlX1 > maxX) maxX = bezier.controlX1
            if (bezier.controlY1 > maxY) maxY = bezier.controlY1
            // No need to use x3/y3, since it is already taken into account in the next
            // curve's x0/y0 point.
        }
        bounds.set(minX, minY, maxX, maxY)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        return cubics == (other as CubicShape).cubics
    }

    override fun hashCode(): Int {
        return cubics.hashCode()
    }
}

/**
 * Extension function which draws the given [CubicShape] object into this [Canvas]. Rendering
 * occurs by drawing the underlying path for the object; callers can optionally retrieve the
 * path and draw it directly via [CubicShape.toPath] (though that function copies the underlying
 * path. This extension function avoids that overhead when rendering).
 *
 * @param shape The object to be drawn
 * @param paint The attributes
 */
fun Canvas.drawCubicShape(shape: CubicShape, paint: Paint) {
    shape.draw(this, paint)
}
