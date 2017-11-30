package serposcope.controllers;

import java.net.IDN;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.serphacker.serposcope.db.base.BaseDB;
import com.serphacker.serposcope.db.google.GoogleDB;
import com.serphacker.serposcope.models.base.Group;
import com.serphacker.serposcope.models.base.Run;
import com.serphacker.serposcope.models.base.User;
import com.serphacker.serposcope.models.base.Group.Module;
import com.serphacker.serposcope.models.google.GoogleSearch;
import com.serphacker.serposcope.models.google.GoogleTarget;
import com.serphacker.serposcope.models.google.GoogleTarget.PatternType;
import com.serphacker.serposcope.scraper.google.GoogleCountryCode;
import com.serphacker.serposcope.scraper.google.GoogleDevice;

import conf.SerposcopeConf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import ninja.Context;
import ninja.Result;
import ninja.Results;
import ninja.Router;
import ninja.params.Param;
import ninja.params.Params;
import ninja.session.FlashScope;
import ninja.session.Session;
import serposcope.controllers.google.GoogleGroupController;
import serposcope.helpers.GoogleHelper;
import serposcope.helpers.Validator;

@Singleton
public class TestController extends BaseController {
	public final static String PASSWORD_RESET_FILENAME = "password-reset.txt";

	private static final Logger LOG = LoggerFactory.getLogger(TestController.class);

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

	static final String email = "abaltser@akolchin.com";
	static final String password = "qewret";

	static final String[] targetTypes = { "DOMAIN", "SUBDOMAIN", "REGEX" };

	static final String websiteCheckerGroup = "DM Website Checker";

	public Result getTest() {
		return Results.json().render("key", "dfsgdf");

	}
	public Result doTest(Context context) {
		Map<String, Object> m = new HashMap<String, Object>();
		if (!login(context, email, password, true)) {
			m.put("error", "this is an auth error");
			m.put("isError", true);
			return Results.forbidden().render(m);
		}
		m.put("isError", false);
		m.put("test", "sdgdjfgsdkfh");

		GoogleHelper gHelper = new GoogleHelper(baseDB, googleDB);

		Group group = gHelper.getOrCreateGroup(context, websiteCheckerGroup);

		String targetType = targetTypes[0];
		String name = "www.digitalmonopoly.com.au";
		String pattern = "www.digitalmonopoly.com.au";
		GoogleTarget target = gHelper.addWebsite(context, group, targetType, name, pattern);
		GoogleSearch search = gHelper.addSearch(context, group, "testSearch", GoogleCountryCode.__.toString(), "", 0, "", "");
		return Results.ok().render(m);
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
