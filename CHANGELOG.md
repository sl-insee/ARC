# Change Log

All notable changes to this project will be documented in this file.

## [Unreleased]

## version-91.1.1 - 2021-09-03
- database and application version now directly picked from git metadata
- tabs in gui are now persitant
- new maintenance menu to set parameters and test loggers
- bugfixes in gui

## version-90.1.1 - 2021-08-02

- fixes bug preventing writing log to file (web-service)
- adds new property fr.insee.arc.log.level to configure ARC log level

## version-90.1 - 2021-06-29

- allows logging to file through property
- extends expressions to control rules
- fixes multithread bug on SimpleDateFormat causing NPE in mapping
- adds /healthcheck to webservice
- improves error message on key-value loading failing
- adds clarifying report to files marked KO because one other file in the archive could not be read

