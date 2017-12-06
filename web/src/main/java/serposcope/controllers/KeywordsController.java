package serposcope.controllers;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import com.serphacker.serposcope.db.base.BaseDB;
import com.serphacker.serposcope.db.google.GoogleDB;
import com.serphacker.serposcope.models.base.Group;
import com.serphacker.serposcope.models.base.User;
import com.serphacker.serposcope.models.google.GoogleSearch;
import com.serphacker.serposcope.models.google.GoogleTarget;
import com.serphacker.serposcope.scraper.google.GoogleCountryCode;
import com.serphacker.serposcope.task.TaskManager;
import conf.SerposcopeConf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import ninja.Context;
import ninja.Result;
import ninja.Results;
import ninja.Router;
import ninja.params.Param;
import ninja.session.Session;
import serposcope.helpers.GoogleHelper;
import serposcope.helpers.objects.ScanResult;

@Singleton
public class KeywordsController extends BaseController {
	public final static String PASSWORD_RESET_FILENAME = "password-reset.txt";

	private static final Logger LOG = LoggerFactory.getLogger(KeywordsController.class);

	protected final static Long SESSION_NORMAL_LIFETIME = 2 * 60 * 60 * 1000L;
	protected final static Long SESSION_REMEMBER_LIFETIME = 30 * 24 * 60 * 60 * 1000L;

	@Inject
	Router router;

	@Inject
	BaseDB baseDB;

	@Inject
	GoogleDB googleDB;

	@Inject
	SerposcopeConf conf;

	@Inject
	TaskManager taskManager;

	@Inject
	GoogleHelper gHelper;
	
	static final String[] countries = countries();

	static final String email = "abaltser@akolchin.com";
	static final String password = "qewret";

	static final String[] targetTypes = { "DOMAIN", "SUBDOMAIN", "REGEX" };

	static final String websiteCheckerGroup = "DM Website Checker";

	public Result api(Context context, @Param("website") String website, @Param("country") String country,
			@Param("keyword[]") String[] keywords) {
		Map<String, Object> m = new HashMap<String, Object>();
		if (!login(context, email, password, true)) {
			m.put("error", "this is an auth error");
			m.put("isError", true);
			return Results.json().render(m);
		}
		m.put("isError", false);

		Group group = gHelper.getOrCreateGroup(websiteCheckerGroup);

		String targetType = targetTypes[0];
		String name = website;
		String pattern = website;
		
		
		GoogleCountryCode countryCode = GoogleCountryCode.findByFancy(country);
		if(countryCode == null) {
			m.put("isError", true);
			return Results.json().render(m);
		}
		GoogleTarget target = gHelper.addWebsite(group, targetType, name, pattern);

		m.put("website", website);

		m.put("group", group.getName());
		if (target != null)
			m.put("target", target.getName());

		if (keywords != null) {
			Map<String, Object> kws = new HashMap<String, Object>();
			for (String keyword : keywords) {
				GoogleSearch search = gHelper.addSearch(group, keyword, countryCode.toString(), "", 0, "", "");
				if (search != null)
					kws.put(search.getKeyword(), "added");

			}
			m.put("search[]", kws);
		}

		ScanResult[] results = gHelper.startScan(keywords);
		m.put("scan_results", results);
		return Results.json().addHeader("Access-Control-Allow-Origin", "*").render(m);
	}
	
	public Result getCountries() {
		return Results.json().addHeader("Access-Control-Allow-Origin", "*").render(countries != null ? countries : "");
	}
	
	public static String[] countries() {
		GoogleCountryCode[] codes = GoogleCountryCode.values();
		String[] countries = new String[codes.length];
		for(int i = 0; i < codes.length; i++) {
			countries[i] = codes[i].fancyName;
		}
		return countries;
	}
	
	public Result viewChecker() {
		return Results.ok();
	}

	public boolean login(Context context, String email, String password, Boolean rememberMe) {
		User user = baseDB.user.findByEmail(email);
		if (user == null)
			return false;

		try {
			if (!user.verifyPassword(password))
				return false;
		} catch (Exception ex) {
			LOG.error("internal error on verifyPassword", ex);
			return false;
		}

		Session sess = context.getSession();
		sess.put("to", Long.toString(LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)));
		sess.put("id", Integer.toString(user.getId()));
		if (rememberMe != null && rememberMe) {
			sess.setExpiryTime(SESSION_REMEMBER_LIFETIME);
		} else {
			sess.setExpiryTime(SESSION_NORMAL_LIFETIME);
		}
		sess.getAuthenticityToken(); // generate token

		return true;
	}
}
