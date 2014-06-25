package com.tangzm.facedetect;

import java.util.ArrayList;
import java.util.Stack;

import android.util.Log;

import com.tangzm.imagefacedetector.BuildConfig;

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
	
	static class FuncStack {
		public FuncStack(){
			mMargin = new String();
			mFuncStack = new Stack<Node>();
			mThreadID = Thread.currentThread().getId();
		}
		
		public void setPrefix(int index){
			for (;index>0; index--){
				mMargin += "*";
			}
		}

		
		public boolean isSameThread(){
			return (Thread.currentThread().getId() == mThreadID);
		}
		
		public long mThreadID;
		public String mMargin;
		public Stack<Node> mFuncStack;
	}
	
	private static FuncStack findCurrentStack(){
		int i = 0;
		
		for (i=0; i<mFuncStacks.size(); i++){
			FuncStack inst = mFuncStacks.get(i);
			if (inst.isSameThread()){
				return inst;
			}
		}	
		
		for (i=0; i<mFuncStacks.size(); i++){
			//Find an empty slot
			FuncStack inst = mFuncStacks.get(i);
			if (inst.mMargin.length()==0 || inst.mMargin.charAt(0) != ' '){
				inst.mThreadID = Thread.currentThread().getId();
				return inst;
			}
		}		
		FuncStack newInst = new FuncStack();
		newInst.setPrefix(i);
		Log.i(TAG, "***Totally "+(i+1)+" threads are traced***");
		mFuncStacks.add(newInst);
		
		return newInst;
	}
	
	public static void startFunc() {
		if (BuildConfig.DEBUG && mEnabled && !mException){
		    StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
		    StackTraceElement e = stacktrace[3];//coz 0th will be getStackTrace so 1st
		    String funcName = e.getMethodName();
		    String fullClassName = e.getClassName();
			String className = fullClassName.substring(fullClassName.lastIndexOf(".")+1, fullClassName.length());
			
			FuncStack currentStack = findCurrentStack();

			currentStack.mFuncStack.push(new Node(className, funcName, System.currentTimeMillis()));

			if (!CONCISE_MODE){
				Log.i(TAG, currentStack.mMargin + className + ":" + funcName + "() start...");
			}
			currentStack.mMargin = INDENT+currentStack.mMargin;
		}
	}
	
	public static void endFunc(){
		if (BuildConfig.DEBUG && mEnabled && !mException){	
			
			FuncStack currentStack = findCurrentStack();

		    Node item = currentStack.mFuncStack.pop();
		    
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
			
		    if (currentStack.mMargin.length() == INDENT.length()){
		    	currentStack.mMargin = "";
		    }
		    else {
		    	currentStack.mMargin = currentStack.mMargin.substring(INDENT.length(), currentStack.mMargin.length());
		    }
			Log.i(TAG, currentStack.mMargin + item.mClassName + ":" + item.mMethodName + "() finish...<" + (System.currentTimeMillis()-item.mStartTime) + "ms>");

		}		
	}
	
	public static void startProc(String token) {
		if (BuildConfig.DEBUG && mEnabled && !mException){
			FuncStack currentStack = findCurrentStack();
			
			currentStack.mFuncStack.push(new Node(token, System.currentTimeMillis()));

			if (!CONCISE_MODE){
				Log.i(TAG, currentStack.mMargin + "<" + token + ">" + " start...");
			}
			
			currentStack.mMargin = INDENT + currentStack.mMargin;
		}
	}
	
	public static void endProc(String token) {
		if (BuildConfig.DEBUG && mEnabled && !mException){	
			FuncStack currentStack = findCurrentStack();

		    Node item = currentStack.mFuncStack.pop();
		    
		    if (STRICT_MODE) {
			    if (token.compareTo(item.mProcName) != 0){
			    	//Not equal
			    	Log.e(TAG, "Exception: endProc and startPRoc do not occur in pair!");
			    	return;
			    }
		    }
		    
		    if (currentStack.mMargin.length() == INDENT.length()){
		    	currentStack.mMargin = "";
		    }
		    else {
		    	currentStack.mMargin = currentStack.mMargin.substring(INDENT.length(), currentStack.mMargin.length());
		    }
		    
			Log.i(TAG, currentStack.mMargin + "<" + token + ">" + " finish...<" + (System.currentTimeMillis()-item.mStartTime) + "ms>");

		}				
	}
	
	public static void procException(Exception e) {
		mException = true;
	}
	private static boolean mEnabled = true;
	private static boolean mException = false;
	
	private static final String TAG = "FuncTracer";
	private static final String INDENT = "  ";
	private static final boolean CONCISE_MODE = false; //Otherwise COMPLETE MODE
	private static final boolean STRICT_MODE = false; //Strict mode will be slow. If startFunc/endFunc do not come in pair, an Exception will be created
	
	private static ArrayList<FuncStack> mFuncStacks = new ArrayList<FuncStack>();
}
