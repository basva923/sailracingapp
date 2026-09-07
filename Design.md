# Sail Racing App

This app is designed to help sailors during the race. Let's explain the features of the app while doing the race.


Before the race starts, we need to input the starting line coordinates. For this we can sail to the pin end and boat end of the line and mark the places using the app. The app can store these coordinates in persistent storage for later use. Then it is the goal to set the wind direction. This can be done in several ways:
- You should be able to input the wind direction manually.
- You should be able to input the tack angle (the angle between the wind and the highest upwind sailing direction).
- You should be able to sail over port or stardboard tack and when pressing a button, the app should calculate the wind direction based on the tack angle and the boat's heading.
Then it is the goal to create histogram of the wind direction over time. The wind direction changes always a bit so it is important to know the probabilty of the wind direction to choose the best strategy. When sailing close to upwind 90° to O° against the configured wind direction, the ap should asume you are sailing upwind and should store the wind direction in a histogram. The app should also create a notation of the avg upwind speed of the boat. This is important to calulate the time to the start and tuning of the sails.

With the wind direction and start line set, we are ready to start the race. During this time, the app should provide a countdown. It should be very easy to:
- Start the countdown at 5 minute, 4 minutes and 1 minute.
- Sync the timer to the closest minute (in case you presses the start button a bit late).
- Stop the timer

With beeps and visulal cues the app should notify on the countdown. Beep if a minute passes and beep every 10s for the last minutes. And every second for the last 10 seconds. And fast beeps for the start. During the start it should always be very clear what the "time to kill is". This is the time you need to wait before you can start sailing full speed to the line and cross it right on time. This number is positive if you are early and negative if you are late. The app should also provide a visual cue of the time to kill. This can be done by showing a red or green color depending on the time to kill. The app should also provide the distance to the start line.


After the start, it is all about tactics and boat speed. The app should provide a visual cue of the wind direction and the best upwind sailing direction. It should also provide a visual cue of the boat speed and the average upwind speed. This for upwind and downwind sailing. 


The app should be android native and should be built using Kotlin. This should be user friendly and battery perforance should be kept in mind from the design (e.g. gps and amoled optimization). Keep in mind the app will be used on a boat and everyone (also from the back) should be able to read the screen.