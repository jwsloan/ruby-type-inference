require 'ostruct'

module ArgScanner
  OPTIONS = OpenStruct.new(
      :local_version => ENV['ARG_SCANNER_LOCAL_VERSION'] || '0',
      :no_local => ENV['ARG_SCANNER_NO_LOCAL'] ? true : false,
      :project_roots => ((ENV['ARG_SCANNER_PROJECT_ROOTS'] || "").split ':')
  )

  def OPTIONS.set_env
    ENV['ARG_SCANNER_LOCAL_VERSION'] = self.local_version.to_s
    ENV['ARG_SCANNER_NO_LOCAL'] = self.no_local ? "1" : nil
    ENV['ARG_SCANNER_PROJECT_ROOTS'] = self.project_roots.join ':'
  end
end
