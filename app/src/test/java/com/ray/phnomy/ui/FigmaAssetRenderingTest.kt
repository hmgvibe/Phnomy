package com.ray.phnomy.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import com.caverock.androidsvg.SVG
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Makes unsupported SVG paint syntax visible before an icon silently ships blank. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FigmaAssetRenderingTest {
    @Test fun originalFigmaLeafAndRingRenderWithBrandColors() {
        val context=ApplicationProvider.getApplicationContext<Application>()
        for((file,color) in listOf("3e6de" to 0xFF274C3D.toInt(),"b2ef7" to 0xFFACCCB4.toInt(),
            "f9714" to 0xFF274C3D.toInt(),"89904" to 0xFFACCCB4.toInt())) {
            val svg=SVG.getFromAsset(context.assets,"figma/$file.svg")
            val picture=svg.renderToPicture()
            val bitmap=Bitmap.createBitmap(picture.width*3,picture.height*3,Bitmap.Config.ARGB_8888)
            val canvas=Canvas(bitmap)
            canvas.scale(3f,3f)
            canvas.drawPicture(picture)
            val output=File("build/qa/figma-$file.png").apply {parentFile.mkdirs()}
            output.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
            var exact=0
            for(y in 0 until bitmap.height) for(x in 0 until bitmap.width) if(bitmap.getPixel(x,y)==color) exact++
            assertTrue("Original Figma asset $file must render its exact brand paint",exact>0)
        }
    }
}
