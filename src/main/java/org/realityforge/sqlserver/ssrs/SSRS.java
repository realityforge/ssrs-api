package org.realityforge.sqlserver.ssrs;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import javax.xml.namespace.QName;
import org.realityforge.sqlserver.ssrs.reportingservice2005.ArrayOfCatalogItem;
import org.realityforge.sqlserver.ssrs.reportingservice2005.ArrayOfProperty;
import org.realityforge.sqlserver.ssrs.reportingservice2005.ArrayOfWarning;
import org.realityforge.sqlserver.ssrs.reportingservice2005.CatalogItem;
import org.realityforge.sqlserver.ssrs.reportingservice2005.CredentialRetrievalEnum;
import org.realityforge.sqlserver.ssrs.reportingservice2005.DataSourceDefinition;
import org.realityforge.sqlserver.ssrs.reportingservice2005.ItemTypeEnum;
import org.realityforge.sqlserver.ssrs.reportingservice2005.ReportingService2005;
import org.realityforge.sqlserver.ssrs.reportingservice2005.ReportingService2005Soap;
import org.realityforge.sqlserver.ssrs.reportingservice2005.Warning;

/**
 * Adapter class for interacting with the SSRS service from ruby code.
 */
@SuppressWarnings( { "UnusedDeclaration" } )
public class SSRS
{
  private static final Logger LOG = Logger.getLogger( SSRS.class.getName() );
  private static final String PATH_SEPARATOR = "/";

  private final ReportingService2005Soap _soap;
  private final String _prefix;

  /**
   * Create an adapter for a specific service, acting on a particular path.
   *
   * @param wsdlURL the URL to the wsdl for the service
   * @param prefix  the prefix for all reports interacted with by this adapter
   */
  public SSRS( final URL wsdlURL, final String prefix )
  {
    if ( null == wsdlURL )
    {
      throw new NullPointerException( "wsdlURL" );
    }
    if ( null == prefix )
    {
      throw new NullPointerException( "prefix" );
    }
    _prefix = prefix;
    final QName qName =
      new QName( "http://schemas.microsoft.com/sqlserver/2005/06/30/reporting/reportingservices",
                 "ReportingService2005" );
    final ReportingService2005 service = new ReportingService2005( wsdlURL, qName );
    _soap = service.getReportingService2005Soap();
  }

  /**
   * A helper method to configure the logging.
   * Used from jruby.
   */
  public static void setupLogger( final boolean verbose )
  {
    LOG.setUseParentHandlers( false );
    LOG.setLevel( verbose ? Level.ALL : Level.INFO );
    final ConsoleHandler handler = new ConsoleHandler();
    handler.setFormatter( new Formatter()
    {
      @Override
      public String format( final LogRecord record )
      {
        return record.getMessage() + "\n";
      }
    } );
    LOG.addHandler( handler );
  }

  /**
   * Log a info message.
   * Used from ruby code. (Not using LOG directly as overloading confuses ruby)
   */
  public static void info( final String message )
  {
    LOG.info( message );
  }

  /**
   * Log a warning message.
   * Used from ruby code. (Not using LOG directly as overloading confuses ruby)
   */
  public static void warning( final String message )
  {
    LOG.warning( message );
  }

  /**
   * Create a data source at path with a specific connection string.
   */
  public void createSQLDataSource( final String path, final String connectionString )
  {
    final DataSourceDefinition definition = new DataSourceDefinition();
    definition.setConnectString( connectionString );
    definition.setEnabled( true );
    definition.setExtension( "SQL" );
    definition.setImpersonateUser( false );
    definition.setPrompt( null );
    definition.setCredentialRetrieval( CredentialRetrievalEnum.NONE );
    definition.setWindowsCredentials( false );
    createDataSource( path, definition );
  }

