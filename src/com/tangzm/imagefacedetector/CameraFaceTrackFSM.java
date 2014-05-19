package com.tangzm.imagefacedetector;

import java.util.Timer;
import java.util.TimerTask;

import android.os.Handler;
import android.os.Message;
import android.util.Log;


public class CameraFaceTrackFSM {
	
	public enum Event {
		FACE_DETECTED,
		FACE_NOT_DETECTED,
		CHECK_REQUIRED,
		SOFT_CHECK_REQUIRED,
		FIT_COMPLETE
	}

	private interface State {
		void enter();
		void exit();
		void process(Event evt);
	}
	
	interface CameraFaceView {
		void startProc();
		void stopProc();
	}
	
	private State[] mStates;
	private int mCurrentState;
	private CameraFaceView mViewer;
	
    public CameraFaceTrackFSM(CameraFaceView viewer) {
    	mViewer = viewer;
    	
    	mStates = new State[STATE_NUM];
    	
    	mStates[STATE_IDLE] = new State() {
			@Override
			public void enter() { mViewer.stopProc(); }

			@Override
			public void exit() {}

			@Override
			public void process(Event evt) {
				if (Event.CHECK_REQUIRED == evt) {
					changeState(STATE_HARD_CHECK);
				}
			}
    	};
    	
    	mStates[STATE_HARD_CHECK] = new State() {

			@Override
			public void enter() { mViewer.startProc(); }

			@Override
			public void exit() {}

			@Override
			public void process(Event evt) {
				if (Event.FACE_DETECTED == evt) {
					changeState(STATE_HARD_FIT);
				}
				else if (Event.FACE_NOT_DETECTED == evt) {
					changeState(STATE_IDLE);
				}
			}
    	};
    	
    	mStates[STATE_HARD_FIT] = new State() {

			@Override
			public void enter() { mViewer.startProc(); }

			@Override
			public void exit() {}

			@Override
			public void process(Event evt) {
				if (Event.FIT_COMPLETE == evt) {
					changeState(STATE_SOFT_FIT);
				}
			}
    	};
    	
    	mStates[STATE_SOFT_CHECK] = new State() {

			@Override
			public void enter() { mViewer.startProc(); }

			@Override
			public void exit() {}

			@Override
			public void process(Event evt) {
				if (Event.FACE_DETECTED == evt) {
					changeState(STATE_SOFT_FIT);
				}
				else if (Event.FACE_NOT_DETECTED == evt) {
					changeState(STATE_IDLE);
				}
			}
    	};
    	
    	mStates[STATE_SOFT_FIT] = new State() {
    		private Timer mSoftCheckTimer;
    		private Handler mHandler;
    		
			@Override
			public void enter() {	
				mViewer.startProc();
				mHandler = new Handler(new Handler.Callback() {
					@Override
					public boolean handleMessage(Message msg) {
						mStates[mCurrentState].process(Event.CHECK_REQUIRED);
						return true;
					}
				});
				
				mSoftCheckTimer  = new Timer("Soft Check");
				
				mSoftCheckTimer.schedule(new TimerTask(){
					public void run() {
						mHandler.obtainMessage().sendToTarget();
						mSoftCheckTimer = null;
					}
				}, SOFT_CHECK_PERIOD);				
			}

			@Override
			public void exit() {
				if (mSoftCheckTimer != null){
					mSoftCheckTimer.cancel();
				}
			}

			@Override
			public void process(Event evt) {
				if (Event.SOFT_CHECK_REQUIRED == evt) {
					changeState(STATE_SOFT_CHECK);
				}
				else if (Event.CHECK_REQUIRED == evt) {
					changeState(STATE_HARD_CHECK);
				}
				else if (Event.FIT_COMPLETE == evt) {
					//mViewer.startProc();
				}
			}
    	};
    	
    	
    	mCurrentState = STATE_IDLE;
    }

	synchronized private void changeState(int next) {
		// TODO Auto-generated method stub
		if (mCurrentState != next) {
			
			Log.i(TAG, test_state_name[mCurrentState] + " -> " + test_state_name[next]);
			mStates[mCurrentState].exit();
			mCurrentState = next;
			mStates[mCurrentState].enter();
		}
	}
	
	synchronized public void sendEvent(Event evt){
		Log.i(TAG, "Event="+evt.toString());
		mStates[mCurrentState].process(evt);
	}
	
	public int getCurrentState() {
		return mCurrentState;
	}
		
	//State list in the fsm
	public static final int STATE_IDLE = 0;
	public static final int STATE_HARD_CHECK=1;
	public static final int STATE_HARD_FIT = 2;
	public static final int STATE_SOFT_CHECK = 3;
	public static final int STATE_SOFT_FIT = 4;
	public static final int STATE_NUM = 5;
	
	private static final String TAG = "CameraFaceTrackFSM";
	
	private static final String[] test_state_name = new String[]{
		"idle", "hard_check", "hard_fit", "soft_check", "soft_fit"
	};
	
	
	private static final int SOFT_CHECK_PERIOD = 1500; //in ms
}
