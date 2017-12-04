package serposcope.helpers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.serphacker.serposcope.db.base.BaseDB;
import com.serphacker.serposcope.db.google.GoogleDB;
import com.serphacker.serposcope.di.CaptchaSolverFactory;
import com.serphacker.serposcope.di.GoogleScraperFactory;
import com.serphacker.serposcope.di.ScrapClientFactory;
import com.serphacker.serposcope.models.base.Proxy;
import com.serphacker.serposcope.models.base.Run;
import com.serphacker.serposcope.models.google.GoogleBest;
import com.serphacker.serposcope.models.google.GoogleRank;
import com.serphacker.serposcope.models.google.GoogleSearch;
import com.serphacker.serposcope.models.google.GoogleSerp;
import com.serphacker.serposcope.models.google.GoogleSerpEntry;
import com.serphacker.serposcope.models.google.GoogleSettings;
import com.serphacker.serposcope.models.google.GoogleTarget;
import com.serphacker.serposcope.models.google.GoogleTargetSummary;
import com.serphacker.serposcope.scraper.captcha.solver.CaptchaSolver;
import com.serphacker.serposcope.scraper.google.GoogleScrapResult;
import com.serphacker.serposcope.scraper.google.scraper.GoogleScraper;
import com.serphacker.serposcope.scraper.http.ScrapClient;
import com.serphacker.serposcope.scraper.http.proxy.DirectNoProxy;
import com.serphacker.serposcope.scraper.http.proxy.ProxyRotator;
import com.serphacker.serposcope.scraper.http.proxy.ScrapProxy;
import com.serphacker.serposcope.task.google.GoogleTask;
import com.serphacker.serposcope.task.google.GoogleTaskRunnable;

public class GoogleScanHelper {
	protected static final Logger LOG = LoggerFactory.getLogger(GoogleScanHelper.class);

	protected GoogleScraperFactory googleScraperFactory;
	protected CaptchaSolverFactory captchaSolverFactory;
	protected ScrapClientFactory scrapClientFactory;

	protected GoogleDB googleDB;
	protected BaseDB baseDB;
	protected ProxyRotator rotator;

	protected final Map<Short, Integer> previousRunsByDay = new ConcurrentHashMap<>();
	protected final Map<Integer, List<GoogleTarget>> targetsByGroup = new ConcurrentHashMap<>();
	protected final Map<Integer, GoogleTargetSummary> summariesByTarget = new ConcurrentHashMap<>();

	protected LinkedBlockingQueue<GoogleSearch> searches;
	protected GoogleSettings googleOptions;
	protected final AtomicInteger searchDone = new AtomicInteger();
	protected final AtomicInteger captchaCount = new AtomicInteger();

	protected volatile int totalSearch;
	protected volatile boolean interrupted;

	protected CaptchaSolver solver;
	protected String httpUserAgent;
	protected int httpTimeoutMS;
	protected boolean updateRun;
	protected boolean shuffle = true;

	public GoogleScanHelper(GoogleScraperFactory googleScraperFactory, CaptchaSolverFactory captchaSolverFactory,
			ScrapClientFactory scrapClientFactory, GoogleDB googleDB, BaseDB baseDB) {

		this.googleScraperFactory = googleScraperFactory;
		this.captchaSolverFactory = captchaSolverFactory;
		this.scrapClientFactory = scrapClientFactory;
		this.googleDB = googleDB;
		this.baseDB = baseDB;

		httpUserAgent = ScrapClient.DEFAULT_USER_AGENT;
		httpTimeoutMS = ScrapClient.DEFAULT_TIMEOUT_MS;
	}

