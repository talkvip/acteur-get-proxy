Acteur Get Proxy
================

A demo of how simple it is to write a proxy with [Acteur](https://github.com/timboudreau/acteur) + 
[Netty](http://netty.io), mainly in response to 
[this stackoverflow question](https://stackoverflow.com/questions/27923680/asynchronous-http-request-handling-with-tomcat-and-spring).
The entire project consists of three Java source files.

Uses entirely asynchronous I/O to implement a simple HTTP proxy for GET requests, with
a naive cache for responses it's already seen.

Build
-----

Just check the sources out using Maven, build them and

```
cd target
java -jar acteur-get-proxy.jar
```

Usage
-----

Start the server (by default it runs on port 5957).  Access it, passing it the
URL you want it to proxy as the URL parameter `url`.  For example, here's 
accessing `google.com`:

```
tim@tim ~/ $ curl -i "http://localhost:5957/?url=http://google.com"
HTTP/1.1 301 Moved Permanently
Allow: GET
Content-Length: 219
Location: http://localhost:5957/?url=http://www.google.com/
Content-Type: text/html; charset=UTF-8
Expires: Mon, 09 Mar 2015 04:18:45 GMT
Cache-Control: public, max-age=2592000
X-XSS-Protection: 1; mode=block
X-Frame-Options: SAMEORIGIN
Connection: close
Alternate-Protocol: 80:quic,p=0.02
Server: acteurGetProxy
Date: Sat, 07 Feb 2015 04:17:30 GMT
Access-Control-Allow-Origin: *
Access-Control-Max-Age: 0

<HTML><HEAD><meta http-equiv="content-type" content="text/html;charset=utf-8">
<TITLE>301 Moved</TITLE></HEAD><BODY>
<H1>301 Moved</H1>
The document has moved
<A HREF="http://www.google.com/">here</A>.
</BODY></HTML>
```
