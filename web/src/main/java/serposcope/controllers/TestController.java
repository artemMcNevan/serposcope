package serposcope.controllers;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import com.serphacker.serposcope.db.base.BaseDB;
import com.serphacker.serposcope.models.base.User;

import conf.SerposcopeConf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import ninja.Context;
import ninja.Result;
import ninja.Results;
import ninja.Router;
import ninja.session.Session;

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
    SerposcopeConf conf;

	public Result doTest(Context context) {

		return Results.ok().render("test", "testgadfghdfgh");
	}

	public void login(Context context, String email,String password, Boolean rememberMe) {
		

		
		User user = baseDB.user.findByEmail(email);
		if (user == null) {
			
			return ;
		}

		try {
			if (!user.verifyPassword(password)) 
				return ;
		} catch (Exception ex) {
			LOG.error("internal error on verifyPassword", ex);
			return;
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

		return ;
	}
}
