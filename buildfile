require 'buildr/single_intermediate_layout'
require 'buildr/top_level_generate_dir'
require 'buildr/git_auto_version'
require 'buildr/gpg'
require 'buildr/custom_pom'

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
  project.group = 'org.realityforge.sqlserver.ssrs'
  compile.options.source = '1.7'
  compile.options.target = '1.7'
  compile.options.lint = 'all'

  project.version = ENV['PRODUCT_VERSION'] if ENV['PRODUCT_VERSION']

  pom.add_apache_v2_license
  pom.add_github_project('realityforge/ssrs-api')
  pom.add_developer('realityforge', 'Peter Donald', 'peter@realityforge.org', ['Developer'])

  wsimport(project,
           _('src/main/wsdl/ReportService2005.wsdl'),
           'Server/ReportService2005.asmx',
           'org.realityforge.sqlserver.ssrs.reportservice2005')

  package(:jar)
  package(:sources)
end

