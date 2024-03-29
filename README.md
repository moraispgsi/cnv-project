# cnv-project

#Organization of Files
## Modules

### Server
Contains the code of the server that is executed in the worker instances.

### MazeRunner
Contains the original code provided, the one that solves the Mazes

### inst
Contains BIT and an Instrumentation class responsible for instrumenting the MazeRunner module

### LoadBalancerAndAutoScaler
Contains the load balancer and autoscaler.

# AWS Isntance:
### Public DNS (IPv4)
`ec2-35-180-13-114.eu-west-3.compute.amazonaws.com`
### IPv4 Public IP
`35.180.13.114`

# To start the server locally:

`./gradlew server:run`

Request example:

XS  - http://127.0.0.1:8000/mzrun.html?m=Maze50.maze&x0=1&y0=1&x1=6&y1=6&v=75&s=bfs  
S   - http://127.0.0.1:8000/mzrun.html?m=Maze100.maze&x0=1&y0=1&x1=74&y1=95&v=40&s=bfs  
M   - http://127.0.0.1:8000/mzrun.html?m=Maze250.maze&x0=124&y0=125&x1=199&y1=201&v=75&s=bfs   
L   - http://127.0.0.1:8000/mzrun.html?m=Maze500.maze&x0=249&y0=250&x1=455&y1=488&v=57&s=astar  
XL  - http://127.0.0.1:8000/mzrun.html?m=Maze500.maze&x0=249&y0=250&x1=349&y1=348&v=100&s=dfs  
XXL - http://127.0.0.1:8000/mzrun.html?m=Maze1000.maze&x0=2&y0=2&x1=700&y1=600&v=100&s=dfs  


## To deploy to aws:

`./gradlew server:deploy`  
`./gradlew LoadBalancerAndAutoScaler:deploy`

Don't forget to start the instances prior to the deploy, or else... it will fail

### Contents of /etc/rc.local

```
app=server

alias start='cd /home/ec2-user/$app/bin && nohup ./$app  0<&- &> /home/ec2-user/$app.log &'
alias stop="pkill -f /bin/java" #could be better
alias status="tail -f /home/ec2-user/$app.log"
alias restartW="stop && sleep 2 && start"

restartW 
```

