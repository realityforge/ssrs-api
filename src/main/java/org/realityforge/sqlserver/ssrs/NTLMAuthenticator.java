package org.realityforge.sqlserver.ssrs;

import java.net.Authenticator;
import java.net.PasswordAuthentication;

public final class NTLMAuthenticator
  extends Authenticator
{
  private final String _domainName;
  private final String _userName;
  private final char[] _password;

  NTLMAuthenticator( final String domainName, final String userName, final String password )
  {
    _domainName = domainName;
    _userName = userName;
    _password = password.toCharArray();
  }

  public static void install( final String domainName, final String userName, final String password )
  {
    Authenticator.setDefault( new NTLMAuthenticator( domainName, userName, password ) );
  }

  @Override
  protected PasswordAuthentication getPasswordAuthentication()
  {
    return new PasswordAuthentication( _domainName + "\\" + _userName, _password );
  }
}
