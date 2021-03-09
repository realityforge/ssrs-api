package org.realityforge.sqlserver.ssrs;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

final class Uploader
{
  @Nonnull
  private final SSRS _ssrs;

  Uploader( @Nonnull final String reportTarget,
            @Nonnull final String uploadPrefix,
            @Nullable final String domain,
            @Nullable final String username,
            @Nullable final String password )
    throws MalformedURLException
  {
    if ( null != username )
    {
      NTLMAuthenticator.install( Objects.requireNonNull( domain ),
                                 Objects.requireNonNull( username ),
                                 Objects.requireNonNull( password ) );
    }

    _ssrs = new SSRS( new URL( Objects.requireNonNull( reportTarget ) + "/ReportService2005.asmx" ),
                      Objects.requireNonNull( uploadPrefix ) );
  }

  void uploadReports( @Nonnull final Report[] reports )
  {
    deleteReports( reports );
    for ( final Report report : reports )
    {
      final String lastPartRemoved = report.name.replace( "/[^/]*$", "" );
      if ( !lastPartRemoved.equals( report.name ) )
      {
        _ssrs.mkdir( lastPartRemoved );
      }
      _ssrs.createReport( report.name, report.filename );
    }
  }

  void deleteReports( @Nonnull final Report[] reports )
  {
    extractTopLevelDirectories( reports ).forEach( _ssrs::delete );
  }

  void uploadDataSources( @Nonnull final DataSource[] dataSources )
  {
    for ( final DataSource dataSource : dataSources )
    {
      createParentDirectory( dataSource.name );
      _ssrs.delete( dataSource.name );
      _ssrs.createSQLDataSource( dataSource.name, dataSource.connectionString );
    }
  }

  void deleteDataSources( @Nonnull final DataSource[] dataSources )
  {
    for ( final DataSource dataSource : dataSources )
    {
      _ssrs.delete( dataSource.name );
    }
  }

  @Nonnull
  private List<String> extractTopLevelDirectories( @Nonnull final Report[] reports )
  {
    return Stream
      .of( reports )
      .map( r -> {
        final String[] parts = r.name.split( "/" );
        return parts.length > 1 ? parts[ 0 ] : null;
      } )
      .filter( Objects::nonNull )
      .distinct()
      .sorted()
      .collect( Collectors.toList() );
  }
}
