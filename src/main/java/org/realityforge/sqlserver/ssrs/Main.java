package org.realityforge.sqlserver.ssrs;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.json.bind.JsonbBuilder;
import org.realityforge.getopt4j.CLArgsParser;
import org.realityforge.getopt4j.CLOption;
import org.realityforge.getopt4j.CLOptionDescriptor;
import org.realityforge.getopt4j.CLUtil;

/**
 * The entry point in which to run the tool.
 */
public class Main
{
  enum Action
  {
    upload, upload_reports, delete
  }

  private static final int HELP_OPT = 1;
  private static final int QUIET_OPT = 'q';
  private static final int VERBOSE_OPT = 'v';
  private static final int REPORT_TARGET_OPT = 2;
  private static final int UPLOAD_PREFIX_OPT = 3;
  private static final int DOMAIN_OPT = 4;
  private static final int USERNAME_OPT = 5;
  private static final int PASSWORD_OPT = 6;
  private static final int CONFIG_OPT = 'c';
  @Nonnull
  private static final CLOptionDescriptor[] OPTIONS = new CLOptionDescriptor[]{
    new CLOptionDescriptor( "report-target",
                            CLOptionDescriptor.ARGUMENT_REQUIRED,
                            REPORT_TARGET_OPT,
                            "The report server endpoint." ),
    new CLOptionDescriptor( "upload-prefix",
                            CLOptionDescriptor.ARGUMENT_REQUIRED,
                            UPLOAD_PREFIX_OPT,
                            "The prefix to use when uploading reports." ),
    new CLOptionDescriptor( "config-filename",
                            CLOptionDescriptor.ARGUMENT_REQUIRED,
                            CONFIG_OPT,
                            "The name of the json configuration file." ),
    new CLOptionDescriptor( "domain",
                            CLOptionDescriptor.ARGUMENT_REQUIRED,
                            DOMAIN_OPT,
                            "The domain used to access server." ),
    new CLOptionDescriptor( "username",
                            CLOptionDescriptor.ARGUMENT_REQUIRED,
                            USERNAME_OPT,
                            "The username used to access report server." ),
    new CLOptionDescriptor( "password",
                            CLOptionDescriptor.ARGUMENT_REQUIRED,
                            PASSWORD_OPT,
                            "The password used to access report server." ),
    new CLOptionDescriptor( "help",
                            CLOptionDescriptor.ARGUMENT_DISALLOWED,
                            HELP_OPT,
                            "print this message and exit" ),
    new CLOptionDescriptor( "quiet",
                            CLOptionDescriptor.ARGUMENT_DISALLOWED,
                            QUIET_OPT,
                            "Do not output unless an error occurs, just return 0 on no difference.",
                            new int[]{ VERBOSE_OPT } ),
    new CLOptionDescriptor( "verbose",
                            CLOptionDescriptor.ARGUMENT_DISALLOWED,
                            VERBOSE_OPT,
                            "Verbose output of differences.",
                            new int[]{ QUIET_OPT } ),
    };
  private static final int SUCCESS_EXIT_CODE = 0;
  private static final int ERROR_PARSING_ARGS_EXIT_CODE = 2;
  private static final int ERROR_OTHER_EXIT_CODE = 4;
  @Nonnull
  private static final Logger c_logger = Logger.getAnonymousLogger();
  private static Action c_action;
  private static String c_reportTarget;
  @Nonnull
  private static String c_uploadPrefix = "";
  private static String c_domain;
  private static String c_username;
  private static String c_password;
  private static String c_configFilename;

  public static void main( @Nonnull final String[] args )
  {
    setupLogger();
    if ( !processOptions( args ) )
    {
      System.exit( ERROR_PARSING_ARGS_EXIT_CODE );
      return;
    }

    try
    {
      final Config config;
      try ( final InputStream inputStream = new FileInputStream( c_configFilename ) )
      {
        config = JsonbBuilder.create().fromJson( inputStream, Config.class );
      }

      final Uploader uploader = new Uploader( c_reportTarget, c_uploadPrefix, c_domain, c_username, c_password );
      if ( Action.delete == c_action )
      {
        uploader.deleteReports( config.reports );
        uploader.deleteDataSources( config.dataSources );
      }
      else if ( Action.upload == c_action )
      {
        uploader.uploadDataSources( config.dataSources );
        uploader.uploadReports( config.reports );
      }
      else if ( Action.upload_reports == c_action )
      {
        uploader.uploadReports( config.reports );
      }
    }
    catch ( final Throwable t )
    {
      c_logger.log( Level.SEVERE, "Error: Error processing action: " + t );
      System.exit( ERROR_OTHER_EXIT_CODE );
      return;
    }
    System.exit( SUCCESS_EXIT_CODE );
  }

