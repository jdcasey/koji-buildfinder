package com.redhat.rcm.koji.build;

import com.redhat.red.build.koji.KojiClient;
import com.redhat.red.build.koji.KojiClientException;
import com.redhat.red.build.koji.model.xmlrpc.KojiBuildInfo;
import com.redhat.red.build.koji.model.xmlrpc.KojiSessionInfo;
import org.commonjava.maven.atlas.ident.ref.ArtifactRef;
import org.commonjava.maven.atlas.ident.util.ArtifactPathInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipFile;

import static org.apache.commons.lang.StringUtils.join;

/**
 * Created by jdcasey on 10/7/16.
 */
public class BuildFinder
{
    private KojiClient client;

    private Set<String> found = new HashSet<>();

    private Set<String> missing = new HashSet<>();

    public BuildFinder( KojiClient client )
    {
        this.client = client;
    }

    public Set<String> findMissingBuilds( File zipFile, int skipParts )
            throws IOException, KojiClientException
    {
        ZipFile zf = new ZipFile( zipFile );
        Logger logger = LoggerFactory.getLogger( getClass() );
        Set<String> missingBuilds = new HashSet<>();

        client.withKojiSession( (session)->{
            zf.stream().parallel().filter((entry)->!entry.isDirectory()).forEach( (entry)->{
                String[] parts = entry.getName().split( "/" );
                if ( parts.length > skipParts )
                {
                    String[] realParts = new String[parts.length - skipParts];
                    System.arraycopy( parts, skipParts, realParts,0, realParts.length );
                    String path = join( realParts, "/" );

                    Boolean foundT_missingF = null;
                    synchronized ( missing )
                    {
                        if ( missing.contains( path ) )
                        {
                            foundT_missingF = false;
                        }
                    }

                    synchronized ( found )
                    {
                        if ( found.contains( path ) )
                        {
                            foundT_missingF = true;
                        }
                    }

                    if ( foundT_missingF == null )
                    {
                        ArtifactPathInfo pathInfo = ArtifactPathInfo.parse( path );
                        if ( pathInfo != null )
                        {
                            ArtifactRef aref= pathInfo.getArtifact();
                            logger.info( "??? {} (trimmed path: '{}', real path: '{}'", aref, path, entry.getName() );
                            try
                            {
                                List<KojiBuildInfo> builds = client.listBuildsContaining( aref, session );
                                if ( builds == null || builds.isEmpty() )
                                {
                                    synchronized ( missing )
                                    {
                                        missing.add( path );
                                    }

                                    synchronized ( missingBuilds )
                                    {
                                        missingBuilds.add( entry.getName() );
                                    }
                                }
                            }
                            catch ( KojiClientException e )
                            {
                                logger.error( "Failed to query koji for artifact: " + aref, e );
                            }
                        }
                    }
                    else if ( !foundT_missingF )
                    {
                        synchronized ( missingBuilds )
                        {
                            missingBuilds.add( entry.getName() );
                        }
                    }
                }
            } );

            return null;
        } );

        return missingBuilds;
    }
}
