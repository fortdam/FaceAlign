package com.tangzm.imagefacedetector;

import java.util.Stack;

import android.util.Log;

public class FuncTracer {
	
	static class Node {
		public Node(String className, String methodName, long time){
			mClassName = className;
			mMethodName = methodName;
			mStartTime = time;
		}
		public String mMethodName;    //name of func
		public String mClassName;
		public long mStartTime; //in ms
	}
	
	
	public static void startFunc() {
		if (BuildConfig.DEBUG){
		    StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
		    StackTraceElement e = stacktrace[3];//coz 0th will be getStackTrace so 1st
		    String funcName = e.getMethodName();
		    String fullClassName = e.getClassName();
			String className = fullClassName.substring(fullClassName.lastIndexOf(".")+1, fullClassName.length());

			mFuncStack.push(new Node(className, funcName, System.currentTimeMillis()));

			if (!PRECISE_MODE){
				Log.i(TAG, mMargin + className + ":" + funcName + "() start...");
			}
			mMargin += INDENT;
		}
	}
	
	public static void endFunc(){
		if (BuildConfig.DEBUG){	
		    Node item = mFuncStack.pop();
		    
		    if (STRICT_MODE) {
			    StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
			    StackTraceElement e = stacktrace[3];//coz 0th will be getStackTrace so 1st
			    String funcName = e.getMethodName();
			    String fullClassName = e.getClassName();
				String className = fullClassName.substring(fullClassName.lastIndexOf(".")+1, fullClassName.length());
			    
			    if (funcName.compareTo(item.mMethodName) != 0 || className.compareTo(item.mClassName) != 0){
			    	//Not equal
			    	Log.e(TAG, "Exception: endFunc and startFunc do not occur in pair!");
			    }
		    }
			
			mMargin = mMargin.substring(0, mMargin.length() - INDENT.length());

			Log.i(TAG, mMargin + item.mClassName + ":" + item.mMethodName + "() finish...<" + (System.currentTimeMillis()-item.mStartTime) + "ms>");

		}		
	}
	
	public static void procException(Exception e) {
		StackTraceElement[] Etrace = e.getStackTrace();
		StackTraceElement[] Ctrace = Thread.currentThread().getStackTrace();
		
		int sameLevel = 1;
		
		while (0 == Ctrace[Ctrace.length-sameLevel].getMethodName().compareTo(Etrace[Etrace.length-sameLevel].getMethodName())){
			//trim the same func history in the stack
			sameLevel += 1;
		}
		
		for (int i=0; i<Etrace.length-sameLevel; i++) {
			Node topItem = mFuncStack.peek();
			
			if (Etrace[i].getMethodName().compareTo(topItem.mMethodName)==0) {
				mFuncStack.pop();
				mMargin = mMargin.substring(0, mMargin.length() - INDENT.length());
			}
		}
	}
	
	private static final String TAG = "FuncTracer";
	private static final String INDENT = "  ";
	private static final boolean PRECISE_MODE = false; //Otherwise COMPLETE MODE
	private static final boolean STRICT_MODE = false; //Strict mode will be slow. If startFunc/endFunc do not come in pair, an Exception will be created
	
	private static String mMargin = new String();
	private static Stack<Node> mFuncStack = new Stack<Node>();
}