	/*
	public Run.Status scan() {
		solver = initializeCaptchaSolver();
		googleOptions = googleDB.options.get();

		initializeSearches();
		initializePreviousRuns();
		initializeTargets();

		int nThread = googleOptions.getMaxThreads();
		List<ScrapProxy> proxies = baseDB.proxy.list().stream().map(Proxy::toScrapProxy).collect(Collectors.toList());

		if (proxies.isEmpty()) {
			LOG.warn("no proxy configured, using direct connection");
			proxies.add(new DirectNoProxy());
		}

		if (proxies.size() < nThread) {
			LOG.info("less proxy ({}) than max thread ({}), setting thread number to {}",
					new Object[] { proxies.size(), nThread, nThread });
			nThread = proxies.size();
		}

		rotator = new ProxyRotator(proxies);
		totalSearch = searches.size();

		finalizeSummaries();

		if (solver != null) {
			try {
				solver.close();
			} catch (IOException ex) {
			}
		}

		LOG.warn("{} proxies failed during the task", proxies.size() - rotator.list().size());

		int remainingSearch = totalSearch - searchDone.get();
		

		return Run.Status.DONE_SUCCESS;
	}



	protected boolean shouldStop() {
		if (searchDone.get() == totalSearch) {
			return true;
		}

		if (interrupted) {
			return true;
		}

		return false;
	}

	protected void incCaptchaCount(int captchas) {
		//run.setCaptchas(captchaCount.addAndGet(captchas));
		//baseDB.run.updateCaptchas(run);
	}

	protected void onSearchDone(GoogleSearch search, GoogleScrapResult res) {
		insertSearchResult(search, res);
		incSearchDone();
	}

	protected void incSearchDone() {
		//run.setProgress((int) (((float) searchDone.incrementAndGet() / (float) totalSearch) * 100f));
		//baseDB.run.updateProgress(run);
	}

	protected void insertSearchResult(GoogleSearch search, GoogleScrapResult res) {
		Map<Short, GoogleSerp> history = getHistory(search);

		GoogleSerp serp = new GoogleSerp(run.getId(), search.getId(), run.getStarted());
		for (String url : res.urls) {
			GoogleSerpEntry entry = new GoogleSerpEntry(url);
			entry.fillPreviousPosition(history);
			serp.addEntry(entry);
		}
		googleDB.serp.insert(serp);

		List<Integer> groups = googleDB.search.listGroups(search);
		for (Integer group : groups) {
			List<GoogleTarget> targets = targetsByGroup.get(group);
			if (targets == null) {
				continue;
			}
			for (GoogleTarget target : targets) {
				int best = googleDB.rank.getBest(group, target.getId(), search.getId()).getRank();
				int rank = GoogleRank.UNRANKED;
				String rankedUrl = null;
				for (int i = 0; i < res.urls.size(); i++) {
					if (target.match(res.urls.get(i))) {
						rankedUrl = res.urls.get(i);
						rank = i + 1;
						break;
					}
				}

				int previousRank = GoogleRank.UNRANKED;
				if (previousRun != null) {
					previousRank = googleDB.rank.get(previousRun.getId(), group, target.getId(), search.getId());
				}

				GoogleRank gRank = new GoogleRank(run.getId(), group, target.getId(), search.getId(), rank,
						previousRank, rankedUrl);
				googleDB.rank.insert(gRank);

				GoogleTargetSummary summary = summariesByTarget.get(target.getId());
				summary.addRankCandidat(gRank);

				if (rank != GoogleRank.UNRANKED && rank <= best) {
					googleDB.rank.insertBest(
							new GoogleBest(group, target.getId(), search.getId(), rank, run.getStarted(), rankedUrl));
				}
			}
		}
	}
	*/

	protected void initializeSearches() {
		List<GoogleSearch> searchList = googleDB.search.list();
		
		if (shuffle) {
			Collections.shuffle(searchList);
		}
		searches = new LinkedBlockingQueue<>(searchList);
		LOG.info("{} searches to do", searches.size());
	}

	protected void initializeTargets() {
		Map<Integer, Integer> previousScorePercent = new HashMap<>();


		List<GoogleTarget> targets = googleDB.target.list();
		for (GoogleTarget target : targets) {
			targetsByGroup.putIfAbsent(target.getGroupId(), new ArrayList<>());
			targetsByGroup.get(target.getGroupId()).add(target);
			//summariesByTarget.put(target.getId(), new GoogleTargetSummary(target.getGroupId(), target.getId(),
			//		run.getId(), previousScorePercent.getOrDefault(target.getId(), 0)));
		}

		/*
		if (updateRun) {
			List<GoogleTargetSummary> summaries = googleDB.targetSummary.list(run.getId());
			for (GoogleTargetSummary summary : summaries) {
				summariesByTarget.put(summary.getTargetId(), summary);
			}
		}
		*/
	}

	protected void finalizeSummaries() {
		Map<Integer, Integer> searchCountByGroup = googleDB.search.countByGroup();
		for (GoogleTargetSummary summary : summariesByTarget.values()) {
			summary.computeScoreBP(searchCountByGroup.getOrDefault(summary.getGroupId(), 0));
		}
		googleDB.targetSummary.insert(summariesByTarget.values());
	}

	protected GoogleScraper genScraper() {
		return googleScraperFactory.get(scrapClientFactory.get(httpUserAgent, httpTimeoutMS), solver);
	}

	protected final CaptchaSolver initializeCaptchaSolver() {
		solver = captchaSolverFactory.get(baseDB.config.getConfig());
		if (solver != null) {
			if (!solver.init()) {
				LOG.info("failed to init captcha solver {}", solver.getFriendlyName());
				return null;
			}
			return solver;
		} else {
			LOG.info("no captcha service configured");
			return null;
		}

	}

	int getSearchDone() {
		return searchDone != null ? searchDone.get() : 0;
	}

}
