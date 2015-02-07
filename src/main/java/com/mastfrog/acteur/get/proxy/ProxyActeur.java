package com.mastfrog.acteur.get.proxy;

import com.google.inject.Inject;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.annotations.Concluders;
import com.mastfrog.url.URL;
import com.mastfrog.acteur.preconditions.Methods;
import com.mastfrog.acteur.preconditions.RequiredUrlParameters;
import static com.mastfrog.acteur.headers.Method.GET;
import com.mastfrog.acteur.annotations.HttpCall;
import com.mastfrog.acteur.errors.Err;
import com.mastfrog.acteur.get.proxy.ProxyActeur.FinishActeur;
import com.mastfrog.acteur.headers.Headers;
import com.mastfrog.acteur.preconditions.Description;
import com.mastfrog.acteur.server.PathFactory;
import com.mastfrog.acteur.util.Connection;
import com.mastfrog.acteurbase.Deferral;
import com.mastfrog.acteurbase.Deferral.Resumer;
import com.mastfrog.netty.http.client.HttpClient;
import com.mastfrog.netty.http.client.ResponseHandler;
import com.mastfrog.url.Path;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.net.URISyntaxException;

/**
 *
 * @author Tim Boudreau
 */
@Methods(GET)
@HttpCall(scopeTypes = {ByteBuf.class, HttpResponseStatus.class, HttpHeaders.class})
@RequiredUrlParameters("url")
@Concluders(FinishActeur.class)
@Description("Proxy's the URL passed as the 'url' url parameter")
public class ProxyActeur extends Acteur {

    @Inject
    ProxyActeur(HttpEvent evt, Deferral defer, HttpClient client, final Database database) {
        final URL url = URL.parse(evt.getParameter("url"));
        // Don't try to use invalid URLs
        if (!url.isValid()) {
            setState(new RespondWith(Err.badRequest(url.getProblems().toString())));
            return;
        }
        // See if we have a cached previous response
        Database.Entry entry = database.getResponse(url);
        if (entry != null) {
            // If yes, send that as the response
            next(entry.status, entry.headers, entry.buf);
            return;
        }
        // Use the Deferral to pause processing this request until we have a
        // response from the HTTP client.  Note that this does *not* tie up a
        // thread waiting for the response
        final Resumer resumer = defer.defer();
        // Perform an HTTP request
        client.get().setURL(url).execute(new ResponseHandler<ByteBuf>(ByteBuf.class) {

            @Override
            protected void receive(HttpResponseStatus status, HttpHeaders headers, ByteBuf bytes) {
                // Got a response of some kind - add it to the database
                database.add(url, status, headers, bytes);
                resumer.resume(status, headers, bytes.duplicate());
            }

            @Override
            protected void onErrorResponse(HttpResponseStatus status, HttpHeaders headers, String content) {
                // Write out the error message as a buffer
                ByteBuf buf = evt.getChannel().alloc().buffer();
                ByteBufUtil.writeUtf8(buf, content);
                resumer.resume(status, headers, buf);
            }
        });
        super.next();
    }

    static class FinishActeur extends Acteur implements ChannelFutureListener {

        private final ByteBuf bytes;

        @Inject
        FinishActeur(HttpResponseStatus status, HttpHeaders headers, ByteBuf bytes, PathFactory pf) throws URISyntaxException {
            // Finish the response
            this.bytes = bytes;
            // Set the response code
            reply(status);
            // proxy the headers
            add(Headers.CONTENT_LENGTH, (long) bytes.readableBytes());
            for (String name : headers.names()) {
                switch (name) {
                    case "Content-Length": // we set this manual
                        continue;
                    case "Server": // The framework will set these
                        continue;
                    case "Date":
                        continue;
                    case "Connection": // We set this
                        continue;
                    case "Location":
                        // Rewrite as a proxy URL to this server
                        // Note this will break on a technically illegal but
                        // common practice of using a path with no host for the
                        // location - doing this better is left as an exercise
                        // for the reader
                        add(Headers.LOCATION, pf.constructURL(Path.parse("/"), false).withParameter("url", headers.get(name)).toURI());
                        continue;
                }
                add(Headers.stringHeader(name), headers.get(name));
            }
            // Tell the browser to close the connection
            add(Headers.CONNECTION, Connection.close);
            // If 0 bytes, don't bother getting a callback once the headers have
            // been flushed to the socket
            if (bytes.readableBytes() > 0) {
                setResponseBodyWriter(this);
            }
            // We are not using chunked encoding.  We *could* intercept every
            // chunk on the fly and forward it, rather than aggregate them -
            // for an example of that see https://github.com/timboudreau/tiny-maven-proxy
            setChunked(false);
        }

        @Override
        public void operationComplete(ChannelFuture f) throws Exception {
            // Flush the bytes to the socket and close the connection
            f.channel().writeAndFlush(bytes).addListener(CLOSE);
        }
    }
}
