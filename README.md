# WordQuizzle
This is a project done for the course "Operative systems and laboratory" during my bachelor in Computer Science at the university of Pisa. 
The project consists in creating a client/server architecure for a simple word game: a player challenges another in translating some word from italian to english.  
The interaction is done by console with the available representing the actions of registering, loggin in/out, add a friend, get leaderboard, etc.  
A long description, in italian, can be found in "info (italian).pdf".
## Build and use
The project needs at least java 11 to compile and run.
A standard use would need the start of the server (server/Main.java) and the run of HumanClient which will prompt all the commands available.  
By default the server and client are supposed to be in the same machine as the client will try to access the callback address. It can be easly fixed by changing the value in commons/Constants.java .  
The project has been tested on Linux and it is possible that it will not work on different platforms if they give a different callback address for TCP and UDP communications.
