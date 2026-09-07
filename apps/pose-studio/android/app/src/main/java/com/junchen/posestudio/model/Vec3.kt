package com.junchen.posestudio.model

import kotlin.math.sqrt

data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(other: Vec3) = Vec3(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vec3) = Vec3(x - other.x, y - other.y, z - other.z)
    operator fun times(scale: Float) = Vec3(x * scale, y * scale, z * scale)
    operator fun div(scale: Float) = Vec3(x / scale, y / scale, z / scale)

    fun dot(other: Vec3): Float = x * other.x + y * other.y + z * other.z
    fun cross(other: Vec3) = Vec3(
        y * other.z - z * other.y,
        z * other.x - x * other.z,
        x * other.y - y * other.x,
    )

    fun lengthSquared(): Float = dot(this)
    fun length(): Float = sqrt(lengthSquared())
    fun normalized(fallback: Vec3 = X): Vec3 {
        val len = length()
        return if (len > EPSILON) this / len else fallback
    }

    fun isFinite(): Boolean = x.isFinite() && y.isFinite() && z.isFinite()

    companion object {
        const val EPSILON = 1e-5f
        val ZERO = Vec3(0f, 0f, 0f)
        val X = Vec3(1f, 0f, 0f)
        val Y = Vec3(0f, 1f, 0f)
        val Z = Vec3(0f, 0f, 1f)
    }
}

fun distance(a: Vec3, b: Vec3): Float = (a - b).length()
