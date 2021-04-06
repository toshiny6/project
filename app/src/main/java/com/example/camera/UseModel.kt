package com.example.camera

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.pytorch.IValue
import org.pytorch.Module
import org.pytorch.Tensor
import org.pytorch.torchvision.TensorImageUtils
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.log10

class UseModel (bitmap : Bitmap, context : Context){

    private var bitmap: Bitmap? = null;
    private var context: Context? = null;
    private var module: Module? = null;

    /**
     * constructor
     */
    init{
        this.bitmap = bitmap
        this.context= context
        Log.d("Init", "Init Successfully")
    }

    /**
     * main logic
     * return : Bitmap result -> 아직 모델 load 불가능해서 return 생락함
     */
    fun process() /*: Bitmap */ {

        // 모델 불러오기
        context?.let { loadModel(it) }

        /*
        // inputTensor 생성
        var inputTensor: Tensor = TensorImageUtils.bitmapToFloat32Tensor(
            bitmap,
            TensorImageUtils.TORCHVISION_NORM_MEAN_RGB,
            TensorImageUtils.TORCHVISION_NORM_STD_RGB)

        // outputTensor 생성 및 forward
        var outputTensor: Tensor = module!!.forward(IValue.from(inputTensor)).toTensor()
        Log.d("Tensor", outputTensor.toString())

        // output 결과로  result_image 생성, 반환

         */
    }


    /**
     * model load
     * Epoch99.pt, Epoch99_3.pt : tensor크기 맞지 않음 expect 12 but found 13
     * Epoch99_2.pt : tensor가 아니라 object가 들어옴  expect tensor but found Object
     */
    private fun loadModel(context: Context ){
        try{
            module = Module.load(assetFilePath(context, "Epoch99_3.pth"))
            Log.d("Model", "Model Loaded Successfully")
        } catch (e: IOException){
            Log.e("UseModel", "Load Model Failed", e)
        }
    }

    /**
     * return : model의 절대경로
     */
    @Throws(IOException::class)
    fun assetFilePath(context: Context, assetName: String?): String? {
        val file = File(context.filesDir, assetName!!)
        if (file.exists() && file.length() > 0) {
            return file.absolutePath
        }
        context.assets.open(assetName).use { `is` ->
            FileOutputStream(file).use { os ->
                val buffer = ByteArray(4 * 1024)
                var read: Int
                while (`is`.read(buffer).also { read = it } != -1) {
                    os.write(buffer, 0, read)
                }
                os.flush()
            }
            return file.absolutePath
        }
    }
}