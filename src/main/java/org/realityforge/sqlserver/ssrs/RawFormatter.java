package org.realityforge.sqlserver.ssrs;

import java.util.logging.Formatter;
import java.util.logging.LogRecord;
import javax.annotation.Nonnull;

final class RawFormatter
  extends Formatter
{
  @Override
  public String format( @Nonnull final LogRecord logRecord )
  {
    return logRecord.getMessage() + "\n";
  }
}