  private static void setupLogger()
  {
    c_logger.setUseParentHandlers( false );
    final ConsoleHandler handler = new ConsoleHandler();
    handler.setFormatter( new RawFormatter() );
    c_logger.addHandler( handler );
  }

  private static boolean processOptions( @Nonnull final String[] args )
  {
    // Parse the arguments
    final CLArgsParser parser = new CLArgsParser( args, OPTIONS );

    //Make sure that there was no errors parsing arguments
    if ( null != parser.getErrorString() )
    {
      c_logger.log( Level.SEVERE, "Error: " + parser.getErrorString() );
      return false;
    }

    // Get a list of parsed options
    final List<CLOption> options = parser.getArguments();
    for ( final CLOption option : options )
    {
      switch ( option.getId() )
      {
        case CLOption.TEXT_ARGUMENT:
        {
          final String actionName = option.getArgument();
          try
          {
            c_action = Action.valueOf( actionName );
          }
          catch ( IllegalArgumentException e )
          {
            c_logger.log( Level.SEVERE, "Error: Bad action: " + actionName );
            return false;
          }
          break;
        }
        case REPORT_TARGET_OPT:
        {
          c_reportTarget = option.getArgument();
          break;
        }
        case UPLOAD_PREFIX_OPT:
        {
          c_uploadPrefix = option.getArgument();
          break;
        }
        case DOMAIN_OPT:
        {
          c_domain = option.getArgument();
          break;
        }
        case USERNAME_OPT:
        {
          c_username = option.getArgument();
          break;
        }
        case PASSWORD_OPT:
        {
          c_password = option.getArgument();
          break;
        }
        case CONFIG_OPT:
        {
          c_configFilename = option.getArgument();
          break;
        }
        case VERBOSE_OPT:
        {
          c_logger.setLevel( Level.ALL );
          break;
        }
        case QUIET_OPT:
        {
          c_logger.setLevel( Level.WARNING );
          break;
        }
        case HELP_OPT:
        {
          printUsage();
          return false;
        }
      }
    }
    if ( null == c_reportTarget )
    {
      c_logger.log( Level.SEVERE, "Error: Report target must be specified" );
      return false;
    }
    if ( ( null != c_password || null != c_domain || null != c_username ) &&
         ( null == c_password || null == c_domain || null == c_username ) )
    {
      c_logger.log( Level.SEVERE, "Error: If domain, username or password is specified then all must be specified" );
      return false;
    }
    if ( null == c_configFilename )
    {
      c_logger.log( Level.SEVERE, "Error: Configuration file must be specified" );
      return false;
    }
    if ( null == c_action )
    {
      c_logger.log( Level.SEVERE, "Error: Action must be specified" );
      return false;
    }
    if ( c_logger.isLoggable( Level.FINE ) )
    {
      c_logger.log( Level.INFO, "Action: " + c_action );
      c_logger.log( Level.INFO, "Report Target: " + c_reportTarget );
      c_logger.log( Level.INFO, "Upload Prefix: " + c_uploadPrefix );
      if ( null != c_domain )
      {
        c_logger.log( Level.INFO, "Domain: " + c_domain );
        c_logger.log( Level.INFO, "Username: " + c_username );
        c_logger.log( Level.INFO, "Password: " + c_password );
      }
    }

    return true;
  }

  /**
   * Print out a usage statement
   */
  @SuppressWarnings( "StringBufferReplaceableByString" )
  private static void printUsage()
  {
    final String lineSeparator = System.getProperty( "line.separator" );

    final StringBuilder msg = new StringBuilder();

    msg.append( "java " );
    msg.append( Main.class.getName() );
    msg.append( " [options] (upload|upload_reports|delete)" );
    msg.append( lineSeparator );
    msg.append( "Options: " );
    msg.append( lineSeparator );

    msg.append( CLUtil.describeOptions( OPTIONS ).toString() );

    c_logger.log( Level.INFO, msg.toString() );
  }
}