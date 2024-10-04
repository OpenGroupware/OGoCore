/*
  Copyright (C) 2008-2009 Helge Hess

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

import java.io.IOException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.FailedLoginException;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.getobjects.eoaccess.EOAccessDataSource;
import org.getobjects.eoaccess.EOActiveDataSource;
import org.getobjects.eoaccess.EOActiveRecord;
import org.getobjects.eoaccess.EOAdaptor;
import org.getobjects.eoaccess.EODatabase;
import org.getobjects.eocontrol.EOFetchSpecification;
import org.getobjects.eocontrol.EOSortOrdering;
import org.getobjects.foundation.UMap;
import org.getobjects.foundation.UObject;
import org.getobjects.foundation.UString;
import org.getobjects.jaas.EODatabaseLoginModule;
import org.opengroupware.logic.db.OGoDatabase;
import org.opengroupware.logic.db.OGoLoginToken;
import org.apache.commons.codec.digest.UnixCrypt;

/**
 * OGoLoginModule
 * <p>
 * A JAAS login module for OGo. Note that each module instance represents just
 * one authentication request. The docs say that the instance keeps
 * authentication state.
 * <p>
 * Parameters:
 * <ul>
 *   <li>adaptor     - either a JDBC URL String or an EOAdaptor object
 *   <li>database    - an OGoDatabase object
 *   <li>preservepwd - whether the login-context should preserve passwords
 * </ul>
 * <p>
 * Note: Not yet finished, work in progress.
 * 
 * @author helge
 */
public class OGoLoginModule extends EODatabaseLoginModule {
  protected static final Log log = LogFactory.getLog("OGoAuthenticator");
  
  protected boolean doesPreservePasswords;
  
  // we need some cache for those
  protected OGoLoginTokenManager tokenManager;
  
  public OGoLoginModule() {
  }
  
  /* convenience */
  
  /**
   * This is a convenience function which sets up a JAAS login context with the
   * default database configuration,
   * and then performs a login with the given login/password.
   * 
   * @param _db   - a setup OGoDatabase object
   * @param _user - the login name
   * @param _pwd  - the login password
   * @return null if the login failed, otherwise the LoginContext
   */
  public static LoginContext jaasLogin
    (final EODatabase _db, final String _user, final String _pwd)
  {
    if (_db == null) {
      log.warn("got no database for JAAS login of user: " + _user);
      return null;
    }
    
    final Subject subject = new Subject();
    LoginContext jlc = null;
    try {
      jlc = new LoginContext(
          "OGo",   /* application     */
          subject, /* subject */
          new NamePasswordCallbackHandler(_user, _pwd), /* CallbackHandler */
          new OGoDefaultLoginConfig(_db) /* configuration */);
    }
    catch (LoginException e) {
      log.error("could not setup JAAS LoginContext", e);
    }
    if (jlc == null)
      return null;
    
    /* login */
    
    try {
      jlc.login();
    }
    catch (LoginException e) {
      jlc = null;
      return null;
    }
    
    return jlc;
  }
  
  
  /* prepare module instance */

  @Override
  public void initialize
    (final Subject _subject, final CallbackHandler _handler,
     final Map<String, ?> _sharedState, final Map<String, ?> _options)
  {
    // TBD: cache objects in a global hash!
    super.initialize(_subject, _handler, _sharedState, _options);
    
    this.doesPreservePasswords = UObject.boolValue(_options.get("preservepwd"));
  }
  
  @Override
  public void dispose() {
    this.tokenManager = null;
    super.dispose();
  }
  
  public boolean doesPreservePasswords() {
    return this.doesPreservePasswords;
  }
  
  
  /* phase one: authenticate user or token */
  
  @Override
  public boolean login() throws LoginException {
    if (this.database == null)
      throw new LoginException("missing valid JAAS OGo database config!");
    
    if (this.handler == null)
      throw new LoginException("missing JAAS callback handler!");

    
    // TBD: check whether there is some known principal, eg from an LDAP
    //      login, if so, perform principal=>OGo UID mapping and add
    //      a specific OGo principal
    
    
    if (this.tokenManager != null) {
      if (this.loginWithTokens())
        return true;
    }
    
    if (this.loginWithUsernameAndPassword())
      return true;
    
    return false;
  }

