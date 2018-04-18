# cnv-project

### To start the server locally:

`./gradlew server:run`

Request example:

http://localhost:8000/mzrun.html?m=Maze100.maze&x0=3&y0=9&x1=78&y1=89&v=50&s=astar

### To deploy to aws:

`./gradlew server:deploy`


#### /etc/rc.local

```
app=server

alias start='cd /home/ec2-user/$app/bin && nohup ./$app  0<&- &> /home/ec2-user/$app.log &'
alias stop="pkill -f /bin/java" #could be better
alias status="tail -f /home/ec2-user/$app.log"
alias restartW="stop && sleep 2 && start"

restartW 
```

