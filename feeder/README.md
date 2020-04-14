# Feeder application

## Authors

Group T01

### Lead developer 

Alexandre Mota, 90585, Kolossive

### Contributors

Daniel Lopes, 90590, DanLopess

Duarte Matias, 90596, dsm43

## About

This is a CLI (Command-Line Interface) that can feed a Sentry server with observations.


## Instructions for using Maven

To compile and run using _exec_ plugin:

```
mvn compile exec:java
```

To generate launch scripts for Windows and Linux
(the POM is configured to attach appassembler:assemble to the _install_ phase):

```
mvn install
```

To run using appassembler plugin on Windows:

```
target\appassembler\bin\feeder arg0 arg1 arg2
```

To run using appassembler plugin on Linux:

```
./target/appassembler/bin/feeder arg0 arg1 arg2
```


## To configure the Maven project in Eclipse

'File', 'Import...', 'Maven'-'Existing Maven Projects'

'Select root directory' and 'Browse' to the project base folder.

Check that the desired POM is selected and 'Finish'.


----

