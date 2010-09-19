VERSION_NUMBER = "1.0.0-SNAPSHOT"
GROUP = 'au.com.stocksoftware'

require 'buildr_bnd'

repositories.remote << Buildr::Bnd.remote_repository

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
    sh "wsimport",
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
end


desc 'SSRS API'
define_with_central_layout('ssrs', true) do
  project.version = VERSION_NUMBER
  project.group = GROUP
  compile.options.source = '1.6'
  compile.options.target = '1.6'
  compile.options.lint = 'all'

  desc "SSRS API: ReportingServices 2005"
  define_with_central_layout "reportingservices-2005" do
    wsimport(project,
             _('../src/main/wsdl/ReportService2005.wsdl'),
             "Server/ReportService2005.asmx",
             "com.microsoft.sqlserver.ssrs.reportingservices_2005")

    package(:bundle).tap do |bnd|
      bnd['Export-Package'] = "com.microsoft.sqlserver.ssrs.reportingservices_2005.*;version=#{version}"
    end
  end

  #TODO: Add ReportExecution2005
  #TODO: Need to add ReportService2010 when we move to SQL Server 2008 R2
end
