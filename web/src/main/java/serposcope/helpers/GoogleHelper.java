package serposcope.helpers;

import java.net.IDN;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.serphacker.serposcope.db.base.BaseDB;
import com.serphacker.serposcope.db.google.GoogleDB;
import com.serphacker.serposcope.models.base.Group;
import com.serphacker.serposcope.models.base.Run;
import com.serphacker.serposcope.models.base.User;
import com.serphacker.serposcope.models.base.Group.Module;
import com.serphacker.serposcope.models.google.GoogleBest;
import com.serphacker.serposcope.models.google.GoogleRank;
import com.serphacker.serposcope.models.google.GoogleSearch;
import com.serphacker.serposcope.models.google.GoogleSerp;
import com.serphacker.serposcope.models.google.GoogleTarget;
import com.serphacker.serposcope.models.google.GoogleTargetSummary;
import com.serphacker.serposcope.models.google.GoogleTarget.PatternType;
import com.serphacker.serposcope.scraper.google.GoogleCountryCode;
import com.serphacker.serposcope.scraper.google.GoogleDevice;

import ninja.Context;
import ninja.session.FlashScope;

public class GoogleHelper {

	private static final Logger LOG = LoggerFactory.getLogger(GoogleHelper.class);

	protected BaseDB baseDB;
	protected GoogleDB googleDB;

	public GoogleHelper(BaseDB baseDB, GoogleDB googleDB) {
		this.baseDB = baseDB;
		this.googleDB = googleDB;
	}
	/*
    public void rescan(Integer specificRunId, Collection<GoogleTarget> targets, Collection<GoogleSearch> searches,  boolean updateSummary) {
        LOG.debug("SERP rescan (bulk) : starting");
        long _start = System.currentTimeMillis();
        Map<Integer, Integer> searchCountByGroup = searchDB.countByGroup();
        Run specPrevRun = null;
        Map<Integer, GoogleTargetSummary> specPrevRunSummaryByTarget = new HashMap<>();
        
        if(specificRunId != null){
            specPrevRun = runDB.findPrevious(specificRunId);
            if(specPrevRun != null){
                specPrevRunSummaryByTarget = targetSummaryDB.list(specPrevRun.getId()).stream()
                    .collect(Collectors.toMap(GoogleTargetSummary::getTargetId, Function.identity()));
            }
        }        
        
        List<GoogleRank> ranks = new ArrayList<>();
        for (GoogleTarget target : targets) {
            
            Map<Integer, GoogleTargetSummary> summaryByRunId = new HashMap<>();
            GoogleTargetSummary specificPreviousSummary = specPrevRunSummaryByTarget.get(target.getId());
            if(specificPreviousSummary != null){
                summaryByRunId.put(specPrevRun.getId(), specificPreviousSummary);
            }
            
            for (GoogleSearch search : searches) {
                final MutableInt previousRunId = new MutableInt(0);
                final MutableInt previousRank = new MutableInt(GoogleRank.UNRANKED);
                GoogleBest searchBest = new GoogleBest(target.getGroupId(), target.getId(), search.getId(), GoogleRank.UNRANKED, null, null);
                
                if(specPrevRun != null){
                    previousRunId.setValue(specPrevRun.getId());
                    previousRank.setValue(rankDB.get(specPrevRun.getId(), target.getGroupId(), target.getId(), search.getId()));
                    GoogleBest specificBest = rankDB.getBest(target.getGroupId(), target.getId(), search.getId());
                    if(specificBest != null){
                        searchBest = specificBest;
                    }
                }
                final GoogleBest best = searchBest;

                serpDB.stream(specificRunId, specificRunId, search.getId(), (GoogleSerp res) -> {
                    
                    int rank = GoogleRank.UNRANKED;
                    String rankedUrl = null;
                    for (int i = 0; i < res.getEntries().size(); i++) {
                        if (target.match(res.getEntries().get(i).getUrl())) {
                            rankedUrl = res.getEntries().get(i).getUrl();
                            rank = i + 1;
                            break;
                        }
                    }

                    // only update last run
                    GoogleRank gRank = new GoogleRank(res.getRunId(), target.getGroupId(), target.getId(), search.getId(),
                        rank, previousRank.shortValue(), rankedUrl);
                    ranks.add(gRank);
                    if(ranks.size() > 2000){
                        rankDB.insert(ranks);
                        ranks.clear();
                    }
                    
                    if(updateSummary){
                        GoogleTargetSummary summary = summaryByRunId.get(res.getRunId());
                        if (summary == null) {
                            summaryByRunId.put(res.getRunId(), summary = new GoogleTargetSummary(target.getGroupId(),
                                target.getId(), res.getRunId(), 0));
                        }
                        summary.addRankCandidat(gRank);
                    }                    

                    if (rank != GoogleRank.UNRANKED && rank <= best.getRank()) {
                        best.setRank((short) rank);
                        best.setUrl(rankedUrl);
                        best.setRunDay(res.getRunDay());
                    }

                    previousRunId.setValue(res.getRunId());
                    previousRank.setValue(rank);
                });
                
                if (best.getRank() != GoogleRank.UNRANKED) {
                    rankDB.insertBest(best);
                }
            }
            
            // fill previous summary score
            if(updateSummary){
                TreeMap<Integer, GoogleTargetSummary> summaries = new TreeMap<>(summaryByRunId);
                
                GoogleTargetSummary previousSummary = null;
                for (Map.Entry<Integer, GoogleTargetSummary> entry : summaries.entrySet()) {
                    GoogleTargetSummary summary = entry.getValue();
                    summary.computeScoreBP(searchCountByGroup.getOrDefault(summary.getGroupId(), 0));
                    if (previousSummary != null) {
                        summary.setPreviousScoreBP(previousSummary.getScoreBP());
                    }
                    previousSummary = summary;
                }
                
                if(specPrevRun != null){
                    summaries.remove(specPrevRun.getId());
                }
                
                if(!summaries.isEmpty()){
                    targetSummaryDB.insert(summaries.values());
                }
            }
        }
        
        if(!ranks.isEmpty()){
            rankDB.insert(ranks);
            ranks.clear();
        }
        
        LOG.debug("SERP rescan : done, duration = {}", DurationFormatUtils.formatDurationHMS(System.currentTimeMillis()-_start));
    } 
*/
	public GoogleSearch addSearch(Context context, Group group, String keyword, String country, String datacenter, Integer device,
			String local, String custom) {
		FlashScope flash = context.getFlashScope();

		if (keyword == null || country == null || datacenter == null || device == null || local == null
				|| custom == null) {
			LOG.info("Searches adding error #1");
			return null;
		}

		Set<GoogleSearch> searches = new HashSet<>();

		GoogleSearch search = new GoogleSearch();

		if (keyword.isEmpty()) {
			LOG.info("Searches adding error #2");
			return null;
		}
		search.setKeyword(keyword);

		GoogleCountryCode countryCode = null;
		if (country != null) {
			try {
				countryCode = GoogleCountryCode.valueOf(country.toUpperCase());
			} catch (Exception ex) {
			}
		}
		if (countryCode == null) {
			LOG.info("Searches adding error #3");
			return null;
		}
		search.setCountry(countryCode);

		if (!datacenter.isEmpty()) {
			if (!Validator.isIPv4(datacenter)) {
				LOG.info("Searches adding error #4");
				return null;
			}
			search.setDatacenter(datacenter);
		}

		if (device != null && device >= 0 && device < GoogleDevice.values().length) {
			search.setDevice(GoogleDevice.values()[device]);
		} else {
			search.setDevice(GoogleDevice.DESKTOP);
		}

		if (!Validator.isEmpty(local))
			search.setLocal(local);
		if (!Validator.isEmpty(custom))
			search.setCustomParameters(custom);

		searches.add(search);

		googleDB.search.insert(searches, group.getId());
		List<GoogleSearch> knownSearches = getSearches();

		List<GoogleTarget> targets = getTargets();

		googleDB.serpRescan.rescan(null, targets, knownSearches, true);

		return search;
	}

