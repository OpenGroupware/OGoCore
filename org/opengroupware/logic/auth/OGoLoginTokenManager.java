/*
  Copyright (C) 2007-2008 Helge Hess

  This file is part of OpenGroupware.org (OGo)

  OGo is free software; you can redistribute it and/or modify it under
  the terms of the GNU General Public License as published by the
  Free Software Foundation; either version 2, or (at your option) any
  later version.

  OGo is distributed in the hope that it will be useful, but WITHOUT ANY
  WARRANTY; without even the implied warranty of MERCHANTABILITY or
  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public
  License for more details.

  You should have received a copy of the GNU General Public
  License along with OGo; see the file COPYING.  If not, write to the
  Free Software Foundation, 59 Temple Place - Suite 330, Boston, MA
  02111-1307, USA.
*/
package org.opengroupware.logic.auth;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

import javax.security.auth.Subject;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.getobjects.eoaccess.EOAdaptor;
import org.getobjects.eoaccess.EOSQLExpression;
import org.getobjects.foundation.NSObject;
import org.getobjects.foundation.NSPropertyListSerialization;
import org.getobjects.foundation.UString;
import org.opengroupware.logic.db.OGoDatabase;
import org.opengroupware.logic.db.OGoLoginToken;

/**
 * OGoLoginTokenManager
 * <p>
 * Manages OGo login tokens. Conceptually a token wraps the login information,
 * that is, it would store the login/password and resurrect an OGoLoginContext
 * when required.
 * <br>
 * However, as long as we use the internal account database there is no need to
 * store the login/password.
 * 
 * <p>
 * The token manager does some aggressive caching. The basic idea is that
 * tokens survive server restarts and that a WOSession is only used for
 * transient or caching information.
 * <br>
 * A maintenance process runs every other minute and keeps the touch dates
 * in sync.
 * 
 * <p>
 * TBD: cleanup comment
 * <p>
 * @author helge
 */
public class OGoLoginTokenManager extends NSObject {
  // TBD: we need to rewrite this for JAAS
  protected static final Log log = LogFactory.getLog("OGoLoginTokenManager");
  
  // TBD: how does the maintenance thread know what to touch in the database?
  
  protected Timer maintenanceTimer = null;
  protected int maintenanceTimeOutInSeconds = 120 /* every two minutes */;
  //protected int maintenanceTimeOutInSeconds = 5;
  
  protected int expirationFactor = 1;
  
  protected OGoDatabase db;
  protected OGoLoginModule authenticator;

  protected ConcurrentHashMap<String, LoginContext>  token2jaas;
  protected ConcurrentHashMap<String, OGoLoginToken> token2info;
  protected ConcurrentHashMap<String, Date>          tokenTouch;
  
  public OGoLoginTokenManager(OGoDatabase _db, OGoLoginModule _auth) {
    super();
    
    this.authenticator = _auth;
    this.db = _db;
   
    this.token2jaas = new ConcurrentHashMap<String, LoginContext>(128);
    this.token2info = new ConcurrentHashMap<String, OGoLoginToken>(128);
    this.tokenTouch = new ConcurrentHashMap<String, Date>(128);

    /* Note: if we do not create a daemon thread, the timer thread won't
     *       allow apps to stop!
     */
    this.maintenanceTimer =
      new Timer("OGoLoginTokenManager", true /* daemon */);
    this.maintenanceTimer.scheduleAtFixedRate
      (new MaintenanceTimerTask(this),
       1 /* delay in ms */,
       this.maintenanceTimeOutInSeconds * 1000 /* period in ms */);
  }

  /* operations */
  
