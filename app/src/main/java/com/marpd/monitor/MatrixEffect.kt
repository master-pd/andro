package com.marpd.monitor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.View
import kotlin.random.Random

class MatrixEffect(context: Context) : View(context) {
    
    private val matrixChars = "01アイウエオカキクケコサシスセソタチツテトナニヌネノハヒフヘホマミムメモヤユヨラリルレロワヲンabcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private val drops = mutableListOf<Drop>()
    private val paint = Paint().apply {
        color = Color.GREEN
        textSize = 20f
        typeface = Typeface.MONOSPACE
        isAntiAlias = true
    }
    
    init {
        // Initialize drops
        for (i in 0 until (width / 20)) {
            drops.add(Drop(
                x = i * 20f,
                y = Random.nextFloat() * -height,
                speed = Random.nextFloat() * 5 + 2,
                length = Random.nextInt(5, 20)
            ))
        }
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Draw black background
        canvas.drawColor(Color.BLACK)
        
        // Draw matrix rain
        for (drop in drops) {
            // Draw drop with fading effect
            for (j in 0 until drop.length) {
                val charY = drop.y - (j * 20)
                if (charY in 0f..height.toFloat()) {
                    // Calculate opacity based on position in drop
                    val opacity = (255 * (drop.length - j) / drop.length).toInt()
                    paint.alpha = opacity
                    
                    // Random character
                    val char = matrixChars.random()
                    canvas.drawText(char.toString(), drop.x, charY, paint)
                }
            }
            
            // Move drop
            drop.y += drop.speed
            
            // Reset drop if it goes off screen
            if (drop.y - (drop.length * 20) > height) {
                drop.y = Random.nextFloat() * -100
                drop.speed = Random.nextFloat() * 5 + 2
                drop.length = Random.nextInt(5, 20)
            }
        }
        
        // Continue animation
        invalidate()
    }
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Reinitialize drops if size changes
        drops.clear()
        for (i in 0 until (w / 20)) {
            drops.add(Drop(
                x = i * 20f,
                y = Random.nextFloat() * -h,
                speed = Random.nextFloat() * 5 + 2,
                length = Random.nextInt(5, 20)
            ))
        }
    }
    
    private data class Drop(
        var x: Float,
        var y: Float,
        var speed: Float,
        var length: Int
    )
}
