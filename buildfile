require 'buildr/single_intermediate_layout'
require 'buildr/top_level_generate_dir'
require 'buildr/git_auto_version'
require 'buildr/gpg'
require 'buildr/wsgen'

desc 'SSRS API'
define 'ssrs' do
  project.group = 'org.realityforge.sqlserver.ssrs'
  compile.options.source = '1.8'
  compile.options.target = '1.8'
  compile.options.lint = 'all'

  project.version = ENV['PRODUCT_VERSION'] if ENV['PRODUCT_VERSION']

  pom.add_apache_v2_license
  pom.add_github_project('realityforge/ssrs-api')
  pom.add_developer('realityforge', 'Peter Donald')

  Buildr::Wsgen.wsdl2java(project,
                          {_('src/main/wsdl/ReportingService2005.wsdl') => {}},
                          :package => 'org.realityforge.sqlserver.ssrs.reportingservice2005')

  compile.with :getopt4j,
               :jsonb_api,
               :yasson,
               :javax_json,
               :javax_annotation

  project.doc.options.merge!('Xdoclint:none' => true)

  package(:jar)
  package(:jar, :classifier => 'all').tap do |jar|
    jar.with :manifest => { 'Main-Class' => 'org.realityforge.sqlserver.ssrs.Main' }
    jar.merge(artifact(:javax_annotation))
    jar.merge(artifact(:jsonb_api))
    jar.merge(artifact(:yasson))
    jar.merge(artifact(:javax_json))
    jar.merge(artifact(:getopt4j))
  end
  package(:sources)
  package(:javadoc)

  ipr.add_component_from_artifact(:idea_codestyle)
end
