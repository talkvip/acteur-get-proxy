package com.mastfrog.acteur.get.proxy;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.mastfrog.acteur.server.ServerBuilder;
import com.mastfrog.acteur.util.ServerControl;
import static com.mastfrog.giulius.SettingsBindings.BOOLEAN;
import static com.mastfrog.giulius.SettingsBindings.INT;
import static com.mastfrog.giulius.SettingsBindings.STRING;
import com.mastfrog.netty.http.client.HttpClient;
import com.mastfrog.settings.Settings;
import com.mastfrog.settings.SettingsBuilder;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelOption;
import static com.mastfrog.acteur.server.ServerModule.BYTEBUF_ALLOCATOR_SETTINGS_KEY;
import static com.mastfrog.acteur.server.ServerModule.HTTP_COMPRESSION;
import static com.mastfrog.acteur.server.ServerModule.DIRECT_ALLOCATOR;
import static com.mastfrog.acteur.server.ServerModule.EVENT_THREADS;
import static com.mastfrog.acteur.server.ServerModule.MAX_CONTENT_LENGTH;
import static com.mastfrog.acteur.server.ServerModule.PORT;
import static com.mastfrog.acteur.server.ServerModule.WORKER_THREADS;
import java.io.IOException;

/**
 *
 * @author Tim Boudreau
 */
public class ActeurGetProxy extends AbstractModule {

    // For the Server: HTTP header
    private static final String APPLICATION_NAME = "acteurGetProxy";
    // Can set the number of download threads on the command-line, e.g.
    // --download.threads 4
    private static final String SETTINGS_KEY_DOWNLOAD_THREADS = "download.threads";

    public static void main(String[] args) throws InterruptedException, IOException {
        // Read /etc/acteurGetProxy.properties, ~/acteurGetProxy.properties and
        // ./acteurGetProxy.properties, layer them up over each other, and allow
        // command-line arguments in the form --property value to override;  and
        // set up some defaults for things that aren't already set
        Settings settings = new SettingsBuilder(APPLICATION_NAME)
                // will bet the Server header
                .add("application.name", APPLICATION_NAME)
                // turn off HTTP comrpession for now
                .add(HTTP_COMPRESSION, "false")
                // Set the number of HTTP client threads - note, it's async,
                // so one thread can service more than one request
                .add(SETTINGS_KEY_DOWNLOAD_THREADS, "12")
                // Number of worker threads to process requests - thsi can be
                // small, again, because it's async
                .add(WORKER_THREADS, "12")
                // Event threads is fine with 1
                .add(EVENT_THREADS, "1")
                // This is the max *inbound* HTTP request size, and we don't need
                // a large buffer allocated for this since we don't accept PUTs
                // or POSTs that have a body
                .add(MAX_CONTENT_LENGTH, "128") // we don't accept PUTs, no need for a big buffer
                // Set the default port - can overridde on the command-line, e.g.
                // --port 8080
                .add(PORT, "5957")
                // Use Netty's direct bytebuf allocator
                .add(BYTEBUF_ALLOCATOR_SETTINGS_KEY, DIRECT_ALLOCATOR)
                // Include /etc/acteurgetProxy.properties and friends after
                // the defaults so they can override them
                .addFilesystemAndClasspathLocations()
                //  Parse command-line arguments last so they can override these
                .parseCommandLineArguments(args).build();
        // Build a server and start it and gets an object that can be used
        // to shut it down or wait for it to exit
        ServerControl ctrl = new ServerBuilder(APPLICATION_NAME)
                // Add this guice module
                .add(new ActeurGetProxy())
                // Optimization - just trims memory footprint down if we won't
                // have bindings for float, double, BigInteger, etc.
                .enableOnlyBindingsFor(BOOLEAN, INT, STRING)
                // Pass it the settings
                .add(settings)
                // Build the server
                .build()
                // Start it
                .start();
        // Wait for exit, so one non-daemon thread stays alive
        ctrl.await();
    }

    @Override
    protected void configure() {
        // Use the HTTP client we instantiate below
        bind(HttpClient.class).toProvider(HttpClientProvider.class);
    }

    @Singleton
    static class HttpClientProvider implements Provider<HttpClient> {

        private final HttpClient client;

        @Inject
        HttpClientProvider(ByteBufAllocator alloc, @Named(SETTINGS_KEY_DOWNLOAD_THREADS) int downloadThreads) {
            
            client = HttpClient.builder()
                    .setUserAgent("ActeurGetProxy 1.0")
//                    .followRedirects()
                    .dontFollowRedirects()
                    // Share the byte buf allocator with the server
                    .setChannelOption(ChannelOption.ALLOCATOR, alloc)
                    // set the number of download threads
                    .threadCount(downloadThreads)
                    // set the maximum chunk size
                    .maxChunkSize(16384).build();
        }

        @Override
        public HttpClient get() {
            return client;
        }
    }
}