  /**
   * Create a data source at path with a using a complete data definition.
   */
  public void createDataSource( final String path, final DataSourceDefinition definition )
  {
    info( "Creating DataSource " + path );
    final String physicalName = toPhysicalFileName( path );
    final String reportName = filenameFromPath( physicalName );
    final String reportDir = dirname( physicalName );

    final ItemTypeEnum type = _soap.getItemType( physicalName );
    if ( ItemTypeEnum.UNKNOWN != type )
    {
      final String s = "Can not create data source as path " + path + " exists and is of type " + type + ".";
      throw new IllegalStateException( s );
    }
    else
    {
      _soap.createDataSource( reportName, reportDir, false, definition, new ArrayOfProperty() );
    }
  }

  /**
   * Create a report at specific path from specified report file. Path must not exist.
   */
  public void createReport( final String path, final String filename )
  {
    final File file = new File( filename );
    info( "Creating Report " + path );
    final String physicalName = toPhysicalFileName( path );
    LOG.fine( "Creating Report with symbolic item " + path + " as " + physicalName );
    final ItemTypeEnum type = _soap.getItemType( physicalName );
    if ( ItemTypeEnum.UNKNOWN != type )
    {
      final String s = "Can not create report as path " + physicalName + " exists and is of type " + type + ".";
      throw new IllegalStateException( s );
    }
    else
    {
      final byte[] bytes = readFully( path, file );
      final String reportName = filenameFromPath( physicalName );
      final String reportDir = dirname( physicalName );
      LOG.finer( "Invoking createReport(name=" + reportName + ",parentDir=" + reportDir + ")" );
      final ArrayOfWarning warnings =
        _soap.createReport( reportName, reportDir, true, bytes, new ArrayOfProperty() );

      if ( null != warnings )
      {
        final String message =
          "createReport(name=" + reportName + ",parentDir=" + reportDir + ") from " + file.getAbsolutePath();
        logWarnings( message, warnings );
      }
    }
  }

  /**
   * Create a report at specific path from specified report file. Path must not exist.
   */
  public void downloadReport( final String path, final String filename )
  {
    final File file = new File( filename );
    final String physicalName = toPhysicalFileName( path );

    info( "Downloading Report with symbolic name " + path + " to " + file );

    final byte[] data = _soap.getReportDefinition( physicalName );

    try ( final FileOutputStream out = new FileOutputStream( file ) )
    {
      out.write( data );
    }
    catch ( final IOException ioe )
    {
      final String message = "Failed to download report with symbolic name " + path + " to " + file;
      LOG.warning( message );
      if ( file.exists() && !file.delete() )
      {
        throw new IllegalStateException( message + " and failed to delete temporary file", ioe );
      }
      else
      {
        throw new IllegalStateException( message, ioe );

      }
    }
  }

  /**
   * List files at symbolic path.
   */
  public String[] listReports( final String path )
  {
    info( "Listing Reports at " + path );
    final List<CatalogItem> catalogItems = listItems( path );
    final ArrayList<String> list = new ArrayList<>();
    for ( final CatalogItem item : catalogItems )
    {
      if ( item.getType() == ItemTypeEnum.REPORT )
      {
        list.add( item.getName() );
      }
    }
    return list.toArray( new String[ 0 ] );
  }

  /**
   * List directories at symbolic path.
   */
  public String[] listFolders( final String path )
  {
    info( "Listing Folders at " + path );
    final List<CatalogItem> catalogItems = listItems( path );

    final List<String> list = new ArrayList<>();
    for ( final CatalogItem item : catalogItems )
    {
      if ( item.getType() == ItemTypeEnum.FOLDER )
      {
        list.add( item.getName() );
      }
    }
    return list.toArray( new String[ 0 ] );
  }

  /**
   * Delete symbolic path and all sub elements. Will skip if no such path.
   */
  public void delete( final String path )
  {
    info( "Deleting item " + path );
    final String physicalName = toPhysicalFileName( path );
    LOG.fine( "Deleting symbolic item " + path + " as " + physicalName );
    final ItemTypeEnum type = _soap.getItemType( physicalName );
    if ( ItemTypeEnum.UNKNOWN == type )
    {
      LOG.finer( "Skipping invocation of deleteItem(item=" + physicalName + ") as item does not exist." );
    }
    else
    {
      LOG.finer( "Invoking deleteItem(item=" + physicalName + ")" );
      _soap.deleteItem( physicalName );
    }
  }

