package com.sheepspacefly.todomanager.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// 1. 完善粒子模型，增加旋转和形状属性
data class ConfettiParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val color: Color,
    val size: Float,
    val widthScale: Float, // 用于模拟翻转效果
    var rotation: Float,
    var rotationSpeed: Float,
    var alpha: Float = 1f,
    val shapeType: Int = Random.nextInt(3) // 0: 方形, 1: 长条, 2: 圆形
)

@Composable
fun ConfettiEffect(trigger: Boolean, triggerId: Int, onAnimationEnd: () -> Unit) {
    if (!trigger) return

    // 使用 remember 避免重组时重新创建列表
    val particles = remember { mutableStateListOf<ConfettiParticle>() }
    val colors = listOf(
        Color(0xFFFF5252), Color(0xFFFFEB3B), Color(0xFF2196F3),
        Color(0xFF4CAF50), Color(0xFFFF4081), Color(0xFF7C4DFF),
        Color(0xFF18FFFF)
    )

    // 初始化粒子：从屏幕底部中心向扇形区域喷射
    LaunchedEffect(triggerId) {
        if (trigger && triggerId > 0) {
            particles.clear() // 清除上一场还没落下的纸片
            repeat(100) {
                val angle = (Random.nextFloat() * 120f + 210f) * (PI.toFloat() / 180f)
                val speed = Random.nextFloat() * 0.035f + 0.02f
                particles.add(
                    ConfettiParticle(
                        x = 0.5f,
                        y = 1f,
                        vx = cos(angle) * speed,
                        vy = sin(angle) * speed,
                        color = colors.random(),
                        size = Random.nextFloat() * 20f + 20f,
                        widthScale = Random.nextFloat() * 0.6f + 0.4f,
                        rotation = Random.nextFloat() * 360f,
                        rotationSpeed = (Random.nextFloat() - 0.5f) * 12f,
                        shapeType = Random.nextInt(3)
                    )
                )
            }
        }
    }

    // 动画逻辑：物理引擎
    LaunchedEffect(trigger) {
        if(trigger && triggerId > 0){
            val startTime = System.currentTimeMillis()
            while (isActive) {
                val elapsed = System.currentTimeMillis() - startTime
                if (elapsed > 3500) break // 延长到3.5秒让粒子落出屏幕

                withFrameMillis {
                    for (i in particles.indices) {
                        val p = particles[i]
                        // 更新物理状态
                        val newVx = p.vx * 0.98f // 空气阻力
                        val newVy = p.vy + 0.0008f // 重力加速度（调小一点让它飘得久一点）
                        val newAlpha = if (elapsed > 2000) (p.alpha - 0.015f).coerceAtLeast(0f) else 1f

                        particles[i] = p.copy(
                            x = p.x + newVx,
                            y = p.y + newVy,
                            vx = newVx,
                            vy = newVy,
                            rotation = p.rotation + p.rotationSpeed,
                            alpha = newAlpha
                        )
                    }
                }
            }
            onAnimationEnd()
        }
    }

    // 绘制层
    Canvas(modifier = Modifier
        .fillMaxSize()
        .graphicsLayer(alpha = 0.99f) // 开启硬件加速
    ) {
        particles.forEach { p ->
            rotate(p.rotation, Offset(p.x * size.width + p.size / 2, p.y * size.height + p.size / 2)) {
                when (p.shapeType) {
                    0 -> { // 正方形
                        drawRect(
                            color = p.color.copy(alpha = p.alpha),
                            topLeft = Offset(p.x * size.width, p.y * size.height),
                            size = Size(p.size, p.size)
                        )
                    }
                    1 -> { // 长条形（会随旋转看起来像在翻转）
                        drawRect(
                            color = p.color.copy(alpha = p.alpha),
                            topLeft = Offset(p.x * size.width, p.y * size.height),
                            size = Size(p.size * p.widthScale, p.size)
                        )
                    }
                    2 -> { // 圆形/椭圆
                        drawOval(
                            color = p.color.copy(alpha = p.alpha),
                            topLeft = Offset(p.x * size.width, p.y * size.height),
                            size = Size(p.size, p.size * p.widthScale)
                        )
                    }
                }
            }
        }
    }
}