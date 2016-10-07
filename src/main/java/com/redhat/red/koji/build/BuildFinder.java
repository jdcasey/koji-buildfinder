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
import com.redhat.red.build.koji.model.xmlrpc.KojiBuildInfo;
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

import static org.apache.commons.lang.StringUtils.join;

/**
 * Iterate through file entries in a zip archive. For each that doesn't end in .md5 or .sha1, and that parses to a
 * Maven artifact reference (GAVTC), search for a Koji build that lists the artifact in its output.
 */
class BuildFinder
{
    private KojiClient client;

    private final Set<String> found = new HashSet<>();

    private final Set<String> missing = new HashSet<>();

    BuildFinder( KojiClient client )
    {
        this.client = client;
    }

    Set<String> findMissingBuilds( File zipFile, int skipParts )
            throws IOException, KojiClientException
    {
        ZipFile zf = new ZipFile( zipFile );
        Logger logger = LoggerFactory.getLogger( getClass() );
        final Set<String> missingBuilds = new HashSet<>();

        client.withKojiSession( ( session ) -> {
            zf.stream().parallel().filter( ( entry ) -> !entry.isDirectory() ).forEach( ( entry ) -> {
                String entryName = entry.getName();

                if ( entryName.endsWith( ".md5" ) || entryName.endsWith( ".sha1" ) || entryName.endsWith( "maven-metadata.xml" ) )
                {
                    logger.debug( "Skipping checksum file: {}", entryName );
                }
                else
                {
                    String[] parts = entryName.split( "/" );
                    if ( parts.length > skipParts )
                    {
                        String[] realParts = new String[parts.length - skipParts];
                        System.arraycopy( parts, skipParts, realParts, 0, realParts.length );
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
                                ArtifactRef aref = pathInfo.getArtifact();
                                logger.info( "??? {} (trimmed path: '{}', real path: '{}'", aref, path, entryName );
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
                                            missingBuilds.add( entryName );
                                        }
                                    }
                                    else
                                    {
                                        synchronized ( found )
                                        {
                                            found.add( path );
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
                                missingBuilds.add( entryName );
                            }
                        }
                    }
                }
            } );

            return null;
        } );

        return missingBuilds;
    }
}