  /**
   * Create a directory node at specified path. Path must not exist.
   */
  public void mkdir( final String filePath )
  {
    info( "Creating dir " + filePath );
    final String physicalName = toPhysicalFileName( filePath );
    LOG.fine( "Creating symbolic dir " + filePath + " as " + physicalName );
    final StringBuilder path = new StringBuilder();
    for ( final String dir : physicalName.substring( 1 ).split( PATH_SEPARATOR ) )
    {
      final String parentDir = ( path.length() == 0 ) ? PATH_SEPARATOR : path.toString();
      final ItemTypeEnum type = _soap.getItemType( path.toString() + PATH_SEPARATOR + dir );
      if ( ItemTypeEnum.UNKNOWN == type )
      {
        LOG.finer( "Invoking createFolder(dir=" + dir + ",parentDir=" + parentDir + ")" );
        _soap.createFolder( dir, parentDir, new ArrayOfProperty() );
      }
      else if ( ItemTypeEnum.FOLDER != type )
      {
        final String s = "Path " + path + " exists and is not a folder but a " + type;
        throw new IllegalStateException( s );
      }
      else
      {
        final String message =
          "Skipping invocation of createFolder(dir=" + dir + ",parentDir=" + parentDir + ") as folder exists";
        LOG.finer( message );
      }
      path.append( PATH_SEPARATOR );
      path.append( dir );
    }
  }

  private String filenameFromPath( final String path )
  {
    final int index = path.lastIndexOf( PATH_SEPARATOR );
    if ( -1 == index )
    {
      return path;
    }
    else
    {
      return path.substring( index + 1 );
    }
  }

  private String dirname( final String path )
  {
    final int index = path.lastIndexOf( PATH_SEPARATOR );
    if ( -1 == index )
    {
      return "";
    }
    else
    {
      return path.substring( 0, index );
    }
  }

  /**
   * Return the fully qualified path for specified name.
   */
  private String toPhysicalFileName( final String name )
  {
    return nameComponent( _prefix ) + nameComponent( name );
  }

  private String nameComponent( final String name )
  {
    if ( 0 == name.length() || PATH_SEPARATOR.equals( name ) )
    {
      return "";
    }
    else if ( name.startsWith( PATH_SEPARATOR ) )
    {
      return name;
    }
    else
    {
      return PATH_SEPARATOR + name;
    }
  }

  private void logWarnings( final String message, final ArrayOfWarning warnings )
  {
    for ( final Warning warning : warnings.getWarning() )
    {
      warning( "Action '" + message + "' resulted in warning " +
               " Code=" + warning.getCode() +
               " ObjectName=" + warning.getObjectName() +
               " ObjectType=" + warning.getObjectType() +
               " Severity=" + warning.getSeverity() +
               " Message=" + warning.getMessage() );
    }
  }

  private byte[] readFully( final String name, final File file )
  {
    if ( !file.exists() )
    {
      final String message = "Report file " + file.getAbsolutePath() + " for " + name + " does not exist.";
      throw new IllegalStateException( message );
    }
    try
    {
      final byte[] bytes = new byte[ (int) file.length() ];
      new DataInputStream( new FileInputStream( file ) ).readFully( bytes );
      return bytes;
    }
    catch ( IOException e )
    {
      throw new IllegalStateException( "Unable to load report file " + file.getAbsolutePath() );
    }
  }

  private List<CatalogItem> listItems( final String path )
  {
    final String physicalName = toPhysicalFileName( path );
    LOG.finer( "Invoking listChildren(item=" + physicalName + ")" );
    final ArrayOfCatalogItem children = _soap.listChildren( physicalName, false );
    return children.getCatalogItem();
  }
}
