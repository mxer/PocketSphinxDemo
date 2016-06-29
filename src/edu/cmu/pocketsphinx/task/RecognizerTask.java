package edu.cmu.pocketsphinx.task;

import java.io.File;
import java.util.concurrent.LinkedBlockingQueue;

import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import edu.cmu.pocketsphinx.Config;
import edu.cmu.pocketsphinx.Decoder;
import edu.cmu.pocketsphinx.Hypothesis;
import edu.cmu.pocketsphinx.pocketsphinx;
import edu.cmu.pocketsphinx.listener.RecognitionListener;

/**
 * Speech recognition task, which runs in a worker thread.
 * 
 * This class implements speech recognition for this demo application. It takes
 * the form of a long-running task which accepts requests to start and stop
 * listening, and emits recognition results to a listener.
 * 
 * @author David Huggins-Daines <dhuggins@cs.cmu.edu>
 */
public class RecognizerTask implements Runnable {

	/**
	 * PocketSphinx native decoder object.
	 */
	Decoder ps;
	/**
	 * Audio recording task.
	 */
	AudioTask audio;
	/**
	 * Thread associated with recording task.
	 */
	Thread audio_thread;
	/**
	 * Queue of audio buffers.
	 */
	LinkedBlockingQueue<short[]> audioq;
	/**
	 * Listener for recognition results.
	 */
	RecognitionListener rl;
	/**
	 * Whether to report partial results.
	 */
	boolean use_partials;

	/* Main loop for this thread. */
	volatile boolean done = false;
	/* State of the main loop. */
	volatile State state = State.IDLE;

	/**
	 * State of the main loop.
	 */
	public enum State {
		IDLE, LISTENING
	};

	/**
	 * Events for main loop.
	 */
	public enum Event {
		NONE, START, STOP, SHUTDOWN
	};

	/**
	 * Current event.
	 */
	Event mailbox;

	public RecognitionListener getRecognitionListener() {
		return rl;
	}

	public void setRecognitionListener(RecognitionListener rl) {
		this.rl = rl;
	}

	public void setUsePartials(boolean use_partials) {
		this.use_partials = use_partials;
	}

	public boolean getUsePartials() {
		return this.use_partials;
	}

	public RecognizerTask() {
		File sdcardDir = Environment.getExternalStorageDirectory();
		final String workbasePath = sdcardDir.getAbsoluteFile() + File.separator + "PocketSphinx";

		pocketsphinx.setLogfile(workbasePath + File.separator + "pocketsphinx.log");
		Config c = new Config();
		/*
		 * In 2.2 and above we can use getExternalFilesDir() or whatever it's
		 * called
		 */
		c.setString("-hmm", workbasePath + File.separator + "hmm/tdt_sc_8k");
		c.setString("-dict", workbasePath + File.separator + "lm/test.dic");
		c.setString("-lm", workbasePath + File.separator + "lm/test.lm");
		c.setFloat("-samprate", 8000.0);
		c.setInt("-maxhmmpf", 2000);
		c.setInt("-maxwpf", 10);
		c.setInt("-pl_window", 2);
		c.setBoolean("-backtrace", true);
		c.setBoolean("-bestpath", false);
		this.ps = new Decoder(c);
		this.audio = null;
		this.audioq = new LinkedBlockingQueue<short[]>();
		this.use_partials = false;
		this.mailbox = Event.NONE;
	}

