set sh1 to "launchctl unload -w /Library/LaunchDaemons/{APP_FULL_NAME}_service.plist;"
set sh2 to "/bin/rm /Library/LaunchDaemons/{APP_FULL_NAME}_service.plist;"
set sh3 to "/bin/rm /Library/LaunchAgents/{APP_FULL_NAME}_server.plist;"

set sh to sh1 & sh2 & sh3
do shell script sh with prompt "{APP_NAME} wants to unload daemon" with administrator privileges