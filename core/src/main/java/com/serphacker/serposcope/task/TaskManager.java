/* 
 * Serposcope - SEO rank checker https://serposcope.serphacker.com/
 * 
 * Copyright (c) 2016 SERP Hacker
 * @author Pierre Nogues <support@serphacker.com>
 * @license https://opensource.org/licenses/MIT MIT License
 */
package com.serphacker.serposcope.task;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.serphacker.serposcope.db.base.BaseDB;
import com.serphacker.serposcope.di.TaskFactory;
import com.serphacker.serposcope.models.base.Group;
import com.serphacker.serposcope.models.base.Group.Module;
import com.serphacker.serposcope.models.base.Run;
import com.serphacker.serposcope.models.base.Run.Mode;
import com.serphacker.serposcope.models.google.GoogleSearch;
import com.serphacker.serposcope.models.google.GoogleTarget;
import com.serphacker.serposcope.task.google.GoogleTask;
import com.serphacker.serposcope.task.google.GoogleTaskResult;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class TaskManager {

	private static final Logger LOG = LoggerFactory.getLogger(TaskManager.class);

	@Inject
	TaskFactory googleTaskFactory;

	@Inject
	BaseDB db;

	final Object googleTaskLock = new Object();
	GoogleTask googleTask;

	public boolean isGoogleRunning() {
		synchronized (googleTaskLock) {
			if (googleTask != null && googleTask.isAlive()) {
				return true;
			}
			return false;
		}
	}

	public boolean startGoogleTask(Run run) {
		LOG.info("Starting task...");
		synchronized (googleTaskLock) {

			if (googleTask != null && googleTask.isAlive()) {
				return false;
			}

			googleTask = googleTaskFactory.create(run);
			googleTask.start();
			return true;
		}
	}
	
	public boolean startCustomGoogleTask(Run run, List<GoogleSearch> searches, List<GoogleTarget> targets) {
		LOG.info("Starting task...");
		synchronized (googleTaskLock) {

			if (googleTask != null && googleTask.isAlive()) {
				return false;
			}

			googleTask = googleTaskFactory.create(run);
			googleTask.setCustomSearches(searches);
			googleTask.setCustomTargets(targets);
			googleTask.setCustomRun(true);
			googleTask.start();
			return true;
		}
	}

	public GoogleTaskResult waitGoogleTask(Run run) {

		if (googleTask != null && googleTask.isAlive()) {
			LOG.info("waiting");
			try {
				googleTask.join();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				LOG.info("Error during waiting");
				e.printStackTrace();
				return null;
			}
		}

		LOG.info("Task is finished");
		GoogleTaskResult results = googleTask.getResults();
		return results;

	}

	public boolean abortGoogleTask(boolean interrupt) {
		synchronized (googleTaskLock) {
			if (googleTask == null || !googleTask.isAlive()) {
				return false;
			}

			if (db.run.updateStatusAborting(googleTask.getRun())) {
				googleTask.getRun().setStatus(Run.Status.ABORTING);
			}
			googleTask.abort();
			if (interrupt) {
				googleTask.interrupt();
			}
			return true;
		}
	}

	public void joinGoogleTask() throws InterruptedException {
		synchronized (googleTaskLock) {
			if (googleTask == null || !googleTask.isAlive()) {
				return;
			}

			googleTask.join();
		}
	}

	public Run getRunningGoogleTask() {
		synchronized (googleTaskLock) {
			if (googleTask == null || !googleTask.isAlive()) {
				return null;
			}

			return googleTask.getRun();
		}
	}

	public List<Run> listRunningTasks() {
		List<Run> tasks = new ArrayList<>();

		synchronized (googleTaskLock) {
			if (googleTask != null && googleTask.isAlive()) {
				tasks.add(googleTask.getRun());
			}
		}

		return tasks;
	}

}
