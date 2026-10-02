on run {daemon_file, agent_file, user, cur_pid, source_dir}

  set unload_service to "launchctl unload -w /Library/LaunchDaemons/{APP_FULL_NAME}_service.plist || true;"

  set kill_others to "pgrep -x '{APP_EXECUTABLE}' | grep -v " & cur_pid & " | xargs kill -9;"

  set copy_files to "rm -rf /Applications/{APP_NAME}.app && cp -r " & source_dir & " /Applications && chown -R " & quoted form of user & ":staff /Applications/{APP_NAME}.app;"

  set sh1 to "echo " & quoted form of daemon_file & " > /Library/LaunchDaemons/{APP_FULL_NAME}_service.plist && chown root:wheel /Library/LaunchDaemons/{APP_FULL_NAME}_service.plist;"

  set sh2 to "echo " & quoted form of agent_file & " > /Library/LaunchAgents/{APP_FULL_NAME}_server.plist && chown root:wheel /Library/LaunchAgents/{APP_FULL_NAME}_server.plist;"

  set sh3 to "launchctl load -w /Library/LaunchDaemons/{APP_FULL_NAME}_service.plist;"

  set sh to unload_service & kill_others & copy_files & sh1 & sh2 & sh3

  do shell script sh with prompt "{APP_NAME} wants to update itself" with administrator privileges
end run