  @SuppressWarnings("unchecked")
  @Override
  protected boolean loginWithUsernameAndPassword(String _login, char[] _pwd)
    throws LoginException
  {
    if (_login == null || _login.length() == 0)
      throw new FailedLoginException(" got no login");
    if (this.database == null) /* no database for auth */
      throw new FailedLoginException(" got no database");
    if (this.subject == null)
      throw new FailedLoginException(" no subject to fill");

    final String pwd = new String(_pwd);
    
    /* next perform the actual database authentication */
    
    final Number uid = this.primaryLogin(_login, pwd);
    if (uid == null || uid.intValue() < 1)
      throw new FailedLoginException("login failed for user: " + _login);
    
    
    final Set<Principal> principals = this.subject.getPrincipals();
    
    /* successfully created account, add principal */

    final OGoAccountPrincipal account = new OGoAccountPrincipal(uid, _login);
    if (account != null && this.doesPreservePasswords())
      account.setPreservedPassword(pwd);
    principals.add(account);
    
    
    /* fetch teams and add them */
    // TBD: could be nested teams?

    EOAccessDataSource teamsDS = new EOActiveDataSource(this.database, "Teams");
    
    EOFetchSpecification fs = 
      teamsDS.entity().fetchSpecificationNamed("teamsForAccountID");
    fs = fs.fetchSpecificationWithQualifierBindings(UMap.create("id", uid));
    fs.setFetchAttributeNames(new String[] { "id", "name" } );
    fs.setFetchesRawRows(true); // returns company_id, which is wrong?
    fs.setFetchesReadOnly(true);
    fs.setSortOrderings(EOSortOrdering.create("id", "ASC"));
    teamsDS.setFetchSpecification(fs);
    
    final List<Map<String, Object>> teams = teamsDS.fetchObjects();
    for (Map<String, Object> team: teams) {
      Number teamId   = (Number)team.get("company_id");
      String teamName = (String)team.get("description");
      
      OGoTeamPrincipal teamPrincipal = new OGoTeamPrincipal(teamId, teamName);
      teamPrincipal.addMember(account);
      principals.add(teamPrincipal);
    }
    
    return true; /* everything went fine */
  }
  
  @Override
  public boolean addPrincipalForAuthenticatedLogin(String _login, Object _usr) {
    if (!(_usr instanceof Principal) || this.subject == null)
      return false;

    // TBD: we should probably add all java.security.acl.Groups as principals
    this.subject.getPrincipals().add((Principal)_usr);
    return true;
  }
  
  protected boolean loginWithTokens() throws LoginException {
    if (this.tokenManager == null) /* no token manager for auth */
      return false;
    
    /* first retrieve tokens from callback handler */
    
    final OGoTokenCallback requestTokens = new OGoTokenCallback();
    
    try {
      this.handler.handle(new Callback[] { requestTokens });
    }
    catch (IOException ie) {
      log.error("some IO error occurred during OGoTokenCallback retrieval",
          ie);
      return false;
    }
    catch (UnsupportedCallbackException uce) {
      /* token callbacks unsupported, this is OK */
      return false;
    }
    
    boolean foundOne = false;
    final List<String> tokens =
      requestTokens != null ? requestTokens.getTokens() : null;
    if (tokens == null || tokens.size() == 0)
      return false; /* no tokens */
    
    for (String token: tokens) {
      // TBD: add LoginModule options for environment?
      // TBD: throw CredentialExpired/CredentialNotFoundException?
      
      OGoLoginToken tokenInfo = this.tokenManager.tokenForID(
          token, requestTokens.getEnvironment(), true /* tough */);
      
      if (tokenInfo == null)
        continue;
      
      // TBD: fill subject with principals for tokenInfo 
      
      // this.subject.getPrincipals().add(lc);
      //foundOne = true;
    }
    
    return foundOne;
  }
  
  
  /* phase two */
  
  @Override
  public boolean commit() throws LoginException {
    final boolean isInfoOn = log.isInfoEnabled();
    
    for (OGoAccountPrincipal p: 
      this.subject.getPrincipals(OGoAccountPrincipal.class))
    {
      /* log successful login */
      this.logLogin(p.getName(), p.id());
      
      if (isInfoOn)
        log.info("user logged in: " + p.id());
    }
    
    return true;
  }

  @Override
  public boolean abort() throws LoginException {
    // TBD: expire tokens?
    final boolean isInfoOn = log.isInfoEnabled();
    
    // TBD: fix me for new setup
    final Set<Principal> principals = this.subject.getPrincipals();
    if (principals == null || principals.size() == 0)
      return true;
    
    Collection<Principal> walkList = new ArrayList<Principal>(principals);
    for (Principal p: walkList) {
      
      if (p instanceof OGoTeamPrincipal) {
        /* no special processing for teams */
        principals.remove(p);
        continue;
      }

      if (p instanceof OGoAccountPrincipal) {
        if (isInfoOn)
          log.info("user login aborted by JAAS: " + p.getName());

        this.sessionLog(((OGoAccountPrincipal)p).id(), "aborted");

        principals.remove(p);
        continue;
      }
    }
    return true;
  }
  
  
  /* phase three, logout */

