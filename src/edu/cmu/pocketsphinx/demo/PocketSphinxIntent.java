package edu.cmu.pocketsphinx.demo;

import java.util.Date;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.EditText;
import android.widget.TextView;
import edu.cmu.pocketsphinx.task.RecognizerTask;

public class PocketSphinxIntent extends Activity implements OnTouchListener, RecognitionListener {
	static {
		System.loadLibrary("pocketsphinx_jni");
	}

	public static final String EXTRA_RESULTS = "PockectSphinxExtraResults";
	/**
	 * Recognizer task, which runs in a worker thread.
	 */
	RecognizerTask rec;
	/**
	 * Thread in which the recognizer task runs.
	 */
	Thread rec_thread;
	/**
	 * Time at which current recognition started.
	 */
	Date start_date;
	/**
	 * Number of seconds of speech.
	 */
	float speech_dur;
	/**
	 * Are we listening?
	 */
	boolean listening;
	/**
	 * Progress dialog for final recognition.
	 */
	ProgressDialog rec_dialog;
	/**
	 * Performance counter view.
	 */
	TextView performance_text;
	/**
	 * Editable text view.
	 */
	EditText edit_text;

	Intent intent;

	/**
	 * Respond to touch events on the Speak button.
	 * 
	 * This allows the Speak button to function as a "push and hold" button, by
	 * triggering the start of recognition when it is first pushed, and the end
	 * of recognition when it is released.
	 * 
	 * @param v
	 *            View on which this event is called
	 * @param event
	 *            Event that was triggered.
	 */
	public boolean onTouch(View v, MotionEvent event) {
		switch (event.getAction()) {
		case MotionEvent.ACTION_DOWN:
			start_date = new Date();
			this.listening = true;
			this.rec.start();
			break;
		case MotionEvent.ACTION_UP:
			Date end_date = new Date();
			long nmsec = end_date.getTime() - start_date.getTime();
			this.speech_dur = (float) nmsec / 1000;
			if (this.listening) {
				// Log.d(getClass().getName(), "Showing Dialog");
				// // this.rec_dialog =
				// ProgressDialog.show(PocketSphinxIntent.this,
				// // "", "Recognizing speech...", true);
				// this.rec_dialog.setCancelable(false);
				this.listening = false;
			}
			this.rec.stop();
			intent.putExtra(EXTRA_RESULTS, this.edit_text.getText().toString());
			setResult(RESULT_OK, intent);
			finish();
			break;
		default:
			;
		}
		/* Let the button handle its own state */
		return false;
	}

	/** Called when the activity is first created. */
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		intent = this.getIntent();
		this.rec = new RecognizerTask();
		this.rec_thread = new Thread(this.rec);
		this.listening = false;
		// Button b = (Button) findViewById(R.id.Button01);
		// b.setOnTouchListener(this);
		this.performance_text = (TextView) findViewById(R.id.PerformanceText);
		this.edit_text = (EditText) findViewById(R.id.EditText01);
		this.rec.setRecognitionListener(this);
		this.rec_thread.start();
	}

	/** Called when partial results are generated. */
	public void onPartialResults(Bundle b) {
		final PocketSphinxIntent that = this;
		final String hyp = b.getString("hyp");
		that.edit_text.post(new Runnable() {
			public void run() {
				that.edit_text.setText(hyp);
			}
		});
	}

	/** Called with full results are generated. */
	public void onResults(Bundle b) {
		final String hyp = b.getString("hyp");
		final PocketSphinxIntent that = this;
		this.edit_text.post(new Runnable() {
			public void run() {
				that.edit_text.setText(hyp);
				// Date end_date = new Date();
				// long nmsec = end_date.getTime() - that.start_date.getTime();
				// float rec_dur = (float) nmsec / 1000;
				// that.performance_text
				// .setText(String.format("%.2f seconds %.2f xRT",
				// that.speech_dur, rec_dur / that.speech_dur));
				// Log.d(getClass().getName(), "Hiding Dialog");
				that.rec_dialog.dismiss();
			}
		});
	}

	public void onError(int err) {
		final PocketSphinxIntent that = this;
		that.edit_text.post(new Runnable() {
			public void run() {
				that.rec_dialog.dismiss();
			}
		});
		setResult(RESULT_CANCELED, intent);
		finish();
	}
}