  /**
   * Creates a new token for the given subject / environment.
   * 
   * @param _subject - the JAAS subject which contains the principals
   * @param _env     - an optional environment
   * @return the token String as inserted in the database
   */
  public String createToken(Subject _subject, Object _env) {
    // TBD: createToken must take a *Subject* (or LoginContext), and not do the
    //      auth
    if (_subject == null)
      return null;
    
    String envext = _env != null
      ? NSPropertyListSerialization.stringFromPropertyList(_env) : null;
      
    Date now = new Date();
    Number uid = null;

    /* generate token */
    
    StringBuilder msg = new StringBuilder(1024);
    for (Principal p: _subject.getPrincipals()) {
      msg.append(p.getName());
      if (p instanceof IOGoPrincipal) {
        // TBD: check DB identifier
        Number pid = ((IOGoPrincipal)p).id();
        msg.append(pid);
        if (p instanceof OGoAccountPrincipal) {
          if (uid == null)
            uid = pid;
          else if (!uid.equals(pid))
            log.error("multiple account principals!");
        }
      }
    }
    if (uid == null) {
      log.error("did not find a primary principal in subject!");
      return null;
    }
    msg.append(now.getTime());
    if (envext != null) msg.append(envext);
    
    String token = UString.md5HashForString(msg.toString());
    msg = null;
    
    /* insert token into database */
    
    Map<String, Object> record = new HashMap<String, Object>(16);
    record.put("token",         token);
    record.put("account_id",    uid);
    record.put("creation_date", now);
    record.put("touch_date",    now);
    if (envext != null) record.put("environment", envext);
    
    if (!this.db.adaptor().insertRow("login_token", record)) {
      log.error("could not insert login token into database: " + _subject);
      return null;
    }
    
    /* token is inserted, we are done */
    
    return token;
  }
  
  
  /**
   * Invalidate the given token. This explicitly removes the token from the
   * database and logs out the OGoLoginContext if there is one.
   * 
   * @param _token - the token which should be logged out
   * @return true if the logout succeeded, false otherwise
   */
  public boolean logoutToken(String _token) {
    if (!this.deleteToken(_token))
      return false;
    
    /* next we logout the principal */
    
    LoginContext lc = this.token2jaas.remove(_token);
    if (lc != null) {
      /* a context was cached, logout */
      try {
        lc.logout();
      }
      catch (LoginException e) {
        log.error("JAAS logout error on token: " + _token, e);
        // what to do? token is already deleted
      }
    }
    
    return true;
  }

  /**
   * Invalidate the given token. This explicitly removes the token from the
   * database.
   * 
   * @param _token - the token which should be logged out
   * @return true if the delete succeeded, false otherwise
   */
  public boolean deleteToken(String _token) {
    if (_token == null)
      return true; /* probably an expired token, we don't care */

    
    /* First we need to delete the token in the database so that concurrent
     * threads do not recreate a login context or touch the token.
     */
    
    /* properly escape token */
    EOAdaptor adaptor = this.db.adaptor();
    EOSQLExpression e = adaptor.expressionFactory().createExpression(null);
    String sql = "DELETE FROM login_token WHERE token = " + 
      e.sqlStringForString(_token);
    
    int affected = adaptor.performUpdateSQL(sql);
    if (affected < 0) {
      log.error("SQL error in deleting token from database: " + _token);
      return false;
    }
    
    if (log.isInfoEnabled()) {
      if (affected == 0)
        log.info("token was already deleted from database: " + _token);
      else
        log.info("deleted token from database: " + _token);
    }
    
    /* remove token object from cache */

    this.token2info.remove(_token);
    
    return true;
  }
  
  /**
   * Retrieves the validated token information for the given ID.
   * 
   * @param _token - the token string
   * @param _env   - the environment for the token
   * @return an OGoLoginContext for the token/env or null
   */
  public OGoLoginToken tokenForID(String _token, Object _env, boolean _touch) {
    Date now = new Date();
    
    /* first check cache, then fetch token */
    
    OGoLoginToken tokObject = this.token2info.get(_token);
    if (tokObject == null) {
      tokObject = (OGoLoginToken)
        this.db.dataSourceForEntity("LoginTokens").findById(_token);
      
      if (tokObject != null) /* cache */
        this.token2info.put(_token, tokObject);
    }
    
    if (tokObject == null) {
      log.warn("could not locate token: " + _token);
      return null;
    }

    /* now validate the token */

    if (tokObject.isExpired(now, this.tokenTouch.get(_token))) {
      if (log.isInfoEnabled()) log.info("token expired: " + _token);
      // no explicit logout: this.logoutToken(_token);
      // TBD: we will remove the token in the maintenance thread???
      return null;
    }

    /* check the environment */
    
    if (tokObject.envPList != null) {
      if (_env == null) {
        log.warn("no environment provided for comparison with env-token: " +
            _token);
        return null;
      }
      
      // TBD: _env must be retrieved using a LoginModule callback
      if (!tokObject.envPList.equals(_env)) {
        log.warn("environment provided did not match with token " +
            _token + ": " + tokObject.envPList + " vs: " + _env);
        return null;
      }
    }

    /* next touch the token (DB sync done asynchronously) */
    
    if (_touch && this.tokenTouch != null)
      this.tokenTouch.put(_token, now);
    
    return tokObject;
  }
  
