on run {daemon_file, agent_file, user}

  set sh1 to "echo " & quoted form of daemon_file & " > /Library/LaunchDaemons/{APP_FULL_NAME}_service.plist && chown root:wheel /Library/LaunchDaemons/{APP_FULL_NAME}_service.plist;"

  set sh2 to "echo " & quoted form of agent_file & " > /Library/LaunchAgents/{APP_FULL_NAME}_server.plist && chown root:wheel /Library/LaunchAgents/{APP_FULL_NAME}_server.plist;"

  set sh3 to "cp -rf /Users/" & user & "/Library/Preferences/{APP_FULL_NAME}/{APP_NAME}.toml /var/root/Library/Preferences/{APP_FULL_NAME}/;"

  set sh4 to "cp -rf /Users/" & user & "/Library/Preferences/{APP_FULL_NAME}/{APP_NAME}2.toml /var/root/Library/Preferences/{APP_FULL_NAME}/;"

  set sh5 to "launchctl load -w /Library/LaunchDaemons/{APP_FULL_NAME}_service.plist;"

  set sh to sh1 & sh2 & sh3 & sh4 & sh5

  do shell script sh with prompt "{APP_NAME} wants to install daemon and agent" with administrator privileges
end run
