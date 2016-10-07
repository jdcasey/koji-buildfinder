package com.redhat.rcm.koji.build;

import org.apache.commons.io.IOUtils;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.ParserProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Paths;
import java.util.List;

/**
 * Created by jdcasey on 10/7/16.
 */
public class Options
{

    private static final File DEFAULT_CONFIG_FILE =
            Paths.get( System.getProperty( "user.home" ), ".koji", "buildfinder.conf" ).toFile();

    private static final int DEFAULT_THREADS = 4;

    private static final String DEFAULT_CONFIG_RESOURCE = "default.conf";

    @Option( name = "-h", aliases = { "--help" }, help = true, usage = "Print this help screen and exit" )
    private boolean help;

    @Option( name = "-W", aliases = { "--write-config" }, usage = "Write a new config file to the specified config location and exit. If it already exists, back it up." )
    private boolean writeConfig;

    @Option( name = "-f", aliases = { "--config" }, usage = "Configuration file to use (default: $HOME/.koji/buildfinder.conf)" )
    private File configFile;

    @Argument( multiValued = true, metaVar = "ZIP_FILES", usage = "Zip files to process" )
    private List<String> files;

    @Option( name = "-t", aliases = { "--threads" }, usage = "Number of threads to use (default: 4)" )
    private int threads;

    public boolean parseArgs( final String[] args )
            throws CmdLineException
    {
        final int cols = ( System.getenv( "COLUMNS" ) == null ? 100 : Integer.valueOf( System.getenv( "COLUMNS" ) ) );
        final ParserProperties props = ParserProperties.defaults()
                                                       .withUsageWidth( cols );

        final CmdLineParser parser = new CmdLineParser( this, props );
        boolean canStart = true;
        parser.parseArgument( args );

        if ( isHelp() )
        {
            printUsage( parser, null );
            canStart = false;
        }

        if ( isWriteConfig() )
        {
            File config = getConfigFile();

            Logger logger = LoggerFactory.getLogger( getClass() );
            logger.info( "Writing default configuration file to: {}", config );

            if ( config.isDirectory() )
            {
                config = new File( config, "buildfinder.conf" );
            }

            if ( config.exists() )
            {
                File backup = new File( config.getPath() + ".bak" );

                logger.info( "Backing up existing configuration to: {}", backup );
                config.renameTo( backup );
            }

            if ( config.getParentFile() != null )
            {
                config.getParentFile().mkdirs();
            }

            try(InputStream in=Thread.currentThread().getContextClassLoader().getResourceAsStream( DEFAULT_CONFIG_RESOURCE );
                OutputStream out= new FileOutputStream( config ) )
            {
                if ( in == null )
                {
                    logger.error( "Cannot find default configuration in the classpath: {}", DEFAULT_CONFIG_FILE );
                }
                else
                {
                    IOUtils.copy( in, out );
                }
            }
            catch ( IOException e )
            {
                logger.error( "Failed to write config file: " + config, e );
            }
            finally
            {
                canStart = false;
            }
        }

        return canStart;
    }

    public static void printUsage( final CmdLineParser parser, final CmdLineException error )
    {
        if ( error != null )
        {
            System.err.println( "Invalid option(s): " + error.getMessage() );
            System.err.println();
        }

        System.err.println( "Usage: $0 [OPTIONS] FILES" );
        System.err.println();
        System.err.println();
        parser.printUsage( System.err );
        System.err.println();
    }

    public boolean isHelp()
    {
        return help;
    }

    public void setHelp( final boolean help )
    {
        this.help = help;
    }

    public List<String> getFiles()
    {
        return files;
    }

    public void setFiles( List<String> files )
    {
        this.files = files;
    }

    public File getConfigFile()
    {
        return configFile == null ? DEFAULT_CONFIG_FILE : configFile;
    }

    public void setConfigFile( File configFile )
    {
        this.configFile = configFile;
    }

    public int getThreads()
    {
        return threads < 1 ? DEFAULT_THREADS: threads;
    }

    public void setThreads( int threads )
    {
        this.threads = threads;
    }

    public boolean isWriteConfig()
    {
        return writeConfig;
    }

    public void setWriteConfig( boolean writeConfig )
    {
        this.writeConfig = writeConfig;
    }
}
