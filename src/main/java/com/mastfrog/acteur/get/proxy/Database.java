package com.mastfrog.acteur.get.proxy;

import com.google.common.collect.Maps;
import com.google.inject.Singleton;
import com.mastfrog.url.URL;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.util.Map;

/**
 * Very, very naive implementation of a cache, which does no header 
 * checking, and does not ever discard data.  This is a demonstration,
 * not production code!
 *
 * @author Tim Boudreau
 */
@Singleton
public class Database {

    private final Map<URL, Entry> cache = Maps.newConcurrentMap();

    public Entry getResponse(URL url) {
        Entry entry = cache.get(url);
        if (entry != null) {
            return entry.duplicate();
        }
        return null;
    }

    public void add(URL url, HttpResponseStatus status, HttpHeaders headers, ByteBuf data) {
        cache.put(url, new Entry(status, headers, Unpooled.unreleasableBuffer(data.copy())));
    }

    public static class Entry {

        public final HttpHeaders headers;
        public final HttpResponseStatus status;
        public final ByteBuf buf;

        public Entry(HttpResponseStatus status, HttpHeaders headers, ByteBuf buf) {
            this.headers = headers;
            this.status = status;
            this.buf = buf;
        }

        Entry duplicate() {
            return new Entry(status, headers, buf.duplicate());
        }
    }
}
