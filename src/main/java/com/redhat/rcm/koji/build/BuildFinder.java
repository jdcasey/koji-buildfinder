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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipFile;

/**
 * Created by jdcasey on 10/7/16.
 */
public class BuildFinder
{
    private KojiClient client;

    public BuildFinder( KojiClient client )
    {
        this.client = client;
    }

    public Set<String> findMissingBuilds( File zipFile )
            throws IOException, KojiClientException
    {
        ZipFile zf = new ZipFile( zipFile );
        Logger logger = LoggerFactory.getLogger( getClass() );
        Set<String> missingBuilds = new HashSet<>();

        client.withKojiSession( (session)->{
            zf.stream().parallel().filter((entry)->!entry.isDirectory()).forEach( (entry)->{
                ArtifactPathInfo pathInfo = ArtifactPathInfo.parse( entry.getName() );
                if ( pathInfo != null )
                {
                    ArtifactRef aref= pathInfo.getArtifact();
                    logger.info( "??? {}", aref );
                    try
                    {
                        List<KojiBuildInfo> builds = client.listBuildsContaining( aref, session );
                        if ( builds == null || builds.isEmpty() )
                        {
                            missingBuilds.add( entry.getName() );
                        }
                    }
                    catch ( KojiClientException e )
                    {
                        logger.error( "Failed to query koji for artifact: " + aref, e );
                    }
                }
            } );

            return null;
        } );

        return missingBuilds;
    }
}
