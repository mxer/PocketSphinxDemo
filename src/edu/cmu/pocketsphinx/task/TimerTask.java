package edu.cmu.pocketsphinx.task;

import edu.cmu.pocketsphinx.task.RecognizerTask;

public class TimerTask implements Runnable {

	private final RecognizerTask task;

	public TimerTask(RecognizerTask task) {
		this.task = task;
	}

	@Override
	public void run() {
		this.task.stop();

		try {
			Thread.sleep(200);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		this.task.start();

	}

}
