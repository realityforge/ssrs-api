require 'buildr/single_intermediate_layout'
require 'buildr/top_level_generate_dir'
require 'buildr/git_auto_version'
require 'buildr/gpg'
require 'buildr/wsgen'

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

  Buildr::Wsgen.wsdl2java(project,
                          {_('src/main/wsdl/ReportingService2005.wsdl') => {}},
                          :package => 'org.realityforge.sqlserver.ssrs.reportingservice2005')

  package(:jar)
  package(:sources)
  package(:javadoc)
end