  @Override
  public boolean logout() throws LoginException {
    // TBD: can we cache login/logout information in the LoginModule
    //      instance?? Eg we would need to expire the appropriate tokens on
    //      logout, do we need to store them in the principals? hopefully not.
    // TBD: expire tokens?
    final boolean isInfoOn = log.isInfoEnabled();
    
    final Set<Principal> principals = this.subject.getPrincipals();
    if (principals == null || principals.size() == 0)
      return true;
    
    // TBD: fix me for new setup
    Collection<Principal> walkList = new ArrayList<Principal>(principals);
    for (Principal p: walkList) {
      
      if (p instanceof OGoTeamPrincipal) {
        /* no special processing for teams */
        principals.remove(p);
        continue;
      }

      if (p instanceof OGoAccountPrincipal) {
        if (isInfoOn) log.info("user logging out: " + p.getName());
        this.sessionLog(((OGoAccountPrincipal)p).id(), "logout");
        principals.remove(p);
        continue;
      }
    }
    return true;
  }
  
  
  
  /* backend */

  /**
   * Validates the login/password in the database and returns the id of the user
   * if the operation was successful.
   * <p>
   * This method logs login attempts with incorrect logins and passwords in the
   * session_log table! It does NOT log successful logins.
   * 
   * @param _login - the login to be checked
   * @param _pwd   - the password associated with the login
   * @return the person id of the user associated with the login or null
   */
  public Number primaryLogin(final String _login, String _pwd) {
    // TBD: should this cache the login context based on a login/pwd hash?
    // TBD: write a JAAS module, use it for auth (hm, JAAS=>IOGoAuthenticator)
    if (this.database == null) {
      log.fatal("missing database ...");
      return null;
    }
    if (_login == null || _login.length() == 0) {
      log.fatal("got no login name to perform login ...");
      return null;
    }
    if (_pwd == null) _pwd = "";
    
    // TBD: if we want to limit the number of failed login attempts this would
    //      have to be done here
    //      (and we definitely DO want this! :-)

    
    /* Fetch stored password. We could also do the comparison in the database,
     * but then we would need to transfer the password on the wire instead of
     * just the hash.
     */
    EOActiveDataSource ds = new EOActiveDataSource(this.database, "Accounts");
    EOActiveRecord p = (EOActiveRecord)
      ds.find("cryptedPassword", "login", _login);
    if (p == null) {
      this.logInvalidUser(_login);
      return null;
    }
    String storedPwdHash = (String)p.valueForKey("password");
    if (log.isDebugEnabled()) log.debug("stored hash: " + storedPwdHash);
    
    
    /* Generate hash for the given password (based on the algorithm of the
     * stored password)
     */
    String pwdHash = null;
    if (storedPwdHash == null)
      pwdHash = null;
    else if (storedPwdHash.startsWith("{md5}"))
      pwdHash = "{md5}" + UString.md5HashForString(_pwd);
    else {
      /* crypt password */
      // TBD: why is that?
      pwdHash = UnixCrypt.crypt(_pwd, storedPwdHash);
    }
    
    
    /* lookup account record */
    
    EOActiveRecord account = (EOActiveRecord) 
      ds.find("login", "login", _login, "password", pwdHash);
    if (account == null) {
      this.logInvalidPassword(_login, (Number)p.valueForKey("id"));
      return null;
    }
    return (Number)account.valueForKey("id");
  }
  
  
  /* session log */
  // TBD: maintain failcount, detect too many logins!!!
  
  public void logInvalidUser(final String _login) {
    // TBD: log failed attempt
    log.error("did not find user in database: '" + _login + "'");
    this.sessionLog(null, "loginfail:" + _login);
  }
  
  public void logInvalidPassword(final String _login, final Number _loginId) {
    // TBD: log failed attempt
    log.error("could not login user '" + _login + "' with provided pwd.");
    this.sessionLog(_loginId, "pwdfail");
  }
  
  public void logLogin(final String _login, final Number _loginId) {
    // TBD: reset failed attempts?
    this.sessionLog(_loginId, "login");
  }
  
  protected void sessionLog(final Number _loginId, final String _action) {
    EOAdaptor adaptor = this.database.adaptor();
    Number    pkey    = ((OGoDatabase)this.database).nextPrimaryKey();
    
    Map<String, Object> logEntry = new HashMap<String, Object>(4);
    logEntry.put("session_log_id", pkey);
    logEntry.put("log_date",       new Date());
    logEntry.put("action",         _action);
    logEntry.put("account_id", _loginId != null ? _loginId : new Integer(0));
    if (!adaptor.insertRow("session_log", logEntry))
      log.fatal("could not log entry in session_log table!");
  }
  
  
  /* description */

  @Override
  public void appendAttributesToDescription(final StringBuilder _d) {
    super.appendAttributesToDescription(_d);
    
    if (this.database == null)
      _d.append(" no-db");
  }
}
