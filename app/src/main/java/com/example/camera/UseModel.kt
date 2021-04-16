package com.example.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Color.rgb
import android.util.Log
import org.pytorch.IValue
import org.pytorch.Module
import org.pytorch.Tensor
import org.pytorch.torchvision.TensorImageUtils
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import kotlin.math.roundToInt


class UseModel (bitmap : Bitmap, module: Module){

    private var bitmap: Bitmap? = null
    private var mModule: Module? = null
    private var width : Int = 0
    private var height : Int = 0

    /**
     * constructor
     */
    init{
        this.bitmap = bitmap
        this.width = bitmap.width
        this.height = bitmap.height
        this.mModule = module
        Log.d("Init", "Init Successfully")
    }

    /**
     * main logic
     * return : Bitmap
     */
    fun process() : Bitmap {

        // inputTensor 생성
        var inputTensor: Tensor =bitmapToFloat32Tensor(bitmap, 0, 0, width, height)

        // outputTensor 생성 및 forward
        var outputTensor = mModule!!.forward(IValue.from(inputTensor)).toTuple()
        val dataAsFloatArray = outputTensor[1].toTensor().dataAsFloatArray

        // bitmap으로 만들어서 반환
        return floatArrayToBitmap(dataAsFloatArray, width, height)
    }

    /**
     * bitmap을 floatArray로 변환
     */
    private fun bitmapToFloatBuffer(bitmap: Bitmap, x: Int, y: Int, width: Int, height: Int, outBuffer: FloatBuffer, outBufferOffset: Int) {
        val pixelsCount = height * width
        val pixels = IntArray(pixelsCount)
        bitmap.getPixels(pixels, 0, width, x, y, width, height)
        val offset_b = 2 * pixelsCount
        for (i in 0 until pixelsCount) {
            val c = pixels[i]
            val r = (c shr 16 and 0xff) / 255.0f
            val g = (c shr 8 and 0xff) / 255.0f
            val b = (c and 0xff) / 255.0f
            outBuffer.put(outBufferOffset + i, r)
            outBuffer.put(outBufferOffset + pixelsCount + i, g)
            outBuffer.put(outBufferOffset + offset_b + i, b)
        }
    }

    /**
     * bitmap -> floatArray -> tensor ( 1, 3 ,width, height) 로 변환
     */
    private fun bitmapToFloat32Tensor(bitmap: Bitmap?, x: Int, y: Int, width: Int, height: Int): Tensor {
        val floatBuffer = Tensor.allocateFloatBuffer(3 * width * height)
        if (bitmap != null) {
            bitmapToFloatBuffer(
                bitmap,
                x,
                y,
                width,
                height,
                floatBuffer,
                0
            )
        }
        return Tensor.fromBlob(floatBuffer, longArrayOf(1, 3, height.toLong(), width.toLong()))
    }

    /**
     * floatArray를 RGB bitmap 으로 변환
     */
    private fun floatArrayToBitmap(floatArray: FloatArray, width: Int, height: Int) : Bitmap {

        // Create empty bitmap in ARGB format
        val bmp: Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height * 4)

        // mapping smallest value to 0 and largest value to 255
        val maxValue = floatArray.max() ?: 1.0f
        val minValue = floatArray.min() ?: -1.0f
        val delta = maxValue-minValue

        // Define if float min..max will be mapped to 0..255 or 255..0
        val conversion = { v: Float -> ((v-minValue)/delta*255.0f).roundToInt()}

        // copy each value from float array to RGB channels
        for (i in 0 until width * height) {
            val r = conversion(floatArray[i])
            val g = conversion(floatArray[i+width*height])
            val b = conversion(floatArray[i+2*width*height])
            pixels[i] = rgb(r, g, b) // you might need to import for rgb()
        }
        bmp.setPixels(pixels, 0, width, 0, 0, width, height)

        return bmp
    }
}

