package serposcope.helpers;

import static com.serphacker.serposcope.models.base.Group.Module.GOOGLE;

import java.net.IDN;
import java.sql.Connection;
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
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mysema.commons.lang.CloseableIterator;
import com.querydsl.core.Tuple;
import com.querydsl.sql.SQLQuery;
import com.serphacker.serposcope.db.base.BaseDB;
import com.serphacker.serposcope.db.google.GoogleDB;
import com.serphacker.serposcope.db.google.GoogleRankDB;
import com.serphacker.serposcope.db.google.GoogleSearchDB;
import com.serphacker.serposcope.db.google.GoogleSerpDB;
import com.serphacker.serposcope.db.google.GoogleTargetSummaryDB;
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
import ninja.Results;
import ninja.session.FlashScope;
import serposcope.controllers.HomeController;

public class GoogleHelper {

	private static final Logger LOG = LoggerFactory.getLogger(GoogleHelper.class);

	protected BaseDB baseDB;
	protected GoogleDB googleDB;
	protected GoogleSearchDB searchDB;
	protected GoogleRankDB rankDB;
	protected GoogleSerpDB serpDB;
	protected GoogleTargetSummaryDB targetSummaryDB;

	public GoogleHelper(BaseDB baseDB, GoogleDB googleDB) {
		this.baseDB = baseDB;
		this.googleDB = googleDB;
	}

	public void setSearchDB(GoogleSearchDB searchDB) {
		this.searchDB = searchDB;
	}

	public void setRankDB(GoogleRankDB rankDB) {
		this.rankDB = rankDB;
	}
	
	public void setTargetSummaryDB(GoogleTargetSummaryDB targetSummaryDB) {
		this.targetSummaryDB = targetSummaryDB;
	}

	public void setSerpDB(GoogleSerpDB serpDB) {
		this.serpDB = serpDB;
	}

	public void startScan() {
	
	    
	     
	}

	public GoogleSearch addSearch(Group group, String keyword, String country, String datacenter, Integer device,
			String local, String custom) {
		if (keyword == null || country == null || datacenter == null || device == null || local == null
				|| custom == null) {
			LOG.info("Searches adding error #1");
			return null;
		}

		List<GoogleSearch> foundSearches = getSearches();
		for (GoogleSearch sh : foundSearches) {
			if (sh.getKeyword().toLowerCase().equals(keyword.toLowerCase())) {
				LOG.info("Can't create search with the same keyword");
				return null;
			}
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

	public GoogleTarget addWebsite(Group group, String targetType, String name, String pattern) {
		if (targetType == null || name == null || pattern == null) {
			LOG.info("Target insert error #0");
			return null;
		}
		List<GoogleTarget> foundTargets = this.getTargets();

		for (GoogleTarget tg : foundTargets) {
			if (tg.getName().toLowerCase().equals(name.toLowerCase())) {
				LOG.info("Can't create target with the same name");
				return null;
			}
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

	public Group getOrCreateGroup(String name) {
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