  /**
   * Retrieve an OGoLoginContext for the given token.
   * 
   * DOES NOT WORK
   * 
   * @param _token - the token string
   * @param _env   - the environment for the token
   * @return an OGoLoginContext for the token/env or null
   */
  public LoginContext principalForToken(String _token, Object _env) {
    // TBD: should be subjectForToken?
    // TBD: this belongs directly into the OGoLoginModule
    if (_token == null)
      return null;
    
    /* first check cache, then fetch token */
    
    OGoLoginToken tokObject = this.tokenForID(_token, _env, true /* touch */);
    if (tokObject == null)
      return null;
    
    /* next, get login context */

    LoginContext lc = this.token2jaas.get(_token);
    if (lc == null) {
      /* no context was cached yet, get one from the authenticator */
      
      /* The database authenticator can login w/o a password, we probably need
       * to store login/creds for LDAP logins
       */
      // TBD: we should NOT call this. If we are inside the LoginModule, *we*
      //      authenticate
      //lc = this.authenticator.compoundPrincipalForId(tokObject.accountId);
      
      // TBD: create LoginContext
      
      if (lc != null) {
        /* cache */
        this.token2jaas.put(_token, lc);
      }
    }
    
    return lc;
  }
  
  
  /* maintenance */
  
  protected void performMaintenance(Date _now, Date _lastRun) {
    /* first collect all tokens which got a touch since the last run */
    
    List<String> tokensUpdate = new ArrayList<String>(128);
    for (String token: this.tokenTouch.keySet()) {
      Date touchDate = this.tokenTouch.get(token);
      if (touchDate == null)
        continue;
      
      if (touchDate.after(_lastRun))
        tokensUpdate.add(token);
      else {
        /* wasn't touched till last run, check expiration */
        OGoLoginToken tokObject = this.token2info.get(token);
        if (tokObject != null) {
          if (tokObject.expirationDate != null) {
            
          }
        }
      }
      // TBD: remove expired tokens?
    }
    
    EOAdaptor adaptor = this.db.adaptor();
    EOSQLExpression e = adaptor.expressionFactory().createExpression(null);
    StringBuilder sql;
    
    if (tokensUpdate.size() > 0) {
      /* Update all changed tokens in one step. We use a single timestamp
       * to keep the number of updates low. So the timestamp stored in the
       * DB is just an approximation.
       */
      
      /* build Update SQL */
      
      sql = new StringBuilder(256);
      sql.append("UPDATE login_token SET touch_date = CURRENT_TIMESTAMP ");
      sql.append("WHERE token IN ( ");
      
      boolean isFirst = true;
      for (String token: tokensUpdate) {
        if (isFirst) isFirst = false;
        else sql.append(", ");
        
        sql.append(e.sqlStringForString(token));
      }
      sql.append(" )");
      
      /* run Update SQL */
      
      int affected = adaptor.performUpdateSQL(sql.toString());
      if (affected < 0) {
        log.error("could not update tokens in database!");
        return;
      }
      
      if (log.isInfoEnabled())
        log.info("touched login_token: " + tokensUpdate);
    }
      
    /* build expiration SQL */

    // PostgreSQL specific, should be moved to model
    sql = new StringBuilder(256); /* yeah, I know we could do setLength() */
    sql.append("DELETE FROM login_token WHERE touch_date + ( ");
    sql.append("timeout");
    if (this.expirationFactor > 0) {
      sql.append(" * ");
      sql.append(this.expirationFactor);
    }
    sql.append(" * '1 second'::interval");
    sql.append(") < CURRENT_TIMESTAMP");

    /* run expiration SQL */

    int deletedTokens = adaptor.performUpdateSQL(sql.toString());
    if (deletedTokens < 0) {
      log.error("could not expire tokens in database!");
      return;
    }
    if (log.isInfoEnabled())
      log.info("expired login_tokens: " + deletedTokens);
  }
  
  
  /* maintenance timer */
  
  private static class MaintenanceTimerTask extends TimerTask {
    private OGoLoginTokenManager tokenManager;
    
    protected Date lastRun;
    
    public MaintenanceTimerTask(OGoLoginTokenManager _tokenManager) {
      this.tokenManager = _tokenManager;
    }

    @Override
    public void run() {
      Date now = new Date(); /* remember when we started */
      this.tokenManager.performMaintenance(now, this.lastRun);
      this.lastRun = now; /* we might have seen updates in the meantime! */
    }
  }
  
}
