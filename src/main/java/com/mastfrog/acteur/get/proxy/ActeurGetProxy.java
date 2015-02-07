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

    private static final String APPLICATION_NAME = "acteurGetProxy";
    private static final String SETTINGS_KEY_DOWNLOAD_THREADS = "download.threads";

    public static void main(String[] args) throws InterruptedException, IOException {
        Settings settings = new SettingsBuilder(APPLICATION_NAME)
                .add("application.name", APPLICATION_NAME)
                .add(HTTP_COMPRESSION, "true")
                .add(SETTINGS_KEY_DOWNLOAD_THREADS, "24")
                .add(WORKER_THREADS, "4")
                .add(EVENT_THREADS, "1")
                .add(MAX_CONTENT_LENGTH, "128") // we don't accept PUTs, no need for a big buffer
                .add(PORT, "5957")
                .add(BYTEBUF_ALLOCATOR_SETTINGS_KEY, DIRECT_ALLOCATOR)
                .addFilesystemAndClasspathLocations()
                .parseCommandLineArguments(args).build();
        ServerControl ctrl = new ServerBuilder(APPLICATION_NAME)
                .add(new ActeurGetProxy())
                .enableOnlyBindingsFor(BOOLEAN, INT, STRING)
                .add(settings)
                .build().start();
        ctrl.await();
    }

    @Override
    protected void configure() {
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
                    .setChannelOption(ChannelOption.ALLOCATOR, alloc)
                    .threadCount(downloadThreads)
                    .maxChunkSize(16384).build();
        }

        @Override
        public HttpClient get() {
            return client;
        }
    }
}
