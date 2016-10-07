/**
 * Copyright (C) 2016 Red Hat, Inc. (jcasey@redhat.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.redhat.red.koji.build;

import com.redhat.red.build.koji.KojiClient;
import com.redhat.red.build.koji.KojiClientException;
import org.apache.commons.io.IOUtils;
import org.commonjava.rwx.binding.error.BindException;
import org.commonjava.util.jhttpc.auth.MemoryPasswordManager;
import org.commonjava.util.jhttpc.auth.PasswordManager;
import org.commonjava.web.config.ConfigurationException;
import org.kohsuke.args4j.CmdLineException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.apache.commons.lang.StringUtils.join;
import static org.commonjava.util.jhttpc.auth.PasswordType.KEY;
import static org.commonjava.util.jhttpc.auth.PasswordType.PROXY;

/**
 * Initialize Koji client, then orchestrate process of matching files contained in specified zip archives to Koji builds.
 */
public class Main
{
    private static final String LINE =
            "----------------------------------------------------------------------------------------";

    private static final java.lang.String NO_EXIT = "no-exit";

    private static final int PARSE_ERROR = -1;

    private static final int INVALID_CONFIG = -2;

    private static final int INIT_ERROR = -3;

    private static final int INVALID_ZIP = -4;

    private static final int KOJI_LOGIN_ERROR = -5;

    private static final int REPORT_ERROR = -6;

    public static void main( String[] args )
    {
        String noExit = System.getProperty( NO_EXIT );
        boolean exit = noExit == null || !Boolean.parseBoolean( noExit );
        Integer result = null;

        Options opts = new Options();
        try
        {
            if ( !opts.parseArgs( args ) )
            {
                result = 0;
            }
        }
        catch ( CmdLineException e )
        {
            e.printStackTrace();
            result = PARSE_ERROR;
        }

        if ( result == null )
        {
            result = new Main( opts ).run();
        }

        if ( exit )
        {
            System.exit( result );
        }
    }

    private Options opts;

    private Config config;

    private KojiClient client;

    private ExecutorService executorService;

    private BuildFinder buildFinder;

    private Map<String, Set<String>> allMissing;

    private File reportFile;

    private Integer result;

    Main( Options opts )
    {

        this.opts = opts;
    }

    Integer run()
    {
        Logger logger = LoggerFactory.getLogger( getClass() );
        if ( !configure() )
        {
            logger.error( "Configuration failed" );
            return result;
        }

        try
        {
            if ( !wire() )
            {
                logger.error( "Application init failed" );
                return result;
            }

            allMissing = new HashMap<>();
            for ( String inFile : opts.getFiles() )
            {
                logger.info( "Processing: {}", inFile );
                File in = new File( inFile );
                if ( in.exists() && !in.isDirectory() )
                {
                    try
                    {
                        Set<String> missing = buildFinder.findMissingBuilds( in, opts.getSkipParts() );
                        if ( missing != null && !missing.isEmpty() )
                        {
                            logger.info( "Adding {} missing builds from: {}", missing.size(), in );
                            allMissing.put( inFile, missing );
                        }
                    }
                    catch ( IOException e )
                    {
                        logger.error( "Invalid input ZIP file: " + in, e );
                        result = INVALID_ZIP;
                        break;
                    }
                    catch ( KojiClientException e )
                    {
                        logger.error( "Failed to login to Koji at: " + config.getUrl(), e );
                        result = KOJI_LOGIN_ERROR;
                        break;
                    }
                }
                else
                {
                    logger.warn( "Cannot find ZIP archive at: {}", inFile );
                }
            }

            if ( result == null )
            {
                logger.info( "Reporting results..." );
                try
                {
                    report();
                    logger.info( "Results are in: {}", reportFile );
                }
                catch ( IOException e )
                {
                    logger.error( "Failed to write report to: " + reportFile, e );
                    result = REPORT_ERROR;
                }
            }

            if ( result == null )
            {
                result = 0;
            }

            logger.info( "Returning exit value: {}", result );
            return result;
        }
        finally
        {
            shutdown();
        }
    }

    void shutdown()
    {
        if ( client != null )
        {
            IOUtils.closeQuietly( client );
        }

        if ( executorService != null )
        {
            executorService.shutdown();
        }
    }

    void report()
            throws IOException
    {
        if ( !allMissing.isEmpty() )
        {
            reportFile = new File( "buildfinder.out.txt" );
            try (PrintWriter pw = new PrintWriter( new FileWriter( reportFile ) ))
            {
                allMissing.forEach( ( file, missing ) -> pw.write(
                        String.format( "%s:\n%s\n  %s\n\n", file, LINE, join( missing, "\n  " ) ) ) );
            }
        }
        else
        {
            Logger logger = LoggerFactory.getLogger( getClass() );
            logger.info( "Nothing to report! All files have a corresponding Koji build!" );
        }
    }

    boolean wire()
    {
        PasswordManager passwordManager = new MemoryPasswordManager();
        passwordManager.bind( config.getKeyPassword(), config.getKojiSiteId(), KEY );
        if ( config.getProxyPassword() != null )
        {
            passwordManager.bind( config.getProxyPassword(), config.getKojiSiteId(), PROXY );
        }

        executorService = Executors.newFixedThreadPool( opts.getThreads() );

        try
        {
            client = new KojiClient( config, passwordManager, executorService );
        }
        catch ( BindException e )
        {
            Logger logger = LoggerFactory.getLogger( getClass() );
            logger.error( "Failed to initialize Koji client.", e );
            result = INIT_ERROR;
        }

        buildFinder = new BuildFinder( client );

        return result == null;
    }

    boolean configure()
    {
        Logger logger = LoggerFactory.getLogger( getClass() );

        this.config = new Config();
        File configFile = opts.getConfigFile();
        if ( configFile != null && configFile.exists() )
        {
            try
            {
                logger.debug( "Loading configuration file from: {}", configFile );

                config.load( configFile );
            }
            catch ( IOException | ConfigurationException e )
            {
                logger.error( "Failed to read configuration file: " + configFile, e );
                result = INVALID_CONFIG;
            }
        }

        return result == null;
    }

    Integer getResult()
    {
        return result;
    }
}
