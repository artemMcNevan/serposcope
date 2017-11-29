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

	static final String websiteCheckerGroup = "DM Website Checker";

	public Result doTest(Context context) {
		Map<String, Object> m = new HashMap<String, Object>();
		if (!login(context, email, password, true)) {
			m.put("error", "this is an auth error");
			m.put("isError", true);
			return Results.forbidden().render(m);
		}
		m.put("isError", false);
		m.put("test", "sdgdjfgsdkfh");

		Group group = getOrCreateGroup(context, websiteCheckerGroup);

		String targetType = "";
		String[] names = { "", "" };
		String[] patterns = { "", "" };
		addWebsite(context, group, targetType, names, patterns);
		return Results.ok().render(m);
	}

	public GoogleTarget addWebsite(Context context, Group group, String targetType, String[] names, String[] patterns) {
		FlashScope flash = context.getFlashScope();

		if (targetType == null || names == null || names.length == 0 || patterns == null || patterns.length == 0
				|| names.length != patterns.length) {
			LOG.info("");
			return null;
		}

		Set<GoogleTarget> targets = new HashSet<>();
		for (int i = 0; i < names.length; i++) {
			String name = names[i];
			String pattern = patterns[i];

			if (name != null) {
				name = name.replaceAll("(^\\s+)|(\\s+$)", "");
			}

			if (pattern != null) {
				pattern = pattern.replaceAll("(^\\s+)|(\\s+$)", "");
			}

			if (Validator.isEmpty(name)) {
				LOG.info("");
				return null;
			}

			PatternType type = null;
			try {
				type = PatternType.valueOf(targetType);
			} catch (Exception ex) {
				LOG.info("");
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
				LOG.info("");
				return null;
			}

			targets.add(new GoogleTarget(group.getId(), name, type, pattern));
		}

		if (googleDB.target.insert(targets) < 1) {
			LOG.info("");
			return null;
		}
		googleDB.serpRescan.rescan(null, targets, getSearches(context), true);

		//Here getting GoogleTarget

		return null;
	}

	protected List<GoogleSearch> getSearches(Context context) {
		return context.getAttribute("searches", List.class);
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
