VERSION_NUMBER = "1.0.0-SNAPSHOT"
GROUP = 'au.com.stocksoftware'

require "buildr/bnd"

repositories.remote << Buildr::Bnd.remote_repository
repositories.remote << 'http://www.ibiblio.org/maven2'

class CentralLayout < Layout::Default
  def initialize(key, top_level, use_subdir)
    super()
    prefix = top_level ? '' : '../'
    subdir = use_subdir ? "/#{key}" : ''
    self[:target] = "#{prefix}target#{subdir}"
    self[:target, :main] = "#{prefix}target#{subdir}"
    self[:reports] = "#{prefix}reports#{subdir}"
  end
end

def define_with_central_layout(name, top_level = false, use_subdir = true, & block)
  define(name, :layout => CentralLayout.new(name, top_level, use_subdir), & block)
end

def wsimport(project, wsdl_file, endpoint_url_spec, package_name)
  dir = project._(:target, :generated, :main, :java)
  # A file we know will exist when generator runs
  package_info_filename = "#{dir}/#{package_name.gsub('.', '/')}/package-info.java"
  directory(dir)
  file(package_info_filename => [dir, wsdl_file]) do
    system "wsimport",
           wsdl_file,
           "-quiet",
           "-Xnocompile",
           "-wsdllocation",
           "http://example.org/#{endpoint_url_spec}",
           "-keep",
           "-s",
           dir,
           "-p",
           package_name
  end
  project.compile.prerequisites << package_info_filename
  project.compile.from dir
  file(package_info_filename)
end


desc 'SSRS API'
define_with_central_layout('ssrs', true) do
  project.version = VERSION_NUMBER
  project.group = GROUP
  compile.options.source = '1.6'
  compile.options.target = '1.6'
  compile.options.lint = 'all'

  desc "SSRS API: Report Service 2005"
  define_with_central_layout "report-service-2005" do
    wsimport(project,
             _('../src/main/wsdl/ReportService2005.wsdl'),
             "Server/ReportService2005.asmx",
             "com.microsoft.sqlserver.ssrs.reportservice2005")

    package(:bundle).tap do |bnd|
      bnd['Export-Package'] = "com.microsoft.sqlserver.ssrs.reportservice2005.*;version=#{version}"
    end
  end

  desc "SSRS API:  Report Execution 2005"
  define_with_central_layout "report-execution-2005" do
    gen_task = wsimport(project,
                        _('../src/main/wsdl/ReportExecution2005.wsdl'),
                        "Server/ReportExecution2005.asmx",
                        "com.microsoft.sqlserver.ssrs.reportexecution2005")
   
    task 'post-process-source' => [gen_task] do
      filename = project._(:target, :generated, :main, :java, "com/microsoft/sqlserver/ssrs/reportexecution2005/ExecutionHeader.java")
      mv filename, "#{filename}.bak" 
      File.open(filename,'w+') do |output_file|
        output_file.puts File.read("#{filename}.bak").gsub(/public class ExecutionHeader/, "@javax.xml.bind.annotation.XmlRootElement(name=\"ExecutionHeader\")  public class ExecutionHeader")
      end
	  rm "#{filename}.bak"
    end

    compile.prerequisites << 'post-process-source'

    package(:bundle).tap do |bnd|
      bnd['Export-Package'] = "com.microsoft.sqlserver.ssrs.reportexecution2005.*;version=#{version}"
    end
  end

  #TODO: Need to add ReportService2010 when we move to SQL Server 2008 R2
end

