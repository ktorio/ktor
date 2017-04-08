<!doctype html>
<html>
<head>
    <title>ktor-samples-httpbin</title>
    <script src="https://unpkg.com/jquery@3.2.0"></script>
</head>
<body>
<style>
    a { text-decoration: underline }
</style>
<div class='mp'>

<h1>ktor-samples-httpbin</h1>

    <h2>DESCRIPTION</h2>

    <p><a href="https://github.com/Kotlin/ktor">Ktor</a> implementation of <a href="https://httpbin.org/">httpbin(1)</a>: HTTP Request & Response Service</p>

    <p>
        A good way to play with the endpoint is to install <a href="https://www.getpostman.com/">Postman</a>
        and to import in it the collection
        <a download="httpbin.postman_collection.json" href="http://localhost:8080/postman">httpbin.postman_collection.json</a>
    </p>

<h2 id="ENDPOINTS">ENDPOINTS</h2>

<ul>
<li><a href="/" data-bare-link="true"><code>/</code></a> This page.</li>
<li><a id="view_ip" data-bare-link="true"><code>/ip</code></a> Returns Origin IP.</li>
<li><a id="view_user_agent" data-bare-link="true"><code>/user-agent</code></a> Returns user-agent.</li>
<li><a id="view_headers" data-bare-link="true"><code>/headers</code></a> Returns header dict.</li>
<li><a id="view_get" data-bare-link="true"><code>/get</code></a> Returns GET data.</li>
<li><a id="view_post"><code>/post</code></a> Returns POST data.</li>
<li><a id="view_patch"><code>/patch</code></a> Returns PATCH data.</li>
<li><a id="view_put"><code>/put</code></a> Returns PUT data.</li>
<li><a id="view_delete"><code>/delete</code></a> Returns DELETE data</li>
<li><a href="/encoding/utf8"><code>/encoding/utf8</code></a> Returns page containing UTF-8 data.</li>
<li><a id="view_gzip_encoded_content" data-bare-link="true"><code>/gzip</code></a> Returns gzip-encoded data.</li>
<li><a id="view_deflate_encoded_content" data-bare-link="true"><code>/deflate</code></a> Returns deflate-encoded data.</li>
<li><a href="/status/418"><code>/status/:code</code></a> Returns given HTTP Status code.</li>
<li><a id="response_headers"><code>/response-headers?key=val</code></a> Returns given response headers.</li>
<li><a href="/redirect/6"><code>/redirect/:n</code></a> 302 Redirects <em>n</em> times.</li>
<li><a href="/redirect-to?url=https://www.wikipedia.org"><code>/redirect-to?url=foo</code></a> 302 Redirects to the <em>foo</em> URL.</li>
<!--<li><a href="{{ url_for('redirect_to', url='http://example.com/', status_code=307) }}"><code>/redirect-to?url=foo&status_code=307</code></a> 307 Redirects to the <em>foo</em> URL.</li>-->
<!--<li><a href="{{ url_for('relative_redirect_n_times', n=6) }}"><code>/relative-redirect/:n</code></a> 302 Relative redirects <em>n</em> times.</li>-->
<!--<li><a href="{{ url_for('absolute_redirect_n_times', n=6) }}"><code>/absolute-redirect/:n</code></a> 302 Absolute redirects <em>n</em> times.</li>-->
<li><a id="view_cookies" data-bare-link="true"><code>/cookies</code></a> Returns cookie data.</li>
<li><a id="set_cookies"><code>/cookies/set?name=value</code></a> Sets one or more simple cookies.</li>
<li><a id="delete_cookies"><code>/cookies/delete?name</code></a> Deletes one or more simple cookies.</li>
<li><a href="/basic-auth/test/test"><code>/basic-auth/:user/:passwd</code></a> Challenges HTTPBasic Auth.</li>
<li><a href="/hidden_basic_auth/test/test"><code>/hidden-basic-auth/:user/:passwd</code></a> 404'd BasicAuth.</li>
<!--<li><a href="{{ url_for('digest_auth', qop='auth', user='user', passwd='passwd', algorithm='MD5') }}"><code>/digest-auth/:qop/:user/:passwd/:algorithm</code></a> Challenges HTTP Digest Auth.</li>-->
<!--<li><a href="{{ url_for('digest_auth', qop='auth', user='user', passwd='passwd', algorithm='MD5') }}"><code>/digest-auth/:qop/:user/:passwd</code></a> Challenges HTTP Digest Auth.</li>-->
<li><a href="/stream/42"><code>/stream/:n</code></a> Streams <em>n</em> lines.</li>
<li><a href="/delay/3"><code>/delay/:n</code></a> Delays responding for <em>n</em> seconds.</li>
<!--<li><a href="{{ url_for('drip', numbytes=5, duration=5, code=200) }}"><code>/drip?numbytes=n&amp;duration=s&amp;delay=s&amp;code=code</code></a> Drips data over a duration after an optional initial delay, then (optionally) returns with the given status code.</li>-->
<!--<li><a href="{{ url_for('range_request', numbytes=1024) }}"><code>/range/1024?duration=s&amp;chunk_size=code</code></a> Streams <em>n</em> bytes, and allows specifying a <em>Range</em> header to select a subset of the data. Accepts a <em>chunk_size</em> and request <em>duration</em> parameter.</li>-->
<li><a href="/html" data-bare-link="true"><code>/html</code></a> Renders an HTML Page.</li>
<li><a href="/robots.txt" data-bare-link="true"><code>/robots.txt</code></a> Returns some robots.txt rules.</li>
<li><a href="/deny" data-bare-link="true"><code>/deny</code></a> Denied by robots.txt file.</li>
<!--<li><a href="{{ url_for('cache') }}" data-bare-link="true"><code>/cache</code></a> Returns 200 unless an If-Modified-Since or If-None-Match header is provided, when it returns a 304.</li>-->
<!--<li><a href="{{ url_for('cache_control', value=60) }}"><code>/cache/:n</code></a> Sets a Cache-Control header for <em>n</em> seconds.</li>-->
<li><a href="/bytes/1024"><code>/bytes/:n</code></a> Generates <em>n</em> random bytes of binary data, accepts optional <em>seed</em> integer parameter.</li>
<!--<li><a href="{{ url_for('stream_random_bytes', n=1024) }}"><code>/stream-bytes/:n</code></a> Streams <em>n</em> random bytes of binary data, accepts optional <em>seed</em> and <em>chunk_size</em> integer parameters.</li>-->
<li><a href="/links/10/3"><code>/links/:n</code></a> Returns page containing <em>n</em> HTML links.</li>
<li><a href="/image"><code>/image</code></a> Returns page containing an image based on sent Accept header.</li>
<li><a href="/image/png"><code>/image/png</code></a> Returns page containing a PNG image.</li>
<li><a href="/image/jpeg"><code>/image/jpeg</code></a> Returns page containing a JPEG image.</li>
<li><a href=/image/webp><code>/image/webp</code></a> Returns page containing a WEBP image.</li>
<li><a href="/image/svg"><code>/image/svg</code></a> Returns page containing a SVG image.</li>
<li><a href="/forms/post" data-bare-link="true"><code>/forms/post</code></a> HTML form that submits to <em>/post</em></li>
<li><a href=/xml data-bare-link="true"><code>/xml</code></a> Returns some XML</li>
</ul>

</div>
<script src="httpbin.js"></script>
</body>
</html>