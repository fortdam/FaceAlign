package com.tangzm.imagefacedetector;

import java.util.Stack;

import android.util.Log;

public class FuncTracer {
	
	static class Node {
		public Node(String className, String methodName, long time){
			mClassName = className;
			mMethodName = methodName;
			mProcName = null;
			mStartTime = time;
		}
		
		public Node(String procName, long time){
			mProcName = procName;
			mClassName = null;
			mMethodName = null;
			mStartTime = time;
		}
		
		public String mMethodName;    //name of func
		public String mClassName;
		public String mProcName;
		public long mStartTime; //in ms
	}
	
	
	public static void startFunc() {
		if (BuildConfig.DEBUG && !mException){
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
		if (BuildConfig.DEBUG && !mException){	
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
	
	public static void startProc(String token) {
		if (BuildConfig.DEBUG && !mException){
			mFuncStack.push(new Node(token, System.currentTimeMillis()));

			if (!PRECISE_MODE){
				Log.i(TAG, mMargin + "<" + token + ">" + " start...");
			}
			mMargin += INDENT;
		}
	}
	
	public static void endProc(String token) {
		if (BuildConfig.DEBUG && !mException){	
		    Node item = mFuncStack.pop();
		    
		    if (STRICT_MODE) {
			    if (token.compareTo(item.mProcName) != 0){
			    	//Not equal
			    	Log.e(TAG, "Exception: endProc and startPRoc do not occur in pair!");
			    	return;
			    }
		    }
			
			mMargin = mMargin.substring(0, mMargin.length() - INDENT.length());

			Log.i(TAG, mMargin + "<" + token + ">" + " finish...<" + (System.currentTimeMillis()-item.mStartTime) + "ms>");

		}				
	}
	
	public static void procException(Exception e) {
		mException = true;
	}
	
	private static boolean mException = false;
	
	private static final String TAG = "FuncTracer";
	private static final String INDENT = "  ";
	private static final boolean PRECISE_MODE = false; //Otherwise COMPLETE MODE
	private static final boolean STRICT_MODE = false; //Strict mode will be slow. If startFunc/endFunc do not come in pair, an Exception will be created
	
	private static String mMargin = new String();
	private static Stack<Node> mFuncStack = new Stack<Node>();
}
