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

import com.redhat.red.build.koji.config.KojiConfig;
import org.commonjava.util.jhttpc.model.SiteConfig;
import org.commonjava.util.jhttpc.model.SiteConfigBuilder;
import org.commonjava.util.jhttpc.model.SiteTrustType;
import org.commonjava.web.config.ConfigurationException;
import org.commonjava.web.config.annotation.ConfigName;
import org.commonjava.web.config.annotation.SectionName;
import org.commonjava.web.config.dotconf.DotConfConfigurationReader;
import org.commonjava.web.config.section.ConfigurationSectionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.apache.commons.io.FileUtils.readFileToString;
import static org.commonjava.util.jhttpc.model.SiteConfig.DEFAULT_PROXY_PORT;
import static org.commonjava.util.jhttpc.model.SiteConfig.DEFAULT_REQUEST_TIMEOUT_SECONDS;

/**
 * Contains configuration options (plus a method for reading these from a .conf file) pertaining to the Koji client
 * connection.
 */
@SectionName( ConfigurationSectionListener.DEFAULT_SECTION )
public class Config
        implements KojiConfig
{

    private static final String KOJI_SITE_ID = "koji";

    public static final int DEFAULT_MAX_CONNECTIONS = 4;

    private String url;

    private String clientPemPath;

    private String serverPemPath;

    private String keyPassword;

    private Integer maxConnections;

    private String proxyHost;

    private Integer proxyPort;

    private String proxyUser;

    private Integer requestTimeoutSeconds;

    private String siteTrustType;

    private String proxyPassword;

    private String storageRootUrl;

    private File configDir;

    public void load( File configFile )
            throws IOException, ConfigurationException
    {
        if ( configFile == null || !configFile.exists() || configFile.isDirectory() )
        {
            throw new ConfigurationException( "You must specify a valid file for reading configuration (file was: %s)",
                                              configFile );
        }

        configDir = configFile.getAbsoluteFile().getParentFile();

        try (InputStream stream = new FileInputStream( configFile ))
        {
            new DotConfConfigurationReader( this ).loadConfiguration( stream );
        }
    }

    @Override
    public SiteConfig getKojiSiteConfig()
            throws IOException
    {
        return new SiteConfigBuilder().withId( getKojiSiteId() )
                                      .withKeyCertPem( getClientPemContent() )
                                      .withServerCertPem( getServerPemContent() )
                                      .withUri( getKojiURL() )
                                      .withMaxConnections( getMaxConnections() )
                                      .withProxyHost( getProxyHost() )
                                      .withProxyPort( getProxyPort() )
                                      .withProxyUser( getProxyUser() )
                                      .withRequestTimeoutSeconds( getRequestTimeoutSeconds() )
                                      .withTrustType( SiteTrustType.getType( getSiteTrustType() ) )
                                      .build();
    }

    public String getServerPemContent()
            throws IOException
    {
        return readPemContent( getPemPath( getServerPemPath() ) );
    }

    private String getPemPath( String pemPath )
    {
        File f = new File( pemPath );
        if ( !f.isAbsolute() )
        {
            f = new File( configDir, pemPath );
            return f.getAbsolutePath();
        }

        return pemPath;
    }

    public String getClientPemContent()
            throws IOException
    {
        return readPemContent( getPemPath( getClientPemPath() ) );
    }

    private String readPemContent( String pemPath )
            throws IOException
    {
        Logger logger = LoggerFactory.getLogger( getClass() );
        logger.trace( "Reading PEM content from path: '{}'", pemPath );

        if ( pemPath == null )
        {
            return null;
        }

        File f = new File( pemPath );
        if ( !f.exists() || f.isDirectory() )
        {
            return null;
        }

        String pem = readFileToString( f );

        logger.trace( "Got PEM content:\n\n{}\n\n", pem );

        return pem;
    }

    @Override
    public String getKojiURL()
    {
        return getUrl();
    }

    @Override
    public String getKojiClientCertificatePassword()
    {
        return keyPassword;
    }

    @Override
    public String getKojiSiteId()
    {
        return KOJI_SITE_ID;
    }

    public Integer getMaxConnections()
    {
        return maxConnections == null ? DEFAULT_MAX_CONNECTIONS : maxConnections;
    }

    public String getServerPemPath()
    {
        return serverPemPath;
    }

    public String getClientPemPath()
    {
        return clientPemPath;
    }

    public String getProxyHost()
    {
        return proxyHost;
    }

    public Integer getProxyPort()
    {
        return proxyPort == null ? DEFAULT_PROXY_PORT : proxyPort;
    }

    public String getProxyUser()
    {
        return proxyUser;
    }

    public Integer getRequestTimeoutSeconds()
    {
        return requestTimeoutSeconds == null ? DEFAULT_REQUEST_TIMEOUT_SECONDS : requestTimeoutSeconds;
    }

    public String getSiteTrustType()
    {
        return siteTrustType;
    }

    public String getUrl()
    {
        return url;
    }

    public String getKeyPassword()
    {
        return keyPassword;
    }

    public String getProxyPassword()
    {
        return proxyPassword;
    }

    public String getStorageRootUrl()
    {
        return storageRootUrl;
    }

    @ConfigName( "url" )
    public void setUrl( String url )
    {
        this.url = url;
    }

    @ConfigName( "client.pem.path" )
    public void setClientPemPath( String clientPemPath )
    {
        this.clientPemPath = clientPemPath;
    }

    @ConfigName( "server.pem.path" )
    public void setServerPemPath( String serverPemPath )
    {
        this.serverPemPath = serverPemPath;
    }

    @ConfigName( "client.pem.password" )
    public void setKeyPassword( String keyPassword )
    {
        this.keyPassword = keyPassword;
    }

    @ConfigName( "max.connections" )
    public void setMaxConnections( Integer maxConnections )
    {
        this.maxConnections = maxConnections;
    }

    @ConfigName( "proxy.host" )
    public void setProxyHost( String proxyHost )
    {
        this.proxyHost = proxyHost;
    }

    @ConfigName( "proxy.port" )
    public void setProxyPort( Integer proxyPort )
    {
        this.proxyPort = proxyPort;
    }

    @ConfigName( "proxy.user" )
    public void setProxyUser( String proxyUser )
    {
        this.proxyUser = proxyUser;
    }

    @ConfigName( "request.timeout.seconds" )
    public void setRequestTimeoutSeconds( Integer requestTimeoutSeconds )
    {
        this.requestTimeoutSeconds = requestTimeoutSeconds;
    }

    @ConfigName( "trust.type" )
    public void setSiteTrustType( String siteTrustType )
    {
        this.siteTrustType = siteTrustType;
    }

    @ConfigName( "proxy.password" )
    public void setProxyPassword( String proxyPassword )
    {
        this.proxyPassword = proxyPassword;
    }

    @ConfigName( "storage.url" )
    public void setStorageRootUrl( String storageRootUrl )
    {
        this.storageRootUrl = storageRootUrl;
    }
}
