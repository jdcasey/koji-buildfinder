# Koji Build-Finder

This is a very small utility that iterates through all file entries in one or more zip archives, parsing each to find an Apache Maven artifact coordinate (GAVTC). For each one it finds, it ensures there is at least one Koji build that lists the file as output. It records paths that don't have corresponding builds, and reports them to a file. 

**NOTE:** Buildfinder expects the directory structure inside the zip archives to be formatted as a Maven repository, with no root directory.

## Usage

    $ java -jar koji-buildfinder-1.0-SNAPSHOT.jar -h
    Usage: $0 [OPTIONS] FILES
    
    
     ZIP_FILES           : Zip files to process
     -W (--write-config) : Write a new config file to the specified config location and exit. If it
                           already exists, back it up.
     -f (--config) FILE  : Configuration file to use (default: $HOME/.koji/buildfinder.conf)
     -h (--help)         : Print this help screen and exit
     -p (--skip) N       : Skip N prefix directories when parsing paths in the ZIPs
     -t (--threads) N    : Number of threads to use (default: 4)

## Getting Started

For your first run, you will probably want to allow buildfinder to write a starter configuration file, via the `-W` command-line option. Then, edit this file to supply URLs and SSL information (remember, relative file paths are interpreted based on the directory containing the configuration file).

After completing configuration, you can run with something like this:

    $ java -jar koji-buildfinder-1.0-SNAPSHOT.jar -p 1 /path/to/my-artifacts.zip

In this execution, Buildfinder will read through the `my-artifacts.zip` file, skipping the root directory of each path when parsing for Maven GAVTC. It will try to match each file entry against a build in Koji.

