require 'buildr/single_intermediate_layout'
require 'buildr/top_level_generate_dir'
require 'buildr/git_auto_version'
require 'buildr/bnd'

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
define 'ssrs' do
  project.group = 'org.realityforge.ssrs'
  compile.options.source = '1.7'
  compile.options.target = '1.7'
  compile.options.lint = 'all'

  desc 'SSRS API: Report Service 2005'
  define 'report-service-2005' do
    project.no_iml
    wsimport(project,
             _('../src/main/wsdl/ReportService2005.wsdl'),
             'Server/ReportService2005.asmx',
             'com.microsoft.sqlserver.ssrs.reportservice2005')

    package(:jar)
    package(:sources)
  end

  desc 'SSRS API:  Report Execution 2005'
  define 'report-execution-2005' do
    project.no_iml
    gen_task = wsimport(project,
                        _('../src/main/wsdl/ReportExecution2005.wsdl'),
                        'Server/ReportExecution2005.asmx',
                        'com.microsoft.sqlserver.ssrs.reportexecution2005')
   
    task 'post-process-source' => [gen_task] do
      filename = project._(:target, :generated, :main, :java, 'com/microsoft/sqlserver/ssrs/reportexecution2005/ExecutionHeader.java')
      mv filename, "#{filename}.bak" 
      File.open(filename,'w+') do |output_file|
        output_file.puts File.read("#{filename}.bak").gsub(/public class ExecutionHeader/, '@javax.xml.bind.annotation.XmlRootElement(name="ExecutionHeader")  public class ExecutionHeader')
      end
	  rm "#{filename}.bak"
    end

    compile.prerequisites << 'post-process-source'

    package(:jar)
    package(:sources)
  end
end