	public void run() {

		/* Previous partial hypothesis. */
		String partial_hyp = null;

		while (!done) {
			/* Read the mail. */
			Event todo = Event.NONE;
			synchronized (this.mailbox) {
				todo = this.mailbox;
				/* If we're idle then wait for something to happen. */
				if (state == State.IDLE && todo == Event.NONE) {
					try {
						Log.d(getClass().getName(), "waiting");
						this.mailbox.wait();
						todo = this.mailbox;
						Log.d(getClass().getName(), "got" + todo);
					} catch (InterruptedException e) {
						/* Quit main loop. */
						Log.e(getClass().getName(), "Interrupted waiting for mailbox, shutting down");
						todo = Event.SHUTDOWN;
					}
				}
				/*
				 * Reset the mailbox before releasing, to avoid race condition.
				 */
				this.mailbox = Event.NONE;
			}
			/* Do whatever the mail says to do. */
			switch (todo) {
			case NONE:
				if (state == State.IDLE)
					Log.e(getClass().getName(), "Received NONE in mailbox when IDLE, threading error?");
				break;
			case START:
				if (state == State.IDLE) {
					Log.d(getClass().getName(), "START");
					this.audio = new AudioTask(this.audioq, 1024);
					this.audio_thread = new Thread(this.audio);
					this.ps.startUtt();
					this.audio_thread.start();
					state = State.LISTENING;
				} else
					Log.e(getClass().getName(), "Received START in mailbox when LISTENING");
				break;
			case STOP:
				if (state == State.IDLE)
					Log.e(getClass().getName(), "Received STOP in mailbox when IDLE");
				else {
					Log.d(getClass().getName(), "STOP");
					assert this.audio != null;
					this.audio.stop();
					try {
						this.audio_thread.join();
					} catch (InterruptedException e) {
						Log.e(getClass().getName(), "Interrupted waiting for audio thread, shutting down");
						done = true;
					}
					/* Drain the audio queue. */
					short[] buf;
					while ((buf = this.audioq.poll()) != null) {
						Log.d(getClass().getName(), "Reading " + buf.length + " samples from queue");
						this.ps.processRaw(buf, buf.length, false, false);
					}
					this.ps.endUtt();
					this.audio = null;
					this.audio_thread = null;

					Hypothesis hyp = this.ps.getHyp();
					if (this.rl != null) {
						if (hyp == null) {
							Log.d(getClass().getName(), "Recognition failure");
							this.rl.onError(-1);
						} else {
							Bundle b = new Bundle();
							Log.i(getClass().getName(), "zs log Final hypothesis: " + hyp.getHypstr());
							Log.i(getClass().getName(), ps.getUttid());
							b.putString("hyp", hyp.getHypstr());
							// this.rl.onResults(b);
						}
					}
					state = State.IDLE;
				}
				break;
			case SHUTDOWN:
				Log.d(getClass().getName(), "SHUTDOWN");
				if (this.audio != null) {
					this.audio.stop();
					assert this.audio_thread != null;
					try {
						this.audio_thread.join();
					} catch (InterruptedException e) {
						/* We don't care! */
					}
				}
				this.ps.endUtt();
				this.audio = null;
				this.audio_thread = null;
				state = State.IDLE;
				done = true;
				break;
			}
			/*
			 * Do whatever's appropriate for the current state. Actually this
			 * just means processing audio if possible.
			 */
			if (state == State.LISTENING) {
				assert this.audio != null;
				try {
					short[] buf = this.audioq.take();
					Log.d(getClass().getName(), "Reading " + buf.length + " samples from queue");
					this.ps.processRaw(buf, buf.length, false, false);
					Hypothesis hyp = this.ps.getHyp();
					if (hyp != null) {
						String hypstr = hyp.getHypstr();
						if (hypstr != partial_hyp) {
							Log.d(getClass().getName(), "Hypothesis: " + hyp.getHypstr() + " Uttid: " + hyp.getUttid()
									+ " Best_score: " + hyp.getBest_score());
							if (this.rl != null && hyp != null) {
								Bundle b = new Bundle();
								b.putString("hyp", hyp.getHypstr());
								this.rl.onPartialResults(b);
							}
						}
						partial_hyp = hypstr;
					}
				} catch (InterruptedException e) {
					Log.d(getClass().getName(), "Interrupted in audioq.take");
				}
			}
		}
	}

	public void start() {
		Log.d(getClass().getName(), "signalling START");
		synchronized (this.mailbox) {
			this.mailbox.notifyAll();
			Log.d(getClass().getName(), "signalled START");
			this.mailbox = Event.START;
		}
	}

	public void stop() {
		Log.d(getClass().getName(), "signalling STOP");
		synchronized (this.mailbox) {
			this.mailbox.notifyAll();
			Log.d(getClass().getName(), "signalled STOP");
			this.mailbox = Event.STOP;
		}
	}

	public void shutdown() {
		Log.d(getClass().getName(), "signalling SHUTDOWN");
		synchronized (this.mailbox) {
			this.mailbox.notifyAll();
			Log.d(getClass().getName(), "signalled SHUTDOWN");
			this.mailbox = Event.SHUTDOWN;
		}
	}
}