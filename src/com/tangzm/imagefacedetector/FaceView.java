package com.tangzm.imagefacedetector;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.widget.ImageView;



public class FaceView extends ImageView{
	interface Plotable {
		void plot(Canvas canvas);
	}
	
	private Paint currentPaint;
	private Plotable p;

	public FaceView(Context context, AttributeSet attrs) {
		super(context, attrs);
		
		currentPaint = new Paint();	
		currentPaint.setColor(0xFF0000FF);
		currentPaint.setStrokeWidth(4);
	}
	
	public void addPlot(Plotable pl){
		p = pl;
		this.invalidate();
	}
	
	@Override
	protected void onDraw(Canvas canvas){
		super.onDraw(canvas);
		//canvas.drawLine(100, 100, 600, 600, currentPaint);
		if (null != p)
			p.plot(canvas);
	}
}
