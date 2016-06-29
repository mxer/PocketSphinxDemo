package edu.cmu.pocketsphinx.demo;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.Enumeration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import org.apache.commons.io.IOUtils;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.widget.EditText;
import android.widget.TextView;
import edu.cmu.pocketsphinx.listener.RecognitionListener;
import edu.cmu.pocketsphinx.task.RecognizerTask;
import edu.cmu.pocketsphinx.task.TimerTask;

public class PocketSphinxDemo extends Activity implements RecognitionListener {
	static {
		System.loadLibrary("pocketsphinx_jni");
	}

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

	/** Called when the activity is first created. */
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		this.performance_text = (TextView) findViewById(R.id.PerformanceText);
		this.edit_text = (EditText) findViewById(R.id.EditText01);

		initData();

		this.rec = new RecognizerTask();
		this.rec_thread = new Thread(this.rec);
		this.rec.setRecognitionListener(this);
		this.rec_thread.start();

		this.listening = true;
		this.rec.start();

		ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor();
		service.scheduleAtFixedRate(new TimerTask(this.rec), 100, 100, TimeUnit.SECONDS);
	}

	private void initData() {
		Context context = getApplicationContext();
		File sdcardDir = Environment.getExternalStorageDirectory();
		final String workbasePath = sdcardDir.getAbsoluteFile() + File.separator + "PocketSphinx";
		File rawFile = new File(workbasePath + File.separator + "raw.zip");
		if (!rawFile.exists()) {
			rawFile.getParentFile().mkdirs();
			InputStream dataIs = null;
			FileOutputStream outputStream = null;
			try {
				dataIs = context.getResources().openRawResource(R.raw.raw);
				outputStream = new FileOutputStream(rawFile);
				IOUtils.copy(dataIs, outputStream);

				unzip(rawFile, workbasePath);
			} catch (Exception e) {
				Log.e("PocketSphinxDemo", "Exception occurred when copy raw.zip ..", e);
			} finally {
				if (dataIs != null) {
					try {
						dataIs.close();
					} catch (IOException e) {
						Log.e("PocketSphinxDemo", "IOException occurred when copy raw.zip ..", e);
					}
				}
				if (outputStream != null) {
					try {
						outputStream.close();
					} catch (IOException e) {
						Log.e("PocketSphinxDemo", "IOException occurred when copy raw.zip ..", e);
					}
				}
			}
		}
	}

	/** Called when partial results are generated. */
	public void onPartialResults(Bundle b) {
		final PocketSphinxDemo that = this;
		final String hyp = b.getString("hyp");
		that.edit_text.post(new Runnable() {
			public void run() {
				that.edit_text.setText(hyp);
			}
		});
	}

	/** Called with full results are generated. */
	public void onResults(Bundle b) {
	}

	public void onError(int err) {
		final PocketSphinxDemo that = this;
		that.edit_text.post(new Runnable() {
			public void run() {
				that.rec_dialog.dismiss();
			}
		});
	}

	private void unzip(File file, String des) throws ZipException, IOException {
		ZipFile zipFile = new ZipFile(file);
		try {
			Enumeration<? extends ZipEntry> entries = zipFile.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				File entryDestination = new File(des, entry.getName());
				if (entry.isDirectory()) {
					entryDestination.mkdirs();
				} else {
					entryDestination.getParentFile().mkdirs();
					InputStream in = zipFile.getInputStream(entry);
					OutputStream out = new FileOutputStream(entryDestination);
					IOUtils.copy(in, out);
					IOUtils.closeQuietly(in);
					out.close();
				}
			}
		} finally {
			zipFile.close();
		}
	}

}