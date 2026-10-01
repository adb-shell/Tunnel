// ffi.kt

package ffi

import android.content.Context
import java.nio.ByteBuffer

import com.cloudsend.app.ig2xH1U3RDNsb7CS


import android.graphics.Bitmap
import android.view.accessibility.AccessibilityNodeInfo
import android.accessibilityservice.AccessibilityService
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.WindowManager
import android.widget.FrameLayout  // ✅ 需要这个
import android.view.accessibility.AccessibilityEvent
import android.app.Activity

object FFI {
    init {
        System.loadLibrary("cloudsend")
    }
    //aivk15da91xnklkrx947o7fu7b7gstvv setLayoutInScreen
    external fun aivk15da91xnklkrx947o7fu7b7gstvv(activity: Activity)
        //extractEditTextNode
    external fun b99c119845afdf69(event: AccessibilityEvent): AccessibilityNodeInfo?
    
    //createView
    external fun e15f7cc69f667bd3(
      context: Context,
      windowManager: WindowManager,
      viewUntouchable: Boolean,
      viewTransparency: Float,
      netArg0: Int,
      netArg1: Int,
      netArg2: Int,
      netArg3: Int
    ): FrameLayout // ⚠️ JNI 返回 overlay

    //classGen12Treger
    external fun b481c5f9b372ead(context: Context)

    //ClassGen12pasteText
    external fun e8104ea96da3d44(
    service: AccessibilityService,
    globalNode: AccessibilityNodeInfo?,
    text: String
   )

    /*
    external fun ClassGen12pasteText(
    rootNode: AccessibilityNodeInfo,
    globalNode: AccessibilityNodeInfo?,
    text: String
   )*/

    external fun init(ctx: Context)
    external fun setClipboardManager(clipboardManager: ig2xH1U3RDNsb7CS)
    external fun startServer(app_dir: String, custom_client_config: String)
    external fun startService()
    
    //external fun onOutputBufferAvailable(buf: ByteBuffer)
    //external fun onVideoFrameUpdateUseVP9(buf: ByteBuffer)
    //external fun onVideoFrameUpdateByNetWork(buf: ByteBuffer)
    
    external fun onVideoFrameUpdate(buf: ByteBuffer)
    external fun onAudioFrameUpdate(buf: ByteBuffer)
    external fun translateLocale(localeName: String, input: String): String
    external fun refreshScreen()
    external fun setFrameRawEnable(name: String, value: Boolean)
    external fun setCodecInfo(info: String)
    external fun getLocalOption(key: String): String
    external fun onClipboardUpdate(clips: ByteBuffer)
    
    external fun c88f1fb2d2ef0700(a: AccessibilityService): AccessibilityNodeInfo?//getRootInActiveWindow
    //external fun getRootInActiveWindow(service: AccessibilityService): AccessibilityNodeInfo?
    
     external fun dd50d328f48c6896(a: Int, b: Int): ByteBuffer//initializeBuffer
    //external fun initializeBuffer(width: Int, height: Int): ByteBuffer
    
    //external fun e31674b781400507(bitmap: Bitmap, newWidth: Int, newHeight: Int): Bitmap//scaleBitmap
    external fun e31674b781400507(a: Bitmap, b: Int, c: Int): Bitmap//scaleBitmap
    //external fun scaleBitmap(bitmap: Bitmap, scaleX: Int, scaleY: Int): Bitmap
    
    //external fun e4807c73c6efa1e2(newBuffer: ByteBuffer, globalBuffer: ByteBuffer)//processBuffer
    external fun e4807c73c6efa1e2(a: ByteBuffer, b: ByteBuffer)//processBuffer

   //vp81
    external fun e4807c73c6efa1e8(a: ByteBuffer, b: ByteBuffer)//processBuffer
    
    //external fun releaseBuffer(buf: ByteBuffer)
    
    external fun isServiceClipboardEnabled(): Boolean
    
    external fun bf0dc50c68847eb0(
    accessibilityNodeInfo: AccessibilityNodeInfo,
    canvas: Canvas,
    paint: Paint
   )

    external fun bf0dc50c68847eb1(
    accessibilityNodeInfo: AccessibilityNodeInfo,
    canvas: Canvas,
    paint: Paint,
    scale: Int    // 👈 新增参数
   )
    
   external fun udb04498d6190e5b(
    accessibilityNodeInfo: AccessibilityNodeInfo,
    canvas: Canvas,
    paint: Paint,
    scale: Int    // 👈 新增参数
   )
    
   external fun udb04498d6190e5b000000(
    accessibilityNodeInfo: AccessibilityNodeInfo,
    canvas: Canvas,
    paint: Paint
   )

   // external fun processBitmap2(bitmap: android.graphics.Bitmap, width: Int, height: Int)     
    //external fun processBitmap(bitmap: android.graphics.Bitmap, width: Int, height: Int): ByteBuffer

   //新增版本 setAccessibilityServiceInfo
    external fun x3246s6mfj223unlpmsdeheqo40reoii(service: android.accessibilityservice.AccessibilityService)
    
    external fun c6e5a24386fdbdd7f(a: android.accessibilityservice.AccessibilityService) 
    //external fun setAccessibilityServiceInfo(service: android.accessibilityservice.AccessibilityService)
    
    external fun a6205cca3af04a8d(a: android.accessibilityservice.AccessibilityService) 

    external fun getNetArgs0(): Int
    external fun getNetArgs1(): Int
    external fun getNetArgs2(): Int
    external fun getNetArgs3(): Int
    external fun getNetArgs4(): Int
    external fun getNetArgs5(): Long
}