	public GoogleTarget addWebsite(Context context, Group group, String targetType, String name, String pattern) {
		FlashScope flash = context.getFlashScope();

		if (targetType == null || name == null || pattern == null) {
			LOG.info("Target insert error #0");
			return null;
		}

		Set<GoogleTarget> targets = new HashSet<>();

		if (name != null)
			name = name.replaceAll("(^\\s+)|(\\s+$)", "");
		if (pattern != null)
			pattern = pattern.replaceAll("(^\\s+)|(\\s+$)", "");

		if (Validator.isEmpty(name)) {
			LOG.info("Target insert error #1");
			return null;
		}

		PatternType type = null;
		try {
			type = PatternType.valueOf(targetType);
		} catch (Exception ex) {
			LOG.info("Target insert error #2");
			return null;
		}

		if (PatternType.DOMAIN.equals(type) || PatternType.SUBDOMAIN.equals(type)) {
			try {
				pattern = IDN.toASCII(pattern);
			} catch (Exception ex) {
				pattern = null;
			}
		}

		if (!GoogleTarget.isValidPattern(type, pattern)) {
			LOG.info("Target insert error #3");
			return null;
		}

		GoogleTarget target = new GoogleTarget(group.getId(), name, type, pattern);
		targets.add(target);

		if (googleDB.target.insert(targets) < 1) {
			LOG.info("Target insert error #4");
			return null;
		}
		List<GoogleSearch> searches = getSearches();
		LOG.debug("GOOGLE SEARCHES LENGTH: " + searches);
		googleDB.serpRescan.rescan(null, targets, searches, true);

		// Here getting GoogleTarget

		return target;
	}

	public Group getOrCreateGroup(Context context, String name) {
		Module module = null;

		List<Group> groups = baseDB.group.list();
		for (Group group : groups) {
			if (group.getName().toLowerCase().equals(name.toLowerCase())) {
				return group;
			}
		}

		if (name == null || name.isEmpty()) {
			LOG.info("Group name is empty");
			return null;
		}

		try {
			module = Module.values()[0];
		} catch (Exception ex) {
			LOG.info("Exception during creation group");
			return null;
		}

		Group group = new Group(module, name);
		baseDB.group.insert(group);

		return group;
	}

	protected List<GoogleSearch> getSearches() {
		return googleDB.search.list();
	}

	protected List<GoogleTarget> getTargets() {
		return googleDB.target.list();
	}
